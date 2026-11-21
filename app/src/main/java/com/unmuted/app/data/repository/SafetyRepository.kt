package com.unmuted.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import com.unmuted.app.data.ReportCategory
import com.unmuted.app.data.SafetyRelation
import com.unmuted.app.data.SafetyRelationType
import com.unmuted.app.data.SafetySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Store-compliant Safety Rails Repository:
 * - User-controlled content sensitivity & community downvote visibility thresholds
 * - Per-user Mute & Block relations
 * - Structured mandatory illegal-content reporting
 */
class SafetyRepository(private val db: FirebaseFirestore) {

    fun observeSafetySettings(): Flow<SafetySettings> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid/safety_settings/config"
        emitAll(
            db.collection("users").document(uid)
                .collection("safety_settings").document("config")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(SafetySettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: SafetySettings()
                    } else {
                        SafetySettings()
                    }
                }
                .handleStreamErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun updateSafetySettings(settings: SafetySettings): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/safety_settings/config"
        return try {
            val payload = mapOf(
                "showUnfilteredControversial" to settings.showUnfilteredControversial,
                "collapseHeavilyDownvoted" to settings.collapseHeavilyDownvoted,
                "downvoteThreshold" to settings.downvoteThreshold.coerceIn(1, 100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("safety_settings").document("config")
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeSafetyRelations(): Flow<List<SafetyRelation>> = flow {
        val uid = requireSignedInUid()
        val path = "users/$uid/safety_relations"
        emitAll(
            db.collection("users").document(uid)
                .collection("safety_relations")
                .limit(200)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SafetyRelation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun setUserRelation(
        targetUid: String,
        targetName: String,
        relationType: SafetyRelationType
    ): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUid.isBlank() || targetUid == uid) return Result.success(Unit)
        val safeTargetUid = targetUid.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        if (safeTargetUid.isBlank()) return Result.failure(IllegalArgumentException("Invalid speaker ID"))
        val path = "users/$uid/safety_relations/$safeTargetUid"
        return try {
            val docRef = db.collection("users").document(uid)
                .collection("safety_relations").document(safeTargetUid)
            val existing = docRef.get().await()
            val cleanName = targetName.trim().ifBlank { "Speaker" }.take(80)
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "targetName" to cleanName,
                        "relationType" to relationType.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "targetUid" to safeTargetUid,
                        "targetName" to cleanName,
                        "relationType" to relationType.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun removeUserRelation(targetUid: String): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/safety_relations/$targetUid"
        return try {
            db.collection("users").document(uid)
                .collection("safety_relations").document(targetUid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitSafetyReport(
        targetType: String,
        targetId: String,
        category: ReportCategory,
        details: String = "",
        targetAuthorUid: String? = null
    ): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "reports/$reportId"
        return try {
            val rawMap = mutableMapOf<String, Any?>(
                "reporterUid" to uid,
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "category" to category.code,
                "reason" to category.title.take(300),
                "details" to details.trim().takeIf { it.isNotEmpty() }?.take(1000),
                "targetAuthorUid" to targetAuthorUid?.takeIf { it.isNotBlank() }?.take(128),
                "urgentLegalReview" to category.urgentLegalReview,
                "createdAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = rawMap.filterValues { it != null } as Map<String, Any>
            db.collection("reports").document(reportId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
