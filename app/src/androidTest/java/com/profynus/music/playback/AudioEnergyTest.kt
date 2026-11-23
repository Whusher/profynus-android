package com.profynus.music.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class AudioEnergyTest {
    @Test fun measuresPcmWithoutConsumingInputAndResetsOnFlush() {
        val sink = AudioEnergy()
        sink.flush(44100, 2, C.ENCODING_PCM_16BIT)
        val silence = ByteBuffer.allocate(2048).order(ByteOrder.nativeOrder())
        sink.handleBuffer(silence)
        assertEquals(0f, AudioEnergy.level, 0f)
        val loud = ByteBuffer.allocate(2048).order(ByteOrder.nativeOrder())
        repeat(1024) { loud.putShort(if (it % 2 == 0) 16000 else -16000) }
        loud.flip()
        sink.handleBuffer(loud)
        assertTrue(AudioEnergy.level > 0.5f)
        assertEquals(0, loud.position())
        sink.flush(48000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(0f, AudioEnergy.level, 0f)
    }
}
