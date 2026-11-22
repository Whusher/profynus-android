package com.profynus.music

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profynus.media.transport.RangeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** Temporary diagnostic screen. No YouTube extraction or MediaStore output yet. */
@Composable
fun RangeTransferTestScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to test Android HTTP ranges") }
    var bytesReceived by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    val expectedSha256 = "8930308b3d034d53f5484682a27fe2d2eb1e922a61095bea3d854158a16ab736"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("PROFYNUS", style = MaterialTheme.typography.headlineLarge)
        Text("Android transfer diagnostic", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text("Source: USB-forwarded local HTTP server")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    running = true
                    bytesReceived = 0L
                    totalBytes = 0L
                    status = "Downloading..."
                    try {
                        val result: Triple<RangeDownloader.Result, String, File> =
                            withContext(Dispatchers.IO) {
                            val output = File(context.cacheDir, "profynus-range-test.bin")
                            val lastUpdateMs = AtomicLong(0L)
                                val transferResult = RangeDownloader(
                                    workers = 4,
                                    chunkBytes = 4L * 1024 * 1024,
                                    allowHttpForLocalTests = true
                                ).download(
                                    source = URL("http://127.0.0.1:8765/media.bin"),
                                    destination = output
                                ) { p ->
                                    val now = SystemClock.elapsedRealtime()
                                    val previous = lastUpdateMs.get()

                                    if (
                                        now - previous >= 150 &&
                                        lastUpdateMs.compareAndSet(previous, now)
                                    ) {
                                        main.post {
                                            bytesReceived = p.transferred
                                            totalBytes = p.total ?: 0L
                                        }
                                    }
                                }
                            val digest = MessageDigest.getInstance("SHA-256")
                            output.inputStream().buffered().use { input ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    digest.update(buffer, 0, n)
                                }
                            }
                            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                                Triple(transferResult, actualSha256, output)
                        }
                        bytesReceived = result.first.bytes
                        totalBytes = result.first.bytes
                        status = if (result.second == expectedSha256) {
                            "PASS: ${result.first.bytes} bytes, parallel ranges=${result.first.rangesUsed}, SHA-256 verified. File in app cache."
                        } else {
                            "FAIL: SHA-256 mismatch: ${result.second}"
                        }
                    } catch (e: Exception) {
                        status = "ERROR: ${e.message ?: e::class.java.simpleName}"
                    } finally {
                        running = false
                    }
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "Downloading..." else "Run range transfer test")
        }
        Spacer(Modifier.height(12.dp))
        if (totalBytes > 0) {
            Text("Transferred: $bytesReceived / $totalBytes bytes")
            Text("Progress: ${bytesReceived * 100L / totalBytes}%")
        }
        Spacer(Modifier.height(12.dp))
        Text(status)
    }
}
