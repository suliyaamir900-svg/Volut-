package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        FolderEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        MemoryEntity::class,
        MemoryTagCrossRef::class,
        FileAttachmentEntity::class,
        ReminderEntity::class,
        VoiceNoteEntity::class,
        ReceiptEntity::class,
        WarrantyEntity::class,
        ExpenseEntity::class,
        PersonalContactEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        AiSourceEntity::class,
        SubscriptionEntity::class,
        PaymentOrderEntity::class,
        PaymentTransactionEntity::class,
        BackupJobEntity::class,
        AuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class YaadRakshakDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun memoryDao(): MemoryDao
    abstract fun lifeTrackerDao(): LifeTrackerDao
    abstract fun aiAndBillingDao(): AiAndBillingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN adFree INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN planName TEXT NOT NULL DEFAULT 'Free Plan'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN adFree INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN aiMonthlyCredits INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN storageLimitMb INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE payment_orders ADD COLUMN utrReference TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payment_orders ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'YOUPI_PG'")
            }
        }
    }
}
