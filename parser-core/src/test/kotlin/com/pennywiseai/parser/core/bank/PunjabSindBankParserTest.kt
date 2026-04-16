package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PunjabSindBankParserTest {

    private val parser = PunjabSindBankParser()

    @TestFactory
    fun `punjab sind bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Generic credit with free-text description",
                message = "PSB000000000000001\nA/C No **1111 Credited with Rs 500--Vendor Amount (CLR BAL 1250.63CR)(18-03-2026 16:51:51) Punjab&Sind Bank",
                sender = "VM-PSBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Vendor Amount",
                    accountLast4 = "1111",
                    balance = BigDecimal("1250.63")
                )
            ),
            ParserTestCase(
                name = "NEFT credit extracts sender name and UTR",
                message = "PSB000000000000002\nA/C No **1111 Credited with Rs 500--NEFT/AXPS260760067935/ACME SCHOOL (CLR BAL 1800.63CR)(19-03-2026 06:51:51) Punjab&Sind Bank",
                sender = "AX-PSBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "ACME SCHOOL",
                    reference = "AXPS260760067935",
                    accountLast4 = "1111",
                    balance = BigDecimal("1800.63")
                )
            ),
            ParserTestCase(
                name = "UPI credit extracts counterparty name and UTR",
                message = "PSB000000000000003\nA/c No **2222 Credited with Rs 5500--UPI/CR/121888265852/JOHN/HDFC/00000000000000/U (CLR BAL 30987.99CR)(13-04-2026 17:22:34)-Punjab&Sind Bank",
                sender = "JD-PSBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5500"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "JOHN",
                    reference = "121888265852",
                    accountLast4 = "2222",
                    balance = BigDecimal("30987.99")
                )
            ),
            ParserTestCase(
                name = "Cheque clearing credit maps to Cheque Credit merchant",
                message = "PSB000000000000004\nA/c No **3333 Credited with Rs 700--Credit of 045252 (CLR BAL 11064.24CR)(13-04-2026 19:16:15)-Punjab&Sind Bank",
                sender = "VM-PSBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("700"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Cheque Credit",
                    reference = "045252",
                    accountLast4 = "3333",
                    balance = BigDecimal("11064.24")
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves punjab sind bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Punjab & Sind Bank",
                sender = "VM-PSBANK-S",
                currency = "INR",
                message = "PSB000000000000005\nA/c No **4444 Credited with Rs 100--Test (CLR BAL 200.00CR)(13-04-2026 19:16:15)-Punjab&Sind Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100"),
                    currency = "INR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Punjab & Sind Bank factory smoke tests")
    }
}
