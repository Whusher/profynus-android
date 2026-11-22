package com.profynus.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrackDetailsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val details = TrackDetails("content://media/external/audio/media/999999", " New song ", " Artist ", " Album ", null)

    @Test fun editsPersistAndDoNotChangeTheAudioIdentity() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            TrackDetailsStore(context, dataStore).save(details)
            val loaded = TrackDetailsStore(context, dataStore).edits.first()[details.mediaUri]!!
            assertEquals("New song", loaded.title)
            assertEquals("Artist", loaded.artist)
            assertEquals("Album", loaded.album)
            val original = LocalTrack(42, "Old title", "Old artist", "Old album", Uri.parse(details.mediaUri), 90_000, "audio/mpeg")
            val edited = original.withDetails(loaded)
            assertEquals(original.uri, edited.uri)
            assertEquals(original.id, edited.id)
            assertEquals(original.durationMs, edited.durationMs)
            assertEquals("Old title", original.title)
            assertEquals("New song", edited.toMediaItem().mediaMetadata.title)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test fun imageIsBoundedAndSurvivesRemovalOfPickedSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        var imported: File? = null
        try {
            val image = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.MAGENTA)
            source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            val saved = TrackDetailsStore(context, dataStore).save(details, Uri.fromFile(source))
            imported = File(Uri.parse(saved.artworkUri).path!!)
            source.delete()
            val decoded = BitmapFactory.decodeFile(imported.path)
            assertEquals(1024, decoded.width)
            assertEquals(512, decoded.height)
            decoded.recycle()
            assertEquals(saved, TrackDetailsStore(context, dataStore).edits.first()[details.mediaUri])
            // Removing a cover clears the stored URI and its private copy.
            TrackDetailsStore(context, dataStore).save(saved.copy(artworkUri = null))
            assertFalse(imported.exists())
        } finally {
            scope.cancel()
            file.delete()
            source.delete()
            imported?.delete()
        }
    }

    @Test fun invalidImageOrBlankTitleDoesNotOverwriteSavedDetails() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            val store = TrackDetailsStore(context, dataStore)
            val saved = store.save(details)
            assertTrue(runCatching { store.save(details.copy(title = "Changed"), Uri.parse("file:///missing-image.png")) }.isFailure)
            assertTrue(runCatching { store.save(details.copy(title = "  ")) }.isFailure)
            assertEquals(saved, store.edits.first()[details.mediaUri])
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
