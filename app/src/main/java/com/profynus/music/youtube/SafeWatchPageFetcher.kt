package com.profynus.music.youtube

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Debug-only watch-page HTML fetcher with bounded, validated redirect handling.
 * Does not copy browser cookies, impersonate a playback client or fetch media.
 */
internal object SafeWatchPageFetcher {
    private const val MAX_REDIRECTS = 5
    private const val MAX_HTML_CHARS = 4_000_000

    fun fetch(videoId: String): String {
        require(Regex("^[A-Za-z0-9_-]{11}$").matches(videoId))
        var current = URL("https://www.youtube.com/watch?v=$videoId")

        for (hop in 0..MAX_REDIRECTS) {
            requireAllowedDestination(current)
            val connection = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/html")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    if (hop == MAX_REDIRECTS) {
                        throw IOException("Too many watch-page redirects")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("HTTP $status without Location header")
                    val target = URL(current, location)
                    requireAllowedDestination(target)
                    current = target
                    continue
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("Watch-page HTTP $status from ${current.host}")
                }
                if (!connection.contentType.orEmpty().startsWith("text/html", ignoreCase = true)) {
                    throw IOException("Expected HTML but received ${connection.contentType.orEmpty().take(70)}")
                }
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val n = reader.read(buffer)
                        if (n < 0) break
                        out.append(buffer, 0, n)
                        if (out.length > MAX_HTML_CHARS) {
                            throw IOException("Watch-page HTML exceeds 4 MB")
                        }
                    }
                    out.toString()
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many watch-page redirects")
    }

    private fun requireAllowedDestination(url: URL) {
        val host = url.host.lowercase(Locale.ROOT)
        val allowed = host == "youtube.com" || host == "www.youtube.com" ||
            host == "m.youtube.com" || host == "music.youtube.com" ||
            host == "consent.youtube.com"
        if (url.protocol != "https" || url.userInfo != null ||
            url.port !in setOf(-1, 443) || !allowed
        ) {
            throw IOException(
                "Watch-page redirect to ${host.ifBlank { "unknown host" }} blocked. " +
                "Inspect that destination in the browser rather than forwarding sessions or cookies."
            )
        }
    }
}
