package com.unmuted.app.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.unmuted.app.R
import com.unmuted.app.core.OperationType
import com.unmuted.app.core.handleFirestoreError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.util.UUID

object FirestoreProvider {
    fun getDatabase(context: Context): FirebaseFirestore =
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
}

internal fun requireSignedInUid(): String =
    Firebase.auth.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

internal fun currentSignedInUidOrNull(): String? = Firebase.auth.currentUser?.uid

internal fun nextReverseChronoId(prefix: String): String {
    val revTs = (9_999_999_999_999L - System.currentTimeMillis()).coerceAtLeast(0L)
        .toString()
        .padStart(13, '0')
    val nonce = UUID.randomUUID().toString().replace("-", "").take(6)
    return "${prefix}_${revTs}_${nonce}"
}

internal fun <T> Flow<T>.handleStreamErrors(
    op: OperationType,
    path: String,
    expectedUid: String
): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && Firebase.auth.currentUser?.uid == expectedUid) {
        handleFirestoreError(exceptionToLog, op, path)
    }
    throw error
}
