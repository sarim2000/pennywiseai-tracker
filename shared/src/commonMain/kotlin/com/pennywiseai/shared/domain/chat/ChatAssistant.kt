@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.pennywiseai.shared.domain.chat

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.model.SharedCategory
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.shared.domain.usecase.CreateManualTransactionUseCase
import com.pennywiseai.shared.domain.usecase.ManualTransactionInput
import com.pennywiseai.shared.domain.usecase.TransactionMutationResult
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

// The iOS chat's side of Android's LlmRepository / PennyWiseTools / PendingChatAction
// (#170, #959), on shared types. New code: Android keeps its own copy for now.

/** A transaction the model proposed from a chat message. Never saved until the user confirms its card. */
data class ChatDraft(
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val category: String,
    val type: SharedTransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val accountLabel: String,
    val sourceText: String
)

/** Something the model proposed doing to the user's data. Applied only after the user confirms. */
sealed class PendingChatAction {
    data class Add(val draft: ChatDraft) : PendingChatAction()
    data class Delete(val transaction: SharedTransaction) : PendingChatAction()
    data class Update(val transaction: SharedTransaction, val newCategory: String?, val newMerchant: String?) : PendingChatAction()
}

/** A tool's answer: [reply] goes back to the model; [action], if any, becomes a confirm card. */
data class ChatToolResult(val reply: String, val action: PendingChatAction? = null)

class ChatAssistant(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository,
    private val categoryRepository: SharedCategoryRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val now: () -> Long = ::currentTimeMillis
) {
    private val createUseCase = CreateManualTransactionUseCase(transactionRepository, accountRepository)

    private fun today(): LocalDate = Instant.fromEpochMilliseconds(now()).toLocalDateTime(timeZone).date
    private fun SharedTransaction.day(): LocalDate = Instant.fromEpochMilliseconds(occurredAtEpochMillis).toLocalDateTime(timeZone).date

    private suspend fun thisMonth(): List<SharedTransaction> {
        val today = today()
        return transactionRepository.observeTransactions().first()
            .filter { !it.isDeleted && it.day().let { d -> d.year == today.year && d.month == today.month } }
    }

    /** Built for every new chat, so the date, figures and lists are never stale (#959). */
    suspend fun systemPrompt(): String {
        val categories = categoryRepository.observeCategories().first()
        val accounts = accountRepository.getDistinctAccounts().map { "${it.bankName} ${it.accountLast4}" }.distinct()
        val month = thisMonth()
        return ChatPrompt.compose(
            currentDate = today().toString(),
            monthLine = "${perCurrency(month, SharedTransactionType.EXPENSE)} spent, " +
                "${perCurrency(month, SharedTransactionType.INCOME)} income, ${month.size} transactions",
            expenseCategories = categories.filter { !it.isIncome }.map { it.name },
            incomeCategories = categories.filter { it.isIncome }.map { it.name },
            accounts = accounts
        )
    }

    /** Read-only: runs without a card. */
    suspend fun monthSummary(): String {
        val month = thisMonth()
        return "This month: ${perCurrency(month, SharedTransactionType.EXPENSE)} spent, " +
            "${perCurrency(month, SharedTransactionType.INCOME)} received, ${month.size} transactions."
    }

    /** Read-only: runs without a card. */
    suspend fun spendingByCategory(category: String): String {
        val name = matchCategory(category, categoryRepository.observeCategories().first()) ?: category.trim()
        val spent = thisMonth().filter { it.transactionType == SharedTransactionType.EXPENSE && it.category.equals(name, ignoreCase = true) }
        if (spent.isEmpty()) return "Nothing spent on $name this month."
        return "${perCurrency(spent, SharedTransactionType.EXPENSE)} on $name this month across ${spent.size} transactions."
    }

    /** add_transaction: a draft for the user to confirm, in [currency] (the user's default). */
    suspend fun proposeAdd(
        amount: Double,
        merchant: String,
        category: String,
        type: String,
        account: String,
        userMessage: String,
        currency: String
    ): ChatToolResult {
        val draft = ChatDrafts.fromToolArgs(
            amount, merchant, category, type, account, userMessage, currency,
            categoryRepository.observeCategories().first(),
            accountRepository.getDistinctAccounts()
        ) ?: return ChatToolResult("I couldn't work out the amount. Try something like \"coffee 120 at Starbucks\".")
        return ChatToolResult(
            "Shown to the user as a card to confirm: ${money(draft.amountMinor, draft.currency)} " +
                "${if (draft.type == SharedTransactionType.INCOME) "from" else "at"} ${draft.merchant} · ${draft.category} · ${draft.accountLabel}. " +
                "Nothing is saved until they tap Add. Ask them to check the card.",
            PendingChatAction.Add(draft)
        )
    }

    /** delete_transaction / update_transaction: finds the row the user means and proposes the change. */
    suspend fun proposeChange(
        delete: Boolean,
        words: String,
        amount: Double,
        daysAgo: Int,
        newCategory: String,
        newMerchant: String
    ): ChatToolResult {
        val since = now() - 30L * 24 * 60 * 60 * 1000
        val recent = transactionRepository.observeTransactions().first().filter { it.occurredAtEpochMillis >= since }
        val amountMinor = (amount * 100).roundToLong().takeIf { amount > 0 && amount < 1e13 }
        val match = ChatTransactionFinder.findWithFallback(recent, words, amountMinor, daysAgo.takeIf { it >= 0 }, today(), timeZone)
            ?: return ChatToolResult("I couldn't find a matching transaction in the last 30 days. Try the merchant name and amount.")
        val target = match.transaction
        // Say so when the amount had to be dropped to find it.
        val closest = if (match.loosened) "Not an exact match, the closest one. " else ""
        if (delete) {
            return ChatToolResult(
                "${closest}Shown to the user as a card to confirm deleting: ${describe(target)}. Nothing is deleted until they tap Delete.",
                PendingChatAction.Delete(target)
            )
        }
        val category = newCategory.trim().takeIf { it.isNotEmpty() }
            ?.let { matchCategory(it, categoryRepository.observeCategories().first()) }
            ?.takeIf { it != target.category }
        val merchant = newMerchant.trim().takeIf { it.isNotEmpty() && !it.equals(target.merchantName, ignoreCase = true) }
        if (category == null && merchant == null) {
            return ChatToolResult("Ask what to change it to: a category from their list or a new merchant name.")
        }
        val change = listOfNotNull(merchant?.let { "merchant $it" }, category?.let { "category $it" }).joinToString(", ")
        return ChatToolResult(
            "${closest}Shown to the user as a card to confirm: ${describe(target)} → $change. Nothing changes until they tap Update.",
            PendingChatAction.Update(target, category, merchant)
        )
    }

    /** The user tapped the card's confirm button. Null when applied, else why it wasn't. */
    suspend fun confirm(action: PendingChatAction): String? = when (action) {
        is PendingChatAction.Add -> {
            val d = action.draft
            val result = createUseCase.execute(
                ManualTransactionInput(
                    amountMinor = d.amountMinor,
                    merchantName = d.merchant,
                    category = d.category,
                    note = "Added from chat: \"${d.sourceText.take(200)}\"",
                    currency = d.currency,
                    transactionType = d.type,
                    occurredAtEpochMillis = now(),
                    bankName = d.bankName,
                    accountLast4 = d.accountLast4
                )
            )
            when (result) {
                is TransactionMutationResult.Success -> null
                is TransactionMutationResult.ValidationError -> result.message
                is TransactionMutationResult.NotFound -> result.message
            }
        }
        is PendingChatAction.Delete -> {
            transactionRepository.softDelete(action.transaction.id, now())
            null
        }
        is PendingChatAction.Update -> {
            val current = transactionRepository.getById(action.transaction.id)
            if (current == null || current.isDeleted) {
                "That transaction is gone"
            } else {
                transactionRepository.update(
                    current.copy(
                        merchantName = action.newMerchant ?: current.merchantName,
                        category = action.newCategory ?: current.category,
                        updatedAtEpochMillis = now()
                    )
                )
                null
            }
        }
    }

    /** "INR 1200.00, USD 10.00": one figure per currency, never summed across them. */
    private fun perCurrency(rows: List<SharedTransaction>, type: SharedTransactionType): String {
        val totals = rows.filter { it.transactionType == type }
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        if (totals.isEmpty()) return "nothing"
        return totals.entries.sortedByDescending { it.value }.joinToString(", ") { money(it.value, it.key) }
    }

    /** An existing row in ITS currency, never relabelled with the default one. */
    private fun describe(tx: SharedTransaction): String =
        "${money(tx.amountMinor, tx.currency)} ${if (tx.transactionType == SharedTransactionType.INCOME) "from" else "at"} " +
            "${tx.merchantName} · ${tx.category} · ${tx.day()}"

    companion object {
        /** Currency-tagged plain text for the model ("INR 120.50"); the UI formats its own. */
        fun money(minor: Long, currency: String): String {
            val sign = if (minor < 0) "-" else ""
            val abs = kotlin.math.abs(minor)
            return "$currency $sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        }

        /** Category the user named, tolerant of "Food", "food and dining", "FOOD & DINING". */
        internal fun matchCategory(wanted: String, categories: List<SharedCategory>): String? {
            val norm = { x: String -> x.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), " ").trim() }
            val w = norm(wanted)
            if (w.isEmpty()) return null
            return categories.firstOrNull { norm(it.name) == w }?.name
                ?: categories.firstOrNull { norm(it.name).startsWith(w) || w.startsWith(norm(it.name)) }?.name
                ?: categories.firstOrNull { norm(it.name).split(" ").first() == w.split(" ").first() }?.name
        }

        private val CLAIMS_A_CHANGE = Regex(
            "\\b(I(?:'ve| have)?|has been|have been|is now|was) (added|updated|changed|deleted|removed|recorded)\\b",
            RegexOption.IGNORE_CASE
        )

        /**
         * A small model sometimes narrates a change without calling a tool. When no
         * card came out of the turn, the chat says nothing happened.
         */
        fun nothingChangedNote(reply: String, proposedSomething: Boolean): String? =
            if (!proposedSomething && CLAIMS_A_CHANGE.containsMatchIn(reply)) {
                "(Nothing was changed. I can only add, update or delete through a card you confirm. " +
                    "Try: \"change the tea to Groceries\" or \"delete the tea from today\".)"
            } else null
    }
}

/** Android's TransactionDraft.fromToolArgs on shared types. */
internal object ChatDrafts {
    // The model flips EXPENSE→INCOME on plain spends, so the user's own words decide:
    // a spend verb wins, then an income word, then the model's type.
    private val SPEND_WORDS = Regex(
        "\\b(spent|spend|paid|pay|bought|buy|purchased|gave|sent|ordered|bill|fee|fees|rent|recharge|donated|subscription)\\b",
        RegexOption.IGNORE_CASE
    )
    private val INCOME_WORDS = Regex(
        "\\b(received|got|salary|refund|refunded|cashback|credited|sold|bonus|interest|income|won|earned|reimburse\\w*)\\b",
        RegexOption.IGNORE_CASE
    )

    fun fromToolArgs(
        amount: Double,
        merchantArg: String,
        categoryArg: String,
        typeArg: String,
        accountArg: String,
        sourceText: String,
        currency: String,
        categories: List<SharedCategory>,
        accounts: List<SharedAccountBalanceEntity>
    ): ChatDraft? {
        if (!amount.isFinite() || amount <= 0 || amount >= 1e13) return null
        val amountMinor = (amount * 100).roundToLong().takeIf { it > 0 } ?: return null
        val merchant = merchantArg.trim().ifEmpty { "Unknown" }
        val wanted = categoryArg.trim()
        // With no verb either way, trust the model's INCOME only with an income-only category.
        val incomeOnlyCategory = categories.any { it.isIncome && it.name.equals(wanted, ignoreCase = true) } &&
            categories.none { !it.isIncome && it.name.equals(wanted, ignoreCase = true) }
        val type = when {
            SPEND_WORDS.containsMatchIn(sourceText) -> SharedTransactionType.EXPENSE
            INCOME_WORDS.containsMatchIn(sourceText) -> SharedTransactionType.INCOME
            typeArg.trim().equals("INCOME", ignoreCase = true) && incomeOnlyCategory -> SharedTransactionType.INCOME
            else -> SharedTransactionType.EXPENSE
        }

        val ofType = categories.filter { it.isIncome == (type == SharedTransactionType.INCOME) }
        val category = ofType.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.name
            ?: SharedCategoryMapping.determineCategory(merchant, type.name).takeIf { f -> ofType.any { it.name == f } }
            ?: ofType.firstOrNull { it.name == "Others" }?.name
            ?: ofType.firstOrNull()?.name
            ?: "Others"

        // Attach an account only when the words pick exactly one; "hdfc" with two HDFC
        // accounts, or a shared last-4, stays unlinked rather than guessing.
        val text = accountArg.trim().lowercase()
        val matches = if (text.isEmpty() || text in setOf("cash", "null", "none")) emptyList() else {
            accounts.filter { acc ->
                (acc.accountLast4.isNotEmpty() && text.contains(acc.accountLast4)) ||
                    acc.bankName.lowercase().split(" ").first().let { bank -> bank.length >= 3 && text.contains(bank) }
            }.distinctBy { it.bankName to it.accountLast4 }
        }
        val account = matches.singleOrNull()
        return ChatDraft(
            amountMinor = amountMinor,
            currency = currency,
            merchant = merchant,
            category = category,
            type = type,
            bankName = account?.bankName,
            accountLast4 = account?.accountLast4,
            accountLabel = account?.let { "${it.bankName} ••${it.accountLast4}" }
                ?: if (matches.size > 1) "No account (say which, last 4 digits)" else "Cash / manual",
            sourceText = sourceText
        )
    }
}

/** Android's TransactionFinder on shared types, with kotlinx-datetime. */
object ChatTransactionFinder {
    data class Match(val transaction: SharedTransaction, val loosened: Boolean)

    /** Picks the row the user most likely means from words, an optional amount (minor units) and "days ago". */
    fun findBest(
        candidates: List<SharedTransaction>,
        words: String,
        amountMinor: Long?,
        daysAgo: Int?,
        today: LocalDate,
        timeZone: TimeZone
    ): SharedTransaction? {
        val tokens = words.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }
        // A stated amount or day is a hard filter, never a tie-breaker: a wrong row on a
        // delete/update card is worse than "not found".
        val wantedDay = daysAgo?.takeIf { it >= 0 }?.let { today.minus(it, DateTimeUnit.DAY) }
        val wantedAmount = amountMinor?.takeIf { it > 0 }
        if (tokens.isEmpty() && wantedAmount == null && wantedDay == null) return null
        return candidates.asSequence()
            .filter { !it.isDeleted }
            .filter { wantedAmount == null || it.amountMinor == wantedAmount }
            .filter { wantedDay == null || Instant.fromEpochMilliseconds(it.occurredAtEpochMillis).toLocalDateTime(timeZone).date == wantedDay }
            .map { tx -> tx to score(tx, tokens) }
            .filter { (_, s) -> s > 0 || tokens.isEmpty() }
            .maxWithOrNull(compareBy({ it.second }, { it.first.occurredAtEpochMillis }))
            ?.first
    }

    /**
     * [findBest] with the model's day and amount, then without the day, then without
     * the amount, then without both: a small model fills them in unasked (often
     * days_ago = 0), and as hard filters a guess hides the row the user meant.
     * Dropping the amount is flagged ([Match.loosened]); the card shows the date.
     */
    fun findWithFallback(
        candidates: List<SharedTransaction>,
        words: String,
        amountMinor: Long?,
        daysAgo: Int?,
        today: LocalDate,
        timeZone: TimeZone
    ): Match? {
        findBest(candidates, words, amountMinor, daysAgo, today, timeZone)?.let { return Match(it, false) }
        if (daysAgo != null) findBest(candidates, words, amountMinor, null, today, timeZone)?.let { return Match(it, false) }
        if (amountMinor == null) return null
        return (findBest(candidates, words, null, daysAgo, today, timeZone) ?: findBest(candidates, words, null, null, today, timeZone))
            ?.let { Match(it, true) }
    }

    private fun score(tx: SharedTransaction, tokens: List<String>): Int {
        val hay = listOfNotNull(tx.merchantName, tx.category, tx.note).joinToString(" ").lowercase()
        return tokens.count { hay.contains(it) }
    }

    private val STOP = setOf("the", "one", "from", "for", "and", "that", "this", "delete", "remove", "update", "change", "yesterday", "today", "transaction", "entry")
}

/** Android's composeSystemPrompt (after #959) and its token budget, for a model with [contextSize] tokens. */
object ChatPrompt {
    /**
     * Each list is capped so a fresh chat always fits the budget however many
     * categories or accounts the user has (#959). The tools still match any
     * category or account the user names.
     */
    fun compose(
        currentDate: String,
        monthLine: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accounts: List<String>
    ): String = """
        You are PennyWise AI, a friendly assistant inside an expense tracker. Today is $currentDate.
        This month so far: $monthLine.

        When the user tells you about money they spent, paid, bought or received, call addTransaction. Use EXPENSE unless they clearly received money.
        When the user asks how much they spent on something, call spendingByCategory; for this month's overall totals call monthSummary (the figures below are only a snapshot from when this chat started).
        If the user refers to something already recorded — "the tea from today", "that uber", "change", "should be", "was actually", "delete", "remove" — call updateTransaction or deleteTransaction with the words they used to identify it. Never call addTransaction for those.
        Otherwise answer briefly and helpfully. Never invent transactions or figures, and never say you added, changed or deleted anything yourself — only the tools do that, and the user confirms each one.

        Expense categories: ${promptList(expenseCategories, MAX_EXPENSE_CATEGORY_CHARS)}
        Income categories: ${promptList(incomeCategories, MAX_INCOME_CATEGORY_CHARS)}
        Accounts: ${promptList(accounts, MAX_ACCOUNT_CHARS).ifEmpty { "none yet" }}

        Plain text only: no markdown, asterisks or backticks.
        """.trimIndent()

    /** Names joined in order until [maxChars]; the rest are counted, not listed. */
    fun promptList(names: List<String>, maxChars: Int): String {
        var used = 0
        val shown = names.takeWhile { used += it.length + 2; used <= maxChars }
        val more = names.size - shown.size
        return (shown + listOfNotNull(if (more > 0) "and $more more" else null)).joinToString(", ")
    }

    /** Rough count, ~4 characters a token (Android's TokenUtils). */
    fun estimateTokens(text: String): Int = (text.length / 4.0).roundToInt()

    /**
     * Whether a chat holding [context] (instructions included) still has room for
     * [nextMessage] in a [contextSize]-token model. [RESERVED_TOKENS] stay free for
     * what the estimate can't see: tool schemas, the chat template and the reply.
     */
    fun fitsBudget(context: List<String>, nextMessage: String, contextSize: Int): Boolean =
        estimateTokens(context.joinToString("") + nextMessage) <= contextSize - RESERVED_TOKENS

    const val RESERVED_TOKENS = 1536
    private const val MAX_EXPENSE_CATEGORY_CHARS = 1200
    private const val MAX_INCOME_CATEGORY_CHARS = 400
    private const val MAX_ACCOUNT_CHARS = 500
}
