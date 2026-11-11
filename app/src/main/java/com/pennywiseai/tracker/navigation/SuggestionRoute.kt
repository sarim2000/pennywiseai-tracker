package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.domain.model.SuggestionDraft

/** The pre-filled Add form a suggestion opens (#994). */
fun SuggestionDraft.toRoute() = AddTransaction(
    amount = amount,
    merchant = merchant,
    bankName = bankName,
    accountLast4 = accountLast4,
    currency = currency,
    type = type,
    dateTime = dateTime,
    unrecognizedSmsId = unrecognizedSmsId,
)
