package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.parser.core.bank.BankParserFactory
import java.math.RoundingMode

// Compiled into both androidMain and iosMain (see shared/build.gradle.kts):
// java.math is the JDK on Android and parser-core's stand-in on iOS.
internal actual fun parseWithBankParsers(sender: String, body: String, receivedAt: Long): BankParseAttempt {
    val parsers = BankParserFactory.getParsers(sender)
    if (parsers.isEmpty()) return BankParseAttempt.NoParser
    val (parser, parsed) = parsers.firstNotNullOfOrNull { p -> p.parse(body, sender, receivedAt)?.let { p to it } }
        ?: return BankParseAttempt.NoTransaction(parsers.first().getBankName())
    // A Shortcut may pass "AX-HDFCBK-S", or nothing (the sender is then guessed from the body);
    // both must dedupe as one SMS, so the hash is taken as if the bank name were the sender.
    // A parser's own hash is kept (M-Pesa Tanzania's spans its English and Swahili copies)
    // only when it ignores the sender and the time: JK Bank's can mix in both, and a Shortcut
    // stamps every run with the current time, so it would never dedupe.
    fun ownHash(at: Long) = parser.parse(body, parsed.bankName, at)?.transactionHash?.takeIf { it.isNotBlank() }
    val stableHash = parsed.transactionHash?.takeIf { it.isNotBlank() && it == ownHash(receivedAt) }
        ?.takeIf { it == ownHash(receivedAt + 1) }
        ?: parsed.copy(sender = parsed.bankName).generateTransactionId()
    return BankParseAttempt.Parsed(
        ParsedBankSms(
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
    )
}
