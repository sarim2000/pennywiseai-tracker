package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import java.time.Duration

/** Made-up SMS: a template taught from one message reading the sender's next ones (#279). */
class MessageTemplateTest {

    private val sender = "AD-ZQTBNK"

    private fun range(body: String, text: String): IntRange =
        body.indexOf(text).also { assertTrue(it >= 0, "'$text' not in message") }.let { it until it + text.length }

    private fun teach(
        body: String,
        amount: String,
        merchant: String? = null,
        account: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "SAR",
        format: AmountFormat? = null,
        fixedMerchant: String? = null
    ): MessageTemplate? = MessageTemplates.build(
        id = "t",
        senderKey = "ZQTBNK",
        body = body,
        selection = TeachSelection(
            amount = range(body, amount),
            merchant = merchant?.let { range(body, it) },
            account = account?.let { range(body, it) }
        ),
        type = type,
        currency = currency,
        bankName = "Zqt Bank",
        amountFormat = format,
        fixedMerchant = fixedMerchant
    )

    private fun read(template: MessageTemplate, body: String) = MessageTemplates.match(template, body, sender, 1L)

    private val sample =
        "Purchase of SAR 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00"

    private val taught get() = teach(sample, "50.00", "Juice Bar", "1234")!!

    // ─── Later messages of the same shape ───────────────────────────────────

    private data class Later(val name: String, val body: String, val expected: ExpectedTransaction)

    @TestFactory
    fun `reads later messages of the same shape`(): List<DynamicTest> = listOf(
        Later(
            "every changing field changes",
            "Purchase of SAR 1,250.75 at City Pharmacy Main Branch card ending 9876 on 01/11/26 23:59. Balance SAR 12,000.00",
            ExpectedTransaction(
                amount = BigDecimal("1250.75"), currency = "SAR", type = TransactionType.EXPENSE,
                merchant = "City Pharmacy Main Branch", accountLast4 = "9876"
            )
        ),
        Later(
            "a merchant holding the word that ends it",
            "Purchase of SAR 7.00 at Shop on Main card ending 1234 on 02/11/26 09:00. Balance SAR 1.00",
            ExpectedTransaction(
                amount = BigDecimal("7.00"), currency = "SAR", type = TransactionType.EXPENSE,
                merchant = "Shop on Main", accountLast4 = "1234"
            )
        ),
        Later(
            "a line break where the example had a space",
            "Purchase of SAR 7.00 at Bakery\ncard ending 1234 on 02/11/26 09:00.\nBalance SAR 1.00",
            ExpectedTransaction(
                amount = BigDecimal("7.00"), currency = "SAR", type = TransactionType.EXPENSE,
                merchant = "Bakery", accountLast4 = "1234"
            )
        )
    ).map { case ->
        dynamicTest(case.name) {
            val parsed = read(taught, case.body)!!
            ParserTestUtils.validateResultDynamic(parsed, case.expected)
            assertEquals(case.body, parsed.smsBody, "the message stays as received, it is the dedupe identity")
            assertEquals(sender, parsed.sender)
            assertEquals("Zqt Bank", parsed.bankName)
        }
    }

    @Test
    fun `reads the example it was taught from`() {
        val parsed = read(taught, sample)!!
        ParserTestUtils.validateResultDynamic(
            parsed,
            ExpectedTransaction(
                amount = BigDecimal("50.00"), currency = "SAR", type = TransactionType.EXPENSE,
                merchant = "Juice Bar", accountLast4 = "1234"
            )
        )
    }

    // ─── Must read as nothing ───────────────────────────────────────────────

    @TestFactory
    fun `reads other shapes as nothing`(): List<DynamicTest> = listOf(
        "a declined attempt" to "Declined of SAR 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00",
        "another currency" to "Purchase of USD 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30. Balance USD 900.00",
        "an extra word" to "Purchase of SAR 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30 Riyadh. Balance SAR 900.00",
        "a missing balance" to "Purchase of SAR 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30.",
        "another bank wording" to "You spent SAR 50.00 at Juice Bar",
        "an OTP" to "Your code is 123456",
        "a word where the amount goes" to "Purchase of SAR free at Juice Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00",
        "a zero amount" to "Purchase of SAR 0.00 at Juice Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00",
        "only a number where the merchant goes" to "Purchase of SAR 5.00 at 777 card ending 1234 on 12/10/26 10:30. Balance SAR 900.00",
        "lower-case changes a literal" to "purchase of SAR 5.00 at Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00"
    ).map { (name, body) -> dynamicTest(name) { assertNull(read(taught, body)) } }

    @Test
    fun `a balance can never slide into the amount`() {
        // The two figures swapped places in the wording: the shape no longer fits at all.
        assertNull(read(taught, "Balance SAR 900.00. Purchase of SAR 50.00 at Juice Bar card ending 1234 on 12/10/26 10:30"))
        val parsed = read(taught, "Purchase of SAR 1.00 at Bar card ending 1234 on 12/10/26 10:30. Balance SAR 900.00")
        assertEquals(0, BigDecimal("1.00").compareTo(parsed!!.amount))
    }

    // ─── What PR #302's generated regexes got wrong ─────────────────────────

    @Test
    fun `a fraction with no whole part is never read as the whole amount`() {
        val body = "Paid USD .50 at Cafe"
        assertNull(teach(body, "50", "Cafe", currency = "USD"))
        assertNull(MessageTemplates.guess(sender, body).amount)

        val glued = teach("Paid Rs.50 at Cafe", "50", "Cafe", currency = "INR")!!
        assertEquals(BigDecimal("75"), read(glued, "Paid Rs.75 at Cafe")!!.amount)
    }

    @Test
    fun `a merchant with nothing after it keeps its whole name`() {
        val template = teach("Paid SAR 5.00 at Juice Bar", "5.00", "Juice Bar")!!
        assertEquals("City Pharmacy Main", read(template, "Paid SAR 9.00 at City Pharmacy Main")!!.merchant)
    }

    @TestFactory
    fun `punctuated and glued currencies stay literal`(): List<DynamicTest> = listOf(
        Triple("Rs.500.00 debited for Rent", "500.00", "Rs.75.50 debited for Rent") to "75.50",
        Triple("SAR500.00 debited for Rent", "500.00", "SAR75.50 debited for Rent") to "75.50",
        Triple("Debit 500.00SAR, for Rent", "500.00", "Debit 75.50SAR, for Rent") to "75.50",
        Triple("خصم 500ريال من حسابك", "500", "خصم 75ريال من حسابك") to "75"
    ).map { (case, value) ->
        val (example, amount, later) = case
        dynamicTest(example) {
            val template = teach(example, amount)!!
            assertEquals(0, BigDecimal(value).compareTo(read(template, later)!!.amount))
            assertNull(read(template, later.replace("SAR", "USD").replace("Rs.", "$").replace("ريال", "دولار")))
        }
    }

    // ─── Wrong money a review found (each read a wrong figure once) ─────────

    private data class Misread(val name: String, val template: () -> MessageTemplate?, val body: String)

    @TestFactory
    fun `a merchant can't hide what changes the money`(): List<DynamicTest> = listOf(
        Misread("a later figure taking the amount's place", { teach("Reversal at Bar: SAR 5.00", "5.00", "Bar") }, "Reversal at Bar: SAR 5.00 remaining: SAR 900.00"),
        Misread("a fee taking the amount's place", { teach("Paid to Bar SAR 5.00 today", "5.00", "Bar") }, "Paid to Bar SAR 5.00 fee SAR 2.00 today"),
        Misread("another currency against the amount", { teach("POS Bar 50.00 approved", "50.00", "Bar") }, "POS Bar USD 50.00 approved"),
        Misread("a minus sign against the amount", { teach("POS Bar 50.00 approved", "50.00", "Bar") }, "POS Bar - 50.00 approved"),
        Misread("spaced thousands against the amount", { teach("POS Bar 50.00 approved", "50.00", "Bar") }, "POS Bar 1 234.50 approved"),
        Misread("a figure after the amount", { teach("Paid 50.00 Juice Bar", "50.00", "Juice Bar") }, "Paid 900.00 50.00 Juice Bar"),
        Misread("declined at the end", { teach("Paid SAR 5.00 at Bar", "5.00", "Bar") }, "Paid SAR 9.00 at Bar declined"),
        Misread("reversed at the end", { teach("Paid SAR 5.00 at Bar", "5.00", "Bar") }, "Paid SAR 9.00 at Bar was reversed"),
        Misread("a credit mark at the end", { teach("Paid SAR 5.00 at Bar", "5.00", "Bar") }, "Paid SAR 9.00 at Bar CR"),
        Misread("a punctuated credit mark", { teach("Paid SAR 5.00 at Bar", "5.00", "Bar") }, "Paid SAR 9.00 at Bar CR."),
        Misread("a lower-case credit mark", { teach("Paid SAR 5.00 at Bar", "5.00", "Bar") }, "Paid SAR 9.00 at Bar cr"),
        Misread("spaced thousands with a number to absorb the rest", { teach("Paid SAR 50.00 12/10/26", "50.00") }, "Paid SAR 1 234.50"),
        Misread("reversed in the middle", { teach("Received SAR 5.00 from Ali ok", "5.00", "Ali", type = TransactionType.INCOME) }, "Received SAR 5.00 from Ali reversed ok"),
        Misread("a refund in front", { teach("Bar paid SAR 5.00", "5.00", "Bar") }, "Refund from Bar paid SAR 5.00"),
        Misread("Arabic rejection at the end", { teach("دفع 5.00 ر.س لدى متجر", "5.00", "متجر") }, "دفع 9.00 ر.س لدى متجر مرفوضة"),
        Misread("a declined reference", { teach("Paid SAR 5.00 ref AB12CD thanks", "5.00") }, "Paid SAR 5.00 ref DECLINED-51 thanks")
    ).map { case ->
        dynamicTest(case.name) {
            val template = case.template()
            assertNotNull(template, "the example itself is fine to teach")
            assertNull(read(template!!, case.body))
        }
    }

    @Test
    fun `merchants that merely look unusual still read`() {
        val template = teach("Paid SAR 5.00 at Bar on 12/10", "5.00", "Bar")!!
        assertEquals("Store 24", read(template, "Paid SAR 9.00 at Store 24 on 12/10")!!.merchant)
        assertEquals("Paid Parking", read(template, "Paid SAR 9.00 at Paid Parking on 12/10")!!.merchant)
        assertEquals("Dr Ahmed Clinic", read(template, "Paid SAR 9.00 at Dr Ahmed Clinic on 12/10")!!.merchant)
    }

    @Test
    fun `a currency tapped as the merchant can't be taught`() {
        assertNull(teach("Paid SAR 5.00", "5.00", merchant = "SAR"))
    }

    @TestFactory
    fun `a leading zero is never thousands`(): List<DynamicTest> = listOf(
        Triple("0,500", "SAR", AmountFormat.DOT_DECIMAL),
        Triple("00,123", "SAR", AmountFormat.DOT_DECIMAL),
        Triple("0.500", "EUR", AmountFormat.COMMA_DECIMAL),
        Triple("0.001", "USD", null)
    ).map { (text, currency, format) ->
        dynamicTest("$text $format") { assertNull(MessageTemplates.parseAmount(text, format, currency)) }
    }

    @Test
    fun `an Arabic decimal sign is always a decimal`() {
        assertEquals(0, BigDecimal("1.5").compareTo(MessageTemplates.parseAmount("١٫٥٠٠", null, "SAR")))
        assertEquals(0, BigDecimal("1.5").compareTo(MessageTemplates.parseAmount("١٫٥٠٠", AmountFormat.COMMA_DECIMAL, "SAR")))
        assertEquals(0, BigDecimal("1234.5").compareTo(MessageTemplates.parseAmount("١٬٢٣٤٫٥", null, "SAR")))
        assertFalse(MessageTemplates.isFormatAmbiguous("١٫٢٣٤", "KWD"), "the sign already says which")
        val template = teach("Paid SAR 500 now", "500")!!
        assertEquals(0, BigDecimal("1.5").compareTo(read(template, "Paid SAR ١٫٥٠٠ now")!!.amount))
    }

    @TestFactory
    fun `spaced and apostrophe thousands are neither guessed nor taught`(): List<DynamicTest> = listOf(
        "Paid SAR 1 234.50 at Bar now" to "1",
        "Paid 1 234.50 SAR at Bar now" to "234.50",
        "Paid CHF 1'234.50 at Bar now" to "1",
        "Paid SAR 1\u00A0234.50 at Bar now" to "1"
    ).map { (body, piece) ->
        dynamicTest(body) {
            assertNull(MessageTemplates.guess(sender, body).amount)
            assertNull(teach(body, piece, currency = "CHF"))
        }
    }

    @Test
    fun `a number beside other digits is still its own number`() {
        // A card before the amount isn't thousands: nothing but a word sits between them.
        val body = "Card 1234 paid SAR 50.00 at Bar"
        assertEquals("50.00", textOf(body, MessageTemplates.guess(sender, body).amount))
        // Right beside each other, either could be the price: left for the user to tap.
        assertNull(MessageTemplates.guess(sender, "Card 1234 SAR 50.00 at Bar").amount)
    }

    @Test
    fun `a figure that says what it is afterwards is not the amount`() {
        assertNull(MessageTemplates.guess(sender, "SAR 900.00 available. Paid 50.00").amount)
    }

    @Test
    fun `a restored template with a broken shape reads nothing and never hangs`() {
        val shape = { parts: List<TemplatePart> ->
            MessageTemplate("t", "K", parts, TransactionType.EXPENSE, "SAR", "B")
        }
        val merchant = TemplatePart(TemplateField.MERCHANT)
        val amount = TemplatePart(TemplateField.AMOUNT)
        val paid = TemplatePart(TemplateField.LITERAL, "Paid ")
        val empty = TemplatePart(TemplateField.LITERAL, "")
        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            assertNull(read(shape(listOf(paid, merchant, amount)), "Paid Bar"))
            assertNull(read(shape(listOf(paid, merchant, empty, amount)), "Paid Bar"))
            assertNull(read(shape(listOf(paid, merchant, TemplatePart(TemplateField.LITERAL, " "), merchant, amount)), "Paid a b c 5"))
            assertEquals(TeachGuess(), MessageTemplates.guess(sender, "at ".repeat(1000)))
        }
    }

    @Test
    fun `a currency has to be a real-looking code`() {
        assertNull(teach("Paid kwd 2.500 now", "2.500", currency = "kwd"))
        assertNull(teach("Paid 2.500 now", "2.500", currency = "   "))
    }

    // ─── Things that change between messages ────────────────────────────────

    @TestFactory
    fun `reads past what changes between messages`(): List<DynamicTest> = listOf(
        Triple("a reference with letters", "Paid SAR 5.00 ref AB12CD34 thanks", "Paid SAR 9.00 ref ZX98YW76 thanks"),
        Triple("a written date in one word", "Paid SAR 5.00 on 12-Oct-26 thanks", "Paid SAR 9.00 on 03-Nov-26 thanks"),
        Triple("a written date in three words", "Paid SAR 5.00 on 12 Oct 2026 thanks", "Paid SAR 9.00 on 3 Nov 2026 thanks"),
        Triple("a weekday and a month", "Paid SAR 5.00 on Mon, 12 Oct thanks", "Paid SAR 9.00 on Tue, 3 Nov thanks"),
        Triple("am and pm", "Paid SAR 5.00 at 10:30 AM thanks", "Paid SAR 9.00 at 09:15 PM thanks"),
        Triple("an Arabic month", "دفع SAR 5.00 في 12 أكتوبر شكرا", "دفع SAR 9.00 في 3 نوفمبر شكرا"),
        Triple("a masked account", "Paid SAR 5.00 from XX1234 thanks", "Paid SAR 9.00 from XX9876 thanks")
    ).map { (name, example, later) ->
        dynamicTest(name) {
            val template = teach(example, "5.00")!!
            assertEquals(0, BigDecimal("9.00").compareTo(read(template, later)!!.amount))
        }
    }

    @Test
    fun `a month word only stands in beside a number`() {
        // "May" here is a literal word, not a date: "Can" must not take its place.
        val template = teach("May we confirm SAR 5.00 paid", "5.00")!!
        assertNull(read(template, "Can we confirm SAR 5.00 paid"))
        assertNotNull(read(template, "May we confirm SAR 7.00 paid"))
    }

    @Test
    fun `an approved word never stands in for a date word`() {
        val template = teach("Paid SAR 5.00 on 12 Oct thanks", "5.00")!!
        assertNull(read(template, "Paid SAR 5.00 on 12 approved thanks"))
    }

    // ─── Arabic ─────────────────────────────────────────────────────────────

    private val arabic = "تم خصم ٥٠٫٠٠ ر.س من بطاقتك ١٢٣٤ لدى متجر النور في ١٢/١٠/٢٠٢٦ الرصيد ٩٠٠٫٠٠ ر.س"

    @Test
    fun `reads Arabic with Arabic digits`() {
        val template = teach(arabic, "٥٠٫٠٠", "متجر النور", "١٢٣٤")!!
        val parsed = read(template, "تم خصم ١٬٢٥٠٫٧٥ ر.س من بطاقتك ٩٨٧٦ لدى صيدلية المدينة في ٠١/١١/٢٠٢٦ الرصيد ٣٠٠٫٠٠ ر.س")
        ParserTestUtils.validateResultDynamic(
            parsed!!,
            ExpectedTransaction(
                amount = BigDecimal("1250.75"), currency = "SAR", type = TransactionType.EXPENSE,
                merchant = "صيدلية المدينة", accountLast4 = "9876"
            )
        )
    }

    @Test
    fun `direction marks and western digits read the same`() {
        val template = teach(arabic, "٥٠٫٠٠", "متجر النور", "١٢٣٤")!!
        val marked = "تم خصم \u200F\u2066٧٥.٠٠\u2069 ر.س من بطاقتك 1234 لدى متجر النور في 12/10/2026 الرصيد 900.00 ر.س"
        assertEquals(0, BigDecimal("75.00").compareTo(read(template, marked)!!.amount))
    }

    @Test
    fun `offsets in text with marks point at the tapped characters`() {
        val body = "\u200FPaid\u00A0\u00A0SAR\n5.00 at Bar"
        val template = teach(body, "5.00", "Bar")!!
        assertEquals("Bar", read(template, body)!!.merchant)
        assertEquals("Cafe", read(template, "Paid SAR 8.00 at Cafe")!!.merchant)
    }

    // ─── Amount formats ─────────────────────────────────────────────────────

    @TestFactory
    fun `amount formats`(): List<DynamicTest> = listOf(
        Triple("1,234.56", "SAR", null) to "1234.56",
        Triple("1.234,56", "EUR", null) to "1234.56",
        Triple("12,50", "EUR", null) to "12.50",
        Triple("12.50", "USD", null) to "12.50",
        Triple("1,234", "USD", null) to "1234",
        Triple("1.234", "EUR", null) to "1234",
        Triple("1,00,000.00", "INR", null) to "100000.00",
        Triple("1,234,567", "USD", null) to "1234567",
        Triple("500", "SAR", null) to "500",
        Triple("٥٠٫٥٠", "SAR", null) to "50.50",
        Triple("٠٫٥٠٠", "SAR", null) to "0.5",
        Triple("1.234", "KWD", AmountFormat.DOT_DECIMAL) to "1.234",
        Triple("1,234", "KWD", AmountFormat.DOT_DECIMAL) to "1234",
        Triple("1,234", "KWD", AmountFormat.COMMA_DECIMAL) to "1.234"
    ).map { (case, value) ->
        val (text, currency, format) = case
        dynamicTest("$text $currency $format") {
            assertEquals(0, BigDecimal(value).compareTo(MessageTemplates.parseAmount(text, format, currency)))
        }
    }

    @TestFactory
    fun `amounts that must not parse`(): List<DynamicTest> = listOf(
        Triple("1.234", "KWD", null),
        Triple("1,234", "OMR", null),
        Triple("12,50", "USD", AmountFormat.DOT_DECIMAL),
        Triple("1.2.3", "USD", AmountFormat.DOT_DECIMAL),
        Triple("0", "USD", null),
        Triple("1234567890123456", "USD", null),
        Triple("12.3456", "USD", AmountFormat.DOT_DECIMAL)
    ).map { (text, currency, format) ->
        dynamicTest("$text $currency $format") { assertNull(MessageTemplates.parseAmount(text, format, currency)) }
    }

    @Test
    fun `a three-decimal currency asks what its separator means`() {
        assertTrue(MessageTemplates.isFormatAmbiguous("1.234", "KWD"))
        assertFalse(MessageTemplates.isFormatAmbiguous("1.234", "EUR"))
        assertFalse(MessageTemplates.isFormatAmbiguous("1.23", "KWD"))
        assertFalse(MessageTemplates.isFormatAmbiguous("500", "KWD"))
        // Until it's answered, teaching this amount builds nothing rather than guessing.
        assertNull(teach("Paid KWD 1.234 for Rent", "1.234", currency = "KWD"))
        assertNotNull(teach("Paid KWD 1.234 for Rent", "1.234", currency = "KWD", format = AmountFormat.DOT_DECIMAL))
    }

    @Test
    fun `a sample with no separator refuses a later amount it can't read`() {
        val template = teach("Paid KWD 5 for Rent", "5", currency = "KWD")!!
        assertNull(read(template, "Paid KWD 1,234 for Rent"))
        assertEquals(0, BigDecimal("1.5").compareTo(read(template, "Paid KWD 1.5 for Rent")!!.amount))
    }

    // ─── Building ───────────────────────────────────────────────────────────

    @Test
    fun `currency comes from the template, never a default`() {
        val template = teach("Paid 5.00 for Rent", "5.00", currency = "EUR")!!
        assertEquals("EUR", read(template, "Paid 9.00 for Rent")!!.currency)
    }

    @Test
    fun `refuses what it can't read back`() {
        assertNull(teach("Paid SAR 5.00 for Rent", "Paid"), "the amount has to be a number")
        assertNull(teach("Paid SAR 5.00 for Rent", "5.00", merchant = "5.00"), "fields can't overlap")
        assertNull(teach("5.00 1.00", "5.00"), "nothing but numbers would match anything")
        assertNull(teach("Paid SAR 5.00 at Bar77", "5.00", merchant = "Bar"), "a merchant needs text after it to stop at, or to end the message")
        assertNull(MessageTemplates.build("t", "K", "Paid 5.00", TeachSelection(5..8), TransactionType.EXPENSE, "", "B"), "needs a currency")
    }

    @Test
    fun `a typed merchant stands in when the message names none`() {
        val template = teach("Rent paid SAR 5.00", "5.00", fixedMerchant = "Landlord")!!
        assertEquals("Landlord", read(template, "Rent paid SAR 9.00")!!.merchant)
    }

    @Test
    fun `direction comes from the template`() {
        val template = teach("Salary SAR 5.00 received", "5.00", type = TransactionType.INCOME)!!
        assertEquals(TransactionType.INCOME, read(template, "Salary SAR 9.00 received")!!.type)
    }

    // ─── Several templates ──────────────────────────────────────────────────

    @Test
    fun `the template that fits reads the message`() {
        val debit = teach("Debit SAR 5.00 at Bar", "5.00", "Bar")!!
        val credit = teach("Credit SAR 5.00 from Bar", "5.00", "Bar", type = TransactionType.INCOME)!!
        val parsed = MessageTemplates.matchAny(listOf(debit, credit), "Credit SAR 9.00 from Cafe", sender, 1L)
        assertEquals(TransactionType.INCOME, parsed!!.type)
    }

    @Test
    fun `templates that disagree read as nothing`() {
        val body = "Moved SAR 5.00 for Rent"
        val out = teach(body, "5.00", type = TransactionType.EXPENSE)!!
        val into = teach(body, "5.00", type = TransactionType.INCOME)!!
        assertNull(MessageTemplates.matchAny(listOf(out, into), body, sender, 1L))
        assertNotNull(MessageTemplates.matchAny(listOf(out, out.copy(id = "2")), body, sender, 1L))
    }

    @Test
    fun `huge or repetitive messages finish quickly`() {
        val template = teach("Paid SAR 5.00 at Bar on 12/10/26", "5.00", "Bar")!!
        val noisy = "Paid SAR 5.00 at " + "on ".repeat(600) + "on 12/10/26"
        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            assertNull(read(template, noisy))
            assertNull(read(template, "Paid SAR 5.00 at Bar on 12/10/26 ".repeat(5000)))
            assertNull(MessageTemplates.parseAmount("1,".repeat(5000) + "1", null, "USD"))
        }
    }

    // ─── Guessing ───────────────────────────────────────────────────────────

    private fun textOf(body: String, range: IntRange?) = range?.let { body.substring(it.first, it.last + 1) }

    @Test
    fun `guesses an English message`() {
        val guess = MessageTemplates.guess(sender, "Purchased SAR 50.00 at Juice Bar card ending 1234 on 12/10/26. Balance SAR 900.00")
        val body = "Purchased SAR 50.00 at Juice Bar card ending 1234 on 12/10/26. Balance SAR 900.00"
        assertEquals("50.00", textOf(body, guess.amount), "the balance is not the amount")
        assertEquals("1234", textOf(body, guess.account))
        assertEquals(TransactionType.EXPENSE, guess.type)
        assertEquals("SAR", guess.currency)
    }

    @TestFactory
    fun `guesses the merchant from the words around it`(): List<DynamicTest> = listOf(
        "Purchased SAR 5.00 at City Pharmacy card ending 1234 on 13/10/26" to "City Pharmacy",
        "Paid SAR 5.00 to Blue Sky Cafe ref 123" to "Blue Sky Cafe",
        "تم خصم ٥٠ ر.س لدى متجر النور في ١٢/١٠/٢٠٢٦" to "متجر النور",
        "Paid SAR 5.00 to your account ending 1234" to null,
        "Paid SAR 5.00 from card 1234" to null,
        "Paid SAR 5.00 for rent" to null
    ).map { (body, merchant) ->
        dynamicTest(body) { assertEquals(merchant, textOf(body, MessageTemplates.guess(sender, body).merchant)) }
    }

    @Test
    fun `an unmarked merchant stays literal, so a guessed one is what makes a template reusable`() {
        val first = "Purchased SAR 18.25 at City Pharmacy card ending 1234 on 13/10/26. Balance SAR 881.75"
        val second = "Purchased SAR 50.00 at Juice Bar card ending 1234 on 12/10/26. Balance SAR 900.00"
        val guess = MessageTemplates.guess(sender, first)
        val template = MessageTemplates.build(
            "t", "ZQTBNK", first, TeachSelection(guess.amount!!, guess.merchant, guess.account),
            guess.type!!, guess.currency!!, "Zqt Bank"
        )!!
        assertEquals("Juice Bar", read(template, second)!!.merchant)
        assertNull(read(teach(first, "18.25")!!, second), "with nothing marked, only this merchant fits")
    }

    @Test
    fun `competing prices are left for the user to tap`() {
        val guess = MessageTemplates.guess(sender, "Spent SAR 50.00 fee SAR 2.00 at Juice Bar")
        assertNull(guess.amount)
        assertNull(guess.currency)
        assertEquals(TransactionType.EXPENSE, guess.type)
    }

    @Test
    fun `words beside a price are not currencies`() {
        // Android's Currency.getInstance took "and" for a code, so 2.00 lost to 40.00 on real phones.
        val guess = MessageTemplates.guess(sender, "Payment done: fee SAR 2.00 and total SAR 40.00 to Fuel Station")
        assertNull(guess.amount)
        listOf("and", "for", "AND", "FEE", "THE", "XXX").forEach { word ->
            val body = "Paid SAR 5.00 $word Rent"
            assertEquals("SAR", MessageTemplates.currencyBeside(body, range(body, "5.00")), word)
        }
    }

    @Test
    fun `guesses an Arabic message`() {
        val guess = MessageTemplates.guess(sender, arabic)
        assertEquals("٥٠٫٠٠", textOf(arabic, guess.amount), "الرصيد is not the amount")
        assertEquals("SAR", guess.currency)
        assertEquals(TransactionType.EXPENSE, guess.type)
    }

    @Test
    fun `an unpriced number stays unresolved`() {
        val guess = MessageTemplates.guess(sender, "Payment of 500 done")
        assertNull(guess.amount)
        assertNull(guess.currency)
    }

    @Test
    fun `currency beside a tapped number`() {
        val body = "Paid EUR 5.00 and 3 items"
        assertEquals("EUR", MessageTemplates.currencyBeside(body, range(body, "5.00")))
        assertNull(MessageTemplates.currencyBeside(body, range(body, "3")))
        assertNull(MessageTemplates.currencyBeside("USD 5.00 EUR", 4..7), "two different tags say nothing")
    }

    // ─── Offering messages ──────────────────────────────────────────────────

    @TestFactory
    fun `which messages are worth offering`(): List<DynamicTest> = listOf(
        Triple("a purchase", sender, "Paid SAR 5.00 at Bar") to true,
        Triple("Arabic with a currency sign", sender, "دفع 5.00 ر.س") to true,
        Triple("an OTP", sender, "Your OTP is 1234 for SAR 5.00") to false,
        Triple("an Arabic code", sender, "رمز التحقق 1234 لمبلغ 5.00 ر.س") to false,
        Triple("a promo", sender, "Special offer! SAR 5.00 off") to false,
        Triple("a failed attempt", sender, "Payment of SAR 5.00 failed") to false,
        Triple("a person", "+966500000000", "I paid SAR 5.00") to false,
        Triple("no price", sender, "Your parcel 1234 arrived") to false
    ).map { (case, expected) ->
        val (name, from, body) = case
        dynamicTest(name) { assertEquals(expected, MessageTemplates.looksLikeMoney(from, body)) }
    }

    // ─── Tapping ────────────────────────────────────────────────────────────

    @Test
    fun `tappable spans separate glued words and numbers`() {
        val body = "Paid SAR500.00, card XX1234."
        val spans = MessageTemplates.tappableSpans(body).map { textOf(body, it.range) to it.isNumber }
        assertEquals(
            listOf("Paid" to false, "SAR" to false, "500.00" to true, "card" to false, "XX" to false, "1234" to true),
            spans
        )
    }

    @Test
    fun `tappable spans map back through marks and double spaces`() {
        val body = "\u200FPaid  ٥٠٫٠٠ ر.س"
        val spans = MessageTemplates.tappableSpans(body).map { textOf(body, it.range) }
        assertEquals(listOf("Paid", "٥٠٫٠٠", "ر.س"), spans)
    }

    @Test
    fun `the source label stops at whole words`() {
        val body = "Purchase of SAR 50.00 at Juice Bar now"
        val around = MessageTemplates.contextAround(body, range(body, "50.00"))
        assertEquals("of SAR 50.00 at Juice", textOf(body, around))
    }
}
