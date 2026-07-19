package com.pennywiseai.tracker.data.csv

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDateTime

class CsvTransactionImporterTest {

    private val importer = CsvTransactionImporter()

    private val header =
        "Date,Time,Merchant,Category,Type,Amount,Currency,Bank,Account,Balance After,Description,SMS Body"

    @Test
    fun `parses valid rows into transactions`() {
        val csv = buildString {
            appendLine(header)
            appendLine("2026-01-15,09:30:00,Coffee Shop,Food,Expense,250.00,INR,DemoBank,1234,4750.00,Morning coffee,")
            appendLine("2026-01-16,18:00:00,Employer,Income,Income,50000.00,INR,DemoBank,1234,54750.00,Salary,")
            appendLine("2026-01-17,12:00:00,Broker,Investments,Investment,1000.00,USD,DemoBank,9876,,,")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(0, result.failedCount)
        assertEquals(3, result.transactions.size)

        val expense = result.transactions[0]
        assertEquals(BigDecimal("250.00"), expense.amount)
        assertEquals(TransactionType.EXPENSE, expense.transactionType)
        assertEquals("Coffee Shop", expense.merchantName)
        assertEquals("INR", expense.currency)
        assertEquals(LocalDateTime.of(2026, 1, 15, 9, 30, 0), expense.dateTime)
        assertEquals("DemoBank", expense.bankName)
        assertEquals("1234", expense.accountNumber)
        assertEquals(BigDecimal("4750.00"), expense.balanceAfter)
        assertNull(expense.smsBody)

        val income = result.transactions[1]
        assertEquals(BigDecimal("50000.00"), income.amount)
        assertEquals(TransactionType.INCOME, income.transactionType)
        assertEquals("Employer", income.merchantName)

        // Currency must be per-row, never assumed to be a single currency.
        val investment = result.transactions[2]
        assertEquals(TransactionType.INVESTMENT, investment.transactionType)
        assertEquals("USD", investment.currency)
        // Missing Time falls back to midnight; missing balance/description -> null.
        assertNull(investment.balanceAfter)
        assertNull(investment.description)
    }

    @Test
    fun `bad amount row is counted as failed not crashed`() {
        val csv = buildString {
            appendLine(header)
            appendLine("2026-01-15,09:30:00,Coffee Shop,Food,Expense,250.00,INR,DemoBank,1234,,,")
            appendLine("2026-01-16,10:00:00,Broken,Food,Expense,not-a-number,INR,DemoBank,1234,,,")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(1, result.transactions.size)
        assertEquals(1, result.failedCount)
        assertTrue(result.failureReasons.any { it.contains("Amount") })
    }

    @Test
    fun `non-positive amount row is rejected as failed`() {
        // Amounts must be positive (direction is carried by Type). A signed-debit
        // CSV must fail the row rather than silently corrupt analytics totals.
        val csv = buildString {
            appendLine(header)
            appendLine("2026-01-15,09:30:00,Ok,Food,Expense,250.00,INR,DemoBank,1234,,,")
            appendLine("2026-01-16,10:00:00,Negative,Food,Expense,-250.00,INR,DemoBank,1234,,,")
            appendLine("2026-01-17,10:00:00,Zero,Food,Expense,0,INR,DemoBank,1234,,,")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(1, result.transactions.size)
        assertEquals(2, result.failedCount)
        assertTrue(result.failureReasons.all { it.contains("Amount") })
    }

    @Test
    fun `type labels and bare enum names map back case-insensitively`() {
        val csv = buildString {
            appendLine(header)
            appendLine("2026-01-15,09:30:00,Card Merchant,Shopping,Credit Card,99.00,INR,DemoBank,1234,,,")
            appendLine("2026-01-16,09:30:00,Enum Merchant,Shopping,credit,99.00,INR,DemoBank,1234,,,")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(2, result.transactions.size)
        assertEquals(TransactionType.CREDIT, result.transactions[0].transactionType)
        assertEquals(TransactionType.CREDIT, result.transactions[1].transactionType)
    }

    @Test
    fun `column order and extra columns are tolerated via header matching`() {
        // Reordered columns plus an unknown extra column.
        val reorderedHeader = "Amount,Type,Date,Merchant,ExtraCol,Currency"
        val csv = buildString {
            appendLine(reorderedHeader)
            appendLine("500.00,Expense,2026-02-01,Reordered Merchant,ignore-me,EUR")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(0, result.failedCount)
        assertEquals(1, result.transactions.size)
        val txn = result.transactions[0]
        assertEquals(BigDecimal("500.00"), txn.amount)
        assertEquals(TransactionType.EXPENSE, txn.transactionType)
        assertEquals("Reordered Merchant", txn.merchantName)
        assertEquals("EUR", txn.currency)
        // Missing Category/Bank -> defaults.
        assertEquals("Others", txn.category)
        assertEquals("Imported", txn.bankName)
    }

    @Test
    fun `identical rows produce identical dedup hashes`() {
        val csv = buildString {
            appendLine(header)
            appendLine("2026-01-15,09:30:00,Coffee Shop,Food,Expense,250.00,INR,DemoBank,1234,,,")
            appendLine("2026-01-15,09:30:00,Coffee Shop,Food,Expense,250.00,INR,DemoBank,1234,,,")
        }

        val result = importer.parse(StringReader(csv))

        assertEquals(2, result.transactions.size)
        assertNotNull(result.transactions[0].transactionHash)
        assertEquals(
            result.transactions[0].transactionHash,
            result.transactions[1].transactionHash
        )
    }
}
