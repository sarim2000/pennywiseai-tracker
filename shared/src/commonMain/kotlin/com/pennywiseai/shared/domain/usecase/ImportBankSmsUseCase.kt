package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import com.pennywiseai.shared.data.repository.SharedMerchantMappingRepository
import com.pennywiseai.shared.data.repository.SharedRuleRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.shared.domain.rules.SharedRuleEvaluator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class BankSmsImportResult {
    data class Saved(val transaction: SharedTransaction) : BankSmsImportResult()
    data object Duplicate : BankSmsImportResult()
    /** Not from a bank PennyWise knows. */
    data object NotATransaction : BankSmsImportResult()
    /** A balance-only alert: the account's balance was recorded, no transaction. */
    data object BalanceOnly : BankSmsImportResult()
    /** From a known bank, but no transaction in it: an OTP, a promo, or a format the parser misses. */
    data class Unparsed(val bankName: String) : BankSmsImportResult()
}

/**
 * Saves one bank SMS handed over by iOS Shortcuts — the iOS twin of Android's
 * `SmsTransactionProcessor`: parse with parser-core, dedupe on the same
 * transaction hash formula, apply the category mapping, merchant mappings and Smart
 * Rules, then record the balance the SMS reports.
 *
 * Not mirrored (no shared storage for them yet): ignored accounts, blocking
 * rules, subscription matching, card rows and credit limits.
 */
class ImportBankSmsUseCase(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository,
    private val merchantMappingRepository: SharedMerchantMappingRepository,
    private val ruleRepository: SharedRuleRepository,
    private val categoryRepository: SharedCategoryRepository
) {
    suspend fun execute(sender: String?, body: String, receivedAtEpochMillis: Long): BankSmsImportResult {
        val parsed = when (val attempt = parseBankSms(sender, body, receivedAtEpochMillis)) {
            BankParseAttempt.NoParser -> return BankSmsImportResult.NotATransaction
            is BankParseAttempt.NoTransaction -> return BankSmsImportResult.Unparsed(attempt.bankName)
            is BankParseAttempt.Parsed -> attempt.sms
        }

        // Two automations can match one message; the check-then-insert below must not interleave.
        return saveLock.withLock { save(parsed, receivedAtEpochMillis) }
    }

    /**
     * Saves what the on-device model read from an SMS [execute] couldn't parse
     * (NotATransaction or Unparsed), through the same dedupe, mappings and rules,
     * marked with [AiSmsValidator.AI_NOTE]. [knownBank] is the bank an Unparsed
     * result named. A reading the validator rejects saves nothing: Unparsed.
     */
    suspend fun executeAiReading(
        body: String,
        receivedAtEpochMillis: Long,
        reading: AiSmsReading,
        knownBank: String?
    ): BankSmsImportResult {
        val parsed = AiSmsValidator.validate(body, reading, knownBank)
            ?: return BankSmsImportResult.Unparsed(knownBank ?: reading.bankName?.trim().orEmpty())
        return saveLock.withLock { save(parsed, receivedAtEpochMillis, note = AiSmsValidator.AI_NOTE) }
    }

    private suspend fun save(parsed: ParsedBankSms, receivedAt: Long, note: String? = null): BankSmsImportResult {
        // Federal Bank's balance-only alert has no spend in it (Android books it as a ₹0 expense).
        if (parsed.type == "BALANCE_UPDATE") {
            recordBalance(parsed, receivedAt, transactionId = null)
            return BankSmsImportResult.BalanceOnly
        }
        // A soft-deleted row also matches: the user removed it, so don't bring it back (as Android).
        if (transactionRepository.getByHash(parsed.transactionHash) != null) return BankSmsImportResult.Duplicate

        val type = SharedTransactionType.fromStorage(parsed.type)
        val merchant = parsed.merchant?.let(::normalizeMerchantName) ?: "Unknown Merchant"
        val defaultCategory = parsed.merchant?.let { SharedCategoryMapping.determineCategory(it, type.name) } ?: "Others"
        val mappedCategory = merchantMappingRepository.getByMerchant(merchant)?.category
            ?.takeIf { mappingFits(it, type) }
        val rule = SharedRuleEvaluator.firstMatch(ruleRepository.observeRules().first(), merchant)
        val finalType = rule?.transactionType?.let { raw -> SharedTransactionType.entries.firstOrNull { it.name == raw } } ?: type

        val now = currentTimeMillis()
        val transaction = SharedTransaction(
            amountMinor = toMinor(parsed.amount),
            merchantName = merchant,
            category = rule?.category ?: mappedCategory ?: defaultCategory,
            transactionType = finalType,
            occurredAtEpochMillis = receivedAt,
            note = note,
            currency = parsed.currency,
            transactionHash = parsed.transactionHash,
            reference = parsed.reference,
            bankName = parsed.bankName,
            accountLast4 = parsed.accountLast4,
            balanceAfterMinor = parsed.balance?.let(::toMinor),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val id = transactionRepository.insert(transaction)
        recordBalance(parsed, receivedAt, id)
        return BankSmsImportResult.Saved(transaction.copy(id = id))
    }

    /** Writes the balance the SMS reports onto its account; an SMS without one leaves accounts alone. */
    private suspend fun recordBalance(parsed: ParsedBankSms, receivedAt: Long, transactionId: Long?) {
        val balance = parsed.balance ?: return
        val last4 = targetAccountLast4(parsed) ?: return
        val existing = accountRepository.getLatestBalance(parsed.bankName, last4)
        accountRepository.insertBalance(
            SharedAccountBalanceEntity(
                bankName = parsed.bankName,
                accountLast4 = last4,
                timestampEpochMillis = receivedAt,
                balanceMinor = toMinor(balance),
                transactionId = transactionId,
                isCreditCard = existing?.isCreditCard ?: (parsed.type == "CREDIT"),
                accountType = existing?.accountType,
                // The account keeps its own currency (Android's processBalanceUpdate): a USD
                // spend on an INR card must not relabel the INR balance as USD.
                currency = existing?.currency ?: parsed.accountCurrency ?: parsed.currency,
                createdAtEpochMillis = currentTimeMillis()
            )
        )
    }

    /** A debit-card spend moves money in the card's linked account (Android's card routing). */
    private suspend fun targetAccountLast4(parsed: ParsedBankSms): String? {
        val last4 = parsed.accountLast4 ?: return if (parsed.isMobileWallet) WALLET_ACCOUNT else null
        if (!parsed.isFromCard) return last4
        val card = accountRepository.observeCards().first()
            .firstOrNull { it.bankName == parsed.bankName && it.cardLast4 == last4 }
        return card?.accountLast4?.takeIf { card.cardType == "DEBIT" } ?: last4
    }

    private suspend fun mappingFits(category: String, type: SharedTransactionType): Boolean {
        val isIncome = categoryRepository.observeCategories().first().firstOrNull { it.name == category }?.isIncome
        return isIncome == null || isIncome == (type == SharedTransactionType.INCOME)
    }

    companion object {
        /** Android's AccountBalanceEntity.WALLET_ACCOUNT_MARKER: a mobile wallet is one account. */
        const val WALLET_ACCOUNT = "WALLET"

        private val saveLock = Mutex()

        /** "450.00" → 45000. Every shared amount is value × 100, whatever the currency. */
        internal fun toMinor(plainAmount: String): Long {
            val negative = plainAmount.startsWith("-")
            val (whole, fraction) = plainAmount.removePrefix("-").split('.').let { it[0] to it.getOrElse(1) { "" } }
            val minor = (whole.ifEmpty { "0" }.toLong() * 100) + fraction.padEnd(2, '0').take(2).toLong()
            return if (negative) -minor else minor
        }

        /** Android's ParsedTransactionMapper rule: ALL-CAPS becomes Title Case, mixed case stays. */
        internal fun normalizeMerchantName(name: String): String {
            val trimmed = name.trim()
            if (trimmed != trimmed.uppercase()) return trimmed
            return trimmed.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }
    }
}

/** What parser-core read from one SMS, flattened to common types (amounts as plain decimals, 2 places). */
internal data class ParsedBankSms(
    val amount: String,
    val type: String,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: String?,
    val bankName: String,
    val transactionHash: String,
    val isFromCard: Boolean,
    val currency: String,
    val accountCurrency: String?,
    val isMobileWallet: Boolean
)

internal sealed class BankParseAttempt {
    data object NoParser : BankParseAttempt()
    data class NoTransaction(val bankName: String) : BankParseAttempt()
    data class Parsed(val sms: ParsedBankSms) : BankParseAttempt()
}

/**
 * Runs parser-core's [BankParserFactory] on [body], using [sender] when it names
 * a known bank and otherwise guessing the bank from the body. parser-core's
 * parsers are platform code (JVM and iOS source sets, not common), so this is
 * expect/actual; both actuals compile the one file in src/bankParserMain.
 */
internal expect fun parseBankSms(sender: String?, body: String, receivedAt: Long): BankParseAttempt
