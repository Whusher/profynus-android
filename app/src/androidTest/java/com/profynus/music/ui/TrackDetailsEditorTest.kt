package com.profynus.music.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import com.profynus.music.data.TrackDetails
import com.profynus.music.data.TrackDetailsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TrackDetailsEditorTest {
    private val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    // MIUI blocks ActivityScenario's app-origin launch while the test process is in the
    // background. A shell launch foregrounds the test host without changing device permissions.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }).around(compose)

    private val details = TrackDetails("content://media/external/audio/media/999998", "Original", "Artist", "Album", null)

    @Test fun blankNameIsRejectedAndDraftSurvivesRecreationBeforeSaving() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = TrackDetailsStore(context, dataStore)
        val dismissed = AtomicBoolean(false)
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                ProfynusTheme(ProfynusPalettes.all.first()) {
                    TrackDetailsEditor(details, store) { dismissed.set(true) }
                }
            }
            compose.onNodeWithText("Song name").performTextReplacement(" ")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Song name").performTextReplacement("My song")
            compose.onNodeWithText("Album name").performTextReplacement("My album")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Song name").assertTextContains("My song")
            compose.onNodeWithText("Album name").assertTextContains("My album")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { dismissed.get() }
            val saved = runBlocking { store.edits.first()[details.mediaUri]!! }
            assertEquals("My song", saved.title)
            assertEquals("My album", saved.album)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test fun cancelDiscardsDraft() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = TrackDetailsStore(context, dataStore)
        val dismissed = AtomicBoolean(false)
        try {
            compose.setContent {
                ProfynusTheme(ProfynusPalettes.all.first()) {
                    TrackDetailsEditor(details, store) { dismissed.set(true) }
                }
            }
            compose.onNodeWithText("Song name").performTextReplacement("Discard this")
            compose.onNodeWithText("Cancel").performClick()
            compose.waitUntil(5_000) { dismissed.get() }
            assertTrue(runBlocking { store.edits.first().isEmpty() })
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
