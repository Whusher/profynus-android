
package com.profynus.music.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

data class BrowserMediaEvent(
    val type: String,
    val mimeType: String?,
    val contentLength: Long?
)

object ProfynusBrowserBridge {

    private const val TRUSTED_ORIGIN =
        "https://appassets.androidplatform.net"

    fun attach(
        webView: WebView,
        onEvent: (BrowserMediaEvent) -> Unit
    ) {
        check(
            WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
        ) {
            "Secure WebMessageListener unavailable"
        }

        WebViewCompat.addWebMessageListener(
            webView,
            "ProfynusNative",
            setOf(TRUSTED_ORIGIN),
            WebViewCompat.WebMessageListener {
                    view,
                    message,
                    sourceOrigin,
                    isMainFrame,
                    _ ->

                if (!isMainFrame ||
                    sourceOrigin.toString() != TRUSTED_ORIGIN
                ) {
                    return@WebMessageListener
                }

                val payload = runCatching {
                    JSONObject(
                        message.data
                            ?: return@WebMessageListener
                    )
                }.getOrNull()
                    ?: return@WebMessageListener

                if (payload.optString("type") != "MEDIA_READY") {
                    return@WebMessageListener
                }

                val mime = payload.optString("mimeType")

                if (mime !in setOf(
                        "video/mp4",
                        "audio/mpeg",
                        "audio/mp4"
                    )
                ) {
                    return@WebMessageListener
                }

                val bytes = payload.optLong(
                    "contentLength",
                    -1L
                )

                view.post {
                    onEvent(
                        BrowserMediaEvent(
                            type = "MEDIA_READY",
                            mimeType = mime,
                            contentLength =
                                bytes.takeIf { it >= 0L }
                        )
                    )
                }
            }
        )
    }
}
