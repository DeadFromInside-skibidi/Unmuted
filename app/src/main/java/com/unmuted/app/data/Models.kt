package com.unmuted.app.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.abs

enum class PostKind {
    FEED_VOICE,
    PULSE_SHORT,
    VOICE_DROP;

    companion object {
        fun fromRaw(raw: String?): PostKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FEED_VOICE
    }
}

enum class VoiceDisguise(
    val label: String,
    val pitchFactor: Float,
    val speedTrim: Float,
    val badge: String,
    val subtitle: String
) {
    NONE("Natural", 1.0f, 1.0f, "Natural", "Unaltered voice"),
    DEEP("Deep", 0.72f, 0.96f, "Deep Mask", "Low bass formant shift"),
    STUDIO_RADIO("Studio Radio", 0.87f, 1.0f, "Studio Radio", "Broadcast EQ + pitch mask"),
    GLITCH("Glitch", 1.18f, 1.04f, "Glitch Mask", "Modulated cyber shift"),
    NEUTRAL_SHIFT("Neutral Shift", 0.92f, 1.01f, "Neutral Shift", "Subtle identity-safe shift");

    companion object {
        val maskingPresets: List<VoiceDisguise> = listOf(DEEP, STUDIO_RADIO, GLITCH, NEUTRAL_SHIFT)

        fun fromRaw(raw: String?): VoiceDisguise = when (raw?.uppercase()) {
            "MASKED" -> STUDIO_RADIO
            "HIGH" -> GLITCH
            else -> entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
        }
    }
}

enum class DebateStance(val label: String) {
    NONE("Neutral"),
    AGREE("Agree"),
    DISAGREE("Disagree");

    companion object {
        fun fromRaw(raw: String?): DebateStance =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class WaveformStyle(val key: String, val label: String) {
    BARS("BARS", "Studio Bars"),
    RIBBON("RIBBON", "Smooth Ribbon"),
    DOTS("DOTS", "Pulse Dots"),
    VINYL("VINYL", "Vinyl Equalizer");

    companion object {
        fun fromKey(raw: String?): WaveformStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: BARS
    }
}

enum class VoteType {
    NONE,
    LIKE,
    DISLIKE;

    companion object {
        fun fromRaw(raw: String?): VoteType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class MessageKind {
    VOICE,
    TEXT,
    IMAGE;

    companion object {
        fun fromRaw(raw: String?): MessageKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEXT
    }
}

// --- PLAY STORE COMPLIANT SAFETY MODELS ---

enum class SafetyRelationType(val label: String) {
    MUTE("Muted"),
    BLOCK("Blocked");

    companion object {
        fun fromRaw(raw: String?): SafetyRelationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MUTE
    }
}

enum class ReportCategory(
    val code: String,
    val title: String,
    val subtitle: String,
    val urgentLegalReview: Boolean
) {
    CSAM_EXPLOITATION(
        code = "CSAM_EXPLOITATION",
        title = "Child Exploitation / CSAM",
        subtitle = "Zero tolerance — immediate urgent legal escalation",
        urgentLegalReview = true
    ),
    VIOLENCE_THREATS(
        code = "VIOLENCE_THREATS",
        title = "Credible Threats & Violence",
        subtitle = "Incitement to physical harm, assault, or weapons threats",
        urgentLegalReview = true
    ),
    SELF_HARM_TERRORISM(
        code = "SELF_HARM_TERRORISM",
        title = "Terrorism or Self-Harm Encouragement",
        subtitle = "Violent extremism, suicide instruction, or dangerous acts",
        urgentLegalReview = true
    ),
    DOXXING_PRIVACY(
        code = "DOXXING_PRIVACY",
        title = "Doxxing / Private Personal Info",
        subtitle = "Sharing home addresses, phone numbers, or non-consensual data",
        urgentLegalReview = false
    ),
    ILLEGAL_GOODS_FRAUD(
        code = "ILLEGAL_GOODS_FRAUD",
        title = "Illegal Goods, Drugs, or Fraud",
        subtitle = "Soliciting illicit transactions, scams, or identity theft",
        urgentLegalReview = false
    ),
    HARASSMENT_HATE(
        code = "HARASSMENT_HATE",
        title = "Targeted Harassment or Dehumanizing Hate",
        subtitle = "Sustained abuse targeting an individual or protected group",
        urgentLegalReview = false
    ),
    OTHER(
        code = "OTHER",
        title = "Other Safety Concern",
        subtitle = "Spam, impersonation, or other community guideline breach",
        urgentLegalReview = false
    )
}

@Keep
@IgnoreExtraProperties
data class SafetySettings(
    @DocumentId val id: String = "config",
    val showUnfilteredControversial: Boolean = true,
    val collapseHeavilyDownvoted: Boolean = true,
    val downvoteThreshold: Int = 5,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SafetyRelation(
    @DocumentId val id: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val relationType: String = SafetyRelationType.MUTE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): SafetyRelationType = SafetyRelationType.fromRaw(relationType)
}

data class SafetyReportTarget(
    val targetType: String,
    val targetId: String,
    val targetAuthorUid: String? = null,
    val targetDisplayName: String = "Speaker"
)

// --- WORD & PHRASE-LEVEL TIMESTAMPED CAPTIONS ---

@Keep
@IgnoreExtraProperties
data class CaptionWord(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val word: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "startMs" to startMs,
        "endMs" to endMs,
        "word" to word
    )
}

@Keep
@IgnoreExtraProperties
data class CaptionSegment(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val text: String = "",
    val words: List<CaptionWord> = emptyList()
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("startMs", startMs)
        put("endMs", endMs)
        put("text", text)
        if (words.isNotEmpty()) {
            put("words", words.map { it.toMap() })
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val bio: String = "",
    val accentKey: String = "CORAL",
    val bannerKey: String = "OBSIDIAN_WAVE",
    val waveformStyle: String = "BARS",
    val avatarBase64: String? = null,
    val voiceBioAudioUrl: String? = null,
    val voiceBioMediaId: String? = null,
    val voiceBioTranscript: String? = null,
    val voiceBioDurationMs: Long = 0L,
    val voiceBioPeaks: List<Double> = emptyList(),
    val interests: List<String> = emptyList(),
    val followerCount: Long = 0L,
    val followingCount: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectiveVoiceBioUrl(): String? = voiceBioAudioUrl ?: voiceBioMediaId
}

@Keep
@IgnoreExtraProperties
data class UserPostReaction(
    @DocumentId val id: String = "",
    val postId: String = "",
    val vote: String = VoteType.NONE.name,
    val echoed: Boolean = false,
    val bookmarked: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun voteEnum(): VoteType = VoteType.fromRaw(vote)
}

@Keep
@IgnoreExtraProperties
data class JoinedRoom(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val anonymousAlias: String = "",
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityRoom(
    @DocumentId val id: String = "",
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "General",
    val anonymousRoom: Boolean = false,
    val accentKey: String = "CORAL",
    val guidelines: String = "",
    val creatorUid: String = "",
    val memberCount: Long = 1L,
    val postCount: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isUnfilteredControversialRoom(): Boolean =
        anonymousRoom || category.equals("Debate", ignoreCase = true) || category.equals("Confessions", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class VoicePost(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAccentKey: String = "CORAL",
    val authorAvatarBase64: String? = null,
    val postKind: String = PostKind.FEED_VOICE.name,
    val roomId: String? = null,
    val roomSlug: String? = null,
    val anonymousMode: Boolean = false,
    val anonymousAlias: String? = null,
    val voiceDisguise: String = VoiceDisguise.NONE.name,
    val audioUrl: String? = null,
    val audioMediaId: String? = null,
    val durationMs: Long = 0L,
    val waveformPeaks: List<Double> = emptyList(),
    val transcript: String = "",
    val captionSegments: List<CaptionSegment> = emptyList(),
    val headlineText: String? = null,
    val attachmentImageBase64: String? = null,
    val hashtags: List<String> = emptyList(),
    val likeCount: Long = 0L,
    val dislikeCount: Long = 0L,
    val commentCount: Long = 0L,
    val echoCount: Long = 0L,
    val engagementScore: Long = 0L,
    val controversyScore: Long = 0L,
    val searchTokens: List<String> = emptyList(),
    val isShortPulse: Boolean = false,
    val isAnonymous: Boolean = false,
    // Dual-Side Audio Debates ("Hot Takes")
    val hotTakeMode: Boolean = false,
    val agreeCount: Long = 0L,
    val disagreeCount: Long = 0L,
    // Voice Quote / "Clapback" Repost metadata
    val quotedPostId: String? = null,
    val quotedAuthorName: String? = null,
    val quotedAudioUrl: String? = null,
    val quotedAudioMediaId: String? = null,
    val quotedStartMs: Long = 0L,
    val quotedEndMs: Long = 0L,
    val quotedDisguise: String = VoiceDisguise.NONE.name,
    val quotedTranscript: String? = null,
    val quotedWaveformPeaks: List<Double> = emptyList(),
    val expiresAtMs: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun kindEnum(): PostKind = PostKind.fromRaw(postKind)
    fun disguiseEnum(): VoiceDisguise = VoiceDisguise.fromRaw(voiceDisguise)
    fun quotedDisguiseEnum(): VoiceDisguise = VoiceDisguise.fromRaw(quotedDisguise)
    fun effectiveAudioUrl(): String? = audioUrl ?: audioMediaId
    fun effectiveQuotedAudioUrl(): String? = quotedAudioUrl ?: quotedAudioMediaId
    fun hasQuoteClip(): Boolean = !effectiveQuotedAudioUrl().isNullOrBlank() && quotedEndMs > quotedStartMs
    fun quotedDurationMs(): Long = (quotedEndMs - quotedStartMs).coerceAtLeast(0L)
    fun combinedDurationMs(): Long = durationMs + if (hasQuoteClip()) quotedDurationMs() else 0L
    fun displaySpeakerName(): String =
        if (anonymousMode) anonymousAlias?.takeIf { it.isNotBlank() } ?: "Anonymous" else authorName
    fun displaySpeakerHandle(): String =
        if (anonymousMode) "hidden-identity" else "@$authorHandle"

    fun netDownvotes(): Long = (dislikeCount - likeCount).coerceAtLeast(0L)
    fun isHeavilyDownvoted(threshold: Int): Boolean = netDownvotes() >= threshold.coerceAtLeast(1)
    fun isUnfilteredOrControversial(): Boolean = anonymousMode || hotTakeMode
}

fun calculateEngagementScore(likes: Long, comments: Long, echoes: Long): Long =
    (likes.coerceAtLeast(0L) * 2L) + (comments.coerceAtLeast(0L) * 3L) + (echoes.coerceAtLeast(0L) * 2L)

fun calculateControversyScore(likes: Long, dislikes: Long, comments: Long): Long =
    (minOf(likes.coerceAtLeast(0L), dislikes.coerceAtLeast(0L)) * 6L) + (comments.coerceAtLeast(0L) * 2L)

fun buildPostSearchTokens(
    headlineText: String?,
    hashtags: List<String>,
    roomName: String?,
    roomSlug: String?,
    transcript: String
): List<String> {
    val tokens = linkedSetOf<String>()
    fun addToken(raw: String?) {
        if (raw.isNullOrBlank()) return
        val clean = raw.lowercase().trim().trim { it !in 'a'..'z' && it !in '0'..'9' }
        if (clean.length in 2..40) {
            tokens.add(clean)
        }
    }
    headlineText?.split("\\s+".toRegex())?.forEach { addToken(it) }
    hashtags.forEach {
        val clean = it.removePrefix("#")
        addToken(clean)
    }
    roomName?.split("\\s+".toRegex())?.forEach { addToken(it) }
    roomSlug?.let { addToken(it) }
    val stopWords = setOf(
        "the", "and", "this", "that", "with", "have", "from", "they", "will", "would",
        "there", "their", "what", "about", "which", "when", "make", "like", "time", "just",
        "know", "take", "people", "into", "year", "your", "good", "some", "could", "them",
        "see", "other", "than", "then", "now", "look", "only", "come", "its", "over",
        "think", "also", "back", "after", "use", "two", "how", "our", "work", "first",
        "well", "way", "even", "new", "want", "because", "any", "these", "give", "day"
    )
    transcript.split("\\s+".toRegex())
        .map { it.lowercase().trim().trim { c -> c !in 'a'..'z' && c !in '0'..'9' } }
        .filter { it.length >= 3 && it !in stopWords }
        .forEach { addToken(it) }

    return tokens.take(30)
}

enum class DiscoverFilter(val label: String) {
    TRENDING("Trending"),
    CONTROVERSIAL("Controversial"),
    ANONYMOUS("Anonymous Takes"),
    FOLLOWING("Following"),
    SHORT_CLIPS("Short Clips")
}

data class DiscoverQuery(
    val filter: DiscoverFilter = DiscoverFilter.TRENDING,
    val searchToken: String? = null,
    val followingUids: Set<String> = emptySet()
)

@Keep
@IgnoreExtraProperties
data class TrendingAudioTopic(
    val id: String = "",
    val title: String = "",
    val debateTag: String = "",
    val hotTakePreview: String = "",
    val postCount: Long = 0L,
    val totalReactions: Long = 0L,
    val isAnonymousHotspot: Boolean = false,
    val roomId: String? = null,
    val searchToken: String? = null
)

@Keep
@IgnoreExtraProperties
data class VoiceComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val anonymousMode: Boolean = false,
    val anonymousAlias: String? = null,
    val voiceDisguise: String = VoiceDisguise.NONE.name,
    val stance: String = DebateStance.NONE.name,
    val textBody: String? = null,
    val audioUrl: String? = null,
    val audioMediaId: String? = null,
    val durationMs: Long = 0L,
    val waveformPeaks: List<Double> = emptyList(),
    val transcript: String? = null,
    val captionSegments: List<CaptionSegment> = emptyList(),
    val likeCount: Long = 0L,
    val dislikeCount: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun disguiseEnum(): VoiceDisguise = VoiceDisguise.fromRaw(voiceDisguise)
    fun stanceEnum(): DebateStance = DebateStance.fromRaw(stance)
    fun effectiveAudioUrl(): String? = audioUrl ?: audioMediaId
    fun displaySpeakerName(): String =
        if (anonymousMode) anonymousAlias?.takeIf { it.isNotBlank() } ?: "Anonymous" else authorName
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantHandles: Map<String, String> = emptyMap(),
    val lastMessagePreview: String = "",
    val lastMessageKind: String = MessageKind.TEXT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherParticipantUid(myUid: String): String =
        participantUids.firstOrNull { it != myUid } ?: myUid
    fun otherParticipantName(myUid: String): String =
        participantNames[otherParticipantUid(myUid)] ?: "Unmuted Speaker"
    fun otherParticipantHandle(myUid: String): String =
        participantHandles[otherParticipantUid(myUid)] ?: "speaker"
}

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val participantUids: List<String> = emptyList(),
    val messageKind: String = MessageKind.TEXT.name,
    val textBody: String? = null,
    val audioUrl: String? = null,
    val audioBase64: String? = null,
    val durationMs: Long = 0L,
    val waveformPeaks: List<Double> = emptyList(),
    val transcript: String? = null,
    val captionSegments: List<CaptionSegment> = emptyList(),
    val imageBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun kindEnum(): MessageKind = MessageKind.fromRaw(messageKind)
    fun effectiveAudioUrl(): String? = audioUrl ?: audioBase64
}

object AliasGenerator {
    private val prefixes = listOf(
        "Phantom", "Velvet", "Cipher", "Midnight", "Echo", "Static",
        "Obsidian", "Prism", "Shadow", "Signal", "Nova", "Hush"
    )
    private val suffixes = listOf(
        "Owl", "Wave", "Fox", "Voice", "Pulse", "Drift",
        "Spark", "Ghost", "Reed", "Chord", "Vibe", "Tone"
    )

    fun generateForRoom(uid: String, roomKey: String, nonce: Int = 0): String {
        val hash = abs("$uid::$roomKey::unmuted_v2::$nonce".hashCode())
        val prefix = prefixes[hash % prefixes.size]
        val suffix = suffixes[(hash / prefixes.size) % suffixes.size]
        val num = (hash % 899) + 100
        return "${prefix}${suffix}_$num"
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%d:%02d", mins, secs)
}

fun formatRelativeTime(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    val diffSec = ((System.currentTimeMillis() - ts.toDate().time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m"
        diffSec < 86400 -> "${diffSec / 3600}h"
        else -> "${diffSec / 86400}d"
    }
}
