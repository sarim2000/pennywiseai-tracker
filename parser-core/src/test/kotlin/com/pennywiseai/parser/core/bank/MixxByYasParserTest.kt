package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MixxByYasParserTest {

    @TestFactory
    fun `mixx by yas parser handles common cases`(): List<DynamicTest> {
        val parser = MixxByYasParser()

        val cases = listOf(
            // #1 Outbound P2P (PRIMARY of a twin pair)
            ParserTestCase(
                name = "Outbound P2P transfer (primary)",
                message = "Money sent successfully to  -255742311312. Amount TSh 52,000. Total Charges TSh 1,125, VAT TSh 172. New balance is TSh 0. TxnID: 26495371373758. Receipt: 503-DFE9B1DABO. 14/06/26 19:19. Every Transaction is a Winning Goal!",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("52000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Mobile Money Transfer",
                    balance = BigDecimal("0"),
                    reference = "26495371373758"
                )
            ),
            // #2 Inbound push transfer
            ParserTestCase(
                name = "Inbound push transfer",
                message = "Transfer Successful. New balance is TSh 15,000. You have received TSh 15,000 from CRDB; JOHN DOE with TxnId: 26452334860211. 003_19ec6e42e888a9a7. 14/06/26 19:08. Every Transaction is a Winning Goal!",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "CRDB",
                    balance = BigDecimal("15000"),
                    reference = "26452334860211"
                )
            ),
            // #3 Utility/PostPaid bill payment (PRIMARY)
            ParserTestCase(
                name = "PostPaid bill payment (primary)",
                message = "Txn Amt TSh 150,000 sent to Yas PostPaid (100100). Wait for confirmation. Ref: 0676263929. New Bal: TSh 0. Total Charges TSh 0.(Fees TSh 0, Levy TSh 0), VAT TSh 0. TxnID: 26952325632986. 13/06/26 19:41",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Yas PostPaid",
                    balance = BigDecimal("0"),
                    reference = "26952325632986"
                )
            ),
            // #4 Bustisha micro-loan repayment
            ParserTestCase(
                name = "Bustisha loan repayment",
                message = "You have successfully paid your Bustisha Balance by TSh 12,366.80. Your outstanding balance: TSh 0.00. New balance: TSh 137,633. TxnID: 26307515424020. Loan ID: 202606082034582035732170597903. 13/06/26 19:39.",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12366.80"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Bustisha",
                    balance = BigDecimal("137633"),
                    reference = "26307515424020"
                )
            ),
            // #5 Agent cash-out
            ParserTestCase(
                name = "Agent cash-out",
                message = "Cash Out of TSh 30,000 from Agent - AGENT NAME HERE is successful. Total Charges TSh 2,201.(Fees TSh 1,850, Levy TSh 351), VAT TSh 282. TxnID: 26106452201270. 08/06/26 17:36. New balance is TSh 5,879. Every Transaction is a Winning Goal!",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Agent Cash Out",
                    balance = BigDecimal("5879"),
                    reference = "26106452201270"
                )
            ),
            // #6 GePG government payment
            ParserTestCase(
                name = "GePG government payment",
                message = "Txn successful, Amt TSh 150,000 sent to Malipo ya Serikali (001001), Control No 994944606117. New Bal TSh 206. Charges TSh 2,000. TxnID: 26592309810022. 29/05/26 21:35.",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Malipo ya Serikali",
                    balance = BigDecimal("206"),
                    reference = "26592309810022"
                )
            ),
            // #7 LUKU electricity token (multi-line; amount on TOTAL line)
            ParserTestCase(
                name = "LUKU electricity token",
                message = "Payment Successful.47300267334\n9001261411323026601\n28.0KWH\n2634 3328 1950 3176 1023\nCost 8,196.73\nVAT 18% 1475.40\nEWURA 1% 81.97\nREA 3% 245.90\nTOTAL 10,000.00 21/05/26 13:23.LKS",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "LUKU"
                )
            ),
            // #8 Mixx interest reward
            ParserTestCase(
                name = "Mixx interest reward",
                message = "Dear Customer, New balance is TSh 414. You have received TSh 414 from MIXX BY YAS as your Mixx interest. TxnId: 26406495404220. 12/06/26 12:11. Ikiingia tu Golii!",
                sender = "MixxByYas",
                expected = ExpectedTransaction(
                    amount = BigDecimal("414"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "Mixx Interest",
                    balance = BigDecimal("414"),
                    reference = "26406495404220"
                )
            ),
            // #9 SECONDARY twin of #1 — must NOT parse (would double-count TxnID)
            ParserTestCase(
                name = "Secondary outbound twin is rejected",
                message = "You have sent TSh 52,000 to Vodacom receiver JOHN DOE - 255742311312. Charges TSh 1,125. VAT TSh 172. New balance is TSh 0. TxnID: 26495371373758. 14/06/26 19:19 Please wait for confirmation. Every Transaction is a Winning Goal!",
                sender = "MixxByYas",
                shouldParse = false
            ),
            // #10 SECONDARY twin of #3 — content-less success ack, must NOT parse
            ParserTestCase(
                name = "Content-less success ack is rejected",
                message = "Payment Successful.Dear Customer your transaction is successfull. PostPaid TxnID TPIL123456789 Mixx by Yas TxnID 26952325632986 13/06/26 19:41.LKS",
                sender = "MixxByYas",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "MixxByYas" to true,
            "MIXX BY YAS" to true,
            "AB-MIXXBYYAS-S" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Mixx by Yas Parser")
    }

    @TestFactory
    fun `factory routes mixx senders without double counting twins`(): List<DynamicTest> {
        val ts = System.currentTimeMillis()
        return listOf(
            // PRIMARY outbound routes to Mixx by Yas.
            dynamicTest("Primary outbound routes to Mixx by Yas") {
                val p = BankParserFactory.parse(
                    "Money sent successfully to  -255742311312. Amount TSh 52,000. New balance is TSh 0. TxnID: 26495371373758. 14/06/26 19:19.",
                    "MIXX BY YAS", ts
                )
                assertEquals("Mixx by Yas", p?.bankName)
                assertEquals(BigDecimal("52000"), p?.amount)
            },
            // SECONDARY twin must not be booked by ANY parser (no double-count).
            dynamicTest("Secondary outbound twin is not booked by any parser") {
                val p = BankParserFactory.parse(
                    "You have sent TSh 52,000 to Vodacom receiver JOHN DOE - 255742311312. New balance is TSh 0. TxnID: 26495371373758. 14/06/26 19:19 Please wait for confirmation.",
                    "MIXX BY YAS", ts
                )
                assertNull(p, "Twin secondary should not be booked: $p")
            },
            // Legacy TIPS transfer stays with Tigo Pesa via content-aware dispatch.
            dynamicTest("Legacy TIPS transfer stays with Tigo Pesa") {
                val p = BankParserFactory.parse(
                    "Transfer Successful. New balance is TSh 97,000. You have received TSh 97,000 from TIPS.Selcom_MFB.2.Tigo, with TxnId: 25693126312543.",
                    "MIXX BY YAS", ts
                )
                assertEquals("Tigo Pesa", p?.bankName)
            }
        )
    }
}
