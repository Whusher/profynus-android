package com.profynus.music.youtube

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
internal fun DownloadMp3Button(enabled: Boolean, onDownload: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentDownload by rememberUpdatedState(onDownload)
    var rationale by remember { mutableStateOf(false) }
    var settingsLink by remember { mutableStateOf(false) }
    val history = remember { context.getSharedPreferences("notification_prompt", Context.MODE_PRIVATE) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        settingsLink = !granted
        currentDownload() // Permission denial must not block the download.
    }
    fun request() {
        history.edit().putBoolean("asked", true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Column {
        Button(enabled = enabled, onClick = {
            when {
                Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> currentDownload()
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.POST_NOTIFICATIONS
                ) -> rationale = true
                history.getBoolean("asked", false) -> {
                    settingsLink = true
                    currentDownload()
                }
                else -> request()
            }
        }) { Text("Download MP3") }
        if (settingsLink) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        }) { Text("Enable notifications in Settings") }
    }
    if (rationale) AlertDialog(
        onDismissRequest = { rationale = false },
        title = { Text("Download notifications") },
        text = { Text("Show download and conversion progress, with a Cancel button, while you use other apps.") },
        confirmButton = { TextButton(onClick = { rationale = false; request() }) { Text("Continue") } },
        dismissButton = {
            TextButton(onClick = { rationale = false; settingsLink = true; currentDownload() }) {
                Text("Download without notifications")
            }
        }
    )
}
