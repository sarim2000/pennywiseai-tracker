package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

/**
 * A transaction the model proposed from a chat message (#170). Never saved by
 * itself — the user confirms it on a card first.
 */
data class TransactionDraft(
    val amount: BigDecimal,
    val merchant: String,
    val category: String,
    val type: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val accountLabel: String,
    val sourceText: String
) {
    companion object {
        // The model flips EXPENSE→INCOME on plain spends ~1 in 6 times, so the
        // user's own words decide: a spend verb wins ("spent my salary on rent"),
        // then an income word, then the model's type.
        private val SPEND_WORDS = Regex(
            "\\b(spent|spend|paid|pay|bought|buy|purchased|gave|sent|ordered|bill|fee|fees|rent|recharge|donated|subscription)\\b",
            RegexOption.IGNORE_CASE
        )
        private val INCOME_WORDS = Regex(
            "\\b(received|got|salary|refund|refunded|cashback|credited|sold|bonus|interest|income|won|earned|reimburse\\w*)\\b",
            RegexOption.IGNORE_CASE
        )

        /** Builds a draft from the tool-call arguments, or null when the amount is unusable. */
        fun fromToolArgs(
            args: Map<String, Any?>,
            sourceText: String,
            categories: List<CategoryEntity>,
            accounts: List<AccountBalanceEntity>,
            fallbackCategory: (merchant: String) -> String?
        ): TransactionDraft? {
            val amount = when (val a = args["amount"]) {
                is Number -> BigDecimal.valueOf(a.toDouble())
                is String -> a.replace(Regex("[^0-9.]"), "").toBigDecimalOrNull()
                else -> null
            }?.takeIf { it.signum() > 0 } ?: return null

            val merchant = (args["merchant"] as? String)?.trim().orEmpty().ifEmpty { "Unknown" }
            // With no verb either way, trust the model's INCOME only when it also
            // named an income-only category — it flips plain spends to INCOME too often.
            val modelSaysIncome = (args["type"] as? String)?.trim()?.uppercase() == "INCOME"
            val modelCategory = (args["category"] as? String)?.trim().orEmpty()
            val incomeOnlyCategory = categories.any { it.isIncome && it.name.equals(modelCategory, ignoreCase = true) } &&
                categories.none { !it.isIncome && it.name.equals(modelCategory, ignoreCase = true) }
            val type = when {
                SPEND_WORDS.containsMatchIn(sourceText) -> TransactionType.EXPENSE
                INCOME_WORDS.containsMatchIn(sourceText) -> TransactionType.INCOME
                modelSaysIncome && incomeOnlyCategory -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }

            val ofType = categories.filter { it.isIncome == (type == TransactionType.INCOME) && !it.isHidden }
            val wanted = (args["category"] as? String)?.trim().orEmpty()
            val category = ofType.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.name
                ?: fallbackCategory(merchant)?.takeIf { f -> ofType.any { it.name == f } }
                ?: ofType.firstOrNull { it.name == "Others" }?.name
                ?: ofType.firstOrNull()?.name
                ?: "Others"

            val accountText = (args["account"] as? String)?.trim().orEmpty().lowercase()
            // Attach an account only when the words pick exactly one; "hdfc" with two
            // HDFC accounts, or a shared last-4, stays unlinked rather than guessing.
            val matches = accountText.takeIf { it.isNotEmpty() && it != "cash" && it != "null" && it != "none" }?.let { text ->
                accounts.filter { acc ->
                    text.contains(acc.accountLast4) ||
                        acc.alias?.lowercase()?.let { text.contains(it) } == true ||
                        acc.bankName.lowercase().split(" ").first().let { bank -> bank.length >= 3 && text.contains(bank) }
                }.distinctBy { it.bankName to it.accountLast4 }
            }.orEmpty()
            val account = matches.singleOrNull()
            val ambiguous = matches.size > 1
            return TransactionDraft(
                amount = amount,
                merchant = merchant,
                category = category,
                type = type,
                bankName = account?.bankName,
                accountLast4 = account?.accountLast4,
                accountLabel = account?.let { "${it.bankName} ••${it.accountLast4}" }
                    ?: if (ambiguous) "No account (say which — last 4 digits)" else "Cash / manual",
                sourceText = sourceText
            )
        }
    }
}
