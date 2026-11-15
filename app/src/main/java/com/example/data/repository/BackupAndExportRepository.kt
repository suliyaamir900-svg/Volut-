package com.example.data.repository

import android.content.Context
import com.example.core.crypto.VaultCryptoManager
import com.example.core.datastore.UserPreferencesStore
import com.example.data.local.AiAndBillingDao
import com.example.data.local.BackupJobEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.LifeTrackerDao
import com.example.data.local.MemoryDao
import com.example.data.local.MemoryEntity
import com.example.data.local.PersonalContactEntity
import com.example.data.local.ReceiptEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.UserDao
import com.example.data.local.WarrantyEntity
import com.example.data.remote.CloudSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EncryptedVaultSnapshot(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val userId: String,
    val memories: List<MemoryEntity>,
    val reminders: List<ReminderEntity>,
    val warranties: List<WarrantyEntity>,
    val receipts: List<ReceiptEntity>,
    val expenses: List<ExpenseEntity>,
    val contacts: List<PersonalContactEntity>
)

@Singleton
class BackupAndExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val memoryDao: MemoryDao,
    private val lifeTrackerDao: LifeTrackerDao,
    private val aiAndBillingDao: AiAndBillingDao,
    private val cryptoManager: VaultCryptoManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val preferencesStore: UserPreferencesStore
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun observeBackupJobs(userId: String): Flow<List<BackupJobEntity>> =
        aiAndBillingDao.observeBackupJobs(userId)

    suspend fun createEncryptedBackup(userId: String): Result<BackupJobEntity> = runCatching {
        val started = System.currentTimeMillis()
        val memories = memoryDao.getAllMemoriesOnce(userId).map { m ->
            if (m.isPrivate) m.copy(content = cryptoManager.decryptText(m.content)) else m
        }
        val reminders = lifeTrackerDao.getRemindersOnce(userId)
        val warranties = lifeTrackerDao.getWarrantiesOnce(userId)
        val receipts = lifeTrackerDao.getReceiptsOnce(userId)
        val expenses = lifeTrackerDao.getExpensesOnce(userId)
        val contacts = lifeTrackerDao.getContactsOnce(userId)

        val snapshot = EncryptedVaultSnapshot(
            exportedAtMillis = started,
            userId = userId,
            memories = memories,
            reminders = reminders,
            warranties = warranties,
            receipts = receipts,
            expenses = expenses,
            contacts = contacts
        )
        val serialized = json.encodeToString(snapshot)
        val encryptedPayload = cryptoManager.encryptText(serialized)
        val checksum = cryptoManager.sha256Hex(encryptedPayload)

        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(backupDir, "yaadrakshak_backup_${started}.yrbak")
        file.writeText(encryptedPayload)

        val totalItems = memories.size + reminders.size + warranties.size + receipts.size + expenses.size + contacts.size
        val job = BackupJobEntity(
            id = "bak_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            userId = userId,
            jobType = "ENCRYPTED_LOCAL_EXPORT",
            status = "COMPLETED",
            encryptedFilePath = file.absolutePath,
            itemCount = totalItems,
            checksumSha256 = checksum,
            startedAtMillis = started,
            completedAtMillis = System.currentTimeMillis()
        )
        aiAndBillingDao.upsertBackupJob(job)
        job
    }

    suspend fun restoreFromEncryptedBackup(userId: String, filePath: String): Result<Int> = runCatching {
        val file = File(filePath)
        require(file.exists()) { "Backup file not found." }
        val raw = file.readText()
        val decrypted = cryptoManager.decryptText(raw)
        val snapshot = json.decodeFromString<EncryptedVaultSnapshot>(decrypted)

        var restoredCount = 0
        snapshot.memories.forEach { m ->
            val content = if (m.isPrivate) cryptoManager.encryptText(m.content) else m.content
            memoryDao.upsertMemory(m.copy(userId = userId, content = content))
            restoredCount++
        }
        snapshot.reminders.forEach { lifeTrackerDao.upsertReminder(it.copy(userId = userId)); restoredCount++ }
        snapshot.warranties.forEach { lifeTrackerDao.upsertWarranty(it.copy(userId = userId)); restoredCount++ }
        snapshot.receipts.forEach { lifeTrackerDao.upsertReceipt(it.copy(userId = userId)); restoredCount++ }
        snapshot.expenses.forEach { lifeTrackerDao.upsertExpense(it.copy(userId = userId)); restoredCount++ }
        snapshot.contacts.forEach { lifeTrackerDao.upsertContact(it.copy(userId = userId)); restoredCount++ }

        aiAndBillingDao.upsertBackupJob(
            BackupJobEntity(
                id = "bak_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = userId,
                jobType = "ENCRYPTED_RESTORE",
                status = "COMPLETED",
                encryptedFilePath = filePath,
                itemCount = restoredCount,
                checksumSha256 = cryptoManager.sha256Hex(raw)
            )
        )
        restoredCount
    }

    suspend fun deleteAllUserData(userId: String, isGuest: Boolean): Result<Unit> = runCatching {
        memoryDao.deleteAllForUser(userId)
        lifeTrackerDao.deleteAllReminders(userId)
        lifeTrackerDao.deleteAllWarranties(userId)
        lifeTrackerDao.deleteAllReceipts(userId)
        lifeTrackerDao.deleteAllExpenses(userId)
        lifeTrackerDao.deleteAllContacts(userId)
        aiAndBillingDao.deleteAllConversations(userId)
        userDao.deleteUser(userId)
        if (!isGuest) {
            cloudSyncRepository.deleteCloudAccountData()
        }
        preferencesStore.clearAllPreferences()
    }
}
