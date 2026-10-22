package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import java.math.RoundingMode

// Compiled into both androidMain and iosMain (see shared/build.gradle.kts):
// java.math is the JDK on Android and parser-core's stand-in on iOS.

private val BODY_WORD = Regex("[A-Za-z][A-Za-z0-9]{2,}")

internal actual fun parseBankSms(sender: String?, body: String, receivedAt: Long): BankParseAttempt {
    val given = sender?.trim().orEmpty()
    if (given.isNotEmpty()) {
        val parsers = BankParserFactory.getParsers(given)
        if (parsers.isNotEmpty()) return attempt(parsers.map { it to given }, body, receivedAt)
    }
    return attempt(guessSenders(body), body, receivedAt)
}

/**
 * Shortcuts' Message trigger hands over the text, not always the sender, so the
 * bank is guessed from the body. A bank named in full ("… -Federal Bank") wins,
 * the one named last first; then single words scanned from the end, where
 * Indian banks sign off ("… -SBI"). Words near the start are usually the payee
 * ("to INDIAN OIL CORP") and would pick the wrong bank.
 */
private fun guessSenders(body: String): List<Pair<BankParser, String>> {
    val named = BankParserFactory.getAllParsers()
        .mapNotNull { parser ->
            val name = parser.getBankName()
            Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).findAll(body).lastOrNull()
                ?.let { Triple(parser, name, it.range.first) }
        }
        .sortedWith(compareByDescending<Triple<BankParser, String, Int>> { it.third }.thenByDescending { it.second.length })
        .map { (parser, name) -> parser to name }
    val words = BODY_WORD.findAll(body).map { it.value.uppercase() }.toList().asReversed().distinct()
        .flatMap { word -> BankParserFactory.getParsers(word).map { it to word } }
    return named + words
}

/** The first parser that reads the body wins; one that throws is skipped, never aborting the rest. */
private fun attempt(candidates: List<Pair<BankParser, String>>, body: String, receivedAt: Long): BankParseAttempt {
    if (candidates.isEmpty()) return BankParseAttempt.NoParser
    for ((parser, sender) in candidates) {
        val parsed = runCatching { parser.parse(body, sender, receivedAt) }.getOrNull() ?: continue
        val sms = runCatching { toParsedBankSms(parser, parsed, body, receivedAt) }.getOrNull() ?: continue
        return BankParseAttempt.Parsed(sms)
    }
    return BankParseAttempt.NoTransaction(candidates.first().first.getBankName())
}

private fun toParsedBankSms(
    parser: BankParser,
    parsed: com.pennywiseai.parser.core.ParsedTransaction,
    body: String,
    receivedAt: Long
): ParsedBankSms {
    // iOS's BigDecimal stops at 16 integer digits; hold the JVM to the same line so both
    // platforms treat such a number as "no amount" (and toMinor can't overflow).
    require(parsed.amount.toPlainString().substringBefore('.').trimStart('-').length <= 16) { "Amount too large" }
    // A Shortcut may pass "AX-HDFCBK-S", or nothing (the sender is then guessed from the body);
    // both must dedupe as one SMS, so the hash is taken as if the bank name were the sender.
    // A parser's own hash is kept (M-Pesa Tanzania's spans its English and Swahili copies)
    // only when it ignores the sender and the time: JK Bank's can mix in both, and a Shortcut
    // stamps every run with the current time, so it would never dedupe.
    fun ownHash(at: Long) = parser.parse(body, parsed.bankName, at)?.transactionHash?.takeIf { it.isNotBlank() }
    val stableHash = parsed.transactionHash?.takeIf { it.isNotBlank() && it == ownHash(receivedAt) }
        ?.takeIf { it == ownHash(receivedAt + 1) }
        ?: parsed.copy(sender = parsed.bankName).generateTransactionId()
    return ParsedBankSms(
        amount = parsed.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        type = parsed.type.name,
        merchant = parsed.merchant,
        reference = parsed.reference,
        accountLast4 = parsed.accountLast4,
        balance = parsed.balance?.setScale(2, RoundingMode.HALF_UP)?.toPlainString(),
        bankName = parsed.bankName,
        transactionHash = stableHash,
        isFromCard = parsed.isFromCard,
        currency = parsed.currency,
        accountCurrency = parsed.accountCurrency,
        isMobileWallet = parsed.isMobileWallet
    )
}
