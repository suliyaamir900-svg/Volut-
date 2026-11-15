package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiConversationEntity
import com.example.data.local.AiMessageEntity
import com.example.data.local.AiSourceEntity
import com.example.data.local.BackupJobEntity
import com.example.data.local.PaymentOrderEntity
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserDao
import com.example.data.remote.AdminOverviewResponseDto
import com.example.data.remote.CreatePaymentOrderResponseDto
import com.example.data.remote.FirestorePlan
import com.example.data.remote.MerchantPublicInfoDto
import com.example.data.repository.AdminRepository
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiQueryResult
import com.example.data.repository.BackupAndExportRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.PaymentOrderOutcome
import com.example.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntitlementUiState(
    val planId: String = "free",
    val planName: String = "Free Plan",
    val status: String = "ACTIVE",
    val adFree: Boolean = false,
    val aiCreditsRemaining: Int = 20,
    val aiMonthlyCredits: Long = 20L,
    val storageLimitMb: Long = 100L,
    val expiresAtMillis: Long = 0L,
    val verifiedByServer: Boolean = false
) {
    val isPremium: Boolean
        get() = planId != "free" && status == "ACTIVE"
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiAndBillingViewModel @Inject constructor(
    private val aiRepository: AiAssistantRepository,
    private val billingRepository: BillingRepository,
    private val backupRepository: BackupAndExportRepository,
    private val adminRepository: AdminRepository,
    private val userDao: UserDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<String?>(null)
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiNotice = MutableStateFlow<String?>(null)
    val aiNotice: StateFlow<String?> = _aiNotice.asStateFlow()

    private val _suggestions = MutableStateFlow(
        listOf(
            "Which product warranties are expiring soon?",
            "Summarize my latest receipts and spending",
            "What important documents have I saved?"
        )
    )
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _billingMessage = MutableStateFlow<String?>(null)
    val billingMessage: StateFlow<String?> = _billingMessage.asStateFlow()

    private val _checkoutUrlToOpen = MutableStateFlow<String?>(null)
    val checkoutUrlToOpen: StateFlow<String?> = _checkoutUrlToOpen.asStateFlow()

    private val _upiPayUriToOpen = MutableStateFlow<String?>(null)
    val upiPayUriToOpen: StateFlow<String?> = _upiPayUriToOpen.asStateFlow()

    private val _latestCreatedOrder = MutableStateFlow<CreatePaymentOrderResponseDto?>(null)
    val latestCreatedOrder: StateFlow<CreatePaymentOrderResponseDto?> = _latestCreatedOrder.asStateFlow()

    private val _backendMerchantInfo = MutableStateFlow<MerchantPublicInfoDto?>(null)

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _adminOverview = MutableStateFlow<AdminOverviewResponseDto?>(null)
    val adminOverview: StateFlow<AdminOverviewResponseDto?> = _adminOverview.asStateFlow()

    private val _adminModels = MutableStateFlow<List<String>>(emptyList())
    val adminModels: StateFlow<List<String>> = _adminModels.asStateFlow()

    private val _adminMessage = MutableStateFlow<String?>(null)
    val adminMessage: StateFlow<String?> = _adminMessage.asStateFlow()

    val conversations: StateFlow<List<AiConversationEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else aiRepository.observeConversations(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val messages: StateFlow<List<AiMessageEntity>> = _activeConversationId.flatMapLatest { cid ->
        if (cid == null) flowOf(emptyList()) else aiRepository.observeMessages(cid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sources: StateFlow<List<AiSourceEntity>> = _activeConversationId.flatMapLatest { cid ->
        if (cid == null) flowOf(emptyList()) else aiRepository.observeSources(cid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val subscription: StateFlow<SubscriptionEntity?> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else billingRepository.observeSubscription(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val paymentOrders: StateFlow<List<PaymentOrderEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else billingRepository.observePaymentOrders(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val paymentTransactions: StateFlow<List<PaymentTransactionEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else billingRepository.observePaymentTransactions(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val backupJobs: StateFlow<List<BackupJobEntity>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else backupRepository.observeBackupJobs(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val availablePlans: StateFlow<List<FirestorePlan>> = _activeUserId.flatMapLatest { uid ->
        if (uid == null || uid == VaultRepository.GUEST_UID) {
            flowOf(billingRepository.defaultPlans)
        } else {
            billingRepository.observeCloudPlans()
                .catch { emit(billingRepository.defaultPlans) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), billingRepository.defaultPlans)

    val merchantInfo: StateFlow<MerchantPublicInfoDto?> = _activeUserId.flatMapLatest { uid ->
        if (uid == null || uid == VaultRepository.GUEST_UID) {
            flowOf(null)
        } else {
            combine(
                billingRepository.observePublicPaymentInfo().catch { emit(null) },
                _backendMerchantInfo
            ) { cloudInfo, backendInfo ->
                when {
                    backendInfo != null && (backendInfo.upiReady || backendInfo.bankReady || backendInfo.youpiReady) -> backendInfo
                    cloudInfo != null -> MerchantPublicInfoDto(
                        paymentUpiId = cloudInfo.paymentUpiId,
                        payeeName = cloudInfo.payeeName,
                        bankName = cloudInfo.bankName,
                        bankAccountDisplay = cloudInfo.bankAccountDisplay,
                        maskedBankAccount = cloudInfo.maskedBankAccount,
                        ifscCode = cloudInfo.ifscCode,
                        merchantId = cloudInfo.merchantId,
                        youpiReady = cloudInfo.youpiReady,
                        upiReady = cloudInfo.upiReady,
                        bankReady = cloudInfo.bankReady
                    )
                    else -> backendInfo
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val entitlement: StateFlow<EntitlementUiState> = combine(
        _activeUserId.flatMapLatest { uid -> if (uid == null) flowOf(null) else userDao.observeUser(uid) },
        subscription,
        availablePlans
    ) { user, sub, plans ->
        val planId = sub?.planId ?: user?.subscriptionTier ?: "free"
        val matchingPlan = plans.find { it.planId == planId }
            ?: billingRepository.defaultPlans.find { it.planId == planId }
            ?: billingRepository.defaultPlans.first()
        val effectiveStatus = sub?.status ?: "ACTIVE"
        val effectiveAdFree = (sub?.adFree == true) ||
            (user?.adFree == true) ||
            (matchingPlan.adFree && effectiveStatus == "ACTIVE" && planId != "free")
        val creditsCap = sub?.aiMonthlyCredits?.takeIf { it > 0L } ?: matchingPlan.aiMonthlyCredits.coerceAtLeast(1L)
        val storageCap = sub?.storageLimitMb?.takeIf { it > 0L }
            ?: ((user?.storageLimitBytes ?: 0L) / (1024L * 1024L)).takeIf { it > 0L }
            ?: matchingPlan.storageLimitMb

        EntitlementUiState(
            planId = planId,
            planName = sub?.planName?.ifBlank { matchingPlan.name } ?: matchingPlan.name,
            status = effectiveStatus,
            adFree = effectiveAdFree,
            aiCreditsRemaining = user?.aiCreditsRemaining ?: creditsCap.toInt(),
            aiMonthlyCredits = creditsCap,
            storageLimitMb = storageCap,
            expiresAtMillis = sub?.expiresAtMillis ?: 0L,
            verifiedByServer = sub?.verifiedByServer ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), EntitlementUiState())

    init {
        viewModelScope.launch {
            _activeUserId.collectLatest { uid ->
                if (uid != null && uid != VaultRepository.GUEST_UID) {
                    launch {
                        combine(
                            billingRepository.observeCloudProfile().catch { emit(null) },
                            billingRepository.observeCloudSubscription().catch { emit(null) },
                            availablePlans
                        ) { profile, cloudSub, plans ->
                            Triple(profile, cloudSub, plans)
                        }.collectLatest { (profile, cloudSub, plans) ->
                            if (profile != null || cloudSub != null) {
                                billingRepository.syncLocalSubscriptionFromCloud(uid, profile, cloudSub, plans)
                            }
                        }
                    }
                    launch {
                        combine(
                            billingRepository.observeCloudOrders().catch { emit(emptyList()) },
                            billingRepository.observeCloudTransactions().catch { emit(emptyList()) }
                        ) { orders, txs -> orders to txs }
                            .collectLatest { (orders, txs) ->
                                if (orders.isNotEmpty() || txs.isNotEmpty()) {
                                    billingRepository.syncCloudOrdersToLocal(uid, orders, txs)
                                }
                            }
                    }
                }
            }
        }
    }

    fun bindUser(uid: String) {
        if (_activeUserId.value != uid) {
            _activeUserId.value = uid
            _activeConversationId.value = null
            _latestCreatedOrder.value = null
            if (uid != VaultRepository.GUEST_UID) {
                loadMerchantInfo()
            }
        }
    }

    fun selectConversation(id: String?) {
        _activeConversationId.value = id
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            aiRepository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
        }
    }

    fun askAi(question: String, isGuest: Boolean, isAdmin: Boolean = false) {
        val uid = _activeUserId.value ?: return
        if (question.isBlank()) return
        val currentEnt = entitlement.value
        if (!isGuest && !isAdmin && currentEnt.aiCreditsRemaining <= 0) {
            _aiNotice.value = "You have 0 AI credits remaining on ${currentEnt.planName}. Upgrade your plan or ask Admin to top up credits."
            return
        }

        _aiLoading.value = true
        _aiNotice.value = null

        viewModelScope.launch {
            val convId = _activeConversationId.value ?: aiRepository.createConversation(
                userId = uid,
                title = question.take(48)
            ).also { _activeConversationId.value = it }

            when (val res = aiRepository.askMemoryAssistant(uid, convId, question, isGuest, messages.value)) {
                is AiQueryResult.Success -> {
                    if (res.relatedSuggestions.isNotEmpty()) {
                        _suggestions.value = res.relatedSuggestions
                    }
                }
                is AiQueryResult.NotConfigured -> _aiNotice.value = res.reason
                is AiQueryResult.Error -> _aiNotice.value = res.message
            }
            _aiLoading.value = false
        }
    }

    fun startNewConversation() {
        _activeConversationId.value = null
        _aiNotice.value = null
    }

    fun loadMerchantInfo() {
        viewModelScope.launch {
            billingRepository.fetchMerchantInfo()
                .onSuccess { _backendMerchantInfo.value = it }
        }
    }

    fun initiateYoupiPayment(planId: String, paymentMethod: String = "AUTO") {
        _billingMessage.value = null
        viewModelScope.launch {
            when (val out = billingRepository.createYoupiOrder(planId, paymentMethod)) {
                is PaymentOrderOutcome.CheckoutReady -> {
                    _latestCreatedOrder.value = out.order
                    when {
                        paymentMethod == "DIRECT_UPI" && out.order.upiPayUri.isNotBlank() -> {
                            _upiPayUriToOpen.value = out.order.upiPayUri
                            _billingMessage.value = "Order ${out.order.orderId} ready for UPI/Bank payment. After paying, submit your 12-digit UTR below for Admin verification."
                        }
                        out.order.checkoutUrl.isNotBlank() -> {
                            _checkoutUrlToOpen.value = out.order.checkoutUrl
                            _billingMessage.value = "Order ${out.order.orderId} created via YouPI PG. Premium activates after server-side webhook or Admin verification."
                        }
                        out.order.upiPayUri.isNotBlank() -> {
                            _billingMessage.value = "Order ${out.order.orderId} created! Tap 'Pay via UPI App' or pay to the Admin bank/UPI details and submit your UTR."
                        }
                        else -> {
                            _billingMessage.value = "Order ${out.order.orderId} created (status: ${out.order.status})."
                        }
                    }
                }
                is PaymentOrderOutcome.NotConfigured -> {
                    _billingMessage.value = "${out.message} Missing: ${out.missingFields.joinToString(", ")}"
                }
                is PaymentOrderOutcome.Error -> _billingMessage.value = out.message
            }
        }
    }

    fun openUpiUri(uri: String) {
        if (uri.isNotBlank()) {
            _upiPayUriToOpen.value = uri
        }
    }

    fun consumeUpiUri() {
        _upiPayUriToOpen.value = null
    }

    fun consumeCheckoutUrl() {
        _checkoutUrlToOpen.value = null
    }

    fun submitOrderUtr(orderId: String, utrReference: String) {
        val cleaned = utrReference.trim()
        if (cleaned.length < 6) {
            _billingMessage.value = "Enter a valid UPI UTR / Bank reference number (at least 6 characters)."
            return
        }
        viewModelScope.launch {
            billingRepository.submitOrderUtr(orderId, cleaned)
                .onSuccess { res ->
                    _billingMessage.value = "UTR ${res.utrReference} submitted for order ${res.orderId}. Pending server/Admin verification."
                    _latestCreatedOrder.value = null
                }
                .onFailure { err ->
                    _billingMessage.value = err.localizedMessage ?: "Failed to submit UTR."
                }
        }
    }

    fun refreshSubscriptionEntitlement() {
        viewModelScope.launch {
            billingRepository.refreshServerEntitlement()
                .onSuccess { sub ->
                    _billingMessage.value = "Verified entitlement: ${sub.planName} (${sub.status}) • Ads Blocked: ${sub.adFree}"
                }
                .onFailure { err ->
                    _billingMessage.value = err.localizedMessage ?: "Unable to verify entitlement."
                }
        }
    }

    fun createBackup() {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            backupRepository.createEncryptedBackup(uid)
                .onSuccess { job ->
                    _backupMessage.value = "Encrypted backup created (${job.itemCount} records). SHA-256: ${job.checksumSha256.take(12)}..."
                }
                .onFailure { err ->
                    _backupMessage.value = "Backup failed: ${err.localizedMessage}"
                }
        }
    }

    fun restoreLatestBackup(filePath: String) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            backupRepository.restoreFromEncryptedBackup(uid, filePath)
                .onSuccess { count ->
                    _backupMessage.value = "Restored $count encrypted records into vault."
                }
                .onFailure { err ->
                    _backupMessage.value = "Restore failed: ${err.localizedMessage}"
                }
        }
    }

    fun deleteAccountAndData(isGuest: Boolean, onDone: () -> Unit) {
        val uid = _activeUserId.value ?: return
        viewModelScope.launch {
            backupRepository.deleteAllUserData(uid, isGuest)
                .onSuccess { onDone() }
                .onFailure { err -> _backupMessage.value = "Deletion failed: ${err.localizedMessage}" }
        }
    }

    fun loadAdminOverview() {
        viewModelScope.launch {
            adminRepository.fetchOverview()
                .onSuccess { _adminOverview.value = it }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun saveGroqConfig(apiKey: String?, defaultModel: String, maxTokens: Int, freeLimit: Int, premiumLimit: Int) {
        viewModelScope.launch {
            adminRepository.saveGroqConfig(apiKey, defaultModel, maxTokens, freeLimit, premiumLimit)
                .onSuccess {
                    _adminMessage.value = "Shared Groq API & limits saved. All users now use this backend AI config."
                    loadAdminOverview()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun fetchGroqModels() {
        viewModelScope.launch {
            adminRepository.fetchGroqModels()
                .onSuccess {
                    _adminModels.value = it
                    _adminMessage.value = "Loaded ${it.size} Groq models from /openai/v1/models."
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun testGroqConnection(model: String?) {
        viewModelScope.launch {
            adminRepository.testGroqConnection(model)
                .onSuccess { res ->
                    _adminMessage.value = "Groq connected (${res.model}): ${res.reply}"
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun saveYoupiConfig(
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
    ) {
        viewModelScope.launch {
            adminRepository.saveYoupiConfig(
                apiUrl = apiUrl,
                merchantId = merchantId,
                merchantApiKey = merchantApiKey,
                webhookSecret = webhookSecret,
                returnUrl = returnUrl,
                paymentUpiId = paymentUpiId,
                payeeName = payeeName,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                ifscCode = ifscCode
            )
                .onSuccess {
                    _adminMessage.value = "Payment UPI, Bank & YouPI PG settings saved securely on backend."
                    loadAdminOverview()
                    loadMerchantInfo()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun updatePlanPricing(
        planId: String,
        name: String,
        priceInr: Long,
        credits: Long,
        storageMb: Long,
        interval: String = planId,
        adFree: Boolean = planId != "free",
        active: Boolean = true
    ) {
        viewModelScope.launch {
            adminRepository.updatePlan(planId, name, priceInr, interval, credits, storageMb, adFree, active)
                .onSuccess {
                    _adminMessage.value = "Saved plan '$name' (₹$priceInr, Ads Blocked: $adFree)."
                    loadAdminOverview()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            adminRepository.deletePlan(planId)
                .onSuccess {
                    _adminMessage.value = "Deleted plan '$planId'."
                    loadAdminOverview()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun updateUserSubscription(
        uid: String,
        subscriptionTier: String,
        adFree: Boolean,
        aiCreditsRemaining: Int,
        storageLimitMb: Long? = null,
        durationDays: Int = 30
    ) {
        viewModelScope.launch {
            adminRepository.updateUserSubscription(
                uid = uid,
                subscriptionTier = subscriptionTier,
                adFree = adFree,
                aiCreditsRemaining = aiCreditsRemaining,
                storageLimitMb = storageLimitMb,
                durationDays = durationDays
            )
                .onSuccess {
                    _adminMessage.value = "Updated user entitlement ($subscriptionTier, Ads Blocked: $adFree, Credits: $aiCreditsRemaining)."
                    loadAdminOverview()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun reconcilePayment(orderId: String, status: String, utrReference: String = "", note: String = "") {
        viewModelScope.launch {
            adminRepository.reconcilePayment(orderId, status, utrReference, note)
                .onSuccess {
                    _adminMessage.value = "Order $orderId reconciled as $status."
                    loadAdminOverview()
                }
                .onFailure { _adminMessage.value = it.localizedMessage }
        }
    }

    fun clearAdminMessage() {
        _adminMessage.value = null
    }
}
