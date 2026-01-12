package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HSBCBankParserTest {

    private val parser = HSBCBankParser()

    @Test
    fun `hsbc parser handles key paths`() {
        ParserTestUtils.printTestHeader(
            parserName = "HSBC Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Outgoing NEFT Transfer - credited to other bank",
                message = "HSBC: Dear HSBC Customer, your NEFT transaction with reference number HSBCN00106726185 for INR 150,000.00 has been credited to the HDFC A/c XXXXXXXXXX6956 of AKASH KEDIA on 01-01-2026 at 15:36:47 .",
                sender = "VM-HSBCIN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000.00"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    merchant = "AKASH KEDIA"
                )
            ),
            ParserTestCase(
                name = "Debit Card Purchase",
                message = "HSBC: Thank you for using HSBC Debit Card XXXXX71xx for INR 305.00 on 15-Dec-25 at IKEA INDIA .",
                sender = "VM-HSBCIN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("305.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "IKEA INDIA",
                    accountLast4 = "71xx"
                )
            ),
            ParserTestCase(
                name = "Incoming NEFT Credit",
                message = "HSBC: INR 50,000.00 is credited to your A/c 074-260***-006 as NEFT from CHAS A/c ***6983 of John Doe .",
                sender = "VM-HSBCIN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "CHAS A/c ***6983 of John Doe",
                    accountLast4 = "0006"
                )
            ),
            ParserTestCase(
                name = "Payment from Account",
                message = "HSBC: INR 1,234.56 is paid from your A/c 074-260***-006 to AMAZON on 20-Dec-25. Your Avl Bal is INR 98,765.44 .",
                sender = "HSBCIN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON",
                    accountLast4 = "0006",
                    balance = BigDecimal("98765.44")
                )
            )
        )

        ParserTestUtils.runTestSuite(parser, cases)
    }
}
