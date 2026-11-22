package com.profynus.music.media

/**
 * Describes format metadata, not permission or ability to download it.
 * A player format's URL field does not imply a transferable, authorized file.
 */
data class MediaFormatEntry(
    val itag: Int,
    val kind: TrackType,
    val mimeType: String,
    val codecs: String,
    val qualityLabel: String? = null,
    val bitrate: Long? = null,
    val contentLength: Long? = null,
    val hasDirectUrlField: Boolean = false,
    val requiresSignature: Boolean = false
) {
    val height: Int
        get() = qualityLabel?.removeSuffix("p")?.toIntOrNull() ?: 0

    val isH264: Boolean get() = codecs.contains("avc1", ignoreCase = true)
    val isAacLc: Boolean get() = codecs.contains("mp4a.40.2", ignoreCase = true)
    val isOpus: Boolean get() = codecs.contains("opus", ignoreCase = true)
}

data class MediaFormatSummary(
    val totalEntries: Int,
    val uniqueItags: Int,
    val combined: Int,
    val videoOnly: Int,
    val audioOnly: Int,
    val directUrlFieldCount: Int,
    val duplicatedItags: Map<Int, Int>,
    val bestCombinedMp4: MediaFormatEntry?,
    val bestH264Video: MediaFormatEntry?,
    val bestAacAudio: MediaFormatEntry?,
    val preferredMp3Input: MediaFormatEntry?
)

object MediaFormatClassifier {
    fun summarize(formats: List<MediaFormatEntry>): MediaFormatSummary {
        val videoOnly = formats.filter { it.kind == TrackType.VIDEO }
        val audioOnly = formats.filter { it.kind == TrackType.AUDIO }
        val combined = formats.filter { it.kind == TrackType.AUDIO_VIDEO }
        val duplicates = formats.groupingBy { it.itag }.eachCount().filterValues { it > 1 }
        val videoPreference = compareBy<MediaFormatEntry> { it.height }
            .thenBy { it.bitrate ?: 0L }
        val audioPreference = compareBy<MediaFormatEntry> { it.bitrate ?: 0L }
            .thenBy { it.contentLength ?: 0L }

        return MediaFormatSummary(
            totalEntries = formats.size,
            uniqueItags = formats.map { it.itag }.distinct().size,
            combined = combined.size,
            videoOnly = videoOnly.size,
            audioOnly = audioOnly.size,
            directUrlFieldCount = formats.count { it.hasDirectUrlField },
            duplicatedItags = duplicates,
            bestCombinedMp4 = combined.filter {
                it.mimeType == "video/mp4" && it.isH264 && it.isAacLc
            }.maxWithOrNull(videoPreference),
            bestH264Video = videoOnly.filter {
                it.mimeType == "video/mp4" && it.isH264
            }.maxWithOrNull(videoPreference),
            bestAacAudio = audioOnly.filter {
                it.mimeType == "audio/mp4" && it.isAacLc
            }.maxWithOrNull(audioPreference),
            preferredMp3Input = audioOnly.maxWithOrNull(audioPreference)
        )
    }

    fun description(summary: MediaFormatSummary): String = buildString {
        appendLine("Entries: ${summary.totalEntries}; unique itags: ${summary.uniqueItags}")
        appendLine("Combined: ${summary.combined}; video-only: ${summary.videoOnly}; audio-only: ${summary.audioOnly}")
        appendLine("URL fields present: ${summary.directUrlFieldCount} (NOT verified downloads)")
        appendLine("Repeated itags: ${summary.duplicatedItags.keys.sorted().joinToString()}")
        appendLine()
        appendLine("MP4 combined candidate: ${summary.bestCombinedMp4?.let { "itag ${it.itag} (${it.qualityLabel})" } ?: "none"}")
        appendLine("Higher-res H.264 track: ${summary.bestH264Video?.let { "itag ${it.itag} (${it.qualityLabel})" } ?: "none"}")
        appendLine("AAC audio for MP4: ${summary.bestAacAudio?.let { "itag ${it.itag}" } ?: "none"}")
        appendLine("MP3 conversion input candidate: ${summary.preferredMp3Input?.let { "itag ${it.itag} (${it.codecs})" } ?: "none"}")
        appendLine()
        append("Selection is informational; no source has been authorized or verified for transfer.")
    }
}
