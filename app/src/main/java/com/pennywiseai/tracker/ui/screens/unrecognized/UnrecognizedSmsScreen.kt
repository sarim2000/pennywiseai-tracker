package com.pennywiseai.tracker.ui.screens.unrecognized

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.shimmer
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
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

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(600)
            hasAnimated = true
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Unrecognized SMS",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Header card with info (0ms delay)
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(0); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    PennyWiseCardV2(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
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
                }
            }

            // Loading State — skeleton items
            if (isLoading) {
                items(4) {
                    UnrecognizedSmsItemSkeleton()
                }
            } else if (unrecognizedMessages.isEmpty()) {
                // Empty State
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Outlined.MarkEmailRead,
                        headline = "All messages recognized",
                        description = "No unrecognized bank messages found"
                    )
                }
            } else {
                // Message items with staggered animation (50ms per item, starting at 50ms)
                itemsIndexed(
                    items = unrecognizedMessages,
                    key = { _, message -> message.id }
                ) { index, message ->
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
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
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
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
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Delete")
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    FilledTonalButton(
                        onClick = onReport
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Report",
                            modifier = Modifier.size(Dimensions.Icon.small)
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
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun UnrecognizedSmsItemSkeleton(
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Sender placeholder
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
            // Date placeholder
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            // Message body placeholders (3 lines)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            // Button placeholder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                        .background(placeholderColor)
                        .shimmer()
                )
            }
        }
    }
}
