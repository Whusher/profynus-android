package com.profynus.music.ui

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.profynus.music.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.concurrent.atomic.AtomicBoolean

class PlaylistEditorSheetTest {
    private val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }).around(compose)
    private val tracks = listOf(LocalTrack(99991, "Test song", "Artist", "", Uri.parse("content://test/1"), 120000, "audio/mpeg"))

    @Test fun draftSurvivesRestorationAndSavesSelectedSongs() {
        val store = PlaylistStore(context)
        val initial = LocalPlaylist()
        val dismissed = AtomicBoolean(false)
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                ProfynusTheme(ProfynusPalettes.all.first()) {
                    PlaylistEditorSheet(initial, true, tracks, true, store, { dismissed.set(true) }, {})
                }
            }
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Playlist name").performTextReplacement("Road trip")
            compose.onNodeWithText("Test song").performScrollTo().performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Playlist name").assertTextContains("Road trip")
            compose.onNodeWithText("Selected • 1").performScrollTo().assertExists()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5000) { dismissed.get() }
            val saved = runBlocking { store.playlists.first().single { it.id == initial.id } }
            assertEquals("Road trip", saved.name)
            assertEquals(listOf("content://test/1"), saved.songs)
        } finally { runBlocking { store.delete(initial.id) } }
    }

    @Test fun cancelAsksBeforeDiscardingDraft() {
        val store = PlaylistStore(context)
        val initial = LocalPlaylist()
        val dismissed = AtomicBoolean(false)
        compose.setContent {
            ProfynusTheme(ProfynusPalettes.all.first()) {
                PlaylistEditorSheet(initial, true, tracks, true, store, { dismissed.set(true) }, {})
            }
        }
        compose.onNodeWithText("Playlist name").performTextReplacement("Unsaved")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        assertFalse(dismissed.get())
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5000) { dismissed.get() }
        assertTrue(runBlocking { store.playlists.first().none { it.id == initial.id } })
    }
}
