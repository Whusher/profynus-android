package com.profynus.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore URIs, never filesystem paths, are passed to ExoPlayer. */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val durationMs: Long,
    val mimeType: String?,
    // MediaStore.DATE_MODIFIED is stored in seconds, not milliseconds.
    val modifiedAtEpochSeconds: Long = 0L,
    val artworkUri: Uri? = null,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setExtras(Bundle().apply {
                    putString(TRACK_URI, uri.toString())
                    putString(TRACK_ARTWORK_URI, artworkUri?.toString())
                })
                .build()
        ).build()
}

internal const val TRACK_URI = "com.profynus.music.TRACK_URI"
internal const val TRACK_ARTWORK_URI = "com.profynus.music.TRACK_ARTWORK_URI"

enum class TrackSortField(val label: String) {
    NAME("Name"),
    DATE_MODIFIED("Date modified"),
    DURATION("Duration"),
}

enum class SortDirection { ASCENDING, DESCENDING }

/** Pure sorting step: keeps changing sort options independent of MediaStore and playback. */
fun sortLocalTracks(
    tracks: List<LocalTrack>,
    field: TrackSortField,
    direction: SortDirection,
): List<LocalTrack> {
    return tracks.sortedWith { left, right ->
        val first = if (direction == SortDirection.ASCENDING) left else right
        val second = if (direction == SortDirection.ASCENDING) right else left
        val primary = when (field) {
            TrackSortField.NAME -> first.title.compareTo(second.title, ignoreCase = true)
            TrackSortField.DATE_MODIFIED ->
                first.modifiedAtEpochSeconds.compareTo(second.modifiedAtEpochSeconds)
            TrackSortField.DURATION -> first.durationMs.compareTo(second.durationMs)
        }
        if (primary != 0) primary
        else {
            // A consistent secondary sort prevents ties jumping around.
            val byName = left.title.compareTo(right.title, ignoreCase = true)
            if (byName != 0) byName else left.id.compareTo(right.id)
        }
    }
}

/** Strictly greater than 60 seconds; exactly 60,000 ms does not qualify. */
fun isEligibleMusicDuration(durationMs: Long): Boolean =
    durationMs > LocalMusicRepository.MINIMUM_TRACK_DURATION_MS

/** Excludes WhatsApp AUD-* files, regardless of duration. */
fun isExcludedAudioFile(fileName: String?): Boolean =
    fileName?.startsWith(
        prefix = "AUD-",
        ignoreCase = true
    ) == true


class LocalMusicRepository(private val context: Context) {
    companion object {
        const val MINIMUM_TRACK_DURATION_MS = 60_000L
    }

    suspend fun loadTracks(): List<LocalTrack> = withContext(Dispatchers.IO) {

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )

        val selection = """
        ${MediaStore.Audio.Media.IS_MUSIC} != 0
        AND ${MediaStore.Audio.Media.DURATION} > ?
        AND (
            ${MediaStore.MediaColumns.DISPLAY_NAME} IS NULL
            OR ${MediaStore.MediaColumns.DISPLAY_NAME} NOT LIKE ?
        )
    """.trimIndent()

        val selectionArgs = arrayOf(
            MINIMUM_TRACK_DURATION_MS.toString(),
            "AUD-%"
        )

        val tracks = mutableListOf<LocalTrack>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )

            val fileNameColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.DISPLAY_NAME
            )

            val titleColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )

            val artistColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            val albumColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM
            )

            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            val durationColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION
            )

            val mimeColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.MIME_TYPE
            )

            val modifiedColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            while (cursor.moveToNext()) {

                val fileName = cursor.getString(fileNameColumn)
                val durationMs = cursor.getLong(durationColumn)

                // Mandatory exclusion of WhatsApp AUD-* filenames.
                if (isExcludedAudioFile(fileName)) continue

                // Mandatory minimum duration.
                if (!isEligibleMusicDuration(durationMs)) continue

                val id = cursor.getLong(idColumn)

                tracks += LocalTrack(
                    id = id,
                    title = cursor.getString(titleColumn)
                        .orEmpty()
                        .ifBlank { "Unknown title" },

                    artist = cursor.getString(artistColumn)
                        .orEmpty()
                        .takeUnless {
                            it.isBlank() ||
                                    it.equals("<unknown>", ignoreCase = true)
                        } ?: "Unknown artist",

                    album = cursor.getString(albumColumn)
                        .orEmpty(),

                    uri = ContentUris.withAppendedId(collection, id),

                    durationMs = durationMs,

                    mimeType = cursor.getString(mimeColumn),
                    artworkUri = if (cursor.getString(pathColumn)?.startsWith("Music/Profynus/") == true) {
                        ContentUris.withAppendedId(collection, id)
                    } else cursor.getLong(albumIdColumn).takeIf { it > 0 }?.let {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it)
                    },

                    modifiedAtEpochSeconds =
                        cursor.getLong(modifiedColumn),
                )
            }
        }

        tracks
    }
}
