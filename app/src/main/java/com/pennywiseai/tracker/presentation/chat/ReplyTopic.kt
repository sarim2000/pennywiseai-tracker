package com.pennywiseai.tracker.presentation.chat

import com.pennywiseai.tracker.data.model.ReplySubject
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape

/**
 * The mascot beside the latest assistant reply: the shape of what it answered, a mood and
 * the shape's figure. Transient: it lives in [ChatUiState] only, is never saved, and goes
 * as soon as the next request starts.
 */
data class ReplyTopic(
    /** The saved reply it stands beside. */
    val messageId: String,
    val shape: MascotShape = MascotShape.Owl,
    val mood: MascotMood = MascotMood.Calm,
    /** A pie's slice share or how much of a gauge is used (0..1), or which way bars went (-1..1). */
    val value: Float = 0f,
    /**
     * A confirmed Add's rows were saved: the one badge a reply wears, by its time. Others
     * carry none, since their text already states the figure.
     */
    val saved: Boolean = false,
)

/**
 * The shape a reply answered by [tool] (a [PennyWiseTools] name) takes; the model's own words
 * are the owl. A spending figure with a [share] of all spending (a category's, or the biggest
 * one's) is a pie; a total is a coin. Budgets, with the [share] of a limit used, are a gauge;
 * bills are a bell.
 */
internal fun replyShape(tool: String?, share: Float? = null): MascotShape = when (tool) {
    PennyWiseTools.ADD_TRANSACTIONS, PennyWiseTools.DELETE_TRANSACTION, PennyWiseTools.UPDATE_TRANSACTION -> MascotShape.Receipt
    PennyWiseTools.QUERY_SPENDING -> if (share != null) MascotShape.Pie else MascotShape.Coin
    PennyWiseTools.COMPARE_SPENDING -> MascotShape.Bars
    PennyWiseTools.PLANNING_STATUS -> if (share != null) MascotShape.Gauge else MascotShape.Bell
    else -> MascotShape.Owl
}

internal fun replyTopic(subject: ReplySubject): ReplyTopic {
    val shape = replyShape(subject.tool, subject.share)
    return ReplyTopic(
        messageId = subject.messageId,
        shape = shape,
        // Happy only for rows just saved; the screen keeps it to the beat after the save.
        mood = if (subject.saved) MascotMood.Happy else MascotMood.Calm,
        value = when (shape) {
            MascotShape.Pie, MascotShape.Gauge -> subject.share ?: 0f
            MascotShape.Bars -> subject.trend.toFloat()
            else -> 0f
        },
        saved = subject.saved,
    )
}
