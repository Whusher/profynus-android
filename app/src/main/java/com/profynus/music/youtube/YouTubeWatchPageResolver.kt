package com.profynus.music.youtube

import com.profynus.music.media.MediaStreamDescriptor
import com.profynus.music.media.MediaStreamResolver
import com.profynus.music.media.ResolutionResult
import com.profynus.music.media.ResolvedMedia
import com.profynus.music.media.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * EXPERIMENTAL: inspect the publicly returned watch-page player response.
 * YouTube may omit direct media URLs, require a session, or use SABR delivery.
 * This implementation does not decrypt signatures, run player JS, mint tokens,
 * read browser cookies, or bypass DRM/access controls.
 *
 * `downloadable` below only describes the presence of a basic HTTPS transfer
 * candidate; it is NOT a grant of rights to copy or export YouTube content.
 */
class YouTubeWatchPageResolver : MediaStreamResolver {

    override suspend fun resolve(videoId: String): ResolutionResult = withContext(Dispatchers.IO) {
        if (!Regex("^[A-Za-z0-9_-]{11}$").matches(videoId)) {
            return@withContext ResolutionResult.Unavailable("Invalid YouTube video ID")
        }
        try {
            val html = SafeWatchPageFetcher.fetch(videoId)
            val raw = PlayerResponseExtractor.extract(html)
                ?: return@withContext ResolutionResult.Unavailable(
                    "No player response was exposed in the HTML. The page may require JavaScript, cookies, or a different playback delivery mechanism."
                )
            val player = JSONObject(raw)
            parseResponse(player, videoId)
        } catch (e: IOException) {
            ResolutionResult.Unavailable("Network error: ${e.message ?: "unknown"}")
        } catch (e: Exception) {
            ResolutionResult.Unavailable("Unrecognized player response: ${e.javaClass.simpleName}")
        }
    }

    private fun parseResponse(player: JSONObject, videoId: String): ResolutionResult {
        val playability = player.optJSONObject("playabilityStatus")
        val state = playability?.optString("status").orEmpty()
        if (state.isNotBlank() && state != "OK") {
            return ResolutionResult.Unavailable(
                "YouTube playability: $state (${playability?.optString("reason").orEmpty().take(120)})"
            )
        }
        val streaming = player.optJSONObject("streamingData")
            ?: return ResolutionResult.Unavailable(
                "Player response has no streamingData. Public metadata is not a media download URL."
            )

        val tracks = mutableListOf<MediaStreamDescriptor>()
        var ciphered = 0
        var missingUrl = 0
        for (field in listOf("formats", "adaptiveFormats")) {
            val formats = streaming.optJSONArray(field) ?: JSONArray()
            for (i in 0 until formats.length()) {
                val item = formats.optJSONObject(i) ?: continue
                val link = item.optString("url")
                if (link.isBlank()) {
                    if (item.has("signatureCipher") || item.has("cipher")) ciphered++
                    else missingUrl++
                    continue
                }
                if (!isTrustedDeliveryUrl(link)) continue
                val rawMime = item.optString("mimeType")
                val mime = rawMime.substringBefore(';').trim().lowercase(Locale.ROOT)
                if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                val codec = Regex("codecs=\\\"([^\\\"]+)\\\"").find(rawMime)?.groupValues?.get(1)
                val video = mime.startsWith("video/")
                val audio = mime.startsWith("audio/") || item.has("audioQuality") || item.has("audioChannels")
                val trackType = when {
                    video && audio -> TrackType.AUDIO_VIDEO
                    video -> TrackType.VIDEO
                    else -> TrackType.AUDIO
                }
                val expiry = try {
                    val uri = URI(link)
                    uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("expire=") }
                        ?.substringAfter('=')?.toLongOrNull()?.times(1000L)
                } catch (_: Exception) { null }
                val isExpired = expiry != null && expiry <= System.currentTimeMillis()
                tracks += MediaStreamDescriptor(
                    id = item.optString("itag", "$field-$i"),
                    type = trackType,
                    url = link,
                    mimeType = mime,
                    codec = codec,
                    bitrate = item.optLong("bitrate").takeIf { it > 0L },
                    contentLength = item.optString("contentLength").toLongOrNull(),
                    expiresAtEpochMs = expiry,
                    downloadable = !isExpired // transport candidate only; NOT license/permission
                )
            }
        }

        if (tracks.isEmpty()) {
            val sabr = streaming.optString("serverAbrStreamingUrl").isNotBlank()
            return ResolutionResult.Unavailable(
                "No directly exposed HTTPS tracks. " +
                "Ciphered: $ciphered, missing URL: $missingUrl, SABR: $sabr. " +
                "This experimental resolver cannot handle signed formats or SABR."
            )
        }
        val title = player.optJSONObject("videoDetails")?.optString("title")
            ?.takeIf { it.isNotBlank() } ?: "YouTube video $videoId"
        return ResolutionResult.Available(ResolvedMedia(videoId, title, tracks))
    }

    private fun isTrustedDeliveryUrl(raw: String): Boolean = try {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null && uri.port in setOf(-1, 443) &&
            (host == "googlevideo.com" || host.endsWith(".googlevideo.com"))
    } catch (_: Exception) {
        false
    }
}
