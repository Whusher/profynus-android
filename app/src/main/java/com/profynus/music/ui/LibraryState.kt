package com.profynus.music.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.profynus.music.data.*
import kotlinx.coroutines.CancellationException

internal enum class LibraryTab(val title: String) { SONGS("Songs"), PLAYLISTS("Playlists") }

internal class LibraryState(
    private val repository: LocalMusicRepository,
    val songsListState: LazyListState,
    val playlistsListState: LazyListState,
) {
    var tab by mutableStateOf(LibraryTab.SONGS)
    var query by mutableStateOf("")
    var sortField by mutableStateOf(TrackSortField.NAME)
    var sortDirection by mutableStateOf(SortDirection.ASCENDING)
    var openPlaylistId by mutableStateOf<String?>(null)
    var tracks by mutableStateOf(emptyList<LocalTrack>())
        private set
    var busy by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastListKey: Triple<TrackSortField, SortDirection, String>? = null
    fun selectSort(field: TrackSortField) {
        if (field == sortField) sortDirection = if (sortDirection == SortDirection.ASCENDING)
            SortDirection.DESCENDING else SortDirection.ASCENDING
        else sortField = field
    }
    suspend fun reload() {
        if (busy) return
        busy = true
        error = null
        try { tracks = repository.loadTracks(); loaded = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage ?: "Unable to load music" }
        finally { busy = false }
    }
}

@Composable
internal fun rememberLibraryState(context: Context): LibraryState {
    val songs = rememberLazyListState()
    val playlists = rememberLazyListState()
    val repository = remember(context) { LocalMusicRepository(context.applicationContext) }
    return rememberSaveable(saver = listSaver(
        save = { listOf(it.tab.name, it.query, it.sortField.name, it.sortDirection.name, it.openPlaylistId.orEmpty()) },
        restore = { values -> LibraryState(repository, songs, playlists).apply {
            tab = LibraryTab.valueOf(values[0]); query = values[1]
            sortField = TrackSortField.valueOf(values[2]); sortDirection = SortDirection.valueOf(values[3])
            openPlaylistId = values[4].ifEmpty { null }
            lastListKey = Triple(sortField, sortDirection, query)
        } },
    )) { LibraryState(repository, songs, playlists) }
}
