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

    @Test
    fun `normal nav - the list scrolls clear of the Add and Sync stack`() {
        systemBarInsets.forEach { systemBar ->
            val stackTop = systemBar + Dimensions.Component.fabBottomInset + HomeFabStackHeight
            val padding = homeListBottomPadding(systemBar, floatingNav = false)
            assertClears(padding, stackTop, "system bar $systemBar")
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
