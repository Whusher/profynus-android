package com.profynus.music.youtube

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.profynus.music.media.BrowserAudioConverter
import com.profynus.music.media.ExportProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One foreground export. Only bounded media bytes enter native code, never browser credentials. */
internal class BrowserAudioExport(
    context: Context,
    private val scope: CoroutineScope
) {
    private val context = context.applicationContext
    var busy by mutableStateOf(false)
        private set
    var report by mutableStateOf("Export audio through the browser to Music/Profynus.")
        private set
    var progress by mutableStateOf(0f)
        private set
    private var view: WebView? = null
    private var script: ScriptHandler? = null
    private var job: Job? = null
    private var messages: Channel<Message>? = null
    private var token: String? = null
    private var expectedVideoId: String? = null
    private var pageStarted = false
    private var startedAt = 0L
    private var supported = false

    private data class Message(val json: JSONObject, val reply: JavaScriptReplyProxy)

    fun attach(webView: WebView) {
        view = webView
        supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (!supported) {
            report = "Update Android System WebView to enable browser audio export."
            return
        }
        WebViewCompat.addWebMessageListener(webView, "ProfynusAudio", ORIGINS) {
                _, message, origin, mainFrame, reply ->
            if (!mainFrame || origin.toString() !in ORIGINS || !busy) return@addWebMessageListener
            val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
            if (raw.length > 70000) { cancel("Oversized audio message; export stopped."); return@addWebMessageListener }
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
            if (json.optString("token") != token) return@addWebMessageListener
            if (messages?.trySend(Message(json, reply))?.isSuccess != true) {
                cancel("Audio transfer queue overflowed; retry the export.")
            }
        }
    }

    fun start(url: String) {
        if (busy) return
        val webView = view ?: return
        if (!supported) return
        val reference = try { YouTubeUrlParser.parse(url) } catch (e: IllegalArgumentException) {
            report = e.message ?: "Invalid video URL"; return
        }
        val id = Uri.parse(reference.watchUrl).getQueryParameter("v") ?: return
        val session = UUID.randomUUID().toString()
        val channel = Channel<Message>(8)
        token = session
        expectedVideoId = id
        messages = channel
        pageStarted = false
        progress = 0f
        busy = true
        startedAt = SystemClock.elapsedRealtime()
        report = "Opening browser audio capture…"
        webView.keepScreenOn = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        job = scope.launch {
            try {
                ExportProgress.start(context, session) { reason -> cancel(reason) }
                val source = withContext(Dispatchers.IO) {
                    context.assets.open("browser-audio-export.js").bufferedReader().use { it.readText() }
                }
                ensureActive()
                val config = JSONObject().put("token", session).put("videoId", id).toString()
                script = WebViewCompat.addDocumentStartJavaScript(
                    webView, source.replace("__EXPORT_CONFIG__", config), ORIGINS
                )
                webView.loadUrl("https://m.youtube.com/watch?v=$id")
                withTimeout(20 * 60 * 1000L) { receiveAudio(channel, session, id) }
            } catch (_: TimeoutCancellationException) {
                report = "Export timed out; no partial MP3 was saved."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Browser and codec exceptions can include URLs; keep logs sanitized.
                Log.e("ProfynusExport", "Export failed: ${e.javaClass.simpleName}")
                report = if (e is IllegalStateException || e is IllegalArgumentException)
                    e.message?.take(180) ?: "Audio export failed" else
                    "Audio export failed (${e.javaClass.simpleName}). Retry the export."
            } finally {
                channel.close()
                if (token == session) {
                    stopBrowser()
                    messages = null
                    token = null
                    busy = false
                    ExportProgress.finish(session, report)
                }
            }
        }
    }

    fun onPageStarted(url: String?) {
        if (!busy) return
        val uri = Uri.parse(url.orEmpty())
        if (uri.scheme != "https" || "https://${uri.host}" !in ORIGINS ||
            uri.path != "/watch" || uri.getQueryParameter("v") != expectedVideoId) {
            cancel("Navigation changed; export cancelled.")
        } else if (pageStarted) {
            cancel("Page reloaded; start a new export.")
        }
    }

    fun cancel(reason: String = "Export cancelled. Partial files discarded.") {
        if (!busy) return
        report = reason
        job?.cancel()
        stopBrowser()
        // Keep busy until the worker's finally block has removed its temporary files.
    }

    fun dispose() {
        cancel()
        if (supported) view?.let { WebViewCompat.removeWebMessageListener(it, "ProfynusAudio") }
        view = null
    }

    private fun stopBrowser(keepAwake: Boolean = false) {
        script?.remove()
        script = null
        view?.apply {
            evaluateJavascript("window.__profynusExport && window.__profynusExport.stop()", null)
            settings.mediaPlaybackRequiresUserGesture = true
            keepScreenOn = keepAwake
        }
    }

    private suspend fun receiveAudio(channel: Channel<Message>, session: String, id: String) =
        withContext(Dispatchers.IO) {
            val input = File(context.cacheDir, "audio-$session.capture")
            val output = File(context.cacheDir, "audio-$session.mp3")
            var bytes = 0L
            var sequence = 0
            var begun = false
            var expectedDuration = 0.0
            var completion: JSONObject? = null
            try {
                input.outputStream().buffered().use { stream ->
                    while (completion == null) {
                        val event = withTimeout(100000) { channel.receive() }
                        val json = event.json
                        when (json.getString("type")) {
                            "ready" -> withContext(Dispatchers.Main) {
                                report = "Waiting for the browser's audio stream…"
                                ExportProgress.update(session, report)
                            }
                            "begin" -> {
                                check(!begun) { "Audio stream restarted" }
                                val mime = json.getString("mime").substringBefore(';')
                                check(mime in setOf("audio/webm", "audio/mp4")) { "Unsupported audio container" }
                                expectedDuration = json.getDouble("duration")
                                check(expectedDuration.isFinite() && expectedDuration in 0.1..10800.0) {
                                    "Unsupported video duration"
                                }
                                begun = true
                                withContext(Dispatchers.Main) { pageStarted = true }
                                Log.i("ProfynusExport", "Capturing $mime, duration=${expectedDuration.toInt()}s")
                            }
                            "chunk" -> {
                                check(begun && json.getInt("sequence") == sequence) { "Audio chunk is out of order" }
                                val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)
                                check(data.isNotEmpty() && data.size <= 48 * 1024) { "Invalid audio chunk size" }
                                bytes += data.size
                                check(bytes <= 250L * 1024 * 1024) { "Audio exceeds the 250 MiB export limit" }
                                stream.write(data)
                                val acknowledgement = JSONObject().put("token", session).put("sequence", sequence++)
                                withContext(Dispatchers.Main) { event.reply.postMessage(acknowledgement.toString()) }
                            }
                            "progress" -> {
                                val covered = json.optDouble("covered", 0.0)
                                if (begun && covered.isFinite()) withContext(Dispatchers.Main) {
                                    progress = (covered / expectedDuration).toFloat().coerceIn(0f, 1f)
                                    report = "Downloading audio: ${covered.toInt()} / ${expectedDuration.toInt()} seconds"
                                    ExportProgress.update(session, report, (progress * 100).toInt())
                                }
                            }
                            "finish" -> {
                                check(begun && bytes > 0 && json.getInt("chunks") == sequence) {
                                    "Audio capture is incomplete"
                                }
                                check(kotlin.math.abs(json.getDouble("duration") - expectedDuration) < 0.1) {
                                    "Video duration changed"
                                }
                                completion = json
                            }
                            "error" -> error(json.optString("reason", "Browser audio capture failed").take(180))
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    stopBrowser(keepAwake = true)
                    report = "Verifying audio and encoding MP3…"
                    progress = 0f
                    ExportProgress.update(session, report, 0)
                }
                val conversionStarted = SystemClock.elapsedRealtime()
                BrowserAudioConverter.convert(input, output, expectedDuration) { percent ->
                    // At most 101 updates per export, on the Compose main dispatcher.
                    scope.launch {
                        if (token == session) {
                            report = "Encoding MP3: $percent%"
                            progress = percent / 100f
                            ExportProgress.update(session, report, percent)
                        }
                    }
                }
                val conversionMs = SystemClock.elapsedRealtime() - conversionStarted
                Log.i("ProfynusExport", "Native conversion: ${conversionMs}ms; capture+setup: ${conversionStarted - startedAt}ms")
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) { ExportProgress.update(session, "Saving MP3…") }
                val title = completion!!.optString("title", id)
                val name = title.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").trim().take(100)
                    .ifBlank { id } + "-$id.mp3"
                save(output, name)
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000
                Log.i("ProfynusExport", "Saved verified MP3: ${output.length()} bytes, ${expectedDuration.toInt()}s, elapsed=${elapsed}s")
                withContext(Dispatchers.Main) { report = "Saved Music/Profynus/$name"; progress = 1f }
            } finally {
                input.delete()
                output.delete()
            }
        }

    private suspend fun save(file: File, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Profynus")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the MP3 in Music")
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { destination ->
                file.inputStream().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null) == 1) { "Could not publish the MP3" }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    companion object {
        private val ORIGINS = setOf("https://www.youtube.com", "https://m.youtube.com")
    }
}
