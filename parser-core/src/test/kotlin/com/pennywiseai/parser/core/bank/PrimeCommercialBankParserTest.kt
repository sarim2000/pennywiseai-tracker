package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PrimeCommercialBankParserTest {

    @TestFactory
    fun `prime commercial bank parser handles real debit and credit sms examples`() = ParserTestUtils.runTestSuite(
        parser = PrimeCommercialBankParser(),
        testCases = listOf(
            ParserTestCase(
                name = "Debit transaction - withdrawn with phone remark",
                message = "Dear Customer, NPR 1,234.50 is withdrawn from A/C XXX#1234 on 01/01/2026 05:55. Rmk: 9812345678. Good Baln: NPR 321.45",
                sender = "PCBLNPKA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.50"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "9812345678",
                    accountLast4 = "1234",
                    reference = "01/01/2026 05:55"
                )
            ),
            ParserTestCase(
                name = "Credit transaction - deposited with text remark",
                message = "Dear Customer, NPR 9,876.00 is deposited in A/C XXX#5678 on 01/01/2026 05:55. Rmk: CASHDEP. Good Baln: NPR 54321.123.",
                sender = "PRIME_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9876.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "CASHDEP",
                    accountLast4 = "5678",
                    reference = "01/01/2026 05:55"
                )
            )
        ),
        handleCases = listOf(
            "PCBLNPKA" to true,
            "PRIME_ALERT" to true,
            "PRIME" to true,
            "AD-PRIME-ALERT" to true,
            "UNKNOWN" to false
        ),
        suiteName = "Prime Commercial Bank Parser"
    )
}
