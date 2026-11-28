package com.pennywiseai.tracker.presentation.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter

/** Existing rows a just-saved trip could take, offered once. */
data class TripBackfillOffer(val groupId: Long, val rows: List<TransactionEntity>, val capped: Boolean = false)

private val IdSetSaver = listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })

/**
 * Checklist of [offer]'s rows, all ticked to start. "Add" hands back the ticked ids; "Skip" (or a
 * dismiss) hands back nothing and the caller records nothing, so the rows stay as they are.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripBackfillSheet(
    offer: TripBackfillOffer,
    onAdd: (List<Long>) -> Unit,
    onSkip: () -> Unit
) {
    // Kept as the unticked set so "everything" is the default and survives a rotation cheaply.
    var unchecked by rememberSaveable(offer.groupId, stateSaver = IdSetSaver) { mutableStateOf(emptySet<Long>()) }
    val checkedIds = offer.rows.map { it.id }.filter { it !in unchecked }
    val thisYear = java.time.LocalDate.now().year
    val withYear = AppDates.dateFormatter()
    val dayMonth = AppDates.dayMonthFormatter()

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = Dimensions.Padding.content)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                stringResource(R.string.trip_backfill_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (offer.capped) {
                    pluralStringResource(R.plurals.trip_backfill_capped, offer.rows.size, offer.rows.size)
                } else {
                    pluralStringResource(R.plurals.trip_backfill_message, offer.rows.size, offer.rows.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { unchecked = emptySet() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.trip_backfill_select_all))
                }
                TextButton(onClick = { unchecked = offer.rows.map { it.id }.toSet() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.trip_backfill_select_none))
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.sm)
            ) {
                items(offer.rows, key = { it.id }) { txn ->
                    val isChecked = txn.id !in unchecked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                            .toggleable(
                                value = isChecked,
                                role = Role.Checkbox,
                                onValueChange = { unchecked = if (it) unchecked - txn.id else unchecked + txn.id }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Text(
                            "${txn.dateTime.format(if (txn.dateTime.year == thisYear) dayMonth else withYear)} · ${txn.merchantName}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = Spacing.sm)
                        )
                        Text(
                            CurrencyFormatter.signed("-", CurrencyFormatter.formatCurrency(txn.amount, txn.currency)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.spending
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
            ) {
                TextButton(onClick = onSkip, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.trip_backfill_skip))
                }
                Button(
                    onClick = { onAdd(checkedIds) },
                    enabled = checkedIds.isNotEmpty(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(pluralStringResource(R.plurals.trip_backfill_add, checkedIds.size, checkedIds.size))
                }
            }
        }
    }
}
