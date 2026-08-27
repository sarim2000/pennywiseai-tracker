package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chat_messages WHERE isSystemPrompt = 0 ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesIncludingSystem(): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE timestamp > :since ORDER BY timestamp ASC")
    fun getMessagesSince(since: Long): Flow<List<ChatMessage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun deleteMessagesBefore(before: Long)

    /**
     * Removes only the hidden system-prompt row(s), leaving every visible message intact.
     * Used to re-render the prompt (e.g. after a base-currency change) without
     * destroying the user's conversation.
     */
    @Query("DELETE FROM chat_messages WHERE isSystemPrompt = 1")
    suspend fun deleteSystemPromptMessages()
    
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesForContext(): List<ChatMessage>
}