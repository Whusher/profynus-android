package com.profynus.music.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mp3TagsTest {
    @Test fun androidReadsUnicodeTagsAndEmbeddedCoverWithoutLosingAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("tagged-", ".mp3", context.cacheDir)
        val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val cover = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, this) }.toByteArray()
        bitmap.recycle()
        try {
            file.outputStream().use { output ->
                output.write(Mp3Tags.header("Canción 日本語", "Artista", "Álbum", cover))
                PcmMp3Encoder(44100, 1, output).use { encoder ->
                    val pcm = ByteBuffer.allocate(44100 * 2).order(ByteOrder.nativeOrder())
                    repeat(44100) { pcm.putShort((10000 * kotlin.math.sin(it * 2 * Math.PI * 440 / 44100)).toInt().toShort()) }
                    pcm.flip()
                    encoder.encode(pcm)
                    encoder.finish()
                }
            }
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(file.absolutePath)
                assertEquals("Canción 日本語", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                assertEquals("Artista", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                assertEquals("Álbum", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                assertArrayEquals(cover, reader.embeddedPicture)
                assertTrue(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 900..1300)
            } finally { reader.release() }
        } finally { file.delete() }
    }

    @Test fun missingCoverStillProducesReadableTags() {
        val bytes = Mp3Tags.header("Title", "", "", null)
        val metadata = androidx.media3.extractor.metadata.id3.Id3Decoder().decode(bytes, bytes.size)!!
        assertEquals(1, metadata.length())
        val title = metadata[0] as androidx.media3.extractor.metadata.id3.TextInformationFrame
        assertEquals("TIT2", title.id)
        assertEquals("Title", title.values.first())
    }
}
