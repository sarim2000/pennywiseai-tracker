package com.pennywiseai.tracker.domain.conscience

import kotlinx.serialization.Serializable

enum class ConscienceReaction { LIKED, RETIRED }

/**
 * A line the conscience said, for the screen's recent remarks. [text] is the
 * rendered line: no amount or merchant can be in it (see [ConscienceLines]).
 * [notified] is false when it was kept quiet, late at night with the screen off.
 */
@Serializable
data class ConscienceRemark(
    val id: Long,
    val atMillis: Long,
    val text: String,
    val kind: ConscienceKind,
    val lineKey: String,
    val notified: Boolean = true,
    val reaction: ConscienceReaction? = null,
)

object ConscienceRemarks {
    const val MAX = 20
    private const val MAX_AGE_MILLIS = 30 * 24 * 60 * 60 * 1000L

    /** Only remarks younger than 30 days exist: older ones are neither shown nor rated. */
    fun fresh(remarks: List<ConscienceRemark>, nowMillis: Long): List<ConscienceRemark> =
        remarks.filter { nowMillis - it.atMillis < MAX_AGE_MILLIS }

    /** Newest first, at most [MAX], none older than 30 days. */
    fun add(remarks: List<ConscienceRemark>, remark: ConscienceRemark, nowMillis: Long): List<ConscienceRemark> =
        fresh(listOf(remark) + remarks.filter { it.id != remark.id }, nowMillis).take(MAX)

    /**
     * The first reaction to a remark wins; null when there's no such remark or
     * it already has one. Returns the updated remarks and feedback.
     */
    fun react(
        remarks: List<ConscienceRemark>,
        feedback: ConscienceFeedback,
        remarkId: Long,
        reaction: ConscienceReaction,
    ): Pair<List<ConscienceRemark>, ConscienceFeedback>? {
        val remark = remarks.firstOrNull { it.id == remarkId } ?: return null
        if (remark.reaction != null) return null
        val updated = remarks.map { if (it.id == remarkId) it.copy(reaction = reaction) else it }
        val key = remark.lineKey
        val newFeedback = when (reaction) {
            ConscienceReaction.LIKED -> feedback.copy(liked = feedback.liked + key, retired = feedback.retired - key)
            ConscienceReaction.RETIRED -> feedback.copy(liked = feedback.liked - key, retired = feedback.retired + key)
        }
        return updated to newFeedback
    }
}
