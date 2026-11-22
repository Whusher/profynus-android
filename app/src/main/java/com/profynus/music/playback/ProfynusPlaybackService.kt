package com.profynus.music.playback


import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.profynus.music.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Owns audio independently of the Activity; Media3 builds system media controls. */
class ProfynusPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var metadata: PlaybackMetadata? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        val activityIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()
        metadata = PlaybackMetadata(applicationContext, exoPlayer, serviceScope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        serviceScope.cancel()
        metadata?.release()
        metadata = null
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
