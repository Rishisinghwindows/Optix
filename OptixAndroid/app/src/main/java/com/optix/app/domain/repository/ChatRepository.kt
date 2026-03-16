package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.ChatContext
import com.optix.app.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat operations with Optixia AI
 */
interface ChatRepository {

    /**
     * Send a quick chat message (stateless, no session)
     * @param message The user's message
     * @param context Optional market context for contextual responses
     * @return Flow emitting the AI response string
     */
    suspend fun quickChat(
        message: String,
        context: ChatContext? = null
    ): Flow<Resource<String>>

    /**
     * Send a message with session management
     * @param message The user's message
     * @param sessionId Optional session ID for conversation continuity
     * @param context Optional market context
     * @return Flow emitting the AI response and session ID
     */
    suspend fun sendMessage(
        message: String,
        sessionId: String? = null,
        context: ChatContext? = null
    ): Flow<Resource<Pair<String, String>>>

    /**
     * Get all chat sessions for the current user
     * @return Flow emitting list of chat sessions
     */
    suspend fun getSessions(): Flow<Resource<List<ChatSession>>>

    /**
     * Delete a chat session
     * @param sessionId The session ID to delete
     * @return Flow emitting success/failure
     */
    suspend fun deleteSession(sessionId: String): Flow<Resource<Unit>>
}
