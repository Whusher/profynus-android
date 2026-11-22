package com.profynus.music.media

import java.io.Closeable
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Native LAME, fed in bounded batches. One encoder owns one ordered MP3 bitstream. */
internal class PcmMp3Encoder(
    sampleRate: Int,
    private val channels: Int,
    private val output: OutputStream
) : Closeable {
    private var handle = NativeMp3.create(sampleRate, channels)
    private val encoded = ByteArray(32768)
    private val scratch by lazy { ByteBuffer.allocateDirect(8192 * channels * 2).order(ByteOrder.nativeOrder()) }
    private var finished = false

    fun encode(pcm: ByteBuffer): Int {
        check(handle != 0L && !finished)
        require(pcm.remaining() % (channels * 2) == 0) { "Incomplete PCM frame" }
        require(pcm.order() == ByteOrder.nativeOrder()) { "PCM must use native byte order" }
        val total = pcm.remaining() / (channels * 2)
        while (pcm.hasRemaining()) {
            val frames = minOf(8192, pcm.remaining() / (channels * 2))
            val bytes = frames * channels * 2
            val input: ByteBuffer
            val offset: Int
            if (pcm.isDirect) {
                input = pcm
                offset = pcm.position()
                pcm.position(pcm.position() + bytes)
            } else {
                input = scratch
                input.clear()
                val slice = pcm.slice()
                slice.limit(bytes)
                input.put(slice)
                pcm.position(pcm.position() + bytes)
                offset = 0
            }
            val size = NativeMp3.encode(handle, input, offset, frames, encoded)
            output.write(encoded, 0, size)
        }
        return total
    }

    fun finish() {
        check(handle != 0L && !finished)
        val size = NativeMp3.finish(handle, encoded)
        output.write(encoded, 0, size)
        finished = true
    }

    override fun close() {
        if (handle != 0L) {
            NativeMp3.close(handle)
            handle = 0L
        }
    }
}

internal object NativeMp3 {
    init { System.loadLibrary("profynus_mp3") }
    external fun create(sampleRate: Int, channels: Int): Long
    external fun encode(handle: Long, pcm: ByteBuffer, offset: Int, frames: Int, output: ByteArray): Int
    external fun finish(handle: Long, output: ByteArray): Int
    external fun close(handle: Long)
}
