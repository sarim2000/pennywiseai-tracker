package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BRACBankParserTest {

    @TestFactory
    fun `brac bank formats (#943)`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "ATM withdrawal",
                message = "TK 500.00 withdrawn from A/C#11111**1111 on 18-10-26 @12:24 PM from TEST MARKET ATM DHAKA BD. Balance TK 22,639.95. BRAC Bank.",
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "1111",
                    balance = BigDecimal("22639.95")
                )
            ),
            ParserTestCase(
                name = "Card purchase",
                message = cardPurchase,
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1920"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "TEST DIAGNOSTIC and DH",
                    accountLast4 = "2222",
                    balance = BigDecimal("98997.32"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Transfer to another account",
                message = transfer,
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Fund Transfer",
                    accountLast4 = "111",
                    balance = BigDecimal("17150.95")
                )
            ),
            ParserTestCase(
                name = "Credit from another bank",
                message = credit,
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("28500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "OTHER BANK",
                    accountLast4 = "3333",
                    balance = BigDecimal("37130.95")
                )
            ),
            ParserTestCase(
                name = "NPSB transfer debit",
                message = npsb,
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10010.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "NPSB Transfer",
                    accountLast4 = "4444",
                    balance = BigDecimal("66330.95")
                )
            ),
            ParserTestCase(
                name = "Other-bank ATM withdrawal",
                message = otherAtm,
                sender = "BRAC BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "5555",
                    balance = BigDecimal("81340.95")
                )
            ),
            ParserTestCase(
                name = "OTP is not a transaction",
                message = otp,
                sender = "BRAC BANK",
                shouldParse = false
            )
        )
        val handleCases = listOf(
            "BRAC BANK" to true,
            "BRACBANK" to true,
            "AD-BRACBNK" to true,
            "BKASH" to false,
            "" to false
        )
        return ParserTestUtils.runTestSuite(BRACBankParser(), cases, handleCases, "BRAC Bank")
    }

    @TestFactory
    fun `factory routes BRAC BANK`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "BRAC Bank",
                    sender = "BRAC BANK",
                    currency = "BDT",
                    message = credit,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("28500.00"),
                        currency = "BDT",
                        type = TransactionType.INCOME
                    ),
                    shouldHandle = true
                )
            ),
            "BRAC factory routing"
        )

    private val cardPurchase =
        "BDT 1,920 transacted at TEST DIAGNOSTIC and  DH on 18/10/26 [08:45:24 AM BST] using Card#***2222. Available balance: BDT 98,997.32. Helpline 16221."
    private val transfer =
        "Tk 4,000.00 has been transferred from your BBL A/C: 11111**111 to A/C: 9**9999. Available balance is Tk17,150.95. Queries: call 16221."
    private val credit =
        "TK 28,500.00 credited to A/C#11111**3333 on 12-10-26 @11:23 AM from OTHER BANK. Balance TK 37,130.95. BRAC Bank."
    private val npsb =
        "Tk 10,010.00 (incl. charges) has been debited from your BBL A/C: 11111**4444 to A/C: 9**9999 for NPSB transfer. Available balance: Tk. 66,330.95. Helpline:16221"
    private val otherAtm =
        "TK 10,000.00 withdrawn from A/C#11111**5555 on 01-10-26 @05:55 PM from OTHER BANK ATM. Balance TK 81,340.95. BRAC Bank."
    private val otp =
        "Your OTP for BRAC Bank transaction of TK 500.00 is 123456. Do not share it with anyone."
}
