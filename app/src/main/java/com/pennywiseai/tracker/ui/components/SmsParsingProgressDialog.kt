package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsParsingProgressDialog(
    isVisible: Boolean,
    workInfo: WorkInfo?,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isVisible && workInfo != null) {
        Dialog(onDismissRequest = { }) {
            PennyWiseCardV2(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                contentPadding = Spacing.lg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Title
                    Text(
                        text = "Scanning SMS Messages",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Progress Details
                    ProgressDetails(workInfo = workInfo)

                    // Progress Bar
                    if (workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 0) > 0) {
                        val progress = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, 0).toFloat() /
                                workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 1).toFloat()

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            strokeCap = StrokeCap.Round
                        )
                    }

                    // Cancel Button
                    if (onCancel != null && workInfo.state == WorkInfo.State.RUNNING) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Cancel Scan")
                        }
                    } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressDetails(workInfo: WorkInfo) {
    val totalMessages = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 0)
    val processedMessages = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, 0)
    val parsedTransactions = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, 0)
    val savedTransactions = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, 0)
    val timeElapsed = workInfo.progress.getLong(OptimizedSmsReaderWorker.PROGRESS_TIME_ELAPSED, 0L)
    val estimatedTimeRemaining = workInfo.progress.getLong(OptimizedSmsReaderWorker.PROGRESS_ESTIMATED_TIME_REMAINING, 0L)
    val currentBatch = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_CURRENT_BATCH, 0)
    val totalBatches = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL_BATCHES, 0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Main progress text
        if (totalMessages > 0) {
            val progressText = if (processedMessages == totalMessages) {
                "All messages processed!"
            } else {
                "Processed $processedMessages of $totalMessages messages"
            }

            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        // Transaction details
        if (parsedTransactions > 0 || savedTransactions > 0) {
            val detailsText = buildAnnotatedString {
                if (parsedTransactions > 0) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                        append("$parsedTransactions transactions parsed")
                    }
                }
                if (parsedTransactions > 0 && savedTransactions > 0) {
                    append(" • ")
                }
                if (savedTransactions > 0) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                        append("$savedTransactions saved")
                    }
                }
            }

            Text(
                text = detailsText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        // Time information
        if (timeElapsed > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time elapsed
                Text(
                    text = formatDuration(timeElapsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Estimated time remaining
                if (estimatedTimeRemaining > 0 && workInfo.state == WorkInfo.State.RUNNING) {
                    Text(
                        text = "~${formatDuration(estimatedTimeRemaining)} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Batch information (for parallel processing)
        if (totalBatches > 1 && workInfo.state == WorkInfo.State.RUNNING) {
            Text(
                text = "Batch $currentBatch of $totalBatches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status based on work state
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Scan completed successfully!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            WorkInfo.State.FAILED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Scan failed. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            WorkInfo.State.CANCELLED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Scan cancelled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // For ENQUEUED or BLOCKED states
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Starting scan...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

@Composable
fun SmsParsingProgressIndicator(
    workInfo: WorkInfo?,
    modifier: Modifier = Modifier
) {
    if (workInfo != null && workInfo.state == WorkInfo.State.RUNNING) {
        val totalMessages = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 0)
        val processedMessages = workInfo.progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, 0)

        if (totalMessages > 0) {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                LinearProgressIndicator(
                    progress = { processedMessages.toFloat() / totalMessages.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    strokeCap = StrokeCap.Round
                )

                Text(
                    text = "Scanning SMS: $processedMessages/$totalMessages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}