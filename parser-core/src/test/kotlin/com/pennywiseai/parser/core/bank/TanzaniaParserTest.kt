package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

/**
 * Test suite for Tanzanian mobile money parsers:
 * - Selcom Pesa
 * - M-Pesa Tanzania (Vodacom)
 * - Tigo Pesa / Mixx by Yas
 */
class TanzaniaParserTest {

    // ==========================================
    // Selcom Pesa Tests
    // ==========================================

    @TestFactory
    fun `selcom pesa parser handles key paths`(): List<DynamicTest> {
        val parser = SelcomPesaParser()

        ParserTestUtils.printTestHeader(
            parserName = "Selcom Pesa (Tanzania)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Incoming Transfer / Cash-In",
                message = "0426JXCX Confirmed. You have received TZS 175,000.00 from MICHAEL EMIL LUYANGI - NMB (201100XXXXX) on 2025-04-26 11:50. Updated balance is TZS 175,000.00. Help 0800 714 888 / 0800 784 888",
                sender = "Selcom Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("175000.00"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "MICHAEL EMIL LUYANGI",
                    balance = BigDecimal("175000.00"),
                    reference = "0426JXCX"
                )
            ),
            ParserTestCase(
                name = "Outgoing Transfer with Tax Breakdown",
                message = "0426JXGC Accepted. You have sent TZS 50,000.00 to NURU ISSA - Mixx by Yas (Tigo Pesa) (25571XXXXXXX) on 2025-04-26 11:56. Total charges TZS 550.00 (Fee 424, VAT 84, Ex Duty 42). Updated balance is TZS 124,450.00. Help 0800 714 888 / 0800 784 888",
                sender = "Selcom Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "NURU ISSA",
                    balance = BigDecimal("124450.00"),
                    reference = "0426JXGC"
                )
            ),
            ParserTestCase(
                name = "ATM Withdrawal",
                message = "10234C2WQ Confirmed. You have withdrawn TZS 200,000.00 at ATM - TEMEKE BRANCH using your card ending with 8318 on 2025-10-23 18:00. Total charges TZS 2,500.00 (Fee 1,926, VAT 381, Ex Duty 193). Govt Levy TZS ( (resp govtLevy ) ). Updated balance is TZS 2,264,749.05. Help 0800 714 888 / 0800 784 888",
                sender = "Selcom Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM - TEMEKE BRANCH",
                    accountLast4 = "8318",
                    balance = BigDecimal("2264749.05"),
                    reference = "10234C2WQ",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Merchant Card Payment",
                message = "0428KRRY Confirmed. You have paid TZS 8,900.00 to APPLECOMBILL using your card ending 1915 on 2025-04-28 11:36. Updated balance is TZS 1,650.00. Help 0800 714 888 / 0800 784 888",
                sender = "Selcom Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8900.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "APPLECOMBILL",
                    accountLast4 = "1915",
                    balance = BigDecimal("1650.00"),
                    reference = "0428KRRY",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Promotional / Free Transaction",
                message = "0426JXSG Accepted. You have sent TZS 80,000.00 to CATHERINE MINJA - Airtel Money (255694XXXXXX) for Taka April 2025 on 2025-04-26 12:10. Charge is FREE. Transaction 1 of 5 kwa Jero. Updated balance is TZS 550.00. Help 0800 714 888 / 0800 784 888",
                sender = "Selcom Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "CATHERINE MINJA",
                    balance = BigDecimal("550.00"),
                    reference = "0426JXSG"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    // ==========================================
    // M-Pesa Tanzania Tests
    // ==========================================

    @TestFactory
    fun `mpesa tanzania parser handles key paths`(): List<DynamicTest> {
        val parser = MPesaTanzaniaParser()

        ParserTestUtils.printTestHeader(
            parserName = "M-Pesa Tanzania (Vodacom)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Received Money",
                message = "SGR1234567 Confirmed. You have received TZS 50,000.00 from JOHN DOE (255754XXXXXX) on 2025-05-12 at 10:30 AM. New M-Pesa balance is TZS 150,000.00.",
                sender = "M-PESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    balance = BigDecimal("150000.00"),
                    reference = "SGR1234567"
                )
            ),
            ParserTestCase(
                name = "Sent Money / P2P",
                message = "SGR9876543 Confirmed. TZS 20,000.00 sent to JANE SMITH (255762XXXXXX) on 2025-05-12 at 11:45 AM. Transaction cost TZS 500.00. New M-Pesa balance is TZS 129,500.00.",
                sender = "M-PESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "JANE SMITH",
                    balance = BigDecimal("129500.00"),
                    reference = "SGR9876543"
                )
            ),
            ParserTestCase(
                name = "Lipa kwa M-Pesa / Merchant",
                message = "SGR5544332 Confirmed. TZS 15,000.00 paid to SUPERMARKET X (Merchant ID: 556677) on 2025-05-13 at 08:20 PM. Transaction cost TZS 0.00. New M-Pesa balance is TZS 114,500.00.",
                sender = "M-PESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "SUPERMARKET X",
                    balance = BigDecimal("114500.00"),
                    reference = "SGR5544332"
                )
            ),
            ParserTestCase(
                name = "LUKU / Utility Payment",
                message = "SGR1122334 Confirmed. TZS 10,000.00 paid to LUKU for account 1423XXXXXXX. Token: 1234-5678-9012-3456-7890. Transaction cost TZS 0.00. New M-Pesa balance is TZS 104,500.00.",
                sender = "M-PESA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "LUKU",
                    balance = BigDecimal("104500.00"),
                    reference = "SGR1122334"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    // ==========================================
    // Tigo Pesa Tests
    // ==========================================

    @TestFactory
    fun `tigo pesa parser handles key paths`(): List<DynamicTest> {
        val parser = TigoPesaParser()

        ParserTestUtils.printTestHeader(
            parserName = "Tigo Pesa / Mixx by Yas (Tanzania)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Cash-In from Agent",
                message = "Cash-In of TSh 100,000 from Agent - LUCY SUKUM is successful. New balance is TSh 100,000. TxnId: 13411949026. 16/08/23 15:19. Dial150 01# or use Tigo Pesa App. No Levy while sending money with Tigo Pesa",
                sender = "TIGOPESA(smsfp)",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "Agent - LUCY SUKUM",
                    balance = BigDecimal("100000"),
                    reference = "13411949026"
                )
            ),
            ParserTestCase(
                name = "Sent Money with Detailed Charges",
                message = "You have sent TSh 25,000 with CashOut fee TSh 2,156 to 255713XXXXXX - BENEDICTA MREMA. Total Charges TSh 380.(Fees TSh 380, Levy TSh 0), VAT TSh 58. TxnID: 27755640833. 14/08/23 14:55. New balance is TSh 481,801. Thank you for using Tigo Pesa.",
                sender = "TIGOPESA(smsfp)",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "BENEDICTA MREMA",
                    balance = BigDecimal("481801"),
                    reference = "27755640833"
                )
            ),
            ParserTestCase(
                name = "Merchant Payment / Lipa",
                message = "You have paid TSh 131,000 to DIAPERS AND WIPES SUPPLIERS. Charges TSh 2,000. VAT TSh 305. Trnx ID: 63425443091. 19/08/23 11:20. Your New balance is TSh 467,372. Thank you for using Tigo Pesa.",
                sender = "TIGOPESA(smsfp)",
                expected = ExpectedTransaction(
                    amount = BigDecimal("131000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "DIAPERS AND WIPES SUPPLIERS",
                    balance = BigDecimal("467372"),
                    reference = "63425443091"
                )
            ),
            ParserTestCase(
                name = "Incoming TIPS / Bank Transfer",
                message = "Transfer Successful. New balance is TSh 97,000. You have received TSh 97,000 from TIPS.Selcom_MFB.2.Tigo, with TxnId: 25693126312543. 035_12307E6LF. 30/12/25 12:57.",
                sender = "MIXX BY YAS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("97000"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "Selcom (TIPS Transfer)",
                    balance = BigDecimal("97000"),
                    reference = "25693126312543"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    // ==========================================
    // Factory Tests
    // ==========================================

    @TestFactory
    fun `factory resolves tanzanian mobile money services`(): List<DynamicTest> {
        val cases = listOf(
            // Selcom Pesa
            SimpleTestCase(
                bankName = "Selcom Pesa",
                sender = "Selcom Pesa",
                currency = "TZS",
                message = "0426JXCX Confirmed. You have received TZS 175,000.00 from MICHAEL EMIL LUYANGI - NMB (201100XXXXX) on 2025-04-26 11:50. Updated balance is TZS 175,000.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("175000.00"),
                    currency = "TZS",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            // Tigo Pesa
            SimpleTestCase(
                bankName = "Tigo Pesa",
                sender = "TIGOPESA(smsfp)",
                currency = "TZS",
                message = "Cash-In of TSh 100,000 from Agent - LUCY SUKUM is successful. New balance is TSh 100,000. TxnId: 13411949026.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000"),
                    currency = "TZS",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            // Mixx by Yas (Tigo Pesa rebranding)
            SimpleTestCase(
                bankName = "Tigo Pesa",
                sender = "MIXX BY YAS",
                currency = "TZS",
                message = "Transfer Successful. New balance is TSh 97,000. You have received TSh 97,000 from TIPS.Selcom_MFB.2.Tigo, with TxnId: 25693126312543.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("97000"),
                    currency = "TZS",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Tanzanian Mobile Money Factory Tests")
    }
}
