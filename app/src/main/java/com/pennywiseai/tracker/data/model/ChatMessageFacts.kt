package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.utils.toLatinDigits
import java.math.BigDecimal

/**
 * What a chat message itself states, in English, Arabic and Hindi. A small model invents
 * currencies, dates and amounts the user never said, so its fields only count when the
 * message backs them. Lookarounds instead of \b: on non-Latin text \b differs between
 * the JVM and Android's ICU regex.
 */
internal object ChatMessageFacts {
    private fun word(alternatives: String) = "(?<![\\p{L}\\p{M}])(?:$alternatives)(?![\\p{L}\\p{M}])"
    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /** Latin digits and separators: "١٬٥٠٠" reads as "1,500". */
    private fun latin(text: String) = text.toLatinDigits().replace('٬', ',').replace('٫', '.')

    // Amounts

    private val SCALES = mapOf(
        "k" to 1_000L, "thousand" to 1_000L, "الف" to 1_000L, "ألف" to 1_000L, "آلاف" to 1_000L, "हजार" to 1_000L, "हज़ार" to 1_000L,
        "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L, "लाख" to 100_000L,
        "million" to 1_000_000L, "مليون" to 1_000_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L, "करोड़" to 10_000_000L
    )
    private val SCALE_PATTERN = SCALES.keys.joinToString("|")
    private val NUMBER = regex("(\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*($SCALE_PATTERN)(?![\\p{L}\\p{M}]))?")

    // Thousands groups only, Indian (1,50,000) or Western (1,500). "12,50" is ambiguous: 12.5 or 1250?
    private val PLAIN_NUMBER = Regex("\\d+(?:\\.\\d+)?|\\d{1,3}(?:,\\d{2,3})*,\\d{3}(?:\\.\\d+)?")
    private val AMOUNT_TEXT = regex("[^\\d]*?([\\d.,]+)(?:\\s*($SCALE_PATTERN)(?![\\p{L}\\p{M}]))?[^\\d]*")

    /**
     * A tool argument as a positive amount, or null when unusable. The sign is dropped: the
     * model writes -15000 for a spend, and the draft's type carries the direction.
     */
    fun parseAmount(value: Any?): BigDecimal? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it) }
        is String -> parseAmountText(value)
        else -> null
    }?.abs()?.takeIf { it.signum() > 0 }

    private fun parseAmountText(text: String): BigDecimal? {
        val match = AMOUNT_TEXT.matchEntire(latin(text.trim()).lowercase()) ?: return null
        val number = match.groupValues[1].takeIf { PLAIN_NUMBER.matches(it) } ?: return null
        val value = number.replace(",", "").toBigDecimalOrNull() ?: return null
        val scale = SCALES[match.groupValues[2]] ?: return value
        return value.multiply(BigDecimal(scale))
    }

    /**
     * Whether [amount] is a number the message contains, scale included: "15k" states 15000,
     * not 15. A guard, not a guarantee: a right number on the wrong item, a stated total or
     * a digit from a name all pass. An ambiguous "12,50" states nothing, so it gets checked.
     */
    fun statesAmount(message: String, amount: BigDecimal): Boolean {
        val wanted = amount.abs().stripTrailingZeros()
        val text = latin(message).lowercase()
        return NUMBER.findAll(text).any { m ->
            val number = m.groupValues[1].trimEnd(',').takeIf { PLAIN_NUMBER.matches(it) } ?: return@any false
            // ".50" is half a unit, but the dot in "rs.500" only ends an abbreviation.
            val start = m.range.first
            val fraction = text.getOrNull(start - 1) == '.' && text.getOrNull(start - 2)?.isLetter() != true
            val value = (if (fraction) "0.$number" else number).replace(",", "").toBigDecimalOrNull() ?: return@any false
            value.multiply(BigDecimal(SCALES[m.groupValues[2]] ?: 1L)).compareTo(wanted) == 0
        }
    }

    // Currencies

    /** Also a name ("JD Sports", "Sr manager"), so it counts only beside a number. */
    private fun beside(mark: String) = "(?<=\\p{Nd}\\s{0,3})$mark|$mark(?=\\s*\\p{Nd})"

    /** A word for money, and the currencies it can mean, most likely first. */
    private val CURRENCY_WORDS = listOf(
        regex("\\$|${word("dollars?|bucks?")}|دولار|डॉलर") to listOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD"),
        regex("${word("euros?")}|يورو|यूरो") to listOf("EUR"),
        // "2 pounds of apples" and "a 5-pound bag" are weights.
        // ponytail: "2 pounds apples for 300" still reads as money; the card shows the currency.
        regex("(?<!e)£|${word("(?<!-)pounds?(?!\\s+of\\b)")}") to listOf("GBP", "EGP"),
        regex("e£|جنيه") to listOf("EGP", "SDG"),
        regex("${word(beside("sr") + "|riyals?|rials?")}|ريال|रियाल") to listOf("SAR", "QAR", "OMR", "YER"),
        regex("${word("dirhams?|dhs?")}|درهم|دراهم|दिरहम") to listOf("AED", "MAD"),
        regex("${word(beside("jd") + "|dinars?")}|دينار|دنانير") to listOf("JOD", "KWD", "BHD", "IQD", "TND", "LYD", "DZD"),
        regex("${word("rs\\.?|rupees?|rupaye|rupay|rupaiya|rupiya")}|रुपये|रुपए|रुपया|रूपये|روبية") to listOf("INR", "PKR", "NPR", "LKR")
    )

    /** Marks with one meaning: they name that currency whatever the user's base is. */
    private val CURRENCY_MARKS = (CURRENCY_WORDS.flatMap { it.second }.distinct() - "MAD")
        .map { regex(word(it)) to it } + listOf(Regex("₹") to "INR", Regex("€") to "EUR")

    /**
     * The currency to record: [base] unless the message names another. "dollars" means the
     * user's own dollar when their base is one; with two currencies named, the model's
     * choice picks between them.
     */
    fun currency(message: String, modelCode: String?, base: String): String {
        val model = modelCode?.trim()?.uppercase().orEmpty()
        // Upper case only: "cup" or "try" in a message is a word, not a currency.
        if (isCurrencyCode(model) && Regex("(?<![A-Z])$model(?![A-Z])").containsMatchIn(message)) return model
        val marked = CURRENCY_MARKS.filter { (mark, _) -> mark.containsMatchIn(message) }.map { it.second }
        val named = (
            marked + CURRENCY_WORDS
                // "USD $5": the mark already says which dollar.
                .filter { (words, codes) -> words.containsMatchIn(message) && codes.none { it in marked } }
                .map { (_, codes) -> codes.firstOrNull { it == base } ?: codes.firstOrNull { it == model } ?: codes.first() }
            ).distinct()
        return when {
            model in named -> model
            named.size == 1 -> named.single()
            else -> base
        }
    }

    private fun isCurrencyCode(code: String) =
        code.length == 3 && code.all { it in 'A'..'Z' } && runCatching { java.util.Currency.getInstance(code) }.isSuccess

    // Received money

    private val MONEY_MARK = "(?:[₹$€£]|rs\\.?|sr|jd|dhs?|rupees?|rupaye|रुपये|रुपए|रुपया|dollars?|डॉलर|euros?|pounds?|riyals?|dirhams?|dinars?|" +
        CURRENCY_WORDS.flatMap { it.second }.distinct().joinToString("|") + ")?"
    private val COUNT = "\\.?\\d[\\d,.]*(?:\\s*(?:$SCALE_PATTERN)(?![\\p{L}\\p{M}]))?"
    private val GOT_MONEY = regex(
        "\\b(?:got|mila|mili)\\s+(?:(?:back|paid|my|some|the)\\s+)*$MONEY_MARK\\s*$COUNT" +
            "|(?<![\\p{L}\\p{M}])(?:मिला|मिली|मिले)\\s+$MONEY_MARK\\s*$COUNT" +
            "|$COUNT\\s*$MONEY_MARK\\s*(?:mila|mili|मिला|मिली|मिले)(?![\\p{L}\\p{M}])"
    )

    private val GOT_CASH = regex("\\bgot\\s+(?:(?:back|my|some|the|an?)\\s+)*(?:money|cash|payment|transfer|deposit|tip|advance)\\b(?!\\s+(?:to|for)\\b)")
    private val GOT_WORD = regex("\\b(?:got|mila|mili)\\b|(?<![\\p{L}\\p{M}])(?:मिला|मिली|मिले)(?![\\p{L}\\p{M}])")

    /**
     * "got" and "mila" also mean buying, so they say income only next to [amount] ("got ₹500",
     * "500 रुपये मिले", not "got a phone for 500"), with money itself ("got some cash from mom 500"),
     * or when the message has no digits to place ("got five hundred from mom").
     */
    fun gotMoney(message: String, amount: BigDecimal): Boolean {
        val text = latin(message).lowercase()
        return GOT_CASH.containsMatchIn(text) ||
            GOT_MONEY.findAll(text).any { statesAmount(it.value, amount) } ||
            (text.none { it.isDigit() } && GOT_WORD.containsMatchIn(text))
    }

    // Dates

    private const val MAX_DAYS_AGO = 365

    private val DAY_BEFORE_YESTERDAY = regex("day before yesterday|${word("parso")}|परसों|(?:أول|اول)\\s*(?:امس|أمس|امبارح|مبارح|البارحة)")
    private val YESTERDAY = regex(
        "${word("yesterday|last night|kal")}|(?<![\\p{L}\\p{M}])[وب]?(?:امس|أمس)(?![\\p{L}\\p{M}])|البارح|مبارح|امبارح|${word("कल")}"
    )
    private val TODAY = regex("${word("today|tonight|this morning|aaj")}|اليوم|${word("आज")}")
    private val TWO_DAYS_AGO = regex("(?:قبل|من)\\s*يومين|${word("two days (?:ago|back)|do din (?:pehle|pahle)")}|दो दिन पहले")
    private val N_DAYS_AGO = regex("(\\d+)\\s*(?:days?\\s*ago|din\\s*(?:pehle|pahle)|दिन\\s*पहले)|قبل\\s*(\\d+)\\s*(?:يوم|ايام|أيام)")
    private val N_WEEKS_AGO = regex("(\\d+)\\s*(?:weeks?\\s*ago|hafte\\s*(?:pehle|pahle)|हफ्ते\\s*पहले)|قبل\\s*(\\d+)\\s*(?:اسبوع|أسبوع|اسابيع|أسابيع)")
    private val WEEK_AGO = regex("${word("a week ago|one week ago|ek hafta pehle|قبل\\s*(?:اسبوع|أسبوع)")}|एक हफ्ते पहले")
    private val TWO_WEEKS_AGO = regex("${word("two weeks ago|قبل\\s*(?:اسبوعين|أسبوعين)")}|दो हफ्ते पहले")

    private const val MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?" +
        "|يناير|فبراير|مارس|ابريل|أبريل|مايو|يونيو|يوليو|اغسطس|أغسطس|سبتمبر|اكتوبر|أكتوبر|نوفمبر|ديسمبر" +
        "|जनवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर"

    // A day the words name but this code can't turn into a number: the model's reading counts.
    private val OTHER_DAY = regex(
        word(
            "(?:on|last)\\s+(?:mon|tues|wednes|thurs|fri|satur|sun)day|last week|last month" +
                // Any other count: "three days ago", "a few weeks back", "3 months ago".
                "|(?:days?|weeks?|months?)\\s+(?:ago|back)|(?:din|hafte|mahine)\\s*(?:pehle|pahle)|(?:दिन|हफ्ते|महीने)\\s*पहले" +
                "|الاسبوع الماضي|الأسبوع الماضي|الشهر الماضي|قبل\\s*شهر(?:ين)?|पिछले हफ्ते|पिछले महीने|pichhle (?:hafte|mahine)" +
                // A bare "24/7" is a shop's hours, not a date.
                "|(?:on|في|بتاريخ)\\s*\\d{1,2}[/-]\\d{1,2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}" +
                "|\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:$MONTHS)|(?:$MONTHS)\\s+\\d{1,2}(?:st|nd|rd|th)?"
        ) + "|يوم\\s*(?:ال)?(?:احد|أحد|اثنين|إثنين|ثلاثاء|اربعاء|أربعاء|خميس|جمعة|سبت)|قبل\\s*(?:ثلاث|تلات|اربع|أربع|خمس|ست|سبع)"
    )

    /** Days ago the message states, as numbers. */
    private fun statedDays(message: String): Set<Int> {
        val text = latin(message)
        val days = mutableSetOf<Int>()
        // Longest phrases first, each removed so "day before yesterday" can't also read as yesterday.
        var rest = text
        fun take(pattern: Regex, day: Int) {
            if (pattern.containsMatchIn(rest)) { days += day; rest = pattern.replace(rest, " ") }
        }
        fun takeCounted(pattern: Regex, daysEach: Long) {
            pattern.findAll(rest).forEach { m ->
                val count = m.groupValues[1].ifEmpty { m.groupValues[2] }.toLongOrNull() ?: return@forEach
                (count * daysEach).takeIf { it in 0..MAX_DAYS_AGO }?.let { days += it.toInt() }
            }
            rest = pattern.replace(rest, " ")
        }
        take(DAY_BEFORE_YESTERDAY, 2)
        take(TWO_DAYS_AGO, 2)
        take(TWO_WEEKS_AGO, 14)
        take(WEEK_AGO, 7)
        takeCounted(N_WEEKS_AGO, 7)
        takeCounted(N_DAYS_AGO, 1)
        take(YESTERDAY, 1)
        take(TODAY, 0)
        return days
    }

    /** A day the code can't count. A number it refused ("60 weeks ago") isn't one. */
    private fun namesOtherDay(message: String) =
        OTHER_DAY.containsMatchIn(N_DAYS_AGO.replace(N_WEEKS_AGO.replace(latin(message), " "), " "))

    /** Whether the message names a particular day at all. */
    fun statesADay(message: String): Boolean = statedDays(message).isNotEmpty() || namesOtherDay(message)

    /** How many days ago to date the transaction: today unless the message says otherwise. */
    fun daysAgo(message: String, modelDays: Any?): Int {
        val model = when (modelDays) {
            is Number -> modelDays.toInt()
            is String -> modelDays.trim().toIntOrNull()
            else -> null
        }
        val stated = statedDays(message)
        return when {
            model != null && model in stated -> model
            stated.size == 1 -> stated.single()
            stated.isEmpty() && model != null && model in 1..MAX_DAYS_AGO && namesOtherDay(message) -> model
            else -> 0
        }
    }
}
