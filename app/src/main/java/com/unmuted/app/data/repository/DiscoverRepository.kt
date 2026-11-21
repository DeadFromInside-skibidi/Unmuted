package com.unmuted.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unmuted.app.data.DiscoverQuery
import com.unmuted.app.data.TrendingAudioTopic
import com.unmuted.app.data.VoicePost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class DiscoverRepository(
    private val db: FirebaseFirestore
) {
    private var currentPagingSource: VoicePostPagingSource? = null

    fun pagedDiscoverPosts(
        query: DiscoverQuery,
        pageSize: Int = 15
    ): Flow<PagingData<VoicePost>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                prefetchDistance = 4,
                initialLoadSize = pageSize
            ),
            pagingSourceFactory = {
                VoicePostPagingSource(
                    db = db,
                    defaultPageSize = pageSize,
                    discoverQuery = query
                ).also {
                    currentPagingSource = it
                }
            }
        ).flow
    }

    fun invalidatePaging() {
        currentPagingSource?.invalidate()
    }

    suspend fun fetchTrendingTopics(limit: Int = 4): List<TrendingAudioTopic> {
        return try {
            val snapshot = db.collection("posts")
                .whereEqualTo("visibility", "PUBLIC")
                .orderBy("engagementScore", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val posts = snapshot.documents.mapNotNull {
                it.toObject(VoicePost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            buildTrendingTopicsFromPosts(posts, limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildTrendingTopicsFromPosts(posts: List<VoicePost>, limit: Int): List<TrendingAudioTopic> {
        val topics = mutableListOf<TrendingAudioTopic>()
        val tagBuckets = mutableMapOf<String, MutableList<VoicePost>>()
        posts.forEach { post ->
            post.hashtags.forEach { rawTag ->
                val normalized = rawTag.trim().removePrefix("#").lowercase()
                if (normalized.isNotEmpty()) {
                    tagBuckets.getOrPut(normalized) { mutableListOf() }.add(post)
                }
            }
        }

        tagBuckets.entries
            .sortedByDescending { (_, bucket) ->
                bucket.size * 5 + bucket.sumOf { it.likeCount + it.dislikeCount + it.commentCount * 2 + it.echoCount }
            }
            .take(limit)
            .forEach { (tag, bucket) ->
                val hottest = bucket.maxByOrNull { it.likeCount + it.dislikeCount * 2 + it.commentCount * 3 }
                val hasDebate = bucket.any { it.dislikeCount > 0 || it.commentCount >= 2 }
                val isAnon = bucket.count { it.anonymousMode || it.isAnonymous } * 2 >= bucket.size
                val preview = hottest?.headlineText?.takeIf { it.isNotBlank() }
                    ?: hottest?.captionSegments?.firstOrNull()?.text
                    ?: hottest?.transcript?.take(72)
                    ?: ""
                topics += TrendingAudioTopic(
                    id = "tag_$tag",
                    title = "#$tag",
                    debateTag = when {
                        isAnon -> "ANON DEBATE"
                        hasDebate -> "HOT TAKE"
                        else -> "TRENDING AUDIO"
                    },
                    hotTakePreview = preview,
                    postCount = bucket.size.toLong(),
                    totalReactions = bucket.sumOf { it.likeCount + it.dislikeCount + it.commentCount + it.echoCount },
                    isAnonymousHotspot = isAnon,
                    searchToken = "#$tag"
                )
            }

        if (topics.size < limit) {
            posts
                .sortedByDescending { it.engagementScore }
                .take(limit - topics.size)
                .forEach { post ->
                    val title = post.headlineText?.takeIf { it.isNotBlank() }
                        ?: post.captionSegments.firstOrNull()?.text?.take(44)
                        ?: post.transcript.take(44).ifBlank { "Voice by ${post.displaySpeakerName()}" }
                    topics += TrendingAudioTopic(
                        id = "voice_${post.id}",
                        title = title,
                        debateTag = if (post.anonymousMode || post.isAnonymous) "ANON TAKE" else "HOT TAKE",
                        hotTakePreview = post.transcript.take(72),
                        postCount = 1L,
                        totalReactions = post.likeCount + post.dislikeCount + post.commentCount + post.echoCount,
                        isAnonymousHotspot = post.anonymousMode || post.isAnonymous,
                        searchToken = title.take(24)
                    )
                }
        }
        return topics
    }
}
