package com.profynus.music.playback


import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.profynus.music.data.LocalTrack
import com.profynus.music.data.TRACK_URI
import com.profynus.music.data.TRACK_ARTWORK_URI
import com.profynus.music.data.TrackDetails

/** UI reads snapshots; Media3's service is the single authoritative player. */
data class PlaybackSnapshot(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val isBuffering: Boolean = false,
    val mediaId: String = "",
    val details: TrackDetails? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

data class QueueEntry(val index: Int, val mediaId: String, val title: String, val artist: String, val artworkUri: String?)

class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val token = SessionToken(
        appContext, ComponentName(appContext, ProfynusPlaybackService::class.java)
    )
    private val future = MediaController.Builder(appContext, token).buildAsync()
    private var mediaController: MediaController? = null
    private var released = false

    var isConnected by mutableStateOf(false)
        private set
    var snapshot by mutableStateOf(PlaybackSnapshot())
        private set

    var queue by mutableStateOf(emptyList<QueueEntry>())
        private set

    private fun refreshQueue() {
        val player = mediaController ?: return
        queue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueEntry(index, item.mediaId, item.mediaMetadata.title?.toString().orEmpty(),
                item.mediaMetadata.artist?.toString().orEmpty(),
                item.mediaMetadata.extras?.getString(TRACK_ARTWORK_URI) ?: item.mediaMetadata.artworkUri?.toString())
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) refreshQueue()
        }
    }

    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                if (released) return@onSuccess
                mediaController = controller
                controller.addListener(listener)
                isConnected = true
                refresh()
                refreshQueue()
            }
        }, appContext.mainExecutor)
    }

    fun refresh() {
        val player = mediaController ?: return
        val metadata = player.mediaMetadata
        snapshot = PlaybackSnapshot(
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            currentIndex = player.currentMediaItemIndex,
            queueSize = player.mediaItemCount,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            mediaId = player.currentMediaItem?.mediaId.orEmpty(),
            details = metadata.extras?.getString(TRACK_URI)?.let { uri ->
                TrackDetails(
                    mediaUri = uri,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    album = metadata.albumTitle?.toString().orEmpty(),
                    artworkUri = metadata.extras?.getString(TRACK_ARTWORK_URI),
                )
            },
        )
    }

    fun play(tracks: List<LocalTrack>, index: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        mediaController?.apply {
            shuffleModeEnabled = shuffle
            setMediaItems(tracks.map(LocalTrack::toMediaItem), index, 0L)
            prepare()
            play()
        }
        refresh()
    }

    fun toggle() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else {
                if (it.playbackState == Player.STATE_IDLE) it.prepare()
                it.play()
            }
        }
    }

    fun shuffleAll(tracks: List<LocalTrack>) {
        if (tracks.isNotEmpty()) play(tracks, tracks.indices.random(), shuffle = true)
    }
    fun toggleShuffle() { mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { mediaController?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    } }
    fun addToQueue(track: LocalTrack): Boolean {
        val player = mediaController ?: return false
        player.addMediaItem(track.toMediaItem())
        return true
    }
    fun skipTo(index: Int) { mediaController?.let {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    } }
    fun removeFromQueue(index: Int) { mediaController?.let {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    } }

    fun next() { mediaController?.seekToNext() }
    fun previous() { mediaController?.seekToPrevious() }
    fun seekTo(positionMs: Long) { mediaController?.seekTo(positionMs) }

    fun release() {
        if (released) return
        released = true
        mediaController?.removeListener(listener)
        MediaController.releaseFuture(future)
        mediaController = null
        isConnected = false
    }
}
