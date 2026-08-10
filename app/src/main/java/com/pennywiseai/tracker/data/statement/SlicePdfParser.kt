package com.pennywiseai.tracker.data.statement

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

/**
 * Parses slice small finance bank account statement PDFs.
 *
 * Android port of the KMP `SliceSharedStatementParser` (used by iOS) — same
 * semantics, expressed against the app's [PdfStatementParser] /
 * [ParsedTransaction] types. Statement shape after text extraction (all values
 * below are illustrative, not from any real statement):
 * ```
 * DATE DETAILS REF NO. AMOUNT BALANCE
 * 01 Jan '26 Interest Cr. for 31-Dec-2025 111122223333 ₹1.23 ₹10,001.23
 * 01 Jan '26 UPI-Debit-999900001111-SOME MERCHANT
 * NAME-ABCD0EFGH11-somevpa@bank-Paid Via ...
 * 2026010112345678 -₹500 ₹9,501.23
 * ```
 * A transaction starts with a `dd MMM 'yy` date. Short rows carry the
 * ref/amount/balance tail on the same line; UPI rows wrap the details over
 * several lines and put the tail on its own line, so lines are accumulated
 * until the tail appears.
 *
 * Slice-specific semantics: "atom" rows (pots), "Auto save" and "Round ups"
 * move money between the user's own slice balances — they are TRANSFERs, not
 * income/spending, so they never touch the Income/Expenses totals. Round-ups
 * in particular exist only in statements (the bank never texts them), which is
 * the whole reason slice statement import exists (#666).
 */
class SlicePdfParser : PdfStatementParser {

    companion object {
        private const val TAG = "SlicePdfParser"

        private val markers = listOf("slice small finance bank", "help@slice.bank.in")

        private val txnStartPattern =
            Regex("""^(\d{1,2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) '(\d{2})(?:\s|$)""")

        // "<ref digits> [-]₹amount ₹balance" at the end of an accumulated block.
        private val tailPattern =
            Regex("""(\d{6,})\s+(-?)₹([\d,]+(?:\.\d{1,2})?)(?!\d)\s+₹([\d,]+(?:\.\d{1,2})?)(?!\d)\s*$""")

        private val accountNumberPattern = Regex("""A/C number\s+(\d{6,})""")

        // IFSC-shaped token inside a UPI descriptor (AAAA0XXXXXX).
        private val ifscPattern = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""")

        // The summary row above the table also carries ₹ figures; real rows are
        // only read after this header.
        private const val TABLE_HEADER = "DATE DETAILS REF NO. AMOUNT BALANCE"
        private const val FOOTER_PREFIX = "Generated on"

        // Page furniture that repeats per page on multi-page statements. The
        // range header ("01 Jan '26 - 31 Jan '26") is the treacherous one — it
        // starts with a date, so without this check it would read as a new
        // transaction row and clobber a block awaiting its tail.
        private val pageMarkerPattern = Regex("""^\d+/\d+$""")
        private val rangeHeaderPattern =
            Regex("""^\d{1,2} [A-Z][a-z]{2} '\d{2} ?- ?\d{1,2} [A-Z][a-z]{2} '\d{2}$""")

        // Matches the SMS parser's getBankName() so statement rows land on the
        // same account as SMS-parsed slice transactions.
        private const val BANK_NAME = "Slice"

        // ₹1 crore, same sanity bound as the shared statement parsers.
        private val MAX_AMOUNT = BigDecimal(10_000_000)

        private val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
    }

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    override fun canHandle(text: String): Boolean {
        val lower = text.lowercase()
        val result = markers.any { it in lower }
        Log.d(TAG, "canHandle=$result")
        return result
    }

    override fun parse(text: String): List<ParsedTransaction> {
        Log.i(TAG, "Starting parse — text length=${text.length}")

        val accountLast4 = accountNumberPattern.find(text)
            ?.groupValues?.getOrNull(1)?.takeLast(4)

        val lines = text.lines()
        val headerIndex = lines.indexOfFirst { it.trim() == TABLE_HEADER }
        if (headerIndex == -1) {
            Log.w(TAG, "Table header not found — no transactions parsed")
            return emptyList()
        }

        val results = mutableListOf<ParsedTransaction>()
        var block: StringBuilder? = null

        fun tryFinalize() {
            val current = block?.toString()?.trim() ?: return
            parseBlock(current, accountLast4)?.let {
                results.add(it)
                block = null
            }
        }

        for (line in lines.drop(headerIndex + 1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // Page furniture (footer, help line, "2/2" marker, repeated range
            // and table headers) is skipped WITHOUT touching the open block: a
            // transaction can straddle a page boundary — details at the end of
            // one page, its amount tail after the next page's header — and
            // must keep accumulating across it.
            if (isPageFurniture(trimmed)) continue

            if (txnStartPattern.containsMatchIn(trimmed)) {
                // A new date row while a block is still open means the open
                // block never got its amount tail — drop it rather than guess.
                block = StringBuilder(trimmed)
            } else {
                block?.append(' ')?.append(trimmed)
            }
            tryFinalize()
        }

        Log.i(TAG, "Finished: ${results.size} transactions parsed")
        return results
    }

    private fun isPageFurniture(trimmed: String): Boolean =
        trimmed.startsWith(FOOTER_PREFIX) ||
            trimmed.startsWith("Need help?") ||
            trimmed == TABLE_HEADER ||
            pageMarkerPattern.matches(trimmed) ||
            rangeHeaderPattern.matches(trimmed)

    private fun parseBlock(block: String, accountLast4: String?): ParsedTransaction? {
        val start = txnStartPattern.find(block) ?: return null
        val tail = tailPattern.find(block) ?: return null
        if (tail.range.first <= start.range.last) return null

        val day = start.groupValues[1].toIntOrNull() ?: return null
        val month = MONTHS[start.groupValues[2].lowercase()] ?: return null
        val year = 2000 + (start.groupValues[3].toIntOrNull() ?: return null)

        val reference = tail.groupValues[1]
        val isDebit = tail.groupValues[2] == "-"
        val amount = tail.groupValues[3].replace(",", "").toBigDecimalOrNull() ?: return null
        if (amount.signum() <= 0 || amount > MAX_AMOUNT) return null
        val balance = tail.groupValues[4].replace(",", "").toBigDecimalOrNull()

        val details = block
            .substring(start.range.last + 1, tail.range.first)
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (details.isEmpty()) return null

        val (type, merchant) = classify(details, isDebit)

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant,
            reference = reference,
            accountLast4 = accountLast4,
            balance = balance,
            smsBody = block,
            sender = "Slice PDF",
            timestamp = istEpochMillis(year, month, day),
            bankName = BANK_NAME,
        )
    }

    private fun classify(details: String, isDebit: Boolean): Pair<TransactionType, String> = when {
        details.startsWith("Interest Cr.", ignoreCase = true) ->
            TransactionType.INCOME to "slice interest"

        details.startsWith("UPI-", ignoreCase = true) -> {
            val type = if (isDebit) TransactionType.EXPENSE else TransactionType.INCOME
            type to upiMerchant(details)
        }

        // Money moving between the user's own slice balances: pots ("atom"),
        // automatic saving, and round-ups. Never income or spending. Prefixes
        // are dropped by length (the startsWith already matched case-
        // insensitively) so "AUTO SAVE TO … ATOM" normalizes the same way.
        details.startsWith("Auto save to ", ignoreCase = true) ->
            TransactionType.TRANSFER to stripPotName(details.drop("Auto save to ".length))

        details.startsWith("Transfer from ", ignoreCase = true) && details.endsWith("atom", ignoreCase = true) ->
            TransactionType.TRANSFER to stripPotName(details.drop("Transfer from ".length))

        details.startsWith("Transfer to ", ignoreCase = true) && details.endsWith("atom", ignoreCase = true) ->
            TransactionType.TRANSFER to stripPotName(details.drop("Transfer to ".length))

        details.equals("Round ups", ignoreCase = true) ->
            TransactionType.TRANSFER to "Round ups"

        else ->
            (if (isDebit) TransactionType.EXPENSE else TransactionType.INCOME) to details
    }

    /** "Daily saver atom" → "Daily saver" (any casing of "atom"). */
    private fun stripPotName(raw: String): String {
        val trimmed = raw.trim()
        val stripped = if (trimmed.endsWith(" atom", ignoreCase = true)) {
            trimmed.dropLast(" atom".length).trim()
        } else {
            trimmed
        }
        return stripped.ifEmpty { trimmed }
    }

    /**
     * Merchant from a UPI descriptor:
     * `UPI-Debit-<upi ref digits>-MERCHANT NAME-<IFSC>-<vpa>-<narration>`
     * → the dash-separated segments between the UPI reference number and the
     * IFSC-shaped token. PDF line wrapping can inject a stray space mid-word;
     * that is left as-is — a slightly gappy name beats a glued-together one.
     */
    private fun upiMerchant(details: String): String {
        val parts = details.split("-")
        val refIndex = parts.indexOfFirst { it.trim().length >= 10 && it.trim().all(Char::isDigit) }
        if (refIndex == -1 || refIndex == parts.lastIndex) return details
        val ifscIndex = parts.drop(refIndex + 1)
            .indexOfFirst { ifscPattern.matches(it.trim()) }
            .let { if (it == -1) -1 else it + refIndex + 1 }
        val merchantParts = if (ifscIndex > refIndex + 1) {
            parts.subList(refIndex + 1, ifscIndex)
        } else {
            parts.subList(refIndex + 1, minOf(refIndex + 2, parts.size))
        }
        return merchantParts.joinToString("-").trim().ifEmpty { details }
    }

    /** Noon IST on the statement date — same convention as the shared parser. */
    private fun istEpochMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(IST).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
}
