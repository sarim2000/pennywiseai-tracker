package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MPESAParserTest {
    @TestFactory
    fun `test M-PESA Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = MPESAParser()

        ParserTestUtils.printTestHeader(
            parserName = "M-PESA",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Payment to store with period in name
            ParserTestCase(
                name = "Payment to Store with Period in Name",
                message = """TLK6H1D4F8 Confirmed. Ksh160.00 paid to F.M STORES. on 20/12/25 at 2:31 PM.New M-PESA balance is Ksh39,905.81. Transaction cost, Ksh0.00. Amount you can transact within the day is 497,300.00. Save frequent Tills for quick payment on M-PESA app https://bit.ly/mpesalnk""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("160.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "F.M STORES",
                    balance = BigDecimal("39905.81"),
                    reference = "TLK6H1D4F8"
                )
            ),

            // Payment to person with full name
            ParserTestCase(
                name = "Payment to Person with Full Name",
                message = """TLP6H1TFCA Confirmed. Ksh200.00 paid to Person Name. on 25/12/25 at 8:54 AM.New M-PESA balance is Ksh34,330.51. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,800.00. Save frequent Tills for quick payment on M-PESA app https://bit.ly/mpesalnk""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person Name",
                    balance = BigDecimal("34330.51"),
                    reference = "TLP6H1TFCA"
                )
            ),

            // Payment to person
            ParserTestCase(
                name = "Payment to Person",
                message = """TJK6H7T3GA Confirmed. Ksh70.00 paid to person 1. on 20/10/24 at 4:21 PM.New M-PESA balance is Ksh123.12. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,895.00. Save frequent Tills for quick payment on M-PESA app https://bit.ly/mpesalnk""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "person",
                    balance = BigDecimal("123.12"),
                    reference = "TJK6H7T3GA"
                )
            ),

            // Paybill payment
            ParserTestCase(
                name = "Paybill Payment to Equity",
                message = """TJK6H7T0JT Confirmed. Ksh1000.00 sent to Equity Paybill Account for account 123123 on 20/10/25 at 4:26 PM New M-PESA balance is Ksh123.12. Transaction cost, Ksh23.00.Amount you can transact within the day is 499,795.00. Save frequent paybills for quick payment on M-PESA app https://bit.ly/mpesalnk""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Equity Paybill Account",
                    balance = BigDecimal("123.12"),
                    reference = "TJK6H7T0JT"
                )
            ),

            // Send to person without phone number
            ParserTestCase(
                name = "Send to Person without Phone Number",
                message = """TLL6H1F7JF Confirmed. Ksh400.00 sent to Person Name on 21/12/25 at 12:41 AM. New M-PESA balance is Ksh38,330.81. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,600.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person Name",
                    balance = BigDecimal("38330.81"),
                    reference = "TLL6H1F7JF"
                )
            ),

            // Send to person with multiple spaces in name
            ParserTestCase(
                name = "Send to Person with Multiple Spaces",
                message = """TLL6H1F7JF Confirmed. Ksh400.00 sent to Person  Name on 21/12/25 at 12:41 AM. New M-PESA balance is Ksh38,330.81. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,600.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person  Name",
                    balance = BigDecimal("38330.81"),
                    reference = "TLL6H1F7JF"
                )
            ),

            // Send to person with phone
            ParserTestCase(
                name = "Send to Person with Phone Number",
                message = """TJK6H7TDIJ Confirmed. Ksh50.00 sent to Person 2 0711 111 111 on 20/10/24 at 6:27 PM. New M-PESA balance is Ksh123.12. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,745.00. Earn interest daily on Ziidi MMF,Dial *334#""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person 2",
                    balance = BigDecimal("123.12"),
                    reference = "TJK6H7TDIJ"
                )
            ),

            // Send to person with unspaced phone number
            ParserTestCase(
                name = "Send to Person with Unspaced Phone Number",
                message = """TLP6H1VM04 Confirmed. Ksh100.00 sent to Person One 0711111111 on 25/12/25 at 8:33 PM. New M-PESA balance is Ksh123.51. Transaction cost, Ksh0.00. Amount you can transact within the day is 498,870.00. Earn interest daily on Ziidi MMF, Dial *334#""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person One",
                    balance = BigDecimal("123.51"),
                    reference = "TLP6H1VM04"
                )
            ),

            // Payment with comma in amount
            ParserTestCase(
                name = "Payment with Comma in Amount",
                message = """TJD6H78J2L Confirmed. Ksh1,120.00 paid to Person 4 1. on 13/10/24 at 8:01 PM.New M-PESA balance is Ksh123.12. Transaction cost, Ksh0.00. Amount you can transact within the day is 498,440.00. Save frequent Tills for quick payment on M-PESA app https://bit.ly/mpesalnk""",
                sender = "MPESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1120.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Person 4",
                    balance = BigDecimal("123.12"),
                    reference = "TJD6H78J2L"
                )
            ),

            // Received from person
            ParserTestCase(
                name = "Received from Person",
                message = """TJF987E58C Confirmed.You have received Ksh300.00 from Person 3 0712121212 on 15/10/24 at 12:16 PM  New M-PESA balance is Ksh123.12. Earn interest daily on Ziidi MMF,Dial *334#""",
                sender = "Person 3",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Person 3",
                    balance = BigDecimal("123.12"),
                    reference = "TJF987E58C"
                )
            ),

            // Received from bank
            ParserTestCase(
                name = "Received from Bank",
                message = """TJE6H7BG0S Confirmed.You have received Ksh3,000.00 from BANK OF BARODA KENYA LIMITED 123123 on 14/10/24 at 7:16 PM New M-PESA balance is Ksh123.12.  Separate personal and business funds through Pochi la Biashara on *334#.""",
                sender = "Bank OF Baroda Kenya Limited",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "BANK OF BARODA KENYA LIMITED",
                    balance = BigDecimal("123.12"),
                    reference = "TJE6H7BG0S"
                )
            ),

            // Received from B2C service
            ParserTestCase(
                name = "Received from B2C Service",
                message = """Congratulations! TJ56H6J1WU confirmed.You have received Ksh425.00 from LOOP B2C. on 5/10/25 at 6:34 PM.New M-PESA balance is Ksh123.11. Separate personal and business funds through Pochi la Biashara on *334#.""",
                sender = "Loop b2c",
                expected = ExpectedTransaction(
                    amount = BigDecimal("425.00"),
                    currency = "KES",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "LOOP B2C",
                    balance = BigDecimal("123.11"),
                    reference = "TJ56H6J1WU"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "MPESA" to true,
            "M-PESA" to true,
            "mpesa" to true,
            "m-pesa" to true,
            "Person 3" to false,  // Sender name doesn't determine M-PESA
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "M-PESA Parser Tests")

    }

    @Test
    fun `test extract transaction cost`() {
        val parser = MPESAParser()

        // Test with transaction cost
        val messageWithCost = """TLL6H1F7JF Confirmed. Ksh400.00 sent to Person Name on 21/12/25 at 12:41 AM. New M-PESA balance is Ksh38,330.81. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,600.00."""
        val cost = parser.extractTransactionCost(messageWithCost)
        assertEquals(BigDecimal("7.00"), cost)

        // Test with zero cost
        val messageWithZeroCost = """TJK6H7T3GA Confirmed. Ksh70.00 paid to person 1. on 20/10/24 at 4:21 PM.New M-PESA balance is Ksh123.12. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,895.00."""
        val zeroCost = parser.extractTransactionCost(messageWithZeroCost)
        assertNull(zeroCost, "Zero cost should return null")

        // Test with higher cost
        val messageWithHighCost = """TJK6H7T0JT Confirmed. Ksh1000.00 sent to Equity Paybill Account for account 123123 on 20/10/25 at 4:26 PM New M-PESA balance is Ksh123.12. Transaction cost, Ksh23.00.Amount you can transact within the day is 499,795.00."""
        val highCost = parser.extractTransactionCost(messageWithHighCost)
        assertEquals(BigDecimal("23.00"), highCost)

        // Test without transaction cost field
        val messageWithoutCost = """TJK6H7T3GA Confirmed. Ksh70.00 paid to person 1. on 20/10/24 at 4:21 PM.New M-PESA balance is Ksh123.12."""
        val noCost = parser.extractTransactionCost(messageWithoutCost)
        assertNull(noCost, "Message without cost should return null")
    }
}
