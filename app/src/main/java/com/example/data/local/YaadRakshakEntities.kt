package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object MemoryTypes {
    const val NOTE = "NOTE"
    const val DOCUMENT = "DOCUMENT"
    const val RECEIPT = "RECEIPT"
    const val WARRANTY = "WARRANTY"
    const val VOICE = "VOICE"
    const val CONTACT = "CONTACT"
}

object SyncStates {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val PENDING_SYNC = "PENDING_SYNC"
    const val SYNCED = "SYNCED"
}

@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String = "",
    val displayName: String = "User",
    val photoUrl: String = "",
    val role: String = "user",
    val subscriptionTier: String = "free",
    val adFree: Boolean = false,
    val aiCreditsRemaining: Int = 20,
    val storageUsedBytes: Long = 0L,
    val storageLimitBytes: Long = 104_857_600L,
    val isGuest: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "folders",
    indices = [Index("userId")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val iconName: String = "folder",
    val colorHex: String = "#7C3AED",
    val parentFolderId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index("userId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val isSystem: Boolean = false,
    val iconName: String = "label"
)

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index("userId"), Index(value = ["userId", "name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String
)

@Serializable
@Entity(
    tableName = "memories",
    indices = [Index("userId"), Index("folderId"), Index("categoryId"), Index("createdAtMillis"), Index("memoryType")]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val summary: String = "",
    val memoryType: String = MemoryTypes.NOTE,
    val folderId: String? = null,
    val categoryId: String? = null,
    val categoryName: String = "General",
    val tagsCsv: String = "",
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "memory_tag_cross_ref",
    primaryKeys = ["memoryId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class MemoryTagCrossRef(
    val memoryId: String,
    val tagId: String
)

@Serializable
@Entity(
    tableName = "file_attachments",
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("memoryId"), Index("userId")]
)
data class FileAttachmentEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val userId: String,
    val localUri: String,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val extractedOcrText: String = "",
    val checksumSha256: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "reminders",
    indices = [Index("userId"), Index("memoryId"), Index("dueAtMillis")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val memoryId: String? = null,
    val title: String,
    val notes: String = "",
    val dueAtMillis: Long,
    val repeatRule: String = "NONE",
    val priority: String = "MEDIUM",
    val isCompleted: Boolean = false,
    val workRequestId: String? = null,
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("memoryId"), Index("userId")]
)
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val userId: String,
    val audioUri: String,
    val durationMs: Long,
    val transcript: String,
    val summary: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "receipts",
    indices = [Index("userId"), Index("memoryId"), Index("purchasedAtMillis")]
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val memoryId: String? = null,
    val userId: String,
    val merchantName: String,
    val totalAmountPaise: Long,
    val taxAmountPaise: Long = 0L,
    val currency: String = "INR",
    val paymentMethod: String = "UPI",
    val invoiceNumber: String = "",
    val purchasedAtMillis: Long,
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "warranties",
    indices = [Index("userId"), Index("memoryId"), Index("expiryDateMillis")]
)
data class WarrantyEntity(
    @PrimaryKey val id: String,
    val memoryId: String? = null,
    val receiptId: String? = null,
    val userId: String,
    val productName: String,
    val brand: String = "",
    val serialNumber: String = "",
    val purchaseDateMillis: Long,
    val expiryDateMillis: Long,
    val coverageNotes: String = "",
    val supportPhone: String = "",
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "expenses",
    indices = [Index("userId"), Index("receiptId"), Index("spentAtMillis")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val receiptId: String? = null,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val currency: String = "INR",
    val spentAtMillis: Long,
    val notes: String = "",
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "personal_contacts",
    indices = [Index("userId")]
)
data class PersonalContactEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val relationOrRole: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val syncStatus: String = SyncStates.LOCAL_ONLY,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "ai_conversations",
    indices = [Index("userId"), Index("updatedAtMillis")]
)
data class AiConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val modelId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(entity = AiConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("conversationId"), Index("userId")]
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userId: String,
    val role: String,
    val content: String,
    val tokensUsed: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "ai_sources",
    foreignKeys = [
        ForeignKey(entity = AiMessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("messageId"), Index("memoryId")]
)
data class AiSourceEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val memoryId: String,
    val memoryTitle: String,
    val snippet: String,
    val relevanceScore: Float = 1.0f
)

@Serializable
@Entity(
    tableName = "subscriptions",
    indices = [Index("userId")]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val planId: String = "free",
    val planName: String = "Free Plan",
    val status: String = "ACTIVE",
    val startedAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = 0L,
    val autoRenew: Boolean = false,
    val verifiedByServer: Boolean = true,
    val adFree: Boolean = false,
    val aiMonthlyCredits: Long = 20L,
    val storageLimitMb: Long = 100L
)

@Serializable
@Entity(
    tableName = "payment_orders",
    indices = [Index("userId"), Index("createdAtMillis")]
)
data class PaymentOrderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val planId: String,
    val amountPaise: Long,
    val currency: String = "INR",
    val youpiPaymentId: String = "",
    val checkoutUrl: String = "",
    val idempotencyKey: String,
    val status: String = "PENDING",
    val utrReference: String = "",
    val paymentMethod: String = "YOUPI_PG",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "payment_transactions",
    indices = [Index("userId"), Index("orderId"), Index("processedAtMillis")]
)
data class PaymentTransactionEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val userId: String,
    val gatewayEvent: String,
    val settlementState: String = "",
    val utrReference: String = "",
    val amountPaise: Long,
    val verifiedSignature: Boolean = false,
    val processedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "backup_jobs",
    indices = [Index("userId"), Index("startedAtMillis")]
)
data class BackupJobEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val jobType: String,
    val status: String,
    val encryptedFilePath: String,
    val itemCount: Int,
    val checksumSha256: String,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "audit_logs",
    indices = [Index("actorUid"), Index("createdAtMillis")]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorUid: String,
    val action: String,
    val targetType: String,
    val targetId: String = "",
    val status: String,
    val detail: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
