package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CanaraBankParserTest {

    private val parser = CanaraBankParser()

    @TestFactory
    fun `canara bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "RTGS MF redemption credit typed as INCOME",
                message = "An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/12/2025 towards RTGS by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC UTIB0000004, Sender A/c XXXX9108, AXIS BANK, MUMBAI BRANCH, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank",
                sender = "VA-CANBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1330614.75"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "AXIS MUTUAL FUND REDEMPTION PO",
                    accountLast4 = "9108"
                )
            ),
            ParserTestCase(
                name = "Compact Dr INR UPI debit from VK sender",
                message = "Dear Customer, Acct XXX123 Dr. INR 260.00 on 06/07/26 to SAMPLE MART; UPI: 123456789012; Bal INR 12,345.67.Not you? SMS BLOCKUPI to XXXXXXXXXX-CanaraBank",
                sender = "VK-CANBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("260.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE MART",
                    reference = "123456789012",
                    accountLast4 = "123",
                    balance = BigDecimal("12345.67")
                )
            ),
            ParserTestCase(
                name = "Compact Dr Rs UPI debit from JK sender",
                message = "Dear Customer, Acct XXX456 Dr. Rs. 75.50 on 07/07/26 to CITY CAFE; UPI: 987654321098; Bal INR 9,876.54.Not you? SMS BLOCKUPI to XXXXXXXXXX-CanaraBank",
                sender = "JK-CANBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.50"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "CITY CAFE",
                    reference = "987654321098",
                    accountLast4 = "456",
                    balance = BigDecimal("9876.54")
                )
            ),
            ParserTestCase(
                name = "Compact Dr rupee-symbol UPI debit",
                message = "Dear Customer, Acct XXX789 Dr. ₹41.00 on 08/07/26 to METRO TRANSIT; UPI: 456789012345; Bal INR 8,765.43.Not you? SMS BLOCKUPI to XXXXXXXXXX-CanaraBank",
                sender = "VK-CANBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("41.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "METRO TRANSIT",
                    reference = "456789012345",
                    accountLast4 = "789",
                    balance = BigDecimal("8765.43")
                )
            ),
            ParserTestCase(
                name = "ATM free-transaction usage notification is ignored",
                message = "Card ending 4321: Dear Customer, you have done 02 out of 06 free transactions at Canara ATMs in this month. Charges applicable beyond free transactions.-Canara Bank",
                sender = "JK-CANBNK-S",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = listOf(
                "VK-CANBNK-S" to true,
                "JK-CANBNK-S" to true
            )
        )
    }
}
