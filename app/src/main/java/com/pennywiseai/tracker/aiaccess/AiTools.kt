package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** Runs a tool call that arrived from the relay and returns an MCP tool result. Read-only. */
@Singleton
class AiTools @Inject constructor(
    private val transactions: TransactionRepository,
    private val balances: AccountBalanceRepository,
    private val subscriptions: SubscriptionRepository,
    private val entitlementGate: EntitlementGate,
) {
    suspend fun call(name: String, args: JsonObject): JsonObject {
        if (!entitlementGate.isProEntitled.value) {
            return error("AI access is part of PennyWise Pro, which isn't active on this phone. Renew Pro in PennyWise to continue.")
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
                )
                "spending_summary" -> AiToolLogic.spendingSummary(
                    all = inRange(args) ?: return error("Give from and to as YYYY-MM-DD."),
                    groupBy = args.string("group_by") ?: "category",
                    type = args.string("type") ?: "EXPENSE",
                    account = args.string("account"),
                )
                "list_accounts" -> AiToolLogic.accounts(balances.getAllLatestBalances().first())
                "list_subscriptions" -> AiToolLogic.subscriptions(subscriptions.getActiveSubscriptions().first())
                else -> return error("PennyWise doesn't know the tool $name.")
            }
            text(body.toString(), isError = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error("PennyWise couldn't read that just now. Try again.")
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

    private fun text(s: String, isError: Boolean) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", s) }) }
        put("isError", isError)
    }
}
