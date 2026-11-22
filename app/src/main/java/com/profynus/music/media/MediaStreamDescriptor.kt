
package com.profynus.music.media

enum class TrackType {
    AUDIO,
    VIDEO,
    AUDIO_VIDEO
}

data class MediaStreamDescriptor(
    val id: String,
    val type: TrackType,
    val url: String,
    val mimeType: String,
    val codec: String?,
    val bitrate: Long?,
    val contentLength: Long?,
    val expiresAtEpochMs: Long?,
    val downloadable: Boolean
)

data class ResolvedMedia(
    val videoId: String,
    val title: String,
    val streams: List<MediaStreamDescriptor>
)

sealed interface ResolutionResult {
    data class Available(
        val media: ResolvedMedia
    ) : ResolutionResult

    data class Unavailable(
        val reason: String
    ) : ResolutionResult
}

interface MediaStreamResolver {
    suspend fun resolve(
        videoId: String
    ): ResolutionResult
}
