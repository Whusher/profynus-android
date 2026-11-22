package com.profynus.music.youtube

/**
 * Parses the balanced JSON object assigned to ytInitialPlayerResponse in a page.
 * This is a best-effort diagnostic parser, not a stable YouTube API contract.
 * Does not run scripts, process signatures, or download any media.
 */
internal object PlayerResponseExtractor {
    private val patterns = listOf(
        Regex("\\bytInitialPlayerResponse\\s*=\\s*"),
        Regex("\\\"ytInitialPlayerResponse\\\"\\s*:\\s*")
    )

    fun extract(html: String): String? {
        for (pattern in patterns) {
            for (match in pattern.findAll(html)) {
                var start = match.range.last + 1
                while (start < html.length && html[start].isWhitespace()) start++
                if (start >= html.length || html[start] != '{') continue
                val end = findBalancedEnd(html, start) ?: continue
                return html.substring(start, end + 1)
            }
        }
        return null
    }

    private fun findBalancedEnd(source: String, start: Int): Int? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
                continue
            }
            when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                    if (depth < 0) return null
                }
            }
        }
        return null
    }
}
