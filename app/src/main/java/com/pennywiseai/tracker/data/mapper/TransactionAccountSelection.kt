package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.tracker.data.database.entity.TransactionEntity

/** Keep a selected transfer leg and its owning transaction account together. */
internal fun TransactionEntity.withTransferAccount(account: String?, bank: String?, incoming: Boolean): TransactionEntity {
    val suffix = account?.takeIf { it.isNotEmpty() }
    val selectedBank = bank?.takeIf { suffix != null }
    val oldSuffix = if (incoming) toAccount else fromAccount
    val oldBank = if (incoming) toBankName else fromBankName
    val ownsLeg = oldSuffix != null && accountNumber == oldSuffix && (oldBank == null || bankName == oldBank)
    val changed = if (incoming) copy(toAccount = suffix, toBankName = selectedBank)
        else copy(fromAccount = suffix, fromBankName = selectedBank)
    return if (ownsLeg) changed.copy(accountNumber = suffix, bankName = selectedBank ?: bankName) else changed
}
