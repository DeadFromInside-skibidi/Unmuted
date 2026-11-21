package com.unmuted.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import com.unmuted.app.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProfileRepository(private val db: FirebaseFirestore) {

    fun observeMyProfile(): Flow<UserProfile?> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleStreamErrors(OperationType.GET, path, uid)
        )
    }

    fun observePublicProfiles(limit: Long = 60L): Flow<List<UserProfile>> = flow {
        val uid = requireSignedInUid()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("visibility", "PUBLIC")
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun ensureProfileExists(defaultDisplayName: String): Result<UserProfile> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = defaultDisplayName,
                        handle = buildSafeHandle(defaultDisplayName, uid)
                    )
                return Result.success(profile)
            }
            val safeName = defaultDisplayName.trim().ifBlank { "Unmuted Speaker" }.take(60)
            val safeHandle = buildSafeHandle(safeName, uid)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to "Speaking unfiltered on Unmuted.",
                "accentKey" to "CORAL",
                "bannerKey" to "OBSIDIAN_WAVE",
                "waveformStyle" to "BARS",
                "interests" to listOf("Late Night", "Culture", "Unfiltered"),
                "followerCount" to 0L,
                "followingCount" to 0L,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    handle = safeHandle,
                    bio = "Speaking unfiltered on Unmuted."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        handle: String,
        bio: String,
        accentKey: String,
        bannerKey: String,
        waveformStyle: String,
        interests: List<String>,
        avatarBase64: String? = null,
        voiceBioAudioUrl: String? = null,
        voiceBioTranscript: String? = null,
        voiceBioDurationMs: Long? = null,
        voiceBioPeaks: List<Double>? = null
    ): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val safeName = displayName.trim().take(60)
            require(safeName.isNotEmpty()) { "Display name cannot be empty." }
            val safeHandle = handle.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "").take(30)
            require(safeHandle.length >= 2) { "Handle must be at least 2 characters." }
            val updates = mutableMapOf<String, Any?>(
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to bio.trim().take(240),
                "accentKey" to accentKey.take(30),
                "bannerKey" to bannerKey.take(30),
                "waveformStyle" to waveformStyle.take(30),
                "interests" to interests.map { it.trim().take(28) }.filter { it.isNotEmpty() }.take(12),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarBase64 != null) updates["avatarBase64"] = avatarBase64.take(215000)
            if (voiceBioAudioUrl != null) {
                updates["voiceBioAudioUrl"] = voiceBioAudioUrl
                updates["voiceBioMediaId"] = voiceBioAudioUrl
            }
            if (voiceBioTranscript != null) updates["voiceBioTranscript"] = voiceBioTranscript.take(600)
            if (voiceBioDurationMs != null) updates["voiceBioDurationMs"] = voiceBioDurationMs.coerceIn(0L, 60000L)
            if (voiceBioPeaks != null) updates["voiceBioPeaks"] = voiceBioPeaks.take(64)

            @Suppress("UNCHECKED_CAST")
            db.collection("users").document(uid)
                .update(updates.filterValues { it != null } as Map<String, Any>)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFollowingUids(): Flow<Set<String>> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid/following"
        emitAll(
            db.collection("users").document(uid).collection("following")
                .limit(200)
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toSet() }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun toggleFollow(targetUid: String, currentlyFollowing: Boolean): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUid == uid) return Result.success(Unit)
        val path = "users/$uid/following/$targetUid"
        return try {
            val ref = db.collection("users").document(uid).collection("following").document(targetUid)
            if (currentlyFollowing) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "targetUid" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun buildSafeHandle(name: String, uid: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]"), "").take(14).ifBlank { "voice" }
        val suffix = uid.takeLast(4).lowercase().replace(Regex("[^a-z0-9]"), "7")
        return "${base}_$suffix"
    }
}
