package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ThailandBankParsersTest {

    // === Bangkok Bank (BBL) ===

    @TestFactory
    fun `bangkok bank parser handles key paths`(): List<DynamicTest> {
        val parser = BangkokBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "BBL ATM withdrawal (English)",
                message = "BBL: Withdrawal 2,000.00 THB from A/C x1234 via ATM on 21/01/26 14:32 Bal 15,820.45 THB",
                sender = "BBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("15820.45")
                )
            ),
            ParserTestCase(
                name = "BBL deposit (English)",
                message = "BBL: Deposit 5,000.00 THB to A/C x1234 on 21/01/26 16:01 Bal 20,820.45 THB",
                sender = "BBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("20820.45")
                )
            ),
            ParserTestCase(
                name = "BBL transfer out (Thai)",
                message = "BBL: โอนเงินออก 1,500.00 บาท บช x1234 คงเหลือ 19,320.45 บาท",
                sender = "BBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("19320.45")
                )
            )
        )

        val handleCases = listOf(
            Pair("BBL", true),
            Pair("BANGKOK BANK", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Bangkok Bank")
    }

    // === Kasikorn Bank (KBank) ===

    @TestFactory
    fun `kasikorn bank parser handles key paths`(): List<DynamicTest> {
        val parser = KasikornBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "KBank spending (English)",
                message = "KBank: You spent 1,250.00 THB at SHOPEE. A/C x5678 Bal 8,430.20 THB",
                sender = "KBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("8430.20")
                )
            ),
            ParserTestCase(
                name = "KBank receive (English)",
                message = "KBank: Receive 10,000.00 THB from A/C x5678 Bal 18,430.20 THB",
                sender = "KBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("18430.20")
                )
            ),
            ParserTestCase(
                name = "KBank PromptPay transfer (Thai)",
                message = "KBank: โอนเงินผ่านพร้อมเพย์ 500.00 บาท บช x5678 คงเหลือ 17,930.20 บาท",
                sender = "KBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("17930.20")
                )
            )
        )

        val handleCases = listOf(
            Pair("KBank", true),
            Pair("KASIKORN", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Kasikorn Bank")
    }

    // === Siam Commercial Bank (SCB) ===

    @TestFactory
    fun `siam commercial bank parser handles key paths`(): List<DynamicTest> {
        val parser = SiamCommercialBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SCB transfer out (English)",
                message = "SCB: Transfer out 3,500.00 THB to A/C x8899 Bal 6,200.00 THB",
                sender = "SCB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "8899",
                    balance = BigDecimal("6200.00")
                )
            ),
            ParserTestCase(
                name = "SCB transfer in (English)",
                message = "SCB: Transfer in 12,000.00 THB A/C x8899 Bal 18,200.00 THB",
                sender = "SCB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "8899",
                    balance = BigDecimal("18200.00")
                )
            ),
            ParserTestCase(
                name = "SCB card spending (Thai)",
                message = "SCB: ใช้จ่ายบัตร 890.00 บาท ร้าน 7-ELEVEN บช x8899",
                sender = "SCB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("890.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "8899",
                    merchant = "7-ELEVEN"
                )
            )
        )

        val handleCases = listOf(
            Pair("SCB", true),
            Pair("SIAM COMMERCIAL", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Siam Commercial Bank")
    }

    // === Krungthai Bank (KTB) ===

    @TestFactory
    fun `krungthai bank parser handles key paths`(): List<DynamicTest> {
        val parser = KrungThaiBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "KTB deposit (Thai)",
                message = "KTB: เงินเข้า 4,200.00 บาท บช x7788 คงเหลือ 12,350.75 บาท",
                sender = "KTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4200.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "7788",
                    balance = BigDecimal("12350.75")
                )
            ),
            ParserTestCase(
                name = "KTB ATM withdrawal (Thai)",
                message = "KTB: ถอนเงินสด 500.00 บาท บช x7788 คงเหลือ 11,850.75 บาท",
                sender = "KTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7788",
                    balance = BigDecimal("11850.75")
                )
            ),
            ParserTestCase(
                name = "KTB PromptPay receive (Thai)",
                message = "KTB: รับเงินพร้อมเพย์ 2,000.00 บาท บช x7788",
                sender = "KTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "7788"
                )
            )
        )

        val handleCases = listOf(
            Pair("KTB", true),
            Pair("KRUNGTHAI", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Krungthai Bank")
    }

    // === Krungsri (BAY) ===

    @TestFactory
    fun `krungsri bank parser handles key paths`(): List<DynamicTest> {
        val parser = KrungsriBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Krungsri ATM withdrawal (English)",
                message = "Krungsri: ATM withdrawal 1,000.00 THB A/C x3344 Bal 9,540.00 THB",
                sender = "BAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3344",
                    balance = BigDecimal("9540.00")
                )
            ),
            ParserTestCase(
                name = "Krungsri card payment (English)",
                message = "Krungsri: Card payment 890.00 THB at 7-ELEVEN A/C x3344",
                sender = "BAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("890.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3344",
                    merchant = "7-ELEVEN"
                )
            ),
            ParserTestCase(
                name = "Krungsri transfer in (Thai)",
                message = "Krungsri: โอนเงินเข้า 6,000.00 บาท บช x3344",
                sender = "BAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "3344"
                )
            )
        )

        val handleCases = listOf(
            Pair("BAY", true),
            Pair("KRUNGSRI", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Krungsri")
    }

    // === TTB ===

    @TestFactory
    fun `ttb bank parser handles key paths`(): List<DynamicTest> {
        val parser = TTBBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "TTB payment (English)",
                message = "ttb: Payment 1,500.00 THB via PromptPay A/C x9012 Bal 7,200.00 THB",
                sender = "TTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9012",
                    balance = BigDecimal("7200.00")
                )
            ),
            ParserTestCase(
                name = "TTB receive transfer (Thai)",
                message = "ttb: รับเงินโอน 8,000.00 บาท บช x9012",
                sender = "TTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "9012"
                )
            )
        )

        val handleCases = listOf(
            Pair("TTB", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "TTB")
    }

    // === Government Savings Bank (GSB) ===

    @TestFactory
    fun `gsb bank parser handles key paths`(): List<DynamicTest> {
        val parser = GSBBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "GSB deposit (Thai)",
                message = "GSB: เงินฝากเข้า 2,500.00 บาท บช x1122 คงเหลือ 9,300.00 บาท",
                sender = "GSB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "1122",
                    balance = BigDecimal("9300.00")
                )
            ),
            ParserTestCase(
                name = "GSB withdrawal (Thai)",
                message = "GSB: ถอนเงิน 1,000.00 บาท บช x1122",
                sender = "GSB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1122"
                )
            )
        )

        val handleCases = listOf(
            Pair("GSB", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "GSB")
    }

    // === BAAC ===

    @TestFactory
    fun `baac bank parser handles key paths`(): List<DynamicTest> {
        val parser = BAACBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "BAAC transfer out (Thai)",
                message = "BAAC: โอนเงินออก 1,200.00 บาท บช x4455 คงเหลือ 5,640.00 บาท",
                sender = "BAAC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4455",
                    balance = BigDecimal("5640.00")
                )
            )
        )

        val handleCases = listOf(
            Pair("BAAC", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "BAAC")
    }

    // === UOB Thailand ===

    @TestFactory
    fun `uob thailand parser handles key paths`(): List<DynamicTest> {
        val parser = UOBThailandParser()

        val testCases = listOf(
            ParserTestCase(
                name = "UOB card transaction (English)",
                message = "UOB: Card transaction 3,200.00 THB at AMAZON Bal 22,400.00 THB",
                sender = "UOB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3200.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("22400.00"),
                    merchant = "AMAZON"
                )
            )
        )

        val handleCases = listOf(
            Pair("UOB", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "UOB Thailand")
    }

    // === CIMB Thai ===

    @TestFactory
    fun `cimb thai parser handles key paths`(): List<DynamicTest> {
        val parser = CIMBThaiParser()

        val testCases = listOf(
            ParserTestCase(
                name = "CIMB transfer received (English)",
                message = "CIMB: Transfer received 6,000.00 THB A/C x5566 Bal 14,980.00 THB",
                sender = "CIMB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME,
                    accountLast4 = "5566",
                    balance = BigDecimal("14980.00")
                )
            )
        )

        val handleCases = listOf(
            Pair("CIMB", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "CIMB Thai")
    }

    // === KTC Credit Card ===

    @TestFactory
    fun `ktc credit card parser handles key paths`(): List<DynamicTest> {
        val parser = KTCCreditCardParser()

        val testCases = listOf(
            ParserTestCase(
                name = "KTC credit card spending (English)",
                message = "KTC: Credit card spending 2,999.00 THB at LAZADA Available limit 47,001.00 THB",
                sender = "KTC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2999.00"),
                    currency = "THB",
                    type = TransactionType.CREDIT,
                    merchant = "LAZADA",
                    isFromCard = true,
                    creditLimit = BigDecimal("47001.00")
                )
            ),
            ParserTestCase(
                name = "KTC international spending (Thai)",
                message = "KTC: ยอดใช้จ่ายต่างประเทศ 120.50 USD",
                sender = "KTC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.50"),
                    currency = "THB",
                    type = TransactionType.CREDIT,
                    isFromCard = true
                )
            )
        )

        val handleCases = listOf(
            Pair("KTC", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "KTC Credit Card")
    }

    // === Factory Resolution Tests ===

    @TestFactory
    fun `factory resolves all thai bank senders`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Bangkok Bank",
                sender = "BBL",
                currency = "THB",
                message = "BBL: Withdrawal 2,000.00 THB from A/C x1234 via ATM on 21/01/26 14:32 Bal 15,820.45 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Kasikorn Bank",
                sender = "KBank",
                currency = "THB",
                message = "KBank: You spent 1,250.00 THB at SHOPEE. A/C x5678 Bal 8,430.20 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Siam Commercial Bank",
                sender = "SCB",
                currency = "THB",
                message = "SCB: Transfer out 3,500.00 THB to A/C x8899 Bal 6,200.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Krungthai Bank",
                sender = "KTB",
                currency = "THB",
                message = "KTB: เงินเข้า 4,200.00 บาท บช x7788 คงเหลือ 12,350.75 บาท",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4200.00"),
                    currency = "THB",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Krungsri",
                sender = "BAY",
                currency = "THB",
                message = "Krungsri: ATM withdrawal 1,000.00 THB A/C x3344 Bal 9,540.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "TTB",
                sender = "TTB",
                currency = "THB",
                message = "ttb: Payment 1,500.00 THB via PromptPay A/C x9012 Bal 7,200.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Government Savings Bank",
                sender = "GSB",
                currency = "THB",
                message = "GSB: เงินฝากเข้า 2,500.00 บาท บช x1122 คงเหลือ 9,300.00 บาท",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "THB",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "BAAC",
                sender = "BAAC",
                currency = "THB",
                message = "BAAC: โอนเงินออก 1,200.00 บาท บช x4455 คงเหลือ 5,640.00 บาท",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "UOB Thailand",
                sender = "UOB",
                currency = "THB",
                message = "UOB: Card transaction 3,200.00 THB at AMAZON Bal 22,400.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3200.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "CIMB Thai",
                sender = "CIMB",
                currency = "THB",
                message = "CIMB: Transfer received 6,000.00 THB A/C x5566 Bal 14,980.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6000.00"),
                    currency = "THB",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "KTC",
                sender = "KTC",
                currency = "THB",
                message = "KTC: Credit card spending 2,999.00 THB at LAZADA Available limit 47,001.00 THB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2999.00"),
                    currency = "THB",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Thai bank factory tests")
    }
}
