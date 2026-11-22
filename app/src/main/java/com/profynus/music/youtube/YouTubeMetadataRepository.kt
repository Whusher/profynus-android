package com.profynus.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Metadata only. oEmbed does not return audio/video download URLs or duration. */
data class YouTubeVideoMetadata(
    val video: YouTubeVideoReference,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String
)

class YouTubeMetadataRepository {
    suspend fun fetch(video: YouTubeVideoReference): YouTubeVideoMetadata =
        withContext(Dispatchers.IO) {
            val watchUrl = URLEncoder.encode(video.watchUrl, "UTF-8")
            val endpoint = URL("https://www.youtube.com/oembed?format=json&url=$watchUrl")
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")

                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException(
                        "YouTube metadata unavailable (HTTP $status). " +
                            "This video may be private, removed, or unavailable for embedding."
                    )
                }

                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var count = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > 64 * 1024) throw IOException("Metadata response too large")
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                val title = json.getString("title")
                val channel = json.getString("author_name")
                val thumbnail = json.optString("thumbnail_url", "")
                require(title.isNotBlank() && channel.isNotBlank()) {
                    "YouTube returned incomplete video metadata."
                }
                YouTubeVideoMetadata(video, title, channel, thumbnail)
            } finally {
                connection.disconnect()
            }
        }
}
