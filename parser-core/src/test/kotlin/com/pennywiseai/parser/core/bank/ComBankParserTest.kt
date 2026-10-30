package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ComBankParserTest {

    @TestFactory
    fun `combank parser handles common cases`(): List<DynamicTest> {
        val parser = ComBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Debit card purchase authorised (LKR)",
                message = "Dear Cardholder, Purchase at Dialog Axiata PLC Colombo 02 LK for LKR 672.00 on 27/09/26 09:32 PM has been authorised on your debit card ending #5555.",
                sender = "ComBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("672.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Dialog Axiata PLC Colombo 02 LK",
                    accountLast4 = "5555",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit card purchase authorised, Rs. amount",
                message = "Dear Cardholder, Purchase at KEELLS SUPER NUGEGODA LK for Rs. 12,450.75 on 01/10/26 11:05 AM has been authorised on your credit card ending #1234.",
                sender = "ComBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12450.75"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "KEELLS SUPER NUGEGODA LK",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Foreign-currency purchase keeps its own currency",
                message = "Dear Cardholder, Purchase at AWS EMEA SARL LU for USD 15.00 on 02/10/26 04:12 AM has been authorised on your credit card ending #1234.",
                sender = "ComBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "AWS EMEA SARL LU",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit to account (amount before account)",
                message = "Credit for Rs. 25,000.00 to A/C #1234 on 03/10/26.",
                sender = "ComBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    isFromCard = false
                )
            )
        )

        val handleCases = listOf(
            "ComBank" to true,
            "COMBANK" to true,
            "AD-ComBank-S" to true,
            // Commercial Bank of *Ethiopia* owns this one — ComBank must not take it.
            "COMMERCIALBANK" to false,
            "SAMPATHTXN" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "ComBank Parser Tests")
    }

    @TestFactory
    fun `factory resolves ComBank sender`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Commercial Bank of Ceylon",
                sender = "ComBank",
                currency = "LKR",
                message = "Dear Cardholder, Purchase at Dialog Axiata PLC Colombo 02 LK for LKR 672.00 on 27/09/26 09:32 PM has been authorised on your debit card ending #5555.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("672.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Dialog Axiata PLC Colombo 02 LK",
                    accountLast4 = "5555",
                    isFromCard = true
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                description = "Commercial Bank of Ethiopia still owns COMMERCIALBANK",
                bankName = "Commercial Bank of Ethiopia",
                sender = "COMMERCIALBANK",
                currency = "ETB",
                message = "Dear Customer your Account 1*********1234 has been Credited with ETB 5,000.00 from Salary Payment, on 15/09/2025 at 09:00:00 with Ref No ABC123456 Your Current Balance is ETB 8,000.00. Thank you for Banking with CBE!",
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "ComBank factory tests")
    }
}
