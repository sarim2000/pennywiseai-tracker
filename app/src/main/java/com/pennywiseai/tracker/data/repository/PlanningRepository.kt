package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user stands against their plans: spending limits and bills coming up. Read from
 * the sources the Budgets and Subscriptions screens use, so an answer can't disagree with the
 * screen it points to. For the chat's planning_status tool, and anything else that asks.
 */
@Singleton
class PlanningRepository @Inject constructor(
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
) {
    /** The spending limits whose window holds [today], each with its own window and currency. */
    suspend fun budgets(today: LocalDate = LocalDate.now()): List<BudgetStatus> =
        budgetStatuses(budgetSummaryProvider.summary(YearMonth.from(today)).first(), today)

    /** Bills overdue, or due within [days] of [today]. */
    suspend fun billsDue(today: LocalDate = LocalDate.now(), days: Long = BILLS_AHEAD_DAYS): BillsDue =
        billsDue(subscriptionRepository.getActiveSubscriptions().first(), today, days)

    companion object {
        const val BILLS_AHEAD_DAYS = 7L

        /**
         * LIMIT groups live today with something to spend against. Targets and expected
         * amounts aren't limits: "left" means nothing for them.
         */
        internal fun budgetStatuses(summary: BudgetOverallSummary, today: LocalDate): List<BudgetStatus> = summary.groups
            .filter { g ->
                g.group.budget.groupType == BudgetGroupType.LIMIT && g.totalBudget.signum() > 0 &&
                    !today.isBefore(g.windowStart) && !today.isAfter(g.windowEnd)
            }
            .map { g -> BudgetStatus(g.group.budget.name, summary.currency, g.totalBudget, g.totalActual, g.windowStart, g.windowEnd) }

        /** Active outgoing payments with a date, by date; overdue ones apart from those still to come. */
        internal fun billsDue(subscriptions: List<SubscriptionEntity>, today: LocalDate, days: Long): BillsDue {
            val through = today.plusDays(days)
            val due = subscriptions
                .filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.EXPENSE }
                .filter { s -> s.nextPaymentDate?.let { !it.isAfter(through) } == true }
                .sortedBy { it.nextPaymentDate }
            val (overdue, upcoming) = due.partition { it.nextPaymentDate!!.isBefore(today) }
            return BillsDue(through, overdue, upcoming)
        }
    }
}
