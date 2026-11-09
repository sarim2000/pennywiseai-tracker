package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A converter with the contract of `CurrencyConversionService.convertAmountOrNull`:
 * unchanged when the currencies match (ignoring case), `rates["FROM>TO"]` applied and
 * rounded to cents otherwise, null for a pair with no rate.
 */
internal fun fakeConvert(rates: Map<String, String> = emptyMap()): ConvertAmount = { amount, from, to ->
    if (from.equals(to, ignoreCase = true)) amount
    else rates["${from.uppercase()}>${to.uppercase()}"]
        ?.let { amount.multiply(BigDecimal(it)).setScale(2, RoundingMode.HALF_UP) }
}

private var nextTxId = 1L

/** A synthetic transaction; only the fields Home's figures read are settable. */
internal fun tx(
    amount: String,
    type: TransactionType,
    currency: String = "INR",
    date: LocalDate = LocalDate.of(2026, 3, 10),
    impact: BudgetImpactType? = null,
    budgetCategory: String? = null,
    category: String = "Food",
    excluded: Boolean = false,
    loanId: Long? = null,
    loanContribution: String? = null,
    transferPairId: String? = null,
): TransactionEntity {
    val id = nextTxId++
    return TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "Test shop",
        category = category,
        transactionType = type,
        dateTime = date.atTime(12, 0),
        transactionHash = "test-$id",
        currency = currency,
        excludedFromAnalytics = excluded,
        loanId = loanId,
        loanContribution = loanContribution?.let(::BigDecimal),
        budgetCategory = budgetCategory,
        budgetImpactType = impact,
        transferPairId = transferPairId,
    )
}

/** BigDecimal equality by value, so 5 and 5.00 match. */
internal fun assertAmount(expected: String, actual: BigDecimal) =
    assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

internal fun assertAmounts(expected: List<String>, actual: List<BigDecimal>) =
    assertEquals(expected.map(::BigDecimal).map { it.stripTrailingZeros() }, actual.map { it.stripTrailingZeros() })
