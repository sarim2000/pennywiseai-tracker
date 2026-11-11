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
