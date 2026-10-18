package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.domain.usecase.landedOn
import com.pennywiseai.tracker.presentation.split.FormPerson
import com.pennywiseai.tracker.presentation.split.owedFor
import com.pennywiseai.tracker.presentation.split.SplitForm
import com.pennywiseai.tracker.presentation.split.SplitMode
import com.pennywiseai.tracker.presentation.split.preview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** #346: share math, validation and the UPI request link for a split bill. */
class SplitRulesTest {
    private fun d(v: String) = BigDecimal(v)
    private fun share(name: String, amount: String) = SplitShare(name, d(amount))

    @Test
    fun `equal split rounds down and you take the remainder so it adds up exactly`() {
        val shares = SplitRules.equalShares(d("1000"), 2, "INR")
        assertEquals(listOf(d("333.33"), d("333.33")), shares)
        assertEquals(d("333.34"), SplitRules.yourPart(d("1000"), shares))
        assertEquals(0, (shares.fold(BigDecimal.ZERO, BigDecimal::add) + d("333.34")).compareTo(d("1000")))
    }

    @Test
    fun `equal split with five people`() {
        val shares = SplitRules.equalShares(d("100"), 5, "INR")
        assertEquals(List(5) { d("16.66") }, shares)
        assertEquals(d("16.70"), SplitRules.yourPart(d("100"), shares))
    }

    @Test
    fun `equal split follows the currency's smallest unit`() {
        val shares = SplitRules.equalShares(d("1000"), 2, "JPY")
        assertEquals(listOf(d("333"), d("333")), shares)
        assertEquals(d("334"), SplitRules.yourPart(d("1000"), shares))
    }

    @Test
    fun `equal split with nobody is empty`() {
        assertTrue(SplitRules.equalShares(d("1000"), 0, "INR").isEmpty())
    }

    @Test
    fun `validation`() {
        val total = d("1000")
        assertEquals(SplitError.NO_PEOPLE, SplitRules.validate(total, emptyList()))
        assertEquals(SplitError.TOO_MANY_PEOPLE, SplitRules.validate(total, (1..6).map { share("P$it", "1") }))
        assertEquals(SplitError.BLANK_NAME, SplitRules.validate(total, listOf(share(" ", "10"))))
        assertEquals(SplitError.DUPLICATE_PERSON, SplitRules.validate(total, listOf(share("Alex", "10"), share("Alex", "20"))))
        assertEquals(SplitError.SHARE_NOT_POSITIVE, SplitRules.validate(total, listOf(share("Alex", "0"))))
        assertEquals(SplitError.OVER_AMOUNT, SplitRules.validate(total, listOf(share("Alex", "600"), share("Sam", "400.01"))))
        // Everyone else pays it all, you nothing: fine.
        assertNull(SplitRules.validate(total, listOf(share("Alex", "600"), share("Sam", "400"))))
        assertNull(SplitRules.validate(total, (1..5).map { share("P$it", "1") }))
    }

    @Test
    fun `custom amounts must add up to the total, and the remainder says by how much`() {
        val total = d("1000")
        val shares = listOf(share("Alex", "300"), share("Sam", "200"))
        assertEquals(d("100"), SplitRules.remainder(total, d("400"), shares.map { it.amount }))
        assertEquals(SplitError.NOT_BALANCED, SplitRules.validateCustom(total, d("400"), shares))
        assertEquals(d("-50"), SplitRules.remainder(total, d("550"), shares.map { it.amount }))
        assertEquals(SplitError.NOT_BALANCED, SplitRules.validateCustom(total, d("550"), shares))
        assertEquals(SplitError.YOUR_PART_NEGATIVE, SplitRules.validateCustom(total, d("-1"), shares))
        assertNull(SplitRules.validateCustom(total, d("500"), shares))
        assertNull(SplitRules.validateCustom(total, d("500.00"), shares)) // scale doesn't matter
    }

    @Test
    fun `sheet preview, equal and custom`() {
        val people = listOf(FormPerson("Alex", "250"), FormPerson("Sam", "250"))
        val equal = SplitForm(people = people).preview(d("1000"), "INR")
        assertEquals(listOf(d("333.33"), d("333.33")), equal.shares)
        assertEquals(d("333.34"), equal.yours)
        assertNull(equal.error)

        val custom = SplitForm(people = people, mode = SplitMode.CUSTOM, yourPart = "400").preview(d("1000"), "INR")
        assertEquals(listOf(d("250"), d("250")), custom.shares)
        assertEquals(d("100"), custom.remainder)
        assertEquals(SplitError.NOT_BALANCED, custom.error)

        val blank = SplitForm(people = listOf(FormPerson("Alex", "")), mode = SplitMode.CUSTOM, yourPart = "1000").preview(d("1000"), "INR")
        assertEquals(SplitError.SHARE_NOT_POSITIVE, blank.error)
    }

    @Test
    fun `request asks only for what is still owed`() {
        fun person(share: String, remaining: String?) = SplitPerson(
            LoanShareEntity(transactionId = 1, loanId = 2, amount = d(share)),
            remaining?.let {
                LoanEntity(id = 2, personName = "Alex", direction = com.pennywiseai.tracker.data.database.entity.LoanDirection.LENT,
                    originalAmount = d("500"), remainingAmount = d(it))
            }
        )
        assertEquals(d("500"), owedFor(person("500", "500")))
        assertEquals(d("100"), owedFor(person("500", "100"))) // ₹400 repaid
        assertEquals(d("500"), owedFor(person("500", "900"))) // loan also holds other bills
        assertEquals(d("0"), owedFor(person("500", "0")))
        assertEquals(d("500"), owedFor(person("500", null)))
    }

    @Test
    fun `a split bill stays an expense in its currency and at least its shares`() {
        val bill = com.pennywiseai.tracker.data.database.entity.TransactionEntity(
            id = 1, amount = d("1000"), merchantName = "Dinner", category = "Food",
            transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE,
            dateTime = java.time.LocalDateTime.of(2026, 10, 1, 20, 0), transactionHash = "h", currency = "INR",
            lentSharesTotal = d("600")
        )
        val edited = bill.copy(
            transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER,
            currency = "USD", amount = d("500"), category = "Travel"
        )
        val kept = SplitRules.keepSplitBill(bill, edited)
        assertEquals(com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE, kept.transactionType)
        assertEquals("INR", kept.currency)
        assertEquals(d("1000"), kept.amount)
        assertEquals("Travel", kept.category) // other edits go through
        assertEquals(d("700"), SplitRules.keepSplitBill(bill, bill.copy(amount = d("700"))).amount)
        // Not split: untouched.
        val plain = bill.copy(lentSharesTotal = null)
        assertEquals(edited, SplitRules.keepSplitBill(plain, edited))
    }

    @Test
    fun `a rule's type change on a split bill isn't recorded as applied`() {
        val bill = com.pennywiseai.tracker.data.database.entity.TransactionEntity(
            id = 1, amount = d("1000"), merchantName = "Dinner", category = "Food",
            transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE,
            dateTime = java.time.LocalDateTime.of(2026, 10, 1, 20, 0), transactionHash = "h", currency = "INR",
            lentSharesTotal = d("600")
        )
        val raw = bill.copy(transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME, category = "Gifts")
        fun mod(field: com.pennywiseai.tracker.domain.model.rule.TransactionField) =
            com.pennywiseai.tracker.domain.model.rule.FieldModification(field, "a", "b", com.pennywiseai.tracker.domain.model.rule.ActionType.SET)
        val apps = listOf(
            com.pennywiseai.tracker.domain.model.rule.RuleApplication(ruleId = "r1", ruleName = "Type", transactionId = "1",
                fieldsModified = listOf(mod(com.pennywiseai.tracker.domain.model.rule.TransactionField.TYPE))),
            com.pennywiseai.tracker.domain.model.rule.RuleApplication(ruleId = "r2", ruleName = "Both", transactionId = "1",
                fieldsModified = listOf(mod(com.pennywiseai.tracker.domain.model.rule.TransactionField.TYPE), mod(com.pennywiseai.tracker.domain.model.rule.TransactionField.CATEGORY))),
        )
        val kept = SplitRules.keepSplitBill(bill, raw)
        assertEquals(com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE, kept.transactionType)
        assertEquals("Gifts", kept.category)
        val landed = apps.landedOn(kept, raw)
        assertEquals(listOf("r2"), landed.map { it.ruleId })
        assertEquals(listOf(com.pennywiseai.tracker.domain.model.rule.TransactionField.CATEGORY), landed.single().fieldsModified.map { it.field })
        // Not split: everything is recorded.
        assertEquals(apps, apps.landedOn(raw, raw))
    }

    @Test
    fun `upi id format`() {
        assertTrue(UpiRequest.isValidUpiId("you@okbank"))
        assertTrue(UpiRequest.isValidUpiId("first.last-1_x@ybl"))
        assertTrue(UpiRequest.isValidUpiId(" name.2026@upi "))
        assertFalse(UpiRequest.isValidUpiId("you"))
        assertFalse(UpiRequest.isValidUpiId("you@"))
        assertFalse(UpiRequest.isValidUpiId("@bank"))
        assertFalse(UpiRequest.isValidUpiId("you@bank@x"))
        assertFalse(UpiRequest.isValidUpiId("y ou@bank"))
        assertFalse(UpiRequest.isValidUpiId("you@1bank"))
        assertFalse(UpiRequest.isValidUpiId("you&am=1@bank"))
    }

    @Test
    fun `pay link carries the amount in rupees and encodes the name and note`() {
        val uri = UpiRequest.payUri("you@okbank", "Alex Kim", d("333.3"), "Dinner & drinks at Café 9")
        assertEquals(
            "upi://pay?pa=you@okbank&pn=Alex%20Kim&am=333.30&cu=INR&tn=Dinner%20%26%20drinks%20at%20Caf%C3%A9%209",
            uri
        )
    }

    @Test
    fun `pay link leaves out a missing name and note and keeps notes short`() {
        assertEquals("upi://pay?pa=you@okbank&am=1000.00&cu=INR", UpiRequest.payUri("you@okbank", null, d("1000"), " "))
        val long = UpiRequest.payUri("you@okbank", "", d("1"), "x".repeat(80))
        assertTrue(long.endsWith("&tn=" + "x".repeat(50)))
        assertFalse(long.contains("pn="))
    }
}
