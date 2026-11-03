package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ArabBankParserTest {

    @TestFactory
    fun `arab bank parser handles common cases`(): List<DynamicTest> {
        val parser = ArabBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "English card spend, EGP transaction (EXPENSE)",
                message = "A Trx using Card XXXX2020 from Top Up ETISALAT Egypt for EGP 123.45 " +
                    "on 18-Jun-2026 at 13:59 GMT+3. Available balance is EGP 9876.54.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "Top Up ETISALAT Egypt",
                    accountLast4 = "2020",
                    balance = BigDecimal("9876.54"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "English card spend, USD transaction with EGP balance (EXPENSE)",
                message = "A Trx using Card XXXX2020 from PORKBUN COM for USD 9.84 " +
                    "on 05-Oct-2025 at 07:48 GMT+2. Available balance is EGP 5432.10.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.84"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "PORKBUN COM",
                    accountLast4 = "2020",
                    balance = BigDecimal("5432.10"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic credit to the card (INCOME)",
                message = "تم قيد مبلغ 500.00 جنيه لبطاقتك الائتمانية رقم #2020",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    accountLast4 = "2020",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Jordan card spend, JOD transaction (EXPENSE, 3 decimals)",
                message = "A Trx using Card XXXX9915 from ADANI CORNER for JOD 2.750 " +
                    "on 20-Jun-2026 at 14:21 GMT+3. Available balance is JOD 1649.832.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.750"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ADANI CORNER",
                    accountLast4 = "9915",
                    balance = BigDecimal("1649.832"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Jordan CliQ transfer debit (EXPENSE, suffix balance, not a card)",
                message = "JOD50.000 has been debited from 0156*500 to John Smith " +
                    "as CliQ transfer Balance 37.920JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "John Smith",
                    accountLast4 = "6500",
                    balance = BigDecimal("37.920"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Jordan CliQ transfer credit (INCOME, no-space to/from, not a card)",
                message = "JOD172.000 has been credited to 0156*500from Jane Doe " +
                    "as CliQ transfer Balance 959.370JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("172.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "Jane Doe",
                    accountLast4 = "6500",
                    balance = BigDecimal("959.370"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "OTP message is rejected",
                message = "Your OTP for Arab Bank is 123456. Do not share it with anyone.",
                sender = "ArabBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Arabic OTP message is rejected",
                // "Your verification code for Arab Bank is 123456" — رمز التحقق = verification code.
                message = "رمز التحقق الخاص بك في Arab Bank هو 123456",
                sender = "ArabBank",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "ArabBank" to true,
            "ARABBANK" to true,
            "AD-ARABBANK" to true,
            "AB-ARABBK-S" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Arab Bank Parser")
    }

    // Debit-card purchase SMS uses the bank account identifier, not the card number.
    // Synthetic account, merchant, amounts and date preserve the bank's SMS layout.
    private val accountPurchaseMessage =
        "JO\nYou have a purchase transaction on account 0123*456 from EXAMPLE SHOP CORPORATI " +
            "amount JOD 7.125 on 15/06/2026 12:34 . Balance JOD 842.375 ."

    private val accountPurchaseExpected = ExpectedTransaction(
        amount = BigDecimal("7.125"),
        currency = "JOD",
        type = TransactionType.EXPENSE,
        merchant = "EXAMPLE SHOP CORPORATI",
        accountLast4 = "3456",
        balance = BigDecimal("842.375"),
        isFromCard = false,
        // "purchase transaction on account" once saved "on" as the reference.
        expectNullReference = true
    )

    @TestFactory
    fun `arab bank debit-card purchases`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Jordan debit-card purchase with country prefix and three decimal places",
                message = accountPurchaseMessage,
                sender = "ArabBank",
                expected = accountPurchaseExpected
            ),
            ParserTestCase(
                name = "Debit-card purchase without country prefix and with grouped amount",
                message = accountPurchaseMessage.removePrefix("JO\n")
                    .replace("7.125", "1,207.125")
                    .replace("842.375", "2,842.375"),
                sender = "ArabBank",
                expected = accountPurchaseExpected.copy(
                    amount = BigDecimal("1207.125"),
                    balance = BigDecimal("2842.375")
                )
            ),
            ParserTestCase(
                name = "Debit-card purchase accepts mixed case and extra whitespace",
                message = accountPurchaseMessage.replace("purchase transaction", "PURCHASE  Transaction")
                    .replace("amount JOD", "AMOUNT   jod"),
                sender = "ArabBank",
                expected = accountPurchaseExpected
            ),
            ParserTestCase(
                name = "Debit-card purchase without amount does not use balance as amount",
                message = accountPurchaseMessage.replace("amount JOD 7.125", "amount JOD"),
                sender = "ArabBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined debit-card purchase is rejected",
                message = accountPurchaseMessage + " Transaction declined.",
                sender = "ArabBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Debit-card purchase verification is rejected",
                message = accountPurchaseMessage + " Your verification code is 123456.",
                sender = "ArabBank",
                shouldParse = false
            )
        )
        return ParserTestUtils.runTestSuite(ArabBankParser(), cases)
    }

    @TestFactory
    fun `factory resolves arab bank debit-card purchases`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "Arab Bank",
                    sender = "ArabBank",
                    currency = "EGP", // Parser default; the transaction currency is JOD.
                    message = accountPurchaseMessage,
                    expected = accountPurchaseExpected,
                    shouldHandle = true
                )
            ),
            "Arab Bank debit-card purchase factory tests"
        )
}
