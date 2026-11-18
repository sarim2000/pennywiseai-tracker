package com.pennywiseai.tracker.data.service

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Tools offered to the on-device model in Chat (#170/#767). The runtime only
 * uses these for their schema: conversations are created with
 * automaticToolCalling = false, so the bodies below never execute — the app
 * intercepts the call and asks the user before writing anything.
 */
@Suppress("unused")
class PennyWiseTools : ToolSet {
    @Tool(description = "Delete a transaction the user recorded earlier, e.g. 'delete the starbucks coffee from yesterday'.")
    fun deleteTransaction(
        @ToolParam(description = "Merchant, shop or description words the user used to identify it") merchant: String,
        @ToolParam(description = "Amount if the user mentioned one, else 0") amount: Double,
        @ToolParam(description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned") daysAgo: Int
    ): String = ""

    @Tool(description = "Change the category or merchant name of a transaction the user recorded earlier, e.g. 'the uber yesterday was actually Transportation'.")
    fun updateTransaction(
        @ToolParam(description = "Merchant, shop or description words the user used to identify it") merchant: String,
        @ToolParam(description = "Amount if the user mentioned one, else 0") amount: Double,
        @ToolParam(description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned") daysAgo: Int,
        @ToolParam(description = "The new category, or empty to keep it") newCategory: String,
        @ToolParam(description = "The new merchant name, or empty to keep it") newMerchant: String
    ): String = ""

    @Tool(description = "Look up how much the user spent or received: a total, or the biggest categories, merchants or accounts. The app works out the figures.")
    fun querySpending(
        @ToolParam(description = "this_month, last_month, this_week, last_week, today, yesterday, a month as YYYY-MM or a year as YYYY; empty for this month") period: String,
        @ToolParam(description = "First day as YYYY-MM-DD only when the user gave dates, else empty") from: String,
        @ToolParam(description = "Last day as YYYY-MM-DD, or empty for today") to: String,
        @ToolParam(description = "One of the known category names, or empty for all spending") category: String,
        @ToolParam(description = "Merchant or shop name the user asked about, or empty") merchant: String,
        @ToolParam(description = "none for a total, or category, merchant or account for the biggest ones") groupBy: String
    ): String = ""

    @Tool(description = "Look up the user's plans: how much is left of their budgets, or the bills due this week and any overdue.")
    fun planningStatus(
        @ToolParam(description = "budget or bills") kind: String
    ): String = ""

    @Tool(description = "Compare the user's spending so far this month with last month, or this week with last week. The app works out the figures.")
    fun compareSpending(
        @ToolParam(description = "month or week") period: String,
        @ToolParam(description = "One of the known category names, or empty for all spending") category: String
    ): String = ""

    companion object {
        const val COMPARE_SPENDING = "compare_spending"
        const val ADD_TRANSACTIONS = "add_transactions"
        const val QUERY_SPENDING = "query_spending"
        const val PLANNING_STATUS = "planning_status"
        const val DELETE_TRANSACTION = "delete_transaction"
        const val UPDATE_TRANSACTION = "update_transaction"
    }
}

/**
 * One call with every transaction of a message. Qwen2.5 merges amounts far less often this way
 * than with one call per transaction, and the array needs a hand-written schema. Never executed,
 * like the tools above.
 */
class AddTransactionsTool : OpenApiTool {
    override fun getToolDescriptionJsonString() = """
        {"name":"${PennyWiseTools.ADD_TRANSACTIONS}","description":"Record the transactions the user is telling you about: money they spent, paid, bought, or received.","parameters":{"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{
        "amount":{"type":"number","description":"Amount of money as a number, without currency symbol"},
        "merchant":{"type":"string","description":"The shop, service or person the money went to or came from. Never a bank or card."},
        "category":{"type":"string","description":"One of the known category names"},
        "type":{"type":"string","description":"EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback"},
        "account":{"type":"string","description":"The bank, card or cash the user mentioned, or empty if not mentioned"},
        "currency":{"type":"string","description":"ISO currency code if the user named a currency, e.g. USD, or empty if not mentioned"},
        "days_ago":{"type":"integer","description":"How many days ago: 0 for today or if not mentioned, 1 for yesterday"}
        },"required":["amount","merchant","category","type","account","currency","days_ago"]}}},"required":["items"]}}
    """.trimIndent()

    override fun execute(paramsJsonString: String) = ""
}
