package com.unmuted.app.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.unmuted.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

private const val SERVICE_TAG = "UnmutedPlaybackService"

object PlaybackSessionContract {
    const val EXTRA_VOICE_DISGUISE = "unmuted.extra.VOICE_DISGUISE"
    const val EXTRA_BASE_SPEED = "unmuted.extra.BASE_SPEED"
    const val EXTRA_SESSION_MEDIA_ID = "unmuted.extra.SESSION_MEDIA_ID"
    const val ACTION_APPLY_AUDIO_PARAMS = "unmuted.action.APPLY_AUDIO_PARAMS"
}

@OptIn(UnstableApi::class)
object UnmutedExoCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "exo_media_cache").apply { mkdirs() }
                val evictor = LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L)
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, dbProvider).also { cache = it }
            }
        }
    }
}

@OptIn(UnstableApi::class)
class UnmutedPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var currentBaseSpeed: Float = 1.0f

    private val applyParamsCommand = SessionCommand(PlaybackSessionContract.ACTION_APPLY_AUDIO_PARAMS, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        val simpleCache = UnmutedExoCache.getInstance(this)
        val upstreamFactory = DefaultDataSource.Factory(this)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyPlaybackSpeed(player, mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    applyPlaybackSpeed(player, player.currentMediaItem)
                }
            }
        })

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(applyParamsCommand)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == PlaybackSessionContract.ACTION_APPLY_AUDIO_PARAMS) {
                    val speed = args.getFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, currentBaseSpeed)
                    currentBaseSpeed = speed.coerceIn(0.75f, 2.5f)
                    exoPlayer?.let { p ->
                        try {
                            p.playbackParameters = PlaybackParameters(currentBaseSpeed, 1.0f)
                        } catch (e: Exception) {
                            Log.w(SERVICE_TAG, "PlaybackParameters update failed", e)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyPlaybackSpeed(player: ExoPlayer, item: MediaItem?) {
        val extras = item?.mediaMetadata?.extras
        val itemSpeed = extras?.getFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, 0f) ?: 0f
        if (itemSpeed > 0f) {
            currentBaseSpeed = itemSpeed.coerceIn(0.75f, 2.5f)
        }
        try {
            player.playbackParameters = PlaybackParameters(currentBaseSpeed, 1.0f)
        } catch (e: Exception) {
            Log.w(SERVICE_TAG, "PlaybackParameters update failed", e)
        }
    }
}
