package com.profynus.music.media

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class Mp3EncoderBenchmarkTest {
    @Test fun compareSamePcmAndBitrate() {
        val pcm = ByteBuffer.allocateDirect(1152 * 4).order(ByteOrder.nativeOrder())
        repeat(1152) { i ->
            val left = (10000 * sin(2 * PI * 440 * i / 48000) + 3000 * sin(2 * PI * 1700 * i / 48000)).toInt().toShort()
            pcm.putShort(left).putShort((-left).toShort())
        }
        val sink = object : OutputStream() {
            override fun write(value: Int) {}
            override fun write(bytes: ByteArray, offset: Int, length: Int) {}
        }
        fun run(native: Boolean, blocks: Int): Long {
            val start = SystemClock.elapsedRealtime()
            if (native) PcmMp3Encoder(48000, 2, sink).use { encoder ->
                repeat(blocks) { pcm.rewind(); encoder.encode(pcm) }
                encoder.finish()
            } else JavaPcmMp3Encoder(48000, 2, sink).use { encoder ->
                repeat(blocks) { pcm.rewind(); encoder.encode(pcm) }
                encoder.finish()
            }
            return SystemClock.elapsedRealtime() - start
        }
        run(false, 42)
        run(true, 42)
        val javaMs = run(false, 1250) // 30 seconds of identical stereo PCM.
        val nativeMs = run(true, 1250)
        Log.i("ProfynusBenchmark", "30s stereo 192kbps quality=5: java=${javaMs}ms native=${nativeMs}ms speedup=${javaMs.toDouble() / nativeMs.coerceAtLeast(1)}")
    }
}
