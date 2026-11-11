package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CityUnionBankParserTest {

    @TestFactory
    fun `city union bank parses its formats`(): List<DynamicTest> = ParserTestUtils.runTestSuite(
        CityUnionBankParser(),
        listOf(
            ParserTestCase(
                name = "UPI Autopay debit",
                message = "Your account has been successfully debited with 50.00 on 2026-11-06 towards EXAMPLE TECHNOLOGY PRIVATE LIMITED UPI Autopay,0a1b2c3d4e5f60718293a4b5c6d7e8f9@ybl -CUB",
                sender = "JK-CUBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "EXAMPLE TECHNOLOGY"
                )
            ),
            ParserTestCase(
                name = "UPI transfer debit",
                message = "Your a/c no. XXXXXXXXXXX1234 is debited for Rs.111.00 on 01-09-2025 and credited to a/c no. XXXXXXXXXXX5678 (UPI Ref no 123456789012)",
                sender = "JK-CUBLTD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("111.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to A/C XX5678",
                    accountLast4 = "1234",
                    reference = "123456789012"
                )
            ),
            ParserTestCase(
                name = "UPI transfer credit",
                message = "Your a/c no. XXXXXXXXXXX1234 is credited for Rs.111.00 on 01-09-2025 and debited from a/c no. XXXXXXXXXXX5678 (UPI Ref no 123456789012)",
                sender = "JK-CUBLTD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("111.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "OTP is not a transaction",
                message = "OTP 123456 for UPI Autopay mandate. Do not share. -CUB",
                sender = "JK-CUBANK-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Autopay advance notice is not a transaction",
                message = "Your account will be debited with Rs.50.00 on 12-11-2026 towards EXAMPLE TECHNOLOGY PRIVATE LIMITED UPI Autopay. -CUB",
                sender = "JK-CUBANK-S",
                shouldParse = false
            )
        )
    )
}
