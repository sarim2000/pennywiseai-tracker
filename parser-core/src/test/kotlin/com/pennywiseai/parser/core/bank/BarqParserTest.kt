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
            ParserTestCase("Reject verification code", "OTP: 000000 for Online Purchase\nAmount: SAR 12.50", "BARQAPP", shouldParse = false),
            ParserTestCase("Foreign purchase uses the parenthesised SAR settlement",
                "Online Purchase\nAmount: USD 10.00 (SAR 37.60)\nFees: SAR 0.94\nAt: Example Store\nCard: **1234\nBalance: SAR 12.40", "BARQAPP",
                ExpectedTransaction(BigDecimal("37.60"), "SAR", TransactionType.EXPENSE, merchant = "Example Store",
                    balance = BigDecimal("12.40"), isFromCard = false, isMobileWallet = true, expectNullAccountLast4 = true)),
            // Same wallet identity as the top-up case above: one Barq balance, one account.
            ParserTestCase("Card purchase with balance lands on the wallet account",
                "Online Purchase\nAmount: SAR 12.50\nAt: Example Store\nCard: **1234\nBalance: SAR 37.50", "BARQAPP-AD",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE, merchant = "Example Store",
                    balance = BigDecimal("37.50"), isFromCard = false, isMobileWallet = true, expectNullAccountLast4 = true)),
            ParserTestCase("Card purchase without balance keeps the card",
                "Online Purchase\nAmount: SAR 12.50\nAt: Example Store\nCard: **1234", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE, merchant = "Example Store",
                    accountLast4 = "1234", isFromCard = true, isMobileWallet = false)),
            ParserTestCase("Refund wording in the merchant field stays a purchase",
                "Online Purchase\nAmount: SAR 12.50\nAt: Refund Services", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE, merchant = "Refund Services")),
            ParserTestCase("Purchase reversal is income",
                "Reversal\nOnline Purchase\nAmount: SAR 12.50\nAt: Example Store", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.INCOME, merchant = "Example Store")),
            ParserTestCase("Modern purchase never takes a field after Balance as the merchant",
                "Online Purchase\nAmount: SAR 12.50\nBalance: SAR 37.50\nCard: **1234", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE,
                    balance = BigDecimal("37.50"), expectNullMerchant = true)),
            ParserTestCase("Historical purchase reads the merchant after Balance",
                "Online Purchase\nSAR 12.50\nBalance: SAR 37.50\nExample Store", "BARQAPP",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE,
                    merchant = "Example Store", balance = BigDecimal("37.50")))
        ),
        listOf("BARQAPP" to true, "barqapp-AD" to true, "BARQ" to false, "UNKNOWN" to false),
        "Barq Parser"
    )
}
