package com.example.data.repository

import com.example.data.remote.AdminOverviewResponseDto
import com.example.data.remote.AdminReconcilePaymentRequestDto
import com.example.data.remote.AdminUpdateUserSubscriptionRequestDto
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.DynamicBackendClient
import com.example.data.remote.GroqTestRequestDto
import com.example.data.remote.GroqTestResponseDto
import com.example.data.remote.UpdateGroqConfigRequestDto
import com.example.data.remote.UpdatePlanRequestDto
import com.example.data.remote.UpdateYoupiConfigRequestDto
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val backendClient: DynamicBackendClient,
    private val cloudSyncRepository: CloudSyncRepository
) {
    suspend fun checkServerAdminRole(): Boolean = cloudSyncRepository.checkIsAdmin()

    suspend fun fetchOverview(): Result<AdminOverviewResponseDto> = runCatching {
        if (backendClient.isBackendPlaceholderUrl()) {
            throw IllegalStateException("Backend URL not configured. Set your HTTPS Node.js server URL in Profile & Settings first.")
        }
        val res = backendClient.getService().getAdminOverview()
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
        res.body() ?: throw IllegalStateException("Empty admin overview response")
    }

    suspend fun saveGroqConfig(
        apiKey: String?,
        defaultModel: String,
        maxTokens: Int,
        dailyFreeLimit: Int,
        dailyPremiumLimit: Int
    ): Result<Unit> = runCatching {
        val res = backendClient.getService().updateGroqConfig(
            UpdateGroqConfigRequestDto(
                apiKey = apiKey?.takeIf { it.isNotBlank() },
                defaultModel = defaultModel,
                maxTokens = maxTokens,
                dailyFreeLimit = dailyFreeLimit,
                dailyPremiumLimit = dailyPremiumLimit
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    suspend fun fetchGroqModels(): Result<List<String>> = runCatching {
        val res = backendClient.getService().listGroqModels()
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
        res.body()?.data?.map { it.id }.orEmpty()
    }

    suspend fun testGroqConnection(model: String?): Result<GroqTestResponseDto> = runCatching {
        val res = backendClient.getService().testGroq(GroqTestRequestDto(model = model))
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
        res.body() ?: throw IllegalStateException("Empty Groq test response")
    }

    suspend fun saveYoupiConfig(
        apiUrl: String,
        merchantId: String,
        merchantApiKey: String?,
        webhookSecret: String?,
        returnUrl: String,
        paymentUpiId: String = "",
        payeeName: String = "YaadRakshak",
        bankName: String = "",
        bankAccountNumber: String = "",
        ifscCode: String = ""
    ): Result<Unit> = runCatching {
        val res = backendClient.getService().updateYoupiConfig(
            UpdateYoupiConfigRequestDto(
                apiUrl = apiUrl,
                merchantId = merchantId,
                merchantApiKey = merchantApiKey?.takeIf { it.isNotBlank() },
                webhookSecret = webhookSecret?.takeIf { it.isNotBlank() },
                returnUrl = returnUrl,
                paymentUpiId = paymentUpiId,
                payeeName = payeeName,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                ifscCode = ifscCode
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    suspend fun updatePlan(
        planId: String,
        name: String,
        priceInr: Long,
        interval: String,
        aiMonthlyCredits: Long,
        storageLimitMb: Long,
        adFree: Boolean,
        active: Boolean
    ): Result<Unit> = runCatching {
        val res = backendClient.getService().updatePlan(
            planId,
            UpdatePlanRequestDto(
                name = name,
                priceInr = priceInr,
                interval = interval,
                aiMonthlyCredits = aiMonthlyCredits,
                storageLimitMb = storageLimitMb,
                adFree = adFree,
                active = active
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    suspend fun deletePlan(planId: String): Result<Unit> = runCatching {
        val res = backendClient.getService().deletePlan(planId)
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    suspend fun updateUserSubscription(
        uid: String,
        subscriptionTier: String,
        adFree: Boolean,
        aiCreditsRemaining: Int,
        storageLimitMb: Long? = null,
        durationDays: Int = 30
    ): Result<Unit> = runCatching {
        val res = backendClient.getService().updateUserSubscription(
            uid,
            AdminUpdateUserSubscriptionRequestDto(
                subscriptionTier = subscriptionTier,
                adFree = adFree,
                aiCreditsRemaining = aiCreditsRemaining,
                storageLimitMb = storageLimitMb,
                durationDays = durationDays
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    suspend fun reconcilePayment(
        orderId: String,
        status: String,
        utrReference: String = "",
        note: String = ""
    ): Result<Unit> = runCatching {
        val res = backendClient.getService().reconcilePayment(
            orderId,
            AdminReconcilePaymentRequestDto(
                status = status,
                utrReference = utrReference,
                note = note
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(parseErrorMessage(res.errorBody()?.string(), res.code()))
        }
    }

    private fun parseErrorMessage(raw: String?, code: Int): String {
        val msg = raw?.let { runCatching { JSONObject(it).optString("message") }.getOrNull() }.orEmpty()
        return msg.ifBlank { "Admin API request failed (HTTP $code)" }
    }
}
