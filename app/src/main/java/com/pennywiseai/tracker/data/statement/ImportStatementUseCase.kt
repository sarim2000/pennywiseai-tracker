package com.pennywiseai.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.pennywiseai.parser.core.ParsedTransaction
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
import java.math.BigDecimal
import java.math.RoundingMode
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
        StatementTableParser.readTable(bytes).also {
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

    /** Writes [plan] minus the new rows at [excluded]; a manual account's balance follows its new rows. */
    suspend fun commit(plan: StatementImportPlan, excluded: Set<Int>): StatementImportResult.Success =
        withContext(Dispatchers.IO) {
            val accounts = plan.newIndices.filter { it !in excluded }
                .map { plan.rows[it].parsed }
                .mapNotNull { row -> row.accountLast4?.let { row.bankName to it } }
                .distinct()
            // Pin a legacy manual account's opening before the rows land, so the
            // recompute below adds them instead of absorbing them into the opening.
            accounts.forEach { (bank, last4) -> accountBalanceRepository.ensureManualOpening(bank, last4) }
            val result = StatementImportProcessor(repositoryStore()).apply(plan, excluded)
            accounts.forEach { (bank, last4) -> accountBalanceRepository.recomputeManualBalance(bank, last4) }
            result
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
        return withContext(Dispatchers.IO) {
            StatementBalanceCheck(
                bankName = account.bankName,
                accountLast4 = account.accountLast4,
                accountLabel = account.displayLabel,
                currency = currency,
                asOf = asOf,
                statementBalance = closing.balance!!,
                appBalance = accountBalanceRepository.balanceAsOf(account.bankName, account.accountLast4, asOf.atTime(LocalTime.MAX)),
                currentBalance = accountBalanceRepository.getLatestBalance(account.bankName, account.accountLast4)?.balance
            )
        }
    }

    /** "Set balance" from the check, through the same path as Manage Accounts' Update balance. */
    suspend fun applyBalance(check: StatementBalanceCheck) = withContext(Dispatchers.IO) {
        accountBalanceRepository.setCurrentBalance(check.bankName, check.accountLast4, check.correctedCurrentBalance)
    }

    private fun repositoryStore() = object : StatementImportProcessor.TransactionStore {
        override suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
            transactionRepository.getTransactionByHash(transactionHash)

        override suspend fun findStatementMergeCandidate(
            transaction: TransactionEntity
        ): TransactionEntity? =
            transactionRepository.findStatementMergeCandidate(transaction)

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            transactionRepository.updateTransaction(transaction)
        }

        // Amounts are stored as text, so "500", "500.0" and "500.00" only match
        // themselves; a statement and an SMS often write the same amount differently.
        override suspend fun getTransactionByAmountAndDate(
            amount: BigDecimal,
            dateStart: LocalDateTime,
            dateEnd: LocalDateTime
        ): List<TransactionEntity> =
            amountSpellings(amount)
                .flatMap { transactionRepository.getTransactionByAmountAndDate(it, dateStart, dateEnd) }
                .distinctBy { it.id }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            transactionRepository.insertTransactions(transactions)
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024

        /** [amount] at scales 0–2 where exact, plus as given — each distinct spelling once. */
        internal fun amountSpellings(amount: BigDecimal): List<BigDecimal> =
            (listOf(amount) + (0..2).mapNotNull { scale ->
                runCatching { amount.setScale(scale, RoundingMode.UNNECESSARY) }.getOrNull()
            }).distinctBy { it.toPlainString() }

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
