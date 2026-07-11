package com.pennywiseai.parser.core

import java.math.BigDecimal

/**
 * Common interface for mandate information across all banks.
 * This allows standardized handling of subscription/mandate data
 * from different banks while maintaining bank-specific implementations.
 */
interface MandateInfo {
    /**
     * The amount that will be charged
     */
    val amount: BigDecimal

    /**
     * The next deduction date in string format
     * Date format varies by bank (e.g., "dd/MM/yy", "d-MMM-yy")
     */
    val nextDeductionDate: String?

    /**
     * The merchant/service name
     */
    val merchant: String

    /**
     * Unique Mandate Number (if available)
     * May be null for some banks or mandate types
     */
    val umn: String?

    /**
     * The date format used by this bank for parsing nextDeductionDate
     * Default is "dd/MM/yy" but can be overridden per bank
     */
    val dateFormat: String
        get() = "dd/MM/yy"

    /**
     * Last 4 digits of the account the mandate debits, if the SMS states it.
     * Null when unknown.
     */
    val accountLast4: String?
        get() = null
}