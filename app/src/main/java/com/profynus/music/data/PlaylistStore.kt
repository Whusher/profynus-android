package com.profynus.music.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.playlistData by preferencesDataStore("playlists")
data class LocalPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val songs: List<String> = emptyList(),
    val artwork: String? = null,
)
class PlaylistStore(context: Context) {
    private val context = context.applicationContext
    private val store = this.context.playlistData
    val playlists = store.data.map { prefs ->
        prefs.asMap().values.filterIsInstance<String>().mapNotNull { value ->
            runCatching {
                val json = JSONObject(value)
                val songs = json.getJSONArray("songs")
                LocalPlaylist(json.getString("id"), json.getString("name"),
                    (0 until songs.length()).map(songs::getString),
                    json.optString("artwork").takeIf(String::isNotEmpty))
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }
    suspend fun save(playlist: LocalPlaylist, image: Uri?) = withContext(Dispatchers.IO) {
        require(playlist.name.isNotBlank()) { "Enter a playlist name." }
        var imported: File? = null
        var committed = false
        try {
            if (image != null) {
                val bitmap = decodeArtwork(context, image, 1024)
                try {
                    val dir = File(context.filesDir, "playlist_artwork")
                    check(dir.exists() || dir.mkdirs())
                    imported = File(dir, "${UUID.randomUUID()}.jpg")
                    imported!!.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                } finally { bitmap.recycle() }
            }
            val artwork = imported?.let { Uri.fromFile(it).toString() } ?: playlist.artwork
            withContext(NonCancellable) {
                store.edit { prefs ->
                    val key = stringPreferencesKey(playlist.id)
                    val previous = prefs[key]?.let { JSONObject(it).optString("artwork") }
                    prefs[key] = JSONObject().put("id", playlist.id).put("name", playlist.name.trim())
                        .put("songs", JSONArray(playlist.songs.distinct())).put("artwork", artwork.orEmpty()).toString()
                    committed = true
                    if (previous != artwork) deleteArtwork(previous)
                }
            }
        } finally { if (!committed) imported?.delete() }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        store.edit { prefs ->
            val old = prefs.remove(stringPreferencesKey(id))
            old?.let { deleteArtwork(JSONObject(it).optString("artwork")) }
        }
    }
    private fun deleteArtwork(uri: String?) {
        val file = uri?.let(Uri::parse)?.path?.let(::File) ?: return
        if (file.parentFile == File(context.filesDir, "playlist_artwork")) file.delete()
    }
}
