package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What an AI app may see, as pure functions over entities. Rules (ADR 0002):
 * money is always `{amount, currency}` and never summed across currencies,
 * accounts are masked to bank + last 4, and no SMS text ever leaves the phone.
 */
object AiToolLogic {

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
    ): JsonObject {
        val q = query?.trim()?.lowercase().orEmpty()
        val filtered = all.asSequence()
            .filter { type == null || it.transactionType.name == type }
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .filter { q.isEmpty() || it.merchantName.lowercase().contains(q) }
            .filter { account == null || accountOf(it) == account }
            .toList()
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
            putJsonArray("transactions") {
                shown.forEach { t ->
                    add(buildJsonObject {
                        put("date", t.dateTime.toString())
                        put("merchant", t.merchantName)
                        put("category", t.category)
                        put("type", t.transactionType.name)
                        put("amount", money(t.amount, t.currency))
                        accountOf(t)?.let { put("account", it) }
                    })
                }
            }
        }
    }

    /**
     * `type` EXPENSE means spending: debits and card purchases; INCOME means money received.
     * `month_category` groups by month, then category: each group carries `group` (YYYY-MM) and `sub`.
     */
    fun spendingSummary(all: List<TransactionEntity>, groupBy: String, type: String, account: String? = null): JsonObject {
        val types = if (type == "INCOME") setOf(TransactionType.INCOME) else setOf(TransactionType.EXPENSE, TransactionType.CREDIT)
        val counted = all.filter { it.transactionType in types && !it.excludedFromAnalytics && (account == null || accountOf(it) == account) }
        val month: (TransactionEntity) -> String = { t -> t.dateTime.toLocalDate().toString().take(7) }
        val key: (TransactionEntity) -> Pair<String, String?> = when (groupBy) {
            "merchant" -> { t -> t.merchantName to null }
            "month" -> { t -> month(t) to null }
            "day" -> { t -> t.dateTime.toLocalDate().toString() to null }
            "account" -> { t -> (accountOf(t) ?: "Unknown account") to null }
            "month_category" -> { t -> month(t) to t.category }
            else -> { t -> t.category to null }
        }
        val groups = counted.groupBy(key).entries.sortedByDescending { it.value.size }
        return buildJsonObject {
            put("type", if (type == "INCOME") "INCOME" else "EXPENSE")
            put("grand_total", totals(counted))
            putJsonArray("groups") {
                groups.forEach { (k, txns) ->
                    add(buildJsonObject {
                        put("group", k.first)
                        k.second?.let { put("sub", it) }
                        put("count", txns.size)
                        put("totals", totals(txns))
                    })
                }
            }
        }
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

    private fun totals(txns: List<TransactionEntity>): JsonArray =
        buildJsonArray { txns.sumByCurrency({ it.currency }, { it.amount }).values.forEach { add(money(it)) } }

    private fun money(m: Money) = money(m.amount, m.currency)

    private fun money(amount: BigDecimal, currency: String) = buildJsonObject {
        put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
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
