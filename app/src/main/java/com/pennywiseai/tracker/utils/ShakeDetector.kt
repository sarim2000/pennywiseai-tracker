package com.pennywiseai.tracker.utils

/**
 * Turns raw accelerometer samples (m/s², gravity included, any orientation) into
 * shakes: it's a shake when most samples over the last half second are hard ones
 * (the approach of Square's seismic). A knock, a step or the phone set down is a
 * sample or two, not most of a window; a hard shake is hard all the way through,
 * so it doesn't need calm moments between jolts to count.
 *
 * After a shake it waits for a quiet second, so one long shake toggles once.
 *
 * ponytail: [HARD_MS2] is seismic's "medium" sensitivity, checked on a Galaxy S23.
 * Tune it first if other phones fire too easily or not at all.
 */
class ShakeDetector {
    private val window = ArrayDeque<Pair<Long, Boolean>>() // timestamp, hard
    private var hardInWindow = 0
    private var lastShakeNs = 0L

    /** True when this sample completes a shake. */
    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        val hard = x * x + y * y + z * z > HARD_MS2 * HARD_MS2
        if (lastShakeNs != 0L && timestampNs - lastShakeNs < COOLDOWN_NS) {
            if (hard) lastShakeNs = timestampNs // still shaking: wait for it to settle
            return false
        }

        window.addLast(timestampNs to hard)
        if (hard) hardInWindow++
        while (timestampNs - window.first().first > WINDOW_NS) {
            if (window.removeFirst().second) hardInWindow--
        }

        val span = timestampNs - window.first().first
        if (span < MIN_WINDOW_NS || hardInWindow < window.size * 3 / 4) return false
        window.clear()
        hardInWindow = 0
        lastShakeNs = timestampNs
        return true
    }

    private companion object {
        const val HARD_MS2 = 13f // ≈ 1.3 g
        const val WINDOW_NS = 500_000_000L
        const val MIN_WINDOW_NS = 250_000_000L
        const val COOLDOWN_NS = 1_000_000_000L
    }
}
