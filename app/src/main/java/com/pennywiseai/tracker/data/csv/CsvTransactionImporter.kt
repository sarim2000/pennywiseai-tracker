package com.pennywiseai.tracker.data.csv

import com.opencsv.CSVReader
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.io.Reader
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses PennyWise's own CSV export format (the 12 columns [CsvExporter] writes)
 * back into [TransactionEntity] rows, so historical data can be re-imported and
 * an export can round-trip.
 *
 * Pure/testable: takes a [Reader] (no Android `Uri`), so it can be exercised from
 * a plain JUnit test with a `StringReader`. The Android side (opening the picked
 * document, dedup against the DB, insertion) lives in `ImportCsvUseCase`.
 *
 * Columns are matched **by header name**, so extra columns or a different column
 * order are tolerated. Only Date, Amount and Type are required; missing optional
 * columns become null/empty (with sensible defaults matching manual entry).
 */
@Singleton
class CsvTransactionImporter @Inject constructor() {

    companion object {
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        // Header labels as written by CsvExporter (matched case-insensitively).
        private const val COL_DATE = "Date"
        private const val COL_TIME = "Time"
        private const val COL_MERCHANT = "Merchant"
        private const val COL_CATEGORY = "Category"
        private const val COL_TYPE = "Type"
        private const val COL_AMOUNT = "Amount"
        private const val COL_CURRENCY = "Currency"
        private const val COL_BANK = "Bank"
        private const val COL_ACCOUNT = "Account"
        private const val COL_BALANCE = "Balance After"
        private const val COL_DESCRIPTION = "Description"

        private const val DEFAULT_BANK = "Imported"
        private const val DEFAULT_CATEGORY = "Others"
        private const val DEFAULT_CURRENCY = "INR"
    }

    /**
     * Result of parsing a CSV: the successfully parsed [transactions] plus a count
     * (and reasons) for rows that could not be parsed. Nothing is inserted here.
     */
    data class ImportParseResult(
        val transactions: List<TransactionEntity>,
        val failedCount: Int,
        val failureReasons: List<String> = emptyList()
    )

    /**
     * Parses the given CSV [reader]. The first row must be the header; column
     * indices are resolved from it by name. Rows that are blank, or missing a
     * required field, or malformed, are counted as failed rather than throwing.
     */
    fun parse(reader: Reader): ImportParseResult {
        val transactions = mutableListOf<TransactionEntity>()
        val failureReasons = mutableListOf<String>()
        var failedCount = 0

        CSVReader(reader).use { csvReader ->
            val header = csvReader.readNext()
                ?: return ImportParseResult(emptyList(), 0)

            // Map normalized header label -> column index.
            val columnIndex = HashMap<String, Int>()
            header.forEachIndexed { index, name ->
                columnIndex[name.trim().lowercase()] = index
            }

            fun indexOf(label: String): Int? = columnIndex[label.lowercase()]

            val dateIdx = indexOf(COL_DATE)
            val amountIdx = indexOf(COL_AMOUNT)
            val typeIdx = indexOf(COL_TYPE)

            if (dateIdx == null || amountIdx == null || typeIdx == null) {
                val missing = buildList {
                    if (dateIdx == null) add(COL_DATE)
                    if (amountIdx == null) add(COL_AMOUNT)
                    if (typeIdx == null) add(COL_TYPE)
                }.joinToString(", ")
                return ImportParseResult(
                    transactions = emptyList(),
                    failedCount = 0,
                    failureReasons = listOf("Missing required column(s): $missing")
                )
            }

            val timeIdx = indexOf(COL_TIME)
            val merchantIdx = indexOf(COL_MERCHANT)
            val categoryIdx = indexOf(COL_CATEGORY)
            val currencyIdx = indexOf(COL_CURRENCY)
            val bankIdx = indexOf(COL_BANK)
            val accountIdx = indexOf(COL_ACCOUNT)
            val balanceIdx = indexOf(COL_BALANCE)
            val descriptionIdx = indexOf(COL_DESCRIPTION)

            var row = csvReader.readNext()
            var rowNumber = 1 // data rows, 1-based (header already consumed)
            while (row != null) {
                // Skip fully blank lines silently (OpenCSV can yield a single empty cell).
                if (row.all { it.isBlank() }) {
                    row = csvReader.readNext()
                    rowNumber++
                    continue
                }

                try {
                    transactions.add(parseRow(
                        row = row,
                        dateIdx = dateIdx,
                        timeIdx = timeIdx,
                        merchantIdx = merchantIdx,
                        categoryIdx = categoryIdx,
                        typeIdx = typeIdx,
                        amountIdx = amountIdx,
                        currencyIdx = currencyIdx,
                        bankIdx = bankIdx,
                        accountIdx = accountIdx,
                        balanceIdx = balanceIdx,
                        descriptionIdx = descriptionIdx
                    ))
                } catch (e: Exception) {
                    failedCount++
                    failureReasons.add("Row $rowNumber: ${e.message}")
                }

                row = csvReader.readNext()
                rowNumber++
            }
        }

        return ImportParseResult(transactions, failedCount, failureReasons)
    }

    private fun parseRow(
        row: Array<String>,
        dateIdx: Int,
        timeIdx: Int?,
        merchantIdx: Int?,
        categoryIdx: Int?,
        typeIdx: Int,
        amountIdx: Int,
        currencyIdx: Int?,
        bankIdx: Int?,
        accountIdx: Int?,
        balanceIdx: Int?,
        descriptionIdx: Int?
    ): TransactionEntity {
        val dateText = row.cell(dateIdx)
            ?: throw IllegalArgumentException("Missing Date")
        val date = try {
            LocalDate.parse(dateText, dateFormatter)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Date '$dateText'")
        }

        val timeText = row.cell(timeIdx)
        val time = if (timeText.isNullOrBlank()) {
            LocalTime.MIDNIGHT
        } else {
            try {
                LocalTime.parse(timeText, timeFormatter)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Time '$timeText'")
            }
        }
        val dateTime = LocalDateTime.of(date, time)

        val amountText = row.cell(amountIdx)
            ?: throw IllegalArgumentException("Missing Amount")
        val amount = try {
            BigDecimal(amountText.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Amount '$amountText'")
        }
        // Amounts are always positive; direction is carried by Type (matching the
        // export). Reject non-positive values so a hand-crafted CSV using signed
        // debits can't silently corrupt analytics totals — fail the row instead.
        if (amount.signum() <= 0) {
            throw IllegalArgumentException("Amount must be positive: '$amountText'")
        }

        val typeText = row.cell(typeIdx)
            ?: throw IllegalArgumentException("Missing Type")
        val type = parseType(typeText)
            ?: throw IllegalArgumentException("Unknown Type '$typeText'")

        val merchant = row.cell(merchantIdx)?.takeIf { it.isNotBlank() } ?: "Unknown"
        val category = row.cell(categoryIdx)?.takeIf { it.isNotBlank() } ?: DEFAULT_CATEGORY
        val currency = row.cell(currencyIdx)?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY
        val bank = row.cell(bankIdx)?.takeIf { it.isNotBlank() } ?: DEFAULT_BANK
        val account = row.cell(accountIdx)?.takeIf { it.isNotBlank() }
        val description = row.cell(descriptionIdx)?.takeIf { it.isNotBlank() }
        val balanceAfter = row.cell(balanceIdx)?.takeIf { it.isNotBlank() }?.let {
            try {
                BigDecimal(it.trim())
            } catch (e: Exception) {
                null // balance is best-effort; a bad value shouldn't fail the row
            }
        }

        return TransactionEntity(
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = dateTime,
            description = description,
            smsBody = null, // null indicates a non-SMS (imported/manual) entry
            bankName = bank,
            smsSender = null,
            accountNumber = account,
            balanceAfter = balanceAfter,
            transactionHash = generateImportHash(
                dateTime = dateTime,
                merchant = merchant,
                amount = amount,
                type = type,
                currency = currency,
                account = account
            ),
            currency = currency,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * Maps a Type label back to [TransactionType]. Accepts the export labels
     * ("Income", "Expense", "Credit Card", "Transfer", "Investment") as well as
     * the bare enum names ("CREDIT", etc.), case-insensitively.
     */
    private fun parseType(raw: String): TransactionType? {
        return when (raw.trim().lowercase()) {
            "income" -> TransactionType.INCOME
            "expense" -> TransactionType.EXPENSE
            "credit card", "credit" -> TransactionType.CREDIT
            "transfer" -> TransactionType.TRANSFER
            "investment" -> TransactionType.INVESTMENT
            else -> null
        }
    }

    /**
     * Deterministic dedup hash from the row's content, so re-importing the same
     * CSV skips duplicates. Mirrors the MD5 style of
     * `AddTransactionUseCase.generateManualTransactionHash`.
     */
    private fun generateImportHash(
        dateTime: LocalDateTime,
        merchant: String,
        amount: BigDecimal,
        type: TransactionType,
        currency: String,
        account: String?
    ): String {
        val date = dateTime.toLocalDate()
        val time = dateTime.toLocalTime()
        val data = "CSV_v1_${date}T${time}_${merchant}_${amount}_${type}_${currency}_${account ?: ""}"
        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun Array<String>.cell(index: Int?): String? {
        if (index == null || index >= size) return null
        return this[index].trim().takeIf { it.isNotEmpty() }
    }
}
