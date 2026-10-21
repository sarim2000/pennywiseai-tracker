package com.pennywiseai.tracker.data.statement

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * Statement import in three steps (#13): read the file (a supported bank PDF,
 * or any bank's .xlsx/.csv through a column mapping), [preview] what it would
 * change without writing, then [commit] the rows the user kept and
 * [balanceCheck] the statement's closing balance against the account's.
 */
class ImportStatementUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val preferences: UserPreferencesRepository,
    private val database: PennyWiseDatabase,
    @ApplicationContext private val context: Context
) {
    sealed interface PdfRead {
        data class Parsed(val transactions: List<ParsedTransaction>) : PdfRead
        data class Error(val message: String) : PdfRead
        /** The statement PDF is locked; ask for its password ([wrongPassword]: the last one failed). */
        data class PasswordRequired(val wrongPassword: Boolean) : PdfRead
    }

    suspend fun readPdf(uri: Uri, password: String? = null): PdfRead = withContext(Dispatchers.IO) {
        try {
            val text = PdfTextExtractor.extractText(context, uri, password)
            val parser = PdfParserFactory.getParser(text)
                ?: return@withContext PdfRead.Error(
                    "Unsupported statement format. Currently supported: Google Pay, PhonePe, Paytm, slice, ICICI Bank. " +
                        "For any other bank, import its Excel (.xlsx) or CSV statement."
                )
            val parsed = parser.parse(text)
            if (parsed.isEmpty()) PdfRead.Error("No transactions found in the statement.") else PdfRead.Parsed(parsed)
        } catch (e: PdfPasswordRequiredException) {
            PdfRead.PasswordRequired(e.wrongPassword)
        } catch (e: Exception) {
            PdfRead.Error(e.message ?: "Failed to import statement.")
        }
    }

    /** The spreadsheet's rows; throws with a user-facing message when it can't be read. */
    suspend fun readTable(uri: Uri): List<List<String>> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(MAX_FILE_BYTES + 1)
            require(buffer.size <= MAX_FILE_BYTES) { "This file is too large to import." }
            buffer
        } ?: throw IllegalArgumentException("Couldn't open the file.")
        val rows = try {
            StatementTableParser.readTable(bytes)
        } catch (e: OutOfMemoryError) {
            // A hostile or enormous file: tell the user rather than crash the app.
            throw IllegalArgumentException("This file is too large to import.")
        }
        rows.also {
            require(it.any { row -> row.any(String::isNotBlank) }) { "This file has no rows." }
        }
    }

    suspend fun savedMappings(): Map<String, StatementColumnMapping> =
        StatementTableParser.decodeMappings(preferences.getStatementColumnMappings())

    suspend fun saveMapping(rows: List<List<String>>, mapping: StatementColumnMapping) {
        val signature = StatementTableParser.signature(rows.getOrNull(mapping.headerRow).orEmpty())
        if (signature.isEmpty()) return
        val updated = savedMappings() + (signature to mapping)
        preferences.setStatementColumnMappings(StatementTableParser.encodeMappings(updated))
    }

    /** Accounts a statement can belong to (credit cards included; their balance isn't checked). */
    suspend fun accounts(): List<AccountBalanceEntity> =
        accountBalanceRepository.getAllLatestBalancesOnce().sortedBy { it.displayLabel.lowercase() }

    /** What an account's amounts are tagged with — never the stored INR default for an SMS-tracked foreign account. */
    fun currencyOf(account: AccountBalanceEntity): String =
        CurrencyFormatter.resolveAccountCurrency(account.sourceType, account.currency, account.bankName)

    suspend fun baseCurrency(): String = preferences.baseCurrency.first()

    /** Matches [parsed] against PennyWise exactly as [commit] will, writing nothing. */
    suspend fun preview(parsed: List<ParsedTransaction>): StatementImportPlan = withContext(Dispatchers.IO) {
        StatementImportProcessor(repositoryStore()).plan(parsed)
    }

    /**
     * Imports [parsed], adding only the rows at [keep] (the preview's new rows
     * the user left ticked). Matching runs again inside one database
     * transaction — the protection SMS writes use — so an SMS saved or edited
     * while the preview was open is matched as it is now, never overwritten
     * with the preview's copy or duplicated. A manual account's balance
     * follows its new rows.
     */
    suspend fun commit(parsed: List<ParsedTransaction>, keep: Set<Int>): StatementImportResult.Success =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val processor = StatementImportProcessor(repositoryStore())
                val plan = processor.plan(parsed)
                val excluded = plan.rows.indices.toSet() - keep
                val accounts = plan.newIndices.filter { it !in excluded }
                    .map { plan.rows[it].parsed }
                    .mapNotNull { row -> row.accountLast4?.let { row.bankName to it } }
                    .distinct()
                // Pin a legacy manual account's opening before the rows land, so the
                // recompute below adds them instead of absorbing them into the opening.
                accounts.forEach { (bank, last4) -> accountBalanceRepository.ensureManualOpening(bank, last4) }
                val result = processor.apply(plan, excluded)
                accounts.forEach { (bank, last4) -> accountBalanceRepository.recomputeManualBalance(bank, last4) }
                result
            }
        }

    /**
     * The account a PDF statement is about: the one account with the rows'
     * bank name (and last 4, when the statement prints it). Null when it's
     * ambiguous or unknown.
     */
    suspend fun resolveAccount(parsed: List<ParsedTransaction>): AccountBalanceEntity? {
        val bank = parsed.map { it.bankName }.distinct().singleOrNull() ?: return null
        val last4 = parsed.mapNotNull { it.accountLast4 }.distinct().let { if (it.size > 1) return null else it.singleOrNull() }
        return accounts()
            .filter { it.bankName.equals(bank, ignoreCase = true) && (last4 == null || it.accountLast4 == last4) }
            .singleOrNull()
    }

    /**
     * Statement's closing balance vs PennyWise's for [account] at the end of the
     * statement's last day. Null when there's nothing to compare: no balance in
     * the statement, no account, a credit card (its statement balance is what's
     * owed, not held), or a statement in another currency.
     */
    suspend fun balanceCheck(parsed: List<ParsedTransaction>, account: AccountBalanceEntity?): StatementBalanceCheck? {
        account ?: return null
        if (account.isCreditCard) return null
        val currency = currencyOf(account)
        val closing = StatementBalanceCheck.closingRow(parsed.filter { it.currency == currency }) ?: return null
        val asOf = StatementBalanceCheck.dateOf(closing)
        val end = asOf.atTime(LocalTime.MAX)
        return withContext(Dispatchers.IO) {
            val bank = account.bankName
            val last4 = account.accountLast4
            val statementBalance = closing.balance!!
            val appBalance = accountBalanceRepository.balanceAsOf(bank, last4, end)
            val latest = accountBalanceRepository.getLatestBalance(bank, last4)
            StatementBalanceCheck(
                bankName = bank,
                accountLast4 = last4,
                accountLabel = account.displayLabel,
                currency = currency,
                asOf = asOf,
                statementBalance = statementBalance,
                appBalance = appBalance,
                correction = StatementBalanceCheck.correctionFor(
                    statementBalance = statementBalance,
                    appBalance = appBalance,
                    currentBalance = latest?.balance,
                    isManual = accountBalanceRepository.isManualAccount(bank, last4),
                    recordedAfterStatement = latest != null && latest.timestamp > end
                )
            )
        }
    }

    /**
     * "Set balance": recomputes the check and writes its correction in one
     * transaction, so a balance SMS landing after the result screen opened is
     * read rather than overwritten. Returns the fresh check (null: nothing written).
     */
    suspend fun applyBalance(parsed: List<ParsedTransaction>, account: AccountBalanceEntity): StatementBalanceCheck? =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val check = balanceCheck(parsed, account) ?: return@withTransaction null
                val target = check.correction ?: return@withTransaction null
                // Dated at the statement's end (never in the future) so a later
                // bank balance still wins; a manual account derives its own.
                val at = minOf(check.asOf.atTime(LocalTime.MAX), LocalDateTime.now())
                accountBalanceRepository.setCurrentBalance(check.bankName, check.accountLast4, target, at)
                check
            }
        }

    private fun repositoryStore() = object : StatementImportProcessor.TransactionStore {
        override suspend fun getTransactionsByHashes(hashes: List<String>): List<TransactionEntity> =
            transactionRepository.getTransactionsByHashes(hashes)

        override suspend fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime): List<TransactionEntity> =
            transactionRepository.getTransactionsBetweenDatesList(start, end)

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            transactionRepository.updateTransaction(transaction)
        }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            transactionRepository.insertTransactions(transactions)
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() < limit) {
                val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
