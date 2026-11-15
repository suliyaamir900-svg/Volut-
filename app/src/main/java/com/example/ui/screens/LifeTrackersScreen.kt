package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.ExpenseEntity
import com.example.data.local.PersonalContactEntity
import com.example.data.local.ReceiptEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.WarrantyEntity
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TangerineVibrant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TrackerSegment(val label: String) {
    REMINDERS("Reminders"),
    WARRANTIES("Warranties"),
    RECEIPTS("Receipts"),
    EXPENSES("Expenses"),
    PURCHASES("Purchases"),
    CONTACTS("Contacts")
}

@Composable
fun LifeTrackersScreen(
    reminders: List<ReminderEntity>,
    warranties: List<WarrantyEntity>,
    receipts: List<ReceiptEntity>,
    expenses: List<ExpenseEntity>,
    contacts: List<PersonalContactEntity>,
    initialSegment: TrackerSegment = TrackerSegment.REMINDERS,
    onAddReminder: (title: String, notes: String, dueAtMillis: Long, priority: String, repeatRule: String) -> Unit,
    onUpdateReminder: (ReminderEntity) -> Unit = {},
    onDeleteReminder: (String) -> Unit = {},
    onToggleReminder: (ReminderEntity) -> Unit,
    onAddWarranty: (productName: String, brand: String, serial: String, purchaseMillis: Long, expiryMillis: Long, coverage: String, phone: String) -> Unit,
    onUpdateWarranty: (WarrantyEntity) -> Unit = {},
    onDeleteWarranty: (String) -> Unit = {},
    onAddReceipt: (merchant: String, totalInr: Double, taxInr: Double, invoice: String, category: String) -> Unit,
    onUpdateReceipt: (ReceiptEntity) -> Unit = {},
    onDeleteReceipt: (String) -> Unit = {},
    onAddExpense: (title: String, category: String, amountInr: Double, notes: String) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit = {},
    onDeleteExpense: (String) -> Unit = {},
    onAddContact: (name: String, relation: String, phone: String, email: String, notes: String) -> Unit,
    onUpdateContact: (PersonalContactEntity) -> Unit = {},
    onDeleteContact: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var activeSegment by remember(initialSegment) { mutableStateOf(initialSegment) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var editingWarranty by remember { mutableStateOf<WarrantyEntity?>(null) }
    var editingReceipt by remember { mutableStateOf<ReceiptEntity?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editingContact by remember { mutableStateOf<PersonalContactEntity?>(null) }

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission = granted }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val shortDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (activeSegment == TrackerSegment.REMINDERS &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotifPermission
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("tracker_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add ${activeSegment.label}")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(TrackerSegment.entries) { seg ->
                    FilterChip(
                        selected = activeSegment == seg,
                        onClick = { activeSegment = seg },
                        label = { Text(seg.label) },
                        modifier = Modifier.testTag("tracker_tab_${seg.name.lowercase()}")
                    )
                }
            }

            if (activeSegment == TrackerSegment.REMINDERS && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Enable notifications to receive scheduled reminder alerts.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                }
            }

            when (activeSegment) {
                TrackerSegment.REMINDERS -> {
                    if (reminders.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.NotificationsActive,
                            title = "No Smart Reminders",
                            subtitle = "Schedule bill dues, warranty renewals, doctor visits, or custom WorkManager alerts.",
                            actionLabel = "Add Reminder",
                            onAction = { showAddDialog = true }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(reminders, key = { it.id }) { rem ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = rem.isCompleted,
                                            onCheckedChange = { onToggleReminder(rem) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rem.title,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    textDecoration = if (rem.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                )
                                            )
                                            if (rem.notes.isNotBlank()) {
                                                Text(
                                                    text = rem.notes,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Due: ${dateFmt.format(Date(rem.dueAtMillis))} • Repeat: ${rem.repeatRule}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SaffronPrimary
                                            )
                                        }
                                        StatusPill(
                                            label = rem.priority,
                                            color = when (rem.priority) {
                                                "HIGH" -> RoseAlert
                                                "LOW" -> EmeraldSuccess
                                                else -> AmberWarning
                                            }
                                        )
                                        IconButton(
                                            onClick = { editingReminder = rem },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Reminder", tint = SaffronPrimary)
                                        }
                                        IconButton(
                                            onClick = { onDeleteReminder(rem.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Reminder", tint = RoseAlert)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TrackerSegment.WARRANTIES -> {
                    if (warranties.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.VerifiedUser,
                            title = "No Warranties Saved",
                            subtitle = "Track serial numbers, brand support numbers, and coverage expiry dates.",
                            actionLabel = "Register Warranty",
                            onAction = { showAddDialog = true }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(warranties, key = { it.id }) { w ->
                                val daysLeft = ((w.expiryDateMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L))
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(w.productName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${w.brand.ifBlank { "Device" }} • SN: ${w.serialNumber.ifBlank { "N/A" }}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(
                                            label = if (daysLeft >= 0) "${daysLeft}d left" else "EXPIRED",
                                            color = if (daysLeft < 0) RoseAlert else if (daysLeft < 30) AmberWarning else EmeraldSuccess
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Purchased ${shortDateFmt.format(Date(w.purchaseDateMillis))} • Expires ${shortDateFmt.format(Date(w.expiryDateMillis))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SaffronPrimary
                                    )
                                    if (w.coverageNotes.isNotBlank() || w.supportPhone.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            listOf(w.coverageNotes, w.supportPhone.takeIf { it.isNotBlank() }?.let { "Support: $it" })
                                                .filter { !it.isNullOrBlank() }
                                                .joinToString(" • "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = { editingWarranty = w }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Warranty", tint = SaffronPrimary)
                                        }
                                        IconButton(onClick = { onDeleteWarranty(w.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Warranty", tint = RoseAlert)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TrackerSegment.RECEIPTS -> {
                    if (receipts.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.ReceiptLong,
                            title = "No Receipts Recorded",
                            subtitle = "Record merchant invoices with GST/tax breakdown or scan physical receipts via CameraX OCR.",
                            actionLabel = "Add Receipt",
                            onAction = { showAddDialog = true }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(receipts, key = { it.id }) { rec ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rec.merchantName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Invoice: ${rec.invoiceNumber.ifBlank { "N/A" }} • ${shortDateFmt.format(Date(rec.purchasedAtMillis))}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "₹${"%.2f".format(rec.totalAmountPaise / 100.0)}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = EmeraldSuccess
                                            )
                                            if (rec.taxAmountPaise > 0) {
                                                Text(
                                                    "Tax ₹${"%.2f".format(rec.taxAmountPaise / 100.0)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row {
                                                IconButton(onClick = { editingReceipt = rec }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Receipt", tint = SaffronPrimary)
                                                }
                                                IconButton(onClick = { onDeleteReceipt(rec.id) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Receipt", tint = RoseAlert)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TrackerSegment.EXPENSES -> {
                    if (expenses.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "No Expenses Tracked Yet",
                            subtitle = "Track personal spend across Electronics, Health, Utilities, Groceries, and Travel.",
                            actionLabel = "Log Expense",
                            onAction = { showAddDialog = true }
                        )
                    } else {
                        val totalInr = remember(expenses) { expenses.sumOf { it.amountPaise } / 100.0 }
                        val byCategory = remember(expenses) {
                            expenses.groupBy { it.category.ifBlank { "General" } }
                                .mapValues { (_, list) -> list.sumOf { it.amountPaise } / 100.0 }
                                .entries
                                .sortedByDescending { it.value }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            item {
                                ExpenseBreakdownCard(totalInr = totalInr, categories = byCategory)
                            }
                            items(expenses, key = { it.id }) { exp ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exp.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${exp.category} • ${shortDateFmt.format(Date(exp.spentAtMillis))}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "₹${"%.2f".format(exp.amountPaise / 100.0)}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = AmberWarning
                                            )
                                            IconButton(onClick = { editingExpense = exp }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Expense", tint = SaffronPrimary)
                                            }
                                            IconButton(onClick = { onDeleteExpense(exp.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = RoseAlert)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TrackerSegment.PURCHASES -> {
                    val combinedPurchases = remember(receipts, warranties) {
                        receipts.map { rec ->
                            Triple(
                                rec.merchantName,
                                "₹${"%.2f".format(rec.totalAmountPaise / 100.0)} • Inv ${rec.invoiceNumber.ifBlank { "—" }}",
                                shortDateFmt.format(Date(rec.purchasedAtMillis))
                            )
                        } + warranties.map { w ->
                            Triple(
                                w.productName,
                                "${w.brand.ifBlank { "Hardware" }} • SN ${w.serialNumber.ifBlank { "—" }}",
                                shortDateFmt.format(Date(w.purchaseDateMillis))
                            )
                        }
                    }
                    if (combinedPurchases.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.ShoppingBag,
                            title = "No Purchase History",
                            subtitle = "Add a receipt or product warranty to build your searchable purchase ledger.",
                            actionLabel = "Add Receipt",
                            onAction = {
                                activeSegment = TrackerSegment.RECEIPTS
                                showAddDialog = true
                            }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(combinedPurchases) { (title, sub, dt) ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, style = MaterialTheme.typography.titleMedium)
                                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusPill(label = dt, color = SkyInfo)
                                    }
                                }
                            }
                        }
                    }
                }

                TrackerSegment.CONTACTS -> {
                    if (contacts.isEmpty()) {
                        EmptyStateGlassCard(
                            icon = Icons.Default.Contacts,
                            title = "No Important Contacts Saved",
                            subtitle = "Keep doctors, emergency contacts, CA/advisors, service engineers, and family records handy.",
                            actionLabel = "Save Contact",
                            onAction = { showAddDialog = true }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(contacts, key = { it.id }) { c ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        if (c.relationOrRole.isNotBlank()) {
                                            StatusPill(label = c.relationOrRole, color = SaffronPrimary)
                                        }
                                        IconButton(onClick = { editingContact = c }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Contact", tint = SaffronPrimary)
                                        }
                                        IconButton(onClick = { onDeleteContact(c.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Contact", tint = RoseAlert)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    if (c.phone.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldSuccess)
                                            Spacer(Modifier.width(6.dp))
                                            Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (c.email.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = SkyInfo)
                                            Spacer(Modifier.width(6.dp))
                                            Text(c.email, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (c.notes.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(c.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        when (activeSegment) {
            TrackerSegment.REMINDERS -> AddReminderDialog(
                onDismiss = { showAddDialog = false },
                onSave = { t, n, due, prio, rep ->
                    onAddReminder(t, n, due, prio, rep)
                    showAddDialog = false
                }
            )
            TrackerSegment.WARRANTIES -> AddWarrantyDialog(
                onDismiss = { showAddDialog = false },
                onSave = { p, b, s, pMillis, eMillis, cov, ph ->
                    onAddWarranty(p, b, s, pMillis, eMillis, cov, ph)
                    showAddDialog = false
                }
            )
            TrackerSegment.RECEIPTS, TrackerSegment.PURCHASES -> AddReceiptDialog(
                onDismiss = { showAddDialog = false },
                onSave = { m, tot, tax, inv, cat ->
                    onAddReceipt(m, tot, tax, inv, cat)
                    showAddDialog = false
                }
            )
            TrackerSegment.EXPENSES -> AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onSave = { t, c, amt, notes ->
                    onAddExpense(t, c, amt, notes)
                    showAddDialog = false
                }
            )
            TrackerSegment.CONTACTS -> AddContactDialog(
                onDismiss = { showAddDialog = false },
                onSave = { n, rel, ph, em, notes ->
                    onAddContact(n, rel, ph, em, notes)
                    showAddDialog = false
                }
            )
        }
    }

    editingReminder?.let { existing ->
        AddReminderDialog(
            existing = existing,
            onDismiss = { editingReminder = null },
            onSave = { t, n, due, prio, rep ->
                onUpdateReminder(existing.copy(title = t, notes = n, dueAtMillis = due, priority = prio, repeatRule = rep))
                editingReminder = null
            }
        )
    }

    editingWarranty?.let { existing ->
        AddWarrantyDialog(
            existing = existing,
            onDismiss = { editingWarranty = null },
            onSave = { p, b, s, pMillis, eMillis, cov, ph ->
                onUpdateWarranty(
                    existing.copy(
                        productName = p,
                        brand = b,
                        serialNumber = s,
                        purchaseDateMillis = pMillis,
                        expiryDateMillis = eMillis,
                        coverageNotes = cov,
                        supportPhone = ph
                    )
                )
                editingWarranty = null
            }
        )
    }

    editingReceipt?.let { existing ->
        AddReceiptDialog(
            existing = existing,
            onDismiss = { editingReceipt = null },
            onSave = { m, tot, tax, inv, _ ->
                onUpdateReceipt(
                    existing.copy(
                        merchantName = m,
                        totalAmountPaise = (tot * 100).toLong(),
                        taxAmountPaise = (tax * 100).toLong(),
                        invoiceNumber = inv
                    )
                )
                editingReceipt = null
            }
        )
    }

    editingExpense?.let { existing ->
        AddExpenseDialog(
            existing = existing,
            onDismiss = { editingExpense = null },
            onSave = { t, c, amt, notes ->
                onUpdateExpense(
                    existing.copy(
                        title = t,
                        category = c,
                        amountPaise = (amt * 100).toLong(),
                        notes = notes
                    )
                )
                editingExpense = null
            }
        )
    }

    editingContact?.let { existing ->
        AddContactDialog(
            existing = existing,
            onDismiss = { editingContact = null },
            onSave = { n, rel, ph, em, notes ->
                onUpdateContact(
                    existing.copy(
                        name = n,
                        relationOrRole = rel,
                        phone = ph,
                        email = em,
                        notes = notes
                    )
                )
                editingContact = null
            }
        )
    }
}

@Composable
private fun ExpenseBreakdownCard(
    totalInr: Double,
    categories: List<Map.Entry<String, Double>>
) {
    val palette = listOf(SaffronPrimary, TangerineVibrant, EmeraldSuccess, AmberWarning, RoseAlert, SkyInfo)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Total Tracked Spend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹${"%.2f".format(totalInr)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            StatusPill(label = "${categories.size} categories", color = SaffronPrimary)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val safeTotal = totalInr.coerceAtLeast(1.0)
            var currentX = 0f
            categories.forEachIndexed { idx, entry ->
                val frac = (entry.value / safeTotal).toFloat().coerceIn(0f, 1f)
                val segW = size.width * frac
                drawRect(
                    color = palette[idx % palette.size],
                    topLeft = Offset(currentX, 0f),
                    size = Size(segW, size.height)
                )
                currentX += segW
            }
        }
        Spacer(Modifier.height(10.dp))
        categories.take(4).forEachIndexed { idx, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.key,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette[idx % palette.size],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("₹${"%.2f".format(entry.value)}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AddReminderDialog(
    existing: ReminderEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var hoursAhead by remember(existing) {
        mutableStateOf(
            if (existing != null) {
                ((existing.dueAtMillis - System.currentTimeMillis()) / 3600_000L).coerceAtLeast(1L).toString()
            } else "24"
        )
    }
    var priority by remember(existing) { mutableStateOf(existing?.priority ?: "MEDIUM") }
    var repeatRule by remember(existing) { mutableStateOf(existing?.repeatRule ?: "NONE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Smart Reminder" else "Schedule Smart Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reminder_title_input"))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = hoursAhead,
                    onValueChange = { hoursAhead = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Trigger in (hours)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE", "DAILY", "MONTHLY").forEach { r ->
                        FilterChip(selected = repeatRule == r, onClick = { repeatRule = r }, label = { Text(r) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hrs = hoursAhead.toLongOrNull()?.coerceAtLeast(1L) ?: 24L
                    onSave(title, notes, System.currentTimeMillis() + hrs * 3600_000L, priority, repeatRule)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_reminder_button")
            ) { Text(if (existing != null) "Update" else "Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddWarrantyDialog(
    existing: WarrantyEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, Long, String, String) -> Unit
) {
    var product by remember(existing) { mutableStateOf(existing?.productName.orEmpty()) }
    var brand by remember(existing) { mutableStateOf(existing?.brand.orEmpty()) }
    var serial by remember(existing) { mutableStateOf(existing?.serialNumber.orEmpty()) }
    var monthsValid by remember(existing) { mutableIntStateOf(12) }
    var coverage by remember(existing) { mutableStateOf(existing?.coverageNotes.orEmpty()) }
    var supportPhone by remember(existing) { mutableStateOf(existing?.supportPhone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Warranty" else "Register Product Warranty") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = product, onValueChange = { product = it }, label = { Text("Product Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 12, 24, 36).forEach { m ->
                        FilterChip(selected = monthsValid == m, onClick = { monthsValid = m }, label = { Text("${m}m") })
                    }
                }
                OutlinedTextField(value = supportPhone, onValueChange = { supportPhone = it }, label = { Text("Support Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = coverage, onValueChange = { coverage = it }, label = { Text("Coverage Terms") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = existing?.purchaseDateMillis ?: System.currentTimeMillis()
                    val exp = base + monthsValid * 30L * 24L * 3600_000L
                    onSave(product, brand, serial, base, exp, coverage, supportPhone)
                },
                enabled = product.isNotBlank()
            ) { Text(if (existing != null) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddReceiptDialog(
    existing: ReceiptEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String, String) -> Unit
) {
    var merchant by remember(existing) { mutableStateOf(existing?.merchantName.orEmpty()) }
    var total by remember(existing) { mutableStateOf(existing?.let { "%.2f".format(it.totalAmountPaise / 100.0) }.orEmpty()) }
    var tax by remember(existing) { mutableStateOf(existing?.let { "%.2f".format(it.taxAmountPaise / 100.0) } ?: "0") }
    var invoice by remember(existing) { mutableStateOf(existing?.invoiceNumber.orEmpty()) }
    var category by remember(existing) { mutableStateOf("Shopping") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Receipt" else "Add Receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it },
                    label = { Text("Total Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tax,
                    onValueChange = { tax = it },
                    label = { Text("Tax / GST (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = invoice, onValueChange = { invoice = it }, label = { Text("Invoice Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existing == null) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Expense Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tVal = total.toDoubleOrNull() ?: 0.0
                    val taxVal = tax.toDoubleOrNull() ?: 0.0
                    onSave(merchant, tVal, taxVal, invoice, category)
                },
                enabled = merchant.isNotBlank() && (total.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text(if (existing != null) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddExpenseDialog(
    existing: ExpenseEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String) -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "Utilities") }
    var amount by remember(existing) { mutableStateOf(existing?.let { "%.2f".format(it.amountPaise / 100.0) }.orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Expense" else "Log Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Utilities", "Groceries", "Electronics", "Health").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, category, amount.toDoubleOrNull() ?: 0.0, notes) },
                enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text(if (existing != null) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddContactDialog(
    existing: PersonalContactEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var relation by remember(existing) { mutableStateOf(existing?.relationOrRole.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.phone.orEmpty()) }
    var email by remember(existing) { mutableStateOf(existing?.email.orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Contact" else "Save Important Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = relation, onValueChange = { relation = it }, label = { Text("Role / Relation (e.g. CA, Doctor)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Address") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, relation, phone, email, notes) },
                enabled = name.isNotBlank()
            ) { Text(if (existing != null) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
