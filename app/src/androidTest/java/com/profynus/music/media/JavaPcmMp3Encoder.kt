package com.profynus.music.media

import de.sciss.jump3r.mp3.*
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.Closeable
import java.io.OutputStream
import java.nio.ByteBuffer

/** Bounded PCM16 -> actual MPEG Layer III encoding, independent of JavaSound/JNI. */
internal class JavaPcmMp3Encoder(
    sampleRate: Int,
    private val channels: Int,
    private val output: OutputStream
) : Closeable {
    private val lame = Lame()
    private val flags: LameGlobalFlags
    private val left = IntArray(1152)
    private val right = IntArray(1152)
    private val encoded = ByteArray(16384)
    private var finished = false

    init {
        require(channels in 1..2)
        val gain = GainAnalysis()
        val bits = BitStream()
        val presets = Presets()
        val quantizePrivate = QuantizePVT()
        val quantize = Quantize()
        val vbr = VBRTag()
        val version = Version()
        val id3 = ID3Tag()
        val reservoir = Reservoir()
        val takehiro = Takehiro()
        val mpg = MPGLib()
        val decoder = Interface()
        val common = Common()
        lame.setModules(gain, bits, presets, quantizePrivate, quantize, vbr, version, id3, mpg)
        bits.setModules(gain, mpg, version, vbr)
        id3.setModules(bits, version)
        presets.setModules(lame)
        quantize.setModules(bits, reservoir, quantizePrivate, takehiro)
        quantizePrivate.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bits)
        takehiro.setModules(quantizePrivate)
        vbr.setModules(lame, bits, version)
        mpg.setModules(decoder, common)
        decoder.setModules(vbr, common)
        flags = lame.lame_init().apply {
            num_channels = channels
            in_samplerate = sampleRate
            out_samplerate = sampleRate
            brate = if (channels == 1) 128 else 192
            mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
            quality = 5
            VBR = VbrMode.vbr_off
            bWriteVbrTag = false // CBR stream; no placeholder seek frame requiring a later rewrite.
            write_id3tag_automatic = false
            findReplayGain = false
        }
        if (lame.lame_init_params(flags) < 0) {
            lame.lame_close(flags)
            error("MP3 encoder does not support this audio format")
        }
    }

    fun encode(pcm: ByteBuffer): Int {
        check(!finished)
        require(pcm.remaining() % (channels * 2) == 0) { "Incomplete PCM frame" }
        val totalFrames = pcm.remaining() / (channels * 2)
        var remaining = totalFrames
        while (remaining > 0) {
            val frames = minOf(remaining, left.size)
            for (i in 0 until frames) {
                // lame_encode_buffer_int expects signed full-scale 32-bit PCM.
                left[i] = pcm.short.toInt() shl 16
                right[i] = if (channels == 2) pcm.short.toInt() shl 16 else left[i]
            }
            val size = lame.lame_encode_buffer_int(flags, left, right, frames, encoded, 0, encoded.size)
            check(size >= 0) { "MP3 encoding failed" }
            output.write(encoded, 0, size)
            remaining -= frames
        }
        return totalFrames
    }

    fun finish() {
        check(!finished)
        val size = lame.lame_encode_flush(flags, encoded, 0, encoded.size)
        check(size >= 0) { "MP3 flush failed" }
        output.write(encoded, 0, size)
        finished = true
    }

    override fun close() {
        lame.lame_close(flags)
    }
}
