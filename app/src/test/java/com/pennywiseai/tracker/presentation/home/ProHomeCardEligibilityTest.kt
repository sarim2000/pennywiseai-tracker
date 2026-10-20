package com.pennywiseai.tracker.presentation.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProHomeCardEligibilityTest {
    private val now = TimeUnit.DAYS.toMillis(1_000)
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    private fun eligible(
        isPro: Boolean? = false,
        handled: Boolean = false,
        installedAt: Long = daysAgo(PRO_CARD_MIN_INSTALL_DAYS),
        count: Int = PRO_CARD_MIN_TRANSACTIONS,
        sharePrompt: Boolean = false,
    ) = shouldShowProHomeCard(isPro, handled, installedAt, now, count, sharePrompt)

    @Test fun `free user at both thresholds sees the card`() = assertTrue(eligible())

    @Test fun `installed under 14 days hides it`() =
        assertFalse(eligible(installedAt = daysAgo(PRO_CARD_MIN_INSTALL_DAYS) + 1))

    @Test fun `under 30 transactions hides it`() =
        assertFalse(eligible(count = PRO_CARD_MIN_TRANSACTIONS - 1))

    @Test fun `pro users (including F-Droid) never see it`() = assertFalse(eligible(isPro = true))

    @Test fun `unresolved entitlement hides it`() = assertFalse(eligible(isPro = null))

    @Test fun `handled card never comes back`() = assertFalse(eligible(handled = true))

    @Test fun `never stacks with the share banner`() = assertFalse(eligible(sharePrompt = true))
}
