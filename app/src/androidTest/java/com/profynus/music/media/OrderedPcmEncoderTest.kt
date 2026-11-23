package com.profynus.music.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class OrderedPcmEncoderTest {
    @Test fun batchingPreservesExactBitstreamForMonoAndStereo() = runBlocking {
        for (channels in 1..2) {
            val pcm = ByteBuffer.allocate(48013 * channels * 2).order(ByteOrder.nativeOrder())
            repeat(48013) { frame ->
                repeat(channels) { channel ->
                    pcm.putShort((12000 * sin(2 * PI * (220 + frame / 1000 * 37 + channel * 113) * frame / 48000)).toInt().toShort())
                }
            }
            pcm.flip()
            val expected = ByteArrayOutputStream()
            PcmMp3Encoder(48000, channels, expected).use {
                it.encode(pcm)
                it.finish()
            }
            val actual = ByteArrayOutputStream()
            var lastFrames = 0L
            withTimeout(10000) {
                coroutineScope {
                    val encoder = OrderedPcmEncoder(48000, channels, actual, this) {
                        assertTrue(it > lastFrames)
                        lastFrames = it
                    }
                    pcm.rewind()
                    while (pcm.hasRemaining()) {
                        val chunk = pcm.slice().order(ByteOrder.nativeOrder())
                        val size = minOf(257 * channels * 2, pcm.remaining())
                        chunk.limit(size)
                        encoder.encode(chunk)
                        pcm.position(pcm.position() + size)
                    }
                    encoder.finish()
                }
            }
            assertEquals(48013L, lastFrames)
            assertArrayEquals(expected.toByteArray(), actual.toByteArray())
        }
    }

    @Test fun writerFailureCancelsProducerInsteadOfDeadlocking() = runBlocking {
        val broken = object : OutputStream() {
            override fun write(value: Int) { throw IOException("disk full") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { throw IOException("disk full") }
        }
        try {
            withTimeout(10000) {
                coroutineScope {
                    val encoder = OrderedPcmEncoder(48000, 2, broken, this) {}
                    repeat(100) {
                        encoder.encode(ByteBuffer.allocate(32768).order(ByteOrder.nativeOrder()))
                    }
                    encoder.finish()
                }
            }
            fail("Expected write failure")
        } catch (expected: IOException) {
            assertEquals("disk full", expected.message)
        }
    }
}
