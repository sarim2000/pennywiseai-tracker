package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/** All amounts, merchants, card numbers, and account identifiers are synthetic. */
class StandardCharteredBangladeshParserTest {
    private val parser = StandardCharteredBangladeshParser()

    private fun purchase(amount: String = "1,234.50", merchant: String = "DEMO ELECTRONICS DHAKA BD",
        credit: String = "50,000.00", intl: Boolean = false): String =
        "${if (intl) "Intl. e-trxn of " else ""}BDT $amount ${if (intl) "was made" else "transaction was made"} " +
            "on card ending 1234 at $merchant on 18-Sep-26. Available credit: BDT $credit. Enquiries:16233"

    private fun account(amount: String, movement: String, balance: String): String =
        "Dear Client, BDT $amount was $movement Acc. ending xxxxxxx5678 on 02-Nov-26. " +
            "Available balance is now BDT $balance. For enquiries, please call 16233."

    private fun payment(): String =
        "Dear Client, BDT 25,000.00 has been credited to card ending 1234 on 08-Sep-26 " +
            "pertaining to PAYMENT RECD.-CASH. Available Credit: BDT 75,000.00. Enquiries:16233"

    private fun charge(): String =
        "Dear Client, BDT 347.89 has been debited from card ending 1234 on 02-Nov-26 " +
            "pertaining to FINANCE CHARGES.. Available Credit: BDT 49,654.44. Enquiries:16233"

    @TestFactory
    fun `supported transaction formats and exclusions`(): List<DynamicTest> =
        ParserTestUtils.runTestSuite(
            parser,
            listOf(
                ParserTestCase(name = "POS purchase", sender = "StanChartBD", message = purchase(),
                    expected = ExpectedTransaction(amount = BigDecimal("1234.50"), type = TransactionType.CREDIT,
                        merchant = "DEMO ELECTRONICS DHAKA BD", currency = "BDT",
                        accountLast4 = "1234", isFromCard = true)),
                ParserTestCase(name = "POS store with ampersand", sender = "StanChartBD", message = purchase("3,207.00", "GADGET & TEST DHAKA BD"),
                    expected = ExpectedTransaction(amount = BigDecimal("3207.00"), type = TransactionType.CREDIT,
                        merchant = "GADGET & TEST DHAKA BD", currency = "BDT", accountLast4 = "1234", isFromCard = true)),
                ParserTestCase(name = "international e-commerce purchase with phone", sender = "StanChartBD", message = purchase("2,915.45", "EXAMPLE *SUBSCRIPTION +14155550123 US", "47,076.65", true),
                    expected = ExpectedTransaction(amount = BigDecimal("2915.45"), type = TransactionType.CREDIT,
                        merchant = "EXAMPLE *SUBSCRIPTION +14155550123 US", currency = "BDT",
                        accountLast4 = "1234", isFromCard = true)),
                ParserTestCase(name = "credit card repayment is not income", sender = "StanChartBD", message = payment(),
                    expected = ExpectedTransaction(amount = BigDecimal("25000.00"), type = TransactionType.TRANSFER,
                        merchant = "Credit Card Payment", currency = "BDT", accountLast4 = "1234", isFromCard = true)),
                ParserTestCase(name = "credit card finance charge", sender = "StanChartBD", message = charge(),
                    expected = ExpectedTransaction(amount = BigDecimal("347.89"), type = TransactionType.CREDIT,
                        merchant = "Finance Charges", currency = "BDT", accountLast4 = "1234", isFromCard = true)),
                ParserTestCase(name = "account deposit", sender = "StanChartBD", message = account("155,000.00", "deposited to", "1,111,017.74"),
                    expected = ExpectedTransaction(amount = BigDecimal("155000.00"), type = TransactionType.INCOME,
                        currency = "BDT", accountLast4 = "5678", balance = BigDecimal("1111017.74"), isFromCard = false)),
                ParserTestCase(name = "account debit", sender = "StanChartBD", message = account("45,002.52", "debited from", "1,111,057.22"),
                    expected = ExpectedTransaction(amount = BigDecimal("45002.52"), type = TransactionType.EXPENSE,
                        currency = "BDT", accountLast4 = "5678", balance = BigDecimal("1111057.22"), isFromCard = false)),
                ParserTestCase(name = "account debit with invalid balance still parses", sender = "StanChartBD", message = account("30,020.86", "debited from", "1,1111,026.36"),
                    expected = ExpectedTransaction(amount = BigDecimal("30020.86"), type = TransactionType.EXPENSE,
                        currency = "BDT", accountLast4 = "5678", isFromCard = false)),
                ParserTestCase(name = "statement is not a transaction", sender = "StanChartBD", message = "Statement of your Credit Card ( 1234) has been sent.Closing Balance:BDT 65442.52, Min Pmt:BDT 11111.00 Due Dt: 17-Nov-26.",
                    shouldParse = false),
                ParserTestCase(name = "eTAC is not a transaction", sender = "StanChartBD", message = "Your Online Transaction to -, XXXXXXXXX9876 in Online Banking initiated. Your eTAC is 0000000000. If you have not performed this, please contact the bank.",
                    shouldParse = false),
                ParserTestCase(name = "NPS confirmation deferred to avoid potential duplicate debit", sender = "StanChartBD", message = "Dear Customer,Your NPS transfer request to DEMO BENEFICIARY amount BDT 20,100.00 has been processed successfully.",
                    shouldParse = false),
                ParserTestCase(name = "reject malformed purchase amount", sender = "StanChartBD", message = purchase("1,23,45.00"), shouldParse = false),
                ParserTestCase(name = "reject zero purchase", sender = "StanChartBD", message = purchase("0.00"), shouldParse = false),
                ParserTestCase(name = "reject other bank sender", sender = "SCBANK", message = purchase(), shouldParse = false)
            ),
            handleCases = listOf("StanChartBD" to true, "stanchartbd" to true,
                "SCBANK" to false, "StanChart" to false, "IFIC BANK" to false)
        )

    @TestFactory
    fun `credit limit and account balance have distinct semantics`(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("card balance stays null but available credit is recorded") {
            val actual = parser.parse(purchase(), "StanChartBD", 12345L)
            assertNotNull(actual)
            assertNull(actual?.balance)
            assertEquals(BigDecimal("50000.00"), actual?.creditLimit)
            assertEquals(12345L, actual?.timestamp)
        },
        DynamicTest.dynamicTest("card repayment carries available credit and is transfer") {
            val actual = parser.parse(payment(), "StanChartBD", 12345L)
            assertEquals(TransactionType.TRANSFER, actual?.type)
            assertEquals(BigDecimal("75000.00"), actual?.creditLimit)
        },
        DynamicTest.dynamicTest("malformed deposit account balance is not silently repaired") {
            val actual = parser.parse(account("100.00", "deposited to", "1,1111,057.22"), "StanChartBD", 1L)
            assertNotNull(actual)
            assertNull(actual?.balance)
        }
    )

    @TestFactory
    fun `factory routes the Bangladesh sender`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(SimpleTestCase(
                bankName = "Standard Chartered Bank (Bangladesh)", sender = "StanChartBD", currency = "BDT",
                message = purchase(),
                expected = ExpectedTransaction(amount = BigDecimal("1234.50"), type = TransactionType.CREDIT,
                    merchant = "DEMO ELECTRONICS DHAKA BD", accountLast4 = "1234", currency = "BDT", isFromCard = true),
                shouldHandle = true
            )),
            "Standard Chartered Bangladesh routing"
        )
}
