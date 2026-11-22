package com.profynus.music.youtube

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.profynus.music.media.MediaFormatClassifier
import com.profynus.music.ui.EmptyState
import com.profynus.music.ui.Panel
import com.profynus.music.ui.SectionLabel
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Downloader: paste a YouTube link, open it in the embedded browser (sign in there if YouTube
 * asks), then export the audio as MP3. Credentials remain in the WebView.
 * Nothing is loaded until the user taps "Open video".
 */
@Composable
fun YouTubeBrowserDiagnosticScreen() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var userUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a YouTube link to begin.") }
    var diagnostics by remember { mutableStateOf("Not inspected") }
    var showAdvanced by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var navigationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val export = remember { BrowserAudioExport(context, scope) }

    // Null when the field is empty or not a recognizable YouTube link.
    val watchUrl: String? = remember(userUrl) {
        if (userUrl.isBlank()) null else try {
            YouTubeUrlParser.parse(userUrl).watchUrl
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    val urlInvalid = userUrl.isNotBlank() && watchUrl == null

    // The WebView is created only after the first "Open video"; its first load happens here.
    LaunchedEffect(browser, pendingUrl) {
        val webView = browser ?: return@LaunchedEffect
        val url = pendingUrl ?: return@LaunchedEffect
        pendingUrl = null
        webView.loadUrl(url)
    }

    DisposableEffect(Unit) {
        onDispose {
            export.dispose()
            browser?.stopLoading()
            browser?.destroy()
            browser = null
        }
    }

    fun openVideo() {
        val target = watchUrl
        if (target == null) {
            status = "That doesn't look like a YouTube link."
            return
        }
        diagnostics = "Not inspected"
        navigationError = null
        val webView = browser
        if (webView == null) {
            pendingUrl = target
            showBrowser = true
            status = "Starting browser…"
        } else {
            status = "Opening YouTube…"
            webView.loadUrl(target)
        }
    }

    fun inspectFormats() {
        val webView = browser
        if (webView == null) {
            diagnostics = "Open a video first."
            return
        }
        webView.evaluateJavascript(PAGE_PROBE_JS) { raw ->
            try {
                val decoded = JSONTokener(raw).nextValue() as? String
                    ?: error("Invalid player response")
                val report = JSONObject(decoded)
                // Convert JSON into strongly typed models, classify, then display the summary.
                val formats = YouTubeFormatInventory.parse(report)
                val summary = MediaFormatClassifier.summarize(formats)
                diagnostics = MediaFormatClassifier.description(summary)
                // Log only the sanitized summary.
                Log.i("ProfynusCatalog", diagnostics)
            } catch (e: Exception) {
                diagnostics = "Inspection error: ${e.message}"
                Log.e("ProfynusProbe", "Format inspection failed", e)
            }
        }
    }

    // The outer Column must NOT scroll: it contains an independently scrolling WebView.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Panel {
            SectionLabel("1 · Paste a link")
            OutlinedTextField(
                value = userUrl,
                onValueChange = { userUrl = it },
                enabled = !export.busy,
                placeholder = { Text("youtube.com/watch?v=…") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon = {
                    if (userUrl.isEmpty()) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val pasted = clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!pasted.isNullOrBlank()) userUrl = pasted.trim()
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    } else {
                        IconButton(onClick = { userUrl = "" }, enabled = !export.busy) {
                            Icon(Icons.Default.Close, contentDescription = "Clear link")
                        }
                    }
                },
                isError = urlInvalid,
                supportingText = if (urlInvalid) {
                    { Text("This is not a valid YouTube link.") }
                } else null,
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openVideo() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedLeadingIconColor = colors.primary,
                    cursorColor = colors.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { openVideo() },
                enabled = watchUrl != null && !export.busy,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (browser == null) "Open video" else "Reload with this link")
            }
        }

        Panel {
            SectionLabel("2 · Export audio")
            Text(
                if (browser == null) {
                    "Open the video first. Export unlocks once the page has loaded."
                } else {
                    "Saves the audio of the open video as an MP3 file."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadMp3Button(enabled = browser != null && !export.busy && watchUrl != null) {
                    export.start(userUrl)
                }
                if (export.busy) {
                    OutlinedButton(onClick = { export.cancel() }) { Text("Cancel") }
                }
            }
            if (export.busy) {
                LinearProgressIndicator(progress = { export.progress }, modifier = Modifier.fillMaxWidth())
            }
            if (export.report.isNotBlank()) {
                Text(
                    export.report,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Status line
        val loading = status.startsWith("Loading") || status.startsWith("Opening") || status.startsWith("Starting")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    Modifier.size(8.dp).background(
                        if (navigationError != null) colors.error else colors.primary,
                        CircleShape,
                    ),
                )
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (navigationError != null) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Browser area: empty hint until a video is opened.
        if (showBrowser) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Browser · sign in here if YouTube asks")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp)),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
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

                                            Log.d(
                                                "ProfynusNetwork",
                                                "method=${request.method} " +
                                                        "host=$host " +
                                                        "path=${uri.path} " +
                                                        "mainFrame=${request.isForMainFrame} " +
                                                        "headers=${headerNames.joinToString(",")}"
                                            )
                                        }

                                        // Return null: let the WebView handle the request normally.
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
                                        status = "Loading ${Uri.parse(url.orEmpty()).host.orEmpty()}…"
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
                        },
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Public,
                    title = "No video open",
                    body = "Paste a link above and tap Open video. The browser appears here so you can sign in if YouTube asks.",
                )
            }
        }

        // Advanced: format inspection, collapsed by default.
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Advanced diagnostics", Modifier.weight(1f))
                Icon(
                    if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showAdvanced) "Collapse" else "Expand",
                    tint = colors.onSurfaceVariant,
                )
            }
            if (showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { inspectFormats() }, enabled = browser != null && !export.busy) {
                        Text("Inspect formats")
                    }
                    // Only the diagnostics text scrolls, never the WebView and controls together.
                    Text(
                        diagnostics,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
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