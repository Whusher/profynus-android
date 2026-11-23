package com.profynus.music.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/** Decode captured container fragments off the main thread; reject gaps and partial audio. */
internal object BrowserAudioConverter {
    suspend fun convert(input: File, output: File, expectedSeconds: Double, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        require(expectedSeconds.isFinite() && expectedSeconds in 0.1..10800.0)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var encoder: OrderedPcmEncoder? = null
        var started = false
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Captured data contains no readable audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rawFrameBytes = if (format.getString(MediaFormat.KEY_MIME) == "audio/raw")
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) * 2 else 0
            var previousPacketDuration = 0L
            check(!format.containsKey("crypto-key")) { "Encrypted audio is unsupported" }
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            started = true
            var inputEnded = false
            var outputEnded = false
            var previousTime = -1L
            var frames = 0L
            var sampleRate = 0
            var channels = 0
            var lastActivity = SystemClock.elapsedRealtime()
            var lastProgress = -1
            val info = MediaCodec.BufferInfo()
            output.outputStream().buffered(64 * 1024).use { stream ->
                coroutineScope {
                    while (!outputEnded) {
                        currentCoroutineContext().ensureActive()
                        check(SystemClock.elapsedRealtime() - lastActivity < 30000) { "Audio decoder stalled" }
                        var fedInput = false
                        if (!inputEnded) {
                            val index = decoder.dequeueInputBuffer(0)
                            if (index >= 0) {
                                fedInput = true
                                val buffer = decoder.getInputBuffer(index)!!
                                buffer.clear()
                                // Seeks may cause the browser to append the same segment again.
                                while (extractor.sampleTime >= 0 && extractor.sampleTime <= previousTime) {
                                    extractor.advance()
                                }
                                val time = extractor.sampleTime
                                val size = if (time < 0) -1 else extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                                        "Encrypted audio is unsupported"
                                    }
                                    check(if (previousTime < 0) time <= 150000 else time - previousTime - previousPacketDuration <= 120000) {
                                        "Captured audio has a gap; no MP3 was saved"
                                    }
                                    decoder.queueInputBuffer(index, 0, size, time, 0)
                                    previousTime = time
                                    previousPacketDuration = if (rawFrameBytes > 0)
                                        size.toLong() * 1000000 / (rawFrameBytes * format.getInteger(MediaFormat.KEY_SAMPLE_RATE)) else 0
                                    extractor.advance()
                                }
                                lastActivity = SystemClock.elapsedRealtime()
                            }
                        }
                        when (val index = decoder.dequeueOutputBuffer(info, if (fedInput) 0 else 1000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val decoded = decoder.outputFormat
                                val rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val count = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                val pcm = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                                check(pcm == AudioFormat.ENCODING_PCM_16BIT && count in 1..2) {
                                    "Unsupported decoded audio format"
                                }
                                check(encoder == null || (sampleRate == rate && channels == count)) {
                                    "Audio format changed during conversion"
                                }
                                sampleRate = rate
                                channels = count
                                if (encoder == null) encoder = OrderedPcmEncoder(rate, count, stream, this) { encodedFrames ->
                                    val percent = (encodedFrames * 100 / (rate * expectedSeconds)).toInt().coerceIn(0, 99)
                                    if (percent != lastProgress) { onProgress(percent); lastProgress = percent }
                                }
                            }
                            else -> if (index >= 0) {
                                try {
                                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                        val pcm = decoder.getOutputBuffer(index)!!
                                        pcm.position(info.offset)
                                        pcm.limit(info.offset + info.size)
                                        pcm.order(ByteOrder.nativeOrder())
                                        frames += checkNotNull(encoder) { "Decoder omitted audio format" }.encode(pcm)
                                    }
                                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                    lastActivity = SystemClock.elapsedRealtime()
                                } finally {
                                    decoder.releaseOutputBuffer(index, false)
                                }
                            }
                        }
                    }
                    val actualSeconds = frames.toDouble() / sampleRate.coerceAtLeast(1)
                    check(abs(actualSeconds - expectedSeconds) <= 0.5) {
                        "Audio is incomplete (${actualSeconds.toInt()} of ${expectedSeconds.toInt()} seconds); no MP3 was saved"
                    }
                    checkNotNull(encoder).finish()
                }
            }
            verifyMp3(output, expectedSeconds)
            onProgress(100)
        } finally {
            if (started) runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun verifyMp3(file: File, expectedSeconds: Double) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            check(extractor.trackCount == 1) { "MP3 validation failed" }
            val format = extractor.getTrackFormat(0)
            check(format.getString(MediaFormat.KEY_MIME) == "audio/mpeg") { "Output is not MP3" }
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                check(abs(format.getLong(MediaFormat.KEY_DURATION) / 1e6 - expectedSeconds) < 1.0) {
                    "MP3 duration validation failed"
                }
            }
        } finally { extractor.release() }
    }
}
