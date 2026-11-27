package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.core.TransactionWidgets
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.domain.usecase.awaitsCategoryForRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
    private val tags: TagRepository,
    private val rules: RuleRepository,
    private val applyRulesToPast: ApplyRulesToPastTransactionsUseCase,
    private val database: PennyWiseDatabase,
    private val writeAccess: AiWriteAccess,
    private val widgets: TransactionWidgets,
) {
    private val ruleLock = Mutex()

    suspend fun call(name: String, args: JsonObject): JsonObject {
        if (!entitlementGate.isProEntitled.value) {
            return error("AI access is part of PennyWise Pro, which isn't active on this phone. Renew Pro in PennyWise to continue.")
        }
        if (name in WRITE_TOOLS && !writeAccess.allowed.value) {
            return error(WRITES_OFF)
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
                    tagsById = if (args.strings("tag").isEmpty() && args.strings("exclude_tags").isEmpty()) emptyMap() else tags.observeTransactionTagNames().first(),
                    tag = args.strings("tag"),
                    excludeCategories = args.strings("exclude_categories"),
                    excludeTags = args.strings("exclude_tags"),
                    subCategories = subCategoriesByParent(),
                )
                "spending_summary" -> AiToolLogic.spendingSummary(
                    all = inRange(args) ?: return error("Give from and to as YYYY-MM-DD."),
                    groupBy = args.string("group_by") ?: "category",
                    type = args.string("type") ?: "EXPENSE",
                    account = args.string("account"),
                    limit = args["limit"]?.jsonPrimitive?.intOrNull,
                    aliases = aliases.getAllAliasesAsMap(),
                    tagsById = if (args.strings("tag").isEmpty() && args.strings("exclude_tags").isEmpty()) emptyMap() else tags.observeTransactionTagNames().first(),
                    tag = args.strings("tag"),
                    excludeCategories = args.strings("exclude_categories"),
                    excludeTags = args.strings("exclude_tags"),
                    subCategories = subCategoriesByParent(),
                )
                "list_accounts" -> AiToolLogic.accounts(balances.getAllLatestBalances().first())
                "list_subscriptions" -> AiToolLogic.subscriptions(subscriptions.getActiveSubscriptions().first())
                "list_categories" -> AiToolLogic.categories(categories.getVisibleCategories().first())
                "list_rules" -> return text(AiToolLogic.listRules(rules.getAllRules().first()), isError = false)
                "categorize_merchants" -> categorizeMerchants(args["mappings"] as? JsonArray ?: return error("Give mappings as a list of {merchant, category}."))
                "set_merchant_aliases" -> setAliases(args["aliases"] as? JsonArray ?: return error("Give aliases as a list of {merchant, alias}."))
                "update_transactions" -> updateTransactions(args["updates"] as? JsonArray ?: return error("Give updates as a list of {id, category?, tags?, excluded_from_analytics?}."))
                "create_category" -> createCategory(
                    name = args.string("name")?.trim() ?: return error("Give the category a name."),
                    isIncome = args["is_income"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                "create_rule" -> createRule(args)
                "update_rule" -> updateRule(args)
                "preview_rule" -> previewRule(args)
                "delete_rule" -> deleteRule(args.string("id") ?: return error("Give the id of the rule, from list_rules."))
                else -> return error("PennyWise doesn't know the tool $name.")
            }
            text(body.toString(), isError = false)
        } catch (e: Refused) {
            error(e.message!!)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error(if (name in WRITE_TOOLS) "PennyWise couldn't save that just now. Try again." else "PennyWise couldn't read that just now. Try again.")
        }
    }

    private fun findCategory(name: String?, all: List<CategoryEntity>): CategoryEntity? =
        name?.trim()?.let { n -> all.firstOrNull { it.name.equals(n, ignoreCase = true) } }

    /**
     * Remembers each merchant's category for future SMS and moves its past transactions out of
     * `from_category` (default "Others"), so rows the user filed elsewhere stay put. Same rules as
     * learning a category in the app: only rows of the category's kind, never split or loan rows,
     * never a placeholder merchant like "UPI Payment".
     */
    private suspend fun categorizeMerchants(items: JsonArray): JsonObject {
        if (items.size > MAX_BATCH) throw Refused("Send at most $MAX_BATCH at a time.")
        val others = BuiltinCategoryNames.current("Others")
        // Read once: the batch writes mappings and moves rows but never adds a category.
        val all = categories.getAllCategories().first()
        val results = items.map { item ->
            val o = item as? JsonObject ?: JsonObject(emptyMap())
            val merchant = o.string("merchant")?.trim()
            val category = findCategory(o.string("category"), all)
            val fromName = o.string("from_category")
            val from = if (fromName == null) others else findCategory(fromName, all)?.name
            val problem = when {
                merchant == null -> "merchant is missing"
                LearnCategoryUseCase.isPlaceholderMerchant(merchant) -> "\"$merchant\" names a payment channel, not a payee, so it can't be remembered"
                category == null -> "No category named \"${o.string("category")}\". Use list_categories, or create_category first."
                from == null -> "No category named \"$fromName\" to move past transactions out of. Nothing was changed for this merchant."
                else -> null
            }
            if (problem != null || merchant == null || category == null || from == null) {
                return@map buildJsonObject { put("merchant", merchant); put("error", problem) }
            }
            val kinds = if (category.isIncome) listOf(TransactionType.INCOME) else listOf(TransactionType.EXPENSE, TransactionType.CREDIT)
            val moved = database.withTransaction {
                mappings.setMapping(merchant, category.name)
                transactions.moveCategory(transactions.getMovableMerchantRowIds(merchant, from, kinds), from, category.name)
            }
            buildJsonObject { put("merchant", merchant); put("category", category.name); put("past_transactions_moved", moved) }
        }
        return buildJsonObject { put("results", JsonArray(results)) }
    }

    /**
     * Changes the category, tags and "don't count in analytics" flag of specific rows, by the `id`
     * search_transactions gives. Each field goes through the same repository call the transaction
     * detail screen uses (quick category pick, tag save, exclude toggle); nothing else can be sent.
     * A row that already has the asked values is reported `unchanged` and not written. Results come
     * back in input order.
     */
    private suspend fun updateTransactions(items: JsonArray): JsonObject {
        if (items.size > MAX_BATCH) throw Refused("Send at most $MAX_BATCH updates at a time. Nothing was changed.")
        return lockedWrite {
            val seen = mutableSetOf<Long>()
            val existingTags = tags.observeAllTagNames().first().map { tagKey(it) }.toSet()
            val newTags = mutableSetOf<String>()
            // One database transaction for the whole batch, so open screens re-query once, not per row. A
            // row that throws can leave half of itself written, so any such failure rolls the whole call back.
            val results = try {
                database.withTransaction {
                    items.map { item ->
                        val o = item as? JsonObject
                        val idValue = o?.get("id") as? JsonPrimitive
                        val rawId = idValue?.takeIf { it.isString }?.content?.trim()
                        val id = rawId?.takeIf { r -> r.isNotEmpty() && r.all { it in '0'..'9' } }?.toLongOrNull()?.takeIf { it.toString() == rawId }
                        val outcome = try {
                            when {
                                !writeAccess.allowed.value -> "refused" to "Changes from AI apps were turned off while this ran. Nothing more was changed."
                                o == null -> "refused" to "each update must be an object"
                                idValue != null && !idValue.isString -> "refused" to "id must be a string, the one from search_transactions"
                                rawId.isNullOrEmpty() -> "refused" to "id is missing; use the id from search_transactions"
                                id == null -> "refused" to "not found"
                                !seen.add(id) -> "refused" to "duplicate id in this call"
                                else -> updateOne(id, o, existingTags, newTags)
                            }
                        } catch (e: Refused) {
                            "refused" to e.message!!
                        }
                        buildJsonObject {
                            if (rawId != null && rawId.length <= 20) put("id", rawId)
                            put("result", outcome.first)
                            outcome.second?.let { put("reason", it) }
                        }
                    }
                }
            } catch (e: Refused) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw Refused("PennyWise couldn't save these. Nothing was changed. Try again.")
            }
            if (results.any { it["result"]?.jsonPrimitive?.content == "updated" }) {
                // The rows are committed: a widget that fails to redraw must not turn the answer into "couldn't save".
                try { widgets.refresh() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }
            buildJsonObject { put("results", JsonArray(results)) }
        }
    }

    /**
     * One row of update_transactions: ("updated" | "unchanged", null), or a [Refused] with the reason.
     * [existingTags] are the lowercase names of tags that exist; [newTags] collects the ones this call
     * creates, capped at [MAX_NEW_TAGS] for the whole call.
     */
    private suspend fun updateOne(id: Long, o: JsonObject, existingTags: Set<String>, newTags: MutableSet<String>): Pair<String, String?> {
        o.keys.firstOrNull { it !in UPDATE_KEYS }?.let { throw Refused("\"$it\" can't be changed from here. Only category, tags and excluded_from_analytics can.") }
        val category = if ("category" in o) {
            val name = (o["category"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw Refused("category must be a category name")
            findCategory(name, categories.getAllCategories().first()) ?: throw Refused(NO_CATEGORY.format(name))
        } else null
        val wantedTags = if ("tags" in o) {
            val list = o["tags"] as? JsonArray ?: throw Refused("tags must be a list of names")
            // New tags are created on the fly, so a careless caller could flood the tag list.
            if (list.size > MAX_TAGS) throw Refused("at most $MAX_TAGS tags on a transaction")
            list.map { t ->
                val name = (t as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf(::isName) ?: throw Refused("tags must be a list of non-empty names")
                // The joiners U+200C/U+200D are format characters too, but emoji sequences and Persian words need them.
                // A name made only of joiners would show as a blank chip.
                if (name.all { it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt() }) throw Refused("tags must be a list of non-empty names")
                if (name.any { it.isISOControl() || (Character.getType(it) == Character.FORMAT.toInt() && it != '‌' && it != '‍') }) throw Refused("a tag name can't contain control or invisible formatting characters")
                if (name.length > MAX_TAG_LENGTH) throw Refused("a tag name can be at most $MAX_TAG_LENGTH characters")
                name
            }
        } else null
        val excluded = if ("excluded_from_analytics" in o) {
            (o["excluded_from_analytics"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw Refused("excluded_from_analytics must be true or false")
        } else null
        if (category == null && wantedTags == null && excluded == null) throw Refused("nothing to change; give category, tags or excluded_from_analytics")

        val txn = transactions.getTransactionById(id)?.takeIf { !it.isDeleted } ?: throw Refused("not found")
        val categoryChanges = category != null && txn.category != category.name
        if (categoryChanges) {
            // What the detail screen's picker offers: visible income categories for income, visible expense ones for the rest.
            if (category!!.isHidden) throw Refused(NO_CATEGORY.format(category.name))
            if (category.isIncome != (txn.transactionType == TransactionType.INCOME)) {
                throw Refused("\"${category.name}\" is an ${if (category.isIncome) "income" else "expense"} category and doesn't fit this ${txn.transactionType.name} transaction")
            }
            // A split's categories live in its splits; the app's quick pick isn't offered for it either.
            if (transactions.hasSplits(txn.id)) throw Refused("this transaction is split across categories; change it in PennyWise")
        }
        // Deduplicated first the way setTagsForTransaction does, or a list with both "Café" and "CAFÉ" would never read as unchanged.
        val wantedKeys = wantedTags?.distinctBy { it.lowercase() }?.map { tagKey(it) }?.toSet()
        val tagsChange = wantedKeys != null && wantedKeys != tags.getTagNamesForTransaction(txn.id).map { tagKey(it) }.toSet()
        val excludedChanges = excluded != null && excluded != txn.excludedFromAnalytics
        if (!categoryChanges && !tagsChange && !excludedChanges) return "unchanged" to null
        if (tagsChange) {
            val fresh = wantedKeys!!.filter { it !in existingTags && it !in newTags }
            if (newTags.size + fresh.size > MAX_NEW_TAGS) throw Refused("this call would create more than $MAX_NEW_TAGS new tags")
            newTags += fresh
        }
        if (categoryChanges) transactions.updateCategory(txn.id, category!!.name)
        if (tagsChange) tags.setTagsForTransaction(txn.id, wantedTags!!)
        if (excludedChanges) transactions.setExcludedFromAnalytics(txn.id, excluded!!)
        return "updated" to null
    }

    /** An empty alias removes the merchant's alias. */
    private suspend fun setAliases(items: JsonArray): JsonObject {
        if (items.size > MAX_BATCH) throw Refused("Send at most $MAX_BATCH at a time.")
        val results = items.map { item ->
            val o = item as? JsonObject ?: JsonObject(emptyMap())
            val merchant = o.string("merchant")?.trim()
            // Only an explicit "" removes an alias; a missing or non-string one must not.
            val alias = (o["alias"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            when {
                merchant == null -> buildJsonObject { put("error", "merchant is missing") }
                alias == null -> buildJsonObject { put("merchant", merchant); put("error", "alias must be a string; use \"\" to remove it") }
                alias.isEmpty() -> { aliases.removeAlias(merchant); buildJsonObject { put("merchant", merchant); put("alias", null as String?) } }
                else -> { aliases.setAlias(merchant, alias); buildJsonObject { put("merchant", merchant); put("alias", alias) } }
            }
        }
        return buildJsonObject { put("results", JsonArray(results)) }
    }

    private suspend fun createCategory(name: String, isIncome: Boolean): JsonObject {
        if (name.isEmpty()) throw Refused("Give the category a name.")
        findCategory(name, categories.getAllCategories().first())?.let { throw Refused("A category named \"${it.name}\" already exists.") }
        categories.createCategory(name, DEFAULT_CATEGORY_COLOR, isIncome)
        return buildJsonObject { put("created", name); put("kind", if (isIncome) "income" else "expense") }
    }

    private suspend fun createRule(args: JsonObject): JsonObject {
        refuseDryRun(args)
        val rule = try {
            AiToolLogic.ruleFrom(args)
        } catch (e: IllegalArgumentException) {
            throw Refused(e.message ?: "That rule isn't valid.")
        }
        val mode = applyMode(args, "none")
        if (mode != "none") AiToolLogic.pastApplyRefusal(rule)?.let { throw Refused(it) }
        // One at a time, and the past run stays inside the lock: two creates of the same rule both pass
        // the name check otherwise, and a delete or update must not land while rows are being rewritten.
        return lockedWrite {
            checkNewName(rule)
            rules.insertRule(rule)
            buildJsonObject {
                put("created", rule.name)
                putPastResult(rule, mode)
            }
        }
    }

    /** Changes only the fields given; the rule stays where it is in the list and keeps its on/off state. */
    private suspend fun updateRule(args: JsonObject): JsonObject {
        refuseDryRun(args)
        val id = args.string("id") ?: throw Refused("Give the id of the rule, from list_rules.")
        val mode = applyMode(args, "none")
        return lockedWrite {
            val updated = updatedRule(id, args)
            if (mode != "none") AiToolLogic.pastApplyRefusal(updated)?.let { throw Refused(it) }
            rules.updateRule(updated)
            buildJsonObject {
                put("updated", updated.name)
                putPastResult(updated, mode)
            }
        }
    }

    /** What update_rule would save for [id] and [args]; throws the refusal update_rule would give. */
    private suspend fun updatedRule(id: String, args: JsonObject): TransactionRule {
        val existing = rules.getRuleById(id) ?: throw Refused("No rule with id \"$id\". Use list_rules for the current ids.")
        if (existing.isSystemTemplate) throw Refused("\"${existing.name}\" is a built-in rule, which can't be changed from here. It can be switched off or edited in PennyWise → Rules.")
        if (existing.actions.any { it.actionType == ActionType.BLOCK }) throw Refused("\"${existing.name}\" blocks transactions, so it can only be changed in PennyWise → Rules.")
        val updated = try {
            AiToolLogic.ruleUpdated(existing, args)
        } catch (e: IllegalArgumentException) {
            // Nothing the caller sent is wrong when it sent neither list: the stored rule is the problem.
            if ("conditions" !in args && "actions" !in args) throw Refused("This rule uses a condition that can't be written from here. Edit it in PennyWise → Rules.")
            throw Refused(e.message ?: "That rule isn't valid.")
        }
        if (rules.getAllRules().first().any { it.id != id && it.name.equals(updated.name, ignoreCase = true) }) throw Refused("A rule named \"${updated.name}\" already exists.")
        return updated
    }

    private suspend fun checkNewName(rule: TransactionRule) {
        if (rules.getAllRules().first().any { it.name.equals(rule.name, ignoreCase = true) }) throw Refused("A rule named \"${rule.name}\" already exists.")
    }

    /** Counts what the rule would change on past transactions, validated like create_rule (no id) or update_rule (with id). Writes nothing. */
    private suspend fun previewRule(args: JsonObject): JsonObject {
        val mode = applyMode(args, "uncategorized")
        if (mode == "none") throw Refused("Give apply_to_past as uncategorized or all; with none there is nothing to preview.")
        val id = args.string("id")
        // Inside the lock, so the count is not taken half way through another call's past run.
        return ruleLock.withLock { previewLocked(id, args, mode) }
    }

    private suspend fun previewLocked(id: String?, args: JsonObject, mode: String): JsonObject {
        val rule = if (id != null) updatedRule(id, args) else {
            val new = try {
                AiToolLogic.ruleFrom(args)
            } catch (e: IllegalArgumentException) {
                throw Refused(e.message ?: "That rule isn't valid.")
            }
            checkNewName(new)
            new
        }
        AiToolLogic.pastApplyRefusal(rule)?.let { throw Refused(it) }
        // ponytail: "uncategorized" keeps every matching row in memory to count the ones in Others; give
        // the use case a count-only mode if large ledgers on low-memory phones make this a problem.
        val preview = applyRulesToPast.previewRuleApplication(rule, maxSamples = if (mode == "all") 0 else Int.MAX_VALUE)
        val count = if (mode == "all") preview.totalWouldUpdate else preview.samples.count { !it.isBlock && it.original.awaitsCategoryForRules() }
        return buildJsonObject {
            put("rule", rule.name)
            put("past_transactions_would_update", count)
            put("apply_to_past", mode)
        }
    }

    /** A caller that asked for a dry run must never get a saved rule, so the argument is refused, not ignored. */
    private fun refuseDryRun(args: JsonObject) {
        if ("dry_run" in args) throw Refused("dry_run isn't supported. Use preview_rule to count what a rule would change. Nothing was changed.")
    }

    /** Built-in rules can be switched off in the app but not deleted, so they can't be here. Past transactions keep what the rule did. */
    private suspend fun deleteRule(id: String): JsonObject = lockedWrite {
        val rule = rules.getRuleById(id) ?: throw Refused("No rule with id \"$id\". It may already be deleted; use list_rules for the current ids.")
        if (rule.isSystemTemplate) throw Refused("\"${rule.name}\" is a built-in rule, which can't be deleted. It can be switched off in PennyWise → Rules.")
        if (rule.actions.any { it.actionType == ActionType.BLOCK }) throw Refused("\"${rule.name}\" blocks transactions, so it can only be deleted in PennyWise → Rules.")
        rules.deleteRule(id)
        buildJsonObject { put("deleted", rule.name) }
    }

    /**
     * The rule lock for a write. The switch is checked again once inside: the wait behind another
     * call's past run can outlast the user turning "Let AI apps make changes" off.
     */
    private suspend fun <T> lockedWrite(block: suspend () -> T): T = ruleLock.withLock {
        if (!writeAccess.allowed.value) throw Refused(WRITES_OFF)
        block()
    }

    private fun applyMode(args: JsonObject, default: String) = try {
        AiToolLogic.applyToPast(args, default)
    } catch (e: IllegalArgumentException) {
        throw Refused(e.message!!)
    }

    /** Runs [rule] over past transactions as [mode] says and reports how many changed. */
    private suspend fun JsonObjectBuilder.putPastResult(rule: TransactionRule, mode: String) {
        if (mode == "none") return
        val r = try {
            if (mode == "all") applyRulesToPast.applyRuleToAllTransactions(rule) else applyRulesToPast.applyRuleToUncategorizedTransactions(rule)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The rule is already saved; say so, or the caller is told nothing was saved and creates it again.
            put("past_error", "The rule was saved, but applying it to past transactions failed. Use preview_rule to see what is left.")
            return
        }
        put("past_transactions_updated", r.totalUpdated)
        // Per-row failures don't stop the run; say which rows stayed as they were.
        if (r.errors.isNotEmpty()) putJsonArray("past_errors") { r.errors.take(20).forEach { add(it) } }
    }

    private suspend fun inRange(args: JsonObject): List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>? {
        val from = args.date("from") ?: return null
        val to = args.date("to") ?: return null
        return if (to.isBefore(from)) null else transactions.getTransactionsBetweenDates(from, to).first()
    }

    /** parent category name to its sub-categories' names, the roll-up Analytics uses. */
    private suspend fun subCategoriesByParent(): Map<String, List<String>> =
        categories.getAllCategories().first().parentNameOf().entries.groupBy({ it.value }, { it.key })

    private fun JsonObject.date(key: String): LocalDate? =
        string(key)?.let { try { LocalDate.parse(it) } catch (_: DateTimeParseException) { null } }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /**
     * A list of names; a lone string counts as a list of one. JSON strings only, the rule the
     * relay's requireFilterEcho uses to decide whether a filter was asked for.
     */
    private fun JsonObject.strings(key: String): List<String> =
        ((this[key] as? JsonArray) ?: listOfNotNull(this[key]))
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf(::isName) }

    /** How the tag table tells names apart: its case-insensitive match folds ASCII letters only, so "CAFÉ" and "Café" are two tags. */
    private fun tagKey(s: String) = String(CharArray(s.length) { i -> s[i].let { if (it in 'A'..'Z') it + 32 else it } })

    /** Has a visible character. Spelled out, not isNotBlank, so it is the same set as the relay's isName. */
    private fun isName(s: String) = s.any { !it.isWhitespace() && !it.isISOControl() && it != '\uFEFF' && it != '\u180E' }

    private fun error(message: String) = text(message, isError = true)

    /** A refusal to report as a tool error (isError: true), not as data. */
    private class Refused(message: String) : Exception(message)

    private companion object {
        val WRITE_TOOLS = setOf("categorize_merchants", "set_merchant_aliases", "create_category", "create_rule", "update_rule", "delete_rule", "update_transactions")
        val UPDATE_KEYS = setOf("id", "category", "tags", "excluded_from_analytics")
        const val MAX_BATCH = 100
        const val MAX_NEW_TAGS = 20
        const val NO_CATEGORY = "No category named \"%s\". Use list_categories, or create_category first."
        const val MAX_TAGS = 10
        const val MAX_TAG_LENGTH = 40
        const val WRITES_OFF = "Changes from AI apps are off. Ask the user to turn on \"Let AI apps make changes\" in PennyWise → Settings → Connect Claude / ChatGPT, then try again. Nothing was changed."
        const val DEFAULT_CATEGORY_COLOR = "#4CAF50" // the category editor's default
    }

    private fun text(s: String, isError: Boolean) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", s) }) }
        put("isError", isError)
    }
}
