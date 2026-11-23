@file:OptIn(ExperimentalMaterial3Api::class)
package com.profynus.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.profynus.music.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistEditorSheet(initial: LocalPlaylist, isNew: Boolean, tracks: List<LocalTrack>,
    libraryReady: Boolean, store: PlaylistStore, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val creating = remember(initial.id) { isNew }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var songs by rememberSaveable(initial.id) { mutableStateOf(ArrayList(initial.songs)) }
    var artwork by rememberSaveable(initial.id) { mutableStateOf(initial.artwork) }
    var image by rememberSaveable(initial.id) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(initial.id) { mutableStateOf("") }
    var onlySelected by rememberSaveable(initial.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dirty = name != initial.name || songs != initial.songs || artwork != initial.artwork || image != null
    fun dismiss() { if (!busy) { if (dirty) discard = true else onDismiss() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) image = it.toString() }
    val selected = remember(songs) { songs.toHashSet() }
    val available = remember(tracks) { tracks.mapTo(HashSet()) { it.uri.toString() } }
    val visible = remember(tracks, query, selected, onlySelected) {
        tracks.filter { (!onlySelected || it.uri.toString() in selected) &&
            (it.title.contains(query.trim(), true) || it.artist.contains(query.trim(), true)) }
    }
    val missing = if (libraryReady) songs.count { it !in available } else 0
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { if (it == SheetValue.Hidden) { dismiss(); false } else true })
    ModalBottomSheet(onDismissRequest = { dismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().padding(horizontal = 20.dp)) {
            Text(if (creating) "New playlist" else "Edit playlist", style = MaterialTheme.typography.headlineSmall)
            // One scrolling area keeps fields and songs reachable with a keyboard or large text.
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Playlist name") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(image ?: artwork, "Playlist cover", Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                            fallback = { ArtTile(seed = name, size = 56.dp) })
                        TextButton(enabled = !busy, onClick = { picker.launch("image/*") }) { Text("Choose picture") }
                        if (image != null || artwork != null) TextButton(enabled = !busy,
                            onClick = { image = null; artwork = null }) { Text("Remove") }
                    }
                }
                item { OutlinedTextField(query, { query = it }, label = { Text("Find songs") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!onlySelected, { onlySelected = false }, label = { Text("All songs") })
                        FilterChip(onlySelected, { onlySelected = true }, label = { Text("Selected • ${songs.size}") })
                    }
                }
                if (visible.isEmpty()) item { Text(if (!libraryReady) "Loading songs…" else "No songs match this view.") }
                items(visible, key = { it.uri.toString() }) { track ->
                    val uri = track.uri.toString()
                    Row(Modifier.fillMaxWidth().toggleable(uri in selected, enabled = !busy,
                        role = Role.Checkbox, onValueChange = {
                            songs = ArrayList(if (it) songs + uri else songs - uri)
                        }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(uri in selected, onCheckedChange = null, enabled = !busy)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (missing > 0) item {
                    TextButton(enabled = !busy, onClick = { songs = ArrayList(songs.filter { it in available }) }) {
                        Text("Remove $missing unavailable songs")
                    }
                }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!creating) TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = { dismiss() }) { Text("Cancel") }
                Button(enabled = !busy && name.isNotBlank(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { store.save(LocalPlaylist(initial.id, name, songs, artwork), image?.let(Uri::parse)); onDismiss() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.localizedMessage ?: "Unable to save playlist" }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Saving…" else "Save") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your unsaved playlist changes will be lost.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("Delete playlist?") }, text = { Text("The songs on your device will be kept.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { store.delete(initial.id); onDeleted(); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage; confirmDelete = false }
                finally { busy = false }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
internal fun AddToPlaylistDialog(track: LocalTrack, playlists: List<LocalPlaylist>, store: PlaylistStore,
    onCreate: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val uri = track.uri.toString()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Add to playlist") },
        text = {
            Column {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (playlists.isEmpty()) Text("Create your first playlist to save this song.")
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val already = uri in playlist.songs
                        TextButton(enabled = !busy && !already, onClick = {
                            busy = true; error = null
                            scope.launch {
                                try { store.setMembership(playlist.id, uri, true); onDismiss() }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.localizedMessage ?: "Unable to add song" }
                                finally { busy = false }
                            }
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (already) "Already added" else "${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) Text("Adding song…")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onCreate) { Text("New playlist") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}
