import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.EverestBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class EverestBankParserTest {

    @TestFactory
    fun `everest bank parser handles debit credit and atm`(): List<DynamicTest> {
        val parser = EverestBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Everest Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit - Mobile recharge",
                message = "Dear Customer, Your A/c 12345678 is debited by NPR 520.00 For: 9843368/Mobile Recharge,Ncell. Never Share Password/OTP With Anyone",
                sender = "9843368",
                expected = ExpectedTransaction(
                    amount = BigDecimal("520.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "Mobile Recharge",
                    accountLast4 = "5678",
                    reference = "9843368"
                )
            ),
            ParserTestCase(
                name = "Credit - Salary",
                message = "Dear Customer, Your A/c 87654321 is credited by NPR 15,000.00 For: Salary Payment/Monthly Salary,UJJ SH. Never Share Password/OTP With Anyone",
                sender = "UJJ SH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "Monthly Salary",
                    accountLast4 = "4321"
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "Dear Customer, Your A/c 11223344 is debited by NPR 6,000.00 For: CWDR/521708008016/202508050854. Never Share Password/OTP With Anyone",
                sender = "CWRD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "3344",
                    reference = "521708008016/202508050854"
                )
            ),
            ParserTestCase(
                name = "Debit - Food delivery",
                message = "Dear Customer, Your A/c 99887766 is debited by NPR 1,250.00 For: 9801234567/Food Delivery,Foodmandu. Never Share Password/OTP With Anyone",
                sender = "9801234567",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "Food Delivery",
                    accountLast4 = "7766",
                    reference = "9801234567"
                )
            ),
            ParserTestCase(
                name = "Credit - Business payment",
                message = "Dear Customer, Your A/c 55443322 is credited by NPR 50,000.00 For: Business Payment/Invoice Settlement,ABC Company. Never Share Password/OTP With Anyone",
                sender = "9843001",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "Invoice Settlement",
                    accountLast4 = "3322"
                )
            ),
            ParserTestCase(
                name = "Debit - Ecommerce",
                message = "Dear Customer, Your A/c 33445566 is debited by NPR 750.00 For: Online Shopping/E-commerce,Daraz. Never Share Password/OTP With Anyone",
                sender = "EVEREST",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "E-commerce",
                    accountLast4 = "5566"
                )
            )
        )

        val handleChecks = listOf(
            "9843368" to true,
            "UJJ SH" to true,
            "CWRD" to true,
            "EVEREST" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Everest Bank Parser"
        )


    }
}
