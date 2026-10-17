package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class TamilnadMercantileBankParserTest {

    @TestFactory
    fun `tmb upi debits`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "UPI debit to a merchant VPA (#917)",
                message = "Your A/c No.XXXX1111 is debited with Rs.487.21 on 09-10-2026 06:19 PM and UTIB A/c linked to testshop.offline@axl is credited (UPI Ref No.628200000001).Current AVBL bal is Rs.1234.73 - TMB",
                sender = "VA-TMBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("487.21"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "testshop.offline",
                    accountLast4 = "1111",
                    balance = BigDecimal("1234.73"),
                    reference = "628200000001"
                )
            ),
            ParserTestCase(
                // The payee side "is credited" too — the user's own a/c is debited.
                name = "UPI debit with a masked VPA prefix stays an expense",
                message = "Your A/c No.XXXX1111 is debited with Rs.70.00 on 15-10-2026 01:23 PM and INDB A/c linked to ****testmerchant.payu@indus is credited (UPI Ref No.665400000002).Current AVBL bal is Rs.987.73 - TMB",
                sender = "VD-TMBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "testmerchant.payu",
                    accountLast4 = "1111",
                    balance = BigDecimal("987.73"),
                    reference = "665400000002"
                )
            )
        )
        val handleCases = listOf(
            "VA-TMBANK-S" to true,
            "VD-TMBANK-S" to true,
            "TTB" to false,
            "TMBThanachart" to false,
            "" to false
        )
        return ParserTestUtils.runTestSuite(TamilnadMercantileBankParser(), cases, handleCases, "Tamilnad Mercantile Bank")
    }

    // TTB (Thailand, formerly TMB Bank) used to claim any sender containing "TMB".
    private val ttbPayment = "ttb: Payment 1,500.00 THB via PromptPay A/C x9012 Bal 7,200.00 THB"

    @TestFactory
    fun `factory routes TMB senders to the right bank`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "Tamilnad Mercantile Bank",
                    sender = "VA-TMBANK-S",
                    currency = "INR",
                    message = "Your A/c No.XXXX1111 is debited with Rs.100.00 on 09-10-2026 06:19 PM and UTIB A/c " +
                        "linked to testshop.offline@axl is credited (UPI Ref No.628200000003).Current AVBL bal is Rs.500.00 - TMB",
                    expected = ExpectedTransaction(
                        amount = BigDecimal("100.00"),
                        currency = "INR",
                        type = TransactionType.EXPENSE
                    ),
                    shouldHandle = true
                )
            ) + listOf("TTB", "TMB", "TMBThanachart").map { sender ->
                SimpleTestCase(
                    bankName = "TTB",
                    sender = sender,
                    currency = "THB",
                    message = ttbPayment,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("1500.00"),
                        currency = "THB",
                        type = TransactionType.EXPENSE
                    ),
                    shouldHandle = true,
                    description = "Thai sender $sender stays on TTB"
                )
            },
            "TMB factory routing"
        )

}
