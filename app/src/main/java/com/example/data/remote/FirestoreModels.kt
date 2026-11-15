package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String = "User",
    val photoUrl: String? = null,
    val role: String = "user",
    val subscriptionTier: String = "free",
    val adFree: Boolean = false,
    val aiCreditsRemaining: Long = 20L,
    val storageUsedBytes: Long = 0L,
    val storageLimitBytes: Long = 104_857_600L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(uid: String, assignRole: String = "user"): Map<String, Any> = mapOf(
        "userId" to uid,
        "email" to email,
        "displayName" to displayName.ifBlank { "User" },
        "photoUrl" to photoUrl,
        "role" to assignRole,
        "subscriptionTier" to "free",
        "adFree" to false,
        "aiCreditsRemaining" to aiCreditsRemaining,
        "storageUsedBytes" to storageUsedBytes,
        "storageLimitBytes" to storageLimitBytes,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class FirestorePlan(
    @DocumentId val id: String = "",
    val planId: String = "free",
    val name: String = "Free",
    val priceInr: Long = 0L,
    val interval: String = "free",
    val aiMonthlyCredits: Long = 20L,
    val storageLimitMb: Long = 100L,
    val adFree: Boolean = false,
    val active: Boolean = true
)

@IgnoreExtraProperties
data class FirestoreSubscription(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planId: String = "free",
    val status: String = "ACTIVE",
    val expiresAtMillis: Long = 0L,
    val verifiedByServer: Boolean = false,
    val adFree: Boolean = false
)

@IgnoreExtraProperties
data class FirestorePaymentOrder(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val planId: String = "",
    val amountPaise: Long = 0L,
    val currency: String = "INR",
    val status: String = "PENDING",
    val youpiPaymentId: String = "",
    val checkoutUrl: String = "",
    val upiPayUri: String = "",
    val utrReference: String = "",
    val paymentMethod: String = "YOUPI_PG",
    val createdAtMillis: Long = 0L
)

@IgnoreExtraProperties
data class FirestorePaymentTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderId: String = "",
    val gatewayEvent: String = "",
    val settlementState: String = "",
    val amountPaise: Long = 0L,
    val utrReference: String = "",
    val verifiedSignature: Boolean = false
)

@IgnoreExtraProperties
data class FirestorePublicPaymentInfo(
    @DocumentId val id: String = "",
    val paymentUpiId: String = "",
    val payeeName: String = "YaadRakshak",
    val bankName: String = "",
    val bankAccountDisplay: String = "",
    val maskedBankAccount: String = "",
    val ifscCode: String = "",
    val merchantId: String = "",
    val youpiReady: Boolean = false,
    val upiReady: Boolean = false,
    val bankReady: Boolean = false
)
