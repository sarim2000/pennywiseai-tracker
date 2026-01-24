package com.example.feedback

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class FeedbackItemType {
    BUG,
    FEATURE
}

enum class FeedbackSource {
    ANDROID_APP,
    WEB_TOOL
}

enum class FeedbackStatus {
    PENDING,
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED
}

@Serializable
data class FeedbackItem(
    val id: String,
    val itemType: FeedbackItemType,
    val source: FeedbackSource,
    val title: String,
    val description: String,
    val status: FeedbackStatus,
    val voteCount: Int,
    val hasVoted: Boolean = false,
    val metadata: JsonObject? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateFeedbackRequest(
    val itemType: String,
    val source: String,
    val title: String,
    val description: String,
    val metadata: JsonObject? = null
)

@Serializable
data class FeedbackResponse(
    val success: Boolean,
    val message: String,
    val feedbackId: String? = null
)

@Serializable
data class VoteResponse(
    val success: Boolean,
    val message: String,
    val newVoteCount: Int = 0,
    val hasVoted: Boolean = false
)

@Serializable
data class FeedbackListResponse(
    val items: List<FeedbackItem>,
    val total: Int
)

data class RateLimitInfo(
    val fingerprint: String,
    val submissionCount: Int,
    val lastSubmissionAt: Long
)
