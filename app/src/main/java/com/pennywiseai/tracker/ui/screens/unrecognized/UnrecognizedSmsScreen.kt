package com.pennywiseai.tracker.ui.screens.unrecognized

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnrecognizedSmsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    viewModel: UnrecognizedSmsViewModel = hiltViewModel()
) {
    val unrecognizedMessages by viewModel.unrecognizedMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showReported by viewModel.showReported.collectAsStateWithLifecycle()
    var selectedMessage by remember { mutableStateOf<UnrecognizedSmsEntity?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header card with info
        PennyWiseCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Unrecognized Bank Messages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "These messages from potential banks couldn't be automatically parsed. " +
                          "Help improve PennyWise by reporting them so we can add support for more banks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Filter toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = showReported,
                        onClick = { viewModel.toggleShowReported() },
                        label = { Text("Show Reported") },
                        leadingIcon = if (showReported) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (unrecognizedMessages.isNotEmpty()) {
                        val reportedCount = unrecognizedMessages.count { it.reported }
                        val unreportedCount = unrecognizedMessages.size - reportedCount
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (unreportedCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text("$unreportedCount new")
                                }
                            }
                            if (reportedCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text("$reportedCount reported")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (unrecognizedMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No unrecognized messages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = unrecognizedMessages,
                    key = { it.id }
                ) { message ->
                    UnrecognizedSmsItem(
                        message = message,
                        onReport = {
                            viewModel.reportMessage(message)
                        },
                        onDelete = {
                            selectedMessage = message
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                selectedMessage = null
            },
            title = { Text("Delete Message") },
            text = { 
                Text("Are you sure you want to delete this unrecognized message? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedMessage?.let { viewModel.deleteMessage(it) }
                        showDeleteConfirmation = false
                        selectedMessage = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        selectedMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UnrecognizedSmsItem(
    message: UnrecognizedSmsEntity,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PennyWiseCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header with sender and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = message.receivedAt.format(
                            DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (message.reported) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "Reported",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // Message content (truncated)
            Text(
                text = message.smsBody,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!message.reported) {
                    TextButton(
                        onClick = onDelete
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Delete")
                    }
                    
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    
                    Button(
                        onClick = onReport,
                        contentPadding = PaddingValues(
                            horizontal = Dimensions.Padding.content,
                            vertical = Spacing.sm
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Report",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Report")
                    }
                } else {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Delete")
                    }
                }
            }
        }
    }
}