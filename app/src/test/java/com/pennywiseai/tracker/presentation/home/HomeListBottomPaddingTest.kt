package com.pennywiseai.tracker.presentation.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's last row must scroll clear of the actions floating over the list, in both nav styles. */
class HomeListBottomPaddingTest {

    // Gesture navigation, three-button navigation, and none (e.g. a hidden bar).
    private val systemBarInsets = listOf(0.dp, 24.dp, 48.dp)

    // M3 SmallFloatingActionButton: a 40dp button centred in a 48dp touch-target slot.
    private val smallFabSlot = 48.dp
    private val smallFabVisible = 40.dp

    @Test
    fun `the stack height counts the small FAB's touch-target slot`() {
        assertTrue(HomeFabStackHeight >= smallFabSlot + Spacing.smd + Dimensions.Component.fab)
    }

    @Test
    fun `normal nav - the last row ends a full gap above the Add FAB`() {
        systemBarInsets.forEach { systemBar ->
            val addFabTop = systemBar + Dimensions.Component.fabBottomInset + Dimensions.Component.fab +
                Spacing.smd + smallFabSlot - (smallFabSlot - smallFabVisible) / 2
            val padding = homeListBottomPadding(systemBar, floatingNav = false)
            assertTrue(
                "system bar $systemBar: padding $padding must end ${Spacing.md} above $addFabTop",
                padding >= addFabTop + Spacing.md
            )
        }
    }

    @Test
    fun `normal nav - the stack stands above the nav bar`() {
        systemBarInsets.forEach { systemBar ->
            val navBarTop = systemBar + Dimensions.Component.bottomBarHeight
            val stackBottom = systemBar + Dimensions.Component.fabBottomInset
            assertTrue("system bar $systemBar", stackBottom > navBarTop)
        }
    }

    @Test
    fun `floating nav - the list scrolls clear of the action docked above Ask`() {
        systemBarInsets.forEach { systemBar ->
            val dockTop = systemBar + Dimensions.Component.floatingNavBottomGap +
                Dimensions.Component.floatingNavHeight + Spacing.sm + Dimensions.Component.dockedActionSmallSize
            val padding = homeListBottomPadding(systemBar, floatingNav = true)
            assertClears(padding, dockTop, "system bar $systemBar")
        }
    }

    private fun assertClears(padding: Dp, obstacleTop: Dp, case: String) =
        assertTrue("$case: padding $padding must clear $obstacleTop", padding >= obstacleTop + Spacing.sm)
}
