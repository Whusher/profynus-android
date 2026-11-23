package com.profynus.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profynus.music.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun PlaylistsDialog(tracks: List<LocalTrack>, canPlay: Boolean,
    onPlay: (List<LocalTrack>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PlaylistStore(context) }
    val playlists by store.playlists.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var songs by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var artwork by rememberSaveable { mutableStateOf<String?>(null) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        image = it?.toString()
    }
    fun edit(playlist: LocalPlaylist) {
        id = playlist.id; name = playlist.name; songs = ArrayList(playlist.songs)
        artwork = playlist.artwork; image = null; query = ""; error = null
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (id == null) "Your playlists" else "Edit playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (id == null) {
                    Button(onClick = { edit(LocalPlaylist(id = UUID.randomUUID().toString())) }) { Text("Create playlist") }
                    if (playlists.isEmpty()) Text("Create a playlist, choose its songs, and add a cover.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(Modifier.fillMaxWidth().clickable { edit(playlist) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                TrackArtwork(playlist.artwork, null, Modifier.size(48.dp),
                                    fallback = { ArtTile(seed = playlist.name, size = 48.dp) })
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(playlist.name)
                                    Text("${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall)
                                }
                                val available = playlist.songs.mapNotNull { uri -> tracks.find { it.uri.toString() == uri } }
                                TextButton(enabled = canPlay && available.isNotEmpty(),
                                    onClick = { onPlay(available); onDismiss() }) { Text("Play") }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true, enabled = !busy)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(image ?: artwork, "Playlist cover", Modifier.size(56.dp),
                            fallback = { ArtTile(seed = name, size = 56.dp) })
                        TextButton(enabled = !busy, onClick = { picker.launch("image/*") }) { Text("Choose picture") }
                    }
                    TextButton(enabled = !busy && (image != null || artwork != null),
                        onClick = { image = null; artwork = null }) { Text("Remove picture") }
                    OutlinedTextField(query, { query = it }, label = { Text("Find songs") }, singleLine = true)
                    Text("${songs.size} selected • Uncheck to remove")
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) },
                            key = { it.uri.toString() }) { track ->
                            val uri = track.uri.toString()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = uri in songs, enabled = !busy, onCheckedChange = { checked ->
                                    songs = ArrayList(if (checked) songs + uri else songs - uri)
                                })
                                Column { Text(track.title); Text(track.artist, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if (songs.any { uri -> tracks.none { it.uri.toString() == uri } }) {
                        TextButton(enabled = !busy, onClick = {
                            songs = ArrayList(songs.filter { uri -> tracks.any { it.uri.toString() == uri } })
                        }) { Text("Remove unavailable songs") }
                    }
                    TextButton(enabled = !busy, onClick = { deleting = true }) { Text("Delete playlist") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (id != null) TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        store.save(LocalPlaylist(id!!, name, songs, artwork), image?.let(Uri::parse))
                        id = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.localizedMessage ?: "Unable to save playlist" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (id == null) onDismiss() else id = null }) { Text(if (id == null) "Close" else "Back") } },
    )
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("Delete playlist?") }, text = { Text("The songs on your device will be kept.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { store.delete(id!!); deleting = false; id = null }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage; deleting = false }
                finally { busy = false }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}
