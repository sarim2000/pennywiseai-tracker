package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.tracker.data.database.entity.TransactionEntity

/** Keep a selected transfer leg and its owning transaction account together. */
internal fun TransactionEntity.withTransferAccount(account: String?, bank: String?, incoming: Boolean): TransactionEntity {
    val suffix = account?.takeIf { it.isNotEmpty() }
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
