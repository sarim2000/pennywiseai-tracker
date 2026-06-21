package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class DiamondTrustBankParserTest {

    @TestFactory
    fun `Diamond Trust Bank parser handles common cases`(): List<DynamicTest> {
        val parser = DiamondTrustBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "TIPS outgoing success",
                message = "Dear JOHN DOE, TIPS transaction of TZS 120000.00 from XXXXXXX to 07XXXXXXXX has been successfully processed Ref 52950397 Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "TIPS Transfer",
                    reference = "52950397",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "ALERT debit - mobile banking charges",
                message = "ALERT: Your account no. XXXXXXX has been debited with TZS 1000 for MOBILE BANKING TXN CHARGES on 22/06/2026.Thank you. Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Mobile Banking Txn Charges",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "ALERT credit - internal funds transfer",
                message = "ALERT: Your account no. XXXXXXX has been credited with TZS 500000 for ONLINE INTERNAL FUNDS TRANSFER on 22/06/2026.Thank you. Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500000"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    merchant = "Online Internal Funds Transfer",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "ALERT debit - POS transaction (card)",
                message = "ALERT: Your account no. XXXXXXX has been debited with TZS 49900 for POS TRANSACTION on 22/06/2026.Thank you. Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("49900"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Pos Transaction",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ALERT debit - ATM cash withdrawal (card)",
                message = "ALERT: Your account no. XXXXXXX has been debited with TZS 180000 for ATM CASH WITHDRAWAL on 06/10/2025.Thank you. Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("180000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Atm Cash Withdrawal",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ALERT debit - standing instruction",
                message = "ALERT: Your account no. XXXXXXX has been debited with TZS 100000 for STANDING INSTRUCTION on 10/06/2026.Thank you. Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "Standing Instruction",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "LUKU electricity token uses TOTAL line",
                message = "LUKU\nMETER OWNER NAME\nMeter XXXXXXXXXXX\nReceipt 9006261721549334171\nUnits 56.1kWh\nToken 5376 9223 7930 4023 8001\nCOST TZS 16,393.45\nVAT 18% TZS 2,950.81\nEWURA 1% TZS 163.93\nREA 3% TZS 491.81\nTRANS FEE TZS 0.00\nTOTAL TZS 20,000\nReference 1764992079 Diamond Trust Bank",
                sender = "DTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "LUKU",
                    reference = "1764992079",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Reject TIPS request intake (would double-count)",
                message = "Dear JOHN DOE, your Instant Payment transfer request for TZS 120,000.00 from XXXXXXX to 07XXXXXXXX, has been received successfully and is being processed. Ref No: 52950397.Thank you.Diamond Trust Bank",
                sender = "DTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Reject TANQR confirmation with no amount",
                message = "Dear Customer your TANQR transaction is successfully processed on 2026-06-21 10:11:56 Ref: 52942040 Diamond Trust Bank",
                sender = "DTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Reject GEPG confirmation with no amount",
                message = "Dear Customer your GEPG transaction is successfully processed on 2026-06-21 05:43:13 Ref: 52938931 Diamond Trust Bank",
                sender = "DTB",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "DTB" to true,
            "AB-DTB-S" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Diamond Trust Bank Parser")
    }
}
