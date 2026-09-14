package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NationalBankOfEgyptParserTest {

    @TestFactory
    fun `nbe parser handles common cases`(): List<DynamicTest> {
        val parser = NationalBankOfEgyptParser()

        val cases = listOf(
            ParserTestCase(
                name = "Credit card spend, جم currency (EXPENSE, available limit)",
                message = "تم خصم 100.50 جم من بطاقة الائتمان رقم 1111 عند KASHIERFast " +
                    "يوم 09-10 الساعة 10:11 المتاح 9000.00 جم للمزيد اتصل ب 10000.",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.50"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "KASHIERFast",
                    accountLast4 = "1111",
                    creditLimit = BigDecimal("9000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Debit card ATM withdrawal, no spaces, EGP token (EXPENSE, balance)",
                // Kept as one literal on purpose: BankSamplesDocTest harvests the
                // message literal and cannot follow a concatenation.
                message = "تم خصم 200 EGP من بطاقة الخصم المباشر رقم2222 عندNBE ATM546 يوم03/09/26 الساعة21:13 المتاح800.00EGP للمزيد اتصل ب 10000",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "NBE ATM546",
                    accountLast4 = "2222",
                    balance = BigDecimal("800.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit card spend, multi-word merchant (EXPENSE, available limit)",
                message = "تم خصم 300 جم من بطاقة الائتمان رقم 3333 عند ASWAK FATHALLA " +
                    "يوم 08-31 الساعة 21:20 المتاح 7000.00 جم للمزيد اتصل ب 10000.",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "ASWAK FATHALLA",
                    accountLast4 = "3333",
                    creditLimit = BigDecimal("7000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Instant transfer received (INCOME, not a card)",
                message = "تم إضافة تحويل لحظي لحسابكم رقم 4444 بمبلغ 400.00 جم من محمد محمد " +
                    "رقم مرجعي 123456789012 يوم 09-10 الساعة 20:11 للمزيد اتصل بـ 10000",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    merchant = "محمد محمد",
                    reference = "123456789012",
                    accountLast4 = "4444",
                    isFromCard = false
                )
            )
        )

        val handleCases = listOf(
            "BanK-AlAhly" to true,
            "bank-alahly" to true,
            "AD-BANK-ALAHLY-S" to true,
            "CIB" to false,
            "AD-FEDBNK" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "NBE Parser")
    }

    @Test
    fun `NBE sender routes through the factory`() {
        assertTrue(
            BankParserFactory.getParser("BanK-AlAhly") is NationalBankOfEgyptParser,
            "BanK-AlAhly should route to NationalBankOfEgyptParser via the factory"
        )
    }
}
