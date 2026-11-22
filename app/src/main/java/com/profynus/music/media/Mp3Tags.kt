package com.profynus.music.media

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** ID3v2.4 header for newly encoded (untagged) MP3 audio. Never rewrites existing files. */
internal object Mp3Tags {
    private fun synchsafe(size: Int) = byteArrayOf(
        ((size ushr 21) and 127).toByte(), ((size ushr 14) and 127).toByte(),
        ((size ushr 7) and 127).toByte(), (size and 127).toByte(),
    )
    fun header(title: String, artist: String, album: String, coverJpeg: ByteArray?): ByteArray {
        require(coverJpeg == null || coverJpeg.size <= 2 * 1024 * 1024)
        val frames = ByteArrayOutputStream()
        fun frame(id: String, payload: ByteArray) {
            DataOutputStream(frames).apply {
                writeBytes(id)
                write(synchsafe(payload.size))
                writeShort(0)
                write(payload)
            }
        }
        fun text(id: String, value: String) {
            if (value.isNotBlank()) {
                // ID3v2.4 supports UTF-8, independent of platform byte order.
                frame(id, byteArrayOf(3) + value.replace("\u0000", "").take(300).toByteArray(Charsets.UTF_8) + byteArrayOf(0))
            }
        }
        text("TIT2", title)
        text("TPE1", artist)
        text("TALB", album)
        coverJpeg?.let {
            frame("APIC", byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) +
                byteArrayOf(0, 3, 0) + it) // Front cover, empty description.
        }
        val body = frames.toByteArray()
        val size = body.size
        return byteArrayOf(73, 68, 51, 4, 0, 0) + synchsafe(size) + body
    }
}
