package com.unmuted.app.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.unmuted.app.core.PlaybackSessionContract
import com.unmuted.app.core.UnmutedPlaybackService
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AudioPlayerManager"

enum class PlaybackPhase {
    QUOTED_CLIP,
    MAIN
}

@Immutable
data class NowPlayingMeta(
    val title: String = "Unmuted Voice",
    val speakerName: String = "Unmuted Speaker",
    val subtitle: String = "Voice Take",
    val accentKey: String = "CORAL",
    val anonymousMode: Boolean = false,
    val avatarBase64: String? = null,
    val sourcePostId: String? = null,
    val isClapback: Boolean = false,
    val quotedAuthorName: String? = null
)

@Immutable
data class PlaybackState(
    val activeMediaId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val voiceDisguise: VoiceDisguise = VoiceDisguise.NONE,
    val activePhase: PlaybackPhase = PlaybackPhase.MAIN,
    val quoteSplitFraction: Float = 0f,
    val combinedPositionMs: Long = 0L,
    val combinedDurationMs: Long = 0L,
    val nowPlaying: NowPlayingMeta? = null,
    val radioModeEnabled: Boolean = false,
    val error: String? = null
)

private data class ClapbackSessionMeta(
    val sessionId: String,
    val quoteSpanMs: Long,
    val mainDurationMs: Long,
    val quoteDisguise: VoiceDisguise,
    val mainDisguise: VoiceDisguise
) {
    val totalDurationMs: Long get() = (quoteSpanMs + mainDurationMs).coerceAtLeast(1L)
    val splitFraction: Float get() = (quoteSpanMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.08f, 0.85f)
}

class AudioPlayerManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerDeferred = CompletableDeferred<MediaController>()
    private var mediaController: MediaController? = null

    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var activeClapback: ClapbackSessionMeta? = null

    private val _radioModeEnabled = MutableStateFlow(false)
    val radioModeEnabled: StateFlow<Boolean> = _radioModeEnabled.asStateFlow()

    private val postMap = ConcurrentHashMap<String, VoicePost>()
    private var cachedQueue: List<VoicePost> = emptyList()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val ctrl = mediaController ?: return
            _state.value = _state.value.copy(
                playing = isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING
            )
            if (isPlaying) startProgressTicker() else progressJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = mediaController ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(buffering = true)
                }
                Player.STATE_READY -> {
                    syncStateFromController(ctrl)
                }
                Player.STATE_ENDED -> {
                    progressJob?.cancel()
                    val cb = activeClapback
                    _state.value = _state.value.copy(
                        playing = false,
                        buffering = false,
                        positionMs = 0L,
                        combinedPositionMs = 0L,
                        activePhase = if (cb != null) PlaybackPhase.QUOTED_CLIP else PlaybackPhase.MAIN
                    )
                }
                Player.STATE_IDLE -> {
                    progressJob?.cancel()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = mediaController ?: return
            val postId = mediaItem?.mediaId
            if (!postId.isNullOrBlank()) {
                val post = postMap[postId]
                val meta = if (post != null) {
                    val headline = post.headlineText?.takeIf { it.isNotBlank() }
                        ?: post.captionSegments.firstOrNull()?.text?.take(64)
                        ?: post.transcript.take(64).ifBlank { "Voice by ${post.displaySpeakerName()}" }
                    val roomLabel = post.roomSlug?.takeIf { it.isNotBlank() }?.let { "r/$it" } ?: "Unmuted"
                    NowPlayingMeta(
                        title = headline,
                        speakerName = post.displaySpeakerName(),
                        subtitle = roomLabel,
                        accentKey = post.authorAccentKey,
                        anonymousMode = post.anonymousMode,
                        avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
                        sourcePostId = post.id,
                        isClapback = post.hasQuoteClip(),
                        quotedAuthorName = post.quotedAuthorName
                    )
                } else {
                    val md = mediaItem.mediaMetadata
                    NowPlayingMeta(
                        title = md.title?.toString() ?: "Unmuted Voice",
                        speakerName = md.artist?.toString() ?: "Unmuted Speaker",
                        subtitle = md.albumTitle?.toString() ?: "Unmuted",
                        sourcePostId = postId
                    )
                }
                val disguise = post?.disguiseEnum() ?: VoiceDisguise.NONE
                _state.value = _state.value.copy(
                    activeMediaId = postId,
                    nowPlaying = meta,
                    voiceDisguise = disguise,
                    activePhase = PlaybackPhase.MAIN
                )
            }
            syncStateFromController(ctrl)
        }
    }

    init {
        initAudioContext(context)
        connectToPlaybackService()
    }

    private fun connectToPlaybackService() {
        try {
            val token = SessionToken(appContext, ComponentName(appContext, UnmutedPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        val ctrl = future.get()
                        mediaController = ctrl
                        ctrl.addListener(playerListener)
                        if (!controllerDeferred.isCompleted) {
                            controllerDeferred.complete(ctrl)
                        }
                        if (ctrl.mediaItemCount > 0) {
                            syncStateFromController(ctrl)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed connecting to UnmutedPlaybackService", e)
                        if (!controllerDeferred.isCompleted) {
                            controllerDeferred.completeExceptionally(e)
                        }
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaController", e)
        }
    }

    private suspend fun awaitController(): MediaController? {
        mediaController?.let { return it }
        return try {
            controllerDeferred.await()
        } catch (_: Exception) {
            controllerDeferred = CompletableDeferred()
            connectToPlaybackService()
            runCatching { controllerDeferred.await() }.getOrNull()
        }
    }

    fun isCached(mediaId: String): Boolean = false

    fun preloadAudioClips(mediaIds: List<String> = emptyList()) {
        // Handled natively by ExoPlayer SimpleCache
    }

    fun updateCachedQueue(queue: List<VoicePost>) {
        if (queue.isNotEmpty()) {
            cachedQueue = queue
            for (p in queue) postMap[p.id] = p
        }
    }

    fun setRadioMode(enabled: Boolean, activeList: List<VoicePost> = emptyList()) {
        _radioModeEnabled.value = enabled
        _state.value = _state.value.copy(radioModeEnabled = enabled)
        if (activeList.isNotEmpty()) {
            cachedQueue = activeList
            for (p in activeList) postMap[p.id] = p
        }
        sendRadioModeCommand(enabled)
        syncQueueToController()
    }

    fun toggleRadioMode(activeList: List<VoicePost> = emptyList()) {
        setRadioMode(!_radioModeEnabled.value, activeList)
    }

    private fun sendRadioModeCommand(enabled: Boolean) {
        val ctrl = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(PlaybackSessionContract.EXTRA_RADIO_MODE, enabled)
        }
        val cmd = SessionCommand(PlaybackSessionContract.ACTION_SET_RADIO_MODE, Bundle.EMPTY)
        try {
            ctrl.sendCustomCommand(cmd, args)
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending radio mode command", e)
        }
    }

    private fun syncQueueToController() {
        val ctrl = mediaController ?: return
        if (ctrl.mediaItemCount == 0 || !_state.value.playing) return

        if (_radioModeEnabled.value) {
            val currentId = _state.value.activeMediaId ?: return
            val postIdx = cachedQueue.indexOfFirst { it.id == currentId }
            val upcoming = if (postIdx != -1) cachedQueue.drop(postIdx + 1) else cachedQueue.filter { it.id != currentId }
            val existingIds = mutableSetOf<String>()
            for (i in 0 until ctrl.mediaItemCount) {
                existingIds.add(ctrl.getMediaItemAt(i).mediaId)
            }
            val toAdd = upcoming.filter { it.id !in existingIds && !it.effectiveAudioUrl().isNullOrBlank() }
                .mapNotNull { buildPostMediaItem(it, _state.value.speed) }
            if (toAdd.isNotEmpty()) {
                ctrl.addMediaItems(toAdd)
            }
        } else {
            val nextIdx = ctrl.currentMediaItemIndex + 1
            if (ctrl.mediaItemCount > nextIdx) {
                ctrl.removeMediaItems(nextIdx, ctrl.mediaItemCount)
            }
        }
    }

    fun buildPostMediaItem(post: VoicePost, speed: Float = 1.0f): MediaItem? {
        val audioUrl = post.effectiveAudioUrl() ?: return null
        val headline = post.headlineText?.takeIf { it.isNotBlank() }
            ?: post.captionSegments.firstOrNull()?.text?.take(64)
            ?: post.transcript.take(64).ifBlank { "Voice by ${post.displaySpeakerName()}" }
        val roomLabel = post.roomSlug?.takeIf { it.isNotBlank() }?.let { "r/$it" } ?: "Unmuted"

        val meta = NowPlayingMeta(
            title = headline,
            speakerName = post.displaySpeakerName(),
            subtitle = roomLabel,
            accentKey = post.authorAccentKey,
            anonymousMode = post.anonymousMode,
            avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
            sourcePostId = post.id,
            isClapback = post.hasQuoteClip(),
            quotedAuthorName = post.quotedAuthorName
        )

        val extras = Bundle().apply {
            putString(PlaybackSessionContract.EXTRA_VOICE_DISGUISE, post.disguiseEnum().name)
            putFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, speed)
            putString(PlaybackSessionContract.EXTRA_SESSION_MEDIA_ID, post.id)
            putBoolean(PlaybackSessionContract.EXTRA_RADIO_MODE, _radioModeEnabled.value)
        }

        val artworkBytes = if (!meta.anonymousMode && !meta.avatarBase64.isNullOrBlank()) {
            runCatching { Base64.decode(meta.avatarBase64, Base64.DEFAULT) }.getOrNull()
        } else null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(meta.title.ifBlank { "Unmuted Voice" })
            .setArtist(meta.speakerName.ifBlank { "Unmuted Speaker" })
            .setAlbumTitle(meta.subtitle.ifBlank { "Unmuted" })
            .setExtras(extras)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(post.id)
            .setUri(Uri.parse(audioUrl))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun playPost(
        post: VoicePost,
        queue: List<VoicePost> = emptyList(),
        autoPlay: Boolean = false
    ) {
        val audioUrl = post.effectiveAudioUrl() ?: return
        postMap[post.id] = post
        if (queue.isNotEmpty()) {
            cachedQueue = queue
            for (p in queue) postMap[p.id] = p
        }

        if (post.hasQuoteClip() && !post.effectiveQuotedAudioUrl().isNullOrBlank()) {
            val headline = post.headlineText?.takeIf { it.isNotBlank() }
                ?: post.captionSegments.firstOrNull()?.text?.take(64)
                ?: post.transcript.take(64).ifBlank { "Voice by ${post.displaySpeakerName()}" }
            val roomLabel = post.roomSlug?.takeIf { it.isNotBlank() }?.let { "r/$it" } ?: "Unmuted"
            val meta = NowPlayingMeta(
                title = headline,
                speakerName = post.displaySpeakerName(),
                subtitle = roomLabel,
                accentKey = post.authorAccentKey,
                anonymousMode = post.anonymousMode,
                avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
                sourcePostId = post.id,
                isClapback = true,
                quotedAuthorName = post.quotedAuthorName
            )
            togglePlayClapback(
                sessionId = "clap_${post.id}",
                quoteAudioUrl = post.effectiveQuotedAudioUrl()!!,
                quoteStartMs = post.quotedStartMs,
                quoteEndMs = post.quotedEndMs,
                quoteDisguise = post.quotedDisguiseEnum(),
                mainAudioUrl = audioUrl,
                mainDurationHintMs = post.durationMs,
                mainDisguise = post.disguiseEnum(),
                autoPlay = autoPlay,
                metadata = meta
            )
            return
        }

        val current = _state.value
        val ctrl = mediaController
        if ((current.activeMediaId == post.id || current.activeMediaId == audioUrl) && ctrl != null && ctrl.mediaItemCount > 0 && activeClapback == null) {
            if (!autoPlay) {
                if (ctrl.isPlaying) pause() else resume()
            } else if (!ctrl.isPlaying) {
                resume()
            }
            return
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            val headline = post.headlineText?.takeIf { it.isNotBlank() }
                ?: post.captionSegments.firstOrNull()?.text?.take(64)
                ?: post.transcript.take(64).ifBlank { "Voice by ${post.displaySpeakerName()}" }
            val roomLabel = post.roomSlug?.takeIf { it.isNotBlank() }?.let { "r/$it" } ?: "Unmuted"
            val meta = NowPlayingMeta(
                title = headline,
                speakerName = post.displaySpeakerName(),
                subtitle = roomLabel,
                accentKey = post.authorAccentKey,
                anonymousMode = post.anonymousMode,
                avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
                sourcePostId = post.id,
                isClapback = false,
                quotedAuthorName = post.quotedAuthorName
            )

            _state.value = current.copy(
                activeMediaId = post.id,
                buffering = true,
                playing = false,
                positionMs = 0L,
                combinedPositionMs = 0L,
                combinedDurationMs = 0L,
                quoteSplitFraction = 0f,
                activePhase = PlaybackPhase.MAIN,
                voiceDisguise = post.disguiseEnum(),
                nowPlaying = meta,
                radioModeEnabled = _radioModeEnabled.value,
                error = null
            )

            try {
                val controller = awaitController() ?: return@launch
                val primaryItem = buildPostMediaItem(post, _state.value.speed) ?: return@launch
                val mediaItems = mutableListOf(primaryItem)

                if (_radioModeEnabled.value && cachedQueue.isNotEmpty()) {
                    val postIdx = cachedQueue.indexOfFirst { it.id == post.id }
                    val upcoming = if (postIdx != -1) cachedQueue.drop(postIdx + 1) else cachedQueue.filter { it.id != post.id }
                    for (up in upcoming) {
                        if (up.id != post.id && !up.effectiveAudioUrl().isNullOrBlank()) {
                            postMap[up.id] = up
                            buildPostMediaItem(up, _state.value.speed)?.let { mediaItems.add(it) }
                        }
                    }
                }

                controller.setMediaItems(mediaItems, 0, 0L)
                controller.prepare()
                sendAudioParams(controller, _state.value.speed, post.disguiseEnum())
                controller.play()
                startProgressTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Post playback failed", e)
                _state.value = PlaybackState(
                    speed = _state.value.speed,
                    radioModeEnabled = _radioModeEnabled.value,
                    error = e.localizedMessage ?: "Audio clip could not be played."
                )
            }
        }
    }

    fun togglePlayLocalFile(
        mediaId: String,
        file: File,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        metadata: NowPlayingMeta = NowPlayingMeta(title = "Studio Preview", speakerName = "You", subtitle = disguise.badge)
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == mediaId && ctrl != null && ctrl.mediaItemCount > 0) {
            if (ctrl.isPlaying) pause() else resume()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            val controller = awaitController() ?: return@launch
            playSingleUriOnController(
                controller = controller,
                mediaId = mediaId,
                uri = Uri.fromFile(file),
                disguise = disguise,
                speed = _state.value.speed,
                metadata = metadata
            )
        }
    }

    fun togglePlayRemote(
        mediaId: String,
        audioUrl: String,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        autoPlay: Boolean = false,
        metadata: NowPlayingMeta = NowPlayingMeta()
    ) {
        val current = _state.value
        val ctrl = mediaController
        if ((current.activeMediaId == mediaId || current.activeMediaId == audioUrl) && ctrl != null && ctrl.mediaItemCount > 0 && activeClapback == null) {
            if (!autoPlay) {
                if (ctrl.isPlaying) pause() else resume()
            } else if (!ctrl.isPlaying) {
                resume()
            }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            _state.value = current.copy(
                activeMediaId = mediaId,
                buffering = true,
                playing = false,
                positionMs = 0L,
                combinedPositionMs = 0L,
                combinedDurationMs = 0L,
                quoteSplitFraction = 0f,
                activePhase = PlaybackPhase.MAIN,
                voiceDisguise = disguise,
                nowPlaying = metadata,
                radioModeEnabled = _radioModeEnabled.value,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val uri = Uri.parse(audioUrl)
                playSingleUriOnController(controller, mediaId, uri, disguise, _state.value.speed, metadata)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remote audio play failed", e)
                _state.value = PlaybackState(
                    speed = _state.value.speed,
                    radioModeEnabled = _radioModeEnabled.value,
                    error = e.localizedMessage ?: "Audio clip could not be played."
                )
            }
        }
    }

    fun playRangePreview(
        previewId: String,
        audioUrl: String,
        startMs: Long,
        endMs: Long,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        metadata: NowPlayingMeta = NowPlayingMeta(title = "Clapback Clip Preview", subtitle = "Quoted Slice")
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == previewId && ctrl != null && ctrl.isPlaying) {
            pause()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            _state.value = current.copy(
                activeMediaId = previewId,
                buffering = true,
                playing = false,
                activePhase = PlaybackPhase.QUOTED_CLIP,
                quoteSplitFraction = 0f,
                voiceDisguise = disguise,
                nowPlaying = metadata,
                radioModeEnabled = _radioModeEnabled.value,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val clippedItem = buildMediaItem(
                    mediaId = previewId,
                    uri = Uri.parse(audioUrl),
                    disguise = disguise,
                    speed = _state.value.speed,
                    meta = metadata,
                    clipStartMs = startMs.coerceAtLeast(0L),
                    clipEndMs = endMs.coerceAtLeast(startMs + 500L)
                )
                controller.setMediaItem(clippedItem)
                controller.prepare()
                sendAudioParams(controller, _state.value.speed, disguise)
                controller.play()
                startProgressTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Range preview failed", e)
                _state.value = PlaybackState(
                    speed = _state.value.speed,
                    radioModeEnabled = _radioModeEnabled.value,
                    error = "Could not preview clip range."
                )
            }
        }
    }

    fun togglePlayClapback(
        sessionId: String,
        quoteAudioUrl: String,
        quoteStartMs: Long,
        quoteEndMs: Long,
        quoteDisguise: VoiceDisguise,
        mainAudioUrl: String,
        mainDurationHintMs: Long,
        mainDisguise: VoiceDisguise,
        autoPlay: Boolean = false,
        metadata: NowPlayingMeta = NowPlayingMeta(isClapback = true)
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == sessionId && ctrl != null && activeClapback?.sessionId == sessionId && ctrl.mediaItemCount >= 2) {
            if (!autoPlay) {
                if (ctrl.isPlaying) pause() else resume()
            } else if (!ctrl.isPlaying) {
                resume()
            }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = current.copy(
                activeMediaId = sessionId,
                buffering = true,
                playing = false,
                activePhase = PlaybackPhase.QUOTED_CLIP,
                nowPlaying = metadata,
                radioModeEnabled = _radioModeEnabled.value,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val safeStart = quoteStartMs.coerceAtLeast(0L)
                val safeEnd = quoteEndMs.coerceAtLeast(safeStart + 500L)
                val quoteSpan = (safeEnd - safeStart).coerceAtLeast(500L)
                val session = ClapbackSessionMeta(
                    sessionId = sessionId,
                    quoteSpanMs = quoteSpan,
                    mainDurationMs = mainDurationHintMs.coerceAtLeast(1000L),
                    quoteDisguise = quoteDisguise,
                    mainDisguise = mainDisguise
                )
                activeClapback = session

                val quoteMeta = metadata.copy(
                    title = "Quoted: ${metadata.quotedAuthorName ?: "Speaker"}",
                    speakerName = metadata.quotedAuthorName ?: "Quoted Speaker",
                    subtitle = "Clapback Clip"
                )
                val quoteItem = buildMediaItem(
                    mediaId = "${sessionId}_quote",
                    uri = Uri.parse(quoteAudioUrl),
                    disguise = quoteDisguise,
                    speed = _state.value.speed,
                    meta = quoteMeta,
                    clipStartMs = safeStart,
                    clipEndMs = safeEnd
                )
                val mainItem = buildMediaItem(
                    mediaId = "${sessionId}_main",
                    uri = Uri.parse(mainAudioUrl),
                    disguise = mainDisguise,
                    speed = _state.value.speed,
                    meta = metadata
                )

                _state.value = _state.value.copy(
                    activeMediaId = sessionId,
                    activePhase = PlaybackPhase.QUOTED_CLIP,
                    quoteSplitFraction = session.splitFraction,
                    combinedPositionMs = 0L,
                    combinedDurationMs = session.totalDurationMs,
                    voiceDisguise = quoteDisguise,
                    nowPlaying = metadata,
                    radioModeEnabled = _radioModeEnabled.value
                )
                controller.setMediaItems(listOf(quoteItem, mainItem), 0, 0L)
                controller.prepare()
                sendAudioParams(controller, _state.value.speed, quoteDisguise)
                controller.play()
                startProgressTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clapback playback failed", e)
                activeClapback = null
                _state.value = PlaybackState(
                    speed = _state.value.speed,
                    radioModeEnabled = _radioModeEnabled.value,
                    error = "Clapback playback failed."
                )
            }
        }
    }

    private fun playSingleUriOnController(
        controller: MediaController,
        mediaId: String,
        uri: Uri,
        disguise: VoiceDisguise,
        speed: Float,
        metadata: NowPlayingMeta
    ) {
        val item = buildMediaItem(
            mediaId = mediaId,
            uri = uri,
            disguise = disguise,
            speed = speed,
            meta = metadata
        )
        _state.value = _state.value.copy(
            activeMediaId = mediaId,
            activePhase = PlaybackPhase.MAIN,
            quoteSplitFraction = 0f,
            voiceDisguise = disguise,
            nowPlaying = metadata,
            radioModeEnabled = _radioModeEnabled.value,
            error = null
        )
        controller.setMediaItem(item)
        controller.prepare()
        sendAudioParams(controller, speed, disguise)
        controller.play()
        startProgressTicker()
    }

    private fun buildMediaItem(
        mediaId: String,
        uri: Uri,
        disguise: VoiceDisguise,
        speed: Float,
        meta: NowPlayingMeta,
        clipStartMs: Long? = null,
        clipEndMs: Long? = null
    ): MediaItem {
        val extras = Bundle().apply {
            putString(PlaybackSessionContract.EXTRA_VOICE_DISGUISE, disguise.name)
            putFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, speed)
            putString(PlaybackSessionContract.EXTRA_SESSION_MEDIA_ID, mediaId)
            putBoolean(PlaybackSessionContract.EXTRA_RADIO_MODE, _radioModeEnabled.value)
        }
        val artworkBytes = if (!meta.anonymousMode && !meta.avatarBase64.isNullOrBlank()) {
            runCatching { Base64.decode(meta.avatarBase64, Base64.DEFAULT) }.getOrNull()
        } else null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(meta.title.ifBlank { "Unmuted Voice" })
            .setArtist(meta.speakerName.ifBlank { "Unmuted Speaker" })
            .setAlbumTitle(meta.subtitle.ifBlank { "Unmuted" })
            .setExtras(extras)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val itemBuilder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())

        if (clipStartMs != null && clipEndMs != null && clipEndMs > clipStartMs) {
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs)
                    .setEndPositionMs(clipEndMs)
                    .build()
            )
        }
        return itemBuilder.build()
    }

    private fun sendAudioParams(controller: MediaController, speed: Float, disguise: VoiceDisguise) {
        val args = Bundle().apply {
            putFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, speed)
            putString(PlaybackSessionContract.EXTRA_VOICE_DISGUISE, disguise.name)
        }
        val cmd = SessionCommand(PlaybackSessionContract.ACTION_APPLY_AUDIO_PARAMS, Bundle.EMPTY)
        try {
            controller.sendCustomCommand(cmd, args)
        } catch (e: Exception) {
            Log.w(TAG, "Custom audio params command failed: ${e.message}")
        }
    }

    fun updateDisguisePreview(disguise: VoiceDisguise) {
        _state.value = _state.value.copy(voiceDisguise = disguise)
        mediaController?.let { ctrl ->
            sendAudioParams(ctrl, _state.value.speed, disguise)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _state.value = _state.value.copy(speed = nextSpeed)
        mediaController?.let { ctrl ->
            sendAudioParams(ctrl, nextSpeed, _state.value.voiceDisguise)
        }
    }

    fun seekTo(positionMs: Long) {
        if (activeClapback != null) {
            seekToCombined(positionMs)
            return
        }
        val ctrl = mediaController ?: return
        val dur = ctrl.duration.takeIf { it > 0L } ?: _state.value.durationMs
        val clamped = positionMs.coerceIn(0L, dur.coerceAtLeast(0L))
        ctrl.seekTo(clamped)
        _state.value = _state.value.copy(positionMs = clamped, combinedPositionMs = clamped)
    }

    fun seekToCombined(combinedMs: Long) {
        val cb = activeClapback ?: run {
            seekTo(combinedMs)
            return
        }
        val ctrl = mediaController ?: return
        val clamped = combinedMs.coerceIn(0L, cb.totalDurationMs)
        if (clamped < cb.quoteSpanMs) {
            ctrl.seekTo(0, clamped)
            _state.value = _state.value.copy(
                activePhase = PlaybackPhase.QUOTED_CLIP,
                positionMs = clamped,
                combinedPositionMs = clamped,
                voiceDisguise = cb.quoteDisguise
            )
            sendAudioParams(ctrl, _state.value.speed, cb.quoteDisguise)
        } else {
            val replyOffset = (clamped - cb.quoteSpanMs).coerceAtLeast(0L)
            ctrl.seekTo(1, replyOffset)
            _state.value = _state.value.copy(
                activePhase = PlaybackPhase.MAIN,
                positionMs = replyOffset,
                combinedPositionMs = clamped,
                voiceDisguise = cb.mainDisguise
            )
            sendAudioParams(ctrl, _state.value.speed, cb.mainDisguise)
        }
    }

    fun pause() {
        mediaController?.pause()
        progressJob?.cancel()
        _state.value = _state.value.copy(playing = false)
    }

    fun resume() {
        val ctrl = mediaController ?: return
        val cb = activeClapback
        if (ctrl.playbackState == Player.STATE_ENDED) {
            if (cb != null) {
                ctrl.seekTo(0, 0L)
            } else {
                ctrl.seekTo(0L)
            }
        }
        ctrl.play()
        _state.value = _state.value.copy(playing = true)
        startProgressTicker()
    }

    fun stop() {
        loadJob?.cancel()
        progressJob?.cancel()
        activeClapback = null
        mediaController?.let { ctrl ->
            ctrl.stop()
            ctrl.clearMediaItems()
        }
        _state.value = PlaybackState(speed = _state.value.speed, radioModeEnabled = _radioModeEnabled.value)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val ctrl = mediaController ?: break
                syncStateFromController(ctrl)
                if (!ctrl.isPlaying && ctrl.playbackState != Player.STATE_BUFFERING) break
                delay(70L)
            }
        }
    }

    private fun syncStateFromController(ctrl: MediaController) {
        val pos = ctrl.currentPosition.coerceAtLeast(0L)
        val rawDur = ctrl.duration.takeIf { it > 0L } ?: _state.value.durationMs.coerceAtLeast(1L)
        val cb = activeClapback
        if (cb != null) {
            val idx = ctrl.currentMediaItemIndex
            val phase = if (idx == 0) PlaybackPhase.QUOTED_CLIP else PlaybackPhase.MAIN
            val actualMainDur = if (idx == 1 && ctrl.duration > 0L) ctrl.duration else cb.mainDurationMs
            val totalDur = (cb.quoteSpanMs + actualMainDur).coerceAtLeast(1L)
            val combinedPos = if (idx == 0) {
                pos.coerceIn(0L, cb.quoteSpanMs)
            } else {
                (cb.quoteSpanMs + pos).coerceIn(cb.quoteSpanMs, totalDur)
            }
            val split = (cb.quoteSpanMs.toFloat() / totalDur.toFloat()).coerceIn(0.08f, 0.85f)
            val phaseDisguise = if (idx == 0) cb.quoteDisguise else cb.mainDisguise
            _state.value = _state.value.copy(
                playing = ctrl.isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = rawDur,
                activePhase = phase,
                quoteSplitFraction = split,
                combinedPositionMs = combinedPos,
                combinedDurationMs = totalDur,
                voiceDisguise = phaseDisguise,
                radioModeEnabled = _radioModeEnabled.value
            )
        } else {
            val currentItem = ctrl.currentMediaItem
            val activeId = currentItem?.mediaId ?: _state.value.activeMediaId
            _state.value = _state.value.copy(
                activeMediaId = activeId,
                playing = ctrl.isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = rawDur,
                combinedPositionMs = pos,
                combinedDurationMs = rawDur,
                activePhase = PlaybackPhase.MAIN,
                radioModeEnabled = _radioModeEnabled.value
            )
        }
    }

    fun release() {
        loadJob?.cancel()
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
