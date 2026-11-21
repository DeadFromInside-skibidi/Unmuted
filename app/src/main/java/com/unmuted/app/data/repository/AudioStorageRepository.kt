package com.unmuted.app.data.repository

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class AudioStorageRepository(private val storage: FirebaseStorage = Firebase.storage) {

    private suspend fun uploadFile(
        storagePath: String,
        audioFile: File,
        mimeType: String = "audio/mp4"
    ): Result<String> {
        val uid = currentSignedInUidOrNull() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return Result.failure(IllegalArgumentException("Audio file does not exist or is empty."))
        }
        require(audioFile.length() <= 10L * 1024L * 1024L) {
            "Audio file exceeds maximum upload limit of 10 MB."
        }
        return try {
            val storageRef = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("uploadedBy", uid)
                .build()

            withContext(Dispatchers.IO) {
                storageRef.putFile(Uri.fromFile(audioFile), metadata).await()
            }
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPostAudio(postId: String, audioFile: File): Result<String> =
        uploadFile("audio/posts/$postId.m4a", audioFile)

    suspend fun uploadCommentAudio(commentId: String, audioFile: File): Result<String> =
        uploadFile("audio/comments/$commentId.m4a", audioFile)

    suspend fun uploadDmAudio(convId: String, msgId: String, audioFile: File): Result<String> =
        uploadFile("audio/dms/$convId/$msgId.m4a", audioFile)

    suspend fun uploadBioAudio(uid: String, audioFile: File): Result<String> =
        uploadFile("audio/bios/$uid/bio.m4a", audioFile)
}
