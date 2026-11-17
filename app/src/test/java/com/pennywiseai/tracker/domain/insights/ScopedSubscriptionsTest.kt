package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ScopedSubscriptionsTest {
    private val business = 2L

    private val balances = listOf(
        account("1111", ProfileEntity.PERSONAL_ID),
        account("2222", business),
    )

    private fun account(last4: String, profileId: Long) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal.TEN,
        timestamp = LocalDateTime.parse("2026-11-01T00:00"), profileId = profileId,
    )

    private fun subscription(id: Long, last4: String?) = SubscriptionEntity(
        id = id, merchantName = "Bill $id", amount = BigDecimal.TEN, nextPaymentDate = null,
        bankName = last4?.let { "Test Bank" }, accountLast4 = last4,
    )

    private val subscriptions = listOf(subscription(1, "1111"), subscription(2, "2222"), subscription(3, null))

    private fun scoped(profileId: Long?, ignored: Set<String> = emptySet()) =
        scopedSubscriptions(subscriptions, ignored, profileId, balances).map { it.id }

    @Test
    fun `all profiles keep every subscription`() {
        assertEquals(listOf(1L, 2L, 3L), scoped(null))
    }

    @Test
    fun `a profile keeps only bills paid from its accounts`() {
        assertEquals(listOf(2L), scoped(business))
    }

    @Test
    fun `a bill with no known account counts as Personal, as a transaction does`() {
        assertEquals(listOf(1L, 3L), scoped(ProfileEntity.PERSONAL_ID))
    }

    @Test
    fun `a bill paid from an ignored account is left out`() {
        assertEquals(listOf(1L, 3L), scoped(null, ignored = setOf("Test Bank_2222")))
    }
}
