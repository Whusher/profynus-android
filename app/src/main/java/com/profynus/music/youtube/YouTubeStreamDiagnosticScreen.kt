package com.profynus.music.youtube

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profynus.music.media.ResolutionResult
import com.profynus.music.media.TrackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Debug-only UI: prints track metadata without leaking signed CDN URLs. */
@Composable
fun YouTubeStreamDiagnosticScreen() {
    var input by remember { mutableStateOf("https://www.youtube.com/watch?v=M7lc1UVf-VE") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ResolutionResult?>(null) }
    val scope = rememberCoroutineScope()
    val resolver = remember { YouTubeWatchPageResolver() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Profynus · Stream Diagnostics", style = MaterialTheme.typography.headlineSmall)
        Text("Experimental public watch-page inspection, not an official download API.")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("YouTube URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !running && input.isNotBlank(),
            onClick = {
                scope.launch {
                    running = true
                    result = null
                    try {
                        val reference = YouTubeUrlParser.parse(input)
                        result = resolver.resolve(reference.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        result = ResolutionResult.Unavailable(error.message ?: "Unknown error")
                    } finally {
                        running = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "Inspecting..." else "Inspect media formats") }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (val current = result) {
            is ResolutionResult.Unavailable -> Text("UNAVAILABLE: ${current.reason}")
            is ResolutionResult.Available -> {
                val media = current.media
                Text(media.title, style = MaterialTheme.typography.titleMedium)
                Text("Video ID: ${media.videoId} · ${media.streams.size} direct track candidates")
                media.streams.forEach { track ->
                    HorizontalDivider()
                    Text("itag ${track.id} · ${track.type}")
                    Text("${track.mimeType} · ${track.codec.orEmpty()}")
                    Text("${track.bitrate ?: "?"} bps · ${track.contentLength ?: "?"} bytes")
                    Text("Direct URL present: yes · expired: ${track.expiresAtEpochMs?.let { it <= System.currentTimeMillis() } ?: "unknown"}")
                }
                Text(
                    "NOTE: These are unverified transport candidates, not authorization to copy. " +
                    "Do not connect this screen directly to downloads."
                )
            }
            null -> Text("Paste a URL and inspect. No media is downloaded.")
        }
    }
}
