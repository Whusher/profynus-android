package com.profynus.music.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaylistStoreTest {
    @Test fun persistsMembershipAndIndependentCoverThenDeletesOnlyPlaylist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaylistStore(context)
        val playlist = LocalPlaylist(name = " Mix ", songs = listOf("song:1", "song:2", "song:1"))
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            store.save(playlist, Uri.fromFile(source))
            val saved = PlaylistStore(context).playlists.first().single { it.id == playlist.id }
            assertEquals("Mix", saved.name)
            assertEquals(listOf("song:1", "song:2"), saved.songs)
            val cover = File(Uri.parse(saved.artwork).path!!)
            assertTrue(cover.exists())
            source.delete()
            assertTrue(cover.exists())
            store.save(saved.copy(name = "Renamed", songs = listOf("song:2"), artwork = null), null)
            val edited = store.playlists.first().single { it.id == playlist.id }
            assertEquals("Renamed", edited.name)
            assertEquals(listOf("song:2"), edited.songs)
            assertFalse(cover.exists())
            store.delete(playlist.id)
            assertTrue(store.playlists.first().none { it.id == playlist.id })
        } finally {
            store.delete(playlist.id)
            source.delete()
        }
    }
}
