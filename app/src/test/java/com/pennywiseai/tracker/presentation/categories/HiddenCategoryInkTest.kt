package com.pennywiseai.tracker.presentation.categories

import com.pennywiseai.tracker.ui.components.builtInSchemes
import com.pennywiseai.tracker.ui.theme.TEXT_CONTRAST
import com.pennywiseai.tracker.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/** A hidden category's name, on its card in every built-in scheme: dimmed, but still text. */
class HiddenCategoryInkTest {

    @Test
    fun hiddenName_reads_andIsNeverStrongerThanAVisibleOne() {
        val failures = builtInSchemes.flatMap { (name, scheme) ->
            val card = scheme.surfaceContainerLow
            val hidden = hiddenCategoryInk(scheme.onSurface, card)
            val ratio = contrastRatio(hidden, card)
            buildList {
                if (ratio < TEXT_CONTRAST) add("$name: hidden name at $ratio")
                if (ratio > contrastRatio(scheme.onSurface, card)) add("$name: hidden name stronger than a visible one")
                if (hidden.alpha < 1f) add("$name: hidden name is translucent")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
