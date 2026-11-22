package com.profynus.music.youtube

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/** Fetch public thumbnail bytes only; never forwards WebView cookies or signed media URLs. */
internal object VideoCoverDownloader {
    suspend fun download(address: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL(address)
        require(url.protocol == "https" && url.userInfo == null &&
            (url.host == "ytimg.com" || url.host.endsWith(".ytimg.com"))) { "Unsupported thumbnail host" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            check(connection.responseCode == 200) { "Cover unavailable" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 4 * 1024 * 1024) { "Cover too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val scale = minOf(1f, 1024f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                    output.toByteArray().also { check(it.size <= 2 * 1024 * 1024) }
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            connection.disconnect()
        }
    }
}
