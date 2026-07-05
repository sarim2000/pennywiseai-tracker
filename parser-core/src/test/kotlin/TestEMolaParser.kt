package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class EMolaParserTest {
    @TestFactory
    fun `emola parser handles common cases`(): List<DynamicTest> {
        val parser = EMolaParser()

        val cases = listOf(
            ParserTestCase(
                name = "Outgoing transfer - expense",
                message = "Transaction ID PP260530.0934.w91238. You transfered 100.00MT to 871234566, name: John Doe at 09:34:45 on 30/05/2026. Fee: 0.00MT. Your account balance is 3,123.45MT. Content: . In case of doubt, please call 100. Thank you!",
                sender = "eMola",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "John Doe",
                    reference = "PP260530.0934.w91238",
                    balance = BigDecimal("3123.45")
                )
            ),
            ParserTestCase(
                name = "Incoming transfer - income",
                message = "Transaction ID: PP260603.0854.K00983. You received 123.45MT from 871234567, name: JOHN DOE at 08:54:09 on 03/06/2026. Content: campo. Your account balance is 1,234.56MT. In case of doubt, please call 100. Thank you!",
                sender = "eMola",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "MZN",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    reference = "PP260603.0854.K00983",
                    balance = BigDecimal("1234.56")
                )
            ),
            ParserTestCase(
                name = "Agent withdrawal - expense",
                message = "Transaction ID: CO260528.1806.W15992. You withdrew 50.00MT in the Agent with code ID 123456, Name JOHN DOE at 18:06:25 on 28/05/2026. Fee: 3.00 MT. Your account balance is 1,234.23MT. In case of doubt, please call 100. Thank you!",
                sender = "eMola",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "CO260528.1806.W15992",
                    balance = BigDecimal("1234.23")
                )
            )
        )

        val handleChecks = listOf(
            "eMola" to true,
            "EMOLA" to true,
            "STDBank" to false,
            "Mbim" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "eMola Parser")
    }

    @Test
    fun `factory routes eMola sender to this parser`() {
        Assertions.assertTrue(
            BankParserFactory.getParser("eMola") is EMolaParser,
            "eMola should route to EMolaParser"
        )
    }

    @Test
    fun `eMola is flagged as a mobile-money wallet`() {
        Assertions.assertTrue(
            EMolaParser().isMobileWallet(),
            "eMola has a running balance but no account number, so it must be a wallet"
        )
        // The flag must reach the parsed result so the app can derive a wallet account.
        val parsed = EMolaParser().parse(
            "Transaction ID PP260530.0934.w91238. You transfered 100.00MT to 871234566, name: John Doe at 09:34:45 on 30/05/2026. Fee: 0.00MT. Your account balance is 3,123.45MT.",
            "eMola",
            0L
        )
        Assertions.assertNotNull(parsed)
        Assertions.assertTrue(parsed!!.isMobileWallet, "parsed eMola transaction must carry isMobileWallet=true")
    }
}
