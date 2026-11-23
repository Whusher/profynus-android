package com.profynus.music.playback

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackMemoryTest {
    @Test fun restoresQueueIndexMetadataAndPositionWithoutAutoplay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { file })
        try {
            withContext(Dispatchers.Main) {
                val first = ExoPlayer.Builder(context).build()
                first.setMediaItems((1..2).map {
                    MediaItem.Builder().setMediaId("$it").setUri("content://media/external/audio/media/$it")
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $it").build()).build()
                }, 1, 12345)
                val memory = PlaybackMemory(context, first, scope, store)
                withTimeout(5000) { store.data.first { it.asMap().isNotEmpty() } }
                memory.release()
                first.release()
                val second = ExoPlayer.Builder(context).build()
                val restored = PlaybackMemory(context, second, scope, store)
                try {
                    withTimeout(5000) { while (second.mediaItemCount == 0) delay(20) }
                    assertEquals(2, second.mediaItemCount)
                    assertEquals(1, second.currentMediaItemIndex)
                    assertEquals(12345L, second.currentPosition)
                    assertEquals("Song 2", second.mediaMetadata.title)
                    assertFalse(second.playWhenReady)
                } finally {
                    restored.release()
                    second.release()
                }
            }
        } finally {
            scope.cancel()
            ioScope.cancel()
            file.delete()
        }
    }
}
