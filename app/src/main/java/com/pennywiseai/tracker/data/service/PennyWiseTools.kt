package com.pennywiseai.tracker.data.service

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
    @Tool(description = "Record a transaction the user is telling you about: money they spent, paid, bought, or received.")
    fun addTransaction(
        @ToolParam(description = "Amount of money as a number, without currency symbol") amount: Double,
        @ToolParam(description = "The shop, service or person the money went to or came from. Never a bank or card.") merchant: String,
        @ToolParam(description = "One of the known category names") category: String,
        @ToolParam(description = "EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback") type: String,
        @ToolParam(description = "The bank, card or cash the user mentioned, or empty if not mentioned") account: String
    ): String = ""

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

    @Tool(description = "Look up how much the user has spent in a category this month.")
    fun spendingByCategory(
        @ToolParam(description = "One of the known category names") category: String
    ): String = ""

    companion object {
        const val ADD_TRANSACTION = "add_transaction"
        const val SPENDING_BY_CATEGORY = "spending_by_category"
        const val DELETE_TRANSACTION = "delete_transaction"
        const val UPDATE_TRANSACTION = "update_transaction"
    }
}
