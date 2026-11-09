package com.pennywiseai.tracker.worker.smsscan

import java.util.concurrent.atomic.AtomicInteger

private const val ETA_WINDOW_MS = 2000L

/**
 * Thread-safe processing stats with a self-correcting sliding-window rate estimator.
 *
 * Root cause of wrong ETA: a 200-slot ring at 500 msg/s wraps in 0.4 s, so
 * msgPerSec() was measuring only ~0.4 s instead of the intended 2 s window —
 * producing a 5× inflated rate and a 5× underestimated ETA.
 *
 * Fix:
 *  1. Ring size 8192 (power-of-2 for cheap bitmask modulo) — handles up to
 *     ~2730 msg/s within ETA_WINDOW_MS = 3 s without wrapping.
 *  2. Bounds check: only count timestamps that are BOTH > cutoff AND <= now,
 *     so stale zeros from the uninitialized ring never inflate the count.
 *  3. Warmup fallback: if the window has fewer than MIN_SAMPLES entries, fall
 *     back to the overall elapsed average — ETA is always a real number from t=0.
 *  4. ETA clamped to 0 — can't go negative when processed > total in a race.
 *
 * Self-correction: msgPerSec() recomputes from the live ring on every call, so a
 * sudden slowdown (e.g. save coroutine hits a slow DB write) is reflected within
 * one ETA_WINDOW_MS interval automatically.
 */
internal class ProcessingStats(val total: Int) {
    val processed = AtomicInteger(0)
    val parsed    = AtomicInteger(0)
    val saved     = AtomicInteger(0)
    val duplicates = AtomicInteger(0)
    val blocked   = AtomicInteger(0)
    val startTime = System.currentTimeMillis()

    private val RING_SIZE          = 8192
    private val MIN_SAMPLES        = 10
    private val ring               = java.util.concurrent.atomic.AtomicLongArray(RING_SIZE)
    private val head               = AtomicInteger(0)
    private val writtenCount       = AtomicInteger(0)

    fun recordCompletion() {
        val pos = head.getAndIncrement() and (RING_SIZE - 1)
        ring.lazySet(pos, System.currentTimeMillis())
        if (writtenCount.get() < RING_SIZE) writtenCount.incrementAndGet()
    }

    fun elapsedMs() = System.currentTimeMillis() - startTime

    fun msgPerSec(): Float {
        val now      = System.currentTimeMillis()
        val cutoff   = now - ETA_WINDOW_MS
        var count    = 0
        val mask     = RING_SIZE - 1
        val limit    = writtenCount.get()
        val currentHead = head.get()
        for (i in 0 until limit) {
            val pos = (currentHead - 1 - i) and mask
            val ts  = ring.get(pos)
            if (ts in (cutoff + 1)..now) count++
        }
        return when {
            count >= MIN_SAMPLES ->
                count / (ETA_WINDOW_MS / 1000f)

            else -> {
                val elapsedSec = elapsedMs() / 1000f
                val done       = processed.get()
                if (elapsedSec > 0.1f && done > 0) done / elapsedSec else 0f
            }
        }
    }

    fun etaSec(): Int {
        val mps       = msgPerSec()
        val remaining = total - processed.get()
        return if (mps > 0f && remaining > 0) maxOf(0, (remaining / mps).toInt()) else 0
    }
}
