package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MashreqEgyptParserTest {

    private val sender = "Mashreq NEO"

    private val cardPurchase =
        "Your Card ending with 1234 has been used at MY FAWRY for EGP 1,250.75"
    private val ipnReceived =
        "لقد استقبلت تحويل لحظي على  5678 بمبلغ 2,500.00 جم عبر IPN من JOHN DOE يوم  01-11-2026 الساعة  10:41 رقم المعاملة a1b2c3d4 للمساعدة www.mashreq.com/mashreqipn"
    private val ipnSent =
        "قمت بتحويل لحظي من 5678  بمبلغ 750.50 جم عبر IPN يوم  22-06-2026 الساعة 20:36 رقم المعاملة 9f8e7d6c للمساعدة www.mashreq.com/mashreqipn"
    private val accountDebit =
        "تم خصم مبلغ EGP 3000 من حسابك # ******123456. لمزيد من التفاصيل، يرجى تسجيل الدخول على تطبيق Mashreq Egypt"
    private val accountCredit =
        "تمت إضافة مبلغ EGP 4500 إلى حسابك # ******123456. لمزيد من التفاصيل، يرجى تسجيل الدخول على تطبيق Mashreq Egypt"

    /** Messages from the same sender that are not transactions and must be ignored. */
    private val nonTransactions = listOf(
        "OTP for points redemption" to
            "Your OTP is 123456 to complete your points redemption.",
        "OTP for online card payment" to
            "Your OTP is 654321. Please use it to complete your card transaction online. Please do not share this code with anyone.",
        "Arabic OTP" to
            "123456 هو الرمز السري لمرة واحدة لهذه المعاملة.  يرجى عدم مشاركته  مع أحد.",
        "Negated debit (was not debited)" to
            "لم يتم خصم مبلغ EGP 3000 من حسابك # ******123456. لمزيد من التفاصيل، يرجى تسجيل الدخول على تطبيق Mashreq Egypt",
        "Negated credit (was not credited)" to
            "لم تتم إضافة مبلغ EGP 4500 إلى حسابك # ******123456. لمزيد من التفاصيل، يرجى تسجيل الدخول على تطبيق Mashreq Egypt",
        "Transfer request received (not the debit itself)" to
            "تم استلام طلب تحويل أموال  إلى EGP 1500 برقم مرجعي MAC1234567890123. اذا لم تتقدم بهذا الطلب، يرجى  التواصل  مع \"مساعد المشرق الذكي\" من خلال \"خدمة العملاء\" داخل تطبيق Mashreq Egypt",
        "Financial inclusion limit warning" to
            "أنت اتجاوزت الحد الخاص بالشمول المالي ، الحد اليومي المتبقي ليك EGP 5000 والحد الشهري هو EGP 20000 بس. تقدر تغير حسابك لحساب بدون حدود الشمول المالي بمجرد زيارتك لفرع المشرق.",
        "E-statement ready" to
            "تم إصدار كشف الحساب الإلكتروني الخاص بيك لشهر OCT2026 سجل الدخول على تطبيق Mashreq Egypt  علشان تحمل كشف حسابك الإلكتروني https://mashreqegypt.go.link/abcde",
        "Password changed" to
            "تم تغيير كلمة المرور بنجاح من خلال Mobile Banking في \u200E04/10/2026\u200E الساعة \u200E09:06:59\u200E. إذا لم تطلب تسجيل الدخول، يرجى تغيير كلمة المرور  الخاصة بك.",
        "Credit card promotion" to
            "ملحقتش العرض؟ لسه قدامك فرصة تقدم دلوقتي على بطاقة المشرق نيو الائتمانية من غير مصاريف إصدار للبطاقة وكمان ممكن تكسب ضعف الكاش باك\nوالحد الائتماني ممكن يوصل لحد 100,000 جنيه. الحق قدم دلوقتي من خلال تطبيق Mashreq Egypt. العرض حصري و لفترة محدودة. \nتطبق الشروط والأحكام.http://mashreq.com/neotcfl",
        "Cashback promotion" to
            "استمتع بضعف الكاش باك لحد %4 مع حد ائتماني يوصل لحد 100,000 جنيه! قدم دلوقتي على بطاقة المشرق نيو الائتمانية من تطبيق Mashreq Egypt وهتوصلك البطاقة مجاناً من غير أي مصاريف إصدار وكمان ضعف الكاش باك.العرض ساري لفترة محدودة.\nتُطبق الشروط والأحكام mashreq.com/tncneo",
        "Savings interest promotion" to
            "كل 800,000  جنيه بيجيلك عليها عائد تقريباً 12,000 جنيه شهرياً!\nزوّد رصيد حساب أعلى فائدة الخاص بيك من المشرق نيو واستمتع ب 18%\nالفائدة بتتحسب شهرياً على أقل رصيد في الحساب.\nتطبق الشروط والأحكام",
        "Savings interest notice" to
            "لو فايدة حساب التوفير نزلت بدري بسبب الإجازات، استخدمها من أول يوم عمل في الشهر الجديد عشان السحب المبكر قد يؤدي إلى خصم جزء من الفايدة أو خصمها بالكامل، تطبق الشروط والأحكام."
    )

    @TestFactory
    fun `mashreq egypt parser handles common cases`(): List<DynamicTest> {
        val parser = MashreqEgyptParser()

        val cases = listOf(
            ParserTestCase(
                name = "Card purchase (EXPENSE, card)",
                message = "Your Card ending with 1234 has been used at MY FAWRY for EGP 1,250.75",
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.75"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "MY FAWRY",
                    accountLast4 = "1234",
                    isFromCard = true,
                    expectNullAccountCurrency = true
                )
            ),
            ParserTestCase(
                name = "Card purchase in a foreign currency",
                message = "Your Card ending with 1234 has been used at NETFLIX.COM for USD 9.99",
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX.COM",
                    accountLast4 = "1234",
                    isFromCard = true,
                    // The card's account stays in EGP, so a USD spend can't re-currency it.
                    accountCurrency = "EGP"
                )
            ),
            ParserTestCase(
                name = "InstaPay transfer received (INCOME, account)",
                message = ipnReceived,
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    reference = "a1b2c3d4",
                    accountLast4 = "5678",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "InstaPay transfer sent (EXPENSE, account)",
                message = ipnSent,
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.50"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "InstaPay Transfer",
                    reference = "9f8e7d6c",
                    accountLast4 = "5678",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Account debit (EXPENSE)",
                message = accountDebit,
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "Account Debit",
                    accountLast4 = "3456",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Account credit (INCOME)",
                message = accountCredit,
                sender = "Mashreq NEO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4500"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "3456",
                    isFromCard = false
                )
            ),
        ) + nonTransactions.map { (name, message) ->
            ParserTestCase(name = "Ignored: $name", message = message, sender = sender, shouldParse = false)
        }

        val handleCases = listOf(
            "Mashreq NEO" to true,
            "MashreqNEO" to true,
            "MASHREQ-NEO" to true,
            "Mashreq" to false,  // UAE Mashreq
            "Shreq" to false,
            "CIB" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "Mashreq Egypt Parser")
    }

    /**
     * "Mashreq NEO" is also claimed by the UAE MashreqBankParser, so go through the
     * content-aware BankParserFactory.parse: transactions must land on Mashreq Egypt, and
     * ignored messages must not fall through to the UAE parser either.
     */
    @TestFactory
    fun `factory routes Mashreq NEO messages to Mashreq Egypt`(): List<DynamicTest> {
        val routed = listOf(cardPurchase, ipnReceived, ipnSent, accountDebit, accountCredit).map { message ->
            DynamicTest.dynamicTest("Routed to Mashreq Egypt: ${message.take(40)}") {
                val parsed = BankParserFactory.parse(message, sender, 0L)
                assertEquals("Mashreq Egypt", parsed?.bankName)
                assertEquals("EGP", parsed?.currency)
            }
        }
        val ignored = nonTransactions.map { (name, message) ->
            DynamicTest.dynamicTest("Ignored by every parser: $name") {
                assertNull(BankParserFactory.parse(message, sender, 0L))
            }
        }
        return routed + ignored
    }
}
