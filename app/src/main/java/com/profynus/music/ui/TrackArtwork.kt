package com.profynus.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.profynus.music.data.decodeArtwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared, bounded bitmap cache for covers in the list, player and media session. */
internal object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(context: Context, uri: String, maxSize: Int = 768): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$maxSize:$uri"
            cache.get(key) ?: try {
                decodeArtwork(context, Uri.parse(uri), maxSize).also { cache.put(key, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
}

@Composable
fun TrackArtwork(
    artworkUri: String?,
    description: String?,
    modifier: Modifier = Modifier,
    maxSize: Int = 768,
    fallback: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, artworkUri, maxSize) {
        value = null
        value = artworkUri?.let { ArtworkLoader.load(context, it, maxSize) }
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallback != null) {
            fallback()
        } else {
            Icon(Icons.Default.MusicNote, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
