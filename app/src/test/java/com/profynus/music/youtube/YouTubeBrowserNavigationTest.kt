package com.profynus.music.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeBrowserNavigationTest {
    @Test fun youtubeAndAccountRedirectsStayInTheSameBrowser() {
        listOf(
            "https://m.youtube.com/results?search_query=music",
            "https://www.youtube.com/signin",
            "https://youtube.com/watch?v=example",
            "https://youtu.be/example",
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fm.youtube.com",
            "https://consent.google.com/m",
            "https://consent.youtube.com/m",
            "https://myaccount.google.com/",
            "https://ACCOUNTS.GOOGLE.COM:443/",
        ).forEach { assertTrue(it, YouTubeBrowserNavigation.staysInBrowser(it)) }
    }

    @Test fun unrelatedAndDeceptiveHostsDoNotStayInTheBrowser() {
        listOf(
            "https://example.com/",
            "https://accounts.google.com.example.com/",
            "https://notyoutube.com/",
            "https://youtube.com.example.com/",
            "https://accounts.google.com@example.com/",
            "https://example.com@accounts.google.com/",
            "https://accounts.google.com:8443/",
            "http://accounts.google.com/",
            "intent://accounts.google.com/",
            "javascript:alert(1)",
            "not a url",
        ).forEach { assertFalse(it, YouTubeBrowserNavigation.staysInBrowser(it)) }
    }
}
