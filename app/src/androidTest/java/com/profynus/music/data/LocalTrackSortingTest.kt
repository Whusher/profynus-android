package com.profynus.music.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTrackSortingTest {
    private fun track(id: Long, name: String, duration: Long, modified: Long) = LocalTrack(
        id = id,
        title = name,
        artist = "Artist",
        album = "Album",
        uri = Uri.parse("content://media/external/audio/media/$id"),
        durationMs = duration,
        mimeType = "audio/mpeg",
        modifiedAtEpochSeconds = modified,
    )

    private val songs = listOf(
        track(1, "Zulu", duration = 40_000, modified = 100),
        track(2, "alpha", duration = 90_000, modified = 300),
        track(3, "Bravo", duration = 60_000, modified = 200),
    )

    @Test fun nameAscending() {
        assertEquals(listOf(2L, 3L, 1L),
            sortLocalTracks(songs, TrackSortField.NAME, SortDirection.ASCENDING).map { it.id })
    }

    @Test fun nameDescending() {
        assertEquals(listOf(1L, 3L, 2L),
            sortLocalTracks(songs, TrackSortField.NAME, SortDirection.DESCENDING).map { it.id })
    }

    @Test fun modificationDateAscendingAndDescending() {
        assertEquals(listOf(1L, 3L, 2L),
            sortLocalTracks(songs, TrackSortField.DATE_MODIFIED, SortDirection.ASCENDING).map { it.id })
        assertEquals(listOf(2L, 3L, 1L),
            sortLocalTracks(songs, TrackSortField.DATE_MODIFIED, SortDirection.DESCENDING).map { it.id })
    }

    @Test fun durationAscendingAndDescending() {
        assertEquals(listOf(1L, 3L, 2L),
            sortLocalTracks(songs, TrackSortField.DURATION, SortDirection.ASCENDING).map { it.id })
        assertEquals(listOf(2L, 3L, 1L),
            sortLocalTracks(songs, TrackSortField.DURATION, SortDirection.DESCENDING).map { it.id })
    }

    @Test fun strictDurationBoundaryConstant() {
        assertEquals(30_000L, LocalMusicRepository.MINIMUM_TRACK_DURATION_MS)
        assertTrue(!isEligibleMusicDuration(29_999L))
        assertTrue(!isEligibleMusicDuration(30_000L))
        assertTrue(isEligibleMusicDuration(30_001L))
    }
}
