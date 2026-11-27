package com.pennywiseai.tracker.presentation.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.PlanSection
import com.pennywiseai.tracker.presentation.goals.GoalsSection
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Set when a screen draws as one section of the Plan tab (titled "Plan", the
 * Budgets · Bills · Goals · Challenges switch on top, its add action docked by the nav bar)
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
    PlanSection.CHALLENGES -> R.string.challenges_title
}

/**
 * The connected Budgets · Bills · Goals · Challenges switch heading each Plan section. The
 * chosen section carries a check, so the choice doesn't rest on colour alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanSwitcher(host: PlanTabHost, modifier: Modifier = Modifier) {
    val options = PlanSection.entries
    // A label that doesn't fit (large font, narrow screen) stacks the options full width
    // rather than cutting a section name short.
    var stacked by remember(LocalDensity.current.fontScale) { mutableStateOf(false) }
    val option: @Composable (Int, Modifier) -> Unit = { index, itemModifier ->
        val section = options[index]
        val checked = section == host.section
        ToggleButton(
            checked = checked,
            onCheckedChange = { host.onSelect(section) },
            modifier = itemModifier
                .heightIn(min = Dimensions.Component.buttonHeight)
                .semantics { role = Role.RadioButton },
            shapes = when {
                stacked -> ToggleButtonDefaults.shapesFor(Dimensions.Component.buttonHeight)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            contentPadding = if (stacked) ButtonDefaults.SmallContentPadding else PaddingValues(horizontal = Spacing.sm),
        ) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(
                text = stringResource(section.labelRes()),
                maxLines = 1,
                onTextLayout = { if (it.hasVisualOverflow) stacked = true },
            )
        }
    }
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.indices.forEach { option(it, Modifier.fillMaxWidth()) }
        }
    } else {
        // Each option is as wide as its label needs, the slack shared out in proportion:
        // four equal quarters of a phone can't hold "✓ Challenges", content-sized ones can.
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.labelLarge
        val density = LocalDensity.current
        val labels = options.map { stringResource(it.labelRes()) }
        val widths = options.indices.map { i ->
            val check = if (options[i] == host.section) ButtonDefaults.IconSize + ButtonDefaults.IconSpacing else Spacing.none
            measurer.measure(labels[i], style, maxLines = 1).size.width + with(density) { (check + Spacing.sm * 2).toPx() }
        }
        Row(
            modifier = modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.indices.forEach { option(it, Modifier.weight(widths[it])) }
        }
    }
}

/** The section switch stays below the title, including when a deep link scrolls the list. */
@Composable
fun PlanTopBar(host: PlanTabHost?, titleBar: @Composable () -> Unit) {
    if (host == null) {
        titleBar()
    } else {
        Column {
            // The title bar draws its expanded and collapsed bars as siblings, which the
            // Scaffold slot overlays; a bare Column stacked them (a blank band when collapsed).
            Box { titleBar() }
            PlanSwitcher(
                host,
                Modifier.background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.md, bottom = Spacing.xs)
            )
        }
    }
}

/** The Plan tab's Goals section: the switch, then the goals, "New goal" docked by the nav bar. */
@Composable
fun PlanGoalsScreen(
    host: PlanTabHost,
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
) {
    PlanListScreen(host) {
        item(key = "goals") {
            GoalsSection(onOpenGoal = onOpenGoal, onCreateGoal = onCreateGoal, inPlanTab = true)
        }
    }
}

/** A Plan section drawn as one list under the Plan title and switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanListScreen(host: PlanTabHost, content: LazyListScope.() -> Unit) {
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            PlanTopBar(host) {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = stringResource(R.string.nav_plan),
                    overline = planOverline(),
                    hazeState = hazeState,
                )
            }
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
                top = Spacing.md + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimensions.Component.bottomBarHeight +
                    if (LocalNavDock.current != null) Dimensions.Component.fabScrollClearance else Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
            content = content,
        )
    }
}
