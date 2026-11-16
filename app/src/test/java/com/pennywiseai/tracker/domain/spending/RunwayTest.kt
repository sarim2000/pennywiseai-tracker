package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.insights.InsightFact.MoneyLasts
import com.pennywiseai.tracker.domain.insights.InsightRules
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class RunwayTest {
    private fun d(v: String) = BigDecimal(v)

    private fun account(
        last4: String,
        balance: String,
        type: String? = "SAVINGS",
        currency: String = "INR",
        card: Boolean = false,
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = d(balance),
        timestamp = LocalDateTime.of(2026, 11, 1, 9, 0), isCreditCard = card, accountType = type, currency = currency,
    )

    private fun usual(currency: String, spent: String, income: String = "0") =
        currency to CycleAverages(currency, d(income), d(spent))

    @Test
    fun `adds up savings, current and cash, never cards or loans`() {
        val runway = Runway.calculate(
            listOf(
                account("1111", "30000"),
                account("2222", "10000", type = "CURRENT"),
                account("3333", "2000", type = "CASH"),
                // An account with no type yet is a bank account.
                account("4444", "3000", type = null),
                account("5555", "50000", type = "CREDIT", card = true),
                account("6666", "900000", type = "LOAN"),
            ),
            mapOf(usual("INR", "20000")),
            emptySet(),
        ).single()
        assertEquals(d("45000"), runway.balance)
        assertEquals(d("2.2"), runway.months)
        assertFalse(runway.lowConfidence)
    }

    @Test
    fun `no usual spending means no runway, never an endless one`() {
        val accounts = listOf(account("1111", "30000"))
        assertTrue(Runway.calculate(accounts, emptyMap(), emptySet()).isEmpty())
        assertTrue(Runway.calculate(accounts, mapOf(usual("INR", "0")), emptySet()).isEmpty())
    }

    @Test
    fun `each currency against its own spending`() {
        val runways = Runway.calculate(
            listOf(account("1111", "60000"), account("2222", "500", currency = "USD")),
            mapOf(usual("INR", "10000"), usual("USD", "1000")),
            emptySet(),
        ).associateBy { it.currency }
        assertEquals(d("6.0"), runways.getValue("INR").months)
        assertEquals(d("0.5"), runways.getValue("USD").months)
    }

    @Test
    fun `overdrawn is zero months, and rounding never overstates`() {
        val overdrawn = Runway.calculate(listOf(account("1111", "-500")), mapOf(usual("INR", "1000")), emptySet()).single()
        assertEquals(d("0.0"), overdrawn.months)
        val almost = Runway.calculate(listOf(account("1111", "5999")), mapOf(usual("INR", "1000")), emptySet()).single()
        assertEquals(d("5.9"), almost.months)
    }

    @Test
    fun `an estimated balance lowers confidence`() {
        val runway = Runway.calculate(
            listOf(account("1111", "1000"), account("2222", "1000")),
            mapOf(usual("INR", "1000")),
            setOf("Test Bank" to "2222"),
        ).single()
        assertTrue(runway.lowConfidence)
    }

    @Test
    fun `insight warns under three months and cheers from six`() {
        fun severityFor(balance: String) = InsightRules.runway(
            listOf(Runway("INR", d(balance), d("1000"), lowConfidence = false)),
            "2026-11-01",
        ).singleOrNull()?.severity

        assertEquals(InsightSeverity.WARNING, severityFor("2400"))
        assertEquals(InsightSeverity.WARNING, severityFor("0"))
        assertEquals(null, severityFor("4000"))
        assertEquals(InsightSeverity.GOOD, severityFor("6000"))

        val insight = InsightRules.runway(listOf(Runway("INR", d("2400"), d("1000"), true)), "2026-11-01").single()
        assertEquals("runway:v1:2026-11-01:INR:held", insight.id)
        val fact = insight.fact as MoneyLasts
        assertEquals(d("2.4"), fact.months)
        assertEquals("INR", fact.balance.currency)
        assertTrue(fact.lowConfidence)
    }
}
