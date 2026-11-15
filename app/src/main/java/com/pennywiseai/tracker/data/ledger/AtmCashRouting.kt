package com.pennywiseai.tracker.data.ledger

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.getAccountType

private val ATM = Regex("""\batm\b""")
private val FEE = Regex("""\b(fee|fees|charges?|chg|gst)\b""")

// "ATM Transaction" is the PNB / IDFC catch-all for any ATM debit, fees included
// (PNB's other-bank ATM charge), so that label moves only when the SMS says it's a
// withdrawal. سحب = withdrawal.
private const val GENERIC_ATM = "atm transaction"
private val WITHDRAWAL = Regex("""\bwithdr|سحب""")

/** Parsers name a cash withdrawal "ATM Withdrawal", "ATM - <place>", and so on. */
fun isAtmMerchant(name: String): Boolean = ATM.containsMatchIn(name.lowercase())

/** The user's Cash wallets, the targets [routeAtmToCashWallet] picks from. */
fun List<AccountBalanceEntity>.cashWallets(): List<AccountBalanceEntity> =
    filter { it.getAccountType() == AccountType.CASH }

/**
 * An ATM withdrawal booked as a transfer from the bank account to the first Cash
 * wallet in the same currency (#1062), so it isn't counted as spending. Anything
 * else, or no such wallet, comes back unchanged. [sourceAccount] is the bank
 * account the money left: for a debit card, its linked account, not the card.
 */
fun TransactionEntity.routeAtmToCashWallet(
    cashWallets: List<AccountBalanceEntity>,
    sourceAccount: String? = accountNumber
): TransactionEntity {
    val name = merchantName.lowercase()
    if (transactionType != TransactionType.EXPENSE || !isAtmMerchant(name) || FEE.containsMatchIn(name) ||
        (name == GENERIC_ATM && !WITHDRAWAL.containsMatchIn(smsBody.orEmpty().lowercase()))
    ) return this
    val account = sourceAccount ?: return this
    val wallet = cashWallets.firstOrNull { it.currency == currency } ?: return this
    return copy(
        transactionType = TransactionType.TRANSFER,
        fromAccount = account,
        fromBankName = bankName,
        toAccount = wallet.accountLast4,
        toBankName = wallet.bankName
    )
}

/** (bank, last4) a transfer row pays into, when it names both; else null. */
fun TransactionEntity.walletLeg(): Pair<String, String>? {
    if (transactionType != TransactionType.TRANSFER) return null
    return (toBankName ?: return null) to (toAccount ?: return null)
}
