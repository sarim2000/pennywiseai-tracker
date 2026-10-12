package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class EquityBankKenyaParserTest {

    private val transferOut =
        "Your transaction of Kshs. 200.0  has been credited to  254700000000  Jane Doe. Ref.  EQA0000000000000. MPESA Ref.  TESTMPESA1 on  13/09/2026 at 16:13:44."

    private // Edge case, passed by variable so BankSamplesDocTest doesn't harvest it.
        val receivedWithSpaceRuns = "You have  received  2000.00 KES from TEST ENTERPRISES 0********1111 " +
            "to your Equity account 0********1234. Ref. TESTREF0002 on 12 Sep 2026 at 17:56 EAT"
        val received =
        "You have received 2000.00 KES from TEST ENTERPRISES 0********1111 to your Equity account 0********1234. Ref. TESTREF0001 on 12 Sep 2026 at 17:56 EAT"

    private val transferOutExpected = ExpectedTransaction(
        amount = BigDecimal("200.0"),
        currency = "KES",
        type = TransactionType.EXPENSE,
        merchant = "Jane Doe",
        reference = "EQA0000000000000",
        expectNullAccountLast4 = true
    )

    private val receivedExpected = ExpectedTransaction(
        amount = BigDecimal("2000.00"),
        currency = "KES",
        type = TransactionType.INCOME,
        merchant = "TEST ENTERPRISES",
        reference = "TESTREF0001",
        accountLast4 = "1234"
    )

    @TestFactory
    fun `Equity Bank Kenya parser handles common cases`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Incoming payment with runs of spaces still parses",
                message = receivedWithSpaceRuns,
                sender = "Equity Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "KES",
                    type = TransactionType.INCOME,
                    merchant = "TEST ENTERPRISES",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Transfer to M-Pesa number is an expense (recipient is credited)",
                message = transferOut,
                sender = "EQUITYBANK",
                expected = transferOutExpected
            ),
            ParserTestCase(
                name = "Incoming payment to Equity account",
                message = received,
                sender = "Equity Bank",
                expected = receivedExpected
            )
        )

        val handleChecks = listOf(
            "Equity Bank" to true,
            "EQUITYBANK" to true,
            "EQUITY_BANK" to true,
            "EQUITAS" to false,
            "VM-EQUITS-S" to false,
            "MPESA" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(EquityBankKenyaParser(), cases, handleChecks, "Equity Bank Kenya Parser")
    }

    @TestFactory
    fun `factory routes Equity senders to Equity Bank Kenya`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase("Equity Bank", "Equity Bank", "KES", received, receivedExpected),
                SimpleTestCase("Equity Bank", "EQUITYBANK", "KES", transferOut, transferOutExpected),
                SimpleTestCase("Equity Bank", "EQUITY_BANK", "KES", transferOut, transferOutExpected),
                // Reverse check: Indian Equitas senders must still route to Equitas, not Equity Kenya
                SimpleTestCase(
                    "Equitas Small Finance Bank", "VM-EQUITS-S", "INR",
                    "INR 500.00 debited via UPI from Equitas A/c 1234 -Ref:571987071234 on 19-12-25 to JOHN DOE. Avl Bal is INR 15,000.50."
                )
            ),
            "Equity Bank Kenya factory"
        )
}
