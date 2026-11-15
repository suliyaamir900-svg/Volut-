package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class MemoryContextDto(
    val id: String,
    val title: String,
    val memoryType: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatHistoryDto(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class AiChatRequestDto(
    val question: String,
    val memories: List<MemoryContextDto>,
    val history: List<ChatHistoryDto> = emptyList(),
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class AiChatResponseDto(
    val answer: String,
    val citedMemoryIds: List<String> = emptyList(),
    val found: Boolean = false,
    val relatedSuggestions: List<String> = emptyList(),
    val model: String? = null,
    val tokensUsed: Int = 0,
    val remainingCredits: Int? = null
)

@JsonClass(generateAdapter = true)
data class AiExtractRequestDto(
    val text: String,
    val memoryType: String = "DOCUMENT",
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractedReceiptDto(
    val merchantName: String? = null,
    val totalAmountInr: Double? = null,
    val taxAmountInr: Double? = null,
    val invoiceNumber: String? = null,
    val dateIso: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractedWarrantyDto(
    val productName: String? = null,
    val brand: String? = null,
    val serialNumber: String? = null,
    val expiryDateIso: String? = null,
    val coverageNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class AiExtractResponseDto(
    val summary: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val receipt: ExtractedReceiptDto? = null,
    val warranty: ExtractedWarrantyDto? = null,
    val model: String? = null,
    val tokensUsed: Int = 0,
    val remainingCredits: Int? = null
)

@JsonClass(generateAdapter = true)
data class MerchantPublicInfoDto(
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

@JsonClass(generateAdapter = true)
data class CreatePaymentOrderRequestDto(
    val planId: String,
    val idempotencyKey: String,
    val paymentMethod: String = "AUTO"
)

@JsonClass(generateAdapter = true)
data class CreatePaymentOrderResponseDto(
    val orderId: String,
    val planId: String,
    val planName: String = "",
    val amountPaise: Long,
    val currency: String = "INR",
    val status: String,
    val paymentMethod: String = "YOUPI_PG",
    val youpiPaymentId: String = "",
    val checkoutUrl: String = "",
    val upiPayUri: String = "",
    val merchantInfo: MerchantPublicInfoDto? = null
)

@JsonClass(generateAdapter = true)
data class SubmitUtrRequestDto(
    val utrReference: String
)

@JsonClass(generateAdapter = true)
data class SubmitUtrResponseDto(
    val orderId: String,
    val status: String,
    val utrReference: String
)

@JsonClass(generateAdapter = true)
data class EntitlementResponseDto(
    val planId: String = "free",
    val planName: String = "Free Plan",
    val status: String = "ACTIVE",
    val expiresAtMillis: Long? = null,
    val verifiedByServer: Boolean = true,
    val adFree: Boolean = false,
    val aiMonthlyCredits: Long = 20L,
    val storageLimitMb: Long = 100L,
    val aiCreditsRemaining: Int = 20
)

@JsonClass(generateAdapter = true)
data class AdminGroqStatusDto(
    val isConfigured: Boolean = false,
    val maskedKey: String = "",
    val defaultModel: String = "llama-3.3-70b-versatile",
    val maxTokens: Int = 1024,
    val dailyFreeLimit: Int = 20,
    val dailyPremiumLimit: Int = 500
)

@JsonClass(generateAdapter = true)
data class AdminYoupiStatusDto(
    val isConfigured: Boolean = false,
    val hasDirectUpiOrBank: Boolean = false,
    val missing: List<String> = emptyList(),
    val apiUrl: String = "",
    val merchantId: String = "",
    val returnUrl: String = "https://yaadrakshak.app/payments/return",
    val paymentUpiId: String = "",
    val payeeName: String = "YaadRakshak",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val maskedBankAccount: String = "",
    val ifscCode: String = ""
)

@JsonClass(generateAdapter = true)
data class AdminAiUsageStatsDto(
    val totalRequests: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val totalTokens: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AdminPlanDto(
    val planId: String = "",
    val name: String = "",
    val priceInr: Long = 0L,
    val interval: String = "monthly",
    val aiMonthlyCredits: Long = 0L,
    val storageLimitMb: Long = 100L,
    val adFree: Boolean = false,
    val active: Boolean = true
)

@JsonClass(generateAdapter = true)
data class AdminUserSummaryDto(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "User",
    val role: String = "user",
    val subscriptionTier: String = "free",
    val adFree: Boolean = false,
    val aiCreditsRemaining: Int = 20,
    val storageLimitMb: Long = 100L
)

@JsonClass(generateAdapter = true)
data class AdminPaymentOrderDto(
    val orderId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val planId: String = "",
    val planName: String = "",
    val amountPaise: Long = 0L,
    val currency: String = "INR",
    val status: String = "PENDING",
    val paymentMethod: String = "YOUPI_PG",
    val utrReference: String = "",
    val createdAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AdminAuditLogDto(
    val id: String = "",
    val actorUid: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val status: String = "",
    val detail: String = "",
    val tokensUsed: Int = 0,
    val createdAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AdminOverviewResponseDto(
    val groq: AdminGroqStatusDto = AdminGroqStatusDto(),
    val youpi: AdminYoupiStatusDto = AdminYoupiStatusDto(),
    val aiUsageStats: AdminAiUsageStatsDto = AdminAiUsageStatsDto(),
    val plans: List<AdminPlanDto> = emptyList(),
    val usersCount: Int = 0,
    val users: List<AdminUserSummaryDto> = emptyList(),
    val payments: List<AdminPaymentOrderDto> = emptyList(),
    val auditLogs: List<AdminAuditLogDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UpdateGroqConfigRequestDto(
    val apiKey: String? = null,
    val defaultModel: String,
    val maxTokens: Int,
    val dailyFreeLimit: Int,
    val dailyPremiumLimit: Int
)

@JsonClass(generateAdapter = true)
data class UpdateYoupiConfigRequestDto(
    val apiUrl: String,
    val merchantId: String,
    val merchantApiKey: String? = null,
    val webhookSecret: String? = null,
    val returnUrl: String,
    val paymentUpiId: String = "",
    val payeeName: String = "YaadRakshak",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val ifscCode: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdatePlanRequestDto(
    val name: String,
    val priceInr: Long,
    val interval: String,
    val aiMonthlyCredits: Long,
    val storageLimitMb: Long,
    val adFree: Boolean,
    val active: Boolean
)

@JsonClass(generateAdapter = true)
data class AdminUpdateUserSubscriptionRequestDto(
    val subscriptionTier: String,
    val adFree: Boolean,
    val aiCreditsRemaining: Int,
    val storageLimitMb: Long? = null,
    val durationDays: Int = 30
)

@JsonClass(generateAdapter = true)
data class AdminReconcilePaymentRequestDto(
    val status: String,
    val utrReference: String = "",
    val note: String = ""
)

@JsonClass(generateAdapter = true)
data class GroqTestRequestDto(
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class GroqTestResponseDto(
    val connected: Boolean = false,
    val model: String? = null,
    val reply: String? = null
)

@JsonClass(generateAdapter = true)
data class GroqModelItemDto(
    val id: String
)

@JsonClass(generateAdapter = true)
data class GroqModelsResponseDto(
    val data: List<GroqModelItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GenericSavedDto(
    val saved: Boolean = true
)

interface BackendApiService {
    @POST("api/ai/chat")
    suspend fun chat(@Body body: AiChatRequestDto): Response<AiChatResponseDto>

    @POST("api/ai/extract")
    suspend fun extract(@Body body: AiExtractRequestDto): Response<AiExtractResponseDto>

    @GET("api/payments/merchant-info")
    suspend fun getMerchantInfo(): Response<MerchantPublicInfoDto>

    @POST("api/payments/create-order")
    suspend fun createPaymentOrder(@Body body: CreatePaymentOrderRequestDto): Response<CreatePaymentOrderResponseDto>

    @POST("api/payments/orders/{orderId}/submit-utr")
    suspend fun submitOrderUtr(
        @Path("orderId") orderId: String,
        @Body body: SubmitUtrRequestDto
    ): Response<SubmitUtrResponseDto>

    @GET("api/payments/entitlement")
    suspend fun getEntitlement(): Response<EntitlementResponseDto>

    @GET("api/admin/overview")
    suspend fun getAdminOverview(): Response<AdminOverviewResponseDto>

    @PUT("api/admin/groq-config")
    suspend fun updateGroqConfig(@Body body: UpdateGroqConfigRequestDto): Response<GenericSavedDto>

    @GET("api/admin/groq-models")
    suspend fun listGroqModels(): Response<GroqModelsResponseDto>

    @POST("api/admin/groq-test")
    suspend fun testGroq(@Body body: GroqTestRequestDto): Response<GroqTestResponseDto>

    @PUT("api/admin/youpi-config")
    suspend fun updateYoupiConfig(@Body body: UpdateYoupiConfigRequestDto): Response<GenericSavedDto>

    @PUT("api/admin/plans/{planId}")
    suspend fun updatePlan(
        @Path("planId") planId: String,
        @Body body: UpdatePlanRequestDto
    ): Response<GenericSavedDto>

    @DELETE("api/admin/plans/{planId}")
    suspend fun deletePlan(@Path("planId") planId: String): Response<GenericSavedDto>

    @PUT("api/admin/users/{uid}/subscription")
    suspend fun updateUserSubscription(
        @Path("uid") uid: String,
        @Body body: AdminUpdateUserSubscriptionRequestDto
    ): Response<GenericSavedDto>

    @POST("api/admin/payments/{orderId}/reconcile")
    suspend fun reconcilePayment(
        @Path("orderId") orderId: String,
        @Body body: AdminReconcilePaymentRequestDto
    ): Response<GenericSavedDto>
}
