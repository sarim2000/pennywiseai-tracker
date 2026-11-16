package com.pennywiseai.tracker.presentation.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.share.SharedLink
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The links this phone has published and not yet seen expire: open, copy or take one
 * down. Only this phone can delete them, since the tokens never leave it.
 */
@Composable
fun SharedLinksScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShareLinksViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }

    val copied = stringResource(R.string.shared_links_copied)
    LaunchedEffect(viewModel) {
        viewModel.deleteOutcomes.collect { outcome ->
            val message = when (outcome) {
                DeleteOutcome.Deleted -> resources.getString(R.string.shared_links_deleted)
                is DeleteOutcome.Failed -> shareLinkErrorMessage(resources, outcome.error)
            }
            launch { snackbar.showSnackbar(message) }
        }
    }

    PennyWiseScaffold(
        title = stringResource(R.string.shared_links_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.share_studio_back))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = links
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                PennyWiseEmptyState(
                    icon = Icons.Default.Link,
                    headline = stringResource(R.string.shared_links_empty_title),
                    description = stringResource(R.string.shared_links_empty_body),
                )
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.sm, bottom = Spacing.Layout.scrollBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent),
            ) {
                GroupedList {
                    list.forEachIndexed { i, link ->
                        LinkRow(
                            link = link,
                            position = ListItemPosition.from(i, list.size),
                            busy = link.id in deleting,
                            onOpen = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri())) }
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(link.url))
                                scope.launch { snackbar.showSnackbar(copied) }
                            },
                            onDelete = { confirmId = link.id },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.shared_links_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val confirming = links?.firstOrNull { it.id == confirmId }
    if (confirming != null) {
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text(stringResource(R.string.shared_links_delete_title)) },
            text = { Text(stringResource(R.string.shared_links_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmId = null
                        viewModel.delete(confirming)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.shared_links_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmId = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.shared_links_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun LinkRow(
    link: SharedLink,
    position: ListItemPosition,
    busy: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val kind = ShareKind.fromName(link.kind)?.let { kindLabel(it) }
    val expiry = link.expiry?.let {
        stringResource(R.string.shared_links_expires, it.atZone(ZoneId.systemDefault()).toLocalDate().format(AppDates.dateFormatter()))
    } ?: stringResource(R.string.shared_links_never_expires)
    GroupedRow(position = position, onClick = onOpen, enabled = !busy) {
        IconTile(
            icon = Icons.Default.Link,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        RowLabels(
            title = link.title,
            subtitle = if (kind != null) stringResource(R.string.shared_links_row_subtitle, kind, expiry) else expiry,
        )
        IconButton(onClick = onCopy, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.shared_links_copy))
        }
        if (busy) {
            Box(Modifier.size(Dimensions.Component.minTouchTarget), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(Dimensions.Icon.small), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shared_links_delete))
            }
        }
    }
}
