package com.example.data.repository

import com.example.data.local.AiAndBillingDao
import com.example.data.local.AiConversationEntity
import com.example.data.local.AiMessageEntity
import com.example.data.local.AiSourceEntity
import com.example.data.local.MemoryDao
import com.example.data.local.MemoryEntity
import com.example.data.local.UserDao
import com.example.data.remote.AiChatRequestDto
import com.example.data.remote.AiExtractRequestDto
import com.example.data.remote.AiExtractResponseDto
import com.example.data.remote.ChatHistoryDto
import com.example.data.remote.DynamicBackendClient
import com.example.data.remote.MemoryContextDto
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiQueryResult {
    data class Success(
        val answer: String,
        val sources: List<AiSourceEntity>,
        val relatedSuggestions: List<String>,
        val model: String?
    ) : AiQueryResult

    data class NotConfigured(val reason: String) : AiQueryResult
    data class Error(val message: String) : AiQueryResult
}

@Singleton
class AiAssistantRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val aiAndBillingDao: AiAndBillingDao,
    private val userDao: UserDao,
    private val backendClient: DynamicBackendClient
) {
    fun observeConversations(userId: String): Flow<List<AiConversationEntity>> =
        aiAndBillingDao.observeConversations(userId)

    suspend fun deleteConversation(conversationId: String) {
        aiAndBillingDao.deleteConversationById(conversationId)
    }

    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>> =
        aiAndBillingDao.observeMessages(conversationId)

    fun observeSources(conversationId: String): Flow<List<AiSourceEntity>> =
        aiAndBillingDao.observeSourcesForConversation(conversationId)

    suspend fun createConversation(userId: String, title: String): String {
        val id = "conv_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        aiAndBillingDao.upsertConversation(
            AiConversationEntity(id = id, userId = userId, title = title.take(80))
        )
        return id
    }

    /**
     * Ranks authorized non-private local memories relevant to the question and invokes the authenticated backend Groq proxy.
     */
    suspend fun askMemoryAssistant(
        userId: String,
        conversationId: String,
        question: String,
        isGuest: Boolean,
        history: List<AiMessageEntity>
    ): AiQueryResult {
        val userMsg = AiMessageEntity(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            conversationId = conversationId,
            userId = userId,
            role = "user",
            content = question
        )
        aiAndBillingDao.insertMessage(userMsg)

        if (isGuest) {
            return AiQueryResult.NotConfigured(
                "Sign in with Google to query the authenticated Groq AI backend. Local offline search remains available in your Memories tab."
            )
        }

        if (backendClient.isBackendPlaceholderUrl()) {
            return AiQueryResult.NotConfigured(
                "Backend server URL is not yet pointed to a live deployment. Set your HTTPS Node.js backend URL in Profile & Settings, and configure GROQ_API_KEY on the server."
            )
        }

        val authorizedMemories = memoryDao.getPublicMemoriesOnce(userId)
        val rankedMemories = rankMemoriesForQuery(question, authorizedMemories).take(10)
        val contextDtos = rankedMemories.map {
            MemoryContextDto(
                id = it.id,
                title = it.title,
                memoryType = it.memoryType,
                content = buildString {
                    append(it.content)
                    if (it.summary.isNotBlank()) append("\nSummary: ${it.summary}")
                    if (it.tagsCsv.isNotBlank()) append("\nTags: ${it.tagsCsv}")
                }.take(1800)
            )
        }

        return try {
            val service = backendClient.getService()
            val response = service.chat(
                AiChatRequestDto(
                    question = question,
                    memories = contextDtos,
                    history = history.takeLast(6).map { ChatHistoryDto(it.role, it.content) }
                )
            )
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string().orEmpty()
                val errCode = runCatching { JSONObject(errBody).optString("error") }.getOrDefault("")
                val errMsg = runCatching { JSONObject(errBody).optString("message") }.getOrDefault("")
                if (errCode == "GROQ_NOT_CONFIGURED") {
                    return AiQueryResult.NotConfigured(
                        errMsg.ifBlank { "GROQ_API_KEY is not configured on the backend server." }
                    )
                }
                return AiQueryResult.Error(
                    errMsg.ifBlank { "Backend AI request failed (HTTP ${response.code()})." }
                )
            }
            val body = response.body()
                ?: return AiQueryResult.Error("Empty response from AI backend.")

            val assistantMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            aiAndBillingDao.insertMessage(
                AiMessageEntity(
                    id = assistantMsgId,
                    conversationId = conversationId,
                    userId = userId,
                    role = "assistant",
                    content = body.answer,
                    tokensUsed = body.tokensUsed
                )
            )

            val memoryById = rankedMemories.associateBy { it.id }
            val sources = body.citedMemoryIds.mapNotNull { memId ->
                val mem = memoryById[memId] ?: return@mapNotNull null
                AiSourceEntity(
                    id = "src_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                    messageId = assistantMsgId,
                    memoryId = mem.id,
                    memoryTitle = mem.title,
                    snippet = mem.content.take(140)
                )
            }
            if (sources.isNotEmpty()) {
                aiAndBillingDao.insertSources(sources)
            }
            body.remainingCredits?.let { rem ->
                userDao.getUser(userId)?.let { u ->
                    userDao.upsertUser(u.copy(aiCreditsRemaining = rem))
                }
            }

            AiQueryResult.Success(
                answer = body.answer,
                sources = sources,
                relatedSuggestions = body.relatedSuggestions,
                model = body.model
            )
        } catch (e: Exception) {
            AiQueryResult.Error("Unable to reach AI backend: ${e.localizedMessage ?: "Network error"}")
        }
    }

    suspend fun extractStructuredMetadata(
        text: String,
        memoryType: String,
        userId: String? = null
    ): Result<AiExtractResponseDto> = runCatching {
        if (backendClient.isBackendPlaceholderUrl()) {
            throw IllegalStateException("Backend server URL is not configured yet.")
        }
        val response = backendClient.getService().extract(
            AiExtractRequestDto(text = text, memoryType = memoryType)
        )
        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string().orEmpty()
            val msg = runCatching { JSONObject(errBody).optString("message") }.getOrDefault("")
            throw IllegalStateException(msg.ifBlank { "Extraction failed (HTTP ${response.code()})" })
        }
        val body = response.body() ?: throw IllegalStateException("Empty extraction response")
        if (userId != null && body.remainingCredits != null) {
            userDao.getUser(userId)?.let { u ->
                userDao.upsertUser(u.copy(aiCreditsRemaining = body.remainingCredits))
            }
        }
        body
    }

    private fun rankMemoriesForQuery(query: String, memories: List<MemoryEntity>): List<MemoryEntity> {
        val tokens = query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
        if (tokens.isEmpty()) return memories.take(10)
        return memories.sortedByDescending { mem ->
            val haystack = "${mem.title} ${mem.content} ${mem.summary} ${mem.categoryName} ${mem.tagsCsv}".lowercase()
            tokens.count { haystack.contains(it) }
        }
    }
}
