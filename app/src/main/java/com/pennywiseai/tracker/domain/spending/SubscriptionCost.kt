package com.pennywiseai.tracker.domain.spending

import java.math.BigDecimal
import java.math.RoundingMode

/** What a subscription costs per month, so charges on different cycles can be totalled. */
object SubscriptionCost {

    /**
     * [amount] billed every [billingCycle], expressed per month. Cycle strings
     * match [com.pennywiseai.tracker.data.repository.SubscriptionRepository.advance];
     * an unknown cycle is treated as monthly, as it is there.
     */
    fun monthlyEquivalent(amount: BigDecimal, billingCycle: String): BigDecimal {
        val (times, per) = when (billingCycle.uppercase()) {
            "WEEKLY" -> 52 to 12
            "QUARTERLY" -> 1 to 3
            "SEMI-ANNUAL", "SEMI ANNUAL", "SEMIANNUAL" -> 1 to 6
            "ANNUAL", "YEARLY" -> 1 to 12
            else -> 1 to 1
        }
        return amount.multiply(BigDecimal(times)).divide(BigDecimal(per), 2, RoundingMode.HALF_UP)
    }
}
