package com.pennywiseai.tracker.aiaccess

import androidx.room.withTransaction
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.rules.RuleSharingCodec
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a tool call that arrived from the relay and returns an MCP tool result. Reads run
 * freely; writes ([WRITE_TOOLS]) only while the user has turned on [AiWriteAccess].
 */
@Singleton
class AiTools @Inject constructor(
    private val transactions: TransactionRepository,
    private val balances: AccountBalanceRepository,
    private val subscriptions: SubscriptionRepository,
    private val entitlementGate: EntitlementGate,
    private val categories: CategoryRepository,
    private val mappings: MerchantMappingRepository,
    private val aliases: MerchantAliasRepository,
    private val rules: RuleRepository,
    private val applyRulesToPast: ApplyRulesToPastTransactionsUseCase,
    private val database: PennyWiseDatabase,
    private val writeAccess: AiWriteAccess,
) {
    suspend fun call(name: String, args: JsonObject): JsonObject {
        if (!entitlementGate.isProEntitled.value) {
            return error("AI access is part of PennyWise Pro, which isn't active on this phone. Renew Pro in PennyWise to continue.")
        }
        if (name in WRITE_TOOLS && !writeAccess.allowed.value) {
            return error("Changes from AI apps are off. Ask the user to turn on \"Let AI apps make changes\" in PennyWise → Settings → Connect Claude / ChatGPT, then try again. Nothing was changed.")
        }
        return try {
            val body = when (name) {
                "search_transactions" -> AiToolLogic.searchTransactions(
                    all = inRange(args) ?: return error("Give from and to as YYYY-MM-DD."),
                    query = args.string("query"),
                    category = args.string("category"),
                    type = args.string("type"),
                    limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 50,
                    account = args.string("account"),
                    offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0,
                    sort = args.string("sort"),
                    aliases = aliases.getAllAliasesAsMap(),
                )
                "spending_summary" -> AiToolLogic.spendingSummary(
                    all = inRange(args) ?: return error("Give from and to as YYYY-MM-DD."),
                    groupBy = args.string("group_by") ?: "category",
                    type = args.string("type") ?: "EXPENSE",
                    account = args.string("account"),
                    limit = args["limit"]?.jsonPrimitive?.intOrNull,
                )
                "list_accounts" -> AiToolLogic.accounts(balances.getAllLatestBalances().first())
                "list_subscriptions" -> AiToolLogic.subscriptions(subscriptions.getActiveSubscriptions().first())
                "list_categories" -> AiToolLogic.categories(categories.getVisibleCategories().first())
                "list_rules" -> return text(RuleSharingCodec.encode(rules.getAllRules().first()), isError = false)
                "categorize_merchants" -> categorizeMerchants(args["mappings"] as? JsonArray ?: return error("Give mappings as a list of {merchant, category}."))
                "set_merchant_aliases" -> setAliases(args["aliases"] as? JsonArray ?: return error("Give aliases as a list of {merchant, alias}."))
                "create_category" -> createCategory(
                    name = args.string("name")?.trim() ?: return error("Give the category a name."),
                    isIncome = args["is_income"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                "create_rule" -> createRule(args)
                else -> return error("PennyWise doesn't know the tool $name.")
            }
            text(body.toString(), isError = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error(if (name in WRITE_TOOLS) "PennyWise couldn't save that just now. Try again." else "PennyWise couldn't read that just now. Try again.")
        }
    }

    private suspend fun findCategory(name: String?): CategoryEntity? =
        name?.trim()?.let { n -> categories.getAllCategories().first().firstOrNull { it.name.equals(n, ignoreCase = true) } }

    /**
     * Remembers each merchant's category for future SMS and moves its past transactions out of
     * `from_category` (default "Others"), so rows the user filed elsewhere stay put. Same rules as
     * learning a category in the app: only rows of the category's kind, never split or loan rows,
     * never a placeholder merchant like "UPI Payment".
     */
    private suspend fun categorizeMerchants(items: JsonArray): JsonObject {
        if (items.size > MAX_BATCH) return error("Send at most $MAX_BATCH at a time.")
        val others = BuiltinCategoryNames.current("Others")
        val results = items.map { item ->
            val o = item as? JsonObject ?: JsonObject(emptyMap())
            val merchant = o.string("merchant")?.trim()
            val category = findCategory(o.string("category"))
            val problem = when {
                merchant == null -> "merchant is missing"
                LearnCategoryUseCase.isPlaceholderMerchant(merchant) -> "\"$merchant\" names a payment channel, not a payee, so it can't be remembered"
                category == null -> "No category named \"${o.string("category")}\". Use list_categories, or create_category first."
                else -> null
            }
            if (problem != null || merchant == null || category == null) {
                return@map buildJsonObject { put("merchant", merchant); put("error", problem) }
            }
            val from = findCategory(o.string("from_category"))?.name ?: others
            val kinds = if (category.isIncome) listOf(TransactionType.INCOME) else listOf(TransactionType.EXPENSE, TransactionType.CREDIT)
            val moved = database.withTransaction {
                mappings.setMapping(merchant, category.name)
                transactions.moveCategory(transactions.getMovableMerchantRowIds(merchant, from, kinds), from, category.name)
            }
            buildJsonObject { put("merchant", merchant); put("category", category.name); put("past_transactions_moved", moved) }
        }
        return buildJsonObject { put("results", JsonArray(results)) }
    }

    /** An empty alias removes the merchant's alias. */
    private suspend fun setAliases(items: JsonArray): JsonObject {
        if (items.size > MAX_BATCH) return error("Send at most $MAX_BATCH at a time.")
        val results = items.map { item ->
            val o = item as? JsonObject ?: JsonObject(emptyMap())
            val merchant = o.string("merchant")?.trim()
            val alias = (o["alias"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            when {
                merchant == null -> buildJsonObject { put("error", "merchant is missing") }
                alias.isEmpty() -> { aliases.removeAlias(merchant); buildJsonObject { put("merchant", merchant); put("alias", null as String?) } }
                else -> { aliases.setAlias(merchant, alias); buildJsonObject { put("merchant", merchant); put("alias", alias) } }
            }
        }
        return buildJsonObject { put("results", JsonArray(results)) }
    }

    private suspend fun createCategory(name: String, isIncome: Boolean): JsonObject {
        if (name.isEmpty()) return error("Give the category a name.")
        findCategory(name)?.let { return error("A category named \"${it.name}\" already exists.") }
        categories.createCategory(name, DEFAULT_CATEGORY_COLOR, isIncome)
        return buildJsonObject { put("created", name); put("kind", if (isIncome) "income" else "expense") }
    }

    private suspend fun createRule(args: JsonObject): JsonObject {
        val rule = try {
            AiToolLogic.ruleFrom(args)
        } catch (e: IllegalArgumentException) {
            return error(e.message ?: "That rule isn't valid.")
        }
        val existing = rules.getAllRules().first()
        if (existing.any { it.name.equals(rule.name, ignoreCase = true) }) return error("A rule named \"${rule.name}\" already exists.")
        rules.insertRule(rule)
        val applied = if (args["apply_to_past"]?.jsonPrimitive?.booleanOrNull == true) {
            applyRulesToPast.applyRuleToUncategorizedTransactions(rule).totalUpdated
        } else null
        return buildJsonObject {
            put("created", rule.name)
            applied?.let { put("past_uncategorized_transactions_updated", it) }
        }
    }

    private suspend fun inRange(args: JsonObject): List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>? {
        val from = args.date("from") ?: return null
        val to = args.date("to") ?: return null
        return if (to.isBefore(from)) null else transactions.getTransactionsBetweenDates(from, to).first()
    }

    private fun JsonObject.date(key: String): LocalDate? =
        string(key)?.let { try { LocalDate.parse(it) } catch (_: DateTimeParseException) { null } }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun error(message: String) = text(message, isError = true)

    private companion object {
        val WRITE_TOOLS = setOf("categorize_merchants", "set_merchant_aliases", "create_category", "create_rule")
        const val MAX_BATCH = 100
        const val DEFAULT_CATEGORY_COLOR = "#4CAF50" // the category editor's default
    }

    private fun text(s: String, isError: Boolean) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", s) }) }
        put("isError", isError)
    }
}
