import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ZemenBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ZemenBankParserTest {

    @TestFactory
    fun `zemen bank parser handles credit debit and transfer`(): List<DynamicTest> {
        val parser = ZemenBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Zemen Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Received telebirr transaction",
                message = "Dear Customer your account 109xxxxxxxx7018 has been credited with ETB 10000 from telebirr wallet 251922222222 with reference 109TEIN260350016 on 4-Feb-2026. Your Current Balance is ETB 10823.37. Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "telebirr wallet 251922222222",
                    accountLast4 = "7018",
                    balance = BigDecimal("10823.37"),
                    reference = "109TEIN260350016"
                )
            ),
            ParserTestCase(
                name = "Bank transfer expense transaction",
                message = "Dear Customer, Birr 100  IB Fund transfer has been made from A/c No. 174xxxxxxxx7012 to A/c of 1091410062087018 on 30-Sep-2025 . The A/c Available Bal. is Birr  293.06  and the transaction Branch is Kara Menged Branch. To download your payment information click this link https://share.zemenbank.com/rt/49927012174IBFT252730003/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "1091410062087018",
                    accountLast4 = "7012",
                    balance = BigDecimal("293.06"),
                    reference = "https://share.zemenbank.com/rt/49927012174IBFT252730003/pdf"
                )
            ),
            ParserTestCase(
                name = "Sent telebirr transaction",
                message = "Dear Customer your Account 174xxxxxxxx7012 has been debited with ETB 6000 to telebirr wallet 955555559 with reference 174ETTB252460007 on 3-Sep-2025. Your Current Balance is ETB 393.06. To download your payment information click this link https://share.zemenbank.com/rt/74547012174ETTB252460007/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "telebirr wallet 955555559",
                    accountLast4 = "7012",
                    balance = BigDecimal("393.06"),
                    reference = "174ETTB252460007"
                )
            ),
            ParserTestCase(
                name = "Bank transfer income transaction",
                message = "Dear Customer your account 109xxxxxxxx7018 has been credited with ETB 13000 from other bank with reference 109P2PI252120016 on 31-Jul-2025. Your Current Balance is ETB 33824.78. Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("13000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "other bank",
                    accountLast4 = "7018",
                    balance = BigDecimal("33824.78"),
                    reference = "109P2PI252120016"
                )
            ),
            ParserTestCase(
                name = "POS transaction expense known POS system",
                message = "Dear Customer, Your account (109xxxxxxxx7018) has been debited with Birr 1593.9 for a POS purchase transaction at LUNA EXPORT SLAGHTER HOUSE PLC/FRESH CORNER on 27-Aug-2025. Your transaction reference number is 108BALE25239003B. Your available balance is Birr 1942.01. For further assistance, please call 6500. To download your payment information click this link https://share.zemenbank.com/rt/63987018108BALE25239003B/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1593.90"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "LUNA EXPORT SLAGHTER HOUSE PLC/FRESH CORNER",
                    accountLast4 = "7018",
                    balance = BigDecimal("1942.01"),
                    reference = "108BALE25239003B"
                )
            ),
            ParserTestCase(
                name = "POS transaction expense unknown POS system",
                message = "Dear Customer, Birr 2839.29 POS transaction has been made from A/c No. 109xxxxxxxx7018 on 1-Sep-2025 .The A/c Available Bal. is Birr 2677.28 and transaction POS location is  other bank POS. To download your payment information click this link https://share.zemenbank.com/rt/83687018108PORE2524400C6/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2839.29"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "other bank POS",
                    accountLast4 = "7018",
                    balance = BigDecimal("2677.28"),
                    reference = "https://share.zemenbank.com/rt/83687018108PORE2524400C6/pdf"
                )
            ),
            ParserTestCase(
                name = "external bank transfer expense transaction",
                message = "Dear Customer your account 109xxxxxxxx7018 has been debited with ETB 2400 to PERSON LAST NAME (1000111111114), COMMERCIAL BANK OF ETHIOPIA with reference 108IBET252581026 on 15-Sep-2025. Your Current Balance is ETB 65.66. To download your payment information click this link https://share.zemenbank.com/ft/78827018108IBET252581026/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2400.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "PERSON LAST NAME (1000111111114), COMMERCIAL BANK OF ETHIOPIA",
                    accountLast4 = "7018",
                    balance = BigDecimal("65.66"),
                    reference = "108IBET252581026"
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal expense transaction",
                message = "Dear Customer, Birr 100 ATM cash withdrawal has been made from A/c No. 109xxxxxxxx7018 on 30-Sep-2025 . The A/c Available Bal. is Birr 65.66 and transaction ATM location is Kara Menged BC ATM . To download your payment information click this link https://share.zemenbank.com/rt/61077018108ATCW25273011N/pdf Thank you for banking with Zemen Bank",
                sender = "Zemen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Kara Menged BC ATM",
                    accountLast4 = "7018",
                    balance = BigDecimal("65.66"),
                    reference = "https://share.zemenbank.com/rt/61077018108ATCW25273011N/pdf"
                )
            ),
        )

        val handleChecks = listOf(
            "Zemen Bank" to true,
            "zemen bank" to true,
            "ZEMEN BANK" to true,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Zemen Bank Parser"
        )


    }
}
