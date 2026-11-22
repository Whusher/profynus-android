package com.profynus.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profynus.music.youtube.YouTubeInspectScreen
import com.profynus.music.youtube.YouTubeBrowserDiagnosticScreen
import com.profynus.music.youtube.YouTubeStreamDiagnosticScreen


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                YouTubeBrowserDiagnosticScreen()
//                YouTubeStreamDiagnosticScreen()
//                YouTubeInspectScreen()
//                RangeTransferTestScreen()
//                ProfynusHome()
            }
        }
    }
}

@Composable
fun ProfynusHome() {
    var videoUrl by rememberSaveable {
        mutableStateOf("")
    }

    var status by rememberSaveable {
        mutableStateOf("Ready")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "PROFYNUS",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Media Engine",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            label = { Text("Video URL") },
            placeholder = {
                Text("https://www.youtube.com/watch?v=...")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                status = if (videoUrl.isBlank()) {
                    "Please enter a video URL."
                } else {
                    "URL received. Resolver not connected yet."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze Video")
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Engine status: $status",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}