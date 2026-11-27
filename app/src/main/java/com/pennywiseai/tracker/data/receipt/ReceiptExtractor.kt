package com.pennywiseai.tracker.data.receipt

import com.pennywiseai.shared.domain.money.MinorUnits
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import kotlin.math.abs

/**
 * Reads a receipt's total, date, shop and currency out of the text lines OCR found on it,
 * top to bottom. Every field is a best guess and null when unclear: the form shows what
 * was found and the user checks it before saving.
 */
object ReceiptExtractor {
    data class Scan(
        val amount: BigDecimal? = null,
        // What the amount's printed currency mark could be: empty when none is printed, one
        // code when it names one ("AED", "€"), several when it's shared ("$", "Rs").
        val currencies: Set<String> = emptySet(),
        val date: LocalDate? = null,
        val merchant: String? = null,
    ) {
        /** The amount's currency when the receipt names exactly one. */
        val currency: String? get() = currencies.singleOrNull()

        val isEmpty: Boolean get() = amount == null && date == null && merchant == null
    }

    /**
     * [knownMerchants] are the user's past merchants: a header line that matches one is
     * named as the user already knows it. [currency] is the form's: a figure with no
     * currency printed by it is read with that currency's decimals ("12.500" is 12.5 dinars
     * but twelve thousand five hundred rupees); null leaves such a figure unread.
     *
     * The text comes from a Latin-script OCR model, so labels in other scripts (the Arabic
     * and Devanagari ones below) only help when the receipt also prints them in Latin
     * letters or OCR is upgraded; they never match anything wrongly.
     */
    fun extract(
        lines: List<String>,
        knownMerchants: List<String> = emptyList(),
        today: LocalDate = LocalDate.now(),
        monthFirst: Boolean = false,
        currency: String? = null,
    ): Scan {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val total = total(clean, currency)
        return Scan(
            amount = total?.first,
            currencies = total?.second.orEmpty(),
            date = clean.firstNotNullOfOrNull { date(it, today, monthFirst) },
            merchant = merchant(clean, knownMerchants),
        )
    }

    // ── Total ──

    // Strongest first: a line naming what was actually paid beats a bare "Total".
    private val STRONG_TOTAL = listOf(
        "grand total", "amount paid", "amount due", "balance due", "total due", "total payable",
        "net payable", "net amount", "net total", "to pay", "الإجمالي", "الاجمالي", "कुल",
    )
    private val TOTAL = Regex("""\b(?:total|net)\b|المجموع""")

    // Lines that name a part of the bill, or what was handed over, rather than the bill.
    private val NOT_TOTAL = Regex(
        """\b(?:sub\s*-?\s*total|tax|vat|gst|tip|change|cash|tendered|discount|savings?|round(?:ing)?)\b|الفرعي|ضريبة"""
    )

    // A date or a time would otherwise read as amounts ("12.05", "10:45").
    private val DATE_OR_TIME = Regex("""\d{1,4}[./-]\d{1,2}[./-]\d{1,4}|\d{1,2}:\d{2}(?::\d{2})?""")

    // Thousands may be grouped by a (narrow) no-break or thin space anywhere ("1 234.56"), and by
    // a plain space before a decimal comma ("1 234,56"); a plain space before a decimal point is
    // more likely two figures ("Qty 3 120.00").
    private val NUMBER = Regex(
        """(?<![\d.,])(?:\d{1,3}(?:[\u00A0\u202F\u2009]\d{3})+(?:[.,]\d+)?(?!\d)|\d{1,3}(?: \d{3})+,\d+|\d+(?:[.,]\d+)*)"""
    )
    private val GROUP_SPACE = Regex("""[ \u00A0\u202F\u2009]""")
    private const val MAX_AMOUNT = 10_000_000

    /**
     * The total and its currency. A labeled total that can't be read for sure (no figure by
     * it, a figure whose decimals are unclear, two currencies) leaves the amount out rather
     * than guessing another figure; only a receipt with no total label at all falls back to
     * its largest figure with decimals.
     */
    private fun total(lines: List<String>, formCurrency: String?): Pair<BigDecimal, Set<String>>? {
        val receiptCurrency = currencyOf(lines.joinToString(" "))
        // Per labeled line: its rank and what it reads, null when it can't be read.
        val found = mutableListOf<Pair<Int, Pair<BigDecimal, Set<String>>?>>()
        lines.forEachIndexed { i, line ->
            val lower = line.lowercase()
            val rank = when {
                STRONG_TOTAL.any { it in lower } -> 2
                TOTAL.containsMatchIn(lower) && !NOT_TOTAL.containsMatchIn(lower) -> 1
                else -> 0
            }
            if (rank == 0) return@forEachIndexed
            // OCR can put the figure on the line under its label, maybe with its currency.
            val figureLine = if (NUMBER.containsMatchIn(DATE_OR_TIME.replace(line, " "))) line
            else lines.getOrNull(i + 1)?.takeIf { isFigureLine(it) }
            found += rank to figureLine?.let { read(if (it == line) line else "$line $it", it, receiptCurrency, formCurrency) }
        }
        val top = found.maxOfOrNull { it.first }
            ?: return read(lines.filterNot { NOT_TOTAL.containsMatchIn(it.lowercase()) }, receiptCurrency, formCurrency)
        val best = found.filter { it.first == top }.map { it.second ?: return null }
        // Totals in two currencies (a dual-currency receipt): which was paid isn't clear.
        if (best.map { it.second }.distinct().size > 1) return null
        return best.maxBy { it.first }
    }

    /** The largest figure on [figureLine] with the currency printed on [text] (its label and figure lines). */
    private fun read(text: String, figureLine: String, receiptCurrency: Set<String>?, formCurrency: String?): Pair<BigDecimal, Set<String>>? {
        val currencies = currencyOf(text) ?: return null
        val named = currencies.ifEmpty { receiptCurrency ?: return null }
        val digits = digitsOf(named, formCurrency)
        val values = amountsOrNull(figureLine, digits) ?: return null
        return values.maxOrNull()?.let { it to named }
    }

    /** No total label: the largest figure written with the currency's decimals is most likely what was paid. */
    private fun read(lines: List<String>, receiptCurrency: Set<String>?, formCurrency: String?): Pair<BigDecimal, Set<String>>? {
        val named = receiptCurrency ?: return null
        val digits = digitsOf(named, formCurrency) ?: return null
        return lines.flatMap { amounts(it, digits, centsOnly = true) }.maxOrNull()?.let { it to named }
    }

    // A line holding only a figure, maybe with its currency: "27.30", "AED 27.30", "12,50 €".
    private fun isFigureLine(line: String): Boolean =
        NUMBER.containsMatchIn(line) && CURRENCY_WORDS.replace(line, " ").none(Char::isLetter)

    /** How many decimals [currencies] count, or the form's when none is printed; null when unclear. */
    private fun digitsOf(currencies: Set<String>, formCurrency: String?): Int? {
        if (currencies.isEmpty()) return formCurrency?.let(MinorUnits::digits)
        return currencies.map(MinorUnits::digits).distinct().singleOrNull()
    }

    /** The amounts on [line] read with [digits] decimals; null when one of them can't be read for sure. */
    private fun amountsOrNull(line: String, digits: Int?): List<BigDecimal>? {
        val tokens = NUMBER.findAll(DATE_OR_TIME.replace(line, " ")).map { it.value }.toList()
        val values = tokens.map { amount(it, digits, centsOnly = false) ?: return null }
        return values.filter { it.signum() > 0 && it < BigDecimal(MAX_AMOUNT) }
    }

    /** The amounts on [line]; with [centsOnly], only figures written with the currency's decimals. */
    internal fun amounts(line: String, digits: Int? = 2, centsOnly: Boolean = false): List<BigDecimal> =
        NUMBER.findAll(DATE_OR_TIME.replace(line, " "))
            .mapNotNull { amount(it.value, digits, centsOnly) }
            .filter { it.signum() > 0 && it < BigDecimal(MAX_AMOUNT) }
            .toList()

    /**
     * "1,234.56", "1.234,56", "9,45", "1,200", "1 234,56" → a number, read with the [digits]
     * decimals its currency counts: the last separator is the decimal point when it differs
     * from the others or fewer than three digits follow it; three digits after a lone separator
     * are fils for a three-decimal currency ("12.500" dinars) and thousands otherwise ("1,200"
     * rupees), and unclear when [digits] is unknown (null). Thousands separators must group by
     * three (or by two before that, as in "1,25,000").
     */
    private fun amount(token: String, digits: Int?, centsOnly: Boolean): BigDecimal? {
        val plain = GROUP_SPACE.replace(token, "")
        val parts = plain.split('.', ',')
        if (parts.size == 1) return if (centsOnly) null else plain.toBigDecimalOrNull()
        val separators = plain.filter { it == '.' || it == ',' }
        val last = parts.last()
        if (separators.dropLast(1).toSet().size > 1) return null
        val hasDecimals = when {
            separators.dropLast(1).any { it == separators.last() } -> false
            separators.length > 1 || last.length < 3 -> true
            last.length == 3 -> (digits ?: return null) == 3
            else -> return null
        }
        if (hasDecimals && last.length > (digits ?: 3)) return null
        val groups = if (hasDecimals) parts.dropLast(1) else parts
        if (groups.size > 1 && (groups.last().length != 3 || groups.drop(1).any { it.length !in 2..3 })) return null
        if (centsOnly && (!hasDecimals || last.length != digits)) return null
        val whole = groups.joinToString("")
        return (if (hasDecimals) "$whole.$last" else whole).toBigDecimalOrNull()
    }

    // ── Currency ──

    // Marks that name one currency.
    private val CURRENCY_MARKS = mapOf(
        "₹" to "INR", "€" to "EUR", "£" to "GBP", "₩" to "KRW", "₱" to "PHP", "₺" to "TRY",
        "₫" to "VND", "₦" to "NGN", "৳" to "BDT", "د.إ" to "AED", "ر.س" to "SAR",
    )
    private val CURRENCY_CODES = setOf(
        "INR", "USD", "EUR", "GBP", "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "JOD", "EGP",
        "PKR", "BDT", "LKR", "NPR", "SGD", "MYR", "THB", "IDR", "PHP", "VND", "JPY", "CNY",
        "KRW", "AUD", "NZD", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF",
        "ZAR", "NGN", "KES", "BRL", "MXN", "UAH", "ILS",
    )
    private val CODE = Regex("""\b[A-Z]{3}\b""")

    // Dollar signs with their country letters name one currency; a bare "$", "¥", "Rs" or
    // "kr" is shared by several, and only says which ones it could be.
    private val PREFIXED_DOLLARS = mapOf("US" to "USD", "A" to "AUD", "AU" to "AUD", "C" to "CAD", "CA" to "CAD", "NZ" to "NZD", "S" to "SGD", "SG" to "SGD", "HK" to "HKD", "R" to "BRL", "MX" to "MXN")
    private val DOLLAR = Regex("""(?<![A-Za-z])([A-Z]{1,2})?\$""")
    private val DOLLARS = setOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD", "TWD", "MXN", "ARS", "CLP", "COP", "JMD", "TTD", "BSD", "BBD", "FJD")
    private val RUPEES = Regex("""\brs\b\.?""", RegexOption.IGNORE_CASE)
    private val RUPEE_CODES = setOf("INR", "PKR", "LKR", "NPR")
    private val YEN = setOf("JPY", "CNY")
    private val KRONOR = Regex("""\bkr\b\.?""", RegexOption.IGNORE_CASE)
    private val KRONA_CODES = setOf("SEK", "NOK", "DKK", "ISK")

    // Everything [currencyOf] reads as a currency, to see past it on a figure's line.
    private val CURRENCY_WORDS = Regex(
        (
            CURRENCY_MARKS.keys.map { Regex.escape(it) } +
                listOf(CURRENCY_CODES.joinToString("|", """\b(?:""", """)\b"""), DOLLAR.pattern, "(?i:${RUPEES.pattern}|${KRONOR.pattern})", "¥")
            ).joinToString("|")
    )

    /**
     * The currencies [text] can be in: empty when none is named, the one it names, or the
     * ones a shared mark could be. Null when it names two that can't be the same ("1 USD =
     * 3.67 AED"), so no single currency fits.
     */
    private fun currencyOf(text: String): Set<String>? {
        val marks = mutableListOf<Set<String>>()
        CURRENCY_MARKS.forEach { (mark, code) -> if (mark in text) marks += setOf(code) }
        CODE.findAll(text).map { it.value }.filter { it in CURRENCY_CODES }.forEach { marks += setOf(it) }
        DOLLAR.findAll(text).forEach { m ->
            val prefix = m.groupValues[1]
            marks += PREFIXED_DOLLARS[prefix]?.let(::setOf) ?: DOLLARS
        }
        if ("¥" in text) marks += YEN
        if (RUPEES.containsMatchIn(text)) marks += RUPEE_CODES
        if (KRONOR.containsMatchIn(text)) marks += KRONA_CODES
        if (marks.isEmpty()) return emptySet()
        return marks.reduce { a, b -> a intersect b }.ifEmpty { null }
    }

    // ── Date ──

    private val YEAR_FIRST = Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})\b""")
    private const val MONTH =
        """(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b\.?"""
    private val DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s-]*$MONTH[\s,-]*(\d{4}|\d{2})\b""", RegexOption.IGNORE_CASE)
    private val MONTH_DAY = Regex("""\b$MONTH\s*(\d{1,2})(?:st|nd|rd|th)?,?\s*(\d{4})\b""", RegexOption.IGNORE_CASE)

    /** The first date on [line] that's a real day not after [today]. */
    internal fun date(line: String, today: LocalDate, monthFirst: Boolean = false): LocalDate? {
        val candidates = sequence {
            YEAR_FIRST.findAll(line).forEach { m ->
                val (y, mo, d) = m.destructured
                yield(dateOf(y.toInt(), mo.toInt(), d.toInt()))
            }
            NUMERIC.findAll(line).forEach { m ->
                val (a, b, y) = m.destructured
                val first = a.toInt()
                val second = b.toInt()
                // Whichever can't be a month settles it; otherwise the region's order.
                val dayFirst = when {
                    first > 12 -> true
                    second > 12 -> false
                    else -> !monthFirst
                }
                yield(if (dayFirst) dateOf(year(y), second, first) else dateOf(year(y), first, second))
            }
            DAY_MONTH.findAll(line).forEach { m ->
                val (d, mo, y) = m.destructured
                yield(dateOf(year(y), month(mo), d.toInt()))
            }
            MONTH_DAY.findAll(line).forEach { m ->
                val (mo, d, y) = m.destructured
                yield(dateOf(y.toInt(), month(mo), d.toInt()))
            }
        }
        return candidates.filterNotNull().firstOrNull { !it.isAfter(today) && it.year >= 2000 }
    }

    private fun year(text: String): Int = text.toInt().let { if (text.length == 2) 2000 + it else it }

    private fun month(text: String): Int =
        Month.entries.first { it.name.startsWith(text.take(3).uppercase()) }.value

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    // ── Merchant ──

    // Header lines that name the paper, the till or a contact, not the shop.
    private val NOT_MERCHANT = Regex(
        """\b(?:receipt|invoice|bill|welcome|thank|order|table|cashier|copy|tel|phone|gstin|trn|vat)\b|www|http|@"""
    )
    private const val HEADER_LINES = 3

    /**
     * The shop: among the first few lines without digits, one that matches a past merchant,
     * else the first of them.
     */
    private fun merchant(lines: List<String>, known: List<String>): String? {
        val header = lines.asSequence()
            .take(HEADER_LINES + 3)
            .filter { line -> line.none(Char::isDigit) && line.count(Char::isLetter) >= 3 }
            .filterNot { NOT_MERCHANT.containsMatchIn(it.lowercase()) }
            .take(HEADER_LINES)
            .toList()
        if (header.isEmpty()) return null
        return header.firstNotNullOfOrNull { matchMerchant(it, known) } ?: tidy(header.first())
    }

    /**
     * The past merchant [candidate] names: the same words, or one's words inside the
     * other's ("COFFEE HUT" for "Coffee Hut Downtown"), the closest in length winning.
     */
    fun matchMerchant(candidate: String, known: List<String>): String? {
        val key = normalize(candidate)
        if (key.length < 3) return null
        known.firstOrNull { normalize(it) == key }?.let { return it }
        return known
            .filter { name ->
                val other = normalize(name)
                minOf(key.length, other.length) >= 5 && (" $other ".contains(" $key ") || " $key ".contains(" $other "))
            }
            .minByOrNull { abs(normalize(it).length - key.length) }
    }

    /** Lowercase words of letters and digits: "Coffee-Hut, Ltd." → "coffeehut ltd". */
    private fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    private val WHITESPACE = Regex("""\s+""")

    /** "COFFEE HUT" → "Coffee Hut"; a line with lowercase letters is kept as printed. */
    private fun tidy(line: String): String =
        if (line.any(Char::isLowerCase)) line
        else line.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}
