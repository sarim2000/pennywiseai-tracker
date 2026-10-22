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
    // A parser's own hash is kept (M-Pesa Tanzania's spans its English and Swahili copies),
    // re-derived the same way since some mix in the sender (JK Bank).
    val stableHash = parsed.transactionHash?.takeIf { it.isNotBlank() }
        ?.let { parser.parse(body, parsed.bankName, receivedAt)?.transactionHash?.takeIf { h -> h.isNotBlank() } }
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
