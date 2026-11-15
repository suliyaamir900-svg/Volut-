package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.OcrAndMediaProcessor
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.FileAttachmentEntity
import com.example.data.local.FolderEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.MemoryTypes
import com.example.data.local.PersonalContactEntity
import com.example.data.local.ReceiptEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.UserEntity
import com.example.data.local.VoiceNoteEntity
import com.example.data.local.WarrantyEntity
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val aiRepository: AiAssistantRepository,
    private val mediaProcessor: OcrAndMediaProcessor
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL")
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _privateVaultUnlocked = MutableStateFlow(false)
    val privateVaultUnlocked: StateFlow<Boolean> = _privateVaultUnlocked.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _ocrBusy = MutableStateFlow(false)
    val ocrBusy: StateFlow<Boolean> = _ocrBusy.asStateFlow()

    val currentUser: StateFlow<UserEntity?> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else vaultRepository.observeUser(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val memories: StateFlow<List<MemoryEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeMemories(uid, decryptPrivate = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val folders: StateFlow<List<FolderEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeFolders(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeCategories(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeReminders(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val warranties: StateFlow<List<WarrantyEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeWarranties(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val receipts: StateFlow<List<ReceiptEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeReceipts(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeExpenses(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val contacts: StateFlow<List<PersonalContactEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeContacts(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val voiceNotes: StateFlow<List<VoiceNoteEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else vaultRepository.observeVoiceNotes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun bindUser(uid: String) {
        if (_activeUserId.value != uid) {
            _activeUserId.value = uid
            _privateVaultUnlocked.value = false
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setPrivateVaultUnlocked(unlocked: Boolean) {
        _privateVaultUnlocked.value = unlocked
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun saveManualMemory(
        title: String,
        content: String,
        memoryType: String = MemoryTypes.NOTE,
        category: String = "General",
        tagsCsv: String = "",
        folderId: String? = null,
        isPrivate: Boolean = false,
        isPinned: Boolean = false,
        autoEnrichWithAi: Boolean = false,
        isGuest: Boolean = false,
        existingId: String? = null
    ) {
        val uid = _activeUserId.value ?: return
        val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            var finalSummary = ""
            var finalCategory = category
            var finalTags = tags

            if (autoEnrichWithAi && !isGuest && !isPrivate && content.isNotBlank()) {
                val aiRes = aiRepository.extractStructuredMetadata(content, memoryType, uid)
                aiRes.onSuccess { extracted ->
                    finalSummary = extracted.summary.orEmpty()
                    if (!extracted.category.isNullOrBlank()) finalCategory = extracted.category
                    if (extracted.tags.isNotEmpty()) finalTags = (tags + extracted.tags).distinct()

                    if (existingId == null) {
                        extracted.receipt?.let { rec ->
                            if (!rec.merchantName.isNullOrBlank() && rec.totalAmountInr != null) {
                                vaultRepository.saveReceiptAndExpense(
                                    userId = uid,
                                    merchantName = rec.merchantName,
                                    totalAmountPaise = (rec.totalAmountInr * 100).toLong(),
                                    taxAmountPaise = ((rec.taxAmountInr ?: 0.0) * 100).toLong(),
                                    invoiceNumber = rec.invoiceNumber.orEmpty(),
                                    purchasedAtMillis = System.currentTimeMillis(),
                                    category = finalCategory
                                )
                            }
                        }
                        extracted.warranty?.let { war ->
                            if (!war.productName.isNullOrBlank()) {
                                vaultRepository.saveWarranty(
                                    userId = uid,
                                    productName = war.productName,
                                    brand = war.brand.orEmpty(),
                                    serialNumber = war.serialNumber.orEmpty(),
                                    purchaseDateMillis = System.currentTimeMillis(),
                                    expiryDateMillis = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000,
                                    coverageNotes = war.coverageNotes.orEmpty()
                                )
                            }
                        }
                    }
                }.onFailure { err ->
                    _statusBanner.value = "Saved locally. AI extraction skipped: ${err.localizedMessage}"
                }
            }

            vaultRepository.saveMemory(
                userId = uid,
                title = title,
                content = content,
                summary = finalSummary,
                memoryType = memoryType,
                folderId = folderId,
                categoryName = finalCategory,
                tags = finalTags,
                isPrivate = isPrivate,
                isPinned = isPinned,
                existingId = existingId
            )
            if (_statusBanner.value == null) {
                _statusBanner.value = if (existingId != null) "Memory updated." else "Memory saved to vault."
            }
        }
    }

    fun importFileOrImage(
        uri: Uri,
        customTitle: String,
        memoryType: String,
        isPrivate: Boolean,
        isGuest: Boolean
    ) {
        val uid = _activeUserId.value ?: return
        _ocrBusy.value = true
        viewModelScope.launch {
            val imported = mediaProcessor.importUriToPrivateStorage(uri)
            imported.onSuccess { fileRes ->
                val title = customTitle.ifBlank { fileRes.fileName }
                val bodyText = fileRes.extractedText.ifBlank {
                    "Imported file: ${fileRes.fileName} (${fileRes.mimeType}, ${fileRes.sizeBytes / 1024} KB)"
                }
                val savedMem = vaultRepository.saveMemory(
                    userId = uid,
                    title = title,
                    content = bodyText,
                    memoryType = memoryType,
                    categoryName = if (memoryType == MemoryTypes.RECEIPT) "Finance" else "Documents",
                    tags = listOf(memoryType.lowercase(), "imported"),
                    isPrivate = isPrivate
                )
                vaultRepository.saveAttachment(
                    FileAttachmentEntity(
                        id = "att_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                        memoryId = savedMem.id,
                        userId = uid,
                        localUri = fileRes.localPath,
                        mimeType = fileRes.mimeType,
                        fileName = fileRes.fileName,
                        sizeBytes = fileRes.sizeBytes,
                        extractedOcrText = fileRes.extractedText,
                        checksumSha256 = fileRes.sha256
                    )
                )
                if (!isGuest && !isPrivate && fileRes.extractedText.isNotBlank()) {
                    aiRepository.extractStructuredMetadata(fileRes.extractedText, memoryType, uid)
                        .onSuccess { extracted ->
                            vaultRepository.saveMemory(
                                userId = uid,
                                title = title,
                                content = bodyText,
                                summary = extracted.summary.orEmpty(),
                                memoryType = memoryType,
                                categoryName = extracted.category ?: "Documents",
                                tags = extracted.tags,
                                isPrivate = false,
                                existingId = savedMem.id
                            )
                        }
                }
                _statusBanner.value = if (fileRes.extractedText.isNotBlank()) {
                    "Imported and extracted OCR text (${fileRes.extractedText.length} chars)."
                } else {
                    "File imported securely into your vault."
                }
            }.onFailure { err ->
                _statusBanner.value = "Import failed: ${err.localizedMessage}"
            }
            _ocrBusy.value = false
        }
    }

    fun processScannedImageFile(file: File, customTitle: String, memoryType: String) {
        val uid = _activeUserId.value ?: return
        _ocrBusy.value = true
        viewModelScope.launch {
            val ocrText = mediaProcessor.recognizeTextFromFile(file).getOrDefault("")
            val title = customTitle.ifBlank { "Scanned Document" }
            val body = ocrText.ifBlank { "Captured document image (${file.name}). No text detected by on-device OCR." }
            val mem = vaultRepository.saveMemory(
                userId = uid,
                title = title,
                content = body,
                memoryType = memoryType,
                categoryName = if (memoryType == MemoryTypes.RECEIPT) "Finance" else "Documents",
                tags = listOf("scanned", memoryType.lowercase())
            )
            vaultRepository.saveAttachment(
                FileAttachmentEntity(
                    id = "att_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                    memoryId = mem.id,
                    userId = uid,
                    localUri = file.absolutePath,
                    mimeType = "image/jpeg",
                    fileName = file.name,
                    sizeBytes = file.length(),
                    extractedOcrText = ocrText
                )
            )
            _statusBanner.value = "Scan saved with on-device OCR."
            _ocrBusy.value = false
        }
    }

    fun saveVoiceMemory(title: String, transcript: String, audioPath: String, durationMs: Long, isGuest: Boolean) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            var summary = ""
            if (!isGuest && transcript.isNotBlank()) {
                aiRepository.extractStructuredMetadata(transcript, MemoryTypes.VOICE, uid)
                    .onSuccess { summary = it.summary.orEmpty() }
            }
            val mem = vaultRepository.saveMemory(
                userId = uid,
                title = title.ifBlank { "Voice Note" },
                content = transcript.ifBlank { "Recorded audio memo (${durationMs / 1000}s)" },
                summary = summary,
                memoryType = MemoryTypes.VOICE,
                categoryName = "Voice",
                tags = listOf("voice", "memo")
            )
            vaultRepository.saveVoiceNote(
                VoiceNoteEntity(
                    id = "voc_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                    memoryId = mem.id,
                    userId = uid,
                    audioUri = audioPath,
                    durationMs = durationMs,
                    transcript = transcript,
                    summary = summary
                )
            )
            _statusBanner.value = "Voice memory saved."
        }
    }

    fun deleteMemory(memoryId: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.deleteMemory(uid, memoryId)
            _statusBanner.value = "Memory deleted."
        }
    }

    fun createFolder(name: String) {
        val uid = _activeUserId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            vaultRepository.createFolder(uid, name)
            _statusBanner.value = "Folder '$name' created."
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            vaultRepository.deleteFolder(folderId)
            _statusBanner.value = "Folder deleted."
        }
    }

    fun addReminder(title: String, notes: String, dueAtMillis: Long, priority: String, repeatRule: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.saveReminder(uid, title, notes, dueAtMillis, priority, repeatRule)
            _statusBanner.value = "Reminder scheduled."
        }
    }

    fun updateReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            vaultRepository.updateReminder(reminder)
            _statusBanner.value = "Reminder updated."
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            vaultRepository.deleteReminder(reminderId)
            _statusBanner.value = "Reminder deleted."
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch { vaultRepository.toggleReminderCompleted(reminder) }
    }

    fun addWarranty(
        productName: String,
        brand: String,
        serialNumber: String,
        purchaseDateMillis: Long,
        expiryDateMillis: Long,
        coverageNotes: String,
        supportPhone: String
    ) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.saveWarranty(
                userId = uid,
                productName = productName,
                brand = brand,
                serialNumber = serialNumber,
                purchaseDateMillis = purchaseDateMillis,
                expiryDateMillis = expiryDateMillis,
                coverageNotes = coverageNotes,
                supportPhone = supportPhone
            )
            _statusBanner.value = "Warranty saved."
        }
    }

    fun updateWarranty(warranty: WarrantyEntity) {
        viewModelScope.launch {
            vaultRepository.updateWarranty(warranty)
            _statusBanner.value = "Warranty updated."
        }
    }

    fun deleteWarranty(warrantyId: String) {
        viewModelScope.launch {
            vaultRepository.deleteWarranty(warrantyId)
            _statusBanner.value = "Warranty deleted."
        }
    }

    fun addReceipt(merchantName: String, totalInr: Double, taxInr: Double, invoiceNumber: String, category: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.saveReceiptAndExpense(
                userId = uid,
                merchantName = merchantName,
                totalAmountPaise = (totalInr * 100).toLong(),
                taxAmountPaise = (taxInr * 100).toLong(),
                invoiceNumber = invoiceNumber,
                purchasedAtMillis = System.currentTimeMillis(),
                category = category
            )
            _statusBanner.value = "Receipt & expense recorded."
        }
    }

    fun updateReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch {
            vaultRepository.updateReceipt(receipt)
            _statusBanner.value = "Receipt updated."
        }
    }

    fun deleteReceipt(receiptId: String) {
        viewModelScope.launch {
            vaultRepository.deleteReceipt(receiptId)
            _statusBanner.value = "Receipt deleted."
        }
    }

    fun addExpense(title: String, category: String, amountInr: Double, notes: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.saveExpense(
                userId = uid,
                title = title,
                category = category,
                amountPaise = (amountInr * 100).toLong(),
                spentAtMillis = System.currentTimeMillis(),
                notes = notes
            )
            _statusBanner.value = "Expense added."
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            vaultRepository.updateExpense(expense)
            _statusBanner.value = "Expense updated."
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            vaultRepository.deleteExpense(expenseId)
            _statusBanner.value = "Expense deleted."
        }
    }

    fun addContact(name: String, relation: String, phone: String, email: String, notes: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            vaultRepository.saveContact(uid, name, relation, phone, email, notes)
            _statusBanner.value = "Contact saved to vault."
        }
    }

    fun updateContact(contact: PersonalContactEntity) {
        viewModelScope.launch {
            vaultRepository.updateContact(contact)
            _statusBanner.value = "Contact updated."
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            vaultRepository.deleteContact(contactId)
            _statusBanner.value = "Contact deleted."
        }
    }

    fun triggerCloudSync() {
        vaultRepository.enqueueBackgroundSync()
        _statusBanner.value = "Firebase Auth & Plans refresh scheduled (vault stays local)."
    }
}
