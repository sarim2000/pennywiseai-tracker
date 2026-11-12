package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/** Synthetic amounts and identifiers, retaining observed IFIC template structure. */
class IFICBangladeshParserTest {
    private val parser = IFICBangladeshParser()
    private fun sms(action: String, balance: String? = "BDT 27,900.00 Balance",
                    date: String = "18/07/2026 at 18:42", joint: Boolean = true): String =
        listOfNotNull(date, action,
            if (action.contains("Automatic Loan Repayment")) balance else null,
            if (joint) "Joint A/C 00003***1234" else "A/C 00006***5678",
            if (!action.contains("Automatic Loan Repayment")) balance else null,
            "IFIC Helpline-16255").joinToString("\n")

    @TestFactory
    fun `observed IFIC types`(): List<DynamicTest> = ParserTestUtils.runTestSuite(
        parser,
        listOf(
            ParserTestCase(name = "monthly principal repayment", sender = "IFIC BANK", message = sms("BDT 149,000.82 Automatic Loan Repayment-Principal"),
                expected = ExpectedTransaction(amount = BigDecimal("149000.82"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Loan Principal Repayment", accountLast4 = "1234",
                    balance = BigDecimal("27900.00"))),
            ParserTestCase(name = "large principal repayment", sender = "IFIC BANK", message = sms("BDT 4,375,000.00 Automatic Loan Repayment-Principal", "BDT 23,875.18 Balance"),
                expected = ExpectedTransaction(amount = BigDecimal("4375000.00"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Loan Principal Repayment", accountLast4 = "1234",
                    balance = BigDecimal("23875.18"))),
            ParserTestCase(name = "large cash deposit, balance after account", sender = "IFIC BANK", message = sms("BDT 5,125,000.00 Cash Deposit for", "Balance: BDT 5,348,875.18"),
                expected = ExpectedTransaction(amount = BigDecimal("5125000.00"), type = TransactionType.INCOME,
                    currency = "BDT", merchant = "Cash Deposit", accountLast4 = "1234",
                    balance = BigDecimal("5348875.18"))),
            ParserTestCase(name = "ordinary cash deposit", sender = "IFIC BANK", message = sms("BDT 175,000.00 Cash Deposit for", "Balance: BDT 185,324.49"),
                expected = ExpectedTransaction(amount = BigDecimal("175000.00"), type = TransactionType.INCOME,
                    currency = "BDT", merchant = "Cash Deposit", accountLast4 = "1234",
                    balance = BigDecimal("185324.49"))),
            ParserTestCase(name = "interest posted to individual account with 12 hour time", sender = "IFIC BANK", message = sms("BDT 97.65 Interest for", "Balance: BDT 24,558.05", "01-09-2026 1:07:45 AM", false),
                expected = ExpectedTransaction(amount = BigDecimal("97.65"), type = TransactionType.INCOME,
                    currency = "BDT", merchant = "Interest", accountLast4 = "5678",
                    balance = BigDecimal("24558.05"))),
            ParserTestCase(name = "tax on interest", sender = "IFIC BANK", message = sms("BDT 160.47 Tax on Interest for", "Balance: BDT 33,324.49"),
                expected = ExpectedTransaction(amount = BigDecimal("160.47"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Tax on Interest", accountLast4 = "1234",
                    balance = BigDecimal("33324.49"))),
            ParserTestCase(name = "system debit A", sender = "IFIC BANK", message = sms("BDT 117.00 Debit (SYSTEM USE)", "Balance: BDT 34,609.99"),
                expected = ExpectedTransaction(amount = BigDecimal("117.00"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Bank Debit (System Use)", accountLast4 = "1234",
                    balance = BigDecimal("34609.99"))),
            ParserTestCase(name = "system debit B same time and amount", sender = "IFIC BANK", message = sms("BDT 117.00 Debit (SYSTEM USE)", "Balance: BDT 34,494.99"),
                expected = ExpectedTransaction(amount = BigDecimal("117.00"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Bank Debit (System Use)", accountLast4 = "1234",
                    balance = BigDecimal("34494.99"))),
            ParserTestCase(name = "system debit different amount", sender = "IFIC BANK", message = sms("BDT 139.00 Debit (SYSTEM USE)", "Balance: BDT 34,356.99"),
                expected = ExpectedTransaction(amount = BigDecimal("139.00"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Bank Debit (System Use)", accountLast4 = "1234",
                    balance = BigDecimal("34356.99"))),
            ParserTestCase(name = "interest without balance", sender = "IFIC BANK", message = sms("BDT 78.09 Interest for", null, "30-10-2026 1:34:00 AM", false),
                expected = ExpectedTransaction(amount = BigDecimal("78.09"), type = TransactionType.INCOME,
                    currency = "BDT", merchant = "Interest", accountLast4 = "5678")),
            ParserTestCase(name = "interest tax without balance", sender = "IFIC BANK", message = sms("BDT 12.75 Tax on Interest for", null, "30-10-2026 1:34:00 AM", false),
                expected = ExpectedTransaction(amount = BigDecimal("12.75"), type = TransactionType.EXPENSE,
                    currency = "BDT", merchant = "Tax on Interest", accountLast4 = "5678")),
            ParserTestCase(name = "reject incomplete OTP", sender = "IFIC BANK", message = "OTP is 00000 for BDT 175,000.00.", shouldParse = false),
            ParserTestCase(name = "reject balance-only message", sender = "IFIC BANK", message = "18/07/2026 at 18:42\nBalance: BDT 25,000.00\nJoint A/C 00003***1234", shouldParse = false),
            ParserTestCase(name = "reject malformed transaction amount", sender = "IFIC BANK", message = sms("BDT 1,22,33.00 Cash Deposit for"), shouldParse = false),
            ParserTestCase(name = "reject unsupported action", sender = "IFIC BANK", message = sms("BDT 10,000.00 Wire Refund for"), shouldParse = false),
            ParserTestCase(name = "reject wrong bank sender", sender = "CITY BANK", message = sms("BDT 149,000.82 Automatic Loan Repayment-Principal"), shouldParse = false)
        ),
        handleCases = listOf("IFIC BANK" to true, "IFICBANK" to true,
            "IFIC-BANK" to true, "CITY BANK" to false, "StanChartBD" to false)
    )

    @TestFactory
    fun `duplicate safety and timestamp semantics`(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("same-time equal debit amounts with different balances remain distinct") {
            val a = parser.parse(sms("BDT 117.00 Debit (SYSTEM USE)", "Balance: BDT 34,609.99"), "IFIC BANK", 12345L)
            val b = parser.parse(sms("BDT 117.00 Debit (SYSTEM USE)", "Balance: BDT 34,494.99"), "IFIC BANK", 12345L)
            assertNotNull(a)
            assertNotNull(b)
            assertNotEquals(a?.generateTransactionId(), b?.generateTransactionId())
        },
        DynamicTest.dynamicTest("missing balance is null; SMS timestamp preserved") {
            val actual = parser.parse(sms("BDT 78.09 Interest for", null,
                "30-10-2026 1:34:00 AM", false), "IFIC BANK", 12345L)
            assertNotNull(actual)
            assertNull(actual?.balance)
            assertEquals(12345L, actual?.timestamp)
        }
    )

    @TestFactory
    fun `factory routes IFIC Bangladesh`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(listOf(
            SimpleTestCase(
                bankName = "IFIC Bank (Bangladesh)", sender = "IFIC BANK", currency = "BDT",
                message = sms("BDT 175,000.00 Cash Deposit for", "Balance: BDT 185,324.49"),
                expected = ExpectedTransaction(amount = BigDecimal("175000.00"), type = TransactionType.INCOME,
                    currency = "BDT", merchant = "Cash Deposit", accountLast4 = "1234",
                    balance = BigDecimal("185324.49")), shouldHandle = true
            )
        ), "IFIC Bangladesh factory routing")
}
