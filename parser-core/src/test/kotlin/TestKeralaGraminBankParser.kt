package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class KeralaGraminBankParserTest {
    @TestFactory
    fun `test Kerala Gramin Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = KeralaGraminBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Kerala Gramin Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // UPI Debit
            ParserTestCase(
                name = "UPI Debit Transfer",
                message = """Your a/c no. XXXX12345 is debited for Rs.160.00 on 28/7/25 05:06 PM and credited to a/c no. XXXXX00019 (UPI Ref no 170632692557)-Kerala Gramin Bank""",
                sender = "AD-KGBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("160.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "UPI Transfer",
                    accountLast4 = "2345",
                    reference = "170632692557"
                )
            ),

            // UPI Credit from phone number
            ParserTestCase(
                name = "UPI Credit from Phone",
                message = """Dear Customer, Account XXXX123 is credited with INR 3000 on 20-10-2025 08:15:26 from 7025784485@upi. UPI Ref. no. 529807237409-Kerala Gramin Bank""",
                sender = "BX-KGBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "UPI Payment",
                    accountLast4 = "0123",
                    reference = "529807237409"
                )
            ),

            // UPI Credit from UPI ID
            ParserTestCase(
                name = "UPI Credit from UPI ID",
                message = """Dear Customer, Account XXXX5678 is credited with INR 500 on 15-10-2025 10:30:00 from merchant@paytm. UPI Ref. no. 123456789012-Kerala Gramin Bank""",
                sender = "AD-KGBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "merchant",
                    accountLast4 = "5678",
                    reference = "123456789012"
                )
            ),

            // Larger UPI debit
            ParserTestCase(
                name = "Larger UPI Debit",
                message = """Your a/c no. XXXX9876 is debited for Rs.1,250.50 on 01/8/25 03:15 PM and credited to a/c no. XXXXX11111 (UPI Ref no 987654321098)-Kerala Gramin Bank""",
                sender = "BX-KGBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.50"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "UPI Transfer",
                    accountLast4 = "9876",
                    reference = "987654321098"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "AD-KGBANK-S" to true,
            "BX-KGBANK-S" to true,
            "KGBANK" to true,
            "kgbank" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Kerala Gramin Bank Parser Tests"
        )

    }
}
