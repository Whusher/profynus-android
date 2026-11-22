package com.profynus.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.profynus.music.data.AppearanceSettings
import com.profynus.music.data.LocalTrack
import com.profynus.music.data.PlayerVisual
import com.profynus.music.data.TRACK_ARTWORK_URI
import com.profynus.music.data.TrackDetails
import com.profynus.music.ui.sphereArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun track(id: Long) = LocalTrack(
        id, "Song $id", "Artist", "Album", Uri.parse("content://media/external/audio/media/$id"), 120_000, "audio/mpeg",
    )

    @Test fun replacingDetailsKeepsMediaIdAndPlaybackConfiguration() {
        val original = track(1).toMediaItem()
        val edited = original.withDetails(TrackDetails(track(1).uri.toString(), "Renamed", "New artist", "New album", "file:///cover.jpg"))
        assertEquals(original.mediaId, edited.mediaId)
        assertEquals(original.localConfiguration, edited.localConfiguration)
        assertEquals("Renamed", edited.mediaMetadata.title)
        assertEquals("New album", edited.mediaMetadata.albumTitle)
        assertEquals("file:///cover.jpg", edited.mediaMetadata.extras!!.getString(TRACK_ARTWORK_URI))
    }

    @Test fun liveEditsAndVisualSwitchPreserveQueueAndPosition() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val edits = MutableStateFlow(emptyMap<String, TrackDetails>())
        val appearance = MutableStateFlow(AppearanceSettings())
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.RED)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        lateinit var player: ExoPlayer
        lateinit var updater: PlaybackMetadata
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItems(listOf(track(1).toMediaItem(), track(2).toMediaItem()), 1, 12_345)
            updater = PlaybackMetadata(context, player, scope, edits, appearance)
        }
        suspend fun awaitCondition(condition: () -> Boolean) {
            withTimeout(8_000) {
                while (!withContext(Dispatchers.Main) { condition() }) delay(50)
            }
        }
        try {
            awaitCondition { player.currentMediaItem!!.mediaMetadata.artworkData != null }
            val sphere = withContext(Dispatchers.Main) { player.currentMediaItem!!.mediaMetadata.artworkData!! }
            val second = track(2)
            edits.value = mapOf(second.uri.toString() to TrackDetails(
                second.uri.toString(), "Renamed song", "Edited artist", "Edited album", Uri.fromFile(source).toString(),
            ))
            appearance.value = AppearanceSettings(playerVisual = PlayerVisual.ALBUM_ART)
            awaitCondition {
                val metadata = player.currentMediaItem!!.mediaMetadata
                metadata.title == "Renamed song" && metadata.artworkData != null &&
                    !sphere.contentEquals(metadata.artworkData)
            }
            withContext(Dispatchers.Main) {
                assertEquals(2, player.mediaItemCount)
                assertEquals(1, player.currentMediaItemIndex)
                assertEquals(12_345L, player.currentPosition)
                assertEquals("1", player.getMediaItemAt(0).mediaId)
                assertEquals("2", player.currentMediaItem!!.mediaId)
            }
            appearance.value = AppearanceSettings()
            awaitCondition { sphere.contentEquals(player.currentMediaItem!!.mediaMetadata.artworkData) }
            appearance.value = AppearanceSettings(playerVisual = PlayerVisual.ALBUM_ART)
            // An unavailable cover must show the sphere, never the previous song's image.
            edits.value = mapOf(second.uri.toString() to edits.value.getValue(second.uri.toString()).copy(artworkUri = "file:///unavailable.jpg"))
            awaitCondition {
                val metadata = player.currentMediaItem!!.mediaMetadata
                metadata.extras?.getString(TRACK_ARTWORK_URI) == "file:///unavailable.jpg" &&
                    sphere.contentEquals(metadata.artworkData)
            }
            // Cover-only edits also reach a queued item before it becomes current.
            val first = track(1)
            edits.value = edits.value + (first.uri.toString() to TrackDetails(
                first.uri.toString(), first.title, first.artist, first.album, Uri.fromFile(source).toString(),
            ))
            awaitCondition {
                player.getMediaItemAt(0).mediaMetadata.extras?.getString(TRACK_ARTWORK_URI) == Uri.fromFile(source).toString()
            }
            withContext(Dispatchers.Main) {
                assertEquals(12_345L, player.currentPosition)
                assertFalse(player.isPlaying)
                player.seekTo(0, 0)
            }
            awaitCondition {
                val metadata = player.currentMediaItem!!.mediaMetadata
                metadata.artworkData != null && !sphere.contentEquals(metadata.artworkData)
            }
        } finally {
            scope.cancel()
            instrumentation.runOnMainSync {
                updater.release()
                player.release()
            }
            source.delete()
        }
    }

    @Test fun sphereArtworkIsABoundedDecodableBitmap() {
        val bytes = sphereArtwork(AppearanceSettings())
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(384, bitmap.width)
        assertEquals(384, bitmap.height)
        assertTrue(bytes.size < 256_000)
        bitmap.recycle()
    }
}
