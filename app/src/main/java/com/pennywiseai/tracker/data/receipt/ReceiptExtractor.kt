package com.pennywiseai.tracker.data.receipt

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
        val currency: String? = null,
        val date: LocalDate? = null,
        val merchant: String? = null,
    ) {
        val isEmpty: Boolean get() = amount == null && date == null && merchant == null
    }

    /**
     * [knownMerchants] are the user's past merchants: a header line that matches one is
     * named as the user already knows it. [monthFirst] reads an ambiguous "03/04/2026" as
     * March 4th (US receipts) instead of April 3rd.
     */
    fun extract(
        lines: List<String>,
        knownMerchants: List<String> = emptyList(),
        today: LocalDate = LocalDate.now(),
        monthFirst: Boolean = false,
    ): Scan {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return Scan(
            amount = total(clean),
            currency = currency(clean),
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
    private val NUMBER = Regex("""(?<![\d.,])\d+(?:[.,]\d+)*""")
    private const val MAX_AMOUNT = 10_000_000

    private fun total(lines: List<String>): BigDecimal? {
        var best: BigDecimal? = null
        var bestRank = 0
        lines.forEachIndexed { i, line ->
            val lower = line.lowercase()
            val rank = when {
                STRONG_TOTAL.any { it in lower } -> 2
                TOTAL.containsMatchIn(lower) && !NOT_TOTAL.containsMatchIn(lower) -> 1
                else -> 0
            }
            if (rank == 0 || rank < bestRank) return@forEachIndexed
            // OCR can put the figure on the line under its label.
            val amount = amounts(line).maxOrNull()
                ?: lines.getOrNull(i + 1)?.takeIf { it.none(Char::isLetter) }?.let { amounts(it).maxOrNull() }
                ?: return@forEachIndexed
            if (rank > bestRank || amount > best) {
                best = amount
                bestRank = rank
            }
        }
        // No total line: the largest figure with cents is most likely what was paid.
        return best ?: lines.filterNot { NOT_TOTAL.containsMatchIn(it.lowercase()) }
            .flatMap { amounts(it, centsOnly = true) }
            .maxOrNull()
    }

    /** The amounts on [line]; with [centsOnly], only figures written with two decimals. */
    internal fun amounts(line: String, centsOnly: Boolean = false): List<BigDecimal> =
        NUMBER.findAll(DATE_OR_TIME.replace(line, " "))
            .mapNotNull { amount(it.value, centsOnly) }
            .filter { it.signum() > 0 && it < BigDecimal(MAX_AMOUNT) }
            .toList()

    /**
     * "1,234.56", "1.234,56", "9,45", "1,200" → a number: the last separator is the
     * decimal point when one or two digits follow it, and the rest are thousands
     * separators, which must then group by three (or by two before that, as in "1,25,000").
     */
    private fun amount(token: String, centsOnly: Boolean): BigDecimal? {
        val parts = token.split('.', ',')
        if (parts.size == 1) return if (centsOnly) null else token.toBigDecimalOrNull()
        val last = parts.last()
        val hasDecimals = last.length in 1..2
        val groups = if (hasDecimals) parts.dropLast(1) else parts
        if (groups.size > 1 && (groups.last().length != 3 || groups.drop(1).any { it.length !in 2..3 })) return null
        if (!hasDecimals && (centsOnly || last.length != 3)) return null
        if (centsOnly && last.length != 2) return null
        val whole = groups.joinToString("")
        return (if (hasDecimals) "$whole.$last" else whole).toBigDecimalOrNull()
    }

    // ── Currency ──

    // Only marks that name one currency: "$" and "¥" are shared by several.
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
    private val RUPEES = Regex("""\b(?:rs\.?|inr)\s*\d""", RegexOption.IGNORE_CASE)

    /** The receipt's currency, only when exactly one is named on it. */
    private fun currency(lines: List<String>): String? {
        val found = mutableSetOf<String>()
        for (line in lines) {
            CURRENCY_MARKS.forEach { (mark, code) -> if (mark in line) found += code }
            CODE.findAll(line).map { it.value }.filter { it in CURRENCY_CODES }.forEach { found += it }
            if (RUPEES.containsMatchIn(line)) found += "INR"
        }
        return found.singleOrNull()
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
