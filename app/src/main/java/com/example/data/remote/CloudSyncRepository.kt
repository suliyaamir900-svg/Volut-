package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Firestore repository strictly scoped to Authentication identity,
 * Admin role verification, and server-managed Billing entitlements.
 * All personal vault data (memories, reminders, warranties, receipts, expenses,
 * contacts, and backups) remains 100% local on device in Room.
 */
@Singleton
class CloudSyncRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firebase account data.")
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<FirestoreUserProfile> = runCatching {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firebase account data.")
        val uid = currentUser.uid
        val isBootstrapAdmin = currentUser.isEmailVerified &&
            currentUser.email.equals(ADMIN_BOOTSTRAP_EMAIL, ignoreCase = true)
        val assignedRole = if (isBootstrapAdmin) "admin" else "user"

        val docRef = db.collection("users").document(uid)
        val snap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        if (!snap.exists()) {
            val profile = FirestoreUserProfile(
                id = uid,
                userId = uid,
                email = email,
                displayName = displayName.ifBlank { "YaadRakshak User" },
                photoUrl = photoUrl,
                role = assignedRole,
                subscriptionTier = "free"
            )
            try {
                docRef.set(profile.toCreateMap(uid, assignRole = assignedRole)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
            profile
        } else {
            val existing = snap.toObject(
                FirestoreUserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: FirestoreUserProfile(id = uid, userId = uid)
            val updateMap = buildMap<String, Any> {
                put("displayName", displayName.ifBlank { existing.displayName })
                if (!email.isNullOrBlank()) put("email", email)
                if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl)
                if (isBootstrapAdmin && existing.role != "admin") put("role", "admin")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            try {
                docRef.update(updateMap).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
            existing.copy(role = if (isBootstrapAdmin) "admin" else existing.role)
        }
    }

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email.equals(ADMIN_BOOTSTRAP_EMAIL, ignoreCase = true)) {
            return true
        }
        val docRef = db.collection("admins").document(user.uid)
        return try {
            docRef.get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            false
        }
    }

    fun observeUserProfile(): Flow<FirestoreUserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        docRef.snapshots()
            .map { snap ->
                snap.toObject(
                    FirestoreUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePlans(): Flow<List<FirestorePlan>> = flow {
        requireUserId()
        val colRef = db.collection("plans")
        colRef.snapshots()
            .map { snap -> snap.toObjects(FirestorePlan::class.java) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, colRef.path)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeSubscription(): Flow<FirestoreSubscription?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("subscriptions").document("current")
        docRef.snapshots()
            .map { it.toObject(FirestoreSubscription::class.java) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePaymentOrders(): Flow<List<FirestorePaymentOrder>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("payment_orders")
        colRef.snapshots()
            .map { it.toObjects(FirestorePaymentOrder::class.java) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, colRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePaymentTransactions(): Flow<List<FirestorePaymentTransaction>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("payment_transactions")
        colRef.snapshots()
            .map { it.toObjects(FirestorePaymentTransaction::class.java) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, colRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observePublicPaymentInfo(): Flow<FirestorePublicPaymentInfo?> = flow {
        requireUserId()
        val docRef = db.collection("app_config").document("payments_public")
        docRef.snapshots()
            .map { it.toObject(FirestorePublicPaymentInfo::class.java) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(null)
            }
            .collect { emit(it) }
    }

    suspend fun deleteCloudAccountData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        try {
            userDoc.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            throw e
        }
    }

    companion object {
        const val ADMIN_BOOTSTRAP_EMAIL = "suliyaamir900@gmail.com"
    }
}
