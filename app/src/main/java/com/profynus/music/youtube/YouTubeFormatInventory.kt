package com.profynus.music.youtube

import com.profynus.music.media.MediaFormatEntry
import com.profynus.music.media.TrackType
import org.json.JSONObject

/** Maps the already-sanitized PAGE_PROBE_JS JSON to domain models.
 *  It intentionally never receives the actual streaming URLs or cookies.
 */
object YouTubeFormatInventory {
    fun parse(report: JSONObject): List<MediaFormatEntry> {
        val items = report.optJSONArray("formats") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = when (item.optString("kind")) {
                    "AUDIO" -> TrackType.AUDIO
                    "VIDEO" -> TrackType.VIDEO
                    "AUDIO_VIDEO" -> TrackType.AUDIO_VIDEO
                    else -> continue
                }
                val itag = item.optInt("itag", -1)
                if (itag < 0) continue
                add(
                    MediaFormatEntry(
                        itag = itag,
                        kind = type,
                        mimeType = item.optString("mimeType"),
                        codecs = item.optString("codecs"),
                        qualityLabel = item.optString("qualityLabel")
                            .takeUnless { it.isBlank() || it == "null" },
                        bitrate = if (item.isNull("bitrate") || !item.has("bitrate")) null
                            else item.optLong("bitrate"),
                        contentLength = if (item.isNull("contentLength") || !item.has("contentLength")) null
                            else item.optLong("contentLength"),
                        hasDirectUrlField = item.optBoolean("hasDirectUrl"),
                        requiresSignature = item.optBoolean("requiresSignature")
                    )
                )
            }
        }
    }
}
