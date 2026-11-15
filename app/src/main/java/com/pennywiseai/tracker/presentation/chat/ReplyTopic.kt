package com.pennywiseai.tracker.presentation.chat

import com.pennywiseai.tracker.data.model.ReplySubject
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.utils.Money

/**
 * The mascot beside the latest assistant reply: the shape of what it answered, a mood, the
 * shape's figure and an optional badge. Transient: it lives in [ChatUiState] only, is never
 * saved, and goes as soon as the next request starts.
 */
data class ReplyTopic(
    /** The saved reply it stands beside. */
    val messageId: String,
    val shape: MascotShape = MascotShape.Owl,
    val mood: MascotMood = MascotMood.Calm,
    /** A pie's slice share (0..1), or which way bars went (-1..1). */
    val value: Float = 0f,
    val badge: ReplyBadge? = null,
)

/** A small label beside the reply's mascot. It repeats the reply's text, never adds to it. */
sealed interface ReplyBadge {
    /** A confirmed Add's rows were saved. */
    data object Saved : ReplyBadge

    /** The month's spending, in its one currency. */
    data class Total(val money: Money) : ReplyBadge

    /** The month's spending is in [count] currencies, which are never added up. */
    data class Currencies(val count: Int) : ReplyBadge
}

/** The shape a reply answered by [tool] (a [PennyWiseTools] name) takes; the model's own words are the owl. */
internal fun replyShape(tool: String?): MascotShape = when (tool) {
    PennyWiseTools.ADD_TRANSACTIONS, PennyWiseTools.DELETE_TRANSACTION, PennyWiseTools.UPDATE_TRANSACTION -> MascotShape.Receipt
    PennyWiseTools.SPENDING_BY_CATEGORY -> MascotShape.Pie
    PennyWiseTools.COMPARE_SPENDING -> MascotShape.Bars
    PennyWiseTools.MONTH_SUMMARY -> MascotShape.Coin
    else -> MascotShape.Owl
}

/** A slice for a category the reply couldn't put a share on: visibly a slice, not a claim. */
private const val UNKNOWN_SHARE = 0.25f

internal fun replyTopic(subject: ReplySubject): ReplyTopic {
    val shape = replyShape(subject.tool)
    return ReplyTopic(
        messageId = subject.messageId,
        shape = shape,
        // Happy only for rows just saved; the screen keeps it to the beat after the save.
        mood = if (subject.saved) MascotMood.Happy else MascotMood.Calm,
        value = when (shape) {
            MascotShape.Pie -> subject.share ?: UNKNOWN_SHARE
            MascotShape.Bars -> subject.trend.toFloat()
            else -> 0f
        },
        badge = when {
            subject.saved -> ReplyBadge.Saved
            shape != MascotShape.Coin -> null
            // Nothing spent yet is no figure to show.
            else -> subject.monthTotal?.takeIf { it.isPositive }?.let(ReplyBadge::Total)
                ?: ReplyBadge.Currencies(subject.currencies).takeIf { subject.currencies > 1 }
        },
    )
}
