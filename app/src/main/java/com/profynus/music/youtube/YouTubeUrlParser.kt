package com.profynus.music.youtube

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** A validated YouTube video identifier, not a downloadable media URL. */
data class YouTubeVideoReference(val id: String) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

object YouTubeUrlParser {
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val youtubeHosts = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com"
    )
    private val shortHosts = setOf("youtu.be", "www.youtu.be")

    fun parse(input: String): YouTubeVideoReference {
        val raw = input.trim()
        require(raw.isNotEmpty()) { "Paste a YouTube video URL." }
        if (videoId.matches(raw)) return YouTubeVideoReference(raw)

        val full = if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) {
            raw
        } else {
            "https://$raw"
        }
        val uri = try {
            URI(full)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid URL syntax.")
        }
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) {
            "Only HTTP(S) YouTube links are supported."
        }
        require(uri.userInfo == null && uri.port in setOf(-1, 80, 443)) {
            "Invalid YouTube URL authority."
        }
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
        require(host in youtubeHosts || host in shortHosts) {
            "This URL does not point to YouTube."
        }

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val id = if (host in shortHosts) {
            segments.singleOrNull()
        } else {
            when (segments.firstOrNull()) {
                "watch" -> queryValue(uri.rawQuery, "v")
                "shorts", "live", "embed", "v" -> segments.getOrNull(1)
                else -> null
            }
        }

        require(id != null && videoId.matches(id)) {
            "No valid YouTube video ID found (playlists and channels are not videos)."
        }
        return YouTubeVideoReference(id)
    }

    private fun queryValue(rawQuery: String?, key: String): String? = rawQuery
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=') == key }
        ?.substringAfter('=', "")
        ?.let { URLDecoder.decode(it, "UTF-8") }
}
