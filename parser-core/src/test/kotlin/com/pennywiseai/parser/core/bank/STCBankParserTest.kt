package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class STCBankParserTest {

    private val parser = STCBankParser()

    @TestFactory
    fun `stc bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Synthetic card purchase",
                message = "**0007 Purchase\nVia:0007\nAmount: 12.34 SAR\nFrom: SYNTHETIC MERCHANT\nAt: 01/01/30 00:00",
                sender = "STC Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card purchase with decimal amount",
                message = "**0008 Purchase\nVia:0008\nAmount: 23.45 SAR\nFrom: SYNTHETIC MARKET\nAt: 01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    accountLast4 = "0008",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your STC Bank verification code is <CODE>. Do not share it.",
                sender = "STC Bank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Historical SR amount alias is parsed",
                message = "**0007 Purchase\nVia:0007\nAmount: 34.56 SR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Flattened online purchase amount is parsed",
                message = "Online Purchase Transaction Amount 45.67 SAR\nFrom: SYNTHETIC WALLET\nCard: *0007",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WALLET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Internal transfer uses incoming direction",
                message = "Internal transfer\nAmount: 56.78 SAR\nFrom: SYNTHETIC SENDER",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Purchase reversal is income",
                message = "Purchase Reversal\nAmount: 67.89 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Refund for previously declined purchase is accepted",
                message = "Refund for previously declined purchase\nAmount: 13.45 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("13.45"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Refund that itself failed is ignored",
                message = "Refund for your previous purchase failed\nAmount: 24.68 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "The credit never landed. Naming an earlier transaction must not " +
                    "excuse this message's own failure, or the refund is booked as income."
            ),
            ParserTestCase(
                name = "Declined reversal of an original transaction is ignored",
                message = "Reversal of the original transaction was declined\nAmount: 35.79 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "Same shape with the failure word after the noun rather than before it."
            ),
            ParserTestCase(
                name = "Previously declined refund is ignored",
                message = "Your previously declined refund\nAmount: 24.68 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "The failure word sits against the historical marker here too, but it " +
                    "describes the refund itself, not an earlier purchase — so the credit never landed."
            ),
            ParserTestCase(
                name = "Previously failed reversal is ignored",
                message = "Your previously failed reversal\nAmount: 35.79 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "Same shape with 'reversal' as the credit noun."
            ),
            ParserTestCase(
                name = "Refund reported as previously declined is ignored",
                message = "Refund was previously declined\nAmount: 46.80 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "The credit noun comes first and the failure phrase qualifies nothing, " +
                    "so it is this refund that was declined."
            ),
            ParserTestCase(
                name = "Refund declined earlier is ignored",
                message = "Refund declined earlier\nAmount: 24.68 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "Reverse word order: the noun the failure describes now leads it, " +
                    "and it is the refund itself."
            ),
            ParserTestCase(
                name = "Reversal failed previously is ignored",
                message = "Reversal failed previously\nAmount: 35.79 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Refund for a purchase declined earlier is accepted",
                message = "Refund for a purchase declined earlier\nAmount: 13.45 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("13.45"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT"
                ),
                description = "Same reverse order, but the failure describes the purchase — the refund is real."
            ),
            ParserTestCase(
                name = "Refund with a generic noun is ignored",
                message = "Refund transaction declined earlier\nAmount: 24.68 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                shouldParse = false,
                description = "'transaction' doesn't say whose — here it is the refund's own, so the " +
                    "qualifier has to name a kind of payment and an unrecognised phrasing fails closed."
            ),
            ParserTestCase(
                name = "Generic STC recharge notice is ignored",
                message = "Sawa recharge service credit\nAmount: 78.90 SAR\nCheck your Sawa balance",
                sender = "stc",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined purchase is ignored",
                message = "Purchase Declined\nAmount: 89.01 SAR\nInsufficient balance",
                sender = "STCBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Outward SARIE transfer is an expense",
                message = "Outward SARIE Transfer\nAmount: 90.12 SAR\nTo: SYNTHETIC RECIPIENT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.12"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "OTP with amount is ignored",
                message = "<CODE> is your OTP\nFor: SYNTHETIC MERCHANT\nAmount: USD 0.0",
                sender = "STCBank",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves stc bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STC Bank",
                currency = "SAR",
                message = "**0007 Purchase\nVia:0007\nAmount: 12.34 SAR\nFrom: SYNTHETIC MERCHANT\nAt: 01/01/30 00:00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STCBank",
                currency = "SAR",
                message = "**0008 Purchase\nVia:0008\nAmount: 23.45 SAR\nFrom: SYNTHETIC MARKET\nAt: 01/01/30 00:00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
