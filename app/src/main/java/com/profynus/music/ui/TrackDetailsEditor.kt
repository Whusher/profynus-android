package com.profynus.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.profynus.music.data.TrackDetails
import com.profynus.music.data.TrackDetailsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val TrackDetailsSaver = androidx.compose.runtime.saveable.listSaver<TrackDetails?, String>(
    save = { details ->
        details?.let { listOf(it.mediaUri, it.title, it.artist, it.album, it.artworkUri.orEmpty()) }
            ?: emptyList()
    },
    restore = { values ->
        if (values.size != 5) null else TrackDetails(
            values[0], values[1], values[2], values[3], values[4].takeIf { it.isNotEmpty() },
        )
    },
)

@Composable
fun TrackDetailsEditor(
    details: TrackDetails,
    store: TrackDetailsStore,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(details.mediaUri) { mutableStateOf(details.title) }
    var artist by rememberSaveable(details.mediaUri) { mutableStateOf(details.artist) }
    var album by rememberSaveable(details.mediaUri) { mutableStateOf(details.album) }
    var artwork by rememberSaveable(details.mediaUri) { mutableStateOf(details.artworkUri) }
    var selectedImage by rememberSaveable(details.mediaUri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImage = uri.toString()
            artwork = uri.toString()
            error = null
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit song details") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackArtwork(
                    artworkUri = artwork,
                    description = "Album art preview",
                    modifier = Modifier.size(128.dp).clip(RoundedCornerShape(20.dp)).align(Alignment.CenterHorizontally),
                    maxSize = 384,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(enabled = !saving, onClick = { picker.launch(arrayOf("image/*")) }) {
                        Text("Choose image")
                    }
                    if (artwork != null) {
                        TextButton(enabled = !saving, onClick = {
                            artwork = null
                            selectedImage = null
                        }) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(300) },
                    label = { Text("Song name") },
                    isError = title.isBlank(),
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it.take(300) },
                    label = { Text("Artist") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it.take(300) },
                    label = { Text("Album name") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saved in Profynus on this device.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && title.isNotBlank(),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            store.save(
                                details.copy(title = title, artist = artist, album = album, artworkUri = artwork),
                                selectedImage?.let(Uri::parse),
                            )
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Could not save. Try choosing the image again."
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}
