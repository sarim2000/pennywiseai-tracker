package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class NetWorthTest {
    private fun d(v: String) = BigDecimal(v)
    private val now = LocalDateTime.of(2026, 11, 16, 12, 0)

    private fun account(
        last4: String,
        balance: String,
        type: String? = "SAVINGS",
        currency: String = "INR",
        card: Boolean = false,
        anchorAmount: String? = null,
        anchorAt: LocalDateTime? = null,
        anchorSource: String? = null,
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = d(balance),
        timestamp = now.minusDays(1), isCreditCard = card, accountType = type, currency = currency,
        anchorAmount = anchorAmount?.let(::d), anchorAt = anchorAt, anchorSource = anchorSource,
    )

    private fun held(account: AccountBalanceEntity, source: BalanceSource? = null, daysSinceUpdate: Long = 1) =
        NetWorthAccount(account, source, transactionsSince = 0, daysSinceUpdate = daysSinceUpdate)

    @Test
    fun `cards and loan accounts are taken off what's held`() {
        val total = NetWorth.totals(
            listOf(
                held(account("1111", "50000")),
                held(account("2222", "2000", type = "CASH")),
                // The full outstanding, billed and unbilled.
                held(account("3333", "12000", type = "CREDIT", card = true)),
                held(account("4444", "30000", type = "LOAN")),
            )
        ).single()
        assertEquals(d("52000"), total.assets)
        assertEquals(d("12000"), total.cards)
        assertEquals(d("30000"), total.loans)
        assertEquals(d("10000"), total.net)
    }

    @Test
    fun `owing more than is held is a negative net worth`() {
        val total = NetWorth.totals(
            listOf(held(account("1111", "1000")), held(account("4444", "5000", type = "LOAN")))
        ).single()
        assertEquals(d("-4000"), total.net)
    }

    @Test
    fun `an overdrawn account lowers assets`() {
        val total = NetWorth.totals(listOf(held(account("1111", "-250")), held(account("2222", "1000")))).single()
        assertEquals(d("750"), total.assets)
    }

    @Test
    fun `each currency is its own total, never summed`() {
        val totals = NetWorth.totals(
            listOf(
                held(account("1111", "1000", currency = "INR")),
                held(account("2222", "2000", currency = "INR")),
                held(account("3333", "500", currency = "USD")),
                held(account("4444", "200", currency = "USD", type = "CREDIT", card = true)),
            )
        )
        assertEquals(listOf("INR", "USD"), totals.map { it.currency })
        assertEquals(d("3000"), totals[0].net)
        assertEquals(d("300"), totals[1].net)
    }

    @Test
    fun `an estimated balance marks its currency`() {
        val totals = NetWorth.totals(
            listOf(
                held(account("1111", "1000"), BalanceSource.Estimated),
                held(account("2222", "50", currency = "USD"), BalanceSource.Reported(now)),
            )
        )
        assertTrue(totals.first { it.currency == "INR" }.hasEstimates)
        assertFalse(totals.first { it.currency == "USD" }.hasEstimates)
    }

    @Test
    fun `an entered balance is the source until the bank states a newer one`() {
        val entered = now.minusDays(10)
        val acct = account("1111", "900", anchorAmount = "1000", anchorAt = entered, anchorSource = "MANUAL")

        assertEquals(
            BalanceSource.Entered(d("1000"), entered, fromStatement = false),
            NetWorth.source(acct, BalanceEvidence(reportedAt = entered.minusDays(3), fixedAt = now), unanchored = false),
        )
        assertEquals(
            BalanceSource.Reported(now.minusDays(2)),
            NetWorth.source(acct, BalanceEvidence(reportedAt = now.minusDays(2), fixedAt = now.minusDays(2)), unanchored = false),
        )
    }

    @Test
    fun `a balance set from a statement says so`() {
        val at = now.minusDays(16)
        val acct = account("1111", "900", anchorAmount = "1000", anchorAt = at, anchorSource = "STATEMENT")
        val source = NetWorth.source(acct, null, unanchored = false) as BalanceSource.Entered
        assertTrue(source.fromStatement)
    }

    @Test
    fun `a balance given with a new currency reads as entered`() {
        val at = now.minusDays(3)
        val acct = account(
            "1111", "900", currency = "EUR", anchorAmount = "900", anchorAt = at,
            anchorSource = AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE,
        )
        assertEquals(
            BalanceSource.Entered(d("900"), at, fromStatement = false),
            NetWorth.source(acct, null, unanchored = false),
        )
    }

    @Test
    fun `the bank's figure is no claim once something else set the balance after it`() {
        // An older "Update balance" with no anchor recorded, after the last stated SMS.
        val evidence = BalanceEvidence(reportedAt = now.minusDays(5), fixedAt = now.minusDays(1))
        assertNull(NetWorth.source(account("1111", "900"), evidence, unanchored = false))
    }

    @Test
    fun `never pinned is an estimate, and a known anchor with no evidence is no claim`() {
        assertEquals(BalanceSource.Estimated, NetWorth.source(account("1111", "900"), null, unanchored = true))
        assertNull(NetWorth.source(account("1111", "900"), BalanceEvidence(null, now), unanchored = false))
    }

    @Test
    fun `a balance not updated for a month is stale`() {
        assertFalse(held(account("1111", "1"), daysSinceUpdate = 30).isStale)
        assertTrue(held(account("1111", "1"), daysSinceUpdate = 31).isStale)
        assertEquals(0, NetWorth.daysSince(now.plusHours(2), now))
    }

    @Test
    fun `people owed are per currency and only what's still outstanding`() {
        fun loan(direction: LoanDirection, remaining: String, currency: String = "INR", status: LoanStatus = LoanStatus.ACTIVE) =
            LoanEntity(
                personName = "Friend", direction = direction, originalAmount = d("1000"),
                remainingAmount = d(remaining), currency = currency, status = status,
            )
        val owed = NetWorth.peopleOwed(
            listOf(
                loan(LoanDirection.LENT, "300"),
                loan(LoanDirection.LENT, "200"),
                loan(LoanDirection.BORROWED, "150"),
                loan(LoanDirection.LENT, "999", status = LoanStatus.SETTLED),
                loan(LoanDirection.BORROWED, "0"),
                loan(LoanDirection.LENT, "40", currency = "USD"),
            )
        ).associateBy { it.currency }
        assertEquals(PeopleOwed("INR", d("500"), d("150")), owed["INR"])
        assertEquals(PeopleOwed("USD", d("40"), d("0")), owed["USD"])
    }
}
