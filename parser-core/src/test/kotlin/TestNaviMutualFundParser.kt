import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.NaviMutualFundParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NaviMutualFundParserTest {

    @TestFactory
    fun `navi mutual fund parser covers SIP unit-allotment scenarios`(): List<DynamicTest> {
        val parser = NaviMutualFundParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SIP unit allotment — Nifty Next 50 Index Fund DG",
                message = "Unit Allotment Update:\nYour SIP purchase of Rs.499.98 in Navi Nifty Next 50 Index Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days. For further queries, please visit the Navi app.\nTeam Navi Mutual Fund",
                sender = "AD-NAVAMC-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("499.98"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    merchant = "Navi Nifty Next 50 Index Fund DG"
                )
            ),
            ParserTestCase(
                name = "SIP unit allotment — Largecap with Indian-style commas",
                message = "Unit Allotment Update:\nYour SIP purchase of Rs. 1,49,999.98 in Navi Largecap Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days.\nTeam Navi Mutual Fund",
                sender = "VK-NAVAMC-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("149999.98"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    merchant = "Navi Largecap Fund DG"
                )
            ),
            ParserTestCase(
                name = "Non-allotment NAV update is rejected",
                message = "NAV updated for Navi Largecap Fund. Latest NAV: 12.3456. Visit the Navi app for details.",
                sender = "AD-NAVAMC-S",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "AD-NAVAMC-S" to true,
            "VK-NAVAMC-T" to true,
            "ad-navamc-s" to true,    // case-insensitive
            "HDFCBK" to false,
            "AD-HDFCMF-AC" to false,
            "NAVI" to false,           // banking-arm sender must NOT match
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Navi Mutual Fund Parser Suite"
        )
    }
}
