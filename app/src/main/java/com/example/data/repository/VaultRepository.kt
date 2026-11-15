package com.example.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.core.crypto.VaultCryptoManager
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.FileAttachmentEntity
import com.example.data.local.FolderEntity
import com.example.data.local.LifeTrackerDao
import com.example.data.local.MemoryDao
import com.example.data.local.MemoryEntity
import com.example.data.local.MemoryTypes
import com.example.data.local.PersonalContactEntity
import com.example.data.local.ReceiptEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SyncStates
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import com.example.data.local.VoiceNoteEntity
import com.example.data.local.WarrantyEntity
import com.example.worker.CloudSyncWorker
import com.example.worker.ReminderWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first vault repository backed exclusively by Room SQLite + Android Keystore.
 * Personal memories, OCR documents, voice notes, reminders, warranties, receipts,
 * expenses, and contacts NEVER leave the device for Firebase storage.
 */
@Singleton
class VaultRepository @Inject constructor(
    private val userDao: UserDao,
    private val memoryDao: MemoryDao,
    private val lifeTrackerDao: LifeTrackerDao,
    private val cryptoManager: VaultCryptoManager,
    private val workManager: WorkManager
) {
    suspend fun ensureLocalUser(
        uid: String,
        email: String,
        displayName: String,
        photoUrl: String,
        isGuest: Boolean,
        role: String = "user"
    ) {
        val existing = userDao.getUser(uid)
        if (existing == null) {
            userDao.upsertUser(
                UserEntity(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    role = role,
                    isGuest = isGuest
                )
            )
            seedDefaultFoldersAndCategories(uid)
        } else if (existing.role != role || existing.email != email || existing.displayName != displayName) {
            userDao.upsertUser(
                existing.copy(
                    email = email.ifBlank { existing.email },
                    displayName = displayName.ifBlank { existing.displayName },
                    photoUrl = photoUrl.ifBlank { existing.photoUrl },
                    role = role,
                    isGuest = isGuest
                )
            )
        }
    }

    private suspend fun seedDefaultFoldersAndCategories(uid: String) {
        val folders = listOf("Personal IDs", "Medical", "Finance & Tax", "Warranties", "Work & Projects")
        folders.forEachIndexed { idx, name ->
            memoryDao.upsertFolder(
                FolderEntity(
                    id = "fld_${uid.take(6)}_$idx",
                    userId = uid,
                    name = name,
                    colorHex = "#EA580C"
                )
            )
        }
        val categories = listOf("General", "Documents", "Finance", "Health", "Shopping", "Travel", "Personal")
        categories.forEachIndexed { idx, name ->
            memoryDao.upsertCategory(
                CategoryEntity(
                    id = "cat_${uid.take(6)}_$idx",
                    userId = uid,
                    name = name,
                    isSystem = true
                )
            )
        }
    }

    fun observeUser(uid: String): Flow<UserEntity?> = userDao.observeUser(uid)

    fun observeMemories(uid: String, decryptPrivate: Boolean = true): Flow<List<MemoryEntity>> {
        return memoryDao.observeMemories(uid).map { list ->
            list.map { m ->
                if (m.isPrivate && decryptPrivate) {
                    m.copy(content = cryptoManager.decryptText(m.content))
                } else {
                    m
                }
            }
        }
    }

    fun observeFolders(uid: String): Flow<List<FolderEntity>> = memoryDao.observeFolders(uid)
    fun observeCategories(uid: String): Flow<List<CategoryEntity>> = memoryDao.observeCategories(uid)
    fun observeReminders(uid: String): Flow<List<ReminderEntity>> = lifeTrackerDao.observeReminders(uid)
    fun observeWarranties(uid: String): Flow<List<WarrantyEntity>> = lifeTrackerDao.observeWarranties(uid)
    fun observeReceipts(uid: String): Flow<List<ReceiptEntity>> = lifeTrackerDao.observeReceipts(uid)
    fun observeExpenses(uid: String): Flow<List<ExpenseEntity>> = lifeTrackerDao.observeExpenses(uid)
    fun observeContacts(uid: String): Flow<List<PersonalContactEntity>> = lifeTrackerDao.observeContacts(uid)
    fun observeVoiceNotes(uid: String): Flow<List<VoiceNoteEntity>> = memoryDao.observeVoiceNotes(uid)

    suspend fun saveMemory(
        userId: String,
        title: String,
        content: String,
        summary: String = "",
        memoryType: String = MemoryTypes.NOTE,
        folderId: String? = null,
        categoryName: String = "General",
        tags: List<String> = emptyList(),
        isPrivate: Boolean = false,
        isPinned: Boolean = false,
        existingId: String? = null
    ): MemoryEntity {
        val existing = existingId?.let { memoryDao.getMemoryById(it) }
        val id = existingId ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val storedContent = if (isPrivate) cryptoManager.encryptText(content) else content
        val now = System.currentTimeMillis()

        val entity = MemoryEntity(
            id = id,
            userId = userId,
            title = title.trim().ifBlank { "Untitled Memory" },
            content = storedContent,
            summary = summary.ifBlank { existing?.summary.orEmpty() },
            memoryType = memoryType,
            folderId = folderId ?: existing?.folderId,
            categoryName = categoryName.ifBlank { "General" },
            tagsCsv = tags.joinToString(", "),
            isPrivate = isPrivate,
            isPinned = isPinned,
            syncStatus = SyncStates.LOCAL_ONLY,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now
        )
        memoryDao.upsertMemory(entity)
        return entity
    }

    suspend fun deleteMemory(userId: String, memoryId: String) {
        memoryDao.deleteMemoryById(memoryId)
    }

    suspend fun saveAttachment(attachment: FileAttachmentEntity) {
        memoryDao.upsertAttachment(attachment)
    }

    suspend fun saveVoiceNote(voiceNote: VoiceNoteEntity) {
        memoryDao.upsertVoiceNote(voiceNote)
    }

    suspend fun createFolder(userId: String, name: String) {
        memoryDao.upsertFolder(
            FolderEntity(
                id = "fld_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                userId = userId,
                name = name.trim(),
                colorHex = "#EA580C"
            )
        )
    }

    suspend fun deleteFolder(folderId: String) {
        memoryDao.deleteFolder(folderId)
    }

    suspend fun saveReminder(
        userId: String,
        title: String,
        notes: String,
        dueAtMillis: Long,
        priority: String = "MEDIUM",
        repeatRule: String = "NONE",
        memoryId: String? = null
    ): ReminderEntity {
        val id = "rem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val delayMs = (dueAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_REMINDER_ID to id,
                    ReminderWorker.KEY_TITLE to title,
                    ReminderWorker.KEY_NOTES to notes
                )
            )
            .build()
        workManager.enqueueUniqueWork("reminder_$id", ExistingWorkPolicy.REPLACE, req)

        val entity = ReminderEntity(
            id = id,
            userId = userId,
            memoryId = memoryId,
            title = title.trim(),
            notes = notes.trim(),
            dueAtMillis = dueAtMillis,
            repeatRule = repeatRule,
            priority = priority,
            isCompleted = false,
            workRequestId = req.id.toString(),
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertReminder(entity)
        return entity
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        lifeTrackerDao.upsertReminder(reminder)
    }

    suspend fun deleteReminder(reminderId: String) {
        workManager.cancelUniqueWork("reminder_$reminderId")
        lifeTrackerDao.deleteReminder(reminderId)
    }

    suspend fun toggleReminderCompleted(reminder: ReminderEntity) {
        lifeTrackerDao.upsertReminder(reminder.copy(isCompleted = !reminder.isCompleted))
    }

    suspend fun saveWarranty(
        userId: String,
        productName: String,
        brand: String,
        serialNumber: String,
        purchaseDateMillis: Long,
        expiryDateMillis: Long,
        coverageNotes: String,
        supportPhone: String = "",
        memoryId: String? = null
    ): WarrantyEntity {
        val id = "war_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val entity = WarrantyEntity(
            id = id,
            memoryId = memoryId,
            userId = userId,
            productName = productName.trim(),
            brand = brand.trim(),
            serialNumber = serialNumber.trim(),
            purchaseDateMillis = purchaseDateMillis,
            expiryDateMillis = expiryDateMillis,
            coverageNotes = coverageNotes.trim(),
            supportPhone = supportPhone.trim(),
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertWarranty(entity)
        return entity
    }

    suspend fun updateWarranty(warranty: WarrantyEntity) {
        lifeTrackerDao.upsertWarranty(warranty)
    }

    suspend fun deleteWarranty(warrantyId: String) {
        lifeTrackerDao.deleteWarranty(warrantyId)
    }

    suspend fun saveReceiptAndExpense(
        userId: String,
        merchantName: String,
        totalAmountPaise: Long,
        taxAmountPaise: Long,
        invoiceNumber: String,
        purchasedAtMillis: Long,
        category: String = "Shopping",
        memoryId: String? = null
    ): ReceiptEntity {
        val id = "rec_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val receipt = ReceiptEntity(
            id = id,
            memoryId = memoryId,
            userId = userId,
            merchantName = merchantName.trim(),
            totalAmountPaise = totalAmountPaise,
            taxAmountPaise = taxAmountPaise,
            invoiceNumber = invoiceNumber.trim(),
            purchasedAtMillis = purchasedAtMillis,
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertReceipt(receipt)

        val expId = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val expense = ExpenseEntity(
            id = expId,
            userId = userId,
            receiptId = id,
            title = merchantName.trim(),
            category = category,
            amountPaise = totalAmountPaise,
            spentAtMillis = purchasedAtMillis,
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertExpense(expense)
        return receipt
    }

    suspend fun updateReceipt(receipt: ReceiptEntity) {
        lifeTrackerDao.upsertReceipt(receipt)
    }

    suspend fun deleteReceipt(receiptId: String) {
        lifeTrackerDao.deleteReceipt(receiptId)
    }

    suspend fun saveExpense(
        userId: String,
        title: String,
        category: String,
        amountPaise: Long,
        spentAtMillis: Long,
        notes: String = ""
    ) {
        val id = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val expense = ExpenseEntity(
            id = id,
            userId = userId,
            title = title.trim(),
            category = category.trim().ifBlank { "General" },
            amountPaise = amountPaise,
            spentAtMillis = spentAtMillis,
            notes = notes.trim(),
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertExpense(expense)
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        lifeTrackerDao.upsertExpense(expense)
    }

    suspend fun deleteExpense(expenseId: String) {
        lifeTrackerDao.deleteExpense(expenseId)
    }

    suspend fun saveContact(
        userId: String,
        name: String,
        relationOrRole: String,
        phone: String,
        email: String,
        notes: String
    ) {
        val id = "cnt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val contact = PersonalContactEntity(
            id = id,
            userId = userId,
            name = name.trim(),
            relationOrRole = relationOrRole.trim(),
            phone = phone.trim(),
            email = email.trim(),
            notes = notes.trim(),
            syncStatus = SyncStates.LOCAL_ONLY
        )
        lifeTrackerDao.upsertContact(contact)
    }

    suspend fun updateContact(contact: PersonalContactEntity) {
        lifeTrackerDao.upsertContact(contact)
    }

    suspend fun deleteContact(contactId: String) {
        lifeTrackerDao.deleteContact(contactId)
    }

    fun enqueueBackgroundSync() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork("yaadrakshak_auth_refresh", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val GUEST_UID = "guest_local_user"
    }
}
