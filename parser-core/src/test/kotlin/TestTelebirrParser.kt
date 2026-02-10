import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.TelebirrParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class TelebirrParserTest {

    @TestFactory
    fun `telebirr parser handles credit debit and transfer`(): List<DynamicTest> {
        val parser = TelebirrParser()

        ParserTestUtils.printTestHeader(
            parserName = "Telebirr",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Received telebirr transaction",
                message = "Dear [Name] You have received ETB 1,000.00 from PERSON NME(2519****2078)  on 31/01/2026 16:51:21. Your transaction number is DAV4D0PVWS. Your current E-Money Account balance is ETB 9,719.23. Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "PERSON NME (2519****2078)",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("9719.23"),
                    reference = "DAV4D0PVWS"
                )
            ),
            ParserTestCase(
                name = "Bank transfer expense transaction",
                message = "Dear [Name] You have transferred ETB 600.00 successfully from your telebirr account 251955555559 to Commercial Bank of Ethiopia account number 1000111111115 on 02/02/2026 12:47:23. Your telebirr transaction number is DB26EO8R6W and your bank transaction number is FT2603327H99. The service fee is  ETB 5.22 and  15% VAT on the service fee is ETB 0.78. Your current balance is ETB 334.23. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DB26EO8R6W Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("600.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Commercial Bank of Ethiopia account number 1000111111115",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("334.23"),
                    reference = "FT2603327H99"
                )
            ),
            ParserTestCase(
                name = "Sent telebirr transaction",
                message = "Dear [Name] You have transferred ETB 775.00 to Person Name (2519****4211) on 02/02/2026 11:35:20. Your transaction number is DB25ELPHRL. The service fee is  ETB 3.48 and  15% VAT on the service fee is ETB 0.52. Your current E-Money Account  balance is ETB 8,940.23. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DB25ELPHRL. Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("775.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Person Name (2519****4211)",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("8940.23"),
                    reference = "DB25ELPHRL"
                )
            ),
            ParserTestCase(
                name = "Bank transfer income transaction",
                message = "Dear [Name], You have received  ETB 1,200.00 by transaction number DAV5COORPD on 2026-01-31 11:26:23 from Zemen Bank to your telebirr Account 251911111119 - [Name]. Your current balance is ETB 1,423.23. Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "Zemen Bank",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("1423.23"),
                    reference = "DAV5COORPD"
                )
            ),
            ParserTestCase(
                name = "Government payment expense transaction",
                message = "Dear [Name] You have paid ETB 1,080.00 to 519680 - City Government of Adis Ababa Driver and Vehicle Licensing and Control Authority Drivers Bole Branch; for Driver Service Payment with payment order 2934245 on 03/02/2026 13:47:22. The service fee is  ETB 5.40 and  15% VAT on the service fee is ETB 0.81. Your transaction number is DB37FSJ0PP Your telebirr account balance is  ETB 496.04.To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DB37FSJ0PP  Thank you for using telebirr   Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1080.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "519680 - City Government of Adis Ababa Driver and Vehicle Licensing and Control Authority Drivers Bole Branch; for Driver Service Payment with payment order 2934245 ",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("496.04"),
                    reference = "DB37FSJ0PP"
                )
            ),
            ParserTestCase(
                name = "merchant payment expense transaction",
                message = "Dear [Name] You have paid ETB 1,569.99 for goods purchased from 521902 - SAMUEL STEPHANET Gateau ECA Branch on 16/01/2026 16:47:34. Your transaction number is  DAG2V7537E. Your current balance is ETB 6,512.23. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DAG2V7537E Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1569.99"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "521902 - SAMUEL STEPHANET Gateau ECA Branch",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("6512.23"),
                    reference = "DAG2V7537E"
                )
            ),
            ParserTestCase(
                name = "airtime payment expense transaction",
                message = "Dear [Name] You have paid ETB 875.00 for package Monthly 240Min + 24GB Data purchase made for 911111119 on 05/02/2026 13:36:33. Your transaction number is  DB59HV01HN. Your current balance is ETB 41.23.To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DB59HV01HN Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("875.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Monthly 240Min + 24GB Data purchase made for 911111119 ",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("41.23"),
                    reference = "DB59HV01HN"
                )
            ),
            ParserTestCase(
                name = "savings transfer expense transaction",
                message = "Dear [Name] You have successfully deposited ETB 10000.00 to your Saving Account on 31/01/2026 12:05:27. Your telebirr transaction number is DAV0CQ2NFC. Your current Saving balance is ETB 12050.63 and Your current telebirr Account balance is ETB 82.25. Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Saving Account",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("82.25"),
                    reference = "DAV0CQ2NFC"
                )
            ),
            ParserTestCase(
                name = "savings transfer income transaction",
                message = "Dear [Name], You have successfully Withdraw ETB 1800.00 from your saving account on 06/02/2026 10:19:02. Your transaction number is DB69IPQY2T. Your current saving balance is ETB 6110.00 and Your current e-money account balance is ETB 1,841.23. Thank you for using telebirr Ethio telecom",
                sender = "127",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1800.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "saving account",
                    accountLast4 = "[Name]",
                    balance = BigDecimal("1841.23"),
                    reference = "DB69IPQY2T"
                )
            ),
        )

        val handleChecks = listOf(
            "127" to true,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Telebirr Parser"
        )


    }
}
