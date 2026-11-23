@file:OptIn(ExperimentalMaterial3Api::class)
package com.profynus.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.profynus.music.data.*
import com.profynus.music.playback.PlaybackConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun LibraryScreen(library: LibraryState, playlistStore: PlaylistStore, playback: PlaybackConnection,
    edits: Map<String, TrackDetails>, onEdit: (TrackDetails) -> Unit, onTrackStarted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) { if (granted && !library.loaded) library.reload() }
    val playlists by playlistStore.playlists.collectAsState(initial = emptyList())
    val allTracks = remember(library.tracks, edits) { library.tracks.map { it.withDetails(edits[it.uri.toString()]) } }
    val byUri = remember(allTracks) { allTracks.associateBy { it.uri.toString() } }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var seedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var addUri by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    fun create(uri: String? = null) { seedUri = uri; creating = true; editorId = UUID.randomUUID().toString() }
    editorId?.let { id ->
        val initial = if (creating) LocalPlaylist(id = id, songs = listOfNotNull(seedUri))
            else playlists.find { it.id == id }
        initial?.let { playlist ->
            key(id) {
                PlaylistEditorSheet(playlist, creating, allTracks, library.loaded && !library.busy && library.error == null,
                    playlistStore, onDismiss = { editorId = null },
                    onDeleted = { if (library.openPlaylistId == id) library.openPlaylistId = null })
            }
        }
    }
    addUri?.let(byUri::get)?.let { track ->
        AddToPlaylistDialog(track, playlists, playlistStore,
            onCreate = { addUri = null; create(track.uri.toString()) }, onDismiss = { addUri = null })
    }
    val displayed = remember(allTracks, library.query, library.sortField, library.sortDirection) {
        val needle = library.query.trim()
        sortLocalTracks(allTracks.filter { it.title.contains(needle, true) || it.artist.contains(needle, true) ||
            it.album.contains(needle, true) }, library.sortField, library.sortDirection)
    }
    val listKey = Triple(library.sortField, library.sortDirection, library.query)
    LaunchedEffect(listKey) {
        if (library.lastListKey != listKey) { library.songsListState.scrollToItem(0); library.lastListKey = listKey }
    }
    val nowPlaying by remember(playback) { derivedStateOf { playback.snapshot.mediaId } }
    val open = playlists.find { it.id == library.openPlaylistId }
    BackHandler(enabled = library.tab == LibraryTab.PLAYLISTS && open != null && editorId == null && addUri == null) {
        library.openPlaylistId = null
    }
    fun play(tracks: List<LocalTrack>, index: Int = 0, shuffle: Boolean = false) {
        if (shuffle) playback.shuffleAll(tracks) else playback.play(tracks, index)
        onTrackStarted()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your library", style = MaterialTheme.typography.headlineMedium)
                Text("${allTracks.size} songs • ${playlists.size} playlists", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(enabled = granted && !library.busy, onClick = { scope.launch { library.reload() } }) {
                if (library.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh music library")
            }
        }
        if (!granted) {
            Text("Allow audio access to browse the songs on your device.", Modifier.padding(vertical = 20.dp))
            Button(onClick = { launcher.launch(permission) }) { Text("Allow audio access") }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(library.tab == tab, { library.tab = tab }, label = { Text(tab.title) },
                    modifier = Modifier.weight(1f))
            }
        }
        library.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!playback.isConnected) Text("Connecting to playback…", style = MaterialTheme.typography.bodySmall)
        if (library.tab == LibraryTab.SONGS) {
            OutlinedTextField(library.query, { library.query = it }, placeholder = { Text("Search songs, artists, albums") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (library.query.isNotEmpty()) IconButton(onClick = { library.query = "" }) {
                    Icon(Icons.Default.Close, "Clear search")
                } }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LibraryPlayActions(displayed.isNotEmpty() && playback.isConnected,
                onPlay = { play(displayed) }, onShuffle = { play(displayed, shuffle = true) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TrackSortField.entries.toList()) { field ->
                    val selected = library.sortField == field
                    FilterChip(selected, { library.selectSort(field) }, label = { Text(field.label) },
                        trailingIcon = if (selected) { {
                            Icon(if (library.sortDirection == SortDirection.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                if (library.sortDirection == SortDirection.ASCENDING) "Ascending; tap to reverse" else "Descending; tap to reverse",
                                Modifier.size(18.dp))
                        } } else null)
                }
            }
            if (displayed.isEmpty()) Text(if (library.busy) "Loading songs…" else if (library.query.isBlank())
                "No songs found. Add music to your device and refresh." else "No matching songs.", Modifier.padding(16.dp))
            LazyColumn(state = library.songsListState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(displayed, key = { _, it -> it.uri.toString() }) { index, track ->
                    LibraryTrackRow(track, track.id.toString() == nowPlaying, playback.isConnected,
                        onPlay = { play(displayed, index) },
                        onQueue = { if (playback.addToQueue(track)) Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show() },
                        onPlaylist = { addUri = track.uri.toString() }, onEdit = { onEdit(track.details()) },
                        showModified = library.sortField == TrackSortField.DATE_MODIFIED)
                }
            }
        } else if (open == null) {
            FilledTonalButton(onClick = { create() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New playlist")
            }
            if (playlists.isEmpty()) Text("Create a playlist, choose its songs, and make it yours with a cover.",
                Modifier.padding(vertical = 20.dp))
            LazyColumn(state = library.playlistsListState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    val tracks = playlist.songs.mapNotNull(byUri::get)
                    ListItem(modifier = Modifier.clickable { library.openPlaylistId = playlist.id },
                        headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${playlist.songs.size} songs") },
                        leadingContent = { TrackArtwork(playlist.artwork, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                            fallback = { ArtTile(seed = playlist.name, size = 56.dp) }) },
                        trailingContent = { IconButton(enabled = tracks.isNotEmpty() && playback.isConnected,
                            onClick = { play(tracks) }) { Icon(Icons.Default.PlayArrow, "Play ${playlist.name}") } })
                }
            }
        } else {
            val tracks = open.songs.mapNotNull(byUri::get)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { library.openPlaylistId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to playlists") }
                Text(open.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { creating = false; editorId = open.id }) { Icon(Icons.Default.Edit, "Edit playlist") }
            }
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(open.artwork, "Playlist cover", Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)),
                            fallback = { ArtTile(seed = open.name, size = 88.dp) })
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("${tracks.size} available songs", style = MaterialTheme.typography.titleMedium)
                            if (library.loaded && !library.busy && library.error == null && tracks.size < open.songs.size)
                                Text("${open.songs.size - tracks.size} unavailable", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    LibraryPlayActions(tracks.isNotEmpty() && playback.isConnected,
                        onPlay = { play(tracks) }, onShuffle = { play(tracks, shuffle = true) })
                    TextButton(onClick = { creating = false; editorId = open.id }) { Text("Add or remove songs") }
                    if (tracks.isEmpty()) Text(if (library.busy) "Loading songs…" else "No available songs in this playlist.")
                }
                itemsIndexed(tracks, key = { _, it -> it.uri.toString() }) { index, track ->
                    LibraryTrackRow(track, track.id.toString() == nowPlaying, playback.isConnected,
                        onPlay = { play(tracks, index) },
                        onQueue = { if (playback.addToQueue(track)) Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show() },
                        onPlaylist = { addUri = track.uri.toString() }, onEdit = { onEdit(track.details()) },
                        onRemove = {
                            run {
                                actionError = null
                                scope.launch {
                                    try { playlistStore.setMembership(open.id, track.uri.toString(), false) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { actionError = e.localizedMessage ?: "Unable to remove song" }
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun LibraryPlayActions(enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(enabled = enabled, onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Play")
        }
        FilledTonalButton(enabled = enabled, onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Shuffle")
        }
    }
}

@Composable
private fun LibraryTrackRow(track: LocalTrack, current: Boolean, connected: Boolean, onPlay: () -> Unit,
    onQueue: () -> Unit, onPlaylist: () -> Unit, onEdit: () -> Unit, onRemove: (() -> Unit)? = null,
    showModified: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(enabled = connected, onClick = onPlay).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork(track.artworkUri?.toString(), null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            fallback = { ArtTile(seed = track.title, size = 48.dp, active = current) })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (current) Text("Now playing", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            if (showModified && track.modifiedAtEpochSeconds > 0) Text(
                "Modified " + java.time.Instant.ofEpochSecond(track.modifiedAtEpochSeconds).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                style = MaterialTheme.typography.labelSmall)
        }
        Text("%d:%02d".format(java.util.Locale.ROOT, track.durationMs / 60000, track.durationMs / 1000 % 60),
            style = MaterialTheme.typography.bodySmall)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options for ${track.title}") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Add to queue") }, enabled = connected, onClick = { menu = false; onQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onPlaylist() })
                DropdownMenuItem(text = { Text("Edit details") }, onClick = { menu = false; onEdit() })
                if (onRemove != null) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}
