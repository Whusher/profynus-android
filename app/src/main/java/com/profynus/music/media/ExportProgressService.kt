package com.profynus.music.media

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.profynus.music.MainActivity
import com.profynus.music.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread session state shared by the screen and the private foreground service. */
internal object ExportProgress {
    data class State(
        val session: String,
        val text: String,
        val percent: Int?,
        val running: Boolean = true
    )
    private val mutableState = MutableStateFlow<State?>(null)
    val state = mutableState.asStateFlow()
    private var cancelAction: ((String) -> Unit)? = null

    fun start(context: Context, session: String, cancel: (String) -> Unit) {
        check(mutableState.value?.running != true) { "Another export is still running" }
        cancelAction = cancel
        mutableState.value = State(session, "Preparing audio download…", null)
        try {
            ContextCompat.startForegroundService(context, Intent(context, ExportProgressService::class.java)
                .setAction(ExportProgressService.START).putExtra("session", session))
        } catch (e: RuntimeException) {
            cancelAction = null
            mutableState.value = null
            throw e
        }
    }

    fun update(session: String, text: String, percent: Int? = null) {
        val current = mutableState.value ?: return
        if (current.session == session && current.running) {
            mutableState.value = current.copy(text = text, percent = percent?.coerceIn(0, 100))
        }
    }

    fun finish(session: String, text: String) {
        val current = mutableState.value ?: return
        if (current.session == session) {
            cancelAction = null
            mutableState.value = current.copy(text = text, percent = null, running = false)
        }
    }

    fun cancel(session: String, reason: String = "Export cancelled. Partial files discarded.") {
        if (mutableState.value?.let { it.session == session && it.running } == true) {
            cancelAction?.invoke(reason)
        }
    }
}

class ExportProgressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var session: String? = null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Audio downloads", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Download and MP3 conversion progress" })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getStringExtra("session")
        val state = ExportProgress.state.value
        if (intent?.action == CANCEL) {
            requested?.let { ExportProgress.cancel(it) }
            if (state?.running != true) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (state == null || state.session != requested || !state.running) {
            // Satisfy startForegroundService even if cancellation won the startup race.
            ServiceCompat.startForeground(this, ID, notification(
                state ?: ExportProgress.State("", "Export finished", null, false)
            ), serviceTypes())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        session = state.session
        ServiceCompat.startForeground(this, ID, notification(state), serviceTypes())
        if (!observing) {
            observing = true
            scope.launch {
                ExportProgress.state.collect { update ->
                    if (update == null) return@collect
                    if (update.running) {
                        session = update.session
                        post(notification(update))
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        post(notification(update))
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun serviceTypes(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
        if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0

    private fun notification(state: ExportProgress.State): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_export_notification)
            .setContentTitle("Profynus Music")
            .setContentText(state.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.text))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(state.running)
            .setAutoCancel(!state.running)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (state.running) {
            builder.setProgress(100, state.percent ?: 0, state.percent == null)
            val cancel = PendingIntent.getService(this, 0,
                Intent(this, ExportProgressService::class.java).setAction(CANCEL)
                    .setData(Uri.parse("profynus://export/${state.session}/cancel"))
                    .putExtra("session", state.session),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_export_notification, "Cancel", cancel)
        }
        return builder.build()
    }

    private fun post(notification: Notification) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            try { manager.notify(ID, notification) } catch (_: SecurityException) { /* Permission revoked. */ }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        session?.let { ExportProgress.cancel(it, "Android stopped the export service. Retry the download.") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.let { ExportProgress.cancel(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session?.let { ExportProgress.cancel(it, "Export service stopped. Retry the download.") }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val START = "com.profynus.music.EXPORT_START"
        internal const val CANCEL = "com.profynus.music.EXPORT_CANCEL"
        private const val CHANNEL = "audio_exports"
        private const val ID = 4101
    }
}
