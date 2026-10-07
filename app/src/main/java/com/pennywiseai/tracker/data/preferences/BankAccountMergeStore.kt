package com.pennywiseai.tracker.data.preferences

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

/** Applies only user-confirmed account aliases, scoped to the bank and currency. */
class BankAccountMergeStore(context: Context) {
    private val prefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    fun mappings(): Map<String, String> = prefs.getStringSet("bank_account_merges", emptySet())
        .orEmpty().mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size == 2 && validMapping(parts[0], parts[1])) parts[0] to parts[1] else null
        }.toMap()

    fun remember(bank: String, currency: String, short: String, full: String) = synchronized(writeLock) {
        val key = mappingKey(bank, currency, short)
        require(validMapping(key, full))
        write(mappings() + (key to full))
    }

    fun restore(imported: Map<String, String>) = synchronized(writeLock) {
        // Existing decisions on this device take precedence over an imported backup.
        write(imported.filter { (key, full) -> validMapping(key, full) } + mappings())
    }

    private fun write(mappings: Map<String, String>) {
        check(prefs.edit().putStringSet("bank_account_merges", mappings.map { "${it.key}:${it.value}" }.toSet()).commit())
    }

    fun resolve(parsed: ParsedTransaction): ParsedTransaction = resolve(parsed, mappings())

    fun resolveSuffix(bank: String, currency: String, suffix: String): String =
        resolveSuffix(bank, currency, suffix, mappings())

    companion object {
        private val writeLock = Any()
        internal val mutationMutex = Mutex()
        private val shortSuffixPattern = Regex("\\d{3}")
        private val fullSuffixPattern = Regex("\\d{4}")
        private val currencyPattern = Regex("[A-Z]{3}")

        fun mappingKey(bank: String, currency: String, suffix: String): String = "$bank|$currency|$suffix"

        fun isShortMaskPair(short: String, full: String): Boolean =
            short.matches(shortSuffixPattern) && full.matches(fullSuffixPattern) && full.endsWith(short)

        private fun validMapping(key: String, full: String): Boolean {
            val parts = key.split('|')
            return parts.size == 3 && parts[0].isNotBlank() && ':' !in parts[0] &&
                parts[1].matches(currencyPattern) && isShortMaskPair(parts[2], full)
        }

        fun resolveSuffix(bank: String, currency: String, suffix: String, mappings: Map<String, String>): String {
            val full = mappings[mappingKey(bank, currency, suffix)] ?: return suffix
            return if (isShortMaskPair(suffix, full)) full else suffix
        }

        fun resolve(parsed: ParsedTransaction, mappings: Map<String, String>): ParsedTransaction {
            if (parsed.isFromCard || parsed.type == TransactionType.CREDIT) return parsed
            val suffix = parsed.accountLast4 ?: return parsed
            return parsed.copy(accountLast4 = resolveSuffix(parsed.bankName, parsed.currency, suffix, mappings))
        }

        fun duplicatePairs(accounts: List<AccountBalanceEntity>): List<Pair<AccountBalanceEntity, AccountBalanceEntity>> {
            val unique = accounts.distinctBy { Triple(it.bankName, it.currency, it.accountLast4) }
            return unique.filter { !it.isCreditCard && it.accountLast4.matches(shortSuffixPattern) }
                .mapNotNull { source ->
                    val targets = unique.filter { target ->
                        target.bankName == source.bankName && !target.isCreditCard &&
                            source.currency == target.currency && source.profileId == target.profileId &&
                            isShortMaskPair(source.accountLast4, target.accountLast4)
                    }
                    targets.singleOrNull()?.let { source to it }
                }
        }
    }
}
