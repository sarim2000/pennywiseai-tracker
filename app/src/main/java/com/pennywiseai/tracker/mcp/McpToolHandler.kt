package com.pennywiseai.tracker.mcp

import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolHandler @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountBalanceDao: AccountBalanceDao,
    private val subscriptionDao: SubscriptionDao
) {

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = "pennywise-mcp", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )

        registerTools(server)
        return server
    }

    private fun registerTools(server: Server) {
        server.addTool(
            name = "get_transactions",
            description = "Fetch transactions with optional filters. Returns transaction details including amount, merchant, category, type, date, and bank.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("date_from") {
                        put("type", "string")
                        put("description", "Start date inclusive (YYYY-MM-DD). Defaults to 30 days ago.")
                    }
                    putJsonObject("date_to") {
                        put("type", "string")
                        put("description", "End date inclusive (YYYY-MM-DD). Defaults to today.")
                    }
                    putJsonObject("category") {
                        put("type", "string")
                        put("description", "Filter by category name (e.g. Food & Dining, Shopping)")
                    }
                    putJsonObject("type") {
                        put("type", "string")
                        put("description", "Filter by transaction type: INCOME, EXPENSE, CREDIT, TRANSFER, or INVESTMENT")
                    }
                    putJsonObject("merchant") {
                        put("type", "string")
                        put("description", "Filter by merchant name (partial match)")
                    }
                    putJsonObject("min_amount") {
                        put("type", "number")
                        put("description", "Minimum transaction amount")
                    }
                    putJsonObject("max_amount") {
                        put("type", "number")
                        put("description", "Maximum transaction amount")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results to return (default 50, max 200)")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Number of results to skip for pagination")
                    }
                }
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { request ->
            getTransactions(request.arguments)
        }

        server.addTool(
            name = "get_spending_summary",
            description = "Get aggregated spending totals grouped by category or merchant for a date range. Useful for understanding spending patterns.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("date_from") {
                        put("type", "string")
                        put("description", "Start date inclusive (YYYY-MM-DD)")
                    }
                    putJsonObject("date_to") {
                        put("type", "string")
                        put("description", "End date inclusive (YYYY-MM-DD)")
                    }
                    putJsonObject("group_by") {
                        put("type", "string")
                        put("description", "Group results by 'category' (default) or 'merchant'")
                    }
                },
                required = listOf("date_from", "date_to")
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { request ->
            getSpendingSummary(request.arguments)
        }

        server.addTool(
            name = "get_categories",
            description = "List all transaction categories with their type (income/expense).",
            inputSchema = ToolSchema(properties = buildJsonObject {}),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { _ ->
            getCategories()
        }

        server.addTool(
            name = "search_transactions",
            description = "Full-text search across transaction merchant names, descriptions, and SMS bodies.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query string")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results to return (default 20, max 100)")
                    }
                },
                required = listOf("query")
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { request ->
            searchTransactions(request.arguments)
        }

        server.addTool(
            name = "get_balance_overview",
            description = "Get current balances across all bank accounts. Shows bank name, account, balance, and currency.",
            inputSchema = ToolSchema(properties = buildJsonObject {}),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { _ ->
            getBalanceOverview()
        }

        server.addTool(
            name = "get_subscriptions",
            description = "Get active subscriptions and recurring payments with amounts and next payment dates.",
            inputSchema = ToolSchema(properties = buildJsonObject {}),
            toolAnnotations = ToolAnnotations(readOnlyHint = true)
        ) { _ ->
            getSubscriptions()
        }
    }

    private suspend fun getTransactions(args: Map<String, kotlinx.serialization.json.JsonElement>?): CallToolResult {
        val dateFrom = args?.get("date_from")?.jsonPrimitive?.content
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.now().minusDays(30)
        val dateTo = args?.get("date_to")?.jsonPrimitive?.content
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.now()
        val category = args?.get("category")?.jsonPrimitive?.content
        val type = args?.get("type")?.jsonPrimitive?.content
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        val merchant = args?.get("merchant")?.jsonPrimitive?.content
        val minAmount = args?.get("min_amount")?.jsonPrimitive?.content?.toDoubleOrNull()
        val maxAmount = args?.get("max_amount")?.jsonPrimitive?.content?.toDoubleOrNull()
        val limit = (args?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 50).coerceIn(1, 200)
        val offset = (args?.get("offset")?.jsonPrimitive?.content?.toIntOrNull() ?: 0).coerceAtLeast(0)

        val startDateTime = dateFrom.atStartOfDay()
        val endDateTime = dateTo.atTime(LocalTime.MAX)

        val transactions = transactionDao.getTransactionsBetweenDatesList(startDateTime, endDateTime)
            .asSequence()
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .filter { type == null || it.transactionType == type }
            .filter { merchant == null || it.merchantName.contains(merchant, ignoreCase = true) }
            .filter { minAmount == null || it.amount.toDouble() >= minAmount }
            .filter { maxAmount == null || it.amount.toDouble() <= maxAmount }
            .drop(offset)
            .take(limit)
            .toList()

        val result = buildJsonObject {
            put("total_matching", transactions.size)
            put("date_range", buildJsonObject {
                put("from", dateFrom.toString())
                put("to", dateTo.toString())
            })
            put("transactions", buildJsonArray {
                transactions.forEach { tx ->
                    add(buildJsonObject {
                        put("id", tx.id)
                        put("amount", tx.amount.toDouble())
                        put("merchant", tx.merchantName)
                        put("category", tx.category)
                        put("type", tx.transactionType.name)
                        put("date", tx.dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        put("currency", tx.currency)
                        tx.bankName?.let { put("bank", it) }
                        tx.accountNumber?.let { put("account", it) }
                        tx.description?.let { put("description", it) }
                        tx.reference?.let { put("reference", it) }
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private suspend fun getSpendingSummary(args: Map<String, kotlinx.serialization.json.JsonElement>?): CallToolResult {
        val dateFrom = args?.get("date_from")?.jsonPrimitive?.content
            ?.let { LocalDate.parse(it) }
            ?: return CallToolResult(
                content = listOf(TextContent(text = "date_from is required")),
                isError = true
            )
        val dateTo = args?.get("date_to")?.jsonPrimitive?.content
            ?.let { LocalDate.parse(it) }
            ?: return CallToolResult(
                content = listOf(TextContent(text = "date_to is required")),
                isError = true
            )
        val groupBy = args?.get("group_by")?.jsonPrimitive?.content ?: "category"

        val startDateTime = dateFrom.atStartOfDay()
        val endDateTime = dateTo.atTime(LocalTime.MAX)

        val transactions = transactionDao.getTransactionsBetweenDatesList(startDateTime, endDateTime)
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }

        val totalSpent = transactions.sumOf { it.amount.toDouble() }

        val grouped = when (groupBy) {
            "merchant" -> transactions.groupBy { it.merchantName }
            else -> transactions.groupBy { it.category }
        }

        val summaries = grouped.map { (key, txs) ->
            Triple(key, txs.sumOf { it.amount.toDouble() }, txs.size)
        }.sortedByDescending { it.second }

        val result = buildJsonObject {
            put("date_range", buildJsonObject {
                put("from", dateFrom.toString())
                put("to", dateTo.toString())
            })
            put("total_spent", totalSpent)
            put("transaction_count", transactions.size)
            put("group_by", groupBy)
            put("groups", buildJsonArray {
                summaries.forEach { (name, total, count) ->
                    add(buildJsonObject {
                        put("name", name)
                        put("total", total)
                        put("count", count)
                        put("percentage", if (totalSpent > 0) (total / totalSpent * 100) else 0.0)
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private suspend fun getCategories(): CallToolResult {
        val categories = categoryDao.getAllCategories().first()
        val result = buildJsonObject {
            put("categories", buildJsonArray {
                categories.forEach { cat ->
                    add(buildJsonObject {
                        put("name", cat.name)
                        put("type", if (cat.isIncome) "income" else "expense")
                        put("color", cat.color)
                        put("is_system", cat.isSystem)
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private suspend fun searchTransactions(args: Map<String, kotlinx.serialization.json.JsonElement>?): CallToolResult {
        val query = args?.get("query")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(TextContent(text = "query is required")),
                isError = true
            )
        val limit = (args?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 20).coerceIn(1, 100)

        val transactions = transactionDao.searchTransactions(query).first().take(limit)

        val result = buildJsonObject {
            put("query", query)
            put("result_count", transactions.size)
            put("transactions", buildJsonArray {
                transactions.forEach { tx ->
                    add(buildJsonObject {
                        put("id", tx.id)
                        put("amount", tx.amount.toDouble())
                        put("merchant", tx.merchantName)
                        put("category", tx.category)
                        put("type", tx.transactionType.name)
                        put("date", tx.dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        put("currency", tx.currency)
                        tx.bankName?.let { put("bank", it) }
                        tx.description?.let { put("description", it) }
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private suspend fun getBalanceOverview(): CallToolResult {
        val balances = accountBalanceDao.getAllLatestBalances().first()
        val totalBalance = balances.sumOf { it.balance.toDouble() }

        val result = buildJsonObject {
            put("total_balance", totalBalance)
            put("account_count", balances.size)
            put("accounts", buildJsonArray {
                balances.forEach { bal ->
                    add(buildJsonObject {
                        put("bank", bal.bankName)
                        put("account_last4", bal.accountLast4)
                        put("balance", bal.balance.toDouble())
                        put("currency", bal.currency)
                        put("is_credit_card", bal.isCreditCard)
                        bal.creditLimit?.let { put("credit_limit", it.toDouble()) }
                        bal.accountType?.let { put("account_type", it) }
                        put("last_updated", bal.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private suspend fun getSubscriptions(): CallToolResult {
        val subscriptions = subscriptionDao.getSubscriptionsByStateList(SubscriptionState.ACTIVE)
        val totalMonthly = subscriptions.sumOf { it.amount.toDouble() }

        val result = buildJsonObject {
            put("total_monthly", totalMonthly)
            put("subscription_count", subscriptions.size)
            put("subscriptions", buildJsonArray {
                subscriptions.forEach { sub ->
                    add(buildJsonObject {
                        put("merchant", sub.merchantName)
                        put("amount", sub.amount.toDouble())
                        put("currency", sub.currency)
                        sub.nextPaymentDate?.let { put("next_payment", it.toString()) }
                        sub.category?.let { put("category", it) }
                        sub.bankName?.let { put("bank", it) }
                    })
                }
            })
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }
}
