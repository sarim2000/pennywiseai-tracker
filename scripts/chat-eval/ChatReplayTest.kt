package com.pennywiseai.tracker.data.model

import com.google.gson.Gson
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Not part of the app's tests. Puts a run's raw tool calls through the app's own TransactionDraft, so
 * score.py measures what the card would show. See README.md. Reads CHAT_REPLAY_CASES and CHAT_REPLAY_IN,
 * writes CHAT_REPLAY_OUT.
 */
class ChatReplayTest {
    private val categories = (listOf("Food & Dining", "Groceries", "Transportation", "Shopping", "Others").map { CategoryEntity(name = it, color = "#000") }
        + listOf("Salary", "Income").map { CategoryEntity(name = it, color = "#000", isIncome = true) })

    @Test
    fun replay() {
        val gson = Gson()
        val today = LocalDate.now()
        val cases = (gson.fromJson(File(System.getenv("CHAT_REPLAY_CASES")).readText(), Map::class.java)["cases"] as List<*>)
            .map { it as Map<*, *> }.associateBy { it["id"] as String }
        File(System.getenv("CHAT_REPLAY_OUT")).printWriter().use { out ->
            File(System.getenv("CHAT_REPLAY_IN")).forEachLine { line ->
                @Suppress("UNCHECKED_CAST") val row = gson.fromJson(line, Map::class.java) as MutableMap<String, Any?>
                val case = cases[row["id"]] ?: return@forEachLine
                val base = case["base"] as? String ?: "INR"
                row["replayed"] = true
                row["calls"] = (row["calls"] as List<*>).mapNotNull { c ->
                    @Suppress("UNCHECKED_CAST") val call = c as Map<String, Any?>
                    if (call["name"] != "add_transaction") return@mapNotNull call
                    @Suppress("UNCHECKED_CAST") val args = call["args"] as Map<String, Any?>
                    val draft = TransactionDraft.fromToolArgs(args, case["text"] as String, categories, emptyList(), base, today) { null }
                        ?: return@mapNotNull null
                    mapOf("name" to "add_transaction", "args" to mapOf(
                        "amount" to draft.amount.toPlainString(), "merchant" to draft.merchant, "category" to draft.category,
                        "type" to draft.type.name, "currency" to draft.currency,
                        "days_ago" to ChronoUnit.DAYS.between(draft.date, today), "flagged" to !draft.amountInMessage
                    ))
                }
                out.println(gson.toJson(row))
            }
        }
    }
}
