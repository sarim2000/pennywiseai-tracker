package com.pennywiseai.tracker.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.pennywiseai.tracker.data.database.converter.Converters
import com.pennywiseai.tracker.data.ledger.signedAccountSum
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A manual account's balance stopped counting rows in other currencies. Its
 * opening was back-solved under the old sum, so [PennyWiseMigrations.MIGRATION_73_74]
 * moves it by those rows: the balance the user set before the update is the one
 * shown after it. Runs on a real SQLite, over the tables as version 73 creates them.
 */
class ManualOpeningCurrencyMigrationTest {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")
    private val converters = Converters()
    private val day = LocalDateTime.of(2026, 1, 15, 12, 0)
    private var nextId = 1L

    @Before
    fun createTables() {
        val dir = listOf("schemas", "app/schemas")
            .map { File(it, "com.pennywiseai.tracker.data.database.PennyWiseDatabase") }
            .first { it.isDirectory }
        Json.parseToJsonElement(File(dir, "73.json").readText()).jsonObject["database"]!!.jsonObject["entities"]!!
            .jsonArray.map { it.jsonObject }
            .filter { it["tableName"]!!.jsonPrimitive.content in setOf("transactions", "account_balances") }
            .forEach { table ->
                val name = table["tableName"]!!.jsonPrimitive.content
                connection.execSQL(table["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            }
    }

    @After
    fun close() = connection.close()

    @Test
    fun `a balance set before the update is the one shown after it`() {
        // A JOD cash account with JOD 40 and USD 7 spent and USD 20 moved in from another account.
        // The user set its balance to 560, so the old sum (-40 - 7 + 20) left an opening of 587.
        tx("EXPENSE", "40", "JOD", bank = CASH, account = "0001")
        val usdSpend = tx("EXPENSE", "7", "USD", bank = CASH, account = "0001")
        tx("TRANSFER", "20", "USD", bank = "Other Bank", account = "0002", from = "0002", to = "0001", toBank = CASH)
        tx("EXPENSE", "100", "USD", bank = CASH, account = "0001", deleted = true)
        val opening = balance(CASH, "0001", "OPENING", "587", "JOD")
        balance(CASH, "0001", "MANUAL", "560", "JOD")

        migrate()

        assertEquals("600", balanceOf(opening))
        assertEquals("560", shown(CASH, "0001"))

        // A later edit to one of those USD rows doesn't move the JOD balance.
        connection.execSQL("UPDATE transactions SET amount = '70' WHERE id = $usdSpend")
        assertEquals("560", shown(CASH, "0001"))
    }

    @Test
    fun `an account with every row in its own currency keeps its opening`() {
        tx("INCOME", "50", "JOD", bank = WALLET, account = "0003")
        val opening = balance(WALLET, "0003", "OPENING", "100", "JOD")

        migrate()

        assertEquals("100", balanceOf(opening))
    }

    @Test
    fun `an account with a row that can't be read is left as it was`() {
        tx("NOT_A_TYPE", "5", "USD", bank = WALLET, account = "0004")
        tx("EXPENSE", "9", "USD", bank = WALLET, account = "0004")
        val opening = balance(WALLET, "0004", "OPENING", "100", "JOD")

        migrate()

        assertEquals("100", balanceOf(opening))
    }

    private fun migrate() = runBlocking { PennyWiseMigrations.MIGRATION_73_74.migrate(connection) }

    /** What the account shows under the new rule: opening + the sum of its rows in its currency. */
    private fun shown(bank: String, account: String): String {
        val (balance, currency) = connection.prepare(
            "SELECT balance, currency FROM account_balances WHERE bank_name = ? AND account_last4 = ? AND source_type = 'OPENING'"
        ).use { it.bindText(1, bank); it.bindText(2, account); it.step(); it.getText(0) to it.getText(1) }
        val sum = with(PennyWiseMigrations) {
            signedAccountSum(
                bank,
                account,
                direct = connection.ledgerRows("bank_name = ? AND account_number = ?", bank, account),
                transfers = connection.ledgerRows(
                    "transaction_type = 'TRANSFER' AND (from_account = ? OR to_account = ?)", account, account
                )
            ) { it.currency == currency }
        }
        return (BigDecimal(balance) + sum).toPlainString()
    }

    private fun balanceOf(id: Long): String =
        connection.prepare("SELECT balance FROM account_balances WHERE id = ?").use {
            it.bindLong(1, id)
            it.step()
            BigDecimal(it.getText(0)).toPlainString()
        }

    private fun tx(
        type: String,
        amount: String,
        currency: String,
        bank: String,
        account: String,
        from: String? = null,
        to: String? = null,
        toBank: String? = null,
        deleted: Boolean = false,
    ): Long {
        val id = nextId++
        insert(
            "transactions",
            mapOf(
                "id" to id, "amount" to amount, "merchant_name" to "Synthetic", "category" to "Others",
                "transaction_type" to type, "date_time" to converters.fromLocalDateTime(day),
                "transaction_hash" to "hash-$id", "bank_name" to bank, "account_number" to account,
                "currency" to currency, "from_account" to from, "to_account" to to, "to_bank_name" to toBank,
                "is_deleted" to if (deleted) 1L else 0L, "is_recurring" to 0L,
                "created_at" to converters.fromLocalDateTime(day), "updated_at" to converters.fromLocalDateTime(day),
            ),
        )
        return id
    }

    private fun balance(bank: String, account: String, source: String, amount: String, currency: String): Long {
        val id = nextId++
        insert(
            "account_balances",
            mapOf(
                "id" to id, "bank_name" to bank, "account_last4" to account, "balance" to amount,
                "timestamp" to converters.fromLocalDateTime(if (source == "OPENING") day.minusDays(30) else day),
                "source_type" to source, "currency" to currency,
                "created_at" to converters.fromLocalDateTime(day),
            ),
        )
        return id
    }

    private fun insert(table: String, values: Map<String, Any?>) {
        connection.prepare(
            "INSERT INTO $table (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})"
        ).use { statement ->
            values.values.forEachIndexed { i, value ->
                when (value) {
                    null -> statement.bindNull(i + 1)
                    is Long -> statement.bindLong(i + 1, value)
                    else -> statement.bindText(i + 1, value.toString())
                }
            }
            statement.step()
        }
    }

    private companion object {
        const val CASH = "Cash"
        const val WALLET = "Wallet"
    }
}
