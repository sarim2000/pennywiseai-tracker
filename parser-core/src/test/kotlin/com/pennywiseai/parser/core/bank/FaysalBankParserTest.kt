package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class FaysalBankParserTest {

    private val parser = FaysalBankParser()

    @TestFactory
    fun `faysal bank parser handles ibft notifications`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Outgoing IBFT to Demo Recipient",
                message = "PKR 55.000.00 sent to DEMO RECIPIENT A/C *9901 via IBFT from FBL A/C *1234 on 06-FEB-2026 02:22 PM Ref # 960855.",
                sender = "com.avanza.ambitwizfbl",
                expected = ExpectedTransaction(
                    amount = BigDecimal("55000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER,
                    merchant = "DEMO RECIPIENT",
                    accountLast4 = "1234",
                    reference = "960855"
                )
            ),
            ParserTestCase(
                name = "Outgoing IBFT to Sample Beneficiary",
                message = "PKR 70.000.00 sent to SAMPLE BENEFICIARY A/C *8518 via IBFT from FBL A/C *1234 on 06-FEB-2026 02:20 PM Ref # 950900.",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER,
                    merchant = "SAMPLE BENEFICIARY",
                    accountLast4 = "1234",
                    reference = "950900"
                )
            ),
            ParserTestCase(
                name = "Incoming via RAAST",
                message = "ACCOUNT HOLDER A/c # *1234 received PKR 500.00 via RAAST from SENDER ALIAS IBAN *3867 on 06-Feb-26 at 04:22 PM Ref#:121621592909 Info:111060606",
                sender = "com.avanza.ambitwizfbl",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "SENDER ALIAS",
                    accountLast4 = "1234",
                    reference = "121621592909"
                )
            ),
            ParserTestCase(
                name = "Incoming IBFT from ACME Services",
                message = "PKR 250,000.00 received from ACME SERVICES  A/C *4388 in FBL A/C *1234 on 06/FEB/2026 at 02:19 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250000.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "ACME SERVICES",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Debit card purchase",
                message = "PKR 16738.79 Debit Card purchase at Sample Delivery Karachi from FBL A/C *1234 on 02/FEB/2026 at 09:14:51 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("16738.79"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Delivery Karachi",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM cash withdrawal",
                message = "PKR 10,000.00 ATM cash withdrawal from FBL A/C *1234 on 01/FEB/2026 at 02:52 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Cash Withdrawal",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Incoming IBFT from BAFL",
                message = "PKR 135,327.46 received from Demo Sender BAFL A/C*2050 via IBFT in FBL A/C *1234 on 29/JAN/2026 at 01:13 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("135327.46"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "Demo Sender BAFL",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Incoming FT with sender and receiver accounts",
                message = "PKR 200,000.00 received from Sender Name FBL A/C *4613 via FT in FBL A/C *1234 on 24/JAN/2026 at 12:50 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200000.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "Sender Name FBL",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Incoming FT with different receiver",
                message = "PKR 950,000.00 received from Demo Sender FBL A/C *4646 via FT in FBL A/C *4388 on 05/JAN/2026 at 02:44 PM",
                sender = "FBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("950000.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "Demo Sender FBL",
                    accountLast4 = "4388"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves faysal bank senders`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Faysal Bank",
                sender = "com.avanza.ambitwizfbl",
                currency = "PKR",
                message = "PKR 55.000.00 sent to DEMO RECIPIENT A/C *9901 via IBFT from FBL A/C *1234 on 06-FEB-2026 02:22 PM Ref # 960855.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("55000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Faysal Bank",
                sender = "FBL",
                currency = "PKR",
                message = "PKR 70.000.00 sent to SAMPLE BENEFICIARY A/C *8518 via IBFT from FBL A/C *1234 on 06-FEB-2026 02:20 PM Ref # 950900.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Faysal Bank",
                sender = "8756",
                currency = "PKR",
                message = "PKR 10,000.00 ATM cash withdrawal from FBL A/C *1234 on 01/FEB/2026 at 02:52 PM",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory resolves Faysal Bank senders")
    }
}
