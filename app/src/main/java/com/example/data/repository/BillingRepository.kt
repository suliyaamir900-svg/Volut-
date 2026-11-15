package com.example.data.repository

import com.example.data.local.AiAndBillingDao
import com.example.data.local.PaymentOrderEntity
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserDao
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.CreatePaymentOrderRequestDto
import com.example.data.remote.CreatePaymentOrderResponseDto
import com.example.data.remote.DynamicBackendClient
import com.example.data.remote.FirestorePaymentOrder
import com.example.data.remote.FirestorePaymentTransaction
import com.example.data.remote.FirestorePlan
import com.example.data.remote.FirestorePublicPaymentInfo
import com.example.data.remote.FirestoreSubscription
import com.example.data.remote.FirestoreUserProfile
import com.example.data.remote.MerchantPublicInfoDto
import com.example.data.remote.SubmitUtrRequestDto
import com.example.data.remote.SubmitUtrResponseDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PaymentOrderOutcome {
    data class CheckoutReady(val order: CreatePaymentOrderResponseDto) : PaymentOrderOutcome
    data class NotConfigured(val missingFields: List<String>, val message: String) : PaymentOrderOutcome
    data class Error(val message: String) : PaymentOrderOutcome
}

@Singleton
class BillingRepository @Inject constructor(
    private val aiAndBillingDao: AiAndBillingDao,
    private val userDao: UserDao,
    private val cloudSyncRepository: CloudSyncRepository,
    private val backendClient: DynamicBackendClient,
    private val auth: FirebaseAuth
) {
    val defaultPlans = listOf(
        FirestorePlan(
            id = "free",
            planId = "free",
            name = "Free Plan",
            priceInr = 0L,
            interval = "free",
            aiMonthlyCredits = 20L,
            storageLimitMb = 100L,
            adFree = false,
            active = true
        ),
        FirestorePlan(
            id = "monthly",
            planId = "monthly",
            name = "Premium Monthly",
            priceInr = 99L,
            interval = "monthly",
            aiMonthlyCredits = 500L,
            storageLimitMb = 5120L,
            adFree = true,
            active = true
        ),
        FirestorePlan(
            id = "yearly",
            planId = "yearly",
            name = "Premium Yearly",
            priceInr = 799L,
            interval = "yearly",
            aiMonthlyCredits = 6000L,
            storageLimitMb = 20480L,
            adFree = true,
            active = true
        )
    )

    fun observeSubscription(userId: String): Flow<SubscriptionEntity?> =
        aiAndBillingDao.observeSubscription(userId)

    fun observePaymentOrders(userId: String): Flow<List<PaymentOrderEntity>> =
        aiAndBillingDao.observePaymentOrders(userId)

    fun observePaymentTransactions(userId: String): Flow<List<PaymentTransactionEntity>> =
        aiAndBillingDao.observePaymentTransactions(userId)

    fun observeCloudPlans(): Flow<List<FirestorePlan>> = cloudSyncRepository.observePlans()

    fun observeCloudProfile(): Flow<FirestoreUserProfile?> =
        if (auth.currentUser == null) flowOf(null) else cloudSyncRepository.observeUserProfile()

    fun observeCloudSubscription(): Flow<FirestoreSubscription?> =
        if (auth.currentUser == null) flowOf(null) else cloudSyncRepository.observeSubscription()

    fun observeCloudOrders(): Flow<List<FirestorePaymentOrder>> =
        if (auth.currentUser == null) flowOf(emptyList()) else cloudSyncRepository.observePaymentOrders()

    fun observeCloudTransactions(): Flow<List<FirestorePaymentTransaction>> =
        if (auth.currentUser == null) flowOf(emptyList()) else cloudSyncRepository.observePaymentTransactions()

    fun observePublicPaymentInfo(): Flow<FirestorePublicPaymentInfo?> =
        if (auth.currentUser == null) flowOf(null) else cloudSyncRepository.observePublicPaymentInfo()

    suspend fun syncLocalSubscriptionFromCloud(
        uid: String,
        profile: FirestoreUserProfile?,
        cloudSub: FirestoreSubscription?,
        plans: List<FirestorePlan>
    ) {
        val activePlanId = when {
            cloudSub != null && cloudSub.status == "ACTIVE" -> cloudSub.planId
            !profile?.subscriptionTier.isNullOrBlank() -> profile!!.subscriptionTier
            else -> "free"
        }
        val matchingPlan = plans.find { it.planId == activePlanId }
            ?: defaultPlans.find { it.planId == activePlanId }
            ?: defaultPlans.first()
        val effectiveAdFree = profile?.adFree ?: cloudSub?.adFree ?: matchingPlan.adFree

        aiAndBillingDao.upsertSubscription(
            SubscriptionEntity(
                id = "current",
                userId = uid,
                planId = activePlanId,
                planName = matchingPlan.name,
                status = cloudSub?.status ?: "ACTIVE",
                expiresAtMillis = cloudSub?.expiresAtMillis ?: 0L,
                verifiedByServer = cloudSub?.verifiedByServer ?: true,
                adFree = effectiveAdFree,
                aiMonthlyCredits = matchingPlan.aiMonthlyCredits,
                storageLimitMb = matchingPlan.storageLimitMb
            )
        )
        val localUser = userDao.getUser(uid)
        if (localUser != null) {
            userDao.upsertUser(
                localUser.copy(
                    subscriptionTier = activePlanId,
                    adFree = effectiveAdFree,
                    aiCreditsRemaining = profile?.aiCreditsRemaining?.toInt() ?: localUser.aiCreditsRemaining,
                    storageLimitBytes = profile?.storageLimitBytes ?: (matchingPlan.storageLimitMb * 1024L * 1024L)
                )
            )
        }
    }

    suspend fun syncCloudOrdersToLocal(
        uid: String,
        cloudOrders: List<FirestorePaymentOrder>,
        cloudTxs: List<FirestorePaymentTransaction>
    ) {
        cloudOrders.forEach { ord ->
            val ordId = ord.orderId.ifBlank { ord.id }
            if (ordId.isNotBlank()) {
                aiAndBillingDao.upsertPaymentOrder(
                    PaymentOrderEntity(
                        id = ordId,
                        userId = uid,
                        planId = ord.planId,
                        amountPaise = ord.amountPaise,
                        currency = ord.currency,
                        youpiPaymentId = ord.youpiPaymentId,
                        checkoutUrl = ord.checkoutUrl,
                        idempotencyKey = ordId,
                        status = ord.status,
                        utrReference = ord.utrReference,
                        paymentMethod = ord.paymentMethod,
                        createdAtMillis = if (ord.createdAtMillis > 0L) ord.createdAtMillis else System.currentTimeMillis()
                    )
                )
            }
        }
        cloudTxs.forEach { tx ->
            if (tx.id.isNotBlank()) {
                aiAndBillingDao.upsertPaymentTransaction(
                    PaymentTransactionEntity(
                        id = tx.id,
                        orderId = tx.orderId,
                        userId = uid,
                        gatewayEvent = tx.gatewayEvent,
                        settlementState = tx.settlementState,
                        utrReference = tx.utrReference,
                        amountPaise = tx.amountPaise,
                        verifiedSignature = tx.verifiedSignature
                    )
                )
            }
        }
    }

    suspend fun fetchMerchantInfo(): Result<MerchantPublicInfoDto> = runCatching {
        if (backendClient.isBackendPlaceholderUrl()) {
            throw IllegalStateException("Backend server URL is not configured yet.")
        }
        val res = backendClient.getService().getMerchantInfo()
        if (!res.isSuccessful) {
            throw IllegalStateException("Unable to load merchant info (HTTP ${res.code()})")
        }
        res.body() ?: MerchantPublicInfoDto()
    }

    suspend fun createYoupiOrder(planId: String, paymentMethod: String = "AUTO"): PaymentOrderOutcome {
        val uid = auth.currentUser?.uid
            ?: return PaymentOrderOutcome.Error("Sign in with Google before upgrading to Premium.")

        if (backendClient.isBackendPlaceholderUrl()) {
            return PaymentOrderOutcome.NotConfigured(
                missingFields = listOf("BACKEND_BASE_URL", "YOUPI_API_URL", "YOUPI_MERCHANT_ID", "YOUPI_MERCHANT_API_KEY", "YOUPI_WEBHOOK_SECRET"),
                message = "Configure your deployed Node.js backend HTTPS URL in Profile & Settings, and enter YouPI PG or UPI/Bank details in Admin Panel."
            )
        }

        return try {
            val idempotencyKey = UUID.randomUUID().toString()
            val response = backendClient.getService().createPaymentOrder(
                CreatePaymentOrderRequestDto(
                    planId = planId,
                    idempotencyKey = idempotencyKey,
                    paymentMethod = paymentMethod
                )
            )
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string().orEmpty()
                val json = runCatching { JSONObject(errBody) }.getOrNull()
                val code = json?.optString("error").orEmpty()
                val msg = json?.optString("message").orEmpty()
                if (code == "YOUPI_NOT_CONFIGURED") {
                    val missingArr = json?.optJSONArray("missingFields")
                    val missing = buildList {
                        if (missingArr != null) {
                            for (i in 0 until missingArr.length()) add(missingArr.optString(i))
                        }
                    }
                    return PaymentOrderOutcome.NotConfigured(
                        missing,
                        msg.ifBlank { "YouPI PG or Merchant UPI credentials are missing on the backend." }
                    )
                }
                return PaymentOrderOutcome.Error(
                    msg.ifBlank { "Payment order failed (HTTP ${response.code()})" }
                )
            }
            val order = response.body()
                ?: return PaymentOrderOutcome.Error("Empty payment order response.")

            aiAndBillingDao.upsertPaymentOrder(
                PaymentOrderEntity(
                    id = order.orderId,
                    userId = uid,
                    planId = order.planId,
                    amountPaise = order.amountPaise,
                    currency = order.currency,
                    youpiPaymentId = order.youpiPaymentId,
                    checkoutUrl = order.checkoutUrl,
                    idempotencyKey = idempotencyKey,
                    status = order.status,
                    paymentMethod = order.paymentMethod
                )
            )
            PaymentOrderOutcome.CheckoutReady(order)
        } catch (e: Exception) {
            PaymentOrderOutcome.Error("Unable to reach billing server: ${e.localizedMessage ?: "Network error"}")
        }
    }

    suspend fun submitOrderUtr(orderId: String, utrReference: String): Result<SubmitUtrResponseDto> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required.")
        if (backendClient.isBackendPlaceholderUrl()) {
            throw IllegalStateException("Backend server URL is not configured.")
        }
        val res = backendClient.getService().submitOrderUtr(orderId, SubmitUtrRequestDto(utrReference))
        if (!res.isSuccessful) {
            val err = res.errorBody()?.string().orEmpty()
            val msg = runCatching { JSONObject(err).optString("message") }.getOrDefault("")
            throw IllegalStateException(msg.ifBlank { "Failed to submit UTR (HTTP ${res.code()})" })
        }
        val body = res.body() ?: throw IllegalStateException("Empty UTR response")
        val existing = aiAndBillingDao.getPaymentOrderById(orderId)
        if (existing != null) {
            aiAndBillingDao.updatePaymentOrderUtrAndStatus(orderId, body.status, body.utrReference)
        } else {
            aiAndBillingDao.upsertPaymentOrder(
                PaymentOrderEntity(
                    id = orderId,
                    userId = uid,
                    planId = "",
                    amountPaise = 0L,
                    idempotencyKey = orderId,
                    status = body.status,
                    utrReference = body.utrReference
                )
            )
        }
        body
    }

    suspend fun refreshServerEntitlement(): Result<SubscriptionEntity> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required to verify subscription.")
        if (backendClient.isBackendPlaceholderUrl()) {
            throw IllegalStateException("Backend server URL is not configured yet.")
        }
        val response = backendClient.getService().getEntitlement()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to verify entitlement (HTTP ${response.code()})")
        }
        val body = response.body() ?: throw IllegalStateException("Empty entitlement payload")
        val sub = SubscriptionEntity(
            id = "current",
            userId = uid,
            planId = body.planId,
            planName = body.planName,
            status = body.status,
            expiresAtMillis = body.expiresAtMillis ?: 0L,
            verifiedByServer = body.verifiedByServer,
            adFree = body.adFree,
            aiMonthlyCredits = body.aiMonthlyCredits,
            storageLimitMb = body.storageLimitMb
        )
        aiAndBillingDao.upsertSubscription(sub)
        val localUser = userDao.getUser(uid)
        if (localUser != null) {
            userDao.upsertUser(
                localUser.copy(
                    subscriptionTier = body.planId,
                    adFree = body.adFree,
                    aiCreditsRemaining = body.aiCreditsRemaining,
                    storageLimitBytes = body.storageLimitMb * 1024L * 1024L
                )
            )
        }
        sub
    }
}
