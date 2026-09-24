package com.pennywiseai.tracker.presentation.loans

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Everyone the user has lent to or borrowed from, with all their loans.
 *
 * [net] is what's still open, per currency: positive = they owe you,
 * negative = you owe them. Currencies are never mixed.
 */
data class LoanPerson(
    val name: String,
    val loans: List<LoanEntity>,
    val net: Map<String, Money>
) {
    val activeLoans: List<LoanEntity> get() = loans.filter { it.status == LoanStatus.ACTIVE }
    val hasActive: Boolean get() = loans.any { it.status == LoanStatus.ACTIVE }
}

/**
 * Groups loans by person (name compared case- and space-insensitively).
 * People with open loans come first, most recently touched first; within a
 * person, open loans come before settled ones.
 */
fun groupLoansByPerson(loans: List<LoanEntity>): List<LoanPerson> =
    loans.groupBy { it.personName.trim().lowercase() }
        .values
        .map { personLoans ->
            val sorted = personLoans.sortedWith(
                compareBy<LoanEntity> { it.status != LoanStatus.ACTIVE }
                    .thenByDescending { it.settledAt ?: it.updatedAt }
            )
            val net = sorted.filter { it.status == LoanStatus.ACTIVE }
                .sumByCurrency({ it.currency }) {
                    if (it.direction == LoanDirection.LENT) it.remainingAmount else it.remainingAmount.negate()
                }
                .filterValues { it.amount.signum() != 0 }
            LoanPerson(name = sorted.first().personName.trim(), loans = sorted, net = net)
        }
        .sortedWith(
            compareBy<LoanPerson> { !it.hasActive }
                .thenByDescending { p -> p.loans.maxOfOrNull { it.updatedAt } ?: LocalDateTime.MIN }
        )

internal val Money.isOwedToYou: Boolean get() = amount > BigDecimal.ZERO
