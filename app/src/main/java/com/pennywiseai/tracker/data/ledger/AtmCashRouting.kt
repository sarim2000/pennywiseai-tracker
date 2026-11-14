package com.pennywiseai.tracker.data.ledger

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.getAccountType

private val ATM = Regex("""\batm\b""")
private val FEE = Regex("""\b(fee|fees|charges?|chg|gst)\b""")

/** Parsers name a cash withdrawal "ATM Withdrawal", "ATM - <place>", and so on. */
fun isAtmMerchant(name: String): Boolean = ATM.containsMatchIn(name.lowercase())

/** The user's Cash wallets, the targets [routeAtmToCashWallet] picks from. */
fun List<AccountBalanceEntity>.cashWallets(): List<AccountBalanceEntity> =
    filter { it.getAccountType() == AccountType.CASH }

/**
 * An ATM withdrawal booked as a transfer from the bank account to the first Cash
 * wallet in the same currency (#1062), so it isn't counted as spending. Anything
 * else, or no such wallet, comes back unchanged.
 */
fun TransactionEntity.routeAtmToCashWallet(cashWallets: List<AccountBalanceEntity>): TransactionEntity {
    if (transactionType != TransactionType.EXPENSE || !isAtmMerchant(merchantName) ||
        FEE.containsMatchIn(merchantName.lowercase())
    ) return this
    val account = accountNumber ?: return this
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
