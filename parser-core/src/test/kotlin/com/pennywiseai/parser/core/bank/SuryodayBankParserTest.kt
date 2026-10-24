package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SuryodayBankParserTest {

    @TestFactory
    fun `suryoday credit card spend`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Stable Money co-brand card spend",
                message = "Rs 553.00 spent on Stable Money Suryoday SFB CC 1111 on 20-10-2026 at 23:38:50. Avlb Rs 41628.18 Not you SMS BLOCK CC 1111 to 9000000000. Convert to EMI? visit: https://cc.suryoday.bank.in/emi-web SSFBNK-T",
                sender = "JM-SSFBNK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("553.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    accountLast4 = "1111",
                    creditLimit = BigDecimal("41628.18"),
                    isFromCard = true,
                    expectNullMerchant = true
                )
            ),
            ParserTestCase(
                name = "Card spend with thousands separators",
                message = largeSpend,
                sender = "VM-SSFBNK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12500.50"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    accountLast4 = "2222",
                    creditLimit = BigDecimal("29127.68"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP is not a transaction",
                message = otp,
                sender = "JM-SSFBNK-T",
                shouldParse = false
            )
        )
        val handleCases = listOf(
            "JM-SSFBNK-T" to true,
            "VM-SSFBNK-S" to true,
            "HDFCBK" to false,
            "" to false
        )
        return ParserTestUtils.runTestSuite(SuryodayBankParser(), cases, handleCases, "Suryoday Small Finance Bank")
    }

    @TestFactory
    fun `factory routes SSFBNK`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "Suryoday Small Finance Bank",
                    sender = "JM-SSFBNK-T",
                    currency = "INR",
                    message = largeSpend,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("12500.50"),
                        currency = "INR",
                        type = TransactionType.CREDIT
                    ),
                    shouldHandle = true
                )
            ),
            "Suryoday factory routing"
        )

    private val largeSpend =
        "Rs 12,500.50 spent on Suryoday SFB CC 2222 on 21-10-2026 at 10:05:11. Avlb Rs 29,127.68 Not you SMS BLOCK CC 2222 to 9000000000. Convert to EMI? visit: https://cc.suryoday.bank.in/emi-web SSFBNK-T"
    private val otp =
        "123456 is the OTP for your Suryoday SFB CC 1111 transaction of Rs 553.00. Do not share it with anyone. SSFBNK-T"
}
