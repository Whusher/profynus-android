package com.profynus.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.profynus.music.data.AppearancePreferences
import com.profynus.music.data.AppearanceSettings
import com.profynus.music.data.PlayerVisual
import com.profynus.music.data.TRACK_ARTWORK_URI
import com.profynus.music.data.TRACK_URI
import com.profynus.music.data.TrackDetails
import com.profynus.music.data.TrackDetailsStore
import com.profynus.music.ui.ArtworkLoader
import com.profynus.music.ui.sphereArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Updates metadata in place; the URI, queue order and playback position remain intact. */
internal fun MediaItem.withDetails(details: TrackDetails): MediaItem {
    val metadata = mediaMetadata
    val previousArt = metadata.extras?.getString(TRACK_ARTWORK_URI)
    if (metadata.title?.toString() == details.title &&
        metadata.artist?.toString() == details.artist &&
        metadata.albumTitle?.toString() == details.album &&
        previousArt == details.artworkUri
    ) return this

    val updated = metadata.buildUpon()
        .setTitle(details.title)
        .setArtist(details.artist)
        .setAlbumTitle(details.album)
        .setExtras(Bundle(metadata.extras ?: Bundle()).apply {
            putString(TRACK_ARTWORK_URI, details.artworkUri)
        })
    // The service will resolve the new cover (or a sphere fallback) below.
    if (previousArt != details.artworkUri) {
        updated.setArtworkData(null, null).setArtworkUri(details.artworkUri?.let(Uri::parse))
    }
    return buildUpon().setMediaMetadata(updated.build()).build()
}

/** Owned by the playback service, so notification artwork follows changes even with no Activity. */
internal class PlaybackMetadata(
    private val context: Context,
    private val player: Player,
    scope: CoroutineScope,
    edits: Flow<Map<String, TrackDetails>> = TrackDetailsStore(context).edits,
    appearance: Flow<AppearanceSettings> = AppearancePreferences(context).settings,
) {
    private val changes = MutableStateFlow(0L)
    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { changes.value++ }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { changes.value++ }
    }
    private var sphereKey: Pair<String, com.profynus.music.data.ShapeProfile>? = null
    private var sphereBytes: ByteArray? = null
    private var coverKey: String? = null
    private var coverBytes: ByteArray? = null

    init {
        player.addListener(listener)
        scope.launch {
            combine(
                edits,
                appearance,
                changes,
            ) { edits, appearance, _ -> edits to appearance }
                .collectLatest { (edits, appearance) ->
                    for (index in 0 until player.mediaItemCount) {
                        val item = player.getMediaItemAt(index)
                        val uri = item.mediaMetadata.extras?.getString(TRACK_URI) ?: continue
                        val details = edits[uri] ?: continue
                        val updated = item.withDetails(details)
                        if (updated !== item) player.replaceMediaItem(index, updated)
                    }
                    val current = player.currentMediaItem ?: return@collectLatest
                    val artUri = current.mediaMetadata.extras?.getString(TRACK_ARTWORK_URI)
                    val artwork = artwork(artUri, appearance)
                    // Loading is asynchronous: never apply a previous song's cover to a new song.
                    val latest = player.currentMediaItem ?: return@collectLatest
                    if (latest != current) return@collectLatest
                    if (!artwork.contentEquals(latest.mediaMetadata.artworkData) ||
                        latest.mediaMetadata.artworkUri != null
                    ) {
                        val metadata = latest.mediaMetadata.buildUpon()
                            .setArtworkUri(null)
                            .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                        player.replaceMediaItem(
                            player.currentMediaItemIndex,
                            latest.buildUpon().setMediaMetadata(metadata).build(),
                        )
                    }
                }
        }
    }

    private suspend fun artwork(uri: String?, settings: AppearanceSettings): ByteArray {
        if (settings.playerVisual == PlayerVisual.ALBUM_ART && uri != null) {
            if (coverKey != uri) {
                val bitmap = ArtworkLoader.load(context, uri, maxSize = 512)
                coverBytes = bitmap?.let {
                    withContext(Dispatchers.IO) {
                        ByteArrayOutputStream().use { output ->
                            it.compress(Bitmap.CompressFormat.JPEG, 90, output)
                            output.toByteArray()
                        }
                    }
                }
                coverKey = uri
            }
            coverBytes?.let { return it }
        }
        val key = settings.paletteId to settings.shape
        if (sphereKey != key) {
            sphereBytes = withContext(Dispatchers.Default) { sphereArtwork(settings) }
            sphereKey = key
        }
        return requireNotNull(sphereBytes)
    }

    fun release() { player.removeListener(listener) }
}
