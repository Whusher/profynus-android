package com.profynus.music.youtube

import java.net.URI

/** Hosts needed to browse YouTube and complete its account/consent redirects. */
internal object YouTubeBrowserNavigation {
    fun staysInBrowser(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" ||
            host == "accounts.google.com" ||
            host == "consent.google.com" ||
            host == "myaccount.google.com"
    }
}
