package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/*
 * The record-keeping cues: rules applied ([MascotCue.Sorted]), a statement imported
 * ([MascotCue.Read]), a backup kept safe ([MascotCue.Secured]) and a bill paid
 * ([MascotCue.Paid]). See MascotArrival.kt.
 */

/** Rules applied: the receipt looks down its lines, then takes a check. */
internal const val SORTED_END = 0.45f

internal fun sortedPose(pose: MascotPose, t: Float): MascotPose =
    lookAt(pose, Offset(0f, 0.6f), smoothstep(0f, 0.08f, t) * (1 - smoothstep(0.3f, SORTED_END, t)))

internal fun sortedCheck(t: Float): Float = smoothstep(0.25f, 0.42f, t)

/** A statement imported: the eyes read the receipt top to bottom, then it takes a check. */
internal const val READ_END = 0.65f

internal fun readPose(pose: MascotPose, t: Float): MascotPose {
    val y = -0.6f + 1.4f * smoothstep(0.04f, 0.42f, t)
    val x = sin(2 * PI * 3 * smoothstep(0.04f, 0.42f, t)).toFloat() * 0.5f
    return lookAt(pose, Offset(x, y), smoothstep(0f, 0.05f, t) * (1 - smoothstep(0.46f, READ_END, t)))
}

internal fun readCheck(t: Float): Float = smoothstep(0.44f, 0.6f, t)

/** A backup kept safe: the shield stands a little taller as a check lands on it. */
internal const val SECURED_END = 0.35f

internal fun securedPose(pose: MascotPose, t: Float): MascotPose {
    val s = t / SECURED_END
    return if (s in 0f..1f) pose.copy(lift = pose.lift - SECURED_RISE * sin(PI * s).toFloat()) else pose
}

internal fun securedCheck(t: Float): Float = smoothstep(0.08f, 0.28f, t)

private const val SECURED_RISE = 3f

/** A bill paid: the bell gives a short ring, damped to rest, as a check lands. */
internal const val PAID_END = 0.45f

internal fun paidPose(pose: MascotPose, t: Float, sinceMs: Long): MascotPose {
    val rung = rung(pose, sinceMs)
    val damp = 1 - smoothstep(0.25f, PAID_END, t)
    return pose.copy(roll = pose.roll + (rung.roll - pose.roll) * damp * PAID_RING)
}

internal fun paidCheck(t: Float): Float = smoothstep(0.15f, 0.35f, t)

private const val PAID_RING = 0.6f
