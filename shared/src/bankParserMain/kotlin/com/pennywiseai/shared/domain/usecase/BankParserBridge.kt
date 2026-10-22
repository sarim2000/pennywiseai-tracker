package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.parser.core.bank.BankParserFactory
import java.math.RoundingMode

// Compiled into both androidMain and iosMain (see shared/build.gradle.kts):
// java.math is the JDK on Android and parser-core's stand-in on iOS.
internal actual fun parseWithBankParsers(sender: String, body: String, receivedAt: Long): BankParseAttempt {
    val parsers = BankParserFactory.getParsers(sender)
    if (parsers.isEmpty()) return BankParseAttempt.NoParser
    val parsed = parsers.firstNotNullOfOrNull { it.parse(body, sender, receivedAt) }
        ?: return BankParseAttempt.NoTransaction(parsers.first().getBankName())
    return BankParseAttempt.Parsed(
        ParsedBankSms(
            amount = parsed.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            type = parsed.type.name,
            merchant = parsed.merchant,
            reference = parsed.reference,
            accountLast4 = parsed.accountLast4,
            balance = parsed.balance?.setScale(2, RoundingMode.HALF_UP)?.toPlainString(),
            bankName = parsed.bankName,
            // Android's hash formula, keyed on the bank instead of the raw sender: a Shortcut may
            // pass "AX-HDFCBK-S", or nothing (then the sender is guessed from the body), and both
            // must dedupe as the same SMS.
            transactionHash = parsed.transactionHash?.takeIf { it.isNotBlank() }
                ?: parsed.copy(sender = parsed.bankName).generateTransactionId(),
            isFromCard = parsed.isFromCard,
            currency = parsed.currency,
            accountCurrency = parsed.accountCurrency,
            isMobileWallet = parsed.isMobileWallet
        )
    )
}
