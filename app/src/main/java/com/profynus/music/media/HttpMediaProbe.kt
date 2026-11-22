package com.profynus.music.media

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Performs an intentionally tiny byte-range request. It does NOT download the full file.
 * Never logs the signed URL or response Location header.
 */
data class HttpMediaProbeResult(
    val statusCode: Int,
    val contentType: String?,
    val totalBytes: Long?,
    val rangeSupported: Boolean,
    val outcome: String
) {
    fun summary(): String = "HTTP $statusCode · ${contentType ?: "unknown MIME"} · " +
        "${totalBytes ?: "unknown"} bytes · ranges=$rangeSupported · $outcome"
}

object HttpMediaProbe {
    private val contentRange = Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE)

    fun inspect(source: URL, allowLoopbackHttpForTests: Boolean = false): HttpMediaProbeResult {
        val safeScheme = source.protocol.equals("https", ignoreCase = true) ||
            (allowLoopbackHttpForTests && source.protocol == "http" &&
                source.host in setOf("127.0.0.1", "localhost"))
        require(safeScheme && source.userInfo == null) { "Unacceptable source URL" }

        val conn = (source.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = conn.responseCode
            val type = conn.getHeaderField("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            if (conn.getHeaderField("Content-Encoding")?.let { !it.equals("identity", true) } == true) {
                return HttpMediaProbeResult(code, type, null, false, "HTTP content encoding is not identity")
            }
            return when (code) {
                206 -> {
                    val match = contentRange.matchEntire(conn.getHeaderField("Content-Range").orEmpty())
                        ?: throw IOException("Invalid Content-Range on byte-zero probe")
                    val size = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 }
                        ?: throw IOException("Unknown total length")
                    HttpMediaProbeResult(code, type, size, true, "range-ready")
                }
                200 -> HttpMediaProbeResult(
                    code, type, conn.contentLengthLong.takeIf { it >= 0 }, false,
                    "server ignored Range; sequential transfer only"
                )
                301, 302, 303, 307, 308 -> HttpMediaProbeResult(
                    code, type, null, false, "redirect; validate target separately"
                )
                401, 403 -> HttpMediaProbeResult(code, type, null, false, "access denied")
                404, 410 -> HttpMediaProbeResult(code, type, null, false, "resource unavailable or expired")
                429 -> HttpMediaProbeResult(code, type, null, false, "rate limited; don't retry aggressively")
                else -> HttpMediaProbeResult(code, type, null, false, "unsupported HTTP response")
            }
        } finally {
            // For 200, do not read the body. A range probe should never fetch the entire file.
            conn.disconnect()
        }
    }
}
