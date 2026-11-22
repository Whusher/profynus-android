
package com.profynus.music.media

import com.profynus.media.transport.RangeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.net.URL

data class AcquiredMedia(
    val video: File?,
    val audio: File?,
    val bytesDownloaded: Long
)

class MediaAcquisitionCoordinator {

    suspend fun acquire(
        video: MediaStreamDescriptor?,
        audio: MediaStreamDescriptor?,
        workingDirectory: File
    ): AcquiredMedia = coroutineScope {

        require(video != null || audio != null)
        require(video == null || video.downloadable)
        require(audio == null || audio.downloadable)

        workingDirectory.mkdirs()

        val videoTask = video?.let { track ->
            async(Dispatchers.IO) {
                RangeDownloader(
                    workers = 2,
                    chunkBytes = 4L * 1024 * 1024
                ).download(
                    source = URL(track.url),
                    destination = File(
                        workingDirectory,
                        "video.source"
                    )
                )
            }
        }

        val audioTask = audio?.let { track ->
            async(Dispatchers.IO) {
                RangeDownloader(
                    workers = 2,
                    chunkBytes = 4L * 1024 * 1024
                ).download(
                    source = URL(track.url),
                    destination = File(
                        workingDirectory,
                        "audio.source"
                    )
                )
            }
        }

        val videoResult = videoTask?.await()
        val audioResult = audioTask?.await()

        AcquiredMedia(
            video = videoResult?.file,
            audio = audioResult?.file,
            bytesDownloaded =
                (videoResult?.bytes ?: 0L) +
                        (audioResult?.bytes ?: 0L)
        )
    }
}
