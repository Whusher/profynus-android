package com.profynus.music.youtube

import com.profynus.music.media.MediaFormatClassifier
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.util.Log
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONObject
import org.json.JSONTokener

/** Browser diagnostics and foreground audio export. Credentials remain in the WebView. */
@Composable
fun YouTubeBrowserDiagnosticScreen() {
    val context = LocalContext.current
    var userUrl by remember {
        mutableStateOf("https://www.youtube.com/watch?v=M7lc1UVf-VE")
    }
    var status by remember { mutableStateOf("Initializing WebView...") }
    var diagnostics by remember { mutableStateOf("Not inspected") }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var navigationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val export = remember { BrowserAudioExport(context, scope) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                export.cancel("Export stopped when the app left the foreground. Retry to download.")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    // Load the initial test URL only once after the WebView is created.
    LaunchedEffect(browser) {
        val webView = browser ?: return@LaunchedEffect
        val watchUrl = YouTubeUrlParser.parse(userUrl).watchUrl
        webView.loadUrl(watchUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            export.dispose()
            browser?.stopLoading()
            browser?.destroy()
            browser = null
        }
    }

    // The outer Column must NOT scroll: it contains an independently scrolling WebView.
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Profynus · Browser Diagnostics", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = userUrl,
            enabled = !export.busy,
            onValueChange = { userUrl = it },
            label = { Text("YouTube URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !export.busy, onClick = {
                try {
                    val watchUrl = YouTubeUrlParser.parse(userUrl).watchUrl
                    diagnostics = "Not inspected"
                    navigationError = null
                    val webView = browser
                    if (webView == null) {
                        status = "WebView is not ready"
                    } else {
                        status = "Opening YouTube..."
                        webView.loadUrl(watchUrl)
                    }
                } catch (e: IllegalArgumentException) {
                    status = e.message ?: "Invalid YouTube URL"
                }
            }) { Text("Open") }


            OutlinedButton(
                enabled = !export.busy,
                onClick = {
                    val webView = browser

                    if (webView == null) {
                        status = "WebView is not ready"
                    } else {
                        webView.evaluateJavascript(PAGE_PROBE_JS) { raw ->
                            try {

                                val decoded = JSONTokener(raw).nextValue() as? String
                                    ?: error("Invalid player response")

                                val report = JSONObject(decoded)

// Convert JSON into strongly typed models.
                                val formats = YouTubeFormatInventory.parse(report)

// Analyze available format metadata.
                                val summary = MediaFormatClassifier.summarize(formats)

// Display the classification.
                                diagnostics = MediaFormatClassifier.description(summary)

// Log only the sanitized summary.
                                Log.i("ProfynusCatalog", diagnostics)


                            } catch (e: Exception) {
                                diagnostics =
                                    "Inspection error: ${e.message}"

                                Log.e(
                                    "ProfynusProbe",
                                    "Format inspection failed",
                                    e
                                )
                            }
                        }
                    }
                }
            ) {
                Text("Inspect")
            }

        }

        Text("Browser: $status", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { export.start(userUrl) }, enabled = browser != null && !export.busy) {
                Text("Download MP3")
            }
            if (export.busy) {
                OutlinedButton(onClick = { export.cancel() }) { Text("Cancel") }
            }
        }
        if (export.busy) LinearProgressIndicator(
            progress = { export.progress }, modifier = Modifier.fillMaxWidth()
        )
        Text(export.report, style = MaterialTheme.typography.bodySmall)

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { androidContext ->
                val debug = (androidContext.applicationInfo.flags and
                        ApplicationInfo.FLAG_DEBUGGABLE) != 0
                WebView.setWebContentsDebuggingEnabled(debug)

                WebView(androidContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {

                        // Customization
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {

                            val uri = request.url
                            val host = uri.host.orEmpty().lowercase()

                            val relevant =
                                host == "youtube.com" ||
                                        host.endsWith(".youtube.com") ||
                                        host == "googlevideo.com" ||
                                        host.endsWith(".googlevideo.com")

                            if (relevant) {

                                // Log structure only, never full signed URLs.
                                val headerNames = request.requestHeaders
                                    .keys
                                    .map { it.lowercase() }
                                    .sorted()

                                android.util.Log.d(
                                    "ProfynusNetwork",
                                    "method=${request.method} " +
                                            "host=$host " +
                                            "path=${uri.path} " +
                                            "mainFrame=${request.isForMainFrame} " +
                                            "headers=${headerNames.joinToString(",")}"
                                )
                            }

                            // Return null: allow the WebView to handle
                            // the request normally.
                            return null
                        }



                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (!request.isForMainFrame) return false
                            val uri = request.url
                            val host = uri.host.orEmpty().lowercase()
                            if (uri.scheme == "https" &&
                                (host == "youtube.com" || host.endsWith(".youtube.com"))
                            ) return false

                            if (uri.scheme == "https") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    status = "External host opened in browser: $host"
                                } catch (_: Exception) {
                                    status = "Could not open external host: $host"
                                }
                            } else {
                                status = "Blocked non-HTTPS navigation"
                            }
                            return true
                        }

                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                        ) {
                            export.onPageStarted(url)
                            navigationError = null
                            status = "Loading ${Uri.parse(url.orEmpty()).host.orEmpty()}..."
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            status = navigationError
                                ?: "Page loaded: ${Uri.parse(url.orEmpty()).host.orEmpty()}"
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                navigationError = "Navigation error: ${error.description}"
                                status = navigationError!!
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse
                        ) {
                            if (request.isForMainFrame) {
                                navigationError = "Page HTTP error: ${errorResponse.statusCode}"
                                status = navigationError!!
                            }
                        }
                    }
                    export.attach(this)
                    browser = this
                }
            }
        )

        Text("Page capabilities", style = MaterialTheme.typography.titleMedium)
        // Only the diagnostics text scrolls, never the WebView and controls together.
        Text(
            diagnostics,
            modifier = Modifier.fillMaxWidth().height(120.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall
        )
    }
}


private val PAGE_PROBE_JS = """
(() => {
    const player = window.ytInitialPlayerResponse;

    if (!player) {
        return JSON.stringify({
            status: "PLAYER_RESPONSE_UNAVAILABLE",
            host: location.hostname
        });
    }

    const streaming = player.streamingData || {};

    const formats = [
        ...(streaming.formats || []),
        ...(streaming.adaptiveFormats || [])
    ];

    const inspectFormat = (f) => {
        const mime = f.mimeType || "";
        const codecMatch = mime.match(/codecs="([^"]+)"/);

        let kind = "UNKNOWN";

        if (f.width || f.height) {
            kind = f.audioQuality
                ? "AUDIO_VIDEO"
                : "VIDEO";
        } else if (
            f.audioQuality ||
            f.audioSampleRate
        ) {
            kind = "AUDIO";
        }

        return {
            itag: f.itag ?? null,
            kind: kind,
            mimeType: mime.split(";")[0],
            codecs: codecMatch ? codecMatch[1] : null,
            bitrate: f.bitrate ?? null,
            qualityLabel: f.qualityLabel ?? null,
            audioQuality: f.audioQuality ?? null,
            audioSampleRate: f.audioSampleRate ?? null,
            contentLength: f.contentLength ?? null,
            approxDurationMs: f.approxDurationMs ?? null,
            hasDirectUrl: Boolean(f.url),
            requiresSignature: Boolean(
                f.signatureCipher || f.cipher
            )
        };
    };

    return JSON.stringify({
        videoId: player.videoDetails?.videoId ?? null,
        playability:
            player.playabilityStatus?.status ?? null,
        sabrAdvertised:
            Boolean(streaming.serverAbrStreamingUrl),
        formatCount: formats.length,
        formats: formats.map(inspectFormat)
    });
})()
""".trimIndent()

