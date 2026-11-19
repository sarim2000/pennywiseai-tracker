package com.pennywiseai.tracker.presentation.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.PlanSection
import com.pennywiseai.tracker.presentation.goals.GoalsSection
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
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

@StringRes
private fun PlanSection.labelRes(): Int = when (this) {
    PlanSection.BUDGETS -> R.string.budgets_title
    PlanSection.BILLS -> R.string.plan_bills
    PlanSection.GOALS -> R.string.goals_section_title
}

/** The connected Budgets · Bills · Goals switch heading each Plan section. */
@Composable
fun PlanSwitcher(host: PlanTabHost, modifier: Modifier = Modifier) {
    ConnectedChoiceRow(
        options = PlanSection.entries,
        selected = host.section,
        onSelect = host.onSelect,
        label = { stringResource(it.labelRes()) },
        modifier = modifier,
    )
}

/** The Plan tab's Goals section: the goals list that used to sit atop Budgets. */
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
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.nav_plan),
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
                top = Dimensions.Padding.content + padding.calculateTopPadding(),
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            item(key = "switch") { PlanSwitcher(host) }
            item(key = "goals") { GoalsSection(onOpenGoal = onOpenGoal, onCreateGoal = onCreateGoal) }
        }
    }
}
