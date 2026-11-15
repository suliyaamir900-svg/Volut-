package com.example.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.YaadRakshakApp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "YaadRakshak Reminder"
        val notes = inputData.getString(KEY_NOTES) ?: "Check your saved memory details."
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: System.currentTimeMillis().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, YaadRakshakApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(notes.ifBlank { "Scheduled personal reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(reminderId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
        const val KEY_NOTES = "notes"
    }
}
