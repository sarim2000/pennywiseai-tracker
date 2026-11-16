package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One of the chat's suggested prompts. */
sealed interface ChatSuggestion {
    /** [count] transactions wait for a category: opens the category picker on them, no answer. */
    data class Uncategorized(val count: Int) : ChatSuggestion

    /** A question the app answers itself; [overdue] bills, for the bills prompt to say so. */
    data class Ask(val action: QuickAction, val overdue: Int = 0) : ChatSuggestion
}

/**
 * The chat's suggested prompts, as they apply right now: what needs doing first (rows to file,
 * bills due, limits running), then the standing questions, capped so they don't crowd the
 * composer. All answered by code, so they work without the model. Changes as the rows, bills
 * and budgets behind them do.
 */
@Singleton
class ChatSuggestions @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val planningRepository: PlanningRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val cardRepository: CardRepository,
) {
    /** The prompts for [today]: bills and budgets are read against it. */
    fun observe(today: LocalDate): Flow<List<ChatSuggestion>> = combine(
        uncategorized().map { it.size }.distinctUntilChanged(),
        planningRepository.observeBillsDue(today),
        planningRepository.observeBudgets(today),
    ) { unsorted, bills, budgets -> pick(unsorted, bills, budgets) }.distinctUntilChanged()

    /**
     * Rows still to file in the profile Home shows, ignored accounts left out (#826) as every
     * list does, newest first: the picker walks these.
     */
    fun uncategorized(): Flow<List<TransactionEntity>> = combine(
        transactionRepository.observeUncategorized(),
        userPreferencesRepository.selectedProfileId,
        accountBalanceRepository.getAllLatestBalances(),
        ignoredKeys(),
    ) { rows, profileId, accounts, ignored ->
        // Ignored first and apart from the profile, as the Transactions list's filterTrackedByProfile does.
        val tracked = rows.filterNot { IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountNumber) }
        filterTransactionsByProfile(tracked, profileId, buildProfileAccountKeys(accounts))
    }

    private fun ignoredKeys(): Flow<Set<String>> = combine(
        ignoredAccountsStore.keysFlow,
        cardRepository.getAllCards(),
        accountBalanceRepository.getAllLatestBalances(),
        IgnoredAccountsStore.Companion::withLinkedCards,
    )

    companion object {
        /** More than this and they compete instead of suggesting. */
        const val MAX_SHOWN = 4

        /** Rows one "N uncategorized" tap walks, newest first: years of Others aren't one sitting. */
        const val REVIEW_MAX = 50

        internal fun pick(unsorted: Int, bills: BillsDue, budgets: List<BudgetStatus>): List<ChatSuggestion> = buildList {
            if (unsorted > 0) add(ChatSuggestion.Uncategorized(unsorted))
            if (bills.overdue.isNotEmpty() || bills.upcoming.isNotEmpty()) {
                add(ChatSuggestion.Ask(QuickAction.BILLS, overdue = bills.overdue.size))
            }
            if (budgets.isNotEmpty()) add(ChatSuggestion.Ask(QuickAction.BUDGETS))
            add(ChatSuggestion.Ask(QuickAction.MONTH_SPEND))
            add(ChatSuggestion.Ask(QuickAction.TOP_CATEGORIES))
            add(ChatSuggestion.Ask(QuickAction.COMPARE_MONTH))
        }.take(MAX_SHOWN)
    }
}
