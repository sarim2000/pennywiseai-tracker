package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.ui.components.GlyphChange.Enter
import com.pennywiseai.tracker.ui.components.GlyphChange.Roll
import com.pennywiseai.tracker.ui.components.GlyphChange.Same
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerDiffTest {

    private fun changes(old: String, new: String) = checkNotNull(odometerPlan(old, new)).moves.map { it.change }

    @Test
    fun `only the digit that changed rolls`() {
        assertEquals(listOf(Same, Same, Same, Same, Roll, Same), changes("₹1,234", "₹1,284"))
    }

    @Test
    fun `symbols and separators stay put`() {
        val plan = checkNotNull(odometerPlan("1.234,56 €", "1.234,57 €"))
        assertEquals(Roll, plan.moves[7].change)
        assertTrue(plan.moves.filterIndexed { i, _ -> i != 7 }.all { it.change == Same })
        assertTrue(plan.exits.isEmpty())
    }

    @Test
    fun `a longer amount keeps units over units and brings the new digits in`() {
        val plan = checkNotNull(odometerPlan("₹999", "₹1,000"))
        assertEquals(listOf(Same, Enter, Enter, Roll, Roll, Roll), plan.moves.map { it.change })
        // The three nines pair with the last three digits, the symbol with the symbol.
        assertEquals(listOf(0, null, null, 1, 2, 3), plan.moves.map { it.oldIndex })
        assertTrue(plan.exits.isEmpty())
        assertTrue(plan.rollsUp)
    }

    @Test
    fun `a shorter amount rolls the dropped digits out`() {
        val plan = checkNotNull(odometerPlan("₹1,000", "₹999"))
        assertEquals(listOf(Same, Roll, Roll, Roll), plan.moves.map { it.change })
        assertEquals(listOf(1, 2), plan.exits.map { it.oldIndex })
        assertFalse(plan.rollsUp)
    }

    @Test
    fun `a minus sign appearing keeps the currency symbol paired`() {
        val plan = checkNotNull(odometerPlan("₹5", "-₹5"))
        assertEquals(listOf(Enter, Same, Same), plan.moves.map { it.change })
        assertFalse(plan.rollsUp)
    }

    @Test
    fun `units roll first, higher digits follow`() {
        val plan = checkNotNull(odometerPlan("₹1,199", "₹1,200"))
        val ranks = plan.moves.filter { it.change == Roll }.map { it.newIndex to it.rank }
        assertEquals(listOf(3 to 2, 4 to 1, 5 to 0), ranks)
    }

    @Test
    fun `non-Latin digits roll too`() {
        assertEquals(listOf(Same, Roll), changes("١٢", "١٣"))
    }

    @Test
    fun `text without digits has nothing to roll`() {
        assertNull(odometerPlan("No budget", "₹100"))
        assertNull(odometerPlan("₹100", "••••"))
    }

    @Test
    fun `the roll direction follows the value`() {
        assertEquals(1, compareAmounts("₹9.99", "₹10.00"))
        assertEquals(-1, compareAmounts("₹10.00", "₹9.99"))
        assertEquals(1, compareAmounts("₹999", "₹1,000"))
        assertEquals(1, compareAmounts("-₹50", "₹5"))
        assertEquals(-1, compareAmounts("₹5", "-₹50"))
        assertEquals(0, compareAmounts("₹5", "₹5"))
        // A thousands group of three is not a fraction.
        assertEquals(-1, compareAmounts("1.234,50 €", "234,50 €"))
    }

    @Test
    fun `amounts with a different number of decimals compare by value`() {
        assertEquals(-1, compareAmounts("₹1,000", "₹999.5"))
        assertEquals(-1, compareAmounts("$461", "$448.6"))
        assertEquals(1, compareAmounts("$448.6", "$461"))
        assertEquals(1, compareAmounts("$448.6", "$448.65"))
        assertEquals(0, compareAmounts("$5", "$5.00"))
        assertNull(compareAmounts("••••", "$5"))
    }

    @Test
    fun `gaining decimals keeps integer over integer and brings the fraction in`() {
        val plan = checkNotNull(odometerPlan("$461", "$448.6"))
        // `$` and the hundreds stay; tens and units roll; `.6` enters.
        assertEquals(listOf(Same, Same, Roll, Roll, Enter, Enter), plan.moves.map { it.change })
        assertEquals(listOf(0, 1, 2, 3, null, null), plan.moves.map { it.oldIndex })
        assertTrue(plan.exits.isEmpty())
        assertFalse(plan.rollsUp)
    }

    @Test
    fun `losing decimals rolls the fraction out`() {
        val plan = checkNotNull(odometerPlan("$448.6", "$461"))
        assertEquals(listOf(Same, Same, Roll, Roll), plan.moves.map { it.change })
        assertEquals(listOf(4, 5), plan.exits.map { it.oldIndex })
        assertTrue(plan.rollsUp)
    }

    @Test
    fun `a longer fraction aligns from the separator`() {
        val plan = checkNotNull(odometerPlan("1.234,5 €", "1.234,56 €"))
        assertEquals(Enter, plan.moves[7].change)
        assertTrue(plan.moves.filterIndexed { i, _ -> i != 7 }.all { it.change == Same })
        assertTrue(plan.exits.isEmpty())
    }

    @Test
    fun `an unchanged amount has nothing to move`() {
        assertFalse(checkNotNull(odometerPlan("₹42.00", "₹42.00")).changed)
    }

    @Test
    fun `three-decimal currencies read the decimal from either string`() {
        // KWD 12.345 is twelve dinars, not 12,345: the other string's `.5` settles it.
        assertEquals(1, compareAmounts("KWD 12.345", "KWD 15.5"))
        assertEquals(-1, compareAmounts("KWD 15.5", "KWD 12.345"))
        assertEquals(1, compareAmounts("KWD 1,012.345", "KWD 1,015.5"))
        val plan = checkNotNull(odometerPlan("KWD 12.345", "KWD 15.5"))
        // `12` over `15` (units roll), `.` stays, `3` rolls to `5`, `45` roll out.
        assertEquals(listOf(Same, Same, Same, Same, Same, Roll, Same, Roll), plan.moves.map { it.change })
        assertEquals(listOf(8, 9), plan.exits.map { it.oldIndex })
    }

    @Test
    fun `a lone three-digit group is grouping in both strings`() {
        assertEquals(',', decimalSeparatorOf("1.234,5 €", "999 €"))
        assertNull(decimalSeparatorOf("₹19,164", "₹10,700"))
        assertEquals(-1, compareAmounts("₹19,164", "₹10,700"))
        assertEquals(1, compareAmounts("1.234 €", "1.235 €"))
    }
}
