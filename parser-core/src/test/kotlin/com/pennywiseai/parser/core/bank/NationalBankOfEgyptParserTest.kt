package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NationalBankOfEgyptParserTest {

    @TestFactory
    fun `nbe parser handles common cases`(): List<DynamicTest> {
        val parser = NationalBankOfEgyptParser()

        val cases = listOf(
            ParserTestCase(
                name = "Credit card spend, جم currency (EXPENSE, available limit)",
                message = "تم خصم 220.8 جم من بطاقة الائتمان رقم 9888 عند KASHIERFast " +
                    "يوم 09-10 الساعة 10:11 المتاح 26032.76 جم للمزيد اتصل ب 19623.",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("220.8"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "KASHIERFast",
                    accountLast4 = "9888",
                    creditLimit = BigDecimal("26032.76"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Debit card ATM withdrawal, no spaces, EGP token (EXPENSE, balance)",
                // Kept as one literal on purpose: BankSamplesDocTest harvests the
                // message literal and cannot follow a concatenation.
                message = "تم خصم 5000 EGP من بطاقة الخصم المباشر رقم3444 عندNBE ATM546 يوم03/09/26 الساعة21:13 المتاح3904.34EGP للمزيد اتصل ب ١٩٦٢٣",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "NBE ATM546",
                    accountLast4 = "3444",
                    balance = BigDecimal("3904.34"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit card spend, multi-word merchant (EXPENSE, available limit)",
                message = "تم خصم 1332 جم من بطاقة الائتمان رقم 9999 عند ASWAK FATHALLA " +
                    "يوم 08-31 الساعة 21:20 المتاح 28720.51 جم للمزيد اتصل ب 19623.",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1332"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "ASWAK FATHALLA",
                    accountLast4 = "9999",
                    creditLimit = BigDecimal("28720.51"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Instant transfer received (INCOME, not a card)",
                message = "تم إضافة تحويل لحظي لحسابكم رقم 4144 بمبلغ 120.00 جم من محمد محمد " +
                    "رقم مرجعي 499601593024 يوم 09-10 الساعة 20:11 للمزيد اتصل بـ 19623",
                sender = "BanK-AlAhly",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    merchant = "محمد محمد",
                    reference = "499601593024",
                    accountLast4 = "4144",
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
}
