package com.pennywiseai.tracker.data.service

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.pennywiseai.tracker.domain.service.LlmService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmService {
    
    private var llmInference: LlmInference? = null
    
    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(4096) // Match Qwen 2.5 1.5B q8 ekv4096
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inference = llmInference ?: return@withContext Result.failure(
                IllegalStateException("LLM not initialized")
            )
            
            val response = inference.generateResponse(prompt)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val inference = llmInference ?: throw IllegalStateException("LLM not initialized")
        
        Log.d("LlmServiceImpl", "Starting generateResponseAsync for prompt: ${prompt.take(50)}...")
        
        inference.generateResponseAsync(prompt) { partialResult, done ->
            Log.d("LlmServiceImpl", "Received partial result: ${partialResult.take(20)}..., done=$done")
            val sent = trySend(partialResult).isSuccess
            Log.d("LlmServiceImpl", "Sent partial result: $sent")
            
            if (done) {
                Log.d("LlmServiceImpl", "Response generation complete, closing channel")
                close()
            }
            done
        }
        
        awaitClose { 
            Log.d("LlmServiceImpl", "Flow closed")
        }
    }
    
    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            llmInference?.close()
            llmInference = null
        }
    }
    
    override fun isInitialized(): Boolean = llmInference != null
}