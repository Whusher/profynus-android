package com.profynus.media.transport

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Byte-range transport for ALREADY-RESOLVED, AUTHORIZED HTTPS media URLs.
 * This class does not resolve YouTube watch URLs, signatures, or DRM.
 * Network work is blocking: run it on Dispatchers.IO or a worker thread on Android.
 */
class RangeDownloader(
    private val workers: Int = 4,
    private val chunkBytes: Long = 8L * 1024 * 1024,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val allowHttpForLocalTests: Boolean = false
) {
    init {
        require(workers in 1..8) { "workers must be 1..8" }
        require(chunkBytes in 64 * 1024L..64 * 1024 * 1024L)
    }

    data class Result(val file: File, val bytes: Long, val rangesUsed: Boolean)
    data class Progress(val transferred: Long, val total: Long?)
    private data class Probe(val length: Long?, val strongETag: String?, val rangeSupported: Boolean)

    fun download(
        source: URL,
        destination: File,
        progress: (Progress) -> Unit = {}
    ): Result {
        validateUrl(source)
        require(!destination.isDirectory)
        destination.parentFile?.mkdirs()
        val partial = File(destination.absolutePath + ".partial")
        // This prototype deliberately starts a clean file rather than resuming old checkpoints.
        partial.delete()

        try {
            val probe = probe(source)
            val transferred = AtomicLong(0)
            if (probe.rangeSupported && probe.length != null && probe.length > 0) {
                RandomAccessFile(partial, "rw").use { raf ->
                    raf.setLength(probe.length)
                    val channel = raf.channel
                    val nextOffset = AtomicLong(0)
                    val pool = Executors.newFixedThreadPool(workers)
                    try {
                        val jobs = (0 until workers).map {
                            pool.submit(Callable {
                                while (true) {
                                    if (Thread.currentThread().isInterrupted) {
                                        throw InterruptedException("Download interrupted")
                                    }
                                    val start = nextOffset.getAndAdd(chunkBytes)
                                    if (start >= probe.length) break
                                    val end = min(probe.length - 1, start + chunkBytes - 1)
                                    downloadRange(
                                        source, channel, start, end, probe.length,
                                        probe.strongETag, transferred, progress
                                    )
                                }
                            })
                        }
                        for (job in jobs) job.get()
                    } catch (e: Exception) {
                        pool.shutdownNow()
                        throw IOException("Parallel transfer failed", e)
                    } finally {
                        pool.shutdownNow()
                    }
                    channel.force(true)
                }
                check(transferred.get() == probe.length) {
                    "Incomplete download: ${transferred.get()}/${probe.length}"
                }
            } else {
                downloadSequential(source, partial, probe.length, transferred, progress)
            }

            try {
                Files.move(
                    partial.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return Result(destination, transferred.get(), probe.rangeSupported && probe.length != null)
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    private fun probe(url: URL): Probe {
        val connection = connect(url).apply { setRequestProperty("Range", "bytes=0-0") }
        return try {
            when (connection.responseCode) {
                206 -> {
                    val match = parseContentRange(connection.getHeaderField("Content-Range"))
                    require(match.first == 0L && match.second == 0L) {
                        "Invalid response to range probe"
                    }
                    require(match.third > 0) { "Unknown resource length" }
                    requireIdentity(connection)
                    val etag = connection.getHeaderField("ETag")
                        ?.takeIf { !it.startsWith("W/") }
                    Probe(match.third, etag, true)
                }
                200 -> Probe(connection.contentLengthLong.takeIf { it >= 0 }, null, false)
                else -> throw IOException("Probe failed: HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadRange(
        url: URL,
        channel: FileChannel,
        start: Long,
        end: Long,
        total: Long,
        etag: String?,
        transferred: AtomicLong,
        progress: (Progress) -> Unit
    ) {
        val connection = connect(url).apply {
            setRequestProperty("Range", "bytes=$start-$end")
            if (etag != null) setRequestProperty("If-Range", etag)
        }
        try {
            if (connection.responseCode != 206) {
                throw IOException("Expected HTTP 206, got HTTP ${connection.responseCode}")
            }
            requireIdentity(connection)
            val actual = parseContentRange(connection.getHeaderField("Content-Range"))
            if (actual != Triple(start, end, total)) {
                throw IOException("Range mismatch: $actual expected $start-$end/$total")
            }
            val buffer = ByteArray(128 * 1024)
            var position = start
            connection.inputStream.use { input ->
                while (position <= end) {
                    val wanted = min(buffer.size.toLong(), end - position + 1).toInt()
                    val count = input.read(buffer, 0, wanted)
                    if (count < 0) throw IOException("Unexpected EOF at $position")
                    if (count == 0) continue
                    val bytes = ByteBuffer.wrap(buffer, 0, count)
                    while (bytes.hasRemaining()) {
                        position += channel.write(bytes, position)
                    }
                    progress(Progress(transferred.addAndGet(count.toLong()), total))
                }
                if (input.read() != -1) throw IOException("Server returned extra range data")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadSequential(
        url: URL,
        partial: File,
        expectedLength: Long?,
        transferred: AtomicLong,
        progress: (Progress) -> Unit
    ) {
        val connection = connect(url)
        try {
            if (connection.responseCode != 200) {
                throw IOException("Expected HTTP 200, got HTTP ${connection.responseCode}")
            }
            requireIdentity(connection)
            val buffer = ByteArray(128 * 1024)
            partial.outputStream().use { output ->
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        progress(Progress(transferred.addAndGet(n.toLong()), expectedLength))
                    }
                }
            }
            if (expectedLength != null && expectedLength != transferred.get()) {
                throw IOException("Incomplete download")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(url: URL): HttpURLConnection {
        validateUrl(url)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false // validate redirects separately in production
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "ProfynusMediaPrototype/0.1")
        }
    }

    private fun validateUrl(url: URL) {
        require(url.protocol == "https" || (allowHttpForLocalTests && url.protocol == "http" &&
                (url.host == "127.0.0.1" || url.host == "localhost"))) {
            "Only HTTPS is allowed (except loopback test URLs)"
        }
        require(url.userInfo == null) { "Credentials in URLs are not allowed" }
    }

    private fun requireIdentity(connection: HttpURLConnection) {
        val encoding = connection.getHeaderField("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            throw IOException("Cannot safely byte-range a compressed HTTP representation")
        }
    }

    private fun parseContentRange(value: String?): Triple<Long, Long, Long> {
        val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$")
            .matchEntire(value.orEmpty()) ?: throw IOException("Invalid Content-Range: $value")
        val (first, last, total) = match.destructured
        return Triple(first.toLong(), last.toLong(), total.toLong())
    }
}
