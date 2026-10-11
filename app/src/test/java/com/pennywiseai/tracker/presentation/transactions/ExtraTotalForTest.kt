package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import org.junit.Test
import java.math.BigDecimal

class ExtraTotalForTest {

    private val totals = FilteredTotals(
        income = BigDecimal("1000"),
        expenses = BigDecimal("300"),
        credit = BigDecimal("250"),
        transfer = BigDecimal("400"),
        investment = BigDecimal("500"),
        netBalance = BigDecimal("700")
    )
    private val noCredit = totals.copy(credit = BigDecimal.ZERO)

    @Test
    fun creditFilter_showsCreditTotal() {
        // #893: the Credit filter used to read ₹0 / ₹0 / ₹0.
        assertEquals(ExtraTotal(ExtraTotalKind.CREDIT, BigDecimal("250")), extraTotalFor(totals, TransactionTypeFilter.CREDIT, false))
    }

    @Test
    fun allFilter_showsCreditOnlyWhenThereIsCardSpend() {
        assertEquals(ExtraTotalKind.CREDIT, extraTotalFor(totals, TransactionTypeFilter.ALL, false)?.kind)
        assertNull(extraTotalFor(noCredit, TransactionTypeFilter.ALL, false))
    }

    @Test
    fun incomeAndExpenseFilters_haveNoExtraTile() {
        assertNull(extraTotalFor(noCredit, TransactionTypeFilter.INCOME, false))
        assertNull(extraTotalFor(noCredit, TransactionTypeFilter.EXPENSE, false))
    }

    @Test
    fun transferAndInvestmentFilters_showTheirOwnTotal() {
        assertEquals(ExtraTotal(ExtraTotalKind.TRANSFER, BigDecimal("400")), extraTotalFor(totals, TransactionTypeFilter.TRANSFER, false))
        assertEquals(ExtraTotal(ExtraTotalKind.INVESTMENT, BigDecimal("500")), extraTotalFor(totals, TransactionTypeFilter.INVESTMENT, false))
    }

    @Test
    fun selection_alwaysShowsCredit_evenWhenZero() {
        // #634: the selection card always has the credit tile.
        assertEquals(ExtraTotal(ExtraTotalKind.CREDIT, BigDecimal.ZERO), extraTotalFor(noCredit, TransactionTypeFilter.TRANSFER, true))
    }

    @Test
    fun creditFilter_showsTileEvenWhenTotalIsZero() {
        // e.g. every card spend in the list is excluded from analytics.
        assertEquals(ExtraTotal(ExtraTotalKind.CREDIT, BigDecimal.ZERO), extraTotalFor(noCredit, TransactionTypeFilter.CREDIT, false))
    }
}
