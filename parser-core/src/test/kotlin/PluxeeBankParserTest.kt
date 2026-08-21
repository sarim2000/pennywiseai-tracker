import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.PluxeeBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PluxeeBankParserTest {

    @TestFactory
    fun `pluxee parser handles common cases`(): List<DynamicTest> {
        val parser = PluxeeBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Meal wallet spend at merchant",
                message = "Rs. 40.00 spent from Pluxee  Meal wallet, card no.xx1234 on 17-08-2026 16:03:14 at NEW SHAKTHI  . Avl bal Rs.24342.81. Not you call 18002161234",
                sender = "AD-PLUXEE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("40.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "NEW SHAKTHI",
                    accountLast4 = "1234",
                    balance = BigDecimal("24342.81"),
                    isFromCard = true
                )
            )
        )

        val handleChecks = listOf(
            "AD-PLUXEE" to true,
            "VM-PLUXEE-S" to true,
            "JD-PLUXEE-T" to true,
            "PLUXEE" to true,
            "AD-SODEXO" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Pluxee Bank Parser")
    }
}
