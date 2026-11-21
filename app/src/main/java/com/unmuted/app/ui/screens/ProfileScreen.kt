package com.unmuted.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.JoinedRoom
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import java.io.File

/**
 * Public entry-point Composable for ProfileScreen, delegating to the modular
 * profile subcomponents under `com.unmuted.app.ui.screens.profile`.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    allPosts: List<VoicePost>,
    reactions: Map<String, UserPostReaction>,
    rooms: List<CommunityRoom>,
    joinedRooms: Map<String, JoinedRoom>,
    safetySettings: SafetySettings,
    profileReplies: List<Pair<VoicePost, VoiceComment>> = emptyList(),
    profileRepliesLoading: Boolean = false,
    playback: PlaybackState,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    currentUid: String,
    onBack: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onMessageUser: () -> Unit,
    onOpenSafetyControls: () -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onReportProfile: (UserProfile) -> Unit,
    onSignOut: () -> Unit,
    onPlayVoiceBio: (String) -> Unit,
    onPreloadVoiceBio: (String?) -> Unit = {},
    onPlayVoiceReply: (VoiceComment) -> Unit = {},
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit = { post, _ -> onOpenComments(post) },
    onQuoteClapback: (VoicePost) -> Unit = {},
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onExpandLyrics: (VoicePost) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    onSaveCustomization: (
        displayName: String,
        handle: String,
        bio: String,
        accentKey: String,
        bannerKey: String,
        waveformStyle: String,
        interests: List<String>,
        newAvatarUri: Uri?,
        newVoiceBioFile: File?,
        newVoiceBioDurationMs: Long,
        newVoiceBioPeaks: List<Float>,
        onDone: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    com.unmuted.app.ui.screens.profile.ProfileScreen(
        profile = profile,
        isOwnProfile = isOwnProfile,
        isFollowing = isFollowing,
        allPosts = allPosts,
        reactions = reactions,
        rooms = rooms,
        joinedRooms = joinedRooms,
        safetySettings = safetySettings,
        profileReplies = profileReplies,
        profileRepliesLoading = profileRepliesLoading,
        playback = playback,
        recordingState = recordingState,
        audioRecorder = audioRecorder,
        currentUid = currentUid,
        onBack = onBack,
        onToggleFollow = onToggleFollow,
        onMessageUser = onMessageUser,
        onOpenSafetyControls = onOpenSafetyControls,
        onMuteSpeaker = onMuteSpeaker,
        onBlockSpeaker = onBlockSpeaker,
        onReportProfile = onReportProfile,
        onSignOut = onSignOut,
        onPlayVoiceBio = onPlayVoiceBio,
        onPreloadVoiceBio = onPreloadVoiceBio,
        onPlayVoiceReply = onPlayVoiceReply,
        onPlayPost = onPlayPost,
        onSeekToMs = onSeekToMs,
        onCycleSpeed = onCycleSpeed,
        onVote = onVote,
        onOpenComments = onOpenComments,
        onOpenDebateSide = onOpenDebateSide,
        onQuoteClapback = onQuoteClapback,
        onEcho = onEcho,
        onBookmark = onBookmark,
        onReportPost = onReportPost,
        onHidePost = onHidePost,
        onDeletePost = onDeletePost,
        onExpandLyrics = onExpandLyrics,
        onOpenRoom = onOpenRoom,
        onOpenSpeakerProfile = onOpenSpeakerProfile,
        onSaveCustomization = onSaveCustomization,
        modifier = modifier
    )
}
