package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.domain.insights.InsightFact.CardBillDue
import com.pennywiseai.tracker.domain.spending.CardBill
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CardBillInsightTest {
    private val today = LocalDate.parse("2026-11-20")

    private fun bill(due: String, amount: String = "4000", paid: String = "0", last4: String = "1111") = CardBill(
        bankName = "Test Bank", accountLast4 = last4, label = "Card $last4", currency = "INR",
        amount = BigDecimal(amount), paid = BigDecimal(paid),
        statementDate = LocalDate.parse(due).minusDays(20), dueDate = LocalDate.parse(due),
    )

    @Test
    fun `says when a card bill is due within a week, overdue first`() {
        val insights = InsightRules.cardBills(
            listOf(
                bill("2026-11-24", last4 = "1111"),
                bill("2026-11-18", paid = "1500", last4 = "2222"), // overdue, part paid
                bill("2026-12-05", last4 = "3333"), // too far off
                bill("2026-11-22", paid = "4000", last4 = "4444"), // paid off
            ),
            today,
            "2026-11-01",
        ).sortedBy { it.severity }

        assertEquals(listOf(InsightSeverity.ALERT, InsightSeverity.WARNING), insights.map { it.severity })
        val overdue = insights.first().fact as CardBillDue
        assertEquals(Money(BigDecimal("2500"), "INR"), overdue.left)
        assertEquals(-2, overdue.daysLeft)
        assertEquals(4, (insights.last().fact as CardBillDue).daysLeft)
        assertEquals(InsightAction.OpenAccount("Test Bank", "2222"), insights.first().action)
    }
}
