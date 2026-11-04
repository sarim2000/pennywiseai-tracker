package com.pennywiseai.tracker.data.manager

import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a message with the templates the user taught, and books it when they confirm (#279).
 *
 * Booking goes through [SmsTransactionProcessor.saveParsedTransaction], the same path a bank
 * parser takes: the row keeps the SMS as its identity, so a rescan or a parser added later
 * can't book it twice, and ignored accounts, blocking rules and duplicates all still apply.
 */
@Singleton
class TaughtMessageBooker @Inject constructor(
    private val processor: SmsTransactionProcessor,
    private val templates: TaughtTemplatesStore,
    private val learnedSenders: LearnedSendersStore,
    private val unrecognizedSms: UnrecognizedSmsRepository,
    private val cards: CardRepository
) {
    enum class Outcome {
        /** A new transaction. */
        SAVED,

        /** Nothing to do: already booked, deleted before, or ignored or blocked by the user's own settings. */
        SETTLED,

        /** It couldn't be saved. The message stays where it is so nothing is lost. */
        FAILED
    }

    /** What a taught template reads in this message. A bank parser that reads it always wins. */
    fun read(sender: String, body: String, timestamp: Long): ParsedTransaction? {
        val forSender = templates.forSender(sender)
        if (forSender.isEmpty() || builtInReads(sender, body, timestamp)) return null
        val parsed = MessageTemplates.matchAny(forSender, body, sender, timestamp) ?: return null
        val account = LearnedSendersStore.accountToUse(learnedSenders.accountFor(sender), parsed.accountLast4, parsed.currency)
            ?: return parsed
        return parsed.copy(bankName = account.bankName, accountLast4 = account.accountLast4)
    }

    /** A sender the user taught sent money wording no template fits: it may have changed its format. */
    fun formatChanged(sender: String, body: String, timestamp: Long): Boolean =
        templates.forSender(sender).isNotEmpty() &&
            read(sender, body, timestamp) == null &&
            !builtInReads(sender, body, timestamp) &&
            MessageTemplates.looksLikeMoney(sender, body)

    /**
     * Books [parsed]. Whatever the outcome short of failure, its message stops being offered.
     *
     * Identical text at a later time (the same coffee, tomorrow) is a new payment, so the save
     * looks for this SMS by time as well. The inbox and the receiver stamp one SMS seconds apart,
     * so "the same time" is a window, checked under the save lock.
     */
    suspend fun book(parsed: ParsedTransaction): Outcome {
        // Digits of a card the app knows move its linked account, and ignoring that account covers them.
        val isCard = parsed.accountLast4?.let { cards.getCard(parsed.bankName, it) } != null
        val result = processor.saveParsedTransaction(
            parsed.copy(
                transactionHash = "$HASH_PREFIX${parsed.generateTransactionId()}_${parsed.timestamp}",
                isFromCard = isCard
            ),
            parsed.smsBody,
            sameEventWindow = UnrecognizedSmsRepository.REDELIVERY_WINDOW
        )
        if (result.failed) return Outcome.FAILED
        unrecognizedSms.settle(
            parsed.sender,
            parsed.smsBody,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(parsed.timestamp), ZoneId.systemDefault())
        )
        return if (result.success) Outcome.SAVED else Outcome.SETTLED
    }

    /** Books a waiting message, read afresh so it reflects the templates as they are now. */
    suspend fun bookWaiting(unrecognizedSmsId: Long): Outcome {
        val sms = unrecognizedSms.getById(unrecognizedSmsId) ?: return Outcome.SETTLED
        if (sms.isDeleted) return Outcome.SETTLED
        val timestamp = sms.receivedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val parsed = read(sms.sender, sms.smsBody, timestamp) ?: return Outcome.FAILED
        return book(parsed)
    }

    private fun builtInReads(sender: String, body: String, timestamp: Long): Boolean =
        BankParserFactory.getParsers(sender).any { it.parse(body, sender, timestamp) != null }

    private companion object {
        /**
         * Marks a row "Rebuild from scratch" must keep. A rebuild deletes what the inbox can
         * bring back, and a taught message only comes back if the user confirms it again.
         * Spelled out in TransactionDao.deleteRebuildableSmsTransactions.
         */
        const val HASH_PREFIX = "taught_"
    }
}
