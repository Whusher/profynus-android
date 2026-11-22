package com.profynus.music

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.profynus.music.media.BrowserAudioConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class BrowserAudioConverterTest {
    @Test fun convertsStereoPcmToDecodableMp3() = runBlocking {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("tone", ".wav", dir)
        val output = File.createTempFile("tone", ".mp3", dir)
        try {
            writeWave(input, 2)
            BrowserAudioConverter.convert(input, output, 1.0) {}
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val format = extractor.getTrackFormat(0)
                assertEquals("audio/mpeg", format.getString(MediaFormat.KEY_MIME))
                assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                // Decode it again: catches silent/corrupt MP3 and incorrect PCM amplitude scaling.
                extractor.selectTrack(0)
                val decoder = MediaCodec.createDecoderByType("audio/mpeg")
                try {
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    var inputEnded = false
                    var outputEnded = false
                    var energy = 0.0
                    var count = 0L
                    val info = MediaCodec.BufferInfo()
                    val deadline = System.nanoTime() + 15_000_000_000
                    while (!outputEnded && System.nanoTime() < deadline) {
                        if (!inputEnded) {
                            val index = decoder.dequeueInputBuffer(10000)
                            if (index >= 0) {
                                val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val index = decoder.dequeueOutputBuffer(info, 10000)
                        if (index >= 0) {
                            val pcm = decoder.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                            pcm.position(info.offset)
                            pcm.limit(info.offset + info.size)
                            while (pcm.remaining() >= 2) {
                                val value = pcm.short.toDouble()
                                energy += value * value
                                count++
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                    assertTrue("MP3 decoder did not finish", outputEnded)
                    assertTrue("Wrong MP3 sample count: $count", count in 90000..102000)
                    val rms = sqrt(energy / count)
                    assertTrue("MP3 amplitude is wrong: $rms", rms in 5000.0..10000.0)
                    decoder.stop()
                } finally { decoder.release() }
            } finally { extractor.release() }
        } finally { input.delete(); output.delete() }
    }

    @Test fun rejectsIncompleteCapture() = runBlocking {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("short-tone", ".wav", dir)
        val output = File.createTempFile("short-tone", ".mp3", dir)
        try {
            writeWave(input, 1)
            try {
                BrowserAudioConverter.convert(input, output, 5.0) {}
                fail("Partial audio was accepted")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("incomplete"))
            }
        } finally { input.delete(); output.delete() }
    }

    private fun writeWave(file: File, channels: Int) {
        val rate = 48000
        val dataSize = rate * channels * 2
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
        wav.putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        wav.put("data".toByteArray()).putInt(dataSize)
        repeat(rate) { index ->
            val sample = (12000 * sin(2 * PI * 440 * index / rate)).toInt().toShort()
            repeat(channels) { wav.putShort(sample) }
        }
        file.writeBytes(wav.array())
    }
}
