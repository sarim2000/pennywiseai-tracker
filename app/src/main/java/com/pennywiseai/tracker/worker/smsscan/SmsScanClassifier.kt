package com.pennywiseai.tracker.worker.smsscan

import android.util.Log
import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.bank.BaseIndianBankParser
import com.pennywiseai.parser.core.bank.FederalBankParser
import com.pennywiseai.parser.core.bank.HDFCBankParser
import com.pennywiseai.parser.core.bank.IndianBankParser
import com.pennywiseai.parser.core.bank.IndusIndBankParser
import com.pennywiseai.parser.core.bank.PNBBankParser
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import java.util.Currency

private const val TAG = "OptimizedSmsReaderWorker"

/**
 * Sealed result from the parse stage — each outcome is a distinct type,
 * impossible states are unrepresentable.
 */
internal sealed class ParseResult {
    abstract val sms: ScannedSms

    /** Promo/gov/unknown sender — discard silently. */
    data class Discard(override val sms: ScannedSms) : ParseResult()

    /** Unknown -T/-S sender — save to unrecognized table for review. */
    data class StoreUnrecognized(override val sms: ScannedSms) : ParseResult()

    /**
     * Subscription mandate or balance update.
     * [onSave] captures the exact typed repository call at parse time;
     * invoked in the sequential save coroutine.
     */
    class SpecialNotification(
        override val sms: ScannedSms,
        val onSave: suspend () -> Unit
    ) : ParseResult()

    /** Normal debit/credit — ready to be written to the transactions table. */
    data class Transaction(
        override val sms: ScannedSms,
        val parsed: ParsedTransaction
    ) : ParseResult()
}

/**
 * The scan's parse stage. Runs on several parser coroutines at once, so it must stay
 * free of DB access and hold no mutable state beyond the concurrent parser cache and
 * the synchronized [taughtTemplates] lazy.
 */
internal class SmsScanClassifier(
    private val recentSinceMillis: Long,
    taughtTemplates: Lazy<Map<String, List<MessageTemplate>>>,
    private val subscriptionRepository: SubscriptionRepository,
    private val saveBalanceNotification: suspend (BaseIndianBankParser.BaseBalanceUpdateInfo, String, ScannedSms) -> Unit
) {
    private val taughtTemplates by taughtTemplates

    companion object {
        private val ISO_CURRENCY_CODES: Set<String> by lazy {
            Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode }
        }
        private val AMOUNT_NEAR_SYMBOL = Regex("""[€$£₹]\s?\.?\d|\d[\d.,]*\s?[€$£₹]""")
        private val AMOUNT_NEAR_CODE = Regex("""\b([A-Z]{3})\s?\.?\d|\d[\d.,]*\s?([A-Z]{3})\b""")

        /**
         * Whether an SMS no parser claims belongs in Unrecognized SMS (the user's way to report a bank).
         * Indian DLT senders (-T/-S) always do. Senders outside India have no DLT suffix, so for those
         * keep alphanumeric senders whose body shows an amount in a currency ("12,50 EUR", "€12.50", "EUR .93");
         * phone-number senders (people) and amount-less SMS (OTPs, promos) stay out.
         */
        internal fun isUnrecognizedCandidate(sender: String, body: String): Boolean {
            val upper = sender.uppercase()
            if (upper.endsWith("-T") || upper.endsWith("-S")) return true
            if (upper.none { it.isLetter() }) return false
            return AMOUNT_NEAR_SYMBOL.containsMatchIn(body) ||
                AMOUNT_NEAR_CODE.findAll(body).any { m ->
                    m.groupValues[1].ifEmpty { m.groupValues[2] } in ISO_CURRENCY_CODES
                }
        }

        /**
         * A taught sender's message that nothing else keeps (#279): its taught shape, or recent
         * money wording that may be a new format, as the live receiver keeps it. An older one is
         * history rather than a change, and the scan's own cleanup would drop it anyway.
         */
        internal fun taughtSenderKeeps(
            templates: List<MessageTemplate>,
            sender: String,
            body: String,
            timestamp: Long,
            recentSince: Long
        ): Boolean = MessageTemplates.matchAny(templates, body, sender, timestamp) != null ||
            (timestamp > recentSince && MessageTemplates.looksLikeMoney(sender, body))
    }

    /** O(1) sender-to-parser lookup. Factory is only called once per unique sender string. */
    private class ParserHolder(val parsers: List<BankParser>)
    private val parserCache = java.util.concurrent.ConcurrentHashMap<String, ParserHolder>(256)
    private fun cachedParsers(sender: String): List<BankParser> =
        parserCache.computeIfAbsent(sender) { ParserHolder(BankParserFactory.getParsers(sender)) }.parsers

    /** Plain fun — no suspend overhead on the hot path. */
    fun classify(sms: ScannedSms): ParseResult {
        val upper = sms.sender.uppercase()

        if (upper.endsWith("-P") || upper.endsWith("-G"))
            return ParseResult.Discard(sms)

        val parsers = cachedParsers(sms.sender)
        val parser = parsers.firstOrNull()
            ?: return if (isUnrecognizedCandidate(sms.sender, sms.body) || keptForTaughtSender(sms))
                ParseResult.StoreUnrecognized(sms)
            else
                ParseResult.Discard(sms)

        val isRecent = sms.timestamp > recentSinceMillis

        // Subscription/balance special-cases are bank-type specific; the primary
        // parser is enough (shared-sender M-Pesa parsers aren't special-cased).
        checkSubscriptionOrBalance(parser, sms, isRecent)?.let { return it }

        // Shared senders (M-Pesa KE/TZ/MZ): first candidate whose content parses.
        val parsed = parsers.firstNotNullOfOrNull { it.parse(sms.body, sms.sender, sms.timestamp) }
            ?: return if (keptForTaughtSender(sms)) ParseResult.StoreUnrecognized(sms) else ParseResult.Discard(sms)

        return ParseResult.Transaction(sms, parsed)
    }

    private fun keptForTaughtSender(sms: ScannedSms): Boolean {
        val templates = taughtTemplates[LearnedSendersStore.normalize(sms.sender)] ?: return false
        return taughtSenderKeeps(templates, sms.sender, sms.body, sms.timestamp, recentSinceMillis)
    }

    /** The user taught this shape (#279), so it's offered for confirming whatever else would have dropped it. */
    fun matchesTaughtTemplate(sms: ScannedSms): Boolean {
        val templates = taughtTemplates[LearnedSendersStore.normalize(sms.sender)] ?: return false
        return MessageTemplates.matchAny(templates, sms.body, sms.sender, sms.timestamp) != null
    }

    // ─── Subscription / balance detection (pure parse, zero DB access) ────────

    /**
     * Returns a SpecialNotification whose [onSave] lambda will be invoked in the sequential
     * save coroutine. Everything is captured by value at parse time so types are preserved.
     * Returns null → normal transaction parse should proceed.
     */
    private fun checkSubscriptionOrBalance(
        parser: BankParser,
        sms: ScannedSms,
        isRecent: Boolean
    ): ParseResult? = when (parser) {

        is SBIBankParser -> {
            if (!parser.isUPIMandateNotification(sms.body) || !isRecent) null
            else parser.parseUPIMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromSBIMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is PNBBankParser -> {
            if (!parser.isUPIMandateNotification(sms.body) || !isRecent) null
            else parser.parseUPIMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is FederalBankParser -> when {
            parser.isMandateCreationNotification(sms.body) ->
                parser.parseEMandateSubscription(sms.body)?.let { info ->
                    ParseResult.SpecialNotification(sms) {
                        subscriptionRepository.createOrUpdateFromFederalBankMandate(info, parser.getBankName(), sms.body)
                    }
                }
            else ->
                parser.parseFutureDebit(sms.body)?.let { info ->
                    // Process if the SMS is recent, OR if the payment date is still in the future
                    // (a mandate from 45 days ago with next-debit next week must still create the subscription).
                    val paymentStillUpcoming = info.nextDeductionDate?.let { dateStr ->
                        try {
                            java.time.LocalDate.parse(
                                dateStr,
                                java.time.format.DateTimeFormatter.ofPattern(info.dateFormat)
                            ).isAfter(java.time.LocalDate.now())
                        } catch (_: Exception) { false }
                    } ?: false

                    if (isRecent || paymentStillUpcoming) {
                        ParseResult.SpecialNotification(sms) {
                            subscriptionRepository.createOrUpdateFromFederalBankMandate(info, parser.getBankName(), sms.body)
                        }
                    } else null
                }
        }

        is HDFCBankParser -> {
            val actions = mutableListOf<suspend () -> Unit>()
            if (parser.isBalanceUpdateNotification(sms.body)) {
                parser.parseBalanceUpdate(sms.body)?.let { info ->
                    actions += { saveBalanceNotification(info, parser.getCurrency(), sms) }
                }
                // If unparseable as balance update, fall through to check eMandate/futureDebit below
            }
            if (parser.isEMandateNotification(sms.body) && isRecent)
                parser.parseEMandateSubscription(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (parser.isFutureDebitNotification(sms.body) && isRecent)
                parser.parseFutureDebit(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (actions.isNotEmpty()) ParseResult.SpecialNotification(sms) {
                actions.forEach { action ->
                    try { action() } catch (e: Exception) {
                        Log.e(TAG, "HDFC action failed: ${e.message}", e)
                    }
                }
            } else null
        }

        is IndianBankParser -> {
            if (!parser.isMandateNotification(sms.body) || !isRecent) null
            else parser.parseMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromIndianBankMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is IndusIndBankParser -> {
            if (!parser.isBalanceUpdateNotification(sms.body)) null
            else parser.parseBalanceUpdate(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    saveBalanceNotification(info, parser.getCurrency(), sms)
                }
            }
        }

        else -> null
    }
}
