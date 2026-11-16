package com.pennywiseai.tracker.presentation.insights

import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightFact
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.insights.InsightsSnapshot
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.reviewRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class InsightsUiStateTest {
    private val range = reviewRange(ReviewKind.MONTH, 0, LocalDate.parse("2026-11-20"), 1, DayOfWeek.MONDAY)

    private fun currency(code: String, count: Int) =
        CurrencyReview(code, BigDecimal.TEN, null, BigDecimal.ZERO, count, emptyList(), emptyList())

    private fun insight(id: String, currency: String) =
        Insight(id, InsightSeverity.GOOD, currency, InsightFact.BudgetsOnTrack(1), null)

    private fun snapshot(
        currencies: List<CurrencyReview>,
        insights: List<Insight>,
        dismissed: Set<String> = emptySet(),
        hasOlder: Boolean = false,
    ) = InsightsSnapshot(PeriodReview(range, currencies, null, null, hasOlder), insights, dismissed)

    @Test
    fun `a new user's page is empty`() {
        val state = insightsUiState(snapshot(emptyList(), emptyList()), null)
        assertTrue(state.isEmpty)
        assertFalse(state.hasHistory)
        assertNull(state.currency)
    }

    @Test
    fun `an empty new cycle after earlier ones is not a new user`() {
        val state = insightsUiState(snapshot(emptyList(), emptyList(), hasOlder = true), null)
        assertTrue(state.isEmpty)
        assertTrue(state.hasHistory)
    }

    @Test
    fun `the page shows one currency at a time, the busiest first`() {
        val state = insightsUiState(
            snapshot(
                listOf(currency("INR", 30), currency("USD", 2)),
                listOf(insight("a", "USD"), insight("b", "INR"), insight("c", "AED")),
            ),
            chosen = null,
        )
        assertEquals(listOf("INR", "USD", "AED"), state.currencies)
        assertEquals("INR", state.currency)
        assertEquals(listOf("b"), state.insights.map { it.id })

        val usd = insightsUiState(snapshot(listOf(currency("INR", 30), currency("USD", 2)), listOf(insight("a", "USD"))), "USD")
        assertEquals("USD", usd.review?.currency)
        assertEquals(listOf("a"), usd.insights.map { it.id })
    }

    @Test
    fun `dismissed insights move aside, and a gone currency falls back`() {
        val state = insightsUiState(
            snapshot(listOf(currency("INR", 3)), listOf(insight("a", "INR"), insight("b", "INR")), setOf("a")),
            chosen = "EUR",
        )
        assertEquals("INR", state.currency)
        assertEquals(listOf("b"), state.insights.map { it.id })
        assertEquals(listOf("a"), state.dismissed.map { it.id })
    }
}
