package com.pennywiseai.tracker.presentation.transactions

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.ExportResult
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

@Composable
fun ExportTransactionsDialog(
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportState by remember { mutableStateOf<ExportState>(ExportState.Ready) }
    
    Dialog(onDismissRequest = {
        if (exportState !is ExportState.Exporting) {
            onDismiss()
        }
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                val icon = when (exportState) {
                    is ExportState.Ready -> Icons.Default.FileDownload
                    is ExportState.Exporting -> Icons.Default.HourglassTop
                    is ExportState.Success -> Icons.Default.CheckCircle
                    is ExportState.Error -> Icons.Default.Error
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = when (exportState) {
                        is ExportState.Success -> MaterialTheme.colorScheme.primary
                        is ExportState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = when (exportState) {
                        is ExportState.Ready -> "Export Transactions"
                        is ExportState.Exporting -> "Exporting..."
                        is ExportState.Success -> "Export Complete!"
                        is ExportState.Error -> "Export Failed"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content based on state
                when (val state = exportState) {
                    is ExportState.Ready -> {
                        Text(
                            text = "Export ${transactions.size} transactions to CSV format",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Summary info
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Total transactions row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total transactions:",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = transactions.size.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                // Date range in column layout to prevent wrapping
                                if (transactions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                                    val startDate = transactions.last().dateTime.format(dateFormatter)
                                    val endDate = transactions.first().dateTime.format(dateFormatter)
                                    
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Date range:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$startDate - $endDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    is ExportState.Exporting -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    is ExportState.Success -> {
                        Text(
                            text = "Successfully exported ${state.transactionCount} transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // File info
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = state.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "Size: ${formatFileSize(state.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    
                    is ExportState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (exportState) {
                        is ExportState.Ready -> {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        viewModel.exportTransactions(transactions).collect { result ->
                                            when (result) {
                                                is ExportResult.Progress -> {
                                                    exportState = ExportState.Exporting(
                                                        progress = result.progress,
                                                        message = result.message
                                                    )
                                                }
                                                is ExportResult.Success -> {
                                                    exportState = ExportState.Success(
                                                        uri = result.uri,
                                                        fileName = result.fileName,
                                                        transactionCount = result.transactionCount,
                                                        fileSizeBytes = result.fileSizeBytes
                                                    )
                                                }
                                                is ExportResult.Error -> {
                                                    exportState = ExportState.Error(
                                                        message = result.message
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export")
                            }
                        }
                        
                        is ExportState.Exporting -> {
                            // No buttons during export
                        }
                        
                        is ExportState.Success -> {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Done")
                            }
                            
                            Button(
                                onClick = {
                                    // Share the exported file
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, (exportState as ExportState.Success).uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "PennyWise Transactions Export")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share CSV"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                        
                        is ExportState.Error -> {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Close")
                            }
                            
                            Button(
                                onClick = {
                                    exportState = ExportState.Ready
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class ExportState {
    object Ready : ExportState()
    data class Exporting(val progress: Float, val message: String) : ExportState()
    data class Success(
        val uri: android.net.Uri,
        val fileName: String,
        val transactionCount: Int,
        val fileSizeBytes: Long
    ) : ExportState()
    data class Error(val message: String) : ExportState()
}

private fun formatFileSize(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
    }
}