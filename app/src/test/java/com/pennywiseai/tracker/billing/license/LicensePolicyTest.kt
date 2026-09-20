package com.pennywiseai.tracker.billing.license

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LicensePolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_000L * day

    @Test
    fun `fresh license grants pro and is not due`() {
        val validated = now - 2 * day
        assertTrue(LicensePolicy.grantsPro(validated, now))
        assertFalse(LicensePolicy.isDue(validated, now))
    }

    @Test
    fun `after 30 days a recheck is due but pro stays on`() {
        val validated = now - 31 * day
        assertTrue(LicensePolicy.isDue(validated, now))
        assertTrue(LicensePolicy.grantsPro(validated, now))
    }

    @Test
    fun `after 60 days without a successful check pro lapses`() {
        val validated = now - 60 * day
        assertFalse(LicensePolicy.grantsPro(validated, now))
    }

    @Test
    fun `never validated grants nothing`() {
        assertFalse(LicensePolicy.grantsPro(0L, now))
    }

    @Test
    fun `clock rolled back before the last validation grants nothing and forces a recheck`() {
        val validated = now + 5 * day
        assertFalse(LicensePolicy.grantsPro(validated, now))
        assertTrue(LicensePolicy.isDue(validated, now))
    }
}
