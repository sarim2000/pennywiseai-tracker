package com.pennywiseai.tracker.data.export

import com.opencsv.CSVWriter
import com.pennywiseai.tracker.data.csv.CsvTransactionImporter
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDateTime

class CsvExporterTest {

    private val exporter = CsvExporter(mockk(), mockk())

    private fun txn(merchant: String, reference: String?) = TransactionEntity(
        amount = BigDecimal("250.00"),
        merchantName = merchant,
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 1, 15, 9, 30),
        currency = "INR",
        reference = reference,
        transactionHash = "h-$merchant"
    )

    @Test
    fun `rows carry the merchant's display alias and the reference, under their headers`() {
        val aliases = mapOf("SWIGGY*BLR" to "Swiggy")
        val header = CsvExporter.CSV_HEADER.toList()

        val aliased = exporter.csvRow(txn("SWIGGY*BLR", "512345678901"), aliases)
        assertEquals("Swiggy", aliased[header.indexOf("Display Alias")])
        assertEquals("512345678901", aliased[header.indexOf("Reference")])

        // No alias set and no reference parsed: empty cells, not "null".
        val plain = exporter.csvRow(txn("Corner Store", null), aliases)
        assertEquals("", plain[header.indexOf("Display Alias")])
        assertEquals("", plain[header.indexOf("Reference")])
        assertEquals(header.size, plain.size)
    }

    @Test
    fun `an export with the new columns still imports`() {
        val out = StringWriter()
        CSVWriter(out).use {
            it.writeNext(CsvExporter.CSV_HEADER)
            it.writeNext(exporter.csvRow(txn("SWIGGY*BLR", "512345678901"), mapOf("SWIGGY*BLR" to "Swiggy")))
        }

        val result = CsvTransactionImporter().parse(StringReader(out.toString()), "INR")

        assertEquals(0, result.failedCount)
        assertEquals("SWIGGY*BLR", result.transactions.single().merchantName)
        assertEquals(BigDecimal("250.00"), result.transactions.single().amount)
    }
}
