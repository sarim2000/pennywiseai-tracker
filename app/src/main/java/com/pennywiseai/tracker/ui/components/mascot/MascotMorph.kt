package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * How a shape change plays: one silhouette that bends from the old outline into the new
 * one, never two bodies faded over each other. Each outline is read as a polar profile —
 * how far its edge is from a point inside it along [PROFILE_RAYS] rays — and the morph
 * eases every ray from one profile to the other. Every shape is star-shaped enough from
 * its middle that the profile traces its edge; the profile is only drawn while the morph
 * runs, and the true outline takes over once it lands.
 */
internal const val PROFILE_RAYS = 180

/** The first ray points up (twelve o'clock); the rest go clockwise, y down. */
private const val FIRST_RAY = -PI / 2

/** How finely a curved contour is walked into straight edges, in box units. */
private const val CONTOUR_STEP = 0.75f

/** An outline seen from [center]: the distance to its outermost edge along each ray. */
internal class MascotProfile(val center: Offset, val radii: FloatArray)

/** The unit direction of ray [i] of [count]. */
internal fun rayDirection(i: Int, count: Int = PROFILE_RAYS): Offset {
    val a = FIRST_RAY + 2 * PI * i / count
    return Offset(cos(a).toFloat(), sin(a).toFloat())
}

/**
 * The profile of the closed [polygons] (x, y pairs; each closes on itself) from [center].
 * Each ray takes its farthest crossing, so a notch hidden behind an edge is filled in. A
 * ray that crosses nothing (a centre outside the outline) keeps the ray before it.
 */
internal fun profileOf(polygons: List<FloatArray>, center: Offset, rays: Int = PROFILE_RAYS): MascotProfile {
    val radii = FloatArray(rays)
    for (i in 0 until rays) {
        val d = rayDirection(i, rays)
        var far = -1f
        for (poly in polygons) {
            val n = poly.size / 2
            for (k in 0 until n) {
                val px = poly[2 * k]
                val py = poly[2 * k + 1]
                val qx = poly[(2 * k + 2) % poly.size]
                val qy = poly[(2 * k + 3) % poly.size]
                val t = rayHit(center, d, px, py, qx, qy)
                if (t > far) far = t
            }
        }
        radii[i] = if (far >= 0f) far else if (i > 0) radii[i - 1] else 0f
    }
    return MascotProfile(center, radii)
}

/** How far along the ray from [o] in [d] it crosses the segment p..q, or -1 if it doesn't. */
private fun rayHit(o: Offset, d: Offset, px: Float, py: Float, qx: Float, qy: Float): Float {
    val ex = qx - px
    val ey = qy - py
    val denom = d.x * ey - d.y * ex
    if (abs(denom) < 1e-6f) return -1f
    val wx = px - o.x
    val wy = py - o.y
    val t = (wx * ey - wy * ex) / denom
    val s = (wx * d.y - wy * d.x) / denom
    return if (t >= 0f && s in 0f..1f) t else -1f
}

/**
 * [a] bent [w] of the way to [b]. A spring past 1 carries the outline a little past [b]
 * and back, so the new shape settles rather than stops; a ray never turns inside out.
 */
internal fun blendProfiles(a: MascotProfile, b: MascotProfile, w: Float): MascotProfile {
    val radii = FloatArray(a.radii.size) { i -> max(0f, a.radii[i] + (b.radii[i] - a.radii[i]) * w) }
    return MascotProfile(lerp(a.center, b.center, w), radii)
}

/** The profile as one smooth closed outline: each ray's edge point, joined through midpoints. */
internal fun MascotProfile.toPath(into: Path): Path {
    into.reset()
    val n = radii.size
    fun x(i: Int) = center.x + rayDirection(i % n, n).x * radii[i % n]
    fun y(i: Int) = center.y + rayDirection(i % n, n).y * radii[i % n]
    into.moveTo((x(n - 1) + x(0)) / 2, (y(n - 1) + y(0)) / 2)
    for (i in 0 until n) into.quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2, (y(i) + y(i + 1)) / 2)
    into.close()
    return into
}

/** A shape's outline as closed polygons, walked from its path one contour at a time. */
internal fun Path.toPolygons(step: Float = CONTOUR_STEP): List<FloatArray> {
    val measure = android.graphics.PathMeasure(asAndroidPath(), true)
    val pos = FloatArray(2)
    val out = mutableListOf<FloatArray>()
    do {
        val length = measure.length
        if (length <= 0f) continue
        val n = max(8, (length / step).toInt())
        val poly = FloatArray(n * 2)
        for (k in 0 until n) {
            measure.getPosTan(length * k / n, pos, null)
            poly[2 * k] = pos[0]
            poly[2 * k + 1] = pos[1]
        }
        out += poly
    } while (measure.nextContour())
    return out
}

/** A shape's profile, seen from the middle of its outline's bounds. */
internal fun MascotGeometry.profile(): MascotProfile = profileOf(body.toPolygons(), body.getBounds().center)

/**
 * The lids through a morph at [progress]: they narrow to dots in the middle and open as
 * the new shape lands, so the eyes never hang open over a body that isn't there yet.
 */
internal fun morphSquint(progress: Float): Float = MORPH_SQUINT * sin(PI * progress.coerceIn(0f, 1f)).toFloat()

private const val MORPH_SQUINT = 0.85f

/** Where a shape's eyes are [w] of the way from [a] to [b]. */
internal fun lerpEyes(a: EyeSpec, b: EyeSpec, w: Float) = EyeSpec(
    x = lerp(a.x, b.x, w),
    y = lerp(a.y, b.y, w),
    spacing = lerp(a.spacing, b.spacing, w),
    scale = lerp(a.scale, b.scale, w),
)

internal fun lerp(a: Offset, b: Offset, w: Float) = Offset(lerp(a.x, b.x, w), lerp(a.y, b.y, w))

private fun lerp(a: Float, b: Float, w: Float) = a + (b - a) * w

/** Where a morph began: the outline, eyes and tilt point on screen then, and the shape it most was. */
internal class MorphFrom(val profile: MascotProfile, val eyes: EyeSpec, val pivot: Offset, val shape: MascotShape)
