package com.profynus.music.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decode and encode overlap, but a single worker owns LAME and writes one ordered stream.
 * Four reusable buffers cap stereo PCM at 128 KiB. A buffer belongs to exactly one stage.
 * Must be used within a coroutineScope that also owns the output stream's lifetime.
 */
internal class OrderedPcmEncoder(
    sampleRate: Int,
    private val channels: Int,
    output: OutputStream,
    scope: CoroutineScope,
    onEncodedFrames: (Long) -> Unit,
) {
    private val free = Channel<ByteBuffer>(4)
    private val pending = Channel<ByteBuffer>(4)
    private var filling: ByteBuffer? = null
    private var finished = false

    init {
        repeat(4) {
            check(free.trySend(ByteBuffer.allocateDirect(8192 * channels * 2)
                .order(ByteOrder.nativeOrder())).isSuccess)
        }
    }

    private val worker = scope.async(Dispatchers.Default) {
        PcmMp3Encoder(sampleRate, channels, output).use { encoder ->
            var frames = 0L
            for (buffer in pending) {
                ensureActive()
                frames += encoder.encode(buffer)
                buffer.clear()
                check(free.trySend(buffer).isSuccess)
                onEncodedFrames(frames)
            }
            ensureActive()
            encoder.finish()
        }
    }

    suspend fun encode(pcm: ByteBuffer): Int {
        check(!finished)
        require(pcm.order() == ByteOrder.nativeOrder())
        require(pcm.remaining() % (channels * 2) == 0) { "Incomplete PCM frame" }
        val frames = pcm.remaining() / (channels * 2)
        while (pcm.hasRemaining()) {
            val buffer = filling ?: free.receive().also { filling = it }
            val count = minOf(buffer.remaining(), pcm.remaining())
            val limit = pcm.limit()
            try {
                pcm.limit(pcm.position() + count)
                buffer.put(pcm)
            } finally { pcm.limit(limit) }
            if (!buffer.hasRemaining()) {
                buffer.flip()
                pending.send(buffer)
                filling = null
            }
        }
        return frames
    }

    suspend fun finish() {
        check(!finished)
        finished = true
        filling?.let { buffer ->
            buffer.flip()
            if (buffer.hasRemaining()) pending.send(buffer)
        }
        filling = null
        pending.close()
        worker.await()
    }
}
