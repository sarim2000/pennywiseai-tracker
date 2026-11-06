package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BarqParserTest {
    @TestFactory
    fun transactions() = ParserTestUtils.runTestSuite(
        BarqParser(),
        listOf(
            ParserTestCase("Purchase", "Online Purchase\nAmount: SAR 12.50\nAt: Example Store", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE, merchant = "Example Store")),
            ParserTestCase("Top-up balance belongs to wallet",
                "Barq Wallet Top-up\nAmount: SAR 25.00\nFrom Card: 1234*MADA\nBalance: SAR 50.00", "BARQAPP",
                ExpectedTransaction(BigDecimal("25.00"), "SAR", TransactionType.TRANSFER,
                    balance = BigDecimal("50.00"), isFromCard = false, isMobileWallet = true, expectNullAccountLast4 = true)),
            ParserTestCase("Incoming transfer does not use the sender account",
                "Incoming Local Transfer\nAmount: SAR 12.50\nFrom: Example Sender\nSender A/C: **1234", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.INCOME,
                    isFromCard = false, expectNullAccountLast4 = true)),
            ParserTestCase("Reject verification code", "OTP: 000000 for Online Purchase\nAmount: SAR 12.50", "BARQAPP", shouldParse = false)
        )
    )
}
