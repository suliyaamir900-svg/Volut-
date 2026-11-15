package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.FolderEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.MemoryTypes
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.FreeTierAdBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SkyInfo
import com.example.ui.viewmodel.EntitlementUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesVaultScreen(
    memories: List<MemoryEntity>,
    folders: List<FolderEntity>,
    searchQuery: String,
    selectedType: String,
    ocrBusy: Boolean,
    isGuest: Boolean,
    entitlement: EntitlementUiState = EntitlementUiState(),
    initialTimelineMode: Boolean = false,
    onSearchChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    onOpenUpgrade: () -> Unit = {},
    onSaveManualMemory: (title: String, content: String, type: String, category: String, tags: String, folderId: String?, isPrivate: Boolean, enrichAi: Boolean) -> Unit,
    onUpdateManualMemory: (existingId: String, title: String, content: String, type: String, category: String, tags: String, folderId: String?, isPrivate: Boolean, enrichAi: Boolean) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onImportUri: (android.net.Uri, String, String, Boolean) -> Unit,
    onScannedImageFile: (File, String, String) -> Unit,
    onSaveVoiceMemory: (title: String, transcript: String, audioPath: String, durationMs: Long) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    var timelineMode by remember(initialTimelineMode) { mutableStateOf(initialTimelineMode) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var showCaptureMenu by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var selectedMemory by remember { mutableStateOf<MemoryEntity?>(null) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri, "", MemoryTypes.DOCUMENT, false)
        }
    }

    // Zero-permission Storage Access Framework PDF/Document Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri, "", MemoryTypes.DOCUMENT, false)
        }
    }

    val filtered = remember(memories, searchQuery, selectedType, selectedFolderId) {
        memories.filter { !it.isPrivate }
            .filter { selectedType == "ALL" || it.memoryType == selectedType }
            .filter { selectedFolderId == null || it.folderId == selectedFolderId }
            .filter {
                if (searchQuery.isBlank()) true
                else {
                    val q = searchQuery.lowercase()
                    it.title.lowercase().contains(q) ||
                        it.content.lowercase().contains(q) ||
                        it.summary.lowercase().contains(q) ||
                        it.tagsCsv.lowercase().contains(q) ||
                        it.categoryName.lowercase().contains(q)
                }
            }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCaptureMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("memories_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search vault offline...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("memories_search_input")
                )
                IconButton(
                    onClick = { timelineMode = !timelineMode },
                    modifier = Modifier.testTag("toggle_timeline_button")
                ) {
                    Icon(
                        imageVector = if (timelineMode) Icons.Default.ViewAgenda else Icons.Default.Timeline,
                        contentDescription = if (timelineMode) "Card View" else "Timeline View",
                        tint = SaffronPrimary
                    )
                }
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = SkyInfo)
                }
            }

            if (ocrBusy) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing OCR & indexing...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(10.dp))
            val types = listOf("ALL", MemoryTypes.NOTE, MemoryTypes.DOCUMENT, MemoryTypes.RECEIPT, MemoryTypes.WARRANTY, MemoryTypes.VOICE)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { t ->
                    FilterChip(
                        selected = selectedType == t,
                        onClick = { onTypeChange(t) },
                        label = { Text(t.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (folders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("All Folders") }
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = {
                                selectedFolderId = if (selectedFolderId == folder.id) null else folder.id
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete Folder",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { folderToDelete = folder }
                                )
                            },
                            label = { Text(folder.name) }
                        )
                    }
                }
            }

            if (!entitlement.adFree) {
                Spacer(Modifier.height(6.dp))
                FreeTierAdBanner(
                    planName = entitlement.planName,
                    onUpgradeClick = onOpenUpgrade
                )
            }

            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyStateGlassCard(
                    icon = Icons.Default.Description,
                    title = "No Memories Found",
                    subtitle = "Tap + to scan a document with CameraX OCR, import a PDF or photo, record a voice memo, or write a note.",
                    actionLabel = "Add Memory",
                    onAction = { showCaptureMenu = true }
                )
            } else if (timelineMode) {
                val grouped = remember(filtered) {
                    filtered.groupBy { dateFormatter.format(Date(it.createdAtMillis)) }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    grouped.forEach { (dateLabel, itemsForDay) ->
                        item(key = "header_$dateLabel") {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = SaffronPrimary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(itemsForDay, key = { it.id }) { mem ->
                            MemoryItemCard(mem = mem, onClick = { selectedMemory = mem })
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filtered, key = { it.id }) { mem ->
                        MemoryItemCard(mem = mem, onClick = { selectedMemory = mem })
                    }
                }
            }
        }
    }

    if (showCaptureMenu) {
        ModalBottomSheet(onDismissRequest = { showCaptureMenu = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Capture Into Memory Vault", style = MaterialTheme.typography.headlineSmall)
                GradientPrimaryButton(
                    text = "Scan Document (CameraX + OCR)",
                    icon = Icons.Default.DocumentScanner,
                    onClick = {
                        showCaptureMenu = false
                        showScannerDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "capture_scan_doc"
                )
                OutlinedButton(
                    onClick = {
                        showCaptureMenu = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import Image (Android Photo Picker + OCR)")
                }
                OutlinedButton(
                    onClick = {
                        showCaptureMenu = false
                        documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import PDF / Document (SAF)")
                }
                OutlinedButton(
                    onClick = {
                        showCaptureMenu = false
                        showVoiceDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record Voice Memory (Speech-to-Text)")
                }
                OutlinedButton(
                    onClick = {
                        showCaptureMenu = false
                        showManualDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Write Manual Memory / Note")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showManualDialog || editingMemory != null) {
        val currentEdit = editingMemory
        ManualMemoryDialog(
            folders = folders,
            isGuest = isGuest,
            existingMemory = currentEdit,
            onDismiss = {
                showManualDialog = false
                editingMemory = null
            },
            onSave = { title, content, type, cat, tags, fId, priv, enrich ->
                if (currentEdit != null) {
                    onUpdateManualMemory(currentEdit.id, title, content, type, cat, tags, fId, priv, enrich)
                } else {
                    onSaveManualMemory(title, content, type, cat, tags, fId, priv, enrich)
                }
                showManualDialog = false
                editingMemory = null
            }
        )
    }

    if (showScannerDialog) {
        DocumentScannerDialog(
            onDismiss = { showScannerDialog = false },
            onCaptured = { file, title, type ->
                onScannedImageFile(file, title, type)
                showScannerDialog = false
            }
        )
    }

    if (showVoiceDialog) {
        VoiceMemoryDialog(
            onDismiss = { showVoiceDialog = false },
            onSave = { title, transcript, audioPath, durationMs ->
                onSaveVoiceMemory(title, transcript, audioPath, durationMs)
                showVoiceDialog = false
            }
        )
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Vault Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateFolder(folderName)
                    showNewFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    folderToDelete?.let { fld ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder?") },
            text = { Text("Remove folder '${fld.name}'? Your memories inside remain safely in your vault.") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedFolderId == fld.id) selectedFolderId = null
                    onDeleteFolder(fld.id)
                    folderToDelete = null
                }) { Text("Delete", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            }
        )
    }

    selectedMemory?.let { mem ->
        AlertDialog(
            onDismissRequest = { selectedMemory = null },
            title = { Text(mem.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(label = mem.memoryType, color = SaffronPrimary)
                        StatusPill(label = mem.categoryName, color = SkyInfo)
                        StatusPill(label = mem.syncStatus, color = EmeraldSuccess)
                    }
                    if (mem.summary.isNotBlank()) {
                        Text("AI Summary: ${mem.summary}", style = MaterialTheme.typography.labelLarge, color = SaffronPrimary)
                    }
                    Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                    if (mem.tagsCsv.isNotBlank()) {
                        Text("Tags: ${mem.tagsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        editingMemory = mem
                        selectedMemory = null
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    TextButton(onClick = { selectedMemory = null }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteMemory(mem.id)
                    selectedMemory = null
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = RoseAlert)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = RoseAlert)
                }
            }
        )
    }
}

@Composable
fun MemoryItemCard(mem: MemoryEntity, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
        if (mem.tagsCsv.isNotBlank() || mem.isPrivate) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mem.isPrivate) StatusPill(label = "ENCRYPTED", color = RoseAlert, icon = Icons.Default.Lock)
                StatusPill(label = mem.categoryName, color = SkyInfo)
            }
        }
    }
}

@Composable
private fun ManualMemoryDialog(
    folders: List<FolderEntity>,
    isGuest: Boolean,
    existingMemory: MemoryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String?, Boolean, Boolean) -> Unit
) {
    var title by remember(existingMemory) { mutableStateOf(existingMemory?.title.orEmpty()) }
    var content by remember(existingMemory) { mutableStateOf(existingMemory?.content.orEmpty()) }
    var memoryType by remember(existingMemory) { mutableStateOf(existingMemory?.memoryType ?: MemoryTypes.NOTE) }
    var category by remember(existingMemory) { mutableStateOf(existingMemory?.categoryName ?: "General") }
    var tags by remember(existingMemory) { mutableStateOf(existingMemory?.tagsCsv.orEmpty()) }
    var selectedFolderId by remember(existingMemory) { mutableStateOf(existingMemory?.folderId ?: folders.firstOrNull()?.id) }
    var isPrivate by remember(existingMemory) { mutableStateOf(existingMemory?.isPrivate ?: false) }
    var enrichAi by remember(existingMemory) { mutableStateOf(!isGuest && existingMemory == null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingMemory != null) "Edit Memory" else "New Personal Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_memory_title")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Details / Notes / Document Text") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("manual_memory_content")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(MemoryTypes.NOTE, MemoryTypes.DOCUMENT, MemoryTypes.RECEIPT, MemoryTypes.WARRANTY)) { t ->
                        FilterChip(selected = memoryType == t, onClick = { memoryType = t }, label = { Text(t) })
                    }
                }
                if (folders.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(folders, key = { it.id }) { fld ->
                            FilterChip(
                                selected = selectedFolderId == fld.id,
                                onClick = { selectedFolderId = if (selectedFolderId == fld.id) null else fld.id },
                                label = { Text(fld.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text("Encrypt in Private Vault", style = MaterialTheme.typography.bodyMedium)
                }
                if (!isGuest && !isPrivate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enrichAi, onCheckedChange = { enrichAi = it })
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = SaffronPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-summarize & tag via Groq", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, content, memoryType, category, tags, selectedFolderId, isPrivate, enrichAi)
                },
                enabled = title.isNotBlank() || content.isNotBlank(),
                modifier = Modifier.testTag("save_manual_memory_button")
            ) { Text(if (existingMemory != null) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DocumentScannerDialog(
    onDismiss: () -> Unit,
    onCaptured: (File, String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var docTitle by remember { mutableStateOf("Scanned Document") }
    var docType by remember { mutableStateOf(MemoryTypes.DOCUMENT) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Scanner (CameraX + OCR)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = docType == MemoryTypes.DOCUMENT, onClick = { docType = MemoryTypes.DOCUMENT }, label = { Text("Document") })
                    FilterChip(selected = docType == MemoryTypes.RECEIPT, onClick = { docType = MemoryTypes.RECEIPT }, label = { Text("Receipt") })
                }
                if (!hasPermission) {
                    Text("Camera permission is needed to scan physical documents and receipts.", style = MaterialTheme.typography.bodyMedium)
                    GradientPrimaryButton(
                        text = "Grant Camera Permission",
                        icon = Icons.Default.CameraAlt,
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder().build()
                                        imageCapture = capture
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    }.onFailure {
                                        captureError = "Camera unavailable on this device."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    captureError?.let {
                        Text(it, color = RoseAlert, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            if (hasPermission) {
                TextButton(onClick = {
                    val capture = imageCapture ?: return@TextButton
                    val outDir = File(context.filesDir, "scans").apply { mkdirs() }
                    val outFile = File(outDir, "scan_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(outFile, docTitle, docType)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                captureError = exception.localizedMessage
                            }
                        }
                    )
                }) { Text("Capture & Run OCR") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun VoiceMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long) -> Unit
) {
    val context = LocalContext.current
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasAudioPerm = it }

    var title by remember { mutableStateOf("Voice Memory") }
    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var recognizerError by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    fun startSpeechCapture() {
        val sr = speechRecognizer
        if (sr == null) {
            recognizerError = "On-device SpeechRecognizer unavailable; type your voice memo notes below."
            return
        }
        recognizerError = null
        startedAt = System.currentTimeMillis()
        isListening = true
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                recognizerError = "Speech capture ended (code $error). You can edit transcript below."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    transcript = if (transcript.isBlank()) text else "$transcript $text"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) transcript = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.startListening(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!hasAudioPerm) {
                    GradientPrimaryButton(
                        text = "Grant Microphone Permission",
                        icon = Icons.Default.Mic,
                        onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    GradientPrimaryButton(
                        text = if (isListening) "Stop Listening" else "Start Live Transcription",
                        icon = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                            } else {
                                startSpeechCapture()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                recognizerError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                }
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("Transcript") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dur = if (startedAt > 0L) (System.currentTimeMillis() - startedAt).coerceAtLeast(1000L) else 1000L
                    onSave(title, transcript, "", dur)
                },
                enabled = transcript.isNotBlank()
            ) { Text("Save Voice Note") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PrivateVaultScreen(
    memories: List<MemoryEntity>,
    isUnlocked: Boolean,
    biometricEnabled: Boolean,
    hasAppPin: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onLockVault: () -> Unit,
    onSavePrivateNote: (String, String) -> Unit,
    onUpdatePrivateNote: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteMemory: (String) -> Unit
) {
    var showAddPrivate by remember { mutableStateOf(false) }
    var editingPrivate by remember { mutableStateOf<MemoryEntity?>(null) }

    if (!isUnlocked) {
        if (hasAppPin || biometricEnabled) {
            AppLockGateScreen(
                biometricEnabled = biometricEnabled,
                onVerifyPin = { pin ->
                    val ok = onVerifyPin(pin)
                    if (ok) onUnlocked()
                    ok
                },
                onBiometricSuccess = onUnlocked,
                title = "Unlock Private Vault"
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateGlassCard(
                    icon = Icons.Default.Lock,
                    title = "Private Vault (AES-256-GCM)",
                    subtitle = "Records marked Private are encrypted with Android Keystore hardware-backed AES-256-GCM keys. Set a Vault PIN in Profile for extra lock protection, or unlock for this session.",
                    actionLabel = "Unlock Private Vault",
                    onAction = onUnlocked
                )
            }
        }
        return
    }

    val privateItems = remember(memories) { memories.filter { it.isPrivate } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(label = "AES-256-GCM UNLOCKED", color = EmeraldSuccess, icon = Icons.Default.LockOpen)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLockVault) { Text("Lock") }
                TextButton(onClick = { showAddPrivate = true }) { Text("+ Add Secret") }
            }
        }
        if (privateItems.isEmpty()) {
            EmptyStateGlassCard(
                icon = Icons.Default.Lock,
                title = "No Encrypted Secrets Yet",
                subtitle = "Store passport numbers, insurance policies, recovery codes, or sensitive notes encrypted via Android Keystore.",
                actionLabel = "Add Private Record",
                onAction = { showAddPrivate = true }
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(privateItems, key = { it.id }) { mem ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                mem.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(onClick = { editingPrivate = mem }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Private Record", tint = SaffronPrimary)
                                }
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Private Record", tint = RoseAlert)
                                }
                            }
                        }
                        Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showAddPrivate || editingPrivate != null) {
        val current = editingPrivate
        var title by remember(current) { mutableStateOf(current?.title.orEmpty()) }
        var secret by remember(current) { mutableStateOf(current?.content.orEmpty()) }
        AlertDialog(
            onDismissRequest = {
                showAddPrivate = false
                editingPrivate = null
            },
            title = { Text(if (current != null) "Edit Encrypted Record" else "Add Encrypted Private Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Label") }, singleLine = true)
                    OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("Encrypted Content") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (current != null) {
                            onUpdatePrivateNote(current.id, title, secret)
                        } else {
                            onSavePrivateNote(title, secret)
                        }
                        showAddPrivate = false
                        editingPrivate = null
                    },
                    enabled = title.isNotBlank() && secret.isNotBlank()
                ) { Text("Encrypt & Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddPrivate = false
                    editingPrivate = null
                }) { Text("Cancel") }
            }
        )
    }
}
