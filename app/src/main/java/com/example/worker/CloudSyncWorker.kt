package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.datastore.UserPreferencesStore
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Refreshes Firebase Auth user profile and role telemetry only.
 * Personal memories, receipts, warranties, reminders, expenses, and contacts
 * never upload to Firebase.
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val auth: FirebaseAuth,
    private val cloudSyncRepository: CloudSyncRepository,
    private val preferencesStore: UserPreferencesStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = auth.currentUser ?: return Result.success()
        cloudSyncRepository.ensureUserProfile(
            displayName = user.displayName ?: "YaadRakshak User",
            email = user.email,
            photoUrl = user.photoUrl?.toString()
        )
        preferencesStore.recordCloudSync(System.currentTimeMillis())
        return Result.success()
    }
}
