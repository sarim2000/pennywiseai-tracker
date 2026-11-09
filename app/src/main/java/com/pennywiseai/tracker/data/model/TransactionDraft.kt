package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.service.PennyWiseTools
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        // "رجع" means "came back" and "gave back" too ("رجعت البيت", "رجعت فلوسه"), so these words
        // decide only when the money came back to the speaker: "رجعلي", "رجعوا فلوسي". Anything
        // else is left to the model, and the card shows the type before it's saved.
        private const val MY_MONEY = "(?:فلوسي|مصاريي|مبلغي)"
        private const val MONEY_CAME_BACK = "رجع(?:وا|و|ت)?\\s*(?:لي|لنا)(?![\\p{L}\\p{M}])" +
            "|رجع(?:وا|و|ت)?\\s+$MY_MONEY(?![\\p{L}\\p{M}])" +
            "|$MY_MONEY\\s+رجع(?:وا|و|ت)?(?![\\p{L}\\p{M}])"
        private fun arabicWord(words: String) = "(?<![\\p{L}\\p{M}])[وفب]?(?:ال)?(?:$words)"
        private fun hindiWord(words: String) = "(?<![\\p{L}\\p{M}])(?:$words)(?![\\p{L}\\p{M}])"

        // The model flips EXPENSE→INCOME on plain spends ~1 in 6 times, so the user's own words decide.
        private val SPEND_VERBS = Regex(
            "\\b(spent|spend|(?<!got )paid|pay|bought|buy|purchased|gave|sent|ordered|donated|diya|diye|kharch|kharida|bhara)\\b" +
                "|${arabicWord("دفعت|اشتريت|شريت|صرفت|سددت")}|${hindiWord("दिया|दिए|खर्च|खरीदा|खरीदी|भरा|चुकाया")}",
            RegexOption.IGNORE_CASE
        )
        private val RECEIVE_VERBS = Regex(
            "\\b(received|refund|refunded|cashback|credited|sold|won|earned|reimburse\\w*)\\b" +
                "|${arabicWord("استلمت|$MONEY_CAME_BACK|استرجعت|استرجاع|استرداد|كاش باك")}|${hindiWord("रिफंड|कैशबैक")}",
            RegexOption.IGNORE_CASE
        )
        private val SPEND_NOUNS = Regex("\\b(bill|fee|fees|rent|recharge|subscription)\\b", RegexOption.IGNORE_CASE)
        private val INCOME_NOUNS = Regex(
            "\\b(salary|bonus|interest|income|aayi|tankha)\\b|${arabicWord("راتب")}|${hindiWord("सैलरी|वेतन")}",
            RegexOption.IGNORE_CASE
        )

        /** The type a row's clause says, or null when it doesn't. What was done beats what it was for: "received 500 for rent". */
        private fun statedType(text: String, amount: BigDecimal, nounsCount: Boolean = true): TransactionType? = when {
            SPEND_VERBS.containsMatchIn(text) -> TransactionType.EXPENSE
            RECEIVE_VERBS.containsMatchIn(text) -> TransactionType.INCOME
            ChatMessageFacts.gotMoney(text, amount) -> TransactionType.INCOME
            !nounsCount -> null
            SPEND_NOUNS.containsMatchIn(text) -> TransactionType.EXPENSE
            INCOME_NOUNS.containsMatchIn(text) -> TransactionType.INCOME
            else -> null
        }

        // "Rent 12000 and train 500 were both refunded": the clause stops at "Rent 12000", so "rent" can't speak for that row.
        // ponytail: English only, like NOT_YET_PAID; Arabic and Hindi rows keep their noun, and the card shows the type.
        private val SHARED_REFUND = Regex("\\b(?:were|both|all)\\s+(?:both\\s+|all\\s+)?(?:refunded|reimbursed)\\b", RegexOption.IGNORE_CASE)

        /** Words for money coming in anywhere: then a row with no word of its own may be income too. */
        private fun mentionsIncome(text: String) =
            RECEIVE_VERBS.containsMatchIn(text) || INCOME_NOUNS.containsMatchIn(text) || ChatMessageFacts.gotMoney(text)

        const val MAX_ROWS = 10
        private val DONE_VERBS = Regex("\\b(spent|paid|bought|purchased|ordered|gave|sent|received|got|earned|sold)\\b", RegexOption.IGNORE_CASE)

        // Qwen2.5 records plans and other people's spending whatever the prompt says. English only for now.
        private val NOT_YET_PAID = Regex(
            "\\?\\s*$|\\b(plan to|plans to|planning|might|going to|gonna|will|would|should i|could i|can i|tomorrow|next (?:week|month|year)|" +
                "haven't|have not|fictional|example|if i|my (?:brother|sister|friend|mom|dad|wife|husband|boss|son|daughter) (?:spent|bought|paid(?! me)))\\b",
            RegexOption.IGNORE_CASE
        )

        /**
         * The review card for one message. A number the message doesn't state, or a row repeated
         * more often than its amount is stated, starts unticked: "bought coffee twice, 50 each" is
         * real, so it's the user's call.
         */
        fun reviewRows(drafts: List<TransactionDraft>): List<ReviewRow> {
            val seen = mutableMapOf<Triple<BigDecimal, String, TransactionType>, Int>()
            val clauses = drafts.takeIf { it.size > 1 }?.let { ChatMessageFacts.clauses(it.first().sourceText, it.map { d -> d.amount }) }
            val notPaid = drafts.firstOrNull()?.let { notYetPaid(it.sourceText, clauses, drafts.size) }.orEmpty()
            return drafts.take(MAX_ROWS).mapIndexed { index, draft ->
                val key = Triple(draft.amount.stripTrailingZeros(), draft.merchant.lowercase(), draft.type)
                val nth = (seen[key] ?: 0) + 1
                seen[key] = nth
                val check = when {
                    !draft.amountInMessage -> ReviewRow.Check.AMOUNT_NOT_IN_MESSAGE
                    nth > ChatMessageFacts.timesStated(draft.sourceText, draft.amount) -> ReviewRow.Check.REPEATED
                    notPaid[index] -> ReviewRow.Check.NOT_A_PAYMENT
                    else -> null
                }
                ReviewRow(index, draft, check)
            }
        }

        /**
         * Which rows the message doesn't say were paid, each by its own clause. One that doesn't say
         * it was done follows the clause before it: "my brother spent 200 on coffee and 400 on lunch",
         * "I will pay 100 for rent and buy shoes for 200".
         */
        private fun notYetPaid(message: String, clauses: List<String>?, rows: Int): List<Boolean> {
            if (clauses == null) return List(rows) { NOT_YET_PAID.containsMatchIn(message) }
            val flags = MutableList(rows) { false }
            var last = false
            ChatMessageFacts.inMessageOrder(message, clauses).forEach { row ->
                last = NOT_YET_PAID.containsMatchIn(clauses[row]) || (last && !DONE_VERBS.containsMatchIn(clauses[row]))
                flags[row] = last
            }
            return flags
        }

        /** The rows of one add call. The model sometimes writes a lone row without the array around it. */
        fun itemsOfCall(arguments: Map<String, Any?>): List<Map<String, Any?>> =
            (arguments["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?: listOf(arguments).takeIf { "amount" in arguments }.orEmpty()

        private val BROKEN_ADD_CALL = Regex("\"name\"\\s*:\\s*\"${PennyWiseTools.ADD_TRANSACTIONS}\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{\\s*\"items\"\\s*:\\s*\\[")

        /**
         * The items of an add call the runtime couldn't read. About 1 run in 20, Qwen2.5 writes a
         * sound array inside a broken wrapper ("}};\\n<tool_call>"), so the array is worth reading.
         * Null when there's no complete array: a half-written list would look like a whole one.
         */
        fun itemsInBrokenCall(text: String): List<Map<String, Any?>>? {
            val open = BROKEN_ADD_CALL.find(text)?.range?.last ?: return null
            var depth = 0
            var inString = false
            var close = -1
            for (i in open until text.length) {
                val c = text[i]
                when {
                    inString -> if (c == '"' && text[i - 1] != '\\') inString = false
                    c == '"' -> inString = true
                    c == '[' -> depth++
                    c == ']' -> if (--depth == 0) { close = i; break }
                }
            }
            if (close < 0) return null
            // ".50" isn't valid JSON, and the model writes it when the user does.
            val array = text.substring(open, close + 1).replace(Regex(":\\s*\\.(\\d)"), ": 0.$1")
            return runCatching {
                Json.parseToJsonElement(array).jsonArray.map { item ->
                    item.jsonObject.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull }
                }
            }.getOrNull()
        }

        /** The drafts of one turn's add calls; calls with an unusable amount are left out. */
        fun fromToolCalls(
            calls: List<Map<String, Any?>>,
            sourceText: String,
            categories: List<CategoryEntity>,
            accounts: List<AccountBalanceEntity>,
            baseCurrency: String,
            today: LocalDate = LocalDate.now(),
            noAccountLabel: String = "Cash / manual",
            ambiguousAccountLabel: String = "No account (say which — last 4 digits)",
            fallbackCategory: (merchant: String) -> String?
        ): List<TransactionDraft> {
            val usable = calls.filter { ChatMessageFacts.parseAmount(it["amount"]) != null }
            val amounts = usable.map { ChatMessageFacts.parseAmount(it["amount"])!! }
            val clauses = amounts.takeIf { it.size > 1 }?.let { ChatMessageFacts.clauses(sourceText, it) }
            // A row with no currency of its own takes one only from a clause that says it's for all of them.
            val codes = usable.map { it["currency"] as? String }
            val sharedText = clauses?.filter { ChatMessageFacts.sharesCurrency(it, codes) }?.joinToString(" ")
            // "in JPY: train 1200, lunch 800": a row the model left blank takes the code the others read.
            // ponytail: equal amounts have no clauses, so "in JPY: train 800, lunch 800" leaves that row on the base
            // currency, on the card for the user to see. Reading it from the whole message gave rows another row's currency.
            val sharedCode = sharedText?.let { text -> codes.firstOrNull { !it.isNullOrBlank() && ChatMessageFacts.namesCurrency(text, it) } }
            return usable.mapIndexedNotNull { index, args ->
                val rowArgs = if ((args["currency"] as? String).isNullOrBlank() && sharedCode != null) args + ("currency" to sharedCode) else args
                fromToolArgs(
                    rowArgs, sourceText, categories, accounts, baseCurrency, today, noAccountLabel, ambiguousAccountLabel,
                    clause = clauses?.get(index), sharedCurrencyText = sharedText, manyRows = usable.size > 1, fallbackCategory = fallbackCategory
                )
            }
        }

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
            clause: String? = null,
            sharedCurrencyText: String? = null,
            manyRows: Boolean = false,
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
            val modelType = when ((args["type"] as? String)?.trim()?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                "EXPENSE" -> TransactionType.EXPENSE
                else -> null
            }
            // A message with several rows can mix directions, so a row's own words go first. Without
            // any, the model only gets a say when the message mentions money coming in at all.
            val type = when {
                manyRows -> clause?.let { statedType(it, amount, nounsCount = !SHARED_REFUND.containsMatchIn(sourceText)) }
                    ?: modelType?.takeIf { mentionsIncome(sourceText) }
                    ?: if (modelSaysIncome && incomeOnlyCategory) TransactionType.INCOME else TransactionType.EXPENSE
                SPEND_VERBS.containsMatchIn(sourceText) || SPEND_NOUNS.containsMatchIn(sourceText) -> TransactionType.EXPENSE
                RECEIVE_VERBS.containsMatchIn(sourceText) || INCOME_NOUNS.containsMatchIn(sourceText) -> TransactionType.INCOME
                ChatMessageFacts.gotMoney(sourceText, amount) -> TransactionType.INCOME
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
            // The prompt lists the user's accounts, and the model sometimes files a row under one.
            val isAccountName = accounts.any { (it.alias ?: "${it.bankName} ${it.accountLast4}").equals(merchant, ignoreCase = true) } &&
                !sourceText.contains(merchant, ignoreCase = true)
            val currency = ChatMessageFacts.currency(
                clause?.takeIf { ChatMessageFacts.namesCurrency(it, args["currency"] as? String) } ?: sharedCurrencyText ?: sourceText,
                args["currency"] as? String,
                baseCurrency
            )
            // An account's balance moves by the raw amount, so only a transaction in its own currency may link.
            val account = matches.singleOrNull()?.takeIf { it.currency == currency }
            val ambiguous = matches.size > 1
            return TransactionDraft(
                amount = amount,
                currency = currency,
                // LiteRT-LM snake_cases parameter names.
                date = today.minusDays(
                    ChatMessageFacts.daysAgo(clause?.takeIf { ChatMessageFacts.statesADay(it) } ?: sourceText, args["daysAgo"] ?: args["days_ago"]).toLong()
                ),
                merchant = if (isAccountName) category else merchant,
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
