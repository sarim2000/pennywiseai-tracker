package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.ledger.BalanceCalculator
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal

/**
 * Totals across credit cards (#1393), each kept per currency: a ₹ card and a $ card
 * never add up to one figure.
 */
object CardTotals {

    /** A card with a limit set. One with no limit (or a zero one) has no unused limit to count. */
    fun hasLimit(card: AccountBalanceEntity): Boolean = card.creditLimit?.let { it.signum() > 0 } == true

    /**
     * The limit not yet used, on the cards with a limit set. A card over its limit
     * adds nothing: it can't lower what's left on another card. Cards with no limit
     * are left out ([hasLimit]); say so next to the figure.
     */
    fun unusedLimit(
        cards: List<AccountBalanceEntity>,
        currencyOf: (AccountBalanceEntity) -> String = { it.currency },
    ): Map<String, Money> =
        cards.filter(::hasLimit).sumByCurrency(currencyOf) { card ->
            BalanceCalculator.availableCredit(card.creditLimit!!, card.balance).max(BigDecimal.ZERO)
        }

    /**
     * What's still to pay on the cards whose latest bill is entered: a paid-off bill
     * counts as zero. A card with no current bill isn't counted, as nobody knows its
     * bill; say so next to the figure.
     */
    fun unpaidBills(statuses: List<CardBillStatus>): Map<String, Money> =
        statuses.filter { it.isKnown }
            .sumByCurrency({ it.card.currency }) { it.bill?.remaining ?: BigDecimal.ZERO }
}
