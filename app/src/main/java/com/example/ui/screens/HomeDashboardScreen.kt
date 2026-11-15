package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ExpenseEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserEntity
import com.example.data.local.WarrantyEntity
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.FreeTierAdBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalGlassTokens
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TangerineVibrant
import com.example.ui.viewmodel.EntitlementUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    displayName: String,
    isGuest: Boolean,
    user: UserEntity?,
    subscription: SubscriptionEntity?,
    entitlement: EntitlementUiState = EntitlementUiState(),
    memories: List<MemoryEntity>,
    reminders: List<ReminderEntity>,
    warranties: List<WarrantyEntity>,
    expenses: List<ExpenseEntity>,
    onQuickScanDoc: () -> Unit,
    onQuickImportFile: () -> Unit,
    onQuickVoiceNote: () -> Unit,
    onQuickAskAi: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onOpenPrivateVault: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onMemoryClick: (MemoryEntity) -> Unit
) {
    val tokens = LocalGlassTokens.current
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val pendingReminders = remember(reminders) { reminders.filter { !it.isCompleted }.take(3) }
    val activeWarranties = remember(warranties) {
        val now = System.currentTimeMillis()
        warranties.filter { it.expiryDateMillis > now }.take(4)
    }
    val monthlySpendInr = remember(expenses) {
        expenses.sumOf { it.amountPaise } / 100.0
    }
    val planLabel = entitlement.planId.uppercase()
    val aiCredits = user?.aiCreditsRemaining ?: entitlement.aiCreditsRemaining
    val aiDenominator = entitlement.aiMonthlyCredits.coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Life Vault Overview Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_hero_saffron_1794757220661),
                        contentDescription = "YaadRakshak Sanctuary Header",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (tokens.isDark) {
                                    Brush.verticalGradient(
                                        listOf(Color(0x55141210), Color(0xF5141210))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0x33FFFFFF), Color(0xF5FFF7ED))
                                    )
                                }
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (tokens.isDark) Color(0xE629221E) else Color.White.copy(alpha = 0.92f)
                            ) {
                                StatusPill(
                                    label = if (isGuest) "LOCAL GUEST VAULT" else "LOCAL VAULT • AUTH SYNCED",
                                    color = if (isGuest) AmberWarning else EmeraldSuccess,
                                    icon = if (isGuest) Icons.Default.CloudOff else Icons.Default.CloudDone
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (entitlement.adFree) {
                                    Surface(
                                        shape = CircleShape,
                                        color = EmeraldSuccess
                                    ) {
                                        Text(
                                            text = "ADS BLOCKED ✓",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = onOpenUpgrade,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "$planLabel TIER",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp),
                                shadowElevation = 4.dp
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_app_logo),
                                    contentDescription = "YaadRakshak Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column {
                                Text(
                                    text = "Namaste, $displayName",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${memories.size} Memories • ${pendingReminders.size} Active Reminders • ₹${"%.0f".format(monthlySpendInr)} Tracked",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AI Credits Remaining (${entitlement.planName})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$aiCredits / $aiDenominator",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (aiCredits.toFloat() / aiDenominator.toFloat()).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }

        if (!entitlement.adFree) {
            item {
                FreeTierAdBanner(
                    planName = entitlement.planName,
                    onUpgradeClick = onOpenUpgrade
                )
            }
        }

        // Quick Capture Strip
        item {
            Text(
                text = "Quick Capture & Vaults",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickTile(
                    title = "Scan Doc",
                    icon = Icons.Default.DocumentScanner,
                    accent = SaffronPrimary,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_scan_doc",
                    onClick = onQuickScanDoc
                )
                QuickTile(
                    title = "Import File",
                    icon = Icons.Default.UploadFile,
                    accent = SkyInfo,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_import_file",
                    onClick = onQuickImportFile
                )
                QuickTile(
                    title = "Voice Memo",
                    icon = Icons.Default.Mic,
                    accent = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_voice_memo",
                    onClick = onQuickVoiceNote
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickTile(
                    title = "Ask AI",
                    icon = Icons.Default.AutoAwesome,
                    accent = TangerineVibrant,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_ask_ai",
                    onClick = onQuickAskAi
                )
                QuickTile(
                    title = "Add Expense",
                    icon = Icons.Default.AccountBalanceWallet,
                    accent = AmberWarning,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_add_expense",
                    onClick = onQuickAddExpense
                )
                QuickTile(
                    title = "Private Vault",
                    icon = Icons.Default.Lock,
                    accent = RoseAlert,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_private_vault",
                    onClick = onOpenPrivateVault
                )
            }
        }

        // Expiring Warranties Carousel
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Warranty Guard", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenReminders) { Text("Manage") }
            }
            if (activeWarranties.isEmpty()) {
                EmptyStateGlassCard(
                    icon = Icons.Default.VerifiedUser,
                    title = "No Active Warranties Tracked",
                    subtitle = "Add appliance, laptop, or vehicle warranties to get expiry alerts before coverage ends.",
                    actionLabel = "Add Warranty",
                    onAction = onOpenReminders
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(activeWarranties, key = { it.id }) { w ->
                        val daysLeft = ((w.expiryDateMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0L)
                        GlassCard(modifier = Modifier.width(210.dp)) {
                            StatusPill(
                                label = "${daysLeft}d left",
                                color = if (daysLeft < 30) AmberWarning else EmeraldSuccess
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = w.productName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = w.brand.ifBlank { "Registered Coverage" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Expires ${dateFmt.format(Date(w.expiryDateMillis))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Upcoming Reminders
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upcoming Reminders", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenReminders) { Text("View All") }
            }
            if (pendingReminders.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "No pending reminders. Schedule bill, renewal, or warranty alerts anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingReminders.forEach { rem ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenReminders) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rem.title, style = MaterialTheme.typography.titleMedium)
                                    if (rem.notes.isNotBlank()) {
                                        Text(
                                            rem.notes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                StatusPill(
                                    label = dateFmt.format(Date(rem.dueAtMillis)),
                                    color = if (rem.priority == "HIGH") RoseAlert else SaffronPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Memories
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Memories", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenMemories) { Text("Open Vault") }
            }
            if (memories.isEmpty()) {
                EmptyStateGlassCard(
                    icon = Icons.Default.ReceiptLong,
                    title = "Your Memory Vault is Empty",
                    subtitle = "Capture notes, scan documents with OCR, import PDFs, or record voice memos.",
                    actionLabel = "Capture First Memory",
                    onAction = onOpenMemories
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    memories.filter { !it.isPrivate }.take(5).forEach { mem ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onMemoryClick(mem) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mem.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                StatusPill(label = mem.memoryType, color = SaffronPrimary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = mem.summary.ifBlank { mem.content },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.testTag(testTag),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
