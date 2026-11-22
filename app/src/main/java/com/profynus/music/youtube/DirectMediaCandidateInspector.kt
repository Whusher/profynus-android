package com.profynus.music.youtube

import android.net.Uri
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL

/**
 * Diagnostic-only inspection of an already exposed direct URL field (no deciphering).
 * The URL is ephemeral and sensitive: do not log, persist, or upload it.
 */
class DirectMediaCandidate(
    val url: URL,
    val itag: Int,
    val mimeType: String,
    val reportedBytes: Long?
) {
    override fun toString(): String =
        "DirectMediaCandidate(itag=$itag, mimeType=$mimeType, url=[REDACTED])"
}

object DirectMediaCandidateInspector {
    private const val JS = """
        (() => {
          const formats = window.ytInitialPlayerResponse?.streamingData?.formats || [];
          const f = formats.find(x => x.itag === 18 && typeof x.url === 'string');
          return JSON.stringify(f ? {
            itag: f.itag,
            mimeType: (f.mimeType || '').split(';')[0],
            reportedBytes: f.contentLength || null,
            url: f.url
          } : { unavailable: true });
        })()
    """

    fun inspect(webView: WebView, callback: (Result<DirectMediaCandidate>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must run on main thread" }
        val pageHost = Uri.parse(webView.url.orEmpty()).host.orEmpty().lowercase()
        if (pageHost !in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) {
            callback(Result.failure(IllegalStateException("Not on a trusted YouTube page")))
            return
        }
        webView.evaluateJavascript(JS) { encoded ->
            callback(runCatching {
                val decoded = JSONTokener(encoded).nextValue() as? String
                    ?: error("No player data")
                val data = JSONObject(decoded)
                if (data.optBoolean("unavailable", false)) error("No direct itag 18 URL field")
                val rawUrl = data.optString("url")
                val url = URL(rawUrl)
                val host = url.host.lowercase()
                require(url.protocol == "https" && url.userInfo == null &&
                    (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) &&
                    (url.port == -1 || url.port == 443)) {
                    "Candidate URL rejected by source policy"
                }
                require(data.optInt("itag") == 18) { "Unexpected format" }
                require(data.optString("mimeType") == "video/mp4") { "Not an MP4 candidate" }
                DirectMediaCandidate(
                    url = url,
                    itag = 18,
                    mimeType = "video/mp4",
                    reportedBytes = data.optString("reportedBytes").toLongOrNull()
                )
            })
        }
    }
}
