import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.StandardCharteredBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class StandardCharteredBankParserTest {

    @TestFactory
    fun `standard chartered bank parser handles expected scenarios`(): List<DynamicTest> {
        val parser = StandardCharteredBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Standard Chartered Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // UPI Debit transactions
            ParserTestCase(
                name = "UPI Debit Transfer - Example 1",
                message = "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232).Plz call 18002586465 if not done by you.",
                sender = "VM-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("302.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX1465",
                    accountLast4 = "3421",
                    reference = "487597904232"
                )
            ),

            ParserTestCase(
                name = "UPI Debit Transfer - Example 2",
                message = "Your a/c XX1234 is debited for Rs. 30.00 on 29-11-2025 22:49 and credited to a/c XX0025 (UPI Ref no 764379954202).Plz call 18002586465 if not done by you.",
                sender = "VD-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX0025",
                    accountLast4 = "1234",
                    reference = "764379954202"
                )
            ),

            // NEFT Credit
            ParserTestCase(
                name = "NEFT Credit with Balance",
                message = "Dear Customer, there is an NEFT credit of INR 48,796.00 in your account 123xxxx7655 on 1/11/2025.Available Balance:INR 97,885.05 -StanChart",
                sender = "JK-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("48796.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "NEFT Credit",
                    accountLast4 = "7655",
                    balance = BigDecimal("97885.05")
                )
            ),

            // Large amount with comma
            ParserTestCase(
                name = "UPI Transfer - Large Amount",
                message = "Your a/c XX5678 is debited for Rs. 1,250.00 on 01-12-2025 10:30 and credited to a/c XX9999 (UPI Ref no 123456789012).Plz call 18002586465 if not done by you.",
                sender = "VM-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX9999",
                    accountLast4 = "5678",
                    reference = "123456789012"
                )
            ),

            // RTGS Credit
            ParserTestCase(
                name = "RTGS Credit",
                message = "Dear Customer, there is an RTGS credit of INR 100,000.00 in your account 456xxxx1234 on 15/12/2025.Available Balance:INR 250,000.00 -StanChart",
                sender = "SCBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "RTGS Credit",
                    accountLast4 = "1234",
                    balance = BigDecimal("250000.00")
                )
            ),

            // IMPS Credit
            ParserTestCase(
                name = "IMPS Credit",
                message = "Dear Customer, there is an IMPS credit of INR 5,000.00 in your account 789xxxx5555 on 10/12/2025.Available Balance:INR 15,000.00 -StanChart",
                sender = "VD-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "IMPS Credit",
                    accountLast4 = "5555",
                    balance = BigDecimal("15000.00")
                )
            ),

            // Pakistan RAAST/IBFT credits (sender 9220)
            ParserTestCase(
                name = "PKR RAAST credit to SCB account",
                message = "Dear Customer, PKR 55,000.00 sent to SCB PK A/C ****9901 for FUNDSTRANSFER 001 on 06-Feb-26 14:22 via RAAST for TX ID FAYS2602061422095059608557891",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("55000.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "RAAST Transfer",
                    accountLast4 = "9901",
                    reference = "FAYS2602061422095059608557891"
                )
            ),
            ParserTestCase(
                name = "PKR IBFT credit with masked account",
                message = "Dear Client, an electronic funds transfer of PKR 5,000.00 has been made into your Account No. 0101xxx9901 on 31-01-26 via Online Banking.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "IBFT Transfer",
                    accountLast4 = "9901"
                )
            ),
            ParserTestCase(
                name = "PKR IBFT credit with sender name",
                message = "Dear Client, your account 01-01***9901 has been credited with amount PKR 1,083,503.58 from account 18-87xxxxx-9039959 PAYONEER from IBFT 04/12/202 on 04/12/25.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1083503.58"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "PAYONEER",
                    accountLast4 = "9901"
                )
            ),
            ParserTestCase(
                name = "PKR IBFT credit with dashed account separators",
                message = "Dear Client, your account 01-01***99-01 has been credited with amount PKR 1,083,503.58 from account 18-87xxxxx-9039959 PAYONEER from IBFT 04/12/202 on 04/12/25.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1083503.58"),
                    currency = "PKR",
                    type = TransactionType.INCOME,
                    merchant = "PAYONEER",
                    accountLast4 = "9901"
                )
            ),

            // PKR debits and transfers
            ParserTestCase(
                name = "PKR Raast transfer outbound masked",
                message = "Dear Client, a transaction of PKR 50,000.00 has been completed on Iban. ****9901 to ****4101 on 2025-12-21 20:30:21 through SC Raast Online Banking. Transaction ID:PK-019-251221-203021021-272607-342",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER,
                    merchant = "Transfer to 4101",
                    accountLast4 = "9901",
                    reference = "PK-019-251221-203021021-272607-342"
                )
            ),
            ParserTestCase(
                name = "PKR ATM withdrawal",
                message = "Dear Client, PKR 20,000.00 were withdrawn from Account No. 0101xxx9901 on 10-12-25 using an ATM.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Cash Withdrawal",
                    accountLast4 = "9901"
                )
            ),
            ParserTestCase(
                name = "PKR Debit card purchase",
                message = "Dear Client, PKR 4,829.00 have been paid at ELITE CLUB on 10-12-25 using Debit Card no. 53119xxxxxxxx1640.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4829.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "ELITE CLUB",
                    isFromCard = true,
                    accountLast4 = "1640"
                )
            ),
            ParserTestCase(
                name = "PKR Debit card purchase masked leading digits",
                message = "Dear Client, PKR 1,100.00 have been paid at Netflix.com on 31-01-26 using Debit Card no. 53119xxxxxxxx1640.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1100.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Netflix.com",
                    isFromCard = true,
                    accountLast4 = "1640"
                )
            ),
            ParserTestCase(
                name = "PKR Online banking transfer with merchant name",
                message = "Dear Client, a transaction of PKR 300,000.00 has been completed on Acc. Number 0101xxx9901 to TANBITS on 09/12/25 through Online Banking.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300000.00"),
                    currency = "PKR",
                    type = TransactionType.TRANSFER,
                    merchant = "TANBITS",
                    accountLast4 = "9901"
                )
            ),
            ParserTestCase(
                name = "PKR Credit card ATM cash withdrawal",
                message = "Dear Client, an ATM cash withdrawal transaction of PKR 30,000.00 has been made on 01-12-25 using credit card no 0141. Avail Limit PKR18062.81. SCBPL",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000.00"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Cash Withdrawal",
                    isFromCard = true,
                    balance = BigDecimal("18062.81"),
                    accountLast4 = "0141"
                )
            ),
            ParserTestCase(
                name = "PKR Financing facility payment receipt",
                message = "Dear Client , Thank you. Your payment of PKR 54,923.13 against availed financing facility has been received. For assistance , please call 111 002 002.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("54923.13"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Financing Payment"
                )
            ),
            ParserTestCase(
                name = "PKR Online banking debit without destination",
                message = "Dear Client, a transaction of PKR 252,195.77 has been completed on Account No. 0101xxx9901 on 17/12/25 using Online Banking.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("252195.77"),
                    currency = "PKR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9901"
                )
            ),
            ParserTestCase(
                name = "USD Debit card purchase",
                message = "Dear Client, USD 79.00 have been paid at Software Planet Group on 26-12-25 using Debit Card no. 53119xxxxxxxx1640.",
                sender = "9220",
                expected = ExpectedTransaction(
                    amount = BigDecimal("79.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Software Planet Group",
                    isFromCard = true,
                    accountLast4 = "1640"
                )
            )
        )

        val handleChecks = listOf(
            "VM-SCBANK-S" to true,
            "VD-SCBANK-S" to true,
            "JK-SCBANK-S" to true,
            "SCBANK" to true,
            "StanChart" to true,
            "STANCHART" to true,
            "AX-SCBANK-T" to true,
            "9220" to true,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Standard Chartered Bank Parser Tests"
        )
    }
}
