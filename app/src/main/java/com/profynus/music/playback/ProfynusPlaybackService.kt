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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ProfynusPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var metadata: PlaybackMetadata? = null
    private var memory: PlaybackMemory? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(androidx.media3.exoplayer.audio.TeeAudioProcessor(AudioEnergy())))
                    .build()
            }
        }
        val exoPlayer = ExoPlayer.Builder(this, renderers)
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
        memory = PlaybackMemory(applicationContext, exoPlayer, serviceScope)

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
        memory?.release()
        memory = null
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
