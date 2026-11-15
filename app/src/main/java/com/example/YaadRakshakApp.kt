package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YaadRakshakApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Smart Reminders & Warranties",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for scheduled reminders, bills, and expiring warranties."
            }
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Vault Sync & Backup",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background cloud sync and encrypted backup status."
            }
            manager.createNotificationChannel(remindersChannel)
            manager.createNotificationChannel(syncChannel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "yaadrakshak_reminders"
        const val CHANNEL_SYNC = "yaadrakshak_sync"
    }
}
