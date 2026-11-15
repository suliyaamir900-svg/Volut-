package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun observeUser(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUser(uid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteUser(uid: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE userId = :userId ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeMemories(userId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE userId = :userId AND isPrivate = 0 ORDER BY updatedAtMillis DESC")
    suspend fun getPublicMemoriesOnce(userId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getAllMemoriesOnce(userId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE userId = :userId AND syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncMemories(userId: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemories(memories: List<MemoryEntity>)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM folders WHERE userId = :userId ORDER BY name ASC")
    fun observeFolders(userId: String): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun observeCategories(userId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM tags WHERE userId = :userId ORDER BY name ASC")
    fun observeTags(userId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryTagCrossRef(ref: MemoryTagCrossRef)

    @Query("SELECT * FROM file_attachments WHERE memoryId = :memoryId")
    fun observeAttachmentsForMemory(memoryId: String): Flow<List<FileAttachmentEntity>>

    @Query("SELECT * FROM file_attachments WHERE userId = :userId")
    suspend fun getAllAttachmentsOnce(userId: String): List<FileAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: FileAttachmentEntity)

    @Query("SELECT * FROM voice_notes WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeVoiceNotes(userId: String): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceNote(voiceNote: VoiceNoteEntity)
}

@Dao
interface LifeTrackerDao {
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY isCompleted ASC, dueAtMillis ASC")
    fun observeReminders(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId")
    suspend fun getRemindersOnce(userId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("SELECT * FROM warranties WHERE userId = :userId ORDER BY expiryDateMillis ASC")
    fun observeWarranties(userId: String): Flow<List<WarrantyEntity>>

    @Query("SELECT * FROM warranties WHERE userId = :userId")
    suspend fun getWarrantiesOnce(userId: String): List<WarrantyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWarranty(warranty: WarrantyEntity)

    @Query("DELETE FROM warranties WHERE id = :id")
    suspend fun deleteWarranty(id: String)

    @Query("SELECT * FROM receipts WHERE userId = :userId ORDER BY purchasedAtMillis DESC")
    fun observeReceipts(userId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE userId = :userId")
    suspend fun getReceiptsOnce(userId: String): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: String)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY spentAtMillis DESC")
    fun observeExpenses(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :userId")
    suspend fun getExpensesOnce(userId: String): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT * FROM personal_contacts WHERE userId = :userId ORDER BY name ASC")
    fun observeContacts(userId: String): Flow<List<PersonalContactEntity>>

    @Query("SELECT * FROM personal_contacts WHERE userId = :userId")
    suspend fun getContactsOnce(userId: String): List<PersonalContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: PersonalContactEntity)

    @Query("DELETE FROM personal_contacts WHERE id = :id")
    suspend fun deleteContact(id: String)

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteAllReminders(userId: String)

    @Query("DELETE FROM warranties WHERE userId = :userId")
    suspend fun deleteAllWarranties(userId: String)

    @Query("DELETE FROM receipts WHERE userId = :userId")
    suspend fun deleteAllReceipts(userId: String)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllExpenses(userId: String)

    @Query("DELETE FROM personal_contacts WHERE userId = :userId")
    suspend fun deleteAllContacts(userId: String)
}

@Dao
interface AiAndBillingDao {
    @Query("SELECT * FROM ai_conversations WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeConversations(userId: String): Flow<List<AiConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: AiConversationEntity)

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessageEntity)

    @Query("SELECT * FROM ai_sources WHERE messageId IN (SELECT id FROM ai_messages WHERE conversationId = :conversationId)")
    fun observeSourcesForConversation(conversationId: String): Flow<List<AiSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<AiSourceEntity>)

    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    fun observeSubscription(userId: String): Flow<SubscriptionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM payment_orders WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observePaymentOrders(userId: String): Flow<List<PaymentOrderEntity>>

    @Query("SELECT * FROM payment_orders WHERE id = :orderId LIMIT 1")
    suspend fun getPaymentOrderById(orderId: String): PaymentOrderEntity?

    @Query("UPDATE payment_orders SET status = :status, utrReference = :utr WHERE id = :orderId")
    suspend fun updatePaymentOrderUtrAndStatus(orderId: String, status: String, utr: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentOrder(order: PaymentOrderEntity)

    @Query("SELECT * FROM payment_transactions WHERE userId = :userId ORDER BY processedAtMillis DESC")
    fun observePaymentTransactions(userId: String): Flow<List<PaymentTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentTransaction(tx: PaymentTransactionEntity)

    @Query("SELECT * FROM backup_jobs WHERE userId = :userId ORDER BY startedAtMillis DESC")
    fun observeBackupJobs(userId: String): Flow<List<BackupJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupJob(job: BackupJobEntity)

    @Query("SELECT * FROM audit_logs ORDER BY createdAtMillis DESC LIMIT 100")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM ai_conversations WHERE userId = :userId")
    suspend fun deleteAllConversations(userId: String)
}
