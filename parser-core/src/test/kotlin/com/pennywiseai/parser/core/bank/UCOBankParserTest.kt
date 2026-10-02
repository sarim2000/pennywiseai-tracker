package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import java.math.BigDecimal

class UCOBankParserTest {

    @TestFactory
    fun `uco parser reads the transaction amount, never the balance`(): List<DynamicTest> {
        val parser = UCOBankParser()
        val sender = "VM-UCOBNK-S"

        val cases = listOf(
            ParserTestCase(
                name = "UPI debit",
                message = "A/c XX1234 Debited with Rs.2000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp",
                sender = sender,
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("11111.11")
                )
            ),
            ParserTestCase(
                name = "UPI credit with grouped amount",
                message = "A/c XX1234 Credited with Rs.2,000.00 on 21-09-2025 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in/Home_Page.jsp -UCO Bank",
                sender = sender,
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("11111.11")
                )
            ),
            ParserTestCase(
                // A sub-rupee amount printed without its leading zero ("Rs..50").
                // The pattern used to reject it and fall through to the next
                // "Rs." in the message — the Avl Bal — so a 50-paise debit was
                // recorded as a 2,992.54 expense.
                name = "Sub-rupee debit written without a leading zero",
                message = "Your UCO Bank A/c XX1234 has been Debited with Rs..50 by Transfer.Avl Bal in your A/c is Rs.2,992.54.",
                sender = sender,
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.50"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("2992.54")
                )
            )
            ,
            ParserTestCase(
                // No "Debited with" clause, so the fallback runs. It must only
                // look before the balance clause, or it could still land on the
                // balance whenever the real amount fails to match.
                name = "Fallback never reads the balance",
                message = "Rs..50 debited from A/c XX1234 by Transfer. Avl Bal Rs.2,992.54",
                sender = sender,
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.50"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("2992.54")
                )
            )

        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @Test
    fun `an unavailable balance stays unknown instead of borrowing another amount`() {
        // ExpectedTransaction treats balance = null as "don't check", so this
        // needs a direct assertion. An open-ended gap after "Avl Bal" would
        // reach the service charge and record it as the balance.
        val parsed = UCOBankParser().parse(
            "A/c XX1234 Debited with Rs.200.00 on 21-09-2025 by UCO-UPI.Avl Bal unavailable. Service charge Rs.10.00",
            "VM-UCOBNK-S",
            System.currentTimeMillis()
        )
        assertNotNull(parsed)
        assertEquals(0, BigDecimal("200.00").compareTo(parsed!!.amount))
        assertNull(parsed.balance)
    }
}
