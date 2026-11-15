package com.example.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.R
import com.example.data.local.AiAndBillingDao
import com.example.data.local.LifeTrackerDao
import com.example.data.local.MemoryDao
import com.example.data.local.UserDao
import com.example.data.local.YaadRakshakDatabase
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YaadRakshakDatabase {
        return Room.databaseBuilder(
            context,
            YaadRakshakDatabase::class.java,
            "yaadrakshak.db"
        )
            .addMigrations(YaadRakshakDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideUserDao(db: YaadRakshakDatabase): UserDao = db.userDao()

    @Provides
    fun provideMemoryDao(db: YaadRakshakDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideLifeTrackerDao(db: YaadRakshakDatabase): LifeTrackerDao = db.lifeTrackerDao()

    @Provides
    fun provideAiAndBillingDao(db: YaadRakshakDatabase): AiAndBillingDao = db.aiAndBillingDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(@ApplicationContext context: Context): FirebaseFirestore {
        val dbId = context.getString(R.string.firestore_database_id)
        return FirebaseFirestore.getInstance(dbId)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
