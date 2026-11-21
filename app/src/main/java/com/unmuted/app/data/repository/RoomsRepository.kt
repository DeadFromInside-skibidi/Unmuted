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
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.JoinedRoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CopyOnWriteArrayList

class RoomsRepository(private val db: FirebaseFirestore) {

    private val activePagingSources = CopyOnWriteArrayList<RoomPagingSource>()

    fun pagedRoomsFlow(pageSize: Int = 15): Flow<PagingData<CommunityRoom>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize,
                prefetchDistance = 4,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                RoomPagingSource(db, pageSize).also { source ->
                    activePagingSources.add(source)
                    source.registerInvalidatedCallback { activePagingSources.remove(source) }
                }
            }
        ).flow
    }

    fun invalidateRoomsPaging() {
        activePagingSources.toList().forEach { it.invalidate() }
    }

    fun observeRoomsWindow(limit: Long = 35L): Flow<List<CommunityRoom>> = flow {
        val uid = requireSignedInUid()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommunityRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.memberCount }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeJoinedRooms(): Flow<Map<String, JoinedRoom>> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid/joined_rooms"
        emitAll(
            db.collection("users").document(uid).collection("joined_rooms")
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(JoinedRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .associateBy { it.roomId.ifBlank { it.id } }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createRoom(
        name: String,
        slugInput: String,
        description: String,
        category: String,
        anonymousRoom: Boolean,
        accentKey: String,
        guidelines: String
    ): Result<String> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val roomId = nextReverseChronoId("room")
        val path = "rooms/$roomId"
        return try {
            val cleanName = name.trim().take(60)
            require(cleanName.length >= 2) { "Room name must be at least 2 characters." }
            val cleanSlug = slugInput.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
                .ifBlank { cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "") }
                .take(32)
            require(cleanSlug.length >= 2) { "Room handle must be at least 2 alphanumeric characters." }
            val cleanDesc = description.trim().take(300)
            require(cleanDesc.isNotEmpty()) { "Please add a short description for your room." }

            @Suppress("UNCHECKED_CAST")
            val payload = mutableMapOf<String, Any?>(
                "slug" to cleanSlug,
                "name" to cleanName,
                "description" to cleanDesc,
                "category" to category.trim().ifBlank { "General" }.take(40),
                "anonymousRoom" to anonymousRoom,
                "accentKey" to accentKey.take(30),
                "guidelines" to guidelines.trim().takeIf { it.isNotEmpty() }?.take(400),
                "creatorUid" to uid,
                "memberCount" to 1L,
                "postCount" to 0L,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            db.collection("rooms").document(roomId).set(payload).await()

            val alias = if (anonymousRoom) AliasGenerator.generateForRoom(uid, roomId) else ""
            db.collection("users").document(uid).collection("joined_rooms").document(roomId).set(
                mapOf(
                    "roomId" to roomId,
                    "anonymousAlias" to alias,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            invalidateRoomsPaging()
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleJoinRoom(room: CommunityRoom, currentlyJoined: Boolean): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/joined_rooms/${room.id}"
        return try {
            val joinRef = db.collection("users").document(uid).collection("joined_rooms").document(room.id)
            if (currentlyJoined) {
                joinRef.delete().await()
                val newCount = (room.memberCount - 1L).coerceAtLeast(0L)
                db.collection("rooms").document(room.id).update(
                    mapOf("memberCount" to newCount, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            } else {
                val alias = if (room.anonymousRoom) AliasGenerator.generateForRoom(uid, room.id) else ""
                joinRef.set(
                    mapOf(
                        "roomId" to room.id,
                        "anonymousAlias" to alias,
                        "joinedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                db.collection("rooms").document(room.id).update(
                    mapOf("memberCount" to (room.memberCount + 1L), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            invalidateRoomsPaging()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
