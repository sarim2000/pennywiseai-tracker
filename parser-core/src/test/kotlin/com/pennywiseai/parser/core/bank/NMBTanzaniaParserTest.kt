package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NMBTanzaniaParserTest {

    @TestFactory
    fun `NMB Tanzania parser handles bilingual transactions`(): List<DynamicTest> {
        val parser = NMBTanzaniaParser()

        val cases = listOf(
            // 1) Loan repayment (Swahili) with doubled "TZS TZS".
            ParserTestCase(
                name = "Loan repayment (doubled TZS TZS)",
                message = "201NDGL261360514. Kiasi cha TZS TZS 263.36 kimetolewa kwenye akaunti yako inayoishia na XXXXX kurejesha Mshiko Fasta, Salio la mkopo ni 335,556.64 15-06-2026 03:45 Rejesha kwa wakati upate kiwango cha juu zaidi. NMB karibu yako",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("263.36"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Mshiko Fasta",
                    reference = "201NDGL261360514"
                )
            ),

            // 2) P2P transfer out (Swahili).
            ParserTestCase(
                name = "P2P transfer out",
                message = "Kumb: GWX102237382945 Imethibitishwa. Kiasi cha TSH8,000 kimetumwa kutoka katika akaunti inayoishia na XXXX kwenda JOHN DOE 07XXXXXXXX. Tarehe:05-06-2026 19:46:00. Teleza Kidigitali na Mshiko Fasta",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "GWX102237382945"
                )
            ),

            // 3) Merchant payment (English).
            ParserTestCase(
                name = "Merchant payment (English)",
                message = "You have paid TZS 85000 with account ending XXXX to LOCAL SUPERMARKET 01 15293743 on 31-05-2026 19:28:03. NMB Karibu Yako",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("85000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "LOCAL SUPERMARKET 01"
                )
            ),

            // 4) Inbound voucher / remittance (Swahili).
            ParserTestCase(
                name = "Inbound voucher (NMB Pesa Fasta)",
                message = "GWX_1780199859027.Umepokea Tsh 50,000 kupitia NMB Pesa Fasta 31/05/2026 06:57:39. Tumia namba ya siri XXXXX kutoa pesa NMB ATM kabla ya 2026-06-01 06:57:39.",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "NMB Pesa Fasta",
                    reference = "GWX_1780199859027"
                )
            ),

            // 5) Loan disbursement (Swahili).
            ParserTestCase(
                name = "Loan disbursement",
                message = "201NDGL261500637. Umepokea kiasi cha TZS 209,236.55 kupitia Mshiko Fasta Kulipwa hadi 28-AUG-26. Gharama 33,059.45 30-MAY-26, Kopa na lipa kwa wakati uweze kukopa kiwango cha juu zaidi . NMB karibu yako",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("209236.55"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "Mshiko Fasta",
                    reference = "201NDGL261500637"
                )
            ),
            // 6) Plural "TShs" currency notation — the gate accepts it, so the amount
            // regex must too (kept in sync).
            ParserTestCase(
                name = "P2P transfer out (TShs plural notation)",
                message = "Kumb: GWX102237382946 Imethibitishwa. Kiasi cha TShs 8,000 kimetumwa kutoka katika akaunti inayoishia na XXXX kwenda JANE DOE 07XXXXXXXX. Tarehe:05-06-2026 19:46:00.",
                sender = "NMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "JANE DOE",
                    reference = "GWX102237382946"
                )
            )
        )

        val handleChecks = listOf(
            "NMB" to true,
            "NMB_ALERT" to true,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "NMB Bank Tanzania Parser")
    }

    @TestFactory
    fun `factory routes NMB senders by content`(): List<DynamicTest> {
        // A Tanzania NMB message must route to "NMB Bank Tanzania".
        val tzMessage = "201NDGL261500637. Umepokea kiasi cha TZS 209,236.55 kupitia Mshiko Fasta Kulipwa hadi 28-AUG-26. Gharama 33,059.45 30-MAY-26 . NMB karibu yako"

        // A Nepal-format NMB message must still route to the Nepal "NMB Bank" parser.
        // Reused from the existing Nepal NMB test corpus.
        val nepalMessage = "Fund transfer of NPR 250.00 to A/C 01000000055 was successful on 19-Feb-2025 15:38:23 If you have not done this transfer please contact us immediately."

        return listOf(
            dynamicTest("Tanzania NMB routes to NMB Bank Tanzania") {
                val parsed = BankParserFactory.parse(tzMessage, "NMB", System.currentTimeMillis())
                assertNotNull(parsed, "Tanzania NMB message did not parse")
                assertEquals("NMB Bank Tanzania", parsed!!.bankName)
                assertEquals("TZS", parsed.currency)
                assertEquals(TransactionType.INCOME, parsed.type)
                assertEquals(BigDecimal("209236.55"), parsed.amount)
            },
            dynamicTest("Nepal NMB still routes to NMB Bank") {
                val parsed = BankParserFactory.parse(nepalMessage, "NMB", System.currentTimeMillis())
                assertNotNull(parsed, "Nepal NMB message did not parse")
                assertEquals("NMB Bank", parsed!!.bankName)
                assertEquals("NPR", parsed.currency)
            }
        )
    }
}
