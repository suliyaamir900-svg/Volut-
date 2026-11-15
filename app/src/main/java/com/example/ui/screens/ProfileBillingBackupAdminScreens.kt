package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.core.datastore.AppPreferences
import com.example.core.datastore.ThemeMode
import com.example.data.local.BackupJobEntity
import com.example.data.local.PaymentOrderEntity
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserEntity
import com.example.data.remote.CreatePaymentOrderResponseDto
import com.example.data.remote.FirestorePlan
import com.example.data.remote.MerchantPublicInfoDto
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SkyInfo
import com.example.ui.viewmodel.EntitlementUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileSettingsScreen(
    displayName: String,
    email: String,
    isGuest: Boolean,
    isAdmin: Boolean,
    user: UserEntity?,
    preferences: AppPreferences,
    subscription: SubscriptionEntity?,
    entitlement: EntitlementUiState = EntitlementUiState(),
    memoryCount: Int,
    reminderCount: Int,
    warrantyCount: Int,
    receiptCount: Int,
    contactCount: Int,
    onSetTheme: (ThemeMode) -> Unit,
    onConfigureLock: (pin: String, enable: Boolean, biometric: Boolean) -> Unit,
    onSetBackendUrl: (String) -> Unit,
    onSetCloudSync: (Boolean) -> Unit,
    onTriggerSync: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAdmin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showBackendDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = email.ifBlank { "Offline Guest Vault" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        label = subscription?.planId?.uppercase() ?: entitlement.planId.uppercase(),
                        color = SaffronPrimary,
                        icon = Icons.Default.WorkspacePremium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = when {
                            isAdmin -> "ADMIN ACCOUNT"
                            isGuest -> "LOCAL GUEST"
                            else -> "STANDARD USER"
                        },
                        color = if (isAdmin) SaffronPrimary else if (isGuest) AmberWarning else EmeraldSuccess
                    )
                    StatusPill(
                        label = if (entitlement.adFree) "ADS BLOCKED ✓" else "AD-SUPPORTED",
                        color = if (entitlement.adFree) EmeraldSuccess else AmberWarning
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${entitlement.planName} • ${user?.aiCreditsRemaining ?: entitlement.aiCreditsRemaining}/${entitlement.aiMonthlyCredits} AI Credits • ${entitlement.storageLimitMb} MB Quota",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isGuest) {
                    Spacer(Modifier.height(12.dp))
                    GradientPrimaryButton(
                        text = "Sign in with Google (Auth, AI & Billing)",
                        onClick = onGoogleSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "profile_google_signin"
                    )
                }
            }
        }

        // Theme Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = SaffronPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "White & Saffron Light default, Warm Ivory text in Dark & System modes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSetTheme(mode) },
                            label = { Text(mode.name) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Security & Backend
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldSuccess)
                    Spacer(Modifier.width(8.dp))
                    Text("Security & Backend Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock & Biometrics", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (preferences.isAppLockEnabled) "4-digit PIN active" else "Unlocked (tap to set PIN)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showPinDialog = true }) {
                        Text("Configure")
                    }
                }
                if (isAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backend API Endpoint", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                preferences.backendBaseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyInfo
                            )
                        }
                        OutlinedButton(onClick = { showBackendDialog = true }) {
                            Text("Edit URL")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Firebase Auth & Plans Sync", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (preferences.lastCloudSyncMillis > 0) {
                                "Auth profile refreshed ${dateFmt.format(Date(preferences.lastCloudSyncMillis))} (vault stays local)"
                            } else {
                                "Only Auth & billing sync with Firebase; personal vault stays on device"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = preferences.cloudSyncEnabled, onCheckedChange = onSetCloudSync)
                }
                if (!isGuest) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onTriggerSync, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh Auth Profile & Plans")
                    }
                }
            }
        }

        // Local Database Viewer Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = SaffronPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Local Room Database Stats (Device-Only)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Memories: $memoryCount • Reminders: $reminderCount • Warranties: $warrantyCount • Receipts: $receiptCount • Contacts: $contactCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Module Links
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientPrimaryButton(
                        text = "Premium Subscription & YouPI PG",
                        icon = Icons.Default.WorkspacePremium,
                        onClick = onOpenSubscription,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_subscription_button"
                    )
                    OutlinedButton(
                        onClick = onOpenBackup,
                        modifier = Modifier.fillMaxWidth().testTag("open_backup_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Encrypted Backup, Restore & Account Data")
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.fillMaxWidth().testTag("open_admin_button")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AmberWarning)
                            Spacer(Modifier.width(8.dp))
                            Text("Admin Control Center (AI, Plans, Users & Bank)")
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RoseAlert)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isGuest) "Exit Guest Mode" else "Sign Out", color = RoseAlert)
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var enableLock by remember { mutableStateOf(preferences.isAppLockEnabled) }
        var enableBio by remember { mutableStateOf(preferences.isBiometricEnabled) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("App Lock & Biometrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it.filter(Char::isDigit) },
                        label = { Text("4-digit PIN (leave blank to keep existing)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableLock, onCheckedChange = { enableLock = it })
                        Text("Require PIN on app launch")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableBio, onCheckedChange = { enableBio = it })
                        Text("Allow Fingerprint / Biometric unlock")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfigureLock(pin, enableLock, enableBio)
                    showPinDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }

    if (showBackendDialog) {
        var url by remember { mutableStateOf(preferences.backendBaseUrl) }
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            title = { Text("Configure Backend Base URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the HTTPS base URL where your YaadRakshak Node.js backend is deployed (used for Groq AI proxy & YouPI PG orders).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://api.yourdomain.com/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetBackendUrl(url)
                    showBackendDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBackendDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SubscriptionAndPaymentsScreen(
    isGuest: Boolean,
    subscription: SubscriptionEntity?,
    entitlement: EntitlementUiState = EntitlementUiState(),
    plans: List<FirestorePlan>,
    orders: List<PaymentOrderEntity>,
    transactions: List<PaymentTransactionEntity>,
    merchantInfo: MerchantPublicInfoDto? = null,
    latestCreatedOrder: CreatePaymentOrderResponseDto? = null,
    billingMessage: String?,
    onSelectPlan: (planId: String, paymentMethod: String) -> Unit,
    onOpenUpiUri: (String) -> Unit = {},
    onOpenCheckoutUrl: (String) -> Unit = {},
    onSubmitUtr: (orderId: String, utr: String) -> Unit = { _, _ -> },
    onRefreshEntitlement: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var utrDialogOrderId by remember { mutableStateOf<String?>(null) }
    var inlineUtr by remember(latestCreatedOrder) { mutableStateOf("") }

    val activePlans = remember(plans, entitlement.planId) {
        plans.filter { it.active || it.planId == entitlement.planId }.sortedBy { it.priceInr }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("subscription_plans_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Entitlement Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Current Entitlement",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = entitlement.planName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                label = if (entitlement.adFree) "ADS BLOCKED ✓" else "AD-SUPPORTED",
                                color = if (entitlement.adFree) EmeraldSuccess else AmberWarning,
                                icon = Icons.Default.Shield
                            )
                            StatusPill(
                                label = entitlement.status,
                                color = if (entitlement.status == "ACTIVE") EmeraldSuccess else AmberWarning
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onRefreshEntitlement,
                        enabled = !isGuest,
                        modifier = Modifier.testTag("restore_purchase_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verify")
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "AI Credits: ${entitlement.aiCreditsRemaining} / ${entitlement.aiMonthlyCredits}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Quota: ${entitlement.storageLimitMb} MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (entitlement.expiresAtMillis > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Valid until: ${dateFmt.format(Date(entitlement.expiresAtMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (billingMessage != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(billingMessage, style = MaterialTheme.typography.bodyMedium, color = SaffronPrimary)
                }
            }
        }

        // Latest Checkout Order with UPI / Bank / UTR submission
        latestCreatedOrder?.let { ord ->
            item {
                val info = ord.merchantInfo ?: merchantInfo
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.5.dp, SaffronPrimary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Complete Payment • ₹${ord.amountPaise / 100.0}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusPill(label = ord.status, color = AmberWarning)
                        }
                        Text(
                            "Order ${ord.orderId} (${ord.planName.ifBlank { ord.planId }})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ord.checkoutUrl.isNotBlank()) {
                            GradientPrimaryButton(
                                text = "Open YouPI PG Checkout",
                                icon = Icons.Default.CreditCard,
                                onClick = { onOpenCheckoutUrl(ord.checkoutUrl) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (ord.upiPayUri.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onOpenUpiUri(ord.upiPayUri) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pay ₹${ord.amountPaise / 100} via UPI App")
                            }
                        }
                        if (info != null && (info.upiReady || info.bankReady)) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Admin Official Payment Details",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SaffronPrimary
                                    )
                                    if (info.paymentUpiId.isNotBlank()) {
                                        Text("UPI ID: ${info.paymentUpiId} (${info.payeeName})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (info.bankName.isNotBlank() || info.bankAccountDisplay.isNotBlank()) {
                                        Text(
                                            "Bank: ${info.bankName} • A/C: ${info.bankAccountDisplay.ifBlank { info.maskedBankAccount }} • IFSC: ${info.ifscCode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = inlineUtr,
                            onValueChange = { inlineUtr = it },
                            label = { Text("Enter 12-digit UTR / Bank Reference") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("inline_utr_input")
                        )
                        GradientPrimaryButton(
                            text = "Submit UTR for Server Verification",
                            enabled = inlineUtr.trim().length >= 6,
                            onClick = {
                                onSubmitUtr(ord.orderId, inlineUtr)
                                inlineUtr = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "submit_utr_button"
                        )
                    }
                }
            }
        }

        // Accepted Merchant Payment Channels Info Strip
        if (merchantInfo != null && (merchantInfo.upiReady || merchantInfo.bankReady || merchantInfo.youpiReady)) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldSuccess)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Verified Merchant Channels Configured by Admin",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (merchantInfo.youpiReady) {
                        Text("• YouPI PG Gateway (ID: ${merchantInfo.merchantId})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (merchantInfo.upiReady) {
                        Text("• Direct UPI: ${merchantInfo.paymentUpiId} (${merchantInfo.payeeName})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (merchantInfo.bankReady) {
                        Text(
                            "• Bank Transfer: ${merchantInfo.bankName} • A/C ${merchantInfo.bankAccountDisplay.ifBlank { merchantInfo.maskedBankAccount }} • IFSC ${merchantInfo.ifscCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Subscription Plans Catalog
        items(activePlans, key = { it.planId }) { plan ->
            val isCurrent = entitlement.planId == plan.planId
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(plan.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (plan.priceInr == 0L) "₹0 / forever" else "₹${plan.priceInr} / ${plan.interval}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SaffronPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isCurrent) {
                        StatusPill(label = "ACTIVE PLAN", color = EmeraldSuccess, icon = Icons.Default.Verified)
                    } else if (plan.adFree) {
                        StatusPill(label = "AD-FREE", color = SkyInfo)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlanBullet(
                        text = if (plan.adFree) "All Ads Blocked across Dashboard & Vault" else "Ad-Supported Experience",
                        highlighted = plan.adFree
                    )
                    PlanBullet(text = "${plan.aiMonthlyCredits} Groq AI Credits / month", highlighted = plan.aiMonthlyCredits > 20L)
                    PlanBullet(text = "${plan.storageLimitMb} MB Encrypted Backup Quota", highlighted = plan.storageLimitMb > 100L)
                }
                if (plan.priceInr > 0 && !isCurrent) {
                    Spacer(Modifier.height(12.dp))
                    GradientPrimaryButton(
                        text = if (isGuest) "Sign in with Google to Subscribe" else "Pay ₹${plan.priceInr} via YouPI PG",
                        icon = Icons.Default.CreditCard,
                        enabled = !isGuest,
                        onClick = { onSelectPlan(plan.planId, "AUTO") },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "subscribe_${plan.planId}"
                    )
                    if (!isGuest && (merchantInfo?.upiReady == true || merchantInfo?.bankReady == true)) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onSelectPlan(plan.planId, "DIRECT_UPI") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pay ₹${plan.priceInr} via UPI / Bank")
                        }
                    }
                }
            }
        }

        item {
            Text("Orders & Payment History", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }

        if (orders.isEmpty() && transactions.isEmpty()) {
            item {
                EmptyStateGlassCard(
                    icon = Icons.Default.CreditCard,
                    title = "No Payment Transactions Yet",
                    subtitle = "All YouPI PG & UPI orders are verified by server webhooks or Admin reconciliation before activating Premium."
                )
            }
        } else {
            items(orders, key = { it.id }) { ord ->
                val isCompleted = ord.status in listOf("COMPLETED", "SUCCESS", "RECONCILED")
                val isFailed = ord.status in listOf("FAILED", "CANCELLED", "REFUNDED")
                val needsUtr = ord.status in listOf("CREATED", "PENDING", "PENDING_PAYMENT")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Order ${ord.id}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Plan: ${ord.planId} • ₹${ord.amountPaise / 100.0} (${ord.paymentMethod})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ord.utrReference.isNotBlank()) {
                                Text("UTR: ${ord.utrReference}", style = MaterialTheme.typography.labelMedium, color = SkyInfo)
                            }
                            Text(dateFmt.format(Date(ord.createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            label = ord.status,
                            color = when {
                                isCompleted -> EmeraldSuccess
                                isFailed -> RoseAlert
                                else -> AmberWarning
                            }
                        )
                    }
                    if (needsUtr && !isGuest) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { utrDialogOrderId = ord.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit / Update UTR Reference")
                        }
                    }
                }
            }
            items(transactions, key = { it.id }) { tx ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Verified Settlement: ${tx.gatewayEvent}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Order ${tx.orderId} • UTR: ${tx.utrReference.ifBlank { "—" }} • Signature verified: ${tx.verifiedSignature}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    utrDialogOrderId?.let { ordId ->
        var utrInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { utrDialogOrderId = null },
            title = { Text("Submit UPI UTR / Bank Ref") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order: $ordId", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = utrInput,
                        onValueChange = { utrInput = it },
                        label = { Text("12-digit UTR / Reference Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSubmitUtr(ordId, utrInput)
                        utrDialogOrderId = null
                    },
                    enabled = utrInput.trim().length >= 6
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { utrDialogOrderId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlanBullet(text: String, highlighted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (highlighted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BackupAndCloudScreen(
    isGuest: Boolean,
    backupJobs: List<BackupJobEntity>,
    backupMessage: String?,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (String) -> Unit,
    onDeleteAccountAndData: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Encrypted Vault Backup", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Exports your memories, reminders, warranties, receipts, expenses, and contacts into an AES-256-GCM encrypted .yrbak archive with SHA-256 integrity verification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                GradientPrimaryButton(
                    text = "Create Encrypted Backup",
                    icon = Icons.Default.Backup,
                    onClick = onCreateBackup,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "create_backup_button"
                )
                if (backupMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(backupMessage, style = MaterialTheme.typography.bodyMedium, color = EmeraldSuccess)
                }
            }
        }

        item {
            Text("Saved Backup Snapshots", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }

        if (backupJobs.isEmpty()) {
            item {
                EmptyStateGlassCard(
                    icon = Icons.Default.Restore,
                    title = "No Backups Created Yet",
                    subtitle = "Create an encrypted snapshot anytime to protect against accidental deletion."
                )
            }
        } else {
            items(backupJobs, key = { it.id }) { job ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${job.itemCount} records • ${job.status}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(dateFmt.format(Date(job.completedAtMillis)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("SHA-256: ${job.checksumSha256.take(16)}...", style = MaterialTheme.typography.labelSmall, color = SkyInfo)
                        }
                        OutlinedButton(onClick = { onRestoreBackup(job.encryptedFilePath) }) {
                            Text("Restore")
                        }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Data Privacy & Account Deletion", style = MaterialTheme.typography.titleMedium, color = RoseAlert)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Permanently erase all local Room vault data" + if (!isGuest) " and your Firebase Auth profile record." else ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RoseAlert)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All My Data", color = RoseAlert)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Confirm Permanent Deletion") },
            text = { Text("This action cannot be undone. All memories, warranties, receipts, and reminders will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccountAndData()
                }) { Text("Delete Everything", color = RoseAlert) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
