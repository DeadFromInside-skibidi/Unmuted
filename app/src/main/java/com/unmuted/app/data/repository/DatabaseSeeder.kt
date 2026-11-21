package com.unmuted.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.CaptionWord
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.buildPostSearchTokens
import com.unmuted.app.data.calculateControversyScore
import com.unmuted.app.data.calculateEngagementScore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * Seeds realistic community rooms, diverse speaker profiles, and word-synced audio takes
 * directly into Cloud Firestore.
 *
 * All sample documents conform strictly to Firestore security rules and schema blueprints.
 */
object DatabaseSeeder {

    private const val TAG = "DatabaseSeeder"

    // Royalty-free speech sample streams hosted on official test CDNs for reliable playback
    private const val SAMPLE_SPEECH_1 = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
    private const val SAMPLE_SPEECH_2 = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"

    suspend fun seedIfEmpty(context: Context, currentUid: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirestoreProvider.getDatabase(context)
                val existingRooms = db.collection("rooms").limit(1).get().await()
                val existingPosts = db.collection("posts").limit(1).get().await()

                if (existingRooms.isEmpty && existingPosts.isEmpty) {
                    Log.d(TAG, "Database is empty. Seeding initial studio content...")
                    seedDatabase(context, currentUid)
                    Result.success(true)
                } else {
                    Log.d(TAG, "Database already has content. Skipping auto-seed.")
                    Result.success(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "seedIfEmpty check note: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun seedDatabase(context: Context, currentUid: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirestoreProvider.getDatabase(context)
                var count = 0

                // 1. Seed Community Rooms
                val rooms = buildSampleRooms(currentUid)
                for (room in rooms) {
                    val roomPayload = mapOf(
                        "slug" to room.slug,
                        "name" to room.name,
                        "description" to room.description,
                        "category" to room.category,
                        "anonymousRoom" to room.anonymousRoom,
                        "accentKey" to room.accentKey,
                        "guidelines" to room.guidelines,
                        "creatorUid" to room.creatorUid,
                        "memberCount" to room.memberCount,
                        "postCount" to room.postCount,
                        "visibility" to room.visibility,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("rooms").document(room.id).set(roomPayload, SetOptions.merge()).await()
                    count++
                }

                // 2. Seed Demo User Profiles
                val demoProfiles = buildSampleProfiles()
                for (profile in demoProfiles) {
                    val profilePayload = mapOf(
                        "userId" to profile.userId,
                        "displayName" to profile.displayName,
                        "handle" to profile.handle,
                        "bio" to profile.bio,
                        "accentKey" to profile.accentKey,
                        "bannerKey" to profile.bannerKey,
                        "waveformStyle" to profile.waveformStyle,
                        "interests" to profile.interests,
                        "followerCount" to profile.followerCount,
                        "followingCount" to profile.followingCount,
                        "visibility" to profile.visibility,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    try {
                        db.collection("users").document(profile.userId).set(profilePayload, SetOptions.merge()).await()
                        count++
                    } catch (e: Exception) {
                        Log.w(TAG, "Note: User profile write for ${profile.userId}: ${e.message}")
                    }
                }

                // 3. Seed Realistic Voice Posts with Synced Lyrics
                val samplePosts = buildSamplePosts(currentUid)
                for ((post, isAnon) in samplePosts) {
                    val postPayload = mutableMapOf<String, Any>(
                        "authorUid" to post.authorUid,
                        "authorName" to post.authorName,
                        "authorHandle" to post.authorHandle,
                        "authorAccentKey" to post.authorAccentKey,
                        "postKind" to post.postKind,
                        "anonymousMode" to post.anonymousMode,
                        "voiceDisguise" to post.voiceDisguise,
                        "durationMs" to post.durationMs,
                        "waveformPeaks" to post.waveformPeaks,
                        "transcript" to post.transcript,
                        "captionSegments" to post.captionSegments.map { it.toMap() },
                        "likeCount" to post.likeCount,
                        "dislikeCount" to post.dislikeCount,
                        "commentCount" to post.commentCount,
                        "echoCount" to post.echoCount,
                        "engagementScore" to post.engagementScore,
                        "controversyScore" to post.controversyScore,
                        "isShortPulse" to post.isShortPulse,
                        "isAnonymous" to post.isAnonymous,
                        "hotTakeMode" to post.hotTakeMode,
                        "agreeCount" to post.agreeCount,
                        "disagreeCount" to post.disagreeCount,
                        "searchTokens" to post.searchTokens,
                        "visibility" to post.visibility,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    post.headlineText?.let { postPayload["headlineText"] = it }
                    post.roomId?.let { postPayload["roomId"] = it }
                    post.roomSlug?.let { postPayload["roomSlug"] = it }
                    post.anonymousAlias?.let { postPayload["anonymousAlias"] = it }
                    post.audioUrl?.let { postPayload["audioUrl"] = it }
                    if (post.hashtags.isNotEmpty()) postPayload["hashtags"] = post.hashtags

                    if (isAnon) {
                        // Anonymous post ownership entry
                        val anonDoc = mapOf(
                            "ownerUid" to currentUid,
                            "targetCollection" to "posts",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("anonymous_ownership").document(post.id).set(anonDoc).await()
                    }

                    db.collection("posts").document(post.id).set(postPayload).await()
                    count++
                }

                Log.d(TAG, "Successfully seeded $count database entities into Cloud Firestore.")
                Result.success(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed seeding database: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun buildSampleRooms(creatorUid: String): List<CommunityRoom> = listOf(
        CommunityRoom(
            id = "room_ai_alignment",
            slug = "ai-alignment",
            name = "AI Alignment & Ethics",
            description = "Debating agent autonomy, model boundaries, reasoning eval drift, and open weights.",
            category = "Tech",
            anonymousRoom = false,
            accentKey = "CORAL",
            guidelines = "Critique technical claims with evidence. Ad hominem attacks are muted.",
            creatorUid = creatorUid,
            memberCount = 142L,
            postCount = 19L,
            visibility = "PUBLIC"
        ),
        CommunityRoom(
            id = "room_founder_burnout",
            slug = "founder-burnout",
            name = "Founder Burnout & Reality",
            description = "The unglamorous side of startups: runway panic, co-founder strife, and mental resilience.",
            category = "Startups",
            anonymousRoom = false,
            accentKey = "AMBER",
            guidelines = "Vulnerability is welcome. Keep feedback candid, empathetic, and constructive.",
            creatorUid = creatorUid,
            memberCount = 238L,
            postCount = 31L,
            visibility = "PUBLIC"
        ),
        CommunityRoom(
            id = "room_late_night_confessions",
            slug = "late-night-confessions",
            name = "Late Night Confessions",
            description = "100% anonymous voice room. Deep formant masking and burner aliases enabled.",
            category = "Confessions",
            anonymousRoom = true,
            accentKey = "VIOLET",
            guidelines = "Complete pseudonymity. Strictly zero doxxing, harassment, or illegal content.",
            creatorUid = creatorUid,
            memberCount = 412L,
            postCount = 58L,
            visibility = "PUBLIC"
        ),
        CommunityRoom(
            id = "room_audio_dsp",
            slug = "audio-dsp",
            name = "Audio DSP & Sound Studio",
            description = "Hardware compressors, convolution reverbs, mic preamps, and mobile Media3 pipeline tricks.",
            category = "Music",
            anonymousRoom = false,
            accentKey = "MINT",
            guidelines = "Share DSP presets, waveforms, and stems for collaborative critique.",
            creatorUid = creatorUid,
            memberCount = 195L,
            postCount = 24L,
            visibility = "PUBLIC"
        ),
        CommunityRoom(
            id = "room_rto_debate",
            slug = "rto-debate",
            name = "Remote Work vs Return to Office",
            description = "The definitive debate: Async autonomy vs in-person whiteboard synergy. Pick Agree or Disagree.",
            category = "Debates",
            anonymousRoom = false,
            accentKey = "CYAN",
            guidelines = "Voice your counter-argument on hot takes. Keep debates fiery but respectful.",
            creatorUid = creatorUid,
            memberCount = 520L,
            postCount = 74L,
            visibility = "PUBLIC"
        )
    )

    private fun buildSampleProfiles(): List<UserProfile> = listOf(
        UserProfile(
            id = "user_elena_vance",
            userId = "user_elena_vance",
            displayName = "Elena Vance",
            handle = "elena_vance",
            bio = "Tech podcaster & researcher exploring agent architectures, speech models, and AI alignment.",
            accentKey = "CORAL",
            bannerKey = "CORAL_HEAT",
            waveformStyle = "BARS",
            interests = listOf("AI", "Speech", "Startups", "Podcasts"),
            followerCount = 1240L,
            followingCount = 180L,
            visibility = "PUBLIC"
        ),
        UserProfile(
            id = "user_marcus_thorne",
            userId = "user_marcus_thorne",
            displayName = "Marcus Thorne",
            handle = "marcus_thorne",
            bio = "Angel investor & 2x founder. Unfiltered takes on venture capital, SaaS metrics, and remote teams.",
            accentKey = "VIOLET",
            bannerKey = "MIDNIGHT_PURPLE",
            waveformStyle = "RIBBON",
            interests = listOf("Startups", "Venture", "Debate", "Leadership"),
            followerCount = 890L,
            followingCount = 95L,
            visibility = "PUBLIC"
        ),
        UserProfile(
            id = "user_priya_audio",
            userId = "user_priya_audio",
            displayName = "Priya Patel",
            handle = "priya_audio",
            bio = "Sound designer & DSP audio engineer. Obsessed with analog warmth, tube compressors, and psychoacoustics.",
            accentKey = "MINT",
            bannerKey = "NEON_CYBER",
            waveformStyle = "VINYL",
            interests = listOf("Music", "AudioDSP", "Production", "Hardware"),
            followerCount = 670L,
            followingCount = 120L,
            visibility = "PUBLIC"
        ),
        UserProfile(
            id = "user_david_chen",
            userId = "user_david_chen",
            displayName = "David Chen",
            handle = "david_chen",
            bio = "Distributed systems engineer. Writing about consensus algorithms, latency tails, and 3 AM production war stories.",
            accentKey = "AMBER",
            bannerKey = "AMBER_STUDIO",
            waveformStyle = "DOTS",
            interests = listOf("Engineering", "Distributed", "Go", "Cloud"),
            followerCount = 530L,
            followingCount = 110L,
            visibility = "PUBLIC"
        )
    )

    private fun sampleWaveform(seed: Int): List<Double> {
        val base = listOf(
            0.15, 0.32, 0.48, 0.72, 0.85, 0.62, 0.44, 0.58,
            0.88, 0.95, 0.78, 0.52, 0.34, 0.61, 0.82, 0.91,
            0.74, 0.60, 0.45, 0.68, 0.85, 0.77, 0.53, 0.39,
            0.28, 0.46, 0.65, 0.83, 0.71, 0.54, 0.35, 0.18
        )
        return base.mapIndexed { idx, v ->
            val factor = 0.85 + ((((idx * 7) + seed) % 31) / 100.0)
            (v * factor).coerceIn(0.1, 1.0)
        }
    }

    private fun buildSamplePosts(currentUid: String): List<Pair<VoicePost, Boolean>> {
        val now = System.currentTimeMillis()

        // Post 1: Elena Vance on AI Alignment
        val p1Transcript = "Why AI agents will hit a reasoning wall without native environment feedback. Text tokens alone are just shadows on the cave wall."
        val p1Segments = listOf(
            CaptionSegment(
                startMs = 0L,
                endMs = 2800L,
                text = "Why AI agents will hit a reasoning wall",
                words = listOf(
                    CaptionWord(0L, 300L, "Why"),
                    CaptionWord(320L, 600L, "AI"),
                    CaptionWord(620L, 1100L, "agents"),
                    CaptionWord(1120L, 1400L, "will"),
                    CaptionWord(1420L, 1700L, "hit"),
                    CaptionWord(1720L, 1850L, "a"),
                    CaptionWord(1870L, 2350L, "reasoning"),
                    CaptionWord(2370L, 2800L, "wall")
                )
            ),
            CaptionSegment(
                startMs = 2900L,
                endMs = 6200L,
                text = "without native environment feedback. Text tokens alone are just shadows.",
                words = listOf(
                    CaptionWord(2900L, 3300L, "without"),
                    CaptionWord(3320L, 3750L, "native"),
                    CaptionWord(3770L, 4400L, "environment"),
                    CaptionWord(4420L, 4950L, "feedback."),
                    CaptionWord(5000L, 5300L, "Text"),
                    CaptionWord(5320L, 5700L, "tokens"),
                    CaptionWord(5720L, 6200L, "alone")
                )
            )
        )
        val p1Tags = listOf("#ai", "#agents", "#reasoning")
        val post1 = VoicePost(
            id = "seed_post_ai_agents_01",
            authorUid = currentUid,
            authorName = "Elena Vance",
            authorHandle = "elena_vance",
            authorAccentKey = "CORAL",
            postKind = PostKind.FEED_VOICE.name,
            roomId = "room_ai_alignment",
            roomSlug = "ai-alignment",
            anonymousMode = false,
            voiceDisguise = VoiceDisguise.NONE.name,
            audioUrl = SAMPLE_SPEECH_1,
            durationMs = 6200L,
            waveformPeaks = sampleWaveform(1),
            transcript = p1Transcript,
            captionSegments = p1Segments,
            headlineText = "The AI Reasoning Wall: Why Tokens Aren't Enough",
            hashtags = p1Tags,
            likeCount = 84L,
            dislikeCount = 6L,
            commentCount = 14L,
            echoCount = 12L,
            engagementScore = calculateEngagementScore(84L, 14L, 12L),
            controversyScore = calculateControversyScore(84L, 6L, 14L),
            searchTokens = buildPostSearchTokens("The AI Reasoning Wall", p1Tags, "AI Alignment & Ethics", "ai-alignment", p1Transcript),
            isShortPulse = false,
            isAnonymous = false,
            hotTakeMode = false,
            visibility = "PUBLIC"
        )

        // Post 2: Marcus Thorne on RTO (Hot Take Debate)
        val p2Transcript = "Hot take: Mandatory return-to-office policies are almost never about culture or collaboration. In ninety percent of cases, they are stealth layoffs designed to reduce headcount without severance."
        val p2Segments = listOf(
            CaptionSegment(
                startMs = 0L,
                endMs = 3200L,
                text = "Hot take: Mandatory return-to-office policies are almost never about culture.",
                words = listOf(
                    CaptionWord(0L, 350L, "Hot"),
                    CaptionWord(370L, 700L, "take:"),
                    CaptionWord(720L, 1400L, "Mandatory"),
                    CaptionWord(1420L, 2300L, "return-to-office"),
                    CaptionWord(2320L, 2750L, "policies"),
                    CaptionWord(2770L, 3200L, "are")
                )
            ),
            CaptionSegment(
                startMs = 3300L,
                endMs = 7400L,
                text = "In ninety percent of cases, they are stealth layoffs designed to cut headcount.",
                words = listOf(
                    CaptionWord(3300L, 3600L, "In"),
                    CaptionWord(3620L, 4000L, "ninety"),
                    CaptionWord(4020L, 4450L, "percent"),
                    CaptionWord(4470L, 4900L, "of"),
                    CaptionWord(4920L, 5400L, "cases,"),
                    CaptionWord(5420L, 5800L, "stealth"),
                    CaptionWord(5820L, 6400L, "layoffs")
                )
            )
        )
        val p2Tags = listOf("#rto", "#startups", "#culture", "#debate")
        val post2 = VoicePost(
            id = "seed_post_rto_debate_02",
            authorUid = currentUid,
            authorName = "Marcus Thorne",
            authorHandle = "marcus_thorne",
            authorAccentKey = "VIOLET",
            postKind = PostKind.FEED_VOICE.name,
            roomId = "room_rto_debate",
            roomSlug = "rto-debate",
            anonymousMode = false,
            voiceDisguise = VoiceDisguise.NONE.name,
            audioUrl = SAMPLE_SPEECH_2,
            durationMs = 7400L,
            waveformPeaks = sampleWaveform(2),
            transcript = p2Transcript,
            captionSegments = p2Segments,
            headlineText = "Hot Take: RTO Mandates Are Just Silent Layoffs",
            hashtags = p2Tags,
            likeCount = 142L,
            dislikeCount = 98L,
            commentCount = 47L,
            echoCount = 28L,
            engagementScore = calculateEngagementScore(142L, 47L, 28L),
            controversyScore = calculateControversyScore(142L, 98L, 47L),
            searchTokens = buildPostSearchTokens("Hot Take: RTO Mandates Are Just Silent Layoffs", p2Tags, "Remote Work vs Return to Office", "rto-debate", p2Transcript),
            isShortPulse = false,
            isAnonymous = false,
            hotTakeMode = true,
            agreeCount = 112L,
            disagreeCount = 89L,
            visibility = "PUBLIC"
        )

        // Post 3: Anonymous Confession (Deep Formant Disguise)
        val p3Transcript = "Late night confession: I've been a senior staff engineer at a trillion dollar company for four years. Our internal tools are so broken that sixty percent of engineering time is spent waiting on builds."
        val p3Segments = listOf(
            CaptionSegment(
                startMs = 0L,
                endMs = 3800L,
                text = "Late night confession from inside Big Tech.",
                words = listOf(
                    CaptionWord(0L, 500L, "Late"),
                    CaptionWord(520L, 950L, "night"),
                    CaptionWord(970L, 1600L, "confession"),
                    CaptionWord(1620L, 2100L, "from"),
                    CaptionWord(2120L, 2700L, "inside"),
                    CaptionWord(2720L, 3300L, "Big"),
                    CaptionWord(3320L, 3800L, "Tech.")
                )
            ),
            CaptionSegment(
                startMs = 3900L,
                endMs = 8500L,
                text = "Our internal tooling is so broken that most engineering time is pure waiting.",
                words = listOf(
                    CaptionWord(3900L, 4200L, "Our"),
                    CaptionWord(4220L, 4700L, "internal"),
                    CaptionWord(4720L, 5200L, "tooling"),
                    CaptionWord(5220L, 5500L, "is"),
                    CaptionWord(5520L, 5800L, "so"),
                    CaptionWord(5820L, 6400L, "broken"),
                    CaptionWord(6420L, 7100L, "that")
                )
            )
        )
        val p3Tags = listOf("#confessions", "#tech", "#unfiltered")
        val post3 = VoicePost(
            id = "seed_post_anon_confession_03",
            authorUid = "anon_staff_whistleblower_01",
            authorName = "Anonymous Speaker",
            authorHandle = "hidden-identity",
            authorAccentKey = "VIOLET",
            postKind = PostKind.FEED_VOICE.name,
            roomId = "room_late_night_confessions",
            roomSlug = "late-night-confessions",
            anonymousMode = true,
            anonymousAlias = "Midnight Phantom",
            voiceDisguise = VoiceDisguise.DEEP.name,
            audioUrl = SAMPLE_SPEECH_1,
            durationMs = 8500L,
            waveformPeaks = sampleWaveform(3),
            transcript = p3Transcript,
            captionSegments = p3Segments,
            headlineText = "Confession: Inside the trillion-dollar build pipeline disaster",
            hashtags = p3Tags,
            likeCount = 195L,
            dislikeCount = 12L,
            commentCount = 38L,
            echoCount = 31L,
            engagementScore = calculateEngagementScore(195L, 38L, 31L),
            controversyScore = calculateControversyScore(195L, 12L, 38L),
            searchTokens = buildPostSearchTokens("Confession: Inside the build pipeline disaster", p3Tags, "Late Night Confessions", "late-night-confessions", p3Transcript),
            isShortPulse = false,
            isAnonymous = true,
            hotTakeMode = false,
            visibility = "PUBLIC"
        )

        // Post 4: Priya Patel on Audio DSP
        val p4Transcript = "Analog tape saturation vs 32-bit floating point DSP. Digital clipping is mathematical harshness, but tube warmth is harmonic distortion our ears evolved to love."
        val p4Segments = listOf(
            CaptionSegment(
                startMs = 0L,
                endMs = 3500L,
                text = "Analog tape saturation versus modern DSP plugins.",
                words = listOf(
                    CaptionWord(0L, 600L, "Analog"),
                    CaptionWord(620L, 1100L, "tape"),
                    CaptionWord(1120L, 1800L, "saturation"),
                    CaptionWord(1820L, 2300L, "versus"),
                    CaptionWord(2320L, 2900L, "modern"),
                    CaptionWord(2920L, 3500L, "DSP")
                )
            ),
            CaptionSegment(
                startMs = 3600L,
                endMs = 7800L,
                text = "Digital clipping is harsh, but even harmonics are what our ears evolved to love.",
                words = listOf(
                    CaptionWord(3600L, 4100L, "Digital"),
                    CaptionWord(4120L, 4700L, "clipping"),
                    CaptionWord(4720L, 5100L, "is"),
                    CaptionWord(5120L, 5600L, "harsh,"),
                    CaptionWord(5620L, 6100L, "even"),
                    CaptionWord(6120L, 6800L, "harmonics")
                )
            )
        )
        val p4Tags = listOf("#audio", "#dsp", "#production", "#sound")
        val post4 = VoicePost(
            id = "seed_post_audio_dsp_04",
            authorUid = currentUid,
            authorName = "Priya Patel",
            authorHandle = "priya_audio",
            authorAccentKey = "MINT",
            postKind = PostKind.FEED_VOICE.name,
            roomId = "room_audio_dsp",
            roomSlug = "audio-dsp",
            anonymousMode = false,
            voiceDisguise = VoiceDisguise.NONE.name,
            audioUrl = SAMPLE_SPEECH_2,
            durationMs = 7800L,
            waveformPeaks = sampleWaveform(4),
            transcript = p4Transcript,
            captionSegments = p4Segments,
            headlineText = "Why Analog Tape Warmth Still Beats Pristine Digital Math",
            hashtags = p4Tags,
            likeCount = 68L,
            dislikeCount = 3L,
            commentCount = 11L,
            echoCount = 9L,
            engagementScore = calculateEngagementScore(68L, 11L, 9L),
            controversyScore = calculateControversyScore(68L, 3L, 11L),
            searchTokens = buildPostSearchTokens("Analog Tape Warmth Beats Digital Math", p4Tags, "Audio DSP & Sound Studio", "audio-dsp", p4Transcript),
            isShortPulse = false,
            isAnonymous = false,
            hotTakeMode = false,
            visibility = "PUBLIC"
        )

        // Post 5: Short Pulse Clip
        val p5Transcript = "Thirty-second pulse: Step away from your desk. Drink some cold water. The production incident will still be there in five minutes."
        val p5Segments = listOf(
            CaptionSegment(
                startMs = 0L,
                endMs = 2800L,
                text = "Thirty-second pulse: Step away from your keyboard.",
                words = listOf(
                    CaptionWord(0L, 500L, "Thirty-second"),
                    CaptionWord(520L, 950L, "pulse:"),
                    CaptionWord(970L, 1400L, "Step"),
                    CaptionWord(1420L, 1800L, "away"),
                    CaptionWord(1820L, 2200L, "from"),
                    CaptionWord(2220L, 2800L, "desk.")
                )
            ),
            CaptionSegment(
                startMs = 2900L,
                endMs = 5800L,
                text = "Drink cold water. The incident will still be there.",
                words = listOf(
                    CaptionWord(2900L, 3300L, "Drink"),
                    CaptionWord(3320L, 3700L, "cold"),
                    CaptionWord(3720L, 4200L, "water."),
                    CaptionWord(4220L, 4600L, "The"),
                    CaptionWord(4620L, 5200L, "incident")
                )
            )
        )
        val p5Tags = listOf("#pulse", "#mindset", "#engineering")
        val post5 = VoicePost(
            id = "seed_post_pulse_short_05",
            authorUid = currentUid,
            authorName = "David Chen",
            authorHandle = "david_chen",
            authorAccentKey = "AMBER",
            postKind = PostKind.PULSE_SHORT.name,
            roomId = null,
            roomSlug = null,
            anonymousMode = false,
            voiceDisguise = VoiceDisguise.NONE.name,
            audioUrl = SAMPLE_SPEECH_1,
            durationMs = 5800L,
            waveformPeaks = sampleWaveform(5),
            transcript = p5Transcript,
            captionSegments = p5Segments,
            headlineText = "Short Pulse: Step away from the incident",
            hashtags = p5Tags,
            likeCount = 135L,
            dislikeCount = 2L,
            commentCount = 19L,
            echoCount = 24L,
            engagementScore = calculateEngagementScore(135L, 19L, 24L),
            controversyScore = calculateControversyScore(135L, 2L, 19L),
            searchTokens = buildPostSearchTokens("Short Pulse Step away from incident", p5Tags, null, null, p5Transcript),
            isShortPulse = true,
            isAnonymous = false,
            hotTakeMode = false,
            visibility = "PUBLIC"
        )

        return listOf(
            post1 to false,
            post2 to false,
            post3 to true,
            post4 to false,
            post5 to false
        )
    }
}
