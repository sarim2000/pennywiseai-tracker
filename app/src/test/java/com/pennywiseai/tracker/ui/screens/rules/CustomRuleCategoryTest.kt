package com.pennywiseai.tracker.ui.screens.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRuleCategoryTest {
    @Test
    fun `a custom value always shows its field, even when it arrives after the first frame`() {
        assertTrue(isCustomRuleCategory("Rent", otherChosen = false))
    }

    @Test
    fun `a shortcut or nothing shows only the chips`() {
        assertFalse(isCustomRuleCategory("Shopping", otherChosen = false))
        assertFalse(isCustomRuleCategory("", otherChosen = false))
    }

    @Test
    fun `the field stays up while typing through blank or a shortcut's name`() {
        assertTrue(isCustomRuleCategory("", otherChosen = true))
        assertTrue(isCustomRuleCategory("Shopping", otherChosen = true))
    }
}
