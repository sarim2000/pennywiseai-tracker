package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedUnrecognizedSmsEntity
import kotlinx.coroutines.flow.Flow

interface SharedUnrecognizedSmsRepository {
    fun observeVisible(): Flow<List<SharedUnrecognizedSmsEntity>>
    suspend fun insert(entity: SharedUnrecognizedSmsEntity): Long
    suspend fun markReported(id: Long)
    suspend fun softDelete(id: Long)
}
