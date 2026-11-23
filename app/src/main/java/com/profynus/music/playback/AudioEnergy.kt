package com.profynus.music.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** A bounded RMS envelope from decoded PCM, never microphone input. */
@UnstableApi
internal class AudioEnergy : TeeAudioProcessor.AudioBufferSink {
    private var encoding = C.ENCODING_INVALID
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        level = 0f
    }
    override fun handleBuffer(buffer: ByteBuffer) {
        val input = buffer.duplicate().order(ByteOrder.nativeOrder())
        if (encoding != C.ENCODING_PCM_16BIT) { level = 0f; return }
        var sum = 0.0
        var count = 0
        while (input.remaining() >= 2) {
            val sample = input.short / 32768.0
            sum += sample * sample
            count++
        }
        val target = if (count == 0) 0f else (sqrt(sum / count) * 3).toFloat().coerceIn(0f, 1f)
        level += (target - level) * if (target > level) 0.65f else 0.15f
        updatedAt = android.os.SystemClock.elapsedRealtime()
    }
    companion object {
        @Volatile var level = 0f
            private set
        @Volatile var updatedAt = 0L
            private set
    }
}
