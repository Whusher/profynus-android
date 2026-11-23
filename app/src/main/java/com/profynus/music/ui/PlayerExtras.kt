@file:OptIn(ExperimentalMaterial3Api::class)
package com.profynus.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.profynus.music.playback.*

@Composable
internal fun PlayerExtras(state: PlaybackSnapshot, playback: PlaybackConnection, onTick: () -> Unit) {
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val enabled = playback.isConnected && state.queueSize > 0
    val repeatLabel = when (state.repeatMode) {
        Player.REPEAT_MODE_ALL -> "Repeat all"
        Player.REPEAT_MODE_ONE -> "Repeat one"
        else -> "Repeat off"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconToggleButton(checked = state.shuffleEnabled, enabled = enabled,
                onCheckedChange = { onTick(); playback.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, if (state.shuffleEnabled) "Shuffle on" else "Shuffle off")
            }
            Text(if (state.shuffleEnabled) "Shuffle on" else "Shuffle off", style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(enabled = enabled, onClick = { onTick(); playback.cycleRepeat() },
                modifier = Modifier.semantics { stateDescription = repeatLabel }) {
                Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    repeatLabel, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(repeatLabel, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(enabled = enabled, onClick = { onTick(); showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Open queue") }
            Text("Queue • ${state.queueSize}", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (showQueue) QueueSheet(state, playback, onDismiss = { showQueue = false })
}

@Composable
private fun QueueSheet(state: PlaybackSnapshot, playback: PlaybackConnection, onDismiss: () -> Unit) {
    val queue = playback.queue
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.currentIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.headlineSmall)
                    Text("${queue.size} songs", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close queue") }
            }
            if (state.shuffleEnabled) Text("Shuffle is on. Songs are listed in their original order.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (queue.isEmpty()) Text("Your queue is empty. Add songs from the library.", Modifier.padding(20.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                items(queue, key = { "${it.index}:${it.mediaId}" }) { entry ->
                    val current = entry.index == state.currentIndex
                    Row(Modifier.fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = playback.isConnected) { playback.skipTo(entry.index) }
                        .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(entry.artworkUri, null, Modifier.size(44.dp),
                            fallback = { ArtTile(seed = entry.title, size = 44.dp, active = current) })
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (current) "Now playing • ${entry.artist}" else entry.artist,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(enabled = playback.isConnected, onClick = { playback.removeFromQueue(entry.index) }) {
                            Icon(Icons.Default.Close, "Remove ${entry.title} from queue")
                        }
                    }
                }
            }
        }
    }
}
