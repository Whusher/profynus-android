package com.profynus.music

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserAudioScriptTest {
    @Test fun captureProtocolAndCoverage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = instrumentation.targetContext.assets.open("browser-audio-export.js")
            .bufferedReader().use { it.readText() }
        val tests = instrumentation.context.assets.open("browser-audio-export.test.cjs")
            .bufferedReader().use { it.readText() }
        val finished = CountDownLatch(1)
        var result = ""
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.evaluateJavascript(
                tests + "\nJSON.stringify(runBrowserAudioTests(" + JSONObject.quote(source) + "));"
            ) { raw -> result = raw; finished.countDown() }
        }
        try {
            assertTrue("JavaScript tests timed out", finished.await(15, TimeUnit.SECONDS))
            val decoded = JSONArray("[" + result + "]").getString(0)
            val results = JSONArray(decoded)
            assertEquals(6, results.length())
            for (i in 0 until results.length()) {
                val test = results.getJSONObject(i)
                assertTrue(test.toString(), test.getBoolean("passed"))
            }
        } finally { instrumentation.runOnMainSync { webView.destroy() } }
    }
}
