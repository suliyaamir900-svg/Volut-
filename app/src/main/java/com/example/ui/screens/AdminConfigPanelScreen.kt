package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.AdminOverviewResponseDto
import com.example.data.remote.AdminPaymentOrderDto
import com.example.data.remote.AdminPlanDto
import com.example.data.remote.AdminUserSummaryDto
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SkyInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminSection(val label: String) {
    AI_AND_LOGS("AI & Logs"),
    PLANS("Plans"),
    USERS_AND_ADS("Users & Ads"),
    PAYMENTS_AND_BANK("Payments & Bank")
}

@Composable
fun AdminConfigPanelScreen(
    overview: AdminOverviewResponseDto?,
    models: List<String>,
    adminMessage: String?,
    onRefreshOverview: () -> Unit,
    onSaveGroq: (apiKey: String?, model: String, maxTokens: Int, freeLimit: Int, premiumLimit: Int) -> Unit,
    onFetchGroqModels: () -> Unit,
    onTestGroq: (String?) -> Unit,
    onSaveYoupi: (
        apiUrl: String,
        merchantId: String,
        apiKey: String?,
        webhookSecret: String?,
        returnUrl: String,
        paymentUpiId: String,
        payeeName: String,
        bankName: String,
        bankAccountNumber: String,
        ifscCode: String
    ) -> Unit,
    onUpdatePlan: (
        planId: String,
        name: String,
        priceInr: Long,
        credits: Long,
        storageMb: Long,
        interval: String,
        adFree: Boolean,
        active: Boolean
    ) -> Unit,
    onDeletePlan: (String) -> Unit = {},
    onUpdateUserSubscription: (
        uid: String,
        subscriptionTier: String,
        adFree: Boolean,
        aiCreditsRemaining: Int,
        storageLimitMb: Long?,
        durationDays: Int
    ) -> Unit = { _, _, _, _, _, _ -> },
    onReconcilePayment: (orderId: String, status: String, utrReference: String, note: String) -> Unit = { _, _, _, _ -> }
) {
    LaunchedEffect(Unit) {
        onRefreshOverview()
    }

    var activeSection by remember { mutableStateOf(AdminSection.AI_AND_LOGS) }
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    // Groq State
    var groqKey by remember { mutableStateOf("") }
    var defaultModel by remember(overview) { mutableStateOf(overview?.groq?.defaultModel ?: "llama-3.3-70b-versatile") }
    var maxTokens by remember(overview) { mutableStateOf((overview?.groq?.maxTokens ?: 1024).toString()) }
    var freeLimit by remember(overview) { mutableStateOf((overview?.groq?.dailyFreeLimit ?: 20).toString()) }
    var premLimit by remember(overview) { mutableStateOf((overview?.groq?.dailyPremiumLimit ?: 500).toString()) }

    // Payment & Bank State
    var paymentUpiId by remember(overview) { mutableStateOf(overview?.youpi?.paymentUpiId.orEmpty()) }
    var payeeName by remember(overview) { mutableStateOf(overview?.youpi?.payeeName?.ifBlank { "YaadRakshak" } ?: "YaadRakshak") }
    var bankName by remember(overview) { mutableStateOf(overview?.youpi?.bankName.orEmpty()) }
    var bankAccountNumber by remember(overview) { mutableStateOf(overview?.youpi?.bankAccountNumber.orEmpty()) }
    var ifscCode by remember(overview) { mutableStateOf(overview?.youpi?.ifscCode.orEmpty()) }
    var youpiUrl by remember(overview) { mutableStateOf(overview?.youpi?.apiUrl.orEmpty()) }
    var youpiMerchantId by remember(overview) { mutableStateOf(overview?.youpi?.merchantId.orEmpty()) }
    var youpiApiKey by remember { mutableStateOf("") }
    var youpiWebhookSecret by remember { mutableStateOf("") }
    var youpiReturnUrl by remember(overview) { mutableStateOf(overview?.youpi?.returnUrl?.ifBlank { "yaadrakshak://payment/callback" } ?: "yaadrakshak://payment/callback") }

    // Dialog states
    var editingPlan by remember { mutableStateOf<AdminPlanDto?>(null) }
    var creatingNewPlan by remember { mutableStateOf(false) }
    var managingUser by remember { mutableStateOf<AdminUserSummaryDto?>(null) }
    var reconcilingOrder by remember { mutableStateOf<AdminPaymentOrderDto?>(null) }
    var userSearch by remember { mutableStateOf("") }
    var paymentStatusFilter by remember { mutableStateOf("ALL") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header KPI & Section Tabs
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = SaffronPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Admin Control Center", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            "Users: ${overview?.usersCount ?: 0} • Plans: ${overview?.plans?.size ?: 0} • AI Calls: ${overview?.aiUsageStats?.totalRequests ?: 0} • Orders: ${overview?.payments?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onRefreshOverview) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                if (adminMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(adminMessage, style = MaterialTheme.typography.bodyMedium, color = SaffronPrimary)
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AdminSection.entries) { section ->
                        FilterChip(
                            selected = activeSection == section,
                            onClick = { activeSection = section },
                            label = { Text(section.label) },
                            modifier = Modifier.testTag("admin_tab_${section.name.lowercase()}")
                        )
                    }
                }
            }
        }

        when (activeSection) {
            AdminSection.AI_AND_LOGS -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SaffronPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Shared Groq AI API Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            StatusPill(
                                label = if (overview?.groq?.isConfigured == true) "ACTIVE" else "MISSING KEY",
                                color = if (overview?.groq?.isConfigured == true) EmeraldSuccess else AmberWarning
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "All authenticated users run AI Assistant, Document Summaries, and Receipt/Warranty extraction using this single backend key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!overview?.groq?.maskedKey.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Active Key: ${overview?.groq?.maskedKey}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSuccess
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = groqKey,
                            onValueChange = { groqKey = it },
                            label = { Text("Enter / Rotate Groq API Key (gsk_...)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_groq_key_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = defaultModel,
                            onValueChange = { defaultModel = it },
                            label = { Text("Default Groq Model") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (models.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(models) { m ->
                                    FilterChip(selected = defaultModel == m, onClick = { defaultModel = m }, label = { Text(m) })
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxTokens,
                                onValueChange = { maxTokens = it.filter(Char::isDigit) },
                                label = { Text("Max Tokens") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = freeLimit,
                                onValueChange = { freeLimit = it.filter(Char::isDigit) },
                                label = { Text("Free Limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = premLimit,
                                onValueChange = { premLimit = it.filter(Char::isDigit) },
                                label = { Text("Pro Limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        GradientPrimaryButton(
                            text = "Save Shared Groq Config",
                            onClick = {
                                onSaveGroq(
                                    groqKey.takeIf { it.isNotBlank() },
                                    defaultModel,
                                    maxTokens.toIntOrNull() ?: 1024,
                                    freeLimit.toIntOrNull() ?: 20,
                                    premLimit.toIntOrNull() ?: 500
                                )
                                groqKey = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onFetchGroqModels, modifier = Modifier.weight(1f)) {
                                Text("Load /models")
                            }
                            OutlinedButton(onClick = { onTestGroq(defaultModel) }, modifier = Modifier.weight(1f)) {
                                Text("Test Groq")
                            }
                        }
                    }
                }

                item {
                    val stats = overview?.aiUsageStats
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("AI Usage Statistics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBlock("Requests", "${stats?.totalRequests ?: 0}", SkyInfo)
                            StatBlock("Success", "${stats?.successCount ?: 0}", EmeraldSuccess)
                            StatBlock("Errors", "${stats?.errorCount ?: 0}", RoseAlert)
                            StatBlock("Tokens", "${stats?.totalTokens ?: 0}", SaffronPrimary)
                        }
                    }
                }

                item {
                    Text("Request & Audit Logs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                }

                val logs = overview?.auditLogs.orEmpty()
                if (logs.isEmpty()) {
                    item {
                        EmptyStateGlassCard(
                            icon = Icons.Default.AutoAwesome,
                            title = "No Audit Logs Yet",
                            subtitle = "AI queries, admin updates, and payment reconciliations will appear here."
                        )
                    }
                } else {
                    items(logs, key = { it.id }) { log ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.action, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                StatusPill(
                                    label = log.status,
                                    color = if (log.status == "SUCCESS") EmeraldSuccess else if (log.status == "ERROR") RoseAlert else SkyInfo
                                )
                            }
                            if (log.detail.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(log.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${if (log.tokensUsed > 0) "${log.tokensUsed} tokens • " else ""}${dateFmt.format(Date(log.createdAtMillis))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AdminSection.PLANS -> {
                item {
                    GradientPrimaryButton(
                        text = "Create New Subscription Plan",
                        icon = Icons.Default.Add,
                        onClick = { creatingNewPlan = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "admin_create_plan_button"
                    )
                }

                val planList = overview?.plans.orEmpty()
                if (planList.isEmpty()) {
                    item {
                        EmptyStateGlassCard(
                            icon = Icons.Default.WorkspacePremium,
                            title = "Load Plans",
                            subtitle = "Tap Refresh above to load subscription plans from the backend."
                        )
                    }
                } else {
                    items(planList, key = { it.planId }) { plan ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${plan.name} (${plan.planId})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("₹${plan.priceInr} / ${plan.interval}", style = MaterialTheme.typography.titleSmall, color = SaffronPrimary, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusPill(
                                        label = if (plan.adFree) "ADS BLOCKED" else "ADS ON",
                                        color = if (plan.adFree) EmeraldSuccess else AmberWarning
                                    )
                                    IconButton(onClick = { editingPlan = plan }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Plan", tint = SaffronPrimary)
                                    }
                                    if (plan.planId != "free") {
                                        IconButton(onClick = { onDeletePlan(plan.planId) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Plan", tint = RoseAlert)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${plan.aiMonthlyCredits} AI Credits • ${plan.storageLimitMb} MB Quota • ${if (plan.active) "Visible to Users" else "Hidden"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AdminSection.USERS_AND_ADS -> {
                item {
                    OutlinedTextField(
                        value = userSearch,
                        onValueChange = { userSearch = it },
                        placeholder = { Text("Search users by email or name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val filteredUsers = overview?.users.orEmpty().filter {
                    userSearch.isBlank() ||
                        it.email.contains(userSearch, ignoreCase = true) ||
                        it.displayName.contains(userSearch, ignoreCase = true)
                }

                if (filteredUsers.isEmpty()) {
                    item {
                        EmptyStateGlassCard(
                            icon = Icons.Default.ManageAccounts,
                            title = "No Users Loaded",
                            subtitle = "Signed-in users appear here once registered with Firebase Auth."
                        )
                    }
                } else {
                    items(filteredUsers, key = { it.userId }) { usr ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(usr.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(usr.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { managingUser = usr }) {
                                    Text("Manage")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(label = usr.subscriptionTier.uppercase(), color = SaffronPrimary)
                                StatusPill(
                                    label = if (usr.adFree) "ADS BLOCKED" else "ADS SHOWN",
                                    color = if (usr.adFree) EmeraldSuccess else AmberWarning
                                )
                                StatusPill(label = "${usr.aiCreditsRemaining} AI Credits", color = SkyInfo)
                            }
                        }
                    }
                }
            }

            AdminSection.PAYMENTS_AND_BANK -> {
                // Admin UPI, Bank & YouPI PG Form
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = SaffronPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Admin Bank, UPI & YouPI PG", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            StatusPill(
                                label = when {
                                    overview?.youpi?.isConfigured == true -> "YOUPI READY"
                                    overview?.youpi?.hasDirectUpiOrBank == true -> "UPI / BANK READY"
                                    else -> "SETUP NEEDED"
                                },
                                color = if (overview?.youpi?.isConfigured == true || overview?.youpi?.hasDirectUpiOrBank == true) EmeraldSuccess else AmberWarning
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Direct UPI & Bank Transfer Details (shown to subscribers)", style = MaterialTheme.typography.labelLarge, color = SaffronPrimary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = paymentUpiId,
                            onValueChange = { paymentUpiId = it },
                            label = { Text("Admin Payment UPI ID (e.g. yourname@okaxis)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_upi_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = payeeName,
                            onValueChange = { payeeName = it },
                            label = { Text("Payee / Business Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text("Bank Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bankAccountNumber,
                                onValueChange = { bankAccountNumber = it },
                                label = { Text("Bank Account No.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ifscCode,
                                onValueChange = { ifscCode = it.uppercase() },
                                label = { Text("IFSC Code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("YouPI PG Gateway Credentials (Optional)", style = MaterialTheme.typography.labelLarge, color = SkyInfo)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = youpiUrl, onValueChange = { youpiUrl = it }, label = { Text("YouPI API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = youpiMerchantId, onValueChange = { youpiMerchantId = it }, label = { Text("Merchant ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = youpiApiKey, onValueChange = { youpiApiKey = it }, label = { Text("Merchant API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = youpiWebhookSecret, onValueChange = { youpiWebhookSecret = it }, label = { Text("Webhook Signing Secret") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = youpiReturnUrl, onValueChange = { youpiReturnUrl = it }, label = { Text("Return URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        GradientPrimaryButton(
                            text = "Save Payment & Bank Settings",
                            icon = Icons.Default.Settings,
                            onClick = {
                                onSaveYoupi(
                                    youpiUrl,
                                    youpiMerchantId,
                                    youpiApiKey.takeIf { it.isNotBlank() },
                                    youpiWebhookSecret.takeIf { it.isNotBlank() },
                                    youpiReturnUrl,
                                    paymentUpiId,
                                    payeeName,
                                    bankName,
                                    bankAccountNumber,
                                    ifscCode
                                )
                                youpiApiKey = ""
                                youpiWebhookSecret = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Incoming YouPI / UPI Payment Orders
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Incoming Subscription Payments", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "PENDING", "COMPLETED").forEach { f ->
                            FilterChip(
                                selected = paymentStatusFilter == f,
                                onClick = { paymentStatusFilter = f },
                                label = { Text(f) }
                            )
                        }
                    }
                }

                val filteredOrders = overview?.payments.orEmpty().filter { ord ->
                    when (paymentStatusFilter) {
                        "PENDING" -> ord.status in listOf("CREATED", "PENDING", "PENDING_PAYMENT", "PENDING_RECONCILIATION")
                        "COMPLETED" -> ord.status in listOf("COMPLETED", "SUCCESS", "RECONCILED")
                        else -> true
                    }
                }

                if (filteredOrders.isEmpty()) {
                    item {
                        EmptyStateGlassCard(
                            icon = Icons.Default.Payments,
                            title = "No Payment Orders",
                            subtitle = "When users subscribe via YouPI PG or Direct UPI/Bank, their orders & UTR numbers appear here for reconciliation."
                        )
                    }
                } else {
                    items(filteredOrders, key = { it.orderId }) { ord ->
                        val isDone = ord.status in listOf("COMPLETED", "SUCCESS", "RECONCILED")
                        val isFail = ord.status in listOf("FAILED", "REFUNDED", "CANCELLED")
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ord.planName.ifBlank { ord.planId }} • ₹${ord.amountPaise / 100.0}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(ord.userEmail.ifBlank { ord.userId }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Order ${ord.orderId} • ${ord.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (ord.utrReference.isNotBlank()) {
                                        Text("UTR: ${ord.utrReference}", style = MaterialTheme.typography.labelMedium, color = SkyInfo)
                                    }
                                }
                                StatusPill(
                                    label = ord.status,
                                    color = when {
                                        isDone -> EmeraldSuccess
                                        isFail -> RoseAlert
                                        else -> AmberWarning
                                    }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isDone) {
                                    OutlinedButton(
                                        onClick = { onReconcilePayment(ord.orderId, "COMPLETED", ord.utrReference, "Approved by Admin") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Verify & Activate", color = EmeraldSuccess)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { reconcilingOrder = ord },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reconcile / UTR")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingNewPlan || editingPlan != null) {
        PlanEditorDialog(
            existing = editingPlan,
            onDismiss = {
                creatingNewPlan = false
                editingPlan = null
            },
            onSave = { planId, name, priceInr, credits, storageMb, interval, adFree, active ->
                onUpdatePlan(planId, name, priceInr, credits, storageMb, interval, adFree, active)
                creatingNewPlan = false
                editingPlan = null
            }
        )
    }

    managingUser?.let { usr ->
        ManageUserEntitlementDialog(
            user = usr,
            plans = overview?.plans.orEmpty(),
            onDismiss = { managingUser = null },
            onSave = { tier, adFree, credits, days ->
                val matchedStorage = overview?.plans?.find { it.planId == tier }?.storageLimitMb
                onUpdateUserSubscription(usr.userId, tier, adFree, credits, matchedStorage, days)
                managingUser = null
            }
        )
    }

    reconcilingOrder?.let { ord ->
        var utr by remember(ord) { mutableStateOf(ord.utrReference) }
        var note by remember(ord) { mutableStateOf("") }
        var status by remember(ord) { mutableStateOf("COMPLETED") }
        AlertDialog(
            onDismissRequest = { reconcilingOrder = null },
            title = { Text("Reconcile Payment Order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ord.orderId} • ${ord.userEmail.ifBlank { ord.userId }} • ₹${ord.amountPaise / 100.0}", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("COMPLETED", "FAILED", "REFUNDED", "CANCELLED")) { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                        }
                    }
                    OutlinedTextField(value = utr, onValueChange = { utr = it }, label = { Text("UTR / Reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Admin Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onReconcilePayment(ord.orderId, status, utr, note)
                    reconcilingOrder = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { reconcilingOrder = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanEditorDialog(
    existing: AdminPlanDto?,
    onDismiss: () -> Unit,
    onSave: (planId: String, name: String, priceInr: Long, credits: Long, storageMb: Long, interval: String, adFree: Boolean, active: Boolean) -> Unit
) {
    var planId by remember(existing) { mutableStateOf(existing?.planId ?: "") }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var price by remember(existing) { mutableStateOf((existing?.priceInr ?: 199L).toString()) }
    var interval by remember(existing) { mutableStateOf(existing?.interval ?: "monthly") }
    var credits by remember(existing) { mutableStateOf((existing?.aiMonthlyCredits ?: 500L).toString()) }
    var storageMb by remember(existing) { mutableStateOf((existing?.storageLimitMb ?: 5120L).toString()) }
    var adFree by remember(existing) { mutableStateOf(existing?.adFree ?: true) }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Plan: ${existing.planId}" else "Create Subscription Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) {
                    OutlinedTextField(
                        value = planId,
                        onValueChange = { planId = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                        label = { Text("Plan ID slug (e.g. quarterly)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = credits,
                        onValueChange = { credits = it.filter(Char::isDigit) },
                        label = { Text("AI Credits/mo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = storageMb,
                    onValueChange = { storageMb = it.filter(Char::isDigit) },
                    label = { Text("Backup Quota (MB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("free", "weekly", "monthly", "quarterly", "yearly", "custom")) { iv ->
                        FilterChip(selected = interval == iv, onClick = { interval = iv }, label = { Text(iv) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ads Block (Ad-Free)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = adFree, onCheckedChange = { adFree = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Plan Visible / Active", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanId = (existing?.planId ?: planId).trim()
                    onSave(
                        cleanId,
                        name.trim().ifBlank { cleanId },
                        price.toLongOrNull() ?: 0L,
                        credits.toLongOrNull() ?: 20L,
                        storageMb.toLongOrNull() ?: 100L,
                        interval,
                        adFree,
                        active
                    )
                },
                enabled = (existing != null || planId.isNotBlank()) && name.isNotBlank()
            ) { Text("Save Plan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManageUserEntitlementDialog(
    user: AdminUserSummaryDto,
    plans: List<AdminPlanDto>,
    onDismiss: () -> Unit,
    onSave: (tier: String, adFree: Boolean, credits: Int, durationDays: Int) -> Unit
) {
    val tierOptions = remember(plans) {
        val ids = plans.map { it.planId }
        if (ids.isNotEmpty()) ids else listOf("free", "monthly", "yearly")
    }
    var selectedTier by remember(user) { mutableStateOf(user.subscriptionTier) }
    var adFree by remember(user) { mutableStateOf(user.adFree) }
    var credits by remember(user) { mutableStateOf(user.aiCreditsRemaining.toString()) }
    var days by remember(user) { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage ${user.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Assign Plan Tier", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tierOptions) { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = {
                                selectedTier = tier
                                plans.find { it.planId == tier }?.let { matched ->
                                    adFree = matched.adFree
                                    credits = matched.aiMonthlyCredits.toString()
                                }
                            },
                            label = { Text(tier.uppercase()) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ads Block (Ad-Free Override)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = adFree, onCheckedChange = { adFree = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = credits,
                        onValueChange = { credits = it.filter(Char::isDigit) },
                        label = { Text("AI Credits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter(Char::isDigit) },
                        label = { Text("Days Valid") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    selectedTier,
                    adFree,
                    credits.toIntOrNull() ?: 20,
                    days.toIntOrNull() ?: 30
                )
            }) { Text("Save Entitlement") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
