package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AiConversationEntity
import com.example.data.local.AiMessageEntity
import com.example.data.local.AiSourceEntity
import com.example.data.local.MemoryEntity
import com.example.ui.components.EmptyStateGlassCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SaffronPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiScreen(
    conversations: List<AiConversationEntity>,
    activeConversationId: String?,
    messages: List<AiMessageEntity>,
    sources: List<AiSourceEntity>,
    suggestions: List<String>,
    isLoading: Boolean,
    noticeMessage: String?,
    memories: List<MemoryEntity>,
    aiCreditsRemaining: Int = 20,
    aiMonthlyCredits: Long = 20L,
    onSelectConversation: (String?) -> Unit,
    onDeleteConversation: (String) -> Unit = {},
    onNewConversation: () -> Unit,
    onAskQuestion: (String) -> Unit,
    onOpenMemory: (MemoryEntity) -> Unit
) {
    var promptText by remember { mutableStateOf("") }
    val memoryMap = remember(memories) { memories.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    label = "GROQ AI",
                    color = SaffronPrimary,
                    icon = Icons.Default.AutoAwesome
                )
                StatusPill(
                    label = "$aiCreditsRemaining / $aiMonthlyCredits credits",
                    color = if (aiCreditsRemaining > 0) EmeraldSuccess else AmberWarning
                )
            }
            TextButton(
                onClick = onNewConversation,
                modifier = Modifier.testTag("ai_new_chat_button")
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Chat")
            }
        }

        if (conversations.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations, key = { it.id }) { conv ->
                    FilterChip(
                        selected = conv.id == activeConversationId,
                        onClick = { onSelectConversation(conv.id) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Conversation",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDeleteConversation(conv.id) }
                            )
                        },
                        label = { Text(conv.title, maxLines = 1) }
                    )
                }
            }
        }

        if (noticeMessage != null) {
            Spacer(Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = noticeMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyStateGlassCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "Ask Anything About Your Life Vault",
                        subtitle = "YaadRakshak answers strictly from your saved documents, receipts, warranties, and voice notes—and always cites its source."
                    )
                    Text("Suggested Queries", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { s ->
                            AssistChip(
                                onClick = { onAskQuestion(s) },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "user"
                        val msgSources = remember(sources, msg.id) {
                            sources.filter { it.messageId == msg.id }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (msgSources.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    msgSources.forEach { src ->
                                        AssistChip(
                                            onClick = {
                                                memoryMap[src.memoryId]?.let(onOpenMemory)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Description,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text("Source: ${src.memoryTitle}") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isLoading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Searching memories with Groq...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Ask about warranties, bills, IDs...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input")
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val q = promptText.trim()
                        if (q.isNotEmpty()) {
                            promptText = ""
                            onAskQuestion(q)
                        }
                    },
                    enabled = !isLoading && promptText.isNotBlank(),
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Question",
                        tint = SaffronPrimary
                    )
                }
            }
        }
    }
}
