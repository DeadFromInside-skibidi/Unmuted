package com.unmuted.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.NowPlayingMeta
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.DirectMessage
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import kotlinx.coroutines.flow.StateFlow

class AudioPlaybackViewModel(
    val audioPlayer: AudioPlayerManager,
    val audioRecorder: AudioRecorderController,
    private val messageBus: AppMessageBus
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = audioPlayer.state
    val recordingState: StateFlow<RecordingState> = audioRecorder.state

    fun preloadPostsAudio(posts: List<VoicePost>) {
        val urls = posts.flatMap { post ->
            listOfNotNull(post.effectiveAudioUrl(), post.effectiveQuotedAudioUrl())
        }
        if (urls.isNotEmpty()) {
            audioPlayer.preloadAudioClips(urls)
        }
    }

    fun preloadMediaId(mediaId: String?) {
        if (!mediaId.isNullOrBlank()) {
            audioPlayer.preloadAudioClips(listOf(mediaId))
        }
    }

    fun togglePlayPauseActive() {
        val st = playbackState.value
        if (st.activeMediaId.isNullOrBlank()) return
        if (st.playing) audioPlayer.pause() else audioPlayer.resume()
    }

    fun togglePlayPost(post: VoicePost, autoPlay: Boolean = false) {
        val audioUrl = post.effectiveAudioUrl()
        if (audioUrl.isNullOrBlank()) {
            messageBus.emit("This post has no audio clip attached.")
            return
        }
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

        if (post.hasQuoteClip() && !post.effectiveQuotedAudioUrl().isNullOrBlank()) {
            audioPlayer.togglePlayClapback(
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
        } else {
            audioPlayer.togglePlayRemote(
                mediaId = audioUrl,
                audioUrl = audioUrl,
                disguise = post.disguiseEnum(),
                autoPlay = autoPlay,
                metadata = meta
            )
        }
    }

    fun togglePlayVoiceBio(profile: UserProfile) {
        val audioUrl = profile.effectiveVoiceBioUrl() ?: return
        val meta = NowPlayingMeta(
            title = profile.voiceBioTranscript?.take(64)?.ifBlank { "Pinned Voice Bio" } ?: "Pinned Voice Bio",
            speakerName = profile.displayName,
            subtitle = "@${profile.handle} · Voice Bio",
            accentKey = profile.accentKey,
            anonymousMode = false,
            avatarBase64 = profile.avatarBase64
        )
        audioPlayer.togglePlayRemote(
            mediaId = audioUrl,
            audioUrl = audioUrl,
            disguise = VoiceDisguise.NONE,
            metadata = meta
        )
    }

    fun togglePlayMediaId(
        mediaId: String,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        title: String = "Voice Reply",
        speakerName: String = "Unmuted Speaker"
    ) {
        val meta = NowPlayingMeta(
            title = title,
            speakerName = speakerName,
            subtitle = if (disguise != VoiceDisguise.NONE) disguise.badge else "Voice Reply"
        )
        audioPlayer.togglePlayRemote(
            mediaId = mediaId,
            audioUrl = mediaId,
            disguise = disguise,
            metadata = meta
        )
    }

    fun togglePlayDirectMessage(message: DirectMessage) {
        val audioUrl = message.effectiveAudioUrl()
        if (audioUrl.isNullOrBlank()) return
        val meta = NowPlayingMeta(
            title = message.transcript?.take(56)?.ifBlank { "Voice Note" } ?: "Voice Note",
            speakerName = message.senderName,
            subtitle = "Direct Message"
        )
        audioPlayer.togglePlayRemote(
            mediaId = "dm_${message.id}",
            audioUrl = audioUrl,
            disguise = VoiceDisguise.NONE,
            metadata = meta
        )
    }

    fun previewQuotedClip(quotedPost: VoicePost, startMs: Long, endMs: Long) {
        val audioUrl = quotedPost.effectiveAudioUrl() ?: return
        audioPlayer.playRangePreview(
            previewId = "quote_preview_${quotedPost.id}",
            audioUrl = audioUrl,
            startMs = startMs,
            endMs = endMs,
            disguise = quotedPost.disguiseEnum()
        )
    }

    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun cycleSpeed() = audioPlayer.cycleSpeed()

    fun stopPlayback() = audioPlayer.stop()

    fun updateDisguisePreview(disguise: VoiceDisguise) = audioPlayer.updateDisguisePreview(disguise)

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioPlaybackViewModel(
                audioPlayer = container.audioPlayer,
                audioRecorder = container.audioRecorder,
                messageBus = container.messageBus
            ) as T
        }
    }
}
