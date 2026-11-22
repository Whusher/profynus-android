package com.profynus.media.transport

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses and validates a YouTube video URL. This does not resolve downloadable streams. */
data class YouTubeVideoReference(val videoId: String) {
    companion object {
        private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
        private val allowedYouTubeHosts = setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtube-nocookie.com", "www.youtube-nocookie.com"
        )

        fun parse(input: String): YouTubeVideoReference {
            val uri = URI(input.trim())
            require(uri.scheme == "https" || uri.scheme == "http") { "Expected HTTP(S) URL" }
            val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Missing host")
            val path = uri.path.orEmpty().trim('/')
            val id = when (host) {
                "youtu.be", "www.youtu.be" -> path.substringBefore('/')
                in allowedYouTubeHosts -> when {
                    path == "watch" -> queryParameter(uri.rawQuery, "v")
                    path.startsWith("shorts/") -> path.removePrefix("shorts/").substringBefore('/')
                    path.startsWith("live/") -> path.removePrefix("live/").substringBefore('/')
                    path.startsWith("embed/") -> path.removePrefix("embed/").substringBefore('/')
                    else -> null
                }
                else -> null
            }
            require(id != null && idPattern.matches(id)) { "Unrecognized YouTube video URL" }
            return YouTubeVideoReference(id)
        }

        private fun queryParameter(rawQuery: String?, name: String): String? {
            return rawQuery?.split('&')?.mapNotNull { pair ->
                val key = pair.substringBefore('=')
                if (key == name) URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8.name())
                else null
            }?.firstOrNull()
        }
    }
}

/**
 * This interface is the missing, independently-maintained integration.
 * Only pass tracks to the transport if obtaining and downloading them is authorized.
 */
interface YouTubeMediaResolver {
    suspend fun inspect(reference: YouTubeVideoReference): SourceCapabilities
}

data class SourceCapabilities(
    val videoId: String,
    val playbackAvailable: Boolean,
    val downloadableTracks: List<ResolvedTrack>,
    val reasonUnavailable: String? = null
)

data class ResolvedTrack(
    val kind: TrackKind,
    val codec: String,
    val mimeType: String,
    val url: java.net.URL,
    val expiresAtUnixSeconds: Long?
)

enum class TrackKind { VIDEO, AUDIO, AUDIO_VIDEO }
