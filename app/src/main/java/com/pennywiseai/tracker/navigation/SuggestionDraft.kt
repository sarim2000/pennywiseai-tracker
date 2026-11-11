package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.domain.model.Suggestion

/** The Add form, pre-filled; saving it hides the suggestion's SMS and teaches its sender. */
fun Suggestion.draft(): AddTransaction {
    val account = LearnedSendersStore.accountToUse(learnedAccount, transaction.accountLast4, transaction.currency)
    return AddTransaction(
        amount = transaction.amount.toPlainString(),
        merchant = transaction.merchant,
        bankName = account?.bankName,
        accountLast4 = account?.accountLast4 ?: transaction.accountLast4,
        currency = transaction.currency,
        type = transaction.type.name,
        dateTime = sms.receivedAt.toString(),
        unrecognizedSmsId = sms.id,
    )
}
