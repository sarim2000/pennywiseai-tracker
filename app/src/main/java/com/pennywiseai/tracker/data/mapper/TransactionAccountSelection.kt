package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.tracker.data.database.entity.TransactionEntity

/**
 * Keep a selected transfer leg and its owning transaction account together.
 * A picked account ([bank] set) keeps its suffix even when blank: a Cash account
 * added without a number is keyed by ("Cash", ""), and dropping the "" cleared
 * the leg on save (#913). Only typed text treats blank as "no account".
 */
internal fun TransactionEntity.withTransferAccount(account: String?, bank: String?, incoming: Boolean): TransactionEntity {
    val suffix = account?.takeIf { it.isNotEmpty() || bank != null }
    val selectedBank = bank?.takeIf { suffix != null }
    val oldSuffix = if (incoming) toAccount else fromAccount
    val oldBank = if (incoming) toBankName else fromBankName
    val otherSuffix = if (incoming) fromAccount else toAccount
    val otherBank = if (incoming) fromBankName else toBankName
    val ownsOtherLeg = accountNumber == otherSuffix && (otherBank == null || bankName == otherBank)
    val ownsLeg = if (oldSuffix != null) accountNumber == oldSuffix && (oldBank == null || bankName == oldBank)
        else accountNumber != null && !ownsOtherLeg
    val changed = if (incoming) copy(toAccount = suffix, toBankName = selectedBank)
        else copy(fromAccount = suffix, fromBankName = selectedBank)
    // Keep the primary identity while clearing, so reselection still owns this leg.
    return if (ownsLeg && suffix != null) changed.copy(accountNumber = suffix, bankName = selectedBank ?: bankName) else changed
}
