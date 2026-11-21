package com.unmuted.app.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DiscoverFilter
import com.unmuted.app.data.DiscoverQuery
import com.unmuted.app.data.VoicePost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Cursor-based Paging 3 PagingSource for public VoicePosts.
 * Uses a single-equality query (`visibility == PUBLIC`) with `.limit()` and `.startAfter(cursor)`
 * so zero Firestore composite indexes are required, while reverse-chronological post IDs keep
 * newly created takes at the top of page 1.
 */
class VoicePostPagingSource(
    private val db: FirebaseFirestore,
    private val defaultPageSize: Int = 15,
    private val discoverQuery: DiscoverQuery? = null
) : PagingSource<DocumentSnapshot, VoicePost>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, VoicePost>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, VoicePost> {
        return try {
            requireSignedInUid()
            val limitCount = params.loadSize.coerceIn(5, 30).toLong().coerceAtLeast(defaultPageSize.toLong())

            if (discoverQuery?.filter == DiscoverFilter.FOLLOWING && discoverQuery.followingUids.isEmpty()) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }

            val baseQuery = db.collection("posts")
                .whereEqualTo("visibility", "PUBLIC")

            val searchToken = discoverQuery?.searchToken?.trim()?.lowercase()
                ?.removePrefix("#")
                ?.trim { it !in 'a'..'z' && it !in '0'..'9' }

            val queryWithFilter = if (!searchToken.isNullOrBlank()) {
                baseQuery
                    .whereArrayContains("searchTokens", searchToken)
                    .orderBy("engagementScore", Query.Direction.DESCENDING)
            } else if (discoverQuery != null) {
                when (discoverQuery.filter) {
                    DiscoverFilter.TRENDING -> {
                        baseQuery.orderBy("engagementScore", Query.Direction.DESCENDING)
                    }
                    DiscoverFilter.CONTROVERSIAL -> {
                        baseQuery.orderBy("controversyScore", Query.Direction.DESCENDING)
                    }
                    DiscoverFilter.ANONYMOUS -> {
                        baseQuery
                            .whereEqualTo("isAnonymous", true)
                            .orderBy("engagementScore", Query.Direction.DESCENDING)
                    }
                    DiscoverFilter.SHORT_CLIPS -> {
                        baseQuery
                            .whereEqualTo("isShortPulse", true)
                            .orderBy("engagementScore", Query.Direction.DESCENDING)
                    }
                    DiscoverFilter.FOLLOWING -> {
                        val targets = discoverQuery.followingUids.take(30).toList()
                        baseQuery
                            .whereIn("authorUid", targets)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                    }
                }
            } else {
                baseQuery
            }

            var pagedQuery = queryWithFilter.limit(limitCount)
            val cursor = params.key
            if (cursor != null) {
                pagedQuery = pagedQuery.startAfter(cursor)
            }

            val snapshot = pagedQuery.get().await()
            val docs = snapshot.documents
            val posts = docs.mapNotNull { doc ->
                doc.toObject(VoicePost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            val finalPosts = if (discoverQuery == null) {
                posts.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            } else {
                posts
            }

            val nextKey = if (docs.size < limitCount) null else docs.lastOrNull()
            LoadResult.Page(
                data = finalPosts,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "posts")
            LoadResult.Error(e)
        }
    }
}

/**
 * Cursor-based Paging 3 PagingSource for public CommunityRooms.
 * Uses single-equality `visibility == PUBLIC` + `.limit()` + `.startAfter(cursor)`.
 */
class RoomPagingSource(
    private val db: FirebaseFirestore,
    private val defaultPageSize: Int = 15
) : PagingSource<DocumentSnapshot, CommunityRoom>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, CommunityRoom>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, CommunityRoom> {
        return try {
            requireSignedInUid()
            val limitCount = params.loadSize.coerceIn(5, 30).toLong().coerceAtLeast(defaultPageSize.toLong())
            var query = db.collection("rooms")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limitCount)

            val cursor = params.key
            if (cursor != null) {
                query = query.startAfter(cursor)
            }

            val snapshot = query.get().await()
            val docs = snapshot.documents
            val rooms = docs.mapNotNull { doc ->
                doc.toObject(CommunityRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.sortedByDescending { it.memberCount }

            val nextKey = if (docs.size < limitCount) null else docs.lastOrNull()
            LoadResult.Page(
                data = rooms,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rooms")
            LoadResult.Error(e)
        }
    }
}
