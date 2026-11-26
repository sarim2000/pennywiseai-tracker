package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.rules.RuleSharingCodec
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What an AI app may see, as pure functions over entities. Rules (ADR 0002):
 * money is always `{amount, currency}` and never summed across currencies,
 * accounts are masked to bank + last 4, and no SMS text ever leaves the phone.
 */
object AiToolLogic {
    /**
     * Rules as AI apps may see them. A condition on the SMS text or a note can quote real text,
     * and AI apps never get either, so its value is hidden.
     */
    fun shareableRules(rules: List<TransactionRule>): List<TransactionRule> = rules.map { rule ->
        rule.copy(conditions = rule.conditions.map { c ->
            when (c.field) {
                TransactionField.SMS_TEXT -> c.copy(value = HIDDEN_SMS_TEXT)
                TransactionField.NARRATION -> c.copy(value = HIDDEN_NOTE)
                else -> c
            }
        })
    }

    const val HIDDEN_SMS_TEXT = "(SMS text hidden)"
    const val HIDDEN_NOTE = "(note hidden)"
    const val NO_NOTE_ACTIONS = "Rules that write a transaction's note can't be created or changed from here: notes are hidden from AI apps. Create it in PennyWise → Rules."

    private val HIDDEN_FIELDS = setOf(TransactionField.SMS_TEXT, TransactionField.NARRATION)
    const val NO_HIDDEN_CONDITIONS = "Rules that match on SMS text or a transaction's note can't be created or changed from here: both are hidden from AI apps. Create it in PennyWise → Rules."

    const val MAX_ROWS = 200

    fun searchTransactions(
        all: List<TransactionEntity>,
        query: String?,
        category: String?,
        type: String?,
        limit: Int,
        account: String? = null,
        offset: Int = 0,
        sort: String? = null,
        aliases: Map<String, String> = emptyMap(),
        tagsById: Map<Long, List<String>> = emptyMap(),
        tag: List<String> = emptyList(),
        excludeCategories: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        subCategories: Map<String, List<String>> = emptyMap(),
    ): JsonObject {
        val q = query?.trim()?.lowercase().orEmpty()
        val excludedCategories = withSubCategories(excludeCategories, subCategories)
        // The query also matches the alias, so a merchant group from spending_summary finds its rows.
        val base = all.filter {
            (type == null || it.transactionType.name == type) &&
                (q.isEmpty() || it.merchantName.lowercase().contains(q) || aliases[it.merchantName]?.lowercase()?.contains(q) == true) &&
                kept(it, categoryOf(it), tagsById, tag, excludedCategories, excludeTags) &&
                // A refund row also shows its own category ("Refunds"), so that name leaves it out too.
                excludedCategories.none { x -> x.equals(it.category, ignoreCase = true) }
        }
        val filtered = base.filter {
            (category == null || it.category.equals(category, ignoreCase = true)) && (account == null || accountOf(it) == account)
        }
        val matches = when (sort) {
            "date_asc" -> filtered.sortedBy { it.dateTime }
            // Amount order only makes sense within one currency; group by currency first.
            "amount_desc" -> filtered.sortedWith(compareBy<TransactionEntity> { it.currency }.thenByDescending { it.amount })
            "amount_asc" -> filtered.sortedWith(compareBy<TransactionEntity> { it.currency }.thenBy { it.amount })
            else -> filtered.sortedByDescending { it.dateTime }
        }
        val from = offset.coerceAtLeast(0)
        val shown = matches.drop(from).take(limit.coerceIn(1, MAX_ROWS))
        return buildJsonObject {
            put("total_matches", matches.size)
            put("offset", from)
            put("shown", shown.size)
            // Every category and account among the matches before those two filters, for pickers.
            putJsonArray("categories") { base.map { it.category }.distinct().sorted().forEach { add(it) } }
            putJsonArray("accounts") { base.mapNotNull { accountOf(it) }.distinct().sorted().forEach { add(it) } }
            putJsonArray("transactions") {
                shown.forEach { t ->
                    add(buildJsonObject {
                        put("date", t.dateTime.toString())
                        put("merchant", t.merchantName)
                        aliases[t.merchantName]?.let { put("alias", it) }
                        put("category", t.category)
                        put("type", t.transactionType.name)
                        put("amount", money(t.amount, t.currency))
                        accountOf(t)?.let { put("account", it) }
                    })
                }
            }
            putFilters(tag, excludeCategories, excludeTags)
        }
    }

    /**
     * `type` EXPENSE means spending: debits and card purchases; INCOME means money received.
     * `month_category` groups by month, then category: each group carries `group` (YYYY-MM) and `sub`.
     * Amounts follow the app's totals: only your part of loan-linked and split rows counts
     * ([forTotals]), and refunds (INCOME + DEDUCT_SPENT) come off spending instead of counting
     * as income, per currency and floored at zero, like Analytics.
     * Merchant groups use the merchant's alias when it has one, so aliased merchants total together.
     */
    fun spendingSummary(
        all: List<TransactionEntity>,
        groupBy: String,
        type: String,
        account: String? = null,
        limit: Int? = null,
        aliases: Map<String, String> = emptyMap(),
        tagsById: Map<Long, List<String>> = emptyMap(),
        tag: List<String> = emptyList(),
        excludeCategories: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        subCategories: Map<String, List<String>> = emptyMap(),
    ): JsonObject {
        val income = type == "INCOME"
        val category: (TransactionEntity) -> String = { t -> categoryOf(t) }
        val excludedCategories = withSubCategories(excludeCategories, subCategories)
        val inAccount = all.filter {
            (account == null || accountOf(it) == account) && kept(it, category(it), tagsById, tag, excludedCategories, excludeTags)
        }.forTotals()
        val counted = if (income) {
            inAccount.filter { it.transactionType == TransactionType.INCOME && !it.isRefund() }
        } else {
            inAccount.filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
        }
        val refunds = if (income) emptyList() else inAccount.filter { it.transactionType == TransactionType.INCOME && it.isRefund() }
        val month: (TransactionEntity) -> String = { t -> t.dateTime.toLocalDate().toString().take(7) }
        val key: (TransactionEntity) -> Pair<String, String?> = when (groupBy) {
            "merchant" -> { t -> (aliases[t.merchantName] ?: t.merchantName) to null }
            "month" -> { t -> month(t) to null }
            "day" -> { t -> t.dateTime.toLocalDate().toString() to null }
            "account" -> { t -> (accountOf(t) ?: "Unknown account") to null }
            "month_category" -> { t -> month(t) to category(t) }
            else -> { t -> category(t) to null }
        }
        val refundsByKey = refunds.groupBy(key)
        val groups = counted.groupBy(key).entries.sortedByDescending { it.value.size }
        return buildJsonObject {
            put("type", if (income) "INCOME" else "EXPENSE")
            put("grand_total", totals(counted, refunds))
            // Groups come most-transactions first (amounts in different currencies can't be ranked together).
            put("total_groups", groups.size)
            putJsonArray("groups") {
                groups.take(limit?.coerceAtLeast(1) ?: groups.size).forEach { (k, txns) ->
                    add(buildJsonObject {
                        put("group", k.first)
                        k.second?.let { put("sub", it) }
                        put("count", txns.size)
                        put("totals", totals(txns, refundsByKey[k].orEmpty()))
                    })
                }
            }
            putFilters(tag, excludeCategories, excludeTags)
        }
    }

    private fun TransactionEntity.isRefund() = budgetImpactType == BudgetImpactType.DEDUCT_SPENT

    /** A refund's category is the budget category it gives back to, as in Analytics. */
    private fun categoryOf(t: TransactionEntity) = if (t.isRefund()) (t.budgetCategory ?: "").ifEmpty { "Others" } else t.category

    /** [names] plus the sub-categories of each ([subCategories]: parent name to its children), as Analytics rolls them up. */
    private fun withSubCategories(names: List<String>, subCategories: Map<String, List<String>>): List<String> =
        names + names.flatMap { n -> subCategories.entries.filter { it.key.equals(n, ignoreCase = true) }.flatMap { it.value } }

    /** What was applied, so a caller can tell the filters took effect; absent when none was asked for. */
    private fun JsonObjectBuilder.putFilters(tag: List<String>, excludeCategories: List<String>, excludeTags: List<String>) {
        if (tag.isEmpty() && excludeCategories.isEmpty() && excludeTags.isEmpty()) return
        putJsonObject("filters") {
            putJsonArray("tag") { tag.forEach { add(it) } }
            putJsonArray("exclude_categories") { excludeCategories.forEach { add(it) } }
            putJsonArray("exclude_tags") { excludeTags.forEach { add(it) } }
        }
    }

    /**
     * Whether [t] (in [category]) carries any of [tag] when any is given, and is in none of
     * [excludeCategories] and carries none of [excludeTags]. Names match ignoring case, as in
     * the app's tag filter; a name that matches nothing leaves nothing out.
     */
    private fun kept(
        t: TransactionEntity,
        category: String,
        tagsById: Map<Long, List<String>>,
        tag: List<String>,
        excludeCategories: List<String>,
        excludeTags: List<String>,
    ): Boolean {
        val tags = tagsById[t.id].orEmpty()
        return (tag.isEmpty() || tags.any { x -> tag.any { it.equals(x, ignoreCase = true) } }) &&
            excludeCategories.none { it.equals(category, ignoreCase = true) } &&
            excludeTags.none { x -> tags.any { it.equals(x, ignoreCase = true) } }
    }

    private fun accountOf(t: TransactionEntity) = maskedAccount(t.bankName, t.accountNumber)

    fun accounts(balances: List<AccountBalanceEntity>): JsonObject = buildJsonObject {
        putJsonArray("accounts") {
            balances.forEach { b ->
                add(buildJsonObject {
                    val last4 = b.accountLast4.takeUnless { it == AccountBalanceEntity.WALLET_ACCOUNT_MARKER }
                    put("account", maskedAccount(b.bankName, last4) ?: b.bankName)
                    put("kind", if (b.isCreditCard) "credit card" else (b.accountType ?: "account").lowercase())
                    put(if (b.isCreditCard) "outstanding" else "balance", money(b.balance, b.currency))
                    b.creditLimit?.let { put("credit_limit", money(it, b.currency)) }
                    put("as_of", b.timestamp.toString())
                })
            }
        }
    }

    fun subscriptions(subs: List<SubscriptionEntity>): JsonObject = buildJsonObject {
        putJsonArray("subscriptions") {
            subs.forEach { s ->
                add(buildJsonObject {
                    put("merchant", s.merchantName)
                    put("amount", money(s.amount, s.currency))
                    put("billing_cycle", s.billingCycle)
                    put("direction", s.direction.name)
                    s.nextPaymentDate?.let { put("next_payment", it.toString()) }
                    s.category?.let { put("category", it) }
                })
            }
        }
    }

    /** Per-currency sums of [txns] less [refunds] in the same currency, floored at zero. */
    private fun totals(txns: List<TransactionEntity>, refunds: List<TransactionEntity>): JsonArray {
        val back = refunds.sumByCurrency({ it.currency }, { it.amount })
        return buildJsonArray {
            txns.sumByCurrency({ it.currency }, { it.amount }).forEach { (currency, gross) ->
                val net = gross.amount - (back[currency]?.amount ?: BigDecimal.ZERO)
                add(money(net.coerceAtLeast(BigDecimal.ZERO), currency))
            }
        }
    }

    fun categories(all: List<CategoryEntity>): JsonObject = buildJsonObject {
        putJsonArray("categories") {
            all.forEach { c -> add(buildJsonObject { put("name", c.name); put("kind", if (c.isIncome) "income" else "expense") }) }
        }
    }

    /**
     * A rule from `create_rule` arguments, checked exactly like an imported rules file. BLOCK
     * is refused: it silently drops future SMS, too much for an AI app to set up.
     */
    fun ruleFrom(args: JsonObject, actionsFromCaller: Boolean = true): TransactionRule {
        val rule = decodeRule(args)
        require(rule.conditions.none { it.field in HIDDEN_FIELDS }) { NO_HIDDEN_CONDITIONS }
        // A note the caller writes, or aims a stored note action at by changing the conditions, is one
        // it can then test other rules' note conditions against.
        require(!actionsFromCaller || rule.actions.none { it.field == TransactionField.NARRATION }) { NO_NOTE_ACTIONS }
        return rule
    }

    private fun decodeRule(args: JsonObject): TransactionRule {
        val shared = buildJsonObject {
            putJsonArray("rules") {
                add(buildJsonObject { RULE_FIELDS.forEach { k -> args[k]?.let { put(k, it) } } })
            }
        }
        val rule = try {
            RuleSharingCodec.decode(shared.toString()).rules.single()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "That rule isn't valid. It needs a name, at least one condition and one action, and each condition's operator and value must suit its field.", e
            )
        }
        require(rule.actions.none { it.actionType == ActionType.BLOCK }) { "AI apps can't create rules that block transactions." }
        return rule
    }

    private val RULE_FIELDS = listOf("name", "description", "priority", "conditions", "actions")

    /**
     * Why [rule] may not be counted or run over past transactions from an AI call, or null when it may.
     * A count on a condition the AI never sees (SMS text, a transaction's note) would let it read that
     * text one guess at a time. Stored conditions count too: a rule saved with one and then previewed
     * by id is the same oracle in two steps. A past run done
     * twice must not do more than once (the relay's timeout makes the client ask again) and must not
     * move money, so only setting a category and tag changes are allowed. Not merchant names or notes:
     * the old text survives only in the rule's history, which goes when the rule is deleted, and a
     * count on a note action would read notes the same way a note condition does.
     */
    fun pastApplyRefusal(rule: TransactionRule): String? {
        val supplied = rule.conditions.map { it.field }
        if (TransactionField.SMS_TEXT in supplied) return "A rule that matches on SMS text can't be previewed or applied to past transactions from here: SMS text is hidden from AI apps."
        if (TransactionField.NARRATION in supplied) return "A rule that matches on a transaction's note can't be previewed or applied to past transactions from here: notes are hidden from AI apps."
        if (!rule.isActive) return "\"${rule.name}\" is switched off, so it changes nothing. Turn it on in PennyWise → Rules first."
        val unsafe = rule.actions.filterNot { a ->
            when (a.actionType) {
                ActionType.SET -> a.field == TransactionField.CATEGORY
                ActionType.ADD_TAG, ActionType.REMOVE_TAG -> true
                else -> false
            }
        }
        if (unsafe.isEmpty()) return null
        val why = if (unsafe.any { it.actionType == ActionType.APPEND || it.actionType == ActionType.PREPEND }) "appends text" else "changes more than categories or tags"
        return "This rule can be saved, but can't be applied to past transactions from here because it $why. Apply it from PennyWise → Rules."
    }

    /** [existing] with only the fields [args] supplies replaced, checked like [ruleFrom]. Id, on/off, template flag and created time stay. */
    fun ruleUpdated(existing: TransactionRule, args: JsonObject): TransactionRule {
        val json = Json { encodeDefaults = true }
        // A rule the user made on SMS text or a note keeps its real condition: only its label fields change here.
        val keepsHidden = existing.conditions.any { it.field in HIDDEN_FIELDS }
        if (keepsHidden) require("actions" !in args && "conditions" !in args) {
            "\"${existing.name}\" matches on SMS text or a note, which are hidden from AI apps, so only its name, description and priority can be changed from here."
        }
        val build: (JsonObject) -> TransactionRule = if (keepsHidden) ::decodeRule else { a -> ruleFrom(a, actionsFromCaller = "actions" in args || "conditions" in args) }
        val edited = build(buildJsonObject {
            put("name", existing.name)
            existing.description?.let { put("description", it) }
            put("priority", existing.priority)
            put("conditions", json.encodeToJsonElement(existing.conditions))
            put("actions", json.encodeToJsonElement(existing.actions))
            RULE_FIELDS.forEach { k -> args[k]?.let { put(k, it) } }
        })
        return existing.copy(
            name = edited.name, description = edited.description, priority = edited.priority,
            conditions = edited.conditions, actions = edited.actions, updatedAt = System.currentTimeMillis(),
        )
    }

    /** list_rules: the user's own rules in create_rule's shape, plus the `id` update_rule and delete_rule take. */
    fun listRules(rules: List<TransactionRule>): String {
        val own = RuleSharingCodec.exportable(shareableRules(rules))
        val set = Json.parseToJsonElement(RuleSharingCodec.encode(own)).jsonObject
        val withIds = set["rules"]!!.jsonArray.mapIndexed { i, r -> JsonObject(r.jsonObject + ("id" to JsonPrimitive(own[i].id))) }
        return JsonObject(set + ("rules" to JsonArray(withIds))).toString()
    }

    /** `none`, `uncategorized` or `all`. An older client's `true` still means uncategorized. */
    fun applyToPast(args: JsonObject, default: String): String {
        val raw = args["apply_to_past"] ?: return default
        val v = raw as? JsonPrimitive ?: throw IllegalArgumentException("apply_to_past must be none, uncategorized or all.")
        return when {
            v.booleanOrNull == true -> "uncategorized"
            v.booleanOrNull == false -> "none"
            v.isString && v.content in listOf("none", "uncategorized", "all") -> v.content
            else -> throw IllegalArgumentException("apply_to_past must be none, uncategorized or all.")
        }
    }

    private fun money(m: Money) = money(m.amount, m.currency)

    private fun money(amount: BigDecimal, currency: String) = buildJsonObject {
        put("amount", amount.setScale(MinorUnits.digits(currency), RoundingMode.HALF_UP).toPlainString())
        put("currency", currency)
    }

    /** "HDFC ••4821"; only the last four digits ever leave the phone. */
    fun maskedAccount(bank: String?, number: String?): String? {
        val last4 = number?.filter { it.isLetterOrDigit() }?.takeLast(4)?.takeIf { it.isNotEmpty() }
        return when {
            bank.isNullOrBlank() && last4 == null -> null
            last4 == null -> bank
            else -> "${bank.orEmpty()} ••$last4".trim()
        }
    }
}
