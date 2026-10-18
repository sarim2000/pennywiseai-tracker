package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SVCBankParserTest {

    @TestFactory
    fun `svc bank upi`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "UPI debit (#928)",
                message = "SVC Bank A/c *1111 DEBITED for Rs.30.00 Clr Bal Rs.696.96. CR UPI/DR/600000000001/TEST PAYEE/. Not You? SMS STOPUPI to 9000000000 /Call 18000000000",
                sender = "SVC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "TEST PAYEE",
                    accountLast4 = "1111",
                    balance = BigDecimal("696.96"),
                    reference = "600000000001"
                )
            )
        )
        val handleCases = listOf(
            "SVC" to true,
            "VM-SVCBNK-S" to true,
            "AD-SVCBNK" to true,
            "ESVC" to false,
            "HDFCBK" to false,
            "" to false
        )
        return ParserTestUtils.runTestSuite(SVCBankParser(), cases, handleCases, "SVC Co-operative Bank")
    }

    @TestFactory
    fun `factory routes SVC senders`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "SVC Co-operative Bank",
                    sender = "VM-SVCBNK-S",
                    currency = "INR",
                    message = factoryDebit,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("1250.50"),
                        currency = "INR",
                        type = TransactionType.EXPENSE
                    ),
                    shouldHandle = true
                )
            ),
            "SVC factory routing"
        )

    private val factoryDebit =
        "SVC Bank A/c *1111 DEBITED for Rs.1,250.50 Clr Bal Rs.2,000.00. CR UPI/DR/600000000002/TEST SHOP/. Not You? SMS STOPUPI to 9000000000"
}
