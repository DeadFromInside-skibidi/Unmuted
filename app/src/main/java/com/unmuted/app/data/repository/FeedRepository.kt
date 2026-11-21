package com.unmuted.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import com.unmuted.app.data.AliasGenerator
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.buildPostSearchTokens
import com.unmuted.app.data.calculateControversyScore
import com.unmuted.app.data.calculateEngagementScore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FeedRepository(private val db: FirebaseFirestore) {

    private val activePagingSources = CopyOnWriteArrayList<VoicePostPagingSource>()

    fun pagedPostsFlow(pageSize: Int = 15): Flow<PagingData<VoicePost>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize,
                prefetchDistance = 4,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                VoicePostPagingSource(db, pageSize).also { source ->
                    activePagingSources.add(source)
                    source.registerInvalidatedCallback { activePagingSources.remove(source) }
                }
            }
        ).flow
    }

    fun invalidatePostsPaging() {
        activePagingSources.toList().forEach { it.invalidate() }
    }

    /**
     * Bounded real-time window for 24h Voice Drops, Profile takes, and Pulses.
     * Strictly capped via `.limit(limit)` so Firestore never downloads entire collections.
     */
    fun observeRecentPosts(limit: Long = 35L): Flow<List<VoicePost>> = flow {
        val uid = requireSignedInUid()
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VoicePost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeUserReactions(): Flow<Map<String, UserPostReaction>> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid/reactions"
        emitAll(
            db.collection("users").document(uid).collection("reactions")
                .limit(200)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPostReaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .associateBy { it.postId.ifBlank { it.id } }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createVoicePost(
        author: UserProfile,
        postKind: PostKind,
        audioUrl: String?,
        durationMs: Long,
        waveformPeaks: List<Float>,
        transcript: String,
        captionSegments: List<CaptionSegment>,
        room: CommunityRoom?,
        anonymousMode: Boolean,
        voiceDisguise: VoiceDisguise,
        headlineText: String?,
        attachmentImageBase64: String?,
        hashtags: List<String>,
        customAnonymousAlias: String? = null,
        hotTakeMode: Boolean = false,
        quotedPost: VoicePost? = null,
        quotedStartMs: Long = 0L,
        quotedEndMs: Long = 0L,
        quotedTranscript: String? = null,
        quotedWaveformPeaks: List<Float> = emptyList(),
        explicitPostId: String? = null
    ): Result<String> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val postId = explicitPostId ?: nextReverseChronoId("post")
        val path = "posts/$postId"
        return try {
            val effectiveAnonymous = anonymousMode || (room?.anonymousRoom == true)
            val alias = if (effectiveAnonymous) {
                customAnonymousAlias?.takeIf { it.isNotBlank() }?.take(40)
                    ?: AliasGenerator.generateForRoom(uid, room?.id ?: "global_unfiltered")
            } else null
            val expiresAtMs = if (postKind == PostKind.VOICE_DROP) {
                System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            } else 0L
            val hasQuote = quotedPost != null && !quotedPost.effectiveAudioUrl().isNullOrBlank() && quotedEndMs > quotedStartMs
            val quotedUrl = if (hasQuote) quotedPost.effectiveAudioUrl() else null

            val isShortPulse = postKind == PostKind.PULSE_SHORT || durationMs <= 60000L
            val searchTokens = buildPostSearchTokens(
                headlineText = headlineText,
                hashtags = hashtags,
                roomName = room?.name,
                roomSlug = room?.slug,
                transcript = transcript
            )
            val initialEngagement = calculateEngagementScore(0L, 0L, 0L)
            val initialControversy = calculateControversyScore(0L, 0L, 0L)

            val rawMap = mutableMapOf<String, Any?>(
                "authorUid" to uid,
                "authorName" to author.displayName.ifBlank { "Unmuted Speaker" }.take(60),
                "authorHandle" to author.handle.ifBlank { "speaker" }.take(40),
                "authorAccentKey" to author.accentKey.take(30),
                "authorAvatarBase64" to if (effectiveAnonymous) null else author.avatarBase64?.take(215000),
                "postKind" to postKind.name,
                "roomId" to room?.id,
                "roomSlug" to room?.slug,
                "anonymousMode" to effectiveAnonymous,
                "anonymousAlias" to alias,
                "voiceDisguise" to voiceDisguise.name,
                "audioUrl" to audioUrl,
                "audioMediaId" to audioUrl,
                "durationMs" to durationMs.coerceIn(0L, 120000L),
                "waveformPeaks" to waveformPeaks.take(64).map { it.toDouble() },
                "transcript" to transcript.trim().take(4000),
                "captionSegments" to captionSegments.take(80).map { it.toMap() },
                "headlineText" to headlineText?.trim()?.takeIf { it.isNotEmpty() }?.take(280),
                "attachmentImageBase64" to attachmentImageBase64?.take(255000),
                "hashtags" to hashtags.map { it.trim().removePrefix("#").take(30) }.filter { it.isNotEmpty() }.take(10),
                "likeCount" to 0L,
                "dislikeCount" to 0L,
                "commentCount" to 0L,
                "echoCount" to 0L,
                "engagementScore" to initialEngagement,
                "controversyScore" to initialControversy,
                "searchTokens" to searchTokens,
                "isShortPulse" to isShortPulse,
                "isAnonymous" to effectiveAnonymous,
                "hotTakeMode" to hotTakeMode,
                "agreeCount" to 0L,
                "disagreeCount" to 0L,
                "quotedPostId" to if (hasQuote) quotedPost.id else null,
                "quotedAuthorName" to if (hasQuote) quotedPost.displaySpeakerName().take(60) else null,
                "quotedAudioUrl" to quotedUrl,
                "quotedAudioMediaId" to quotedUrl,
                "quotedStartMs" to if (hasQuote) quotedStartMs.coerceIn(0L, 120000L) else null,
                "quotedEndMs" to if (hasQuote) quotedEndMs.coerceIn(0L, 120000L) else null,
                "quotedDisguise" to if (hasQuote) quotedPost.disguiseEnum().name else null,
                "quotedTranscript" to if (hasQuote) quotedTranscript?.trim()?.takeIf { it.isNotEmpty() }?.take(600) else null,
                "quotedWaveformPeaks" to if (hasQuote) quotedWaveformPeaks.take(64).map { it.toDouble() } else null,
                "expiresAtMs" to expiresAtMs,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val cleanPayload = rawMap.filterValues { it != null } as Map<String, Any>
            db.collection("posts").document(postId).set(cleanPayload).await()

            if (room != null) {
                runCatching {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "postCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            invalidatePostsPaging()
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireSignedInUid()
            db.collection("posts").document(postId).delete().await()
            invalidatePostsPaging()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleVote(
        post: VoicePost,
        currentReaction: UserPostReaction?,
        targetVote: VoteType
    ): Result<Pair<Long, Long>> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val reactionPath = "users/$uid/reactions/${post.id}"
        return try {
            val prevVote = currentReaction?.voteEnum() ?: VoteType.NONE
            val nextVote = if (prevVote == targetVote) VoteType.NONE else targetVote

            var likeDelta = 0L
            var dislikeDelta = 0L
            if (prevVote == VoteType.LIKE) likeDelta -= 1L
            if (prevVote == VoteType.DISLIKE) dislikeDelta -= 1L
            if (nextVote == VoteType.LIKE) likeDelta += 1L
            if (nextVote == VoteType.DISLIKE) dislikeDelta += 1L

            val reactionPayload = mapOf(
                "postId" to post.id,
                "vote" to nextVote.name,
                "echoed" to (currentReaction?.echoed ?: false),
                "bookmarked" to (currentReaction?.bookmarked ?: false),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("reactions")
                .document(post.id)
                .set(reactionPayload)
                .await()

            val newLikes = (post.likeCount + likeDelta).coerceAtLeast(0L)
            val newDislikes = (post.dislikeCount + dislikeDelta).coerceAtLeast(0L)
            val newEngagement = calculateEngagementScore(newLikes, post.commentCount, post.echoCount)
            val newControversy = calculateControversyScore(newLikes, newDislikes, post.commentCount)
            db.collection("posts").document(post.id).update(
                mapOf(
                    "likeCount" to newLikes,
                    "dislikeCount" to newDislikes,
                    "engagementScore" to newEngagement,
                    "controversyScore" to newControversy,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newLikes to newDislikes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reactionPath)
            Result.failure(e)
        }
    }

    suspend fun toggleEcho(post: VoicePost, currentReaction: UserPostReaction?): Result<Long> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/reactions/${post.id}"
        return try {
            val nextEchoed = !(currentReaction?.echoed ?: false)
            val reactionPayload = mapOf(
                "postId" to post.id,
                "vote" to (currentReaction?.vote ?: VoteType.NONE.name),
                "echoed" to nextEchoed,
                "bookmarked" to (currentReaction?.bookmarked ?: false),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("reactions")
                .document(post.id)
                .set(reactionPayload)
                .await()

            val newEchoes = (post.echoCount + if (nextEchoed) 1L else -1L).coerceAtLeast(0L)
            val newEngagement = calculateEngagementScore(post.likeCount, post.commentCount, newEchoes)
            db.collection("posts").document(post.id).update(
                mapOf(
                    "echoCount" to newEchoes,
                    "engagementScore" to newEngagement,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newEchoes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(post: VoicePost, currentReaction: UserPostReaction?): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/reactions/${post.id}"
        return try {
            val nextBookmarked = !(currentReaction?.bookmarked ?: false)
            val reactionPayload = mapOf(
                "postId" to post.id,
                "vote" to (currentReaction?.vote ?: VoteType.NONE.name),
                "echoed" to (currentReaction?.echoed ?: false),
                "bookmarked" to nextBookmarked,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("reactions")
                .document(post.id)
                .set(reactionPayload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- COMMENTS ---

    fun observeComments(postId: String, limit: Long = 60L): Flow<List<VoiceComment>> = flow {
        val uid = requireSignedInUid()
        val path = "posts/$postId/comments"
        emitAll(
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VoiceComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun addComment(
        post: VoicePost,
        author: UserProfile,
        textBody: String?,
        audioUrl: String?,
        durationMs: Long,
        waveformPeaks: List<Float>,
        transcript: String?,
        captionSegments: List<CaptionSegment>,
        voiceDisguise: VoiceDisguise,
        stance: DebateStance = DebateStance.NONE,
        explicitCommentId: String? = null
    ): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val commentId = explicitCommentId ?: "cmt_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "posts/${post.id}/comments/$commentId"
        return try {
            val anon = post.anonymousMode
            val alias = if (anon) AliasGenerator.generateForRoom(uid, post.roomId ?: post.id) else null
            val effectiveStance = if (post.hotTakeMode) stance else DebateStance.NONE
            val rawMap = mutableMapOf<String, Any?>(
                "postId" to post.id,
                "authorUid" to uid,
                "authorName" to author.displayName.ifBlank { "Unmuted Speaker" }.take(60),
                "authorHandle" to author.handle.ifBlank { "speaker" }.take(40),
                "anonymousMode" to anon,
                "anonymousAlias" to alias,
                "voiceDisguise" to voiceDisguise.name,
                "stance" to effectiveStance.name,
                "textBody" to textBody?.trim()?.takeIf { it.isNotEmpty() }?.take(1000),
                "audioUrl" to audioUrl,
                "audioMediaId" to audioUrl,
                "durationMs" to durationMs.coerceIn(0L, 120000L),
                "waveformPeaks" to waveformPeaks.take(64).map { it.toDouble() },
                "transcript" to transcript?.trim()?.takeIf { it.isNotEmpty() }?.take(2000),
                "captionSegments" to captionSegments.take(50).map { it.toMap() },
                "likeCount" to 0L,
                "dislikeCount" to 0L,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = rawMap.filterValues { it != null } as Map<String, Any>
            db.collection("posts").document(post.id)
                .collection("comments").document(commentId)
                .set(payload)
                .await()

            val newComments = post.commentCount + 1L
            val newEngagement = calculateEngagementScore(post.likeCount, newComments, post.echoCount)
            val newControversy = calculateControversyScore(post.likeCount, post.dislikeCount, newComments)
            val postUpdates = mutableMapOf<String, Any>(
                "commentCount" to FieldValue.increment(1L),
                "engagementScore" to newEngagement,
                "controversyScore" to newControversy,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (effectiveStance == DebateStance.AGREE) {
                postUpdates["agreeCount"] = FieldValue.increment(1L)
            } else if (effectiveStance == DebateStance.DISAGREE) {
                postUpdates["disagreeCount"] = FieldValue.increment(1L)
            }
            db.collection("posts").document(post.id).update(postUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun upvoteComment(postId: String, comment: VoiceComment): Result<Unit> {
        val path = "posts/$postId/comments/${comment.id}"
        return try {
            requireSignedInUid()
            db.collection("posts").document(postId)
                .collection("comments").document(comment.id)
                .update(
                    mapOf(
                        "likeCount" to (comment.likeCount + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchRepliesByAuthor(
        targetUid: String,
        posts: List<VoicePost>
    ): List<Pair<VoicePost, VoiceComment>> {
        if (currentSignedInUidOrNull() == null || targetUid.isBlank()) return emptyList()
        val candidatePosts = posts.filter { it.commentCount > 0L }.take(25)
        if (candidatePosts.isEmpty()) return emptyList()
        return coroutineScope {
            candidatePosts.map { post ->
                async(Dispatchers.IO) {
                    try {
                        val snap = db.collection("posts").document(post.id)
                            .collection("comments")
                            .whereEqualTo("visibility", "PUBLIC")
                            .limit(60)
                            .get()
                            .await()
                        snap.toObjects(VoiceComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .filter { it.authorUid == targetUid && !it.anonymousMode }
                            .map { comment -> post to comment }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
                .flatten()
                .sortedByDescending { (_, comment) -> comment.createdAt?.toDate()?.time ?: 0L }
        }
    }
}
