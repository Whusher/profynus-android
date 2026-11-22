package com.profynus.music.youtube

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profynus.media.transport.RangeDownloader
import com.profynus.music.media.HttpMediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager

/**
 * Developer-only control. Enable full download ONLY for media you are authorized to save.
 * Does not bypass signed streams, DRM, SABR, or playback authorization.
 */
@Composable
fun DirectSourceTestControl(webView: WebView?) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("Direct source not tested") }
    var progress by remember { mutableStateOf("") }

    fun startTest(download: Boolean) {
        val view = webView ?: run { report = "Open YouTube in WebView first"; return }
        busy = true
        progress = ""
        report = "Checking direct itag 18 candidate..."
        DirectMediaCandidateInspector.inspect(view) { result ->
            result.fold(
                onSuccess = { candidate ->


                    val expirySeconds = runCatching {
                        Uri.parse(candidate.url.toExternalForm())
                            .getQueryParameter("expire")
                            ?.toLongOrNull()
                    }.getOrNull()

                    val remainingSeconds = expirySeconds?.minus(
                        System.currentTimeMillis() / 1000L
                    )

                    Log.i(
                        "ProfynusHTTP",
                        "Candidate: itag=${candidate.itag}, " +
                                "reportedBytes=${candidate.reportedBytes}, " +
                                "expiresInSeconds=${remainingSeconds ?: "unknown"}"
                    )

                    // session debug

                    val matchingCookiesPresent = CookieManager
                        .getInstance()
                        .getCookie(candidate.url.toExternalForm())
                        ?.isNotBlank() == true

                    Log.i(
                        "ProfynusHTTP",
                        "Matching media-host cookies present: " +
                                matchingCookiesPresent
                    )


                    scope.launch {
                        try {
                            val probe = withContext(Dispatchers.IO) { HttpMediaProbe.inspect(candidate.url) }
                            // Probe evaluation
                            if (probe.statusCode == 403) {
                                report =
                                    "HTTP 403: Native media access denied.\n" +
                                            "The URL has not expired, but this request " +
                                            "was not authorized by the source.\n" +
                                            "Range support: not established.\n" +
                                            "Transfer cancelled."

                                return@launch
                            }



                            report = probe.summary()


                            Log.i(
                                "ProfynusHTTP",
                                "Probe: status=${probe.statusCode}, " +
                                        "mime=${probe.contentType}, " +
                                        "rangeSupported=${probe.rangeSupported}, " +
                                        "totalBytes=${probe.totalBytes}"
                            )

                            if (probe.statusCode == 403) {
                                report += "\nServer denied independent media access."
                                report += "\nPlayback availability does not imply download access."
                                report += "\nNo transfer attempted."
                            }



                            val allowedMime = probe.contentType in setOf("video/mp4", "application/octet-stream")
                            val size = probe.totalBytes
                            if (download) {
                                if (probe.statusCode != 206 || !probe.rangeSupported || !allowedMime ||
                                    size == null || size > 250L * 1024 * 1024) {
                                    report += "\nDownload not attempted: unsupported source response or size."
                                } else if (candidate.reportedBytes != null && candidate.reportedBytes != size) {
                                    report += "\nDownload not attempted: content length differs from advertised."
                                } else {
                                    val output = File(context.cacheDir, "profynus-itag18-diagnostic.mp4")
                                    val lastUpdate = AtomicLong(0)
                                    val started = SystemClock.elapsedRealtime()
                                    val completed = withContext(Dispatchers.IO) {
                                        RangeDownloader(workers = 4, chunkBytes = 4L * 1024 * 1024)
                                            .download(candidate.url, output) { p ->
                                                val now = SystemClock.elapsedRealtime()
                                                val previous = lastUpdate.get()
                                                if (now - previous >= 250 && lastUpdate.compareAndSet(previous, now)) {
                                                    main.post { progress = "${p.transferred} / ${p.total ?: size} bytes" }
                                                }
                                            }
                                    }
                                    val seconds = ((SystemClock.elapsedRealtime() - started) / 1000.0).coerceAtLeast(0.001)
                                    val mibPerSec = completed.bytes / (1024.0 * 1024.0 * seconds)
                                    report = "Downloaded ${completed.bytes} bytes to app cache. " +
                                        "Ranges=${completed.rangesUsed}; avg %.2f MiB/s. ".format(mibPerSec) +
                                        "Container and media tracks NOT YET verified."
                                }
                            }
                        } catch (e: Exception) {
                            // Do not print exception messages; some HTTP error chains contain signed URLs.
                            report = "Transport error: ${e::class.java.simpleName} (URL redacted)"
                        } finally {
                            busy = false
                        }
                    }
                },
                onFailure = { ex ->
                    report = "Candidate unavailable: ${ex::class.java.simpleName}"
                    busy = false
                }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Source access diagnostics", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { startTest(false) }, enabled = !busy) { Text("Probe HTTP") }
            Button(onClick = { startTest(true) }, enabled = !busy) { Text("Test transfer") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (progress.isNotEmpty()) Text(progress, style = MaterialTheme.typography.bodySmall)
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}
