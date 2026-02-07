package com.pennywiseai.tracker.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.opencsv.CSVWriter
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PROVIDER_AUTHORITY = ".fileprovider"
        private const val EXPORT_DIR = "exports"
        
        // Date and time formatters
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
    
    /**
     * Exports transactions to CSV file with progress updates
     * @param transactions List of transactions to export
     * @param fileName Optional custom filename (without extension)
     * @return Flow emitting progress updates and final Uri
     */
    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> = flow {
        emit(ExportResult.Progress(0f, "Preparing export..."))
        
        try {
            // Prepare export directory
            val exportDir = File(context.cacheDir, EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // Clean up old export files (older than 24 hours)
            cleanOldExports(exportDir)
            
            // Generate filename
            val timestamp = LocalDateTime.now().format(fileNameFormatter)
            val finalFileName = fileName ?: "transactions_$timestamp"
            val file = File(exportDir, "$finalFileName.csv")
            
            emit(ExportResult.Progress(0.1f, "Creating CSV file..."))
            
            // Write CSV using OpenCSV for proper escaping
            FileWriter(file).use { fileWriter ->
                CSVWriter(fileWriter).use { csvWriter ->
                    // Write header
                    csvWriter.writeNext(arrayOf(
                        "Date",
                        "Time",
                        "Merchant",
                        "Category",
                        "Type",
                        "Amount",
                        "Currency",
                        "Bank",
                        "Account",
                        "Balance After",
                        "Description",
                        "SMS Body"
                    ))

                    // Write transactions with progress updates
                    val totalTransactions = transactions.size
                    transactions.forEachIndexed { index, transaction ->
                        // Write transaction row
                        csvWriter.writeNext(arrayOf(
                            transaction.dateTime.format(dateFormatter),
                            transaction.dateTime.format(timeFormatter),
                            transaction.merchantName,
                            transaction.category,
                            when (transaction.transactionType) {
                                TransactionType.INCOME -> "Income"
                                TransactionType.EXPENSE -> "Expense"
                                TransactionType.CREDIT -> "Credit Card"
                                TransactionType.TRANSFER -> "Transfer"
                                TransactionType.INVESTMENT -> "Investment"
                            },
                            transaction.amount.toString(),
                            transaction.currency,
                            transaction.bankName ?: "",
                            transaction.accountNumber ?: "",
                            transaction.balanceAfter?.toString() ?: "",
                            transaction.description ?: "",
                            transaction.smsBody ?: ""
                        ))
                        
                        // Update progress (10% to 90% for writing)
                        val progress = 0.1f + (0.8f * (index + 1) / totalTransactions)
                        if (index % 100 == 0 || index == totalTransactions - 1) {
                            emit(ExportResult.Progress(
                                progress,
                                "Exporting ${index + 1} of $totalTransactions transactions..."
                            ))
                        }
                    }
                }
            }
            
            emit(ExportResult.Progress(0.95f, "Finalizing export..."))
            
            // Generate content URI for sharing
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}$PROVIDER_AUTHORITY",
                file
            )
            
            emit(ExportResult.Progress(1.0f, "Export complete!"))
            emit(ExportResult.Success(
                uri = uri,
                fileName = "$finalFileName.csv",
                transactionCount = transactions.size,
                fileSizeBytes = file.length()
            ))
            
        } catch (e: Exception) {
            emit(ExportResult.Error(
                message = "Failed to export transactions: ${e.message}",
                exception = e
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Exports transactions directly to file (blocking version for simple use cases)
     */
    suspend fun exportTransactionsToFile(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, EXPORT_DIR)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        val timestamp = LocalDateTime.now().format(fileNameFormatter)
        val finalFileName = fileName ?: "transactions_$timestamp"
        val file = File(exportDir, "$finalFileName.csv")
        
        FileWriter(file).use { fileWriter ->
            CSVWriter(fileWriter).use { csvWriter ->
                // Write header
                csvWriter.writeNext(arrayOf(
                    "Date",
                    "Time",
                    "Merchant",
                    "Category",
                    "Type",
                    "Amount",
                    "Currency",
                    "Bank",
                    "Account",
                    "Balance After",
                    "Description",
                    "SMS Body"
                ))

                // Write transactions
                transactions.forEach { transaction ->
                    csvWriter.writeNext(arrayOf(
                        transaction.dateTime.format(dateFormatter),
                        transaction.dateTime.format(timeFormatter),
                        transaction.merchantName,
                        transaction.category,
                        when (transaction.transactionType) {
                            TransactionType.INCOME -> "Income"
                            TransactionType.EXPENSE -> "Expense"
                            TransactionType.CREDIT -> "Credit Card"
                            TransactionType.TRANSFER -> "Transfer"
                            TransactionType.INVESTMENT -> "Investment"
                        },
                        transaction.amount.toString(),
                        transaction.currency,
                        transaction.bankName ?: "",
                        transaction.accountNumber ?: "",
                        transaction.balanceAfter?.toString() ?: "",
                        transaction.description ?: "",
                        transaction.smsBody ?: ""
                    ))
                }
            }
        }
        
        FileProvider.getUriForFile(
            context,
            "${context.packageName}$PROVIDER_AUTHORITY",
            file
        )
    }
    
    /**
     * Cleans up old export files older than 24 hours
     */
    private fun cleanOldExports(exportDir: File) {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        exportDir.listFiles()?.forEach { file ->
            if (file.lastModified() < oneDayAgo) {
                file.delete()
            }
        }
    }
}

/**
 * Sealed class representing export results
 */
sealed class ExportResult {
    data class Progress(
        val progress: Float, // 0.0 to 1.0
        val message: String
    ) : ExportResult()
    
    data class Success(
        val uri: Uri,
        val fileName: String,
        val transactionCount: Int,
        val fileSizeBytes: Long
    ) : ExportResult()
    
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : ExportResult()
}