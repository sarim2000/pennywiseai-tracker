package com.pennywiseai.tracker.domain.model

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore

/** A suggested transaction (#994), with the account last booked from its sender, if any. */
data class Suggestion(
    val sms: UnrecognizedSmsEntity,
    val transaction: ParsedTransaction,
    val learnedAccount: LearnedSendersStore.Account?,
    /** Read by a template the user taught: Add books it directly instead of opening the form (#279). */
    val taught: Boolean = false,
)

/** The Add form's fields for a suggestion; saving it hides the SMS and teaches its sender. */
data class SuggestionDraft(
    val amount: String,
    val merchant: String?,
    val bankName: String?,
    val accountLast4: String?,
    val currency: String,
    val type: String,
    val dateTime: String,
    val unrecognizedSmsId: Long,
)

fun Suggestion.draft(): SuggestionDraft {
    val account = LearnedSendersStore.accountToUse(learnedAccount, transaction.accountLast4, transaction.currency)
    return SuggestionDraft(
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
