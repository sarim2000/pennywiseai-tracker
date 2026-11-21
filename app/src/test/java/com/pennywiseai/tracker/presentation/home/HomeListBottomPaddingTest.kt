package com.pennywiseai.tracker.presentation.home

import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's last row must scroll clear of the actions floating over the list, in both nav styles. */
class HomeListBottomPaddingTest {

    // None (e.g. a hidden bar), gesture navigation, three-button navigation, and a large taskbar.
    private val systemBarInsets = listOf(0.dp, 24.dp, 48.dp, 60.dp)

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
    fun `normal nav - the Sync hint's lift is cleared too`() {
        // labelSmall's 16sp line plus the xs gap, at font scales 1.0, 1.3 and 2.0.
        listOf(20.dp, 25.dp, 36.dp).forEach { hint ->
            systemBarInsets.forEach { systemBar ->
                val addFabTop = systemBar + Dimensions.Component.fabBottomInset + hint + Dimensions.Component.fab +
                    Spacing.smd + smallFabSlot - (smallFabSlot - smallFabVisible) / 2
                val padding = homeListBottomPadding(systemBar, floatingNav = false, syncHintHeight = hint)
                assertTrue(
                    "system bar $systemBar, hint $hint: padding $padding must end ${Spacing.md} above $addFabTop",
                    padding >= addFabTop + Spacing.md
                )
            }
        }
    }

    @Test
    fun `normal nav - the stack stands above the nav bar`() {
        systemBarInsets.forEach { systemBar ->
            val navBarTop = systemBar + Dimensions.Component.bottomBarHeight
            val stackBottom = homeFabStackBottomPadding(systemBar)
            assertTrue("system bar $systemBar: stack bottom $stackBottom must clear $navBarTop", stackBottom > navBarTop)
        }
    }

    @Test
    fun `floating nav - the list scrolls clear of the action docked above Ask`() {
        systemBarInsets.forEach { systemBar ->
            val dockTop = systemBar + Dimensions.Component.floatingNavBottomGap +
                Dimensions.Component.floatingNavHeight + Spacing.sm + Dimensions.Component.dockedActionSmallSize
            val padding = homeListBottomPadding(systemBar, floatingNav = true)
            assertTrue(
                "system bar $systemBar: padding $padding must end ${Spacing.md} above $dockTop",
                padding >= dockTop + Spacing.md
            )
        }
    }
}
