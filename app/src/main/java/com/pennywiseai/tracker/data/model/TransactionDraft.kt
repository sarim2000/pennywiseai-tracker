package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A transaction the model proposed from a chat message (#170). Never saved by
 * itself — the user confirms it on a card first.
 */
data class TransactionDraft(
    val amount: BigDecimal,
    val currency: String,
    val date: LocalDate,
    val merchant: String,
    val category: String,
    val type: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val accountLabel: String,
    val sourceText: String,
    /** False when the message doesn't contain [amount]: the card asks the user to check it. */
    val amountInMessage: Boolean
) {
    companion object {
        // Whole words, so "مراتب" isn't "راتب" and Milan's "मिलान" isn't "मिला". Arabic keeps its
        // prefixes and suffixes ("الراتب", "راتبي"), and lookarounds because \\b differs on ICU.
        // "رجع" alone also means "came back" ("رجعت البيت"), so a refund must say to whom or what:
        // "رجعلي", "رجعت لي", "رجعوا فلوسي", "الفلوس رجعت". "رجعت ليلا" (came back at night) isn't "لي".
        private const val MONEY_CAME_BACK = "رجع(?:وا|و|ت)?\\s*(?:لي|لنا)(?![\\p{L}\\p{M}])" +
            "|رجع(?:وا|و|ت)?\\s+(?:ال)?(?:فلوس|مصاري|مبلغ)" +
            "|(?:فلوس|مصاري|مبلغ)\\S*\\s+رجع"
        private fun arabicWord(words: String) = "(?<![\\p{L}\\p{M}])[وفب]?(?:ال)?(?:$words)"
        private fun hindiWord(words: String) = "(?<![\\p{L}\\p{M}])(?:$words)(?![\\p{L}\\p{M}])"

        // The model flips EXPENSE→INCOME on plain spends ~1 in 6 times, so the
        // user's own words decide: a spend verb wins ("spent my salary on rent"),
        // then an income word, then the model's type.
        private val SPEND_WORDS = Regex(
            "\\b(spent|spend|paid|pay|bought|buy|purchased|gave|sent|ordered|bill|fee|fees|rent|recharge|donated|subscription|diya|diye|kharch|kharida|bhara)\\b" +
                "|${arabicWord("دفعت|اشتريت|شريت|صرفت|سددت")}|${hindiWord("दिया|दिए|खर्च|खरीदा|खरीदी|भरा|चुकाया")}",
            RegexOption.IGNORE_CASE
        )
        private val INCOME_WORDS = Regex(
            "\\b(received|got|salary|refund|refunded|cashback|credited|sold|bonus|interest|income|won|earned|reimburse\\w*|mila|mili|aayi|tankha)\\b" +
                "|${arabicWord("راتب|استلمت|$MONEY_CAME_BACK|استرجعت|استرجاع|استرداد|كاش باك")}|${hindiWord("सैलरी|वेतन|रिफंड|कैशबैक|मिला|मिली|मिले")}",
            RegexOption.IGNORE_CASE
        )

        /** Builds a draft from the tool-call arguments, or null when the amount is unusable. */
        fun fromToolArgs(
            args: Map<String, Any?>,
            sourceText: String,
            categories: List<CategoryEntity>,
            accounts: List<AccountBalanceEntity>,
            baseCurrency: String,
            today: LocalDate = LocalDate.now(),
            noAccountLabel: String = "Cash / manual",
            ambiguousAccountLabel: String = "No account (say which — last 4 digits)",
            fallbackCategory: (merchant: String) -> String?
        ): TransactionDraft? {
            val amount = ChatMessageFacts.parseAmount(args["amount"]) ?: return null

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
            // Stated last-4 digits decide first (narrowed by a named bank), then an alias,
            // then the bank alone. Digits that match no account link nothing.
            val matches = accountText.takeIf { it.isNotEmpty() && it != "cash" && it != "null" && it != "none" }?.let { text ->
                val namesBank = { acc: AccountBalanceEntity ->
                    acc.bankName.lowercase().split(" ").first().let { bank -> bank.length >= 3 && text.contains(bank) }
                }
                val byLast4 = accounts.filter { it.accountLast4.isNotEmpty() && text.contains(it.accountLast4) }
                val byAlias = accounts.filter { acc -> acc.alias?.lowercase()?.takeIf { it.isNotBlank() }?.let { text.contains(it) } == true }
                when {
                    byLast4.isNotEmpty() -> byLast4.filter(namesBank).ifEmpty { byLast4.takeIf { accounts.none(namesBank) }.orEmpty() }
                    byAlias.isNotEmpty() -> byAlias
                    Regex("\\d{4}").containsMatchIn(text) -> emptyList()
                    else -> accounts.filter(namesBank)
                }.distinctBy { it.bankName to it.accountLast4 }
            }.orEmpty()
            val currency = ChatMessageFacts.currency(sourceText, args["currency"] as? String, baseCurrency)
            // An account's balance moves by the raw amount, so only a transaction in its own currency may link.
            val account = matches.singleOrNull()?.takeIf { it.currency == currency }
            val ambiguous = matches.size > 1
            return TransactionDraft(
                amount = amount,
                currency = currency,
                // LiteRT-LM snake_cases parameter names.
                date = today.minusDays(ChatMessageFacts.daysAgo(sourceText, args["daysAgo"] ?: args["days_ago"]).toLong()),
                merchant = merchant,
                category = category,
                type = type,
                bankName = account?.bankName,
                accountLast4 = account?.accountLast4,
                accountLabel = account?.let { "${it.bankName} ••${it.accountLast4}" }
                    ?: if (ambiguous) ambiguousAccountLabel else noAccountLabel,
                sourceText = sourceText,
                amountInMessage = ChatMessageFacts.statesAmount(sourceText, amount)
            )
        }
    }
}
