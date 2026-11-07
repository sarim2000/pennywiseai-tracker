package com.pennywiseai.parser.core

import java.math.BigDecimal

/** What one piece of a taught message is. Stored by name, so never rename a value. */
enum class TemplateField {
    /** Text that must appear exactly. Currency codes and words like "approved" stay here. */
    LITERAL,
    AMOUNT,
    MERCHANT,
    ACCOUNT,

    /** A number nobody tapped: a date, a time, a balance. Read past, never kept. */
    NUMBER,

    /** One word holding digits and letters: "AB12CD34", "12-Oct-26". Read past, never kept. */
    TOKEN,

    /** A month, weekday or AM/PM beside a number. Only those words match, so "approved" can't. */
    DATE_WORD
}

data class TemplatePart(val field: TemplateField, val text: String = "")

/** Which separator marks the decimals of this sender's amounts. */
enum class AmountFormat { DOT_DECIMAL, COMMA_DECIMAL }

/**
 * The shape of one kind of SMS, taught by tapping an example (#279). It reads a later
 * message only when every part of the shape fits, so a message it doesn't understand
 * reads as nothing rather than as a wrong amount.
 *
 * Whole-shape matching with no regex: no pattern is ever compiled from what a user
 * tapped, so a template can't hang on a message or misread `Rs.` the way PR #302's
 * generated expressions did.
 *
 * ponytail: a number that changes the meaning ("status 00" vs "status 51") looks like any
 * other number, so a template only ever drafts a transaction for the user to confirm.
 * Unattended booking needs more than the shape, such as confirmed-history evidence.
 */
data class MessageTemplate(
    val id: String,
    val senderKey: String,
    val parts: List<TemplatePart>,
    val type: TransactionType,
    val currency: String,
    val bankName: String,
    val amountFormat: AmountFormat? = null,
    val fixedMerchant: String? = null
)

/** Where the user tapped, as ranges of the message exactly as received. */
data class TeachSelection(
    val amount: IntRange,
    val merchant: IntRange? = null,
    val account: IntRange? = null
)

/** One word or number in a message that a tap can pick, as a range of the original text. */
data class TappableSpan(val range: IntRange, val isNumber: Boolean)

/** The first reading of a message, to pre-highlight. Anything unsure stays null for the user to tap. */
data class TeachGuess(
    val amount: IntRange? = null,
    val merchant: IntRange? = null,
    val account: IntRange? = null,
    val type: TransactionType? = null,
    val currency: String? = null
)

object MessageTemplates {
    private const val MAX_BODY = 2000
    private const val MAX_MERCHANT = 60
    private const val MAX_DIGITS = 15
    private const val MAX_TOKEN = 64
    private const val MIN_LITERAL_LETTERS = 3

    // ISO 4217 currencies with three decimals, where "1,234" can really mean 1.234.
    private val THREE_DECIMALS = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

    /**
     * The template that [selection] on [body] describes, or null if it couldn't read
     * [body] back to exactly that selection: no template is better than a wrong one.
     */
    fun build(
        id: String,
        senderKey: String,
        body: String,
        selection: TeachSelection,
        type: TransactionType,
        currency: String,
        bankName: String,
        amountFormat: AmountFormat? = null,
        fixedMerchant: String? = null
    ): MessageTemplate? {
        if (body.length > MAX_BODY || !isCurrencyCode(currency)) return null
        val text = normalize(body)
        val amount = text.fromOriginal(selection.amount) ?: return null
        if (isGroupFragment(text.text, amount) || isBareFraction(text.text, amount)) return null
        val merchant = selection.merchant?.let { text.fromOriginal(it) ?: return null }
        val account = selection.account?.let { text.fromOriginal(it) ?: return null }

        val slots = listOfNotNull(
            amount to TemplateField.AMOUNT,
            merchant?.let { it to TemplateField.MERCHANT },
            account?.let { it to TemplateField.ACCOUNT }
        ).sortedBy { it.first.first }
        if (slots.zipWithNext().any { (a, b) -> a.first.last >= b.first.first }) return null

        val parts = ArrayList<TemplatePart>()
        var at = 0
        for ((range, field) in slots) {
            maskGap(text.text.substring(at, range.first), parts)
            parts += TemplatePart(field)
            at = range.last + 1
        }
        maskGap(text.text.substring(at), parts)

        val merged = mergeLiterals(parts)
        // A merchant ends at the text after it; with none there, its end would be a guess.
        val merchantAt = merged.indexOfFirst { it.field == TemplateField.MERCHANT }
        if (merchantAt >= 0 && merchantAt < merged.lastIndex && merged[merchantAt + 1].field != TemplateField.LITERAL) {
            return null
        }
        // Nearly all numbers would match anything from this sender.
        val letters = merged.filter { it.field == TemplateField.LITERAL }.sumOf { p -> p.text.count { it.isLetter() } }
        if (letters < MIN_LITERAL_LETTERS) return null

        val template = MessageTemplate(
            id = id, senderKey = senderKey, parts = merged, type = type, currency = currency,
            bankName = bankName, amountFormat = amountFormat, fixedMerchant = fixedMerchant
        )
        val expected = Captures(
            amount = text.text.substring(amount.first, amount.last + 1),
            merchant = merchant?.let { text.text.substring(it.first, it.last + 1).trim() },
            account = account?.let { text.text.substring(it.first, it.last + 1) }
        )
        return template.takeIf { read(it, text.text) == expected && parseAmount(expected.amount.orEmpty(), amountFormat, currency) != null }
    }

    /** [body] as [template] reads it, or null unless every part fits exactly one way. */
    fun match(template: MessageTemplate, body: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (body.length > MAX_BODY) return null
        val found = read(template, normalize(body).text) ?: return null
        val amount = parseAmount(found.amount ?: return null, template.amountFormat, template.currency) ?: return null
        return ParsedTransaction(
            amount = amount,
            type = template.type,
            merchant = found.merchant ?: template.fixedMerchant,
            reference = null,
            accountLast4 = found.account?.takeLast(4),
            balance = null,
            smsBody = body,
            sender = sender,
            timestamp = timestamp,
            bankName = template.bankName,
            currency = template.currency
        )
    }

    /** The one reading [templates] agree on. Two that disagree mean nobody knows, so nothing. */
    fun matchAny(templates: List<MessageTemplate>, body: String, sender: String, timestamp: Long): ParsedTransaction? {
        val readings = templates.mapNotNull { match(it, body, sender, timestamp) }
        val first = readings.firstOrNull() ?: return null
        val same = readings.all {
            it.amount.compareTo(first.amount) == 0 && it.type == first.type && it.currency == first.currency &&
                it.merchant == first.merchant && it.accountLast4 == first.accountLast4
        }
        return first.takeIf { same }
    }

    /**
     * [text] (one number, digits already ASCII or not) as a value. Null when the
     * separators don't fit [format], or when [format] is null and they could mean two things.
     */
    fun parseAmount(text: String, format: AmountFormat?, currency: String): BigDecimal? {
        val written = normalize(text).text
        // "٫" can only be a decimal point and "٬" only a thousands mark, whatever else this sender writes.
        val arabic = written.any { it == ARABIC_DECIMAL || it == ARABIC_THOUSANDS }
        val number = written.replace(ARABIC_DECIMAL, '.').replace(ARABIC_THOUSANDS, ',')
        val resolved = when {
            arabic -> AmountFormat.DOT_DECIMAL
            format != null -> format
            else -> inferFormat(number, currency) ?: when {
                number.none { it == '.' || it == ',' } -> AmountFormat.DOT_DECIMAL
                else -> return null
            }
        }
        val decimal = if (resolved == AmountFormat.DOT_DECIMAL) '.' else ','
        val grouping = if (resolved == AmountFormat.DOT_DECIMAL) ',' else '.'

        val pieces = number.split(decimal)
        if (pieces.size > 2) return null
        val whole = pieces[0]
        val fraction = pieces.getOrNull(1).orEmpty()
        if (pieces.size == 2 && (fraction.isEmpty() || fraction.length > 3 || !fraction.all(::isDigit))) return null
        if (!validWhole(whole, grouping)) return null
        val digits = whole.replace(grouping.toString(), "")
        if (digits.length > MAX_DIGITS) return null
        val value = try {
            BigDecimal(if (fraction.isEmpty()) digits else "$digits.$fraction")
        } catch (e: Exception) {
            return null
        }
        return value.takeIf { it.signum() > 0 }
    }

    /**
     * What the separators in [number] say. Null when there are none, or when one
     * separator before three digits in a three-decimal currency could be either.
     */
    fun inferFormat(number: String, currency: String): AmountFormat? {
        val text = normalize(number).text
        if (text.any { it == ARABIC_DECIMAL || it == ARABIC_THOUSANDS }) return AmountFormat.DOT_DECIMAL
        val dots = text.count { it == '.' }
        val commas = text.count { it == ',' }
        return when {
            dots > 0 && commas > 0 -> if (text.lastIndexOf('.') > text.lastIndexOf(',')) AmountFormat.DOT_DECIMAL else AmountFormat.COMMA_DECIMAL
            dots > 1 -> AmountFormat.COMMA_DECIMAL
            commas > 1 -> AmountFormat.DOT_DECIMAL
            dots == 0 && commas == 0 -> null
            else -> {
                val separator = if (dots == 1) '.' else ','
                val after = text.length - text.indexOf(separator) - 1
                when {
                    after != 3 -> if (dots == 1) AmountFormat.DOT_DECIMAL else AmountFormat.COMMA_DECIMAL
                    currency in THREE_DECIMALS -> null
                    else -> if (dots == 1) AmountFormat.COMMA_DECIMAL else AmountFormat.DOT_DECIMAL
                }
            }
        }
    }

    /** True when only the user can say what [number]'s separator means in [currency]. */
    fun isFormatAmbiguous(number: String, currency: String): Boolean {
        val text = normalize(number).text
        return text.any { it == '.' || it == ',' } && inferFormat(text, currency) == null
    }

    /** Every word and number in [body] a tap can select, in reading order. */
    fun tappableSpans(body: String): List<TappableSpan> {
        val text = normalize(body)
        val s = text.text
        val spans = ArrayList<TappableSpan>()
        var i = 0
        while (i < s.length) {
            when {
                isDigit(s[i]) -> {
                    val end = scanNumber(s, i, AMOUNT_SEPARATORS)
                    spans += TappableSpan(text.toOriginal(i until end), isNumber = true)
                    i = end
                }
                isWordChar(s[i]) -> {
                    var end = i
                    while (end < s.length && !s[end].isWhitespace() && !isDigit(s[end])) end++
                    while (end > i && !isWordChar(s[end - 1])) end--
                    spans += TappableSpan(text.toOriginal(i until end), isNumber = false)
                    i = end
                }
                else -> i++
            }
        }
        return spans
    }

    /** A first reading of [body]: the fields the shipped extractor finds, plus what sits beside a currency. */
    fun guess(sender: String, body: String): TeachGuess {
        if (body.length > MAX_BODY) return TeachGuess()
        val text = normalize(body)
        val s = text.text
        val generic = GenericTransactionExtractor.extract(sender, s)?.transaction

        val numbers = numberRanges(s)
        val priced = numbers.filter { currencyAt(s, it) != null && !isFigure(s, it) && !isGroupFragment(s, it) && !isBareFraction(s, it) }
        // Two different prices in one message: only the user knows which one moved.
        val amount = priced.singleOrNull()
        val merchant = (generic?.merchant ?: merchantWords(s))?.let { name ->
            s.indexOf(name).takeIf { it >= 0 && s.indexOf(name, it + 1) < 0 }?.let { it until it + name.length }
        }
        val account = generic?.accountLast4?.let { last4 ->
            numbers.filter { s.substring(it.first, it.last + 1).endsWith(last4) }.singleOrNull()
        }
        return TeachGuess(
            amount = amount?.let(text::toOriginal),
            merchant = merchant?.let(text::toOriginal),
            account = account?.takeIf { it != amount }?.let(text::toOriginal),
            type = generic?.type ?: GenericTransactionExtractor.directionOf(s) ?: arabicDirection(s),
            currency = amount?.let { currencyAt(s, it) }
        )
    }

    /** The currency printed beside the number at [range] of [body], if there is exactly one. */
    fun currencyBeside(body: String, range: IntRange): String? {
        val text = normalize(body)
        return text.fromOriginal(range)?.let { currencyAt(text.text, it) }
    }

    /** A message worth offering for teaching: it shows a priced number and isn't an OTP, promo or failure. */
    fun looksLikeMoney(sender: String, body: String): Boolean {
        if (GenericTransactionExtractor.isNeverTransaction(sender, body)) return false
        if (NOT_MONEY_AR.any { it in body }) return false
        val s = normalize(body).text
        return numberRanges(s).any { currencyAt(s, it) != null }
    }

    /**
     * The text before and after [range], for showing where an amount was read from,
     * as a range of [body]. Never starts or ends inside a word.
     */
    fun contextAround(body: String, range: IntRange, wordsEachSide: Int = 2): IntRange {
        val spans = tappableSpans(body)
        val words = wordsEachSide.coerceIn(0, spans.size)
        val inside = spans.indexOfFirst { it.range.first <= range.first && range.first <= it.range.last }
        if (inside < 0) return range
        val last = spans.indexOfFirst { it.range.first <= range.last && range.last <= it.range.last }.takeIf { it >= 0 } ?: inside
        return spans[(inside - words).coerceAtLeast(0)].range.first..spans[(last + words).coerceAtMost(spans.lastIndex)].range.last
    }

    // ─── Reading ──────────────────────────────────────────────────────────────

    private data class Captures(val amount: String? = null, val merchant: String? = null, val account: String? = null)

    /** A restored template can hold anything, so its shape is checked here rather than trusted. */
    private fun read(template: MessageTemplate, normalized: String): Captures? {
        if (template.parts.count { it.field == TemplateField.MERCHANT } > 1) return null
        return scan(template.parts, 0, 0, normalized, Captures())
    }

    private fun scan(parts: List<TemplatePart>, from: Int, start: Int, s: String, captured: Captures): Captures? {
        var pos = start
        var found = captured
        for (i in from until parts.size) {
            val part = parts[i]
            when (part.field) {
                TemplateField.LITERAL -> {
                    if (!s.startsWith(part.text, pos)) return null
                    pos += part.text.length
                }
                TemplateField.AMOUNT -> {
                    val end = scanNumber(s, pos, AMOUNT_SEPARATORS)
                    // "1" of "1 234.50" is a number too, and the part after it could read past the rest.
                    if (end <= pos || isGroupFragment(s, pos until end)) return null
                    found = found.copy(amount = s.substring(pos, end))
                    pos = end
                }
                TemplateField.ACCOUNT -> {
                    var end = pos
                    while (end < s.length && isDigit(s[end])) end++
                    if (end - pos < 2 || end - pos > MAX_DIGITS + 5) return null
                    found = found.copy(account = s.substring(pos, end))
                    pos = end
                }
                TemplateField.NUMBER -> {
                    val end = scanNumber(s, pos, NUMBER_SEPARATORS)
                    if (end <= pos) return null
                    pos = end
                }
                TemplateField.TOKEN -> {
                    val end = scanToken(s, pos)
                    if (end <= pos) return null
                    pos = end
                }
                TemplateField.DATE_WORD -> {
                    var end = pos
                    while (end < s.length && s[end].isLetter()) end++
                    if (s.substring(pos, end).lowercase() !in DATE_WORDS) return null
                    pos = end
                }
                TemplateField.MERCHANT -> return scanMerchant(parts, i, pos, s, found)
            }
        }
        return found.takeIf { pos == s.length }
    }

    /**
     * A merchant runs to the literal after it. "Shop on Main on 12/10" has two places that
     * could be: the rest of the shape decides, and if it fits both the message is ambiguous,
     * so it reads as nothing. Only one merchant per template, so this branches once.
     */
    private fun scanMerchant(parts: List<TemplatePart>, index: Int, pos: Int, s: String, captured: Captures): Captures? {
        val next = parts.getOrNull(index + 1)
        val amountBefore = amountBeside(parts, index, -1)
        if (next == null) {
            return validMerchant(s.substring(pos), amountBefore, false)?.let { captured.copy(merchant = it) }
        }
        if (next.field != TemplateField.LITERAL || next.text.isEmpty()) return null
        val amountAfter = amountBeside(parts, index, 1)
        var match: Captures? = null
        var at = s.indexOf(next.text, pos)
        while (at >= 0 && at - pos <= MAX_MERCHANT) {
            val merchant = validMerchant(s.substring(pos, at), amountBefore, amountAfter)
            if (merchant != null) {
                val rest = scan(parts, index + 1, at, s, captured.copy(merchant = merchant))
                if (rest != null) {
                    if (match != null) return null
                    match = rest
                }
            }
            at = s.indexOf(next.text, at + 1)
        }
        return match
    }

    /** True when only a space separates the part at [index] from the amount on that side. */
    private fun amountBeside(parts: List<TemplatePart>, index: Int, side: Int): Boolean {
        val gap = parts.getOrNull(index + side) ?: return false
        return gap.field == TemplateField.LITERAL && gap.text.isBlank() &&
            parts.getOrNull(index + 2 * side)?.field == TemplateField.AMOUNT
    }

    /**
     * A merchant is free text, so it's the one place the message's meaning could hide. It can't
     * hold a price ("Bar: SAR 5.00 remaining" would shift the amount to the next figure) or say the
     * payment failed. Against the amount, its end word must be a name, not "USD" or "1" of "1 234.50".
     */
    private fun validMerchant(text: String, amountBefore: Boolean, amountAfter: Boolean): String? {
        val name = text.trim()
        if (name.length > MAX_MERCHANT || name.none { it.isLetter() }) return null
        if (numberRanges(name).any { currencyAt(name, it) != null }) return null
        if (GenericTransactionExtractor.saysItFailed(name) || NOT_A_NAME_AR.any { it in name }) return null
        val words = name.split(' ')
        // After the first word: "Bar CR." is a credit marker, "Dr Ahmed Clinic" is a name.
        if (words.drop(1).any { word -> word.trim { !it.isLetterOrDigit() }.uppercase() in STATUS_CODES }) return null
        if (amountBefore && !isNameWord(words.first())) return null
        if (amountAfter && !isNameWord(words.last())) return null
        return name
    }

    private fun isNameWord(word: String) = word.any { it.isLetter() } && word.none(::isDigit) && tag(word.trim('.', ',', ':')) == null

    /** Digits with single separators between them. Always ends on a digit, so a full stop after stays out. */
    private fun scanNumber(s: String, from: Int, separators: String): Int {
        var i = from
        while (i < s.length && isDigit(s[i])) i++
        if (i == from) return from
        while (i + 1 < s.length && s[i] in separators && isDigit(s[i + 1])) {
            i++
            while (i < s.length && isDigit(s[i])) i++
        }
        return i
    }

    private fun scanToken(s: String, from: Int): Int {
        var i = from
        var hasDigit = false
        while (i < s.length) {
            if (s[i].isLetterOrDigit()) {
                hasDigit = hasDigit || isDigit(s[i])
                i++
            } else if (s[i] in TOKEN_SEPARATORS && i + 1 < s.length && s[i + 1].isLetterOrDigit()) {
                i++
            } else break
        }
        if (!hasDigit || i - from > MAX_TOKEN || GenericTransactionExtractor.saysItFailed(s.substring(from, i))) return from
        return i
    }

    private fun validWhole(whole: String, grouping: Char): Boolean {
        if (whole.isEmpty()) return false
        val groups = whole.split(grouping)
        if (!groups.all { group -> group.isNotEmpty() && group.all(::isDigit) }) return false
        if (groups.size == 1) return true
        // "0,500" is half of something written with the wrong separator, not five hundred.
        if (groups.first().startsWith('0')) return false
        // "1,234,567" and India's "1,00,000". A last group that isn't 3 digits was never grouping.
        return groups.first().length <= 3 && groups.last().length == 3 && groups.drop(1).dropLast(1).all { it.length in 2..3 }
    }

    // ─── Building ─────────────────────────────────────────────────────────────

    /** Turns text nobody tapped into literals, and what changes between messages into read-past parts. */
    private fun maskGap(gap: String, out: MutableList<TemplatePart>) {
        val tokens = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < gap.length) {
            if (gap[i].isWhitespace()) { i++; continue }
            val start = i
            while (i < gap.length && !gap[i].isWhitespace()) i++
            tokens += start to i
        }
        var at = 0
        tokens.forEachIndexed { index, (start, end) ->
            if (start > at) out += TemplatePart(TemplateField.LITERAL, gap.substring(at, start))
            val before = tokens.getOrNull(index - 1)?.let { (a, b) -> gap.substring(a, b) }
            val after = tokens.getOrNull(index + 1)?.let { (a, b) -> gap.substring(a, b) }
            maskToken(gap.substring(start, end), before, after, out)
            at = end
        }
        if (at < gap.length) out += TemplatePart(TemplateField.LITERAL, gap.substring(at))
    }

    private fun maskToken(token: String, before: String?, after: String?, out: MutableList<TemplatePart>) {
        val core = token.trim { !it.isLetterOrDigit() }
        val lead = token.substring(0, token.indexOf(core).coerceAtLeast(0))
        val trail = token.substring(lead.length + core.length)

        if (token.none(::isDigit)) {
            val nearNumber = before?.any(::isDigit) == true || after?.any(::isDigit) == true
            if (nearNumber && core.lowercase() in DATE_WORDS) {
                if (lead.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, lead)
                out += TemplatePart(TemplateField.DATE_WORD)
                if (trail.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, trail)
            } else {
                out += TemplatePart(TemplateField.LITERAL, token)
            }
            return
        }

        val spans = numberRanges(token, NUMBER_SEPARATORS)
        val only = spans.singleOrNull()
        if (only != null) {
            val prefix = token.substring(0, only.first)
            val suffix = token.substring(only.last + 1)
            if (!(prefix.any { it.isLetter() } && suffix.any { it.isLetter() })) {
                // Currency and masks stay literal: "SAR500.00", "XX1234", "500ريال".
                if (prefix.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, prefix)
                out += TemplatePart(TemplateField.NUMBER)
                if (suffix.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, suffix)
                return
            }
        }
        // A reference or a written date: "AB12CD34", "12-Oct-26".
        if (lead.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, lead)
        out += TemplatePart(TemplateField.TOKEN)
        if (trail.isNotEmpty()) out += TemplatePart(TemplateField.LITERAL, trail)
    }

    private fun mergeLiterals(parts: List<TemplatePart>): List<TemplatePart> {
        val merged = ArrayList<TemplatePart>(parts.size)
        for (part in parts) {
            val last = merged.lastOrNull()
            if (part.field == TemplateField.LITERAL && last?.field == TemplateField.LITERAL) {
                merged[merged.lastIndex] = TemplatePart(TemplateField.LITERAL, last.text + part.text)
            } else if (part.field != TemplateField.LITERAL || part.text.isNotEmpty()) {
                merged += part
            }
        }
        return merged
    }

    // ─── Guessing ─────────────────────────────────────────────────────────────

    private fun numberRanges(s: String, separators: String = AMOUNT_SEPARATORS): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var i = 0
        while (i < s.length) {
            if (isDigit(s[i])) {
                val end = scanNumber(s, i, separators)
                ranges += i until end
                i = end
            } else i++
        }
        return ranges
    }

    private fun currencyAt(s: String, range: IntRange): String? {
        val left = s.substring((range.first - 12).coerceAtLeast(0), range.first).trimEnd()
            .takeLastWhile { !it.isWhitespace() }.trimEnd('.', ':')
        val right = s.substring((range.last + 1).coerceAtMost(s.length)).trimStart()
            .takeWhile { !it.isWhitespace() }
        val before = tag(left.takeLastWhile { !isDigit(it) })
        val after = tag(right.trimEnd('.', ',', ':', ';', ')', '،'))
        return when {
            before != null && after != null && before != after -> null
            else -> before ?: after
        }
    }

    private fun tag(text: String): String? {
        return ARABIC_CURRENCIES[text] ?: SYMBOL_CURRENCIES[text] ?: text.takeIf { it in ISO_CURRENCIES }
    }

    /** A balance or limit, by the word just before it with no other number between. */
    private fun isFigure(s: String, range: IntRange): Boolean {
        val before = s.substring((range.first - 30).coerceAtLeast(0), range.first)
        val sinceNumber = before.takeLastWhile { !isDigit(it) }.lowercase()
        if (FIGURE_WORDS.any { it in sinceNumber }) return true
        // "SAR 900.00 available" says what it is after the number.
        val after = s.substring((range.last + 1).coerceAtMost(s.length)).take(24).lowercase()
        return FIGURE_WORDS_AFTER.any { it in after }
    }

    /**
     * Who it was, by the words around a name. Worth reaching for: a merchant left unmarked
     * stays literal, and the template then only ever reads that one merchant.
     */
    private fun merchantWords(s: String): String? =
        (MERCHANT_WORDS.find(s) ?: MERCHANT_WORDS_AR.find(s))?.groupValues?.get(1)?.trim()?.takeIf { name ->
            name.length <= MAX_MERCHANT && name.none(::isDigit) && !GenericTransactionExtractor.namesAnAccount(name)
        }

    /**
     * One piece of a number written with spaces or apostrophes for thousands ("1 234.50", "1'234.50").
     * Read alone it would be 1 or 234.50, so it's neither guessed nor learned.
     */
    private fun isGroupFragment(s: String, range: IntRange): Boolean {
        val groupMark = { c: Char? -> c == ' ' || c == '\'' }
        val before = s.getOrNull(range.first - 1)
        val after = s.getOrNull(range.last + 1)
        val whole = s.substring(range.first, range.last + 1).takeWhile(::isDigit)
        val digitsBefore = if (range.first >= 2 && groupMark(before)) s.substring(0, range.first - 1).takeLastWhile(::isDigit) else ""
        val digitsAfter = if (groupMark(after)) s.substring((range.last + 2).coerceAtMost(s.length)).takeWhile(::isDigit) else ""
        return (whole.length == 3 && digitsBefore.length in 1..3) || (whole == s.substring(range.first, range.last + 1) && whole.length in 1..3 && digitsAfter.length == 3)
    }

    /**
     * The 50 of ".50": read alone it's 100 times the real amount, so it's neither guessed nor
     * learned. The separator can't join the number, as after a word it's the currency's: "Rs.50" is 50.
     */
    // ponytail: such a message stays untaught, read a leading separator if a real bank writes one
    private fun isBareFraction(s: String, range: IntRange): Boolean {
        if (s.getOrNull(range.first - 1)?.let { it in AMOUNT_SEPARATORS } != true) return false
        return s.getOrNull(range.first - 2)?.isLetterOrDigit() != true
    }

    private fun arabicDirection(s: String): TransactionType? {
        val out = ARABIC_OUT.mapNotNull { w -> s.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val inward = ARABIC_IN.mapNotNull { w -> s.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        return when {
            out == null && inward == null -> null
            inward == null -> TransactionType.EXPENSE
            out == null -> TransactionType.INCOME
            out < inward -> TransactionType.EXPENSE
            else -> TransactionType.INCOME
        }
    }

    // ─── Text ─────────────────────────────────────────────────────────────────

    /** [text] with one spelling for digits and spaces, and where each of its characters came from. */
    private class Normalized(val text: String, private val origin: IntArray) {
        fun toOriginal(range: IntRange): IntRange = origin[range.first]..origin[range.last]

        fun fromOriginal(range: IntRange): IntRange? {
            if (range.isEmpty()) return null
            val first = origin.indexOfFirst { it >= range.first }
            val last = origin.indexOfLast { it <= range.last }
            return if (first < 0 || last < first) null else first..last
        }
    }

    /** Digits in any script become ASCII, spaces collapse and direction marks go, so RTL text compares as itself. */
    private fun normalize(raw: String): Normalized {
        val out = StringBuilder(raw.length)
        val origin = IntArray(raw.length)
        var pendingSpace = -1
        for (i in raw.indices) {
            val c = raw[i]
            if (c in INVISIBLE) continue
            if (c.isWhitespace()) {
                if (out.isNotEmpty() && pendingSpace < 0) pendingSpace = i
                continue
            }
            if (pendingSpace >= 0) {
                origin[out.length] = pendingSpace
                out.append(' ')
                pendingSpace = -1
            }
            origin[out.length] = i
            out.append(
                when {
                    c.isDigit() -> '0' + c.digitToInt()
                    else -> c
                }
            )
        }
        return Normalized(out.toString(), origin.copyOf(out.length))
    }

    private fun isDigit(c: Char) = c in '0'..'9'

    private fun isCurrencyCode(code: String) = code.length == 3 && code.all { it in 'A'..'Z' }

    private fun isWordChar(c: Char) = c.isLetter() || c == '&' || c == '\''

    private const val ARABIC_DECIMAL = '\u066B'
    private const val ARABIC_THOUSANDS = '\u066C'
    private const val AMOUNT_SEPARATORS = ".,\u066B\u066C"
    private const val NUMBER_SEPARATORS = ".,:/-\u066B\u066C"
    private const val TOKEN_SEPARATORS = "-_/:.,"

    private val INVISIBLE = setOf(
        '\u061C', '\u200B', '\u200C', '\u200D', '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069', '\uFEFF'
    )

    private val DATE_WORDS = setOf(
        "am", "pm", "ص", "م",
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        "january", "february", "march", "april", "june", "july", "august", "september", "october", "november", "december",
        "mon", "tue", "wed", "thu", "fri", "sat", "sun",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "يناير", "فبراير", "مارس", "أبريل", "ابريل", "مايو", "يونيو", "يوليو", "أغسطس", "اغسطس", "سبتمبر", "أكتوبر", "اكتوبر", "نوفمبر", "ديسمبر",
        "كانون", "شباط", "آذار", "اذار", "نيسان", "أيار", "ايار", "حزيران", "تموز", "آب", "اب", "أيلول", "ايلول", "تشرين",
        "الأحد", "الاحد", "الإثنين", "الاثنين", "الثلاثاء", "الأربعاء", "الاربعاء", "الخميس", "الجمعة", "السبت"
    )

    // Only the unambiguous forms: "ريال" and "دينار" alone name several countries' money.
    private val ARABIC_CURRENCIES = mapOf(
        "ر.س" to "SAR", "ر.ق" to "QAR", "ر.ع" to "OMR", "د.إ" to "AED", "د.ك" to "KWD", "د.ب" to "BHD", "د.أ" to "JOD", "ج.م" to "EGP"
    )

    private val SYMBOL_CURRENCIES = mapOf("₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP", "Rs" to "INR", "RS" to "INR", "rs" to "INR")

    // Spelled out, not Currency.getInstance: Android's accepts ordinary words like "and"
    // where the JVM throws, which read "SAR 2.00 and" as two currencies disagreeing.
    private val ISO_CURRENCIES = (
        "AED AFN ALL AMD ANG AOA ARS AUD AWG AZN BAM BBD BDT BGN BHD BIF BMD BND BOB BRL BSD BTN BWP BYN BZD " +
            "CAD CDF CHF CLP CNY COP CRC CUP CVE CZK DJF DKK DOP DZD EGP ERN ETB EUR FJD FKP GBP GEL GHS GIP GMD GNF " +
            "GTQ GYD HKD HNL HTG HUF IDR ILS INR IQD IRR ISK JMD JOD JPY KES KGS KHR KMF KPW KRW KWD KYD KZT LAK LBP " +
            "LKR LRD LSL LYD MAD MDL MGA MKD MMK MNT MOP MRU MUR MVR MWK MXN MYR MZN NAD NGN NIO NOK NPR NZD OMR PAB " +
            "PEN PGK PHP PKR PLN PYG QAR RON RSD RUB RWF SAR SBD SCR SDG SEK SGD SHP SLE SOS SRD SSP STN SVC SYP SZL " +
            "THB TJS TMT TND TOP TRY TTD TWD TZS UAH UGX USD UYU UZS VES VND VUV WST XAF XCD XOF XPF YER ZAR ZMW ZWG"
        ).split(" ").toSet()

    private val MERCHANT_WORDS = Regex(
        """\b(?:at|to|from)\s+(\p{L}[^,;:\n]*?)\s+(?:card|ending|on|ref|using|via|with|a/c|acct|account|dated)\b""",
        RegexOption.IGNORE_CASE
    )
    private val MERCHANT_WORDS_AR = Regex("""(?:لدى|عند)\s+(\p{L}[^،,;:\n]*?)\s+(?:في|بتاريخ|الرصيد|رقم|من)(?:\s|$)""")

    private val FIGURE_WORDS = listOf("bal", "balance", "limit", "lmt", "available", "avl", "رصيد", "الرصيد", "المتاح", "متاح", "متبقي", "المتبقي")
    private val ARABIC_OUT = listOf("خصم", "شراء", "سحب", "دفع", "مشتريات", "صادر", "صادرة")
    private val ARABIC_IN = listOf("إيداع", "ايداع", "استلام", "وارد", "واردة", "راتب", "إضافة")
    private val FIGURE_WORDS_AFTER = listOf("available", "limit", "balance", "متاح", "المتاح", "رصيد", "الرصيد")
    private val STATUS_CODES = setOf("CR", "DR")
    private val NOT_A_NAME_AR = listOf("مرفوض", "مرفوضة", "لم تتم", "فشل", "ملغاة", "ملغية")
    private val NOT_MONEY_AR = listOf("رمز التحقق", "رمز التفعيل", "رمز الدخول", "كلمة المرور", "كلمة السر", "مرفوض", "مرفوضة", "لم تتم", "فشل")
}
