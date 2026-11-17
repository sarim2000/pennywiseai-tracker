package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UpcomingPaymentsTest {

    private fun d(s: String) = BigDecimal(s)
    private fun date(s: String) = LocalDate.parse(s)
    private val today = date("2026-10-23")
    private val end = date("2026-11-21")
    private var nextId = 1L

    private fun sub(
        name: String,
        next: String?,
        amount: String = "100",
        cycle: String = "Monthly",
        bank: String? = null,
        last4: String? = null,
        state: SubscriptionState = SubscriptionState.ACTIVE,
        direction: SubscriptionDirection = SubscriptionDirection.EXPENSE,
        lastPaid: String? = null,
    ) = SubscriptionEntity(
        id = nextId++, merchantName = name, amount = d(amount), nextPaymentDate = next?.let(::date),
        billingCycle = cycle, bankName = bank, accountLast4 = last4, state = state, direction = direction,
        lastPaidAt = lastPaid?.let(::date),
    )

    private fun template(name: String, next: String, amount: String = "100", frequency: RecurringFrequency = RecurringFrequency.MONTHLY, last4: String? = null) =
        RecurringTransactionEntity(
            merchantName = name, amount = d(amount), nextDueDate = date(next), frequency = frequency,
            dayOfMonth = date(next).dayOfMonth, accountLast4 = last4, bankName = last4?.let { "Test Bank" },
        )

    private fun charge(name: String, on: String, amount: String = "100", bank: String? = null, last4: String? = null) = TransactionEntity(
        id = nextId, amount = d(amount), merchantName = name, category = "Bills", transactionType = TransactionType.EXPENSE,
        dateTime = date(on).atTime(10, 0), transactionHash = "h${nextId++}", bankName = bank, accountNumber = last4,
    )

    private val card = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "4444", balance = d("3000"),
        timestamp = today.atStartOfDay(), isCreditCard = true,
    )

    /** The card's latest bill (closed 12 October) is entered and paid off, unless [cardBills] says otherwise. */
    private fun upcoming(
        subscriptions: List<SubscriptionEntity> = emptyList(),
        recurring: List<RecurringTransactionEntity> = emptyList(),
        cardBills: List<CardBillStatus> = listOf(CardBillStatus(card, null, needsBill = false, enteredStatement = date("2026-10-12"))),
        posted: List<TransactionEntity> = emptyList(),
        on: LocalDate = today,
        isExcluded: (String?, String?) -> Boolean = { _, _ -> false },
    ) = UpcomingPayments.of(subscriptions, recurring, cardBills, listOf(card), posted, on, end, isExcluded)

    @Test
    fun `only active expense subscriptions with a date count`() {
        val bills = UpcomingPayments.subscriptions(
            listOf(
                sub("Netflix", "2026-10-28"),
                sub("No date", null),
                sub("Ended", "2026-10-28", state = SubscriptionState.ENDED),
                sub("Salary", "2026-10-28", direction = SubscriptionDirection.INCOME),
            ),
            today, end,
        )
        assertEquals(listOf("Netflix"), bills.map { it.name })
    }

    @Test
    fun `a weekly subscription counts every time it falls in the window`() {
        val bills = upcoming(subscriptions = listOf(sub("Cleaner", "2026-10-24", cycle = "Weekly")))

        assertEquals(
            listOf("2026-10-24", "2026-10-31", "2026-11-07", "2026-11-14", "2026-11-21").map(::date),
            bills.map { it.dueDate },
        )
    }

    @Test
    fun `a weekly template counts every time too, and the one due today`() {
        val bills = upcoming(recurring = listOf(template("Allowance", "2026-10-23", frequency = RecurringFrequency.WEEKLY)))

        assertEquals(5, bills.size)
        assertEquals(today, bills.first().dueDate)
    }

    @Test
    fun `a missed template is the worker's to post, the count resumes after today`() {
        val bills = upcoming(recurring = listOf(template("Rent", "2026-10-01", frequency = RecurringFrequency.WEEKLY)))

        assertEquals(listOf("2026-10-29", "2026-11-05", "2026-11-12", "2026-11-19").map(::date), bills.map { it.dueDate })
    }

    @Test
    fun `a subscription already due before today isn't counted again`() {
        assertEquals(listOf(date("2026-11-20")), upcoming(subscriptions = listOf(sub("Gym", "2026-10-20"))).map { it.dueDate })
    }

    @Test
    fun `two subscriptions from the same merchant are both counted`() {
        val bills = upcoming(
            subscriptions = listOf(
                sub("Google", "2026-10-25", amount = "130"),
                sub("Google", "2026-10-25", amount = "650"),
            )
        )

        assertEquals(listOf(d("130"), d("650")), bills.map { it.amount.amount }.sorted())
    }

    @Test
    fun `a subscription and a template for the same payment count once`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Rent", "2026-11-01", amount = "15000")),
            recurring = listOf(
                template("rent", "2026-11-01", amount = "15000"),
                template("Rent", "2026-11-02", amount = "15000"), // another day: another payment
            ),
        )

        assertEquals(listOf(date("2026-11-01"), date("2026-11-02")), bills.map { it.dueDate })
    }

    @Test
    fun `a template for a subscription payment that's already posted is paid too`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Rent", "2026-10-25", amount = "15000")),
            recurring = listOf(template("Rent", "2026-10-25", amount = "15000")),
            posted = listOf(charge("Rent", "2026-10-23", amount = "15000")),
        )

        // Next month's payment falls after payday: nothing left to set aside.
        assertTrue(bills.isEmpty())
    }

    @Test
    fun `a posted charge settles one occurrence, within a few days of it`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Cleaner", "2026-10-24", cycle = "Weekly")),
            posted = listOf(charge("Cleaner", "2026-10-22")),
        )

        assertEquals(date("2026-10-31"), bills.first().dueDate)
        assertEquals(4, bills.size)
    }

    @Test
    fun `last month's charge doesn't settle this month's payment`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Netflix", "2026-10-25")),
            posted = listOf(charge("Netflix", "2026-09-25")),
        )

        assertEquals(listOf(date("2026-10-25"), date("2026-11-25")).filter { !it.isAfter(end) }, bills.map { it.dueDate })
    }

    @Test
    fun `the charge that already paid the last cycle doesn't settle the next`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Cleaner", "2026-10-24", cycle = "Weekly", lastPaid = "2026-10-20")),
            posted = listOf(charge("Cleaner", "2026-10-20")),
        )

        assertEquals(date("2026-10-24"), bills.first().dueDate)
    }

    @Test
    fun `a same-priced charge from another account doesn't settle the subscription`() {
        val netflix = sub("Netflix", "2026-10-24", bank = "Test Bank", last4 = "1111")

        val otherAccount = upcoming(subscriptions = listOf(netflix), posted = listOf(charge("Netflix", "2026-10-23", bank = "Test Bank", last4 = "2222")))
        val ownAccount = upcoming(subscriptions = listOf(netflix), posted = listOf(charge("Netflix", "2026-10-23", bank = "Test Bank", last4 = "1111")))
        val byHand = upcoming(subscriptions = listOf(netflix), posted = listOf(charge("Netflix", "2026-10-23")))
        val otherBank = upcoming(subscriptions = listOf(netflix), posted = listOf(charge("Netflix", "2026-10-23", bank = "Other Bank")))

        assertEquals(listOf(date("2026-10-24")), otherAccount.map { it.dueDate })
        // Paid; the next one, 24 November, falls after payday.
        assertTrue(ownAccount.isEmpty())
        // A charge with no account, even from another bank, can't be told to come from this one.
        assertEquals(listOf(date("2026-10-24")), byHand.map { it.dueDate })
        assertEquals(listOf(date("2026-10-24")), otherBank.map { it.dueDate })
    }

    @Test
    fun `a subscription with no account is paid from any`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Netflix", "2026-10-24")),
            posted = listOf(charge("Netflix", "2026-10-23", bank = "Test Bank", last4 = "2222")),
        )

        assertTrue(bills.isEmpty())
    }

    @Test
    fun `a charge on an excluded account settles nothing`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Netflix", "2026-10-24")),
            posted = listOf(charge("Netflix", "2026-10-23", bank = "Other Bank", last4 = "9999")),
            isExcluded = { bank, last4 -> bank == "Other Bank" && last4 == "9999" },
        )

        assertEquals(date("2026-10-24"), bills.first().dueDate)
    }

    @Test
    fun `a card payment by the entered statement's close is inside that bill`() {
        // Entered on the statement day: what's charged that day is in it, what's charged after isn't.
        val bills = upcoming(
            subscriptions = listOf(
                sub("Spotify", "2026-10-23", bank = "Test Bank", last4 = "4444"),
                sub("Music", "2026-10-23", last4 = "4444"),
                sub("Cloud", "2026-10-24", bank = "Test Bank", last4 = "4444"),
            ),
            recurring = listOf(template("Fuel", "2026-10-23", last4 = "4444")),
            cardBills = listOf(CardBillStatus(card, null, needsBill = false, enteredStatement = today)),
        )

        assertEquals(listOf("Cloud"), bills.map { it.name })
    }

    @Test
    fun `a card payment after the entered statement is still set aside, even with that bill paid`() {
        // 4 November: October's statement (closed 5 October, due the 10th) is entered and
        // paid; November's closes tomorrow, so today's charge isn't in any bill entered yet.
        val bills = upcoming(
            subscriptions = listOf(sub("Spotify", "2026-11-04", bank = "Test Bank", last4 = "4444")),
            recurring = listOf(template("Fuel", "2026-11-06", last4 = "4444")),
            cardBills = listOf(CardBillStatus(card, null, needsBill = false, enteredStatement = date("2026-10-05"))),
            on = date("2026-11-04"),
        )

        assertEquals(listOf("Spotify", "Fuel"), bills.map { it.name })
    }

    @Test
    fun `a subscription on a card with no bill entered still counts`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Spotify", "2026-10-28", bank = "Test Bank", last4 = "4444")),
            recurring = listOf(template("Fuel", "2026-10-30", last4 = "4444")),
            cardBills = listOf(CardBillStatus(card, null, needsBill = true)),
        )

        assertEquals(listOf("Spotify", "Fuel"), bills.map { it.name })
    }

    @Test
    fun `payments from an excluded account don't count`() {
        val bills = upcoming(
            subscriptions = listOf(sub("Rent", "2026-11-01", bank = "Other Bank", last4 = "9999"), sub("Gym", "2026-11-01")),
            isExcluded = { bank, last4 -> bank == "Other Bank" && last4 == "9999" },
        )

        assertEquals(listOf("Gym"), bills.map { it.name })
    }

    @Test
    fun `card bills count with what's left, overdue ones included, until the window ends`() {
        fun bill(due: String, amount: String = "4000", paid: String = "0") = CardBill(
            "Test Bank", "4444", "Card", "INR", d(amount), d(paid), date(due).minusDays(20), date(due),
        )
        val bills = upcoming(
            cardBills = listOf(
                bill("2026-10-20", paid = "1500"), // overdue, part paid
                bill("2026-11-05"),
                bill("2026-11-10", paid = "4000"), // paid off
                bill("2026-12-02"), // after payday
            ).map { CardBillStatus(card, it, needsBill = false) }
        )

        assertEquals(listOf(Money(d("2500"), "INR"), Money(d("4000"), "INR")), bills.map { it.amount })
        assertTrue(bills.all { it.isCardBill })
    }
}
