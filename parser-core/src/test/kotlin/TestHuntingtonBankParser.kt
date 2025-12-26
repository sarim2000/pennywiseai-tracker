import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.HuntingtonBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class HuntingtonBankParserTest {

    @TestFactory
    fun `huntington bank parser handles expected scenarios`(): List<DynamicTest> {
        val parser = HuntingtonBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Huntington Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card withdrawal with positive balance",
                message = "Huntington Heads Up. We processed a debit card withdrawal: \$25.00 at Bob Inc. Acct CK0000 has a \$10.12 bal (10/19/25 5:43 AM ET).",
                sender = "Huntington Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Bob Inc",
                    accountLast4 = "0000",
                    balance = BigDecimal("10.12"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Debit card withdrawal with negative balance",
                message = "Huntington Heads Up. We processed a debit card withdrawal: \$20.00 at BC *UBER CASH. Acct CK0000 has a -\$15.01 bal (9/10/25 11:41 PM ET).",
                sender = "Huntington Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BC *UBER CASH",
                    accountLast4 = "0000",
                    balance = BigDecimal("-15.01"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "Huntington Heads Up. We processed an ATM withdrawal: \$162.45 at POS John Inc. Acct CK0000 has a \$20.20 bal (9/03/25 12:12 PM ET).",
                sender = "Huntington Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("162.45"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "POS John Inc",
                    accountLast4 = "0000",
                    balance = BigDecimal("20.20"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ACH withdrawal",
                message = "Huntington Heads Up. We processed an ACH withdrawal: \$50.67 at GEICO           . Acct CK0000 has a \$6211.32 bal (8/09/25 3:23 PM ET).",
                sender = "Huntington Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.67"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "GEICO",
                    accountLast4 = "0000",
                    balance = BigDecimal("6211.32"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Different account number",
                message = "Huntington Heads Up. We processed a debit card withdrawal: \$100.50 at AMAZON.COM. Acct CK1234 has a \$500.00 bal (12/01/25 2:30 PM ET).",
                sender = "HUNTINGTON",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.50"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON.COM",
                    accountLast4 = "1234",
                    balance = BigDecimal("500.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Large amount with comma",
                message = "Huntington Heads Up. We processed a debit card withdrawal: \$1,250.00 at BEST BUY. Acct CK5678 has a \$3,500.75 bal (11/15/25 10:00 AM ET).",
                sender = "Huntington Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BEST BUY",
                    accountLast4 = "5678",
                    balance = BigDecimal("3500.75"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ACH withdrawal from utility company",
                message = "Huntington Heads Up. We processed an ACH withdrawal: \$125.00 at ELECTRIC COMPANY. Acct CK9999 has a \$1000.00 bal (10/01/25 8:00 AM ET).",
                sender = "HUNTINGTON",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "ELECTRIC COMPANY",
                    accountLast4 = "9999",
                    balance = BigDecimal("1000.00"),
                    isFromCard = false
                )
            )
        )

        val handleChecks = listOf(
            "Huntington Bank" to true,
            "HUNTINGTON" to true,
            "huntington" to true,
            "US-HUNTINGTON-A" to true,
            "UNKNOWN" to false,
            "CHASE" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Huntington Bank Parser Tests"
        )


    }
}
