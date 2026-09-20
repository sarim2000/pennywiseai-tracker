package com.pennywiseai.tracker.billing.license

import java.util.concurrent.TimeUnit

/**
 * Offline policy for a stored license, kept pure so it's unit-testable.
 *
 * A key is re-validated against Dodo every [REVALIDATE_AFTER_MS]. If the
 * device can't reach Dodo, Pro stays on for a further grace window; only
 * after [EXPIRE_AFTER_MS] with no successful check does entitlement lapse.
 * A definitive "invalid" answer from Dodo revokes immediately regardless.
 *
 * The wall clock is user-controlled, so a clock set *earlier* than the last
 * validation is treated as "unknown": it forces a re-check and grants
 * nothing until Dodo answers. Rolling the clock back therefore can't extend
 * a revoked license.
 */
object LicensePolicy {
    val REVALIDATE_AFTER_MS: Long = TimeUnit.DAYS.toMillis(30)
    val EXPIRE_AFTER_MS: Long = TimeUnit.DAYS.toMillis(60)

    /** True when the last successful validation is old enough — or the clock moved backwards. */
    fun isDue(lastValidatedAt: Long, now: Long): Boolean =
        now < lastValidatedAt || now - lastValidatedAt >= REVALIDATE_AFTER_MS

    /** True while the stored license still grants Pro without a fresh check. */
    fun grantsPro(lastValidatedAt: Long, now: Long): Boolean =
        lastValidatedAt > 0 && now >= lastValidatedAt && now - lastValidatedAt < EXPIRE_AFTER_MS
}
