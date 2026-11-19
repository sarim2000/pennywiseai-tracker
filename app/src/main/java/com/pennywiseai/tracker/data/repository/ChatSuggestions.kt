package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One of the chat's suggested prompts: a question the app answers itself; [overdue] bills, for the bills prompt to say so. */
data class ChatSuggestion(val action: QuickAction, val overdue: Int = 0)

/**
 * The chat's suggested prompts, as they apply right now: what needs doing first (bills due,
 * budgets over or nearly spent), then the standing questions, capped so they don't crowd the
 * composer. All answered by code, so they work without the model. Changes as the bills and
 * budgets behind them do.
 */
@Singleton
class ChatSuggestions @Inject constructor(
    private val planningRepository: PlanningRepository,
) {
    /**
     * The prompts for [today]: bills and budgets are read against it. A source that fails only
     * drops its own prompt. Budgets may wait on exchange rates over the network, so bills don't
     * wait for them: their prompt joins once they're read.
     */
    fun observe(today: LocalDate): Flow<List<ChatSuggestion>> = combine(
        planningRepository.observeBillsDue(today).orElse(BillsDue(today, emptyList(), emptyList())),
        planningRepository.observeBudgets(today).onStart { emit(emptyList()) }.orElse(emptyList()),
    ) { bills, budgets -> pick(bills, budgets) }.distinctUntilChanged()

    private fun <T> Flow<T>.orElse(fallback: T): Flow<T> = catch { e ->
        android.util.Log.w("ChatSuggestions", "A suggestion source failed (${e.javaClass.simpleName})")
        emit(fallback)
    }

    companion object {
        /** Under this share of its limit left, a budget is worth a look. */
        private val LOW_LEFT = BigDecimal("0.2")

        /**
         * Over its limit or under [LOW_LEFT] of it left, like a bill that's due: a budget with
         * plenty left, or with no rate to show its limit, isn't "what needs doing now".
         */
        internal fun BudgetStatus.needsAttention(): Boolean {
            val left = remaining ?: return false
            val limit = limit ?: return false
            return left < limit * LOW_LEFT
        }

        /** More than this and they compete instead of suggesting. */
        const val MAX_SHOWN = 4

        /** The questions that always apply, answered from the rows alone: shown before anything is read. */
        val STANDING: List<ChatSuggestion> = listOf(
            ChatSuggestion(QuickAction.MONTH_SPEND),
            ChatSuggestion(QuickAction.TOP_CATEGORIES),
            ChatSuggestion(QuickAction.COMPARE_MONTH),
        )

        internal fun pick(bills: BillsDue, budgets: List<BudgetStatus>): List<ChatSuggestion> = buildList {
            if (bills.overdue.isNotEmpty() || bills.upcoming.isNotEmpty()) {
                add(ChatSuggestion(QuickAction.BILLS, overdue = bills.overdue.size))
            }
            if (budgets.any { it.needsAttention() }) add(ChatSuggestion(QuickAction.BUDGETS))
            addAll(STANDING)
        }.take(MAX_SHOWN)
    }
}
