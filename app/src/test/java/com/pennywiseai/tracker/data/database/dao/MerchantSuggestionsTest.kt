package com.pennywiseai.tracker.data.database.dao

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.pennywiseai.tracker.data.model.MerchantSuggestion
import com.pennywiseai.tracker.data.model.MerchantSuggestions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The merchant suggestions under Add transaction's merchant field (#1392):
 * [MERCHANT_SUGGESTIONS_SQL] run on a real SQLite, over the tables as Room's latest exported schema creates them.
 */
class MerchantSuggestionsTest {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")
    private lateinit var tables: Map<String, JsonObject>
    private var nextId = 1
    private val day = LocalDateTime.of(2026, 1, 1, 12, 0)

    @Before
    fun createTables() {
        val dir = listOf("schemas", "app/schemas")
            .map { File(it, "com.pennywiseai.tracker.data.database.PennyWiseDatabase") }
            .first { it.isDirectory }
        val latest = dir.listFiles()!!.maxBy { it.nameWithoutExtension.toInt() }
        tables = Json.parseToJsonElement(latest.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .associateBy { it["tableName"]!!.jsonPrimitive.content }
        listOf("transactions", "account_balances", "merchant_aliases").forEach { name ->
            connection.execSQL(tables.getValue(name)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name))
        }
    }

    @After
    fun close() = connection.close()

    @Test
    fun `names that start with the text come before ones that only contain it`() {
        repeat(3) { tx("Corner Cafe") }
        tx("Cafe Nova")

        assertEquals(listOf("Cafe Nova", "Corner Cafe"), names(suggest("caf")))
    }

    @Test
    fun `then the most used, then the most recent`() {
        repeat(2) { tx("Bake House", at = day) }
        repeat(2) { tx("Bake Stop", at = day.plusDays(1)) }
        repeat(3) { tx("Bake Lab", at = day.minusDays(30)) }

        assertEquals(listOf("Bake Lab", "Bake Stop", "Bake House"), names(suggest("bake")))
        assertEquals(listOf(3, 2, 2), suggest("bake").map { it.uses })
    }

    @Test
    fun `at most the limit`() {
        (1..8).forEach { tx("Shop $it") }

        assertEquals(6, suggest("shop", limit = 6).size)
    }

    @Test
    fun `deleted rows and quick adds named after their category are left out`() {
        tx("Book Nook", deleted = true)
        tx("Books", category = "Books")
        tx("Bookshop")

        assertEquals(listOf("Bookshop"), names(suggest("book")))
    }

    @Test
    fun `spellings that differ only in case are one merchant, under the latest`() {
        tx("TEA ROOM", at = day)
        tx("Tea Room", at = day.plusDays(2))
        tx("tea room", at = day.plusDays(1))

        val found = suggest("tea")
        assertEquals(listOf("Tea Room"), names(found))
        assertEquals(3, found.single().uses)
    }

    @Test
    fun `an alias matches too, and is what the chip shows`() {
        tx("POS 4411 XYZ")
        alias("POS 4411 XYZ", "Corner Shop")
        repeat(2) { tx("Shop Corner") }

        val found = suggest("corner")
        // The alias starts with the text, the other name only contains it.
        assertEquals(listOf("POS 4411 XYZ", "Shop Corner"), names(found))
        assertEquals("Corner Shop", found.first().alias)
        assertEquals(null, found.last().alias)
    }

    @Test
    fun `an alias on one spelling names the whole merchant, all its use counted`() {
        tx("Tea Room", at = day)
        alias("Tea Room", "Cafe Mira")
        repeat(10) { tx("TEA ROOM", at = day.plusDays(1)) }

        listOf("mira", "tea").forEach { typed ->
            val found = suggest(typed).single()
            // The named spelling, though older, so the row saved with it keeps the alias.
            assertEquals("Tea Room", found.merchantName)
            assertEquals("Cafe Mira", found.alias)
            assertEquals(11, found.uses)
        }
    }

    @Test
    fun `only the profile in view, a row's own profile before its account's, Personal by default`() {
        account("Test Bank", "0001", profileId = 2)
        tx("Gym Work", profileId = 2)
        tx("Gym Account", bank = "Test Bank", last4 = "0001")
        tx("Gym Home")
        tx("Gym Moved", bank = "Test Bank", last4 = "0001", profileId = 1)

        assertEquals(setOf("Gym Work", "Gym Account"), names(suggest("gym", profileId = 2)).toSet())
        assertEquals(setOf("Gym Home", "Gym Moved"), names(suggest("gym", profileId = 1)).toSet())
        assertEquals(4, suggest("gym", profileId = null).size)
    }

    @Test
    fun `percent and underscore typed are matched literally`() {
        tx("50% Off Store")
        tx("500 Store")
        tx("A_B Mart")
        tx("AXB Mart")

        assertEquals(listOf("50% Off Store"), names(suggest("50%")))
        assertEquals(listOf("A_B Mart"), names(suggest("a_b")))
    }

    @Test
    fun `the name typed as is isn't offered again, a different case is`() {
        val found = listOf("Tea Room", "Tea House", "Teapot", "Tea Bar", "Tea Stall", "Tea Cart").map {
            MerchantSuggestion(it, alias = null, uses = 1, lastUsed = null)
        }

        assertEquals(listOf("Tea House", "Teapot", "Tea Bar", "Tea Stall", "Tea Cart"), names(MerchantSuggestions.shown(found, " Tea Room ")))
        assertEquals(MerchantSuggestions.LIMIT, MerchantSuggestions.shown(found, "tea room").size)
        assertEquals("Tea Room", MerchantSuggestions.shown(found, "tea room").first().merchantName)
    }

    private fun names(found: List<MerchantSuggestion>) = found.map { it.merchantName }

    /** Runs the DAO's SQL as Room would, its named arguments bound in place. */
    private fun suggest(typed: String, profileId: Long? = null, limit: Int = MerchantSuggestions.LIMIT): List<MerchantSuggestion> {
        val sql = MERCHANT_SUGGESTIONS_SQL.replace(":term", "?1").replace(":profileId", "?2").replace(":limit", "?3")
        val statement = connection.prepare(sql)
        try {
            statement.bindText(1, MerchantSuggestions.likeTerm(typed))
            if (profileId == null) statement.bindNull(2) else statement.bindLong(2, profileId)
            statement.bindLong(3, limit.toLong())
            val found = mutableListOf<MerchantSuggestion>()
            while (statement.step()) {
                found += MerchantSuggestion(
                    merchantName = statement.getText(0),
                    alias = if (statement.isNull(1)) null else statement.getText(1),
                    uses = statement.getLong(2).toInt(),
                    lastUsed = LocalDateTime.parse(statement.getText(3)),
                )
            }
            return found
        } finally {
            statement.close()
        }
    }

    private fun tx(
        merchant: String,
        category: String = "Food",
        at: LocalDateTime = day,
        deleted: Boolean = false,
        bank: String? = null,
        last4: String? = null,
        profileId: Long? = null,
    ) = insert(
        "transactions",
        mapOf(
            "id" to nextId++, "merchant_name" to merchant, "category" to category, "date_time" to at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "is_deleted" to if (deleted) 1 else 0, "bank_name" to bank, "account_number" to last4, "profile_id" to profileId,
            "transaction_hash" to "hash-$nextId",
        ),
    )

    private fun account(bank: String, last4: String, profileId: Long) =
        insert("account_balances", mapOf("bank_name" to bank, "account_last4" to last4, "profile_id" to profileId))

    private fun alias(merchant: String, alias: String) =
        insert("merchant_aliases", mapOf("merchant_name" to merchant, "alias" to alias))

    /** A row of [table] with [values], and a placeholder in every other required column. */
    private fun insert(table: String, values: Map<String, Any?>) {
        val required = tables.getValue(table)["fields"]!!.jsonArray.map { it.jsonObject }
            .filter { it["notNull"]?.jsonPrimitive?.content == "true" && it["defaultValue"] == null }
            .associate { field ->
                val placeholder: Any = if (field["affinity"]!!.jsonPrimitive.content == "INTEGER") 0 else "0"
                field["columnName"]!!.jsonPrimitive.content to placeholder
            }
            .filterKeys { it != "id" }
        val row = required + values
        val statement = connection.prepare(
            "INSERT INTO $table (${row.keys.joinToString { "`$it`" }}) VALUES (${row.keys.joinToString { "?" }})"
        )
        try {
            row.values.forEachIndexed { i, value ->
                when (value) {
                    null -> statement.bindNull(i + 1)
                    is Int -> statement.bindLong(i + 1, value.toLong())
                    is Long -> statement.bindLong(i + 1, value)
                    else -> statement.bindText(i + 1, value.toString())
                }
            }
            statement.step()
        } finally {
            statement.close()
        }
    }
}
