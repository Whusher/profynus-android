package com.profynus.music.youtube

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Initial inspection UI; download wiring will be a separate milestone. */
@Composable
fun YouTubeInspectScreen() {
    var input by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<YouTubeVideoMetadata?>(null) }
    var status by remember { mutableStateOf("Paste a YouTube video URL to inspect it.") }
    val scope = rememberCoroutineScope()
    val repository = remember { YouTubeMetadataRepository() }
    val openUri = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("PROFYNUS MUSIC", style = MaterialTheme.typography.headlineMedium)
        Text("YouTube URL inspector", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Video URL") },
            placeholder = { Text("https://youtu.be/M7lc1UVf-VE") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        )

        Button(
            enabled = !loading && input.isNotBlank(),
            onClick = {
                scope.launch {
                    loading = true
                    metadata = null
                    status = "Validating URL..."
                    try {
                        val reference = YouTubeUrlParser.parse(input)
                        status = "Fetching metadata for ${reference.id}..."
                        metadata = repository.fetch(reference)
                        status = "Metadata resolved. Media stream resolution is not implemented yet."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        status = "ERROR: ${error.message ?: error.javaClass.simpleName}"
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Inspecting..." else "Analyze video")
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodyMedium)

        metadata?.let { item ->
            HorizontalDivider()
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Text("Channel: ${item.channelName}")
            Text("Video ID: ${item.video.id}")
            if (item.thumbnailUrl.isNotBlank()) Text("Thumbnail: ${item.thumbnailUrl}")
            OutlinedButton(onClick = { openUri.openUri(item.video.watchUrl) }) {
                Text("Open video in YouTube")
            }
            Text(
                "This confirms metadata lookup only; it does not supply an MP4/MP3 file or a downloadable stream.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
