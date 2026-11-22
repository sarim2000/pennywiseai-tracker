package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request body for creating a new SMS report
 */
@Serializable
data class CreateSmsReportRequest(
    val senderId: String,
    val message: String,
    val parsedResult: JsonObject? = null,
    val userExpected: JsonObject,  // Changed to JsonObject to match frontend
    val userNote: String? = null
)

/**
 * Response for SMS report creation
 */
@Serializable
data class SmsReportResponse(
    val success: Boolean,
    val message: String,
    val reportId: String? = null
)