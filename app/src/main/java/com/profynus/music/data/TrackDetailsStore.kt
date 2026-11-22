package com.profynus.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.trackDetailsDataStore by preferencesDataStore(name = "profynus_track_details")

/** App-local metadata. Audio files and their embedded tags are left intact. */
data class TrackDetails(
    val mediaUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
)

class TrackDetailsStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.trackDetailsDataStore,
) {
    private val appContext = context.applicationContext

    val edits = dataStore.data.map { preferences ->
        preferences.asMap().values.filterIsInstance<String>().mapNotNull { value ->
            runCatching {
                val json = JSONObject(value)
                TrackDetails(
                    mediaUri = json.getString("uri"),
                    title = json.getString("title"),
                    artist = json.getString("artist"),
                    album = json.getString("album"),
                    artworkUri = json.optString("artwork").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }.associateBy { it.mediaUri }
    }

    suspend fun save(details: TrackDetails, selectedImage: Uri? = null) = withContext(Dispatchers.IO) {
        require(details.title.isNotBlank()) { "Enter a song name." }
        val imported = selectedImage?.let { importArtwork(it) }
        var committed = false
        var previousArt: String? = null
        try {
            val saved = details.copy(
                title = details.title.trim(),
                artist = details.artist.trim(),
                album = details.album.trim(),
                artworkUri = imported?.let { Uri.fromFile(it).toString() } ?: details.artworkUri,
            )
            val json = JSONObject().apply {
                put("uri", saved.mediaUri)
                put("title", saved.title)
                put("artist", saved.artist)
                put("album", saved.album)
                put("artwork", saved.artworkUri.orEmpty())
            }
            // Once committing starts, finish it even if the editor is recreated. Otherwise a
            // cancellation after DataStore commits could delete the newly referenced image.
            withContext(NonCancellable) {
                dataStore.edit {
                    val key = stringPreferencesKey(saved.mediaUri)
                    previousArt = it[key]?.let { value ->
                        runCatching { JSONObject(value).optString("artwork") }.getOrNull()
                    }
                    it[key] = json.toString()
                }
                committed = true
            }
            if (previousArt != saved.artworkUri) {
                previousArt?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let { path ->
                    val previousFile = File(path)
                    if (previousFile.parentFile == File(appContext.filesDir, "track_artwork")) {
                        previousFile.delete()
                    }
                }
            }
            saved
        } finally {
            if (!committed) imported?.delete()
        }
    }

    /** Decode at a bounded size and copy into app storage, independent of picker URI grants. */
    private fun importArtwork(uri: Uri): File {
        val bitmap = decodeArtwork(appContext, uri, 1024)
        val directory = File(appContext.filesDir, "track_artwork")
        check(directory.exists() || directory.mkdirs()) { "Unable to create artwork storage." }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Unable to save this image." }
            }
            return file
        } catch (exception: Exception) {
            file.delete()
            throw exception
        } finally {
            bitmap.recycle()
        }
    }
}

/** ImageDecoder also applies EXIF orientation; never decode a full-resolution camera image. */
internal fun decodeArtwork(context: Context, uri: Uri, maxSize: Int): Bitmap {
    val source = if (uri.scheme == "file") {
        ImageDecoder.createSource(File(requireNotNull(uri.path)))
    } else {
        ImageDecoder.createSource(context.contentResolver, uri)
    }
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = minOf(1f, maxSize.toFloat() / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize(
            (info.size.width * scale).toInt().coerceAtLeast(1),
            (info.size.height * scale).toInt().coerceAtLeast(1),
        )
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

fun LocalTrack.withDetails(details: TrackDetails?): LocalTrack =
    if (details == null) this else copy(
        title = details.title,
        artist = details.artist,
        album = details.album,
        artworkUri = details.artworkUri?.let(Uri::parse),
    )

fun LocalTrack.details() = TrackDetails(uri.toString(), title, artist, album, artworkUri?.toString())
