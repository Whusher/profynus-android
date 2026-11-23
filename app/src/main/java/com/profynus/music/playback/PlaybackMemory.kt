package com.profynus.music.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.profynus.music.data.TRACK_URI
import com.profynus.music.data.TRACK_ARTWORK_URI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.playbackMemory by preferencesDataStore("playback_memory")

/** Persist immutable main-thread snapshots; disk serialization stays off the player thread. */
internal class PlaybackMemory(context: Context, private val player: Player, scope: CoroutineScope,
    private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> = context.applicationContext.playbackMemory,
) {
    private val key = stringPreferencesKey("queue")
    private val writes = Channel<String>(Channel.CONFLATED)
    private var ready = false
    private var ticker: Job? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { save() }
    }
    init {
        CoroutineScope(Dispatchers.IO).launch {
            for (value in writes) {
                try { store.edit { it[key] = value } }
                catch (_: java.io.IOException) { /* Retry at the next checkpoint. */ }
            }
        }
        ticker = scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { store.data.first()[key]?.let(::JSONObject) }.getOrNull()
            }
            if (player.mediaItemCount == 0 && saved != null) {
                runCatching {
                    val queue = saved.getJSONArray("items")
                    val items = (0 until queue.length()).map { index ->
                        val item = queue.getJSONObject(index)
                        MediaItem.Builder().setMediaId(item.getString("id")).setUri(item.getString("uri"))
                            .setMediaMetadata(MediaMetadata.Builder()
                                .setTitle(item.optString("title")).setArtist(item.optString("artist"))
                                .setAlbumTitle(item.optString("album"))
                                .setArtworkUri(item.optString("art").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                                .setExtras(Bundle().apply {
                                    putString(TRACK_URI, item.getString("uri"))
                                    putString(TRACK_ARTWORK_URI, item.optString("art").takeIf { it.isNotEmpty() })
                                }).build()).build()
                    }
                    if (items.isNotEmpty()) player.setMediaItems(items,
                        saved.optInt("index").coerceIn(items.indices), saved.optLong("position").coerceAtLeast(0))
                }
            }
            ready = true
            player.addListener(listener)
            while (isActive) { save(); delay(3000) }
        }
    }
    fun release() {
        ticker?.cancel()
        save()
        player.removeListener(listener)
        writes.close()
    }

    fun save() {
        if (!ready || player.mediaItemCount == 0) return
        val items = JSONArray()
        repeat(player.mediaItemCount) {
            val item = player.getMediaItemAt(it)
            val uri = item.localConfiguration?.uri ?: return@repeat
            val meta = item.mediaMetadata
            items.put(JSONObject().put("id", item.mediaId).put("uri", uri.toString())
                .put("title", meta.title?.toString().orEmpty()).put("artist", meta.artist?.toString().orEmpty())
                .put("album", meta.albumTitle?.toString().orEmpty()).put("art", meta.artworkUri?.toString().orEmpty()))
        }
        writes.trySend(JSONObject().put("items", items).put("index", player.currentMediaItemIndex)
            .put("position", player.currentPosition.coerceAtLeast(0)).toString())
    }
}
