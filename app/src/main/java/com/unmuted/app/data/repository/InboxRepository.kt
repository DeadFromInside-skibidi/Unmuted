package com.unmuted.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.Conversation
import com.unmuted.app.data.DirectMessage
import com.unmuted.app.data.MessageKind
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.formatDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class InboxRepository(
    private val db: FirebaseFirestore,
    private val audioStorage: AudioStorageRepository = AudioStorageRepository()
) {

    fun observeConversations(limit: Long = 35L): Flow<List<Conversation>> = flow {
        val uid = requireSignedInUid()
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeMessages(conversationId: String, limit: Long = 80L): Flow<List<DirectMessage>> = flow {
        val uid = requireSignedInUid()
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun getOrCreateConversation(myProfile: UserProfile, otherProfile: UserProfile): Result<Conversation> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val sortedUids = listOf(uid, otherProfile.userId).sorted()
        val convId = "${sortedUids[0]}_${sortedUids[1]}".replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(120)
        val path = "conversations/$convId"
        return try {
            val docRef = db.collection("conversations").document(convId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val conv = existing.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (conv != null) return Result.success(conv)
            }
            val names = mapOf(uid to myProfile.displayName, otherProfile.userId to otherProfile.displayName)
            val handles = mapOf(uid to myProfile.handle, otherProfile.userId to otherProfile.handle)
            val payload = mapOf(
                "participantUids" to sortedUids,
                "participantNames" to names,
                "participantHandles" to handles,
                "lastMessagePreview" to "Start speaking...",
                "lastMessageKind" to MessageKind.TEXT.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                Conversation(
                    id = convId,
                    participantUids = sortedUids,
                    participantNames = names,
                    participantHandles = handles,
                    lastMessagePreview = "Start speaking...",
                    lastMessageKind = MessageKind.TEXT.name
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        sender: UserProfile,
        kind: MessageKind,
        textBody: String? = null,
        audioFile: File? = null,
        durationMs: Long = 0L,
        waveformPeaks: List<Float> = emptyList(),
        transcript: String? = null,
        captionSegments: List<CaptionSegment> = emptyList(),
        imageBase64: String? = null
    ): Result<Unit> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "conversations/${conversation.id}/messages/$msgId"
        return try {
            val audioUrl = if (audioFile != null && audioFile.exists()) {
                val upRes = audioStorage.uploadDmAudio(conversation.id, msgId, audioFile)
                upRes.getOrThrow()
            } else null

            val rawMap = mutableMapOf<String, Any?>(
                "conversationId" to conversation.id,
                "senderUid" to uid,
                "senderName" to sender.displayName.ifBlank { "Speaker" }.take(60),
                "participantUids" to conversation.participantUids,
                "messageKind" to kind.name,
                "textBody" to textBody?.trim()?.takeIf { it.isNotEmpty() }?.take(2000),
                "audioUrl" to audioUrl,
                "durationMs" to durationMs.coerceIn(0L, 120000L),
                "waveformPeaks" to waveformPeaks.take(64).map { it.toDouble() },
                "transcript" to transcript?.trim()?.takeIf { it.isNotEmpty() }?.take(2000),
                "captionSegments" to captionSegments.take(50).map { it.toMap() },
                "imageBase64" to imageBase64?.take(255000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = rawMap.filterValues { it != null } as Map<String, Any>
            db.collection("conversations").document(conversation.id)
                .collection("messages").document(msgId)
                .set(payload)
                .await()

            val preview = when (kind) {
                MessageKind.VOICE -> "Voice (${formatDurationMs(durationMs)}) · ${transcript?.take(50).orEmpty()}"
                MessageKind.IMAGE -> "Photo attachment"
                MessageKind.TEXT -> textBody?.trim().orEmpty()
            }.take(195)

            db.collection("conversations").document(conversation.id).update(
                mapOf(
                    "lastMessagePreview" to preview,
                    "lastMessageKind" to kind.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
