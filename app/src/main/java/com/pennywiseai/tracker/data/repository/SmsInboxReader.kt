package com.pennywiseai.tracker.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.bank.BankParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds recent messages that look like money but became no transaction, so one can be
 * taught (#279). That covers what never reaches Unrecognized SMS: a supported bank's
 * format its parser passes on. Read only when the user asks, and never leaves the phone.
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository
) {
    data class Message(val sender: String, val body: String, val timestamp: Long)

    fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    // ponytail: a message the user deleted from Unrecognized SMS stays out, as does an old one
    // past MAX_SCANNED. Its hidden row can't tell "deleted" from "booked through the Add form".
    suspend fun recentUntracked(): List<Message> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val found = LinkedHashMap<Pair<String, String>, Message>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            var scanned = 0
            while (cursor.moveToNext() && scanned++ < MAX_SCANNED && found.size < MAX_OFFERED) {
                val sender = cursor.getString(0) ?: continue
                val body = cursor.getString(1) ?: continue
                val timestamp = cursor.getLong(2)
                if (sender to body in found || !worthOffering(sender, body)) continue
                if (BankParserFactory.getParsers(sender).any { it.parse(body, sender, timestamp) != null }) continue
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                val window = UnrecognizedSmsRepository.REDELIVERY_WINDOW
                if (transactionRepository.getAnyBySmsAround(body, sender, at, window) != null) continue
                // Listed already, or this very SMS was dealt with. Hidden but from another time is another payment.
                val row = unrecognizedSmsRepository.find(sender, body)
                if (row != null && (!row.isDeleted || Duration.between(row.receivedAt, at).abs() < window)) continue
                found[sender to body] = Message(sender, body, timestamp)
            }
        }
        found.values.toList()
    }

    companion object {
        private const val MAX_SCANNED = 1000
        private const val MAX_OFFERED = 30

        /** India's promotional and government senders are never a bank's transaction alert. */
        internal fun worthOffering(sender: String, body: String): Boolean {
            val upper = sender.uppercase()
            return !upper.endsWith("-P") && !upper.endsWith("-G") && MessageTemplates.looksLikeMoney(sender, body)
        }
    }
}
