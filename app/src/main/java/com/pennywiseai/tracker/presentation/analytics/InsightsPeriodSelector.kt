package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.ui.theme.Dimensions

/** The periods one tap away; the rest sit behind the third ("More") button. */
private val QuickPeriods = listOf(TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH)
private val MorePeriods = listOf(TimePeriod.CURRENT_FY, TimePeriod.ALL, TimePeriod.CUSTOM)

/**
 * The Insights period switch: a connected button group of This month · Last
 * month · More. More opens the other periods (financial year, all time, a
 * custom range) and, while one of them is picked, shows its name instead.
 * Month periods follow the budget cycle, so nothing the old menu offered is lost.
 */
@Composable
internal fun InsightsPeriodSelector(
    selected: TimePeriod,
    /** What the More button reads while one of its periods is picked (e.g. a custom range). */
    selectedMoreLabel: String,
    onSelect: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scrollable = LocalDensity.current.fontScale >= 1.5f
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        QuickPeriods.forEachIndexed { index, period ->
            PeriodToggle(
                checked = selected == period,
                label = period.label,
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                else ButtonGroupDefaults.connectedMiddleButtonShapes(),
                onClick = { onSelect(period) },
                scrollableLabel = scrollable,
                modifier = if (scrollable) Modifier else Modifier.weight(selectedWeight(selected == period))
            )
        }
        val moreSelected = selected in MorePeriods
        Box(modifier = if (scrollable) Modifier else Modifier.weight(selectedWeight(moreSelected))) {
            PeriodToggle(
                checked = moreSelected,
                label = if (moreSelected) selectedMoreLabel else stringResource(R.string.analytics_period_more),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                onClick = { menuOpen = true },
                trailingDropDown = true,
                scrollableLabel = scrollable,
                modifier = if (scrollable) Modifier else Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = MaterialTheme.shapes.large
            ) {
                MorePeriods.forEachIndexed { index, period ->
                    SelectableDropdownMenuItem(
                        selected = selected == period,
                        text = { Text(period.label) },
                        shapes = MenuDefaults.itemShape(index, MorePeriods.size),
                        colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSelect(period)
                        }
                    )
                }
            }
        }
    }
}

/** The checked button widens (Expressive), so its check icon doesn't squeeze the label. */
@Composable
private fun selectedWeight(checked: Boolean): Float =
    animateFloatAsState(
        targetValue = if (checked) SELECTED_WEIGHT else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "periodWeight",
    ).value

private const val SELECTED_WEIGHT = 1.45f

@Composable
private fun PeriodToggle(
    checked: Boolean,
    label: String,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
    scrollableLabel: Boolean,
    modifier: Modifier = Modifier,
    trailingDropDown: Boolean = false
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = shapes,
        modifier = modifier
            .heightIn(min = Dimensions.Component.buttonHeight)
            .semantics { role = if (trailingDropDown) Role.DropdownList else Role.RadioButton }
    ) {
        if (checked && !trailingDropDown) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(
            text = label,
            textAlign = TextAlign.Center,
            modifier = if (scrollableLabel) Modifier.widthIn(max = Dimensions.Component.minTouchTarget * 5)
            else Modifier.weight(1f, fill = false)
        )
        if (trailingDropDown) {
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        }
    }
}
