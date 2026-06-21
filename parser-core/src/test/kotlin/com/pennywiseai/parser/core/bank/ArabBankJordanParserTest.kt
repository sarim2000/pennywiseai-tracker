package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ArabBankJordanParserTest {

    @TestFactory
    fun `Arab Bank Jordan parser handles key paths`(): List<DynamicTest> {
        val parser = ArabBankJordanParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit card purchase (JOD)",
                message = "A Trx using Card XXXX9915 from ADANI CORNER for JOD 2.750 on 20-Jun-2026 at 14:21 GMT+3. Available balance is JOD 1649.832.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.750"),
                    currency = "JOD",
                    type = TransactionType.CREDIT,
                    merchant = "ADANI CORNER",
                    accountLast4 = "9915",
                    balance = BigDecimal("1649.832"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Account debit (JOD, CliQ transfer)",
                message = "JO\nJOD50.000 has been debited from 0156*500 to JOHN MICHAEL SMITH as CliQ transfer Balance 37.920JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN MICHAEL SMITH",
                    accountLast4 = "6500",
                    balance = BigDecimal("37.920"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Account credit (JOD, CliQ transfer)",
                message = "JO\nJOD172.000 has been credited to 0156*500from EMILY ROSE CARTER as CliQ transfer Balance 959.370JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("172.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "EMILY ROSE CARTER",
                    accountLast4 = "6500",
                    balance = BigDecimal("959.370"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Account debit - non-CliQ transfer type",
                message = "JO\nJOD25.500 has been debited from 0156*500 to DAVID ALAN WRIGHT as wire transfer Balance 12.420JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.500"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "DAVID ALAN WRIGHT",
                    accountLast4 = "6500",
                    balance = BigDecimal("12.420"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Account credit - no transfer description",
                message = "JO\nJOD300.000 has been credited to 0156*500from SARAH JANE BENNETT Balance 1200.000JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "SARAH JANE BENNETT",
                    accountLast4 = "6500",
                    balance = BigDecimal("1200.000"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Credit card purchase (USD)",
                message = "A Trx using Card XXXX9915 from AMAZON.COM for USD 19.99 on 20-Jun-2026 at 14:21 GMT+3. Available balance is USD 540.18.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("19.99"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "AMAZON.COM",
                    accountLast4 = "9915",
                    balance = BigDecimal("540.18"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your Arab Bank OTP is 123456. Do not share it with anyone.",
                sender = "ArabBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined transaction is ignored",
                message = "A Trx using Card XXXX9915 from ADANI CORNER for JOD 2.750 was declined.",
                sender = "ArabBank",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "ArabBank" to true,
            "ARABBANK" to true,
            "Arab Bank" to true,
            "arabbank" to true,
            "AB-ARABBK-S" to true,
            "FAB" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Arab Bank Jordan")
    }

    @TestFactory
    fun `factory resolves Arab Bank Jordan`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Arab Bank Jordan",
                sender = "ArabBank",
                currency = "JOD",
                message = "JO\nJOD172.000 has been credited to 0156*500from EMILY ROSE CARTER as CliQ transfer Balance 959.370JOD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("172.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Arab Bank Jordan Factory Tests")
    }
}
