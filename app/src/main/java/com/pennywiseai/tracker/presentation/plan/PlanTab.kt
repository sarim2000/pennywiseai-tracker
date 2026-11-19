package com.pennywiseai.tracker.presentation.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.PlanSection
import com.pennywiseai.tracker.presentation.goals.GoalsSection
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Set when a screen draws as one section of the Plan tab (titled "Plan", the
 * Budgets · Bills · Goals switch on top, its add action docked by the nav bar)
 * rather than on its own.
 */
@Immutable
class PlanTabHost(val section: PlanSection, val onSelect: (PlanSection) -> Unit)

/** The small line over the Plan title: "Budgets, bills & goals". */
@Composable
fun planOverline(): String = stringResource(R.string.plan_overline)

@StringRes
private fun PlanSection.labelRes(): Int = when (this) {
    PlanSection.BUDGETS -> R.string.budgets_title
    PlanSection.BILLS -> R.string.plan_bills
    PlanSection.GOALS -> R.string.goals_section_title
}

/**
 * The connected Budgets · Bills · Goals switch heading each Plan section. The
 * chosen section carries a check, so the choice doesn't rest on colour alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanSwitcher(host: PlanTabHost, modifier: Modifier = Modifier) {
    val options = PlanSection.entries
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == host.section
            ToggleButton(
                checked = checked,
                onCheckedChange = { host.onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimensions.Component.buttonHeight)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = ButtonDefaults.SmallContentPadding,
            ) {
                if (checked) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(
                    text = stringResource(option.labelRes()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The Plan tab's Goals section: the switch, then the goals, "New goal" docked by the nav bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanGoalsScreen(
    host: PlanTabHost,
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
) {
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.nav_plan),
                overline = planOverline(),
                hazeState = hazeState,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Spacing.sm + padding.calculateTopPadding(),
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            item(key = "switch") { PlanSwitcher(host) }
            item(key = "goals") {
                GoalsSection(onOpenGoal = onOpenGoal, onCreateGoal = onCreateGoal, inPlanTab = true)
            }
        }
    }
}
