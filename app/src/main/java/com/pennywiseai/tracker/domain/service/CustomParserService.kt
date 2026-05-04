package com.pennywiseai.tracker.domain.service

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.repository.CustomParserRuleRepository
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs user-defined parsing rules as a fallback when no built-in BankParser
 * claims an SMS sender. Each rule stores regex patterns generated from the
 * token-tagging UI; this service loads active rules and tries them in priority
 * order, returning the first successful match.
 */
@Singleton
class CustomParserService @Inject constructor(
    private val repository: CustomParserRuleRepository,
    private val unrecognizedSmsDao: UnrecognizedSmsDao
) {
    companion object {
        private const val TAG = "CustomParserService"
    }

    suspend fun tryParse(sender: String, smsBody: String, timestamp: Long): ParsedTransaction? {
        val rules = repository.getActiveRules()
        Log.d(TAG, "tryParse sender='$sender' activeRules=${rules.size}")
        if (rules.isEmpty()) return null
        for (rule in rules) {
            val parsed = runCatching { applyRule(rule, sender, smsBody, timestamp) }
                .onFailure { Log.w(TAG, "Rule '${rule.name}' (id=${rule.id}) threw while parsing: ${it.message}") }
                .getOrNull()
            if (parsed != null) {
                Log.d(TAG, "Rule '${rule.name}' (id=${rule.id}) matched: amount=${parsed.amount} type=${parsed.type} merchant=${parsed.merchant}")
                return parsed
            }
        }
        Log.d(TAG, "No custom rule matched sender='$sender'")
        return null
    }

    fun applyRule(
        rule: CustomParserRuleEntity,
        sender: String,
        smsBody: String,
        timestamp: Long
    ): ParsedTransaction? {
        if (!matchesSender(rule, sender)) {
            Log.v(TAG, "Rule '${rule.name}' skipped — sender '$sender' doesn't match pattern '${rule.senderPattern}'")
            return null
        }

        val amount = extractAmount(rule, smsBody)
        if (amount == null) {
            Log.v(TAG, "Rule '${rule.name}' rejected — amount regex /${rule.amountRegex}/ didn't match")
            return null
        }
        val type = detectType(rule, smsBody)
        if (type == null) {
            Log.v(TAG, "Rule '${rule.name}' rejected — no expense/income keyword found in body")
            return null
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractFirstGroup(rule.merchantRegex, smsBody)?.trim(),
            reference = null,
            accountLast4 = extractFirstGroup(rule.accountRegex, smsBody)?.takeLast(4),
            balance = null,
            creditLimit = null,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = rule.bankNameDisplay,
            isFromCard = false,
            currency = rule.currency
        )
    }

    /**
     * Convenience for the editor preview: extract values without requiring
     * the sender to match. Used by the add/edit screen's live preview pane.
     */
    fun extractPreview(rule: CustomParserRuleEntity, smsBody: String): PreviewResult {
        val amount = extractAmount(rule, smsBody)
        val type = detectType(rule, smsBody)
        val merchant = extractFirstGroup(rule.merchantRegex, smsBody)?.trim()
        val account = extractFirstGroup(rule.accountRegex, smsBody)?.takeLast(4)
        return PreviewResult(amount = amount, type = type, merchant = merchant, accountLast4 = account)
    }

    private fun matchesSender(rule: CustomParserRuleEntity, sender: String): Boolean {
        val pattern = rule.senderPattern.trim()
        if (pattern.isEmpty()) return false
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
            ?: return sender.contains(pattern, ignoreCase = true)
        return regex.containsMatchIn(sender)
    }

    private fun extractAmount(rule: CustomParserRuleEntity, smsBody: String): BigDecimal? {
        val raw = extractFirstGroup(rule.amountRegex, smsBody) ?: return null
        // Strip thousand separators; keep decimal point.
        val cleaned = raw.replace(",", "").trim()
        return runCatching { BigDecimal(cleaned) }.getOrNull()
    }

    private fun detectType(rule: CustomParserRuleEntity, smsBody: String): TransactionType? {
        val lower = smsBody.lowercase()
        val expense = splitKeywords(rule.expenseKeywords)
        if (expense.any { lower.contains(it.lowercase()) }) return TransactionType.EXPENSE
        val income = splitKeywords(rule.incomeKeywords)
        if (income.any { lower.contains(it.lowercase()) }) return TransactionType.INCOME
        return null
    }

    private fun splitKeywords(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractFirstGroup(pattern: String?, text: String): String? {
        if (pattern.isNullOrBlank()) return null
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: return null
        val match = regex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: match.value.takeIf { it.isNotBlank() }
    }

    data class PreviewResult(
        val amount: BigDecimal?,
        val type: TransactionType?,
        val merchant: String?,
        val accountLast4: String?
    ) {
        val isComplete: Boolean get() = amount != null && type != null
    }

    /**
     * Apply a rule to every unrecognised SMS already on disk and return the
     * matches without persisting anything. Used by the editor's post-save
     * preview to show the user what would happen before they commit.
     */
    suspend fun dryRunOnPastSms(rule: CustomParserRuleEntity, sampleLimit: Int = 5): DryRunResult {
        val unrecognized = unrecognizedSmsDao.getAllVisibleSnapshot()
        Log.d(TAG, "dryRunOnPastSms rule='${rule.name}' (id=${rule.id}) scanning ${unrecognized.size} unrecognised SMS")
        val matches = unrecognized.mapNotNull { sms ->
            val timestamp = sms.receivedAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val parsed = runCatching { applyRule(rule, sms.sender, sms.smsBody, timestamp) }
                .onFailure { Log.w(TAG, "Dry-run threw on SMS ${sms.id}: ${it.message}") }
                .getOrNull()
            parsed?.let { DryRunMatch(sms, it) }
        }
        Log.d(TAG, "dryRunOnPastSms rule='${rule.name}' matched ${matches.size}/${unrecognized.size}")
        return DryRunResult(
            totalScanned = unrecognized.size,
            totalMatched = matches.size,
            samples = matches.take(sampleLimit),
            allMatches = matches
        )
    }

    data class DryRunMatch(
        val sms: UnrecognizedSmsEntity,
        val parsed: ParsedTransaction
    )

    data class DryRunResult(
        val totalScanned: Int,
        val totalMatched: Int,
        val samples: List<DryRunMatch>,
        val allMatches: List<DryRunMatch>
    )
}
