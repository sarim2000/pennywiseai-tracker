package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.cos
import kotlin.math.sin

/**
 * The silhouettes the mascot can take. The owl is the assistant; the others are what it
 * becomes for a moment: a coin for income, a receipt for an expense or card purchase, a
 * wallet for a transfer, a jar for an investment, a message bubble for an SMS scan. A
 * chat reply takes its topic's: a pie for categories, bars for a month comparison. An
 * empty screen takes its subject's: a jar for goals, a bell for bills and subscriptions, a
 * wallet for accounts, a gauge for budgets, a bubble for messages.
 */
enum class MascotShape { Owl, Coin, Receipt, Wallet, Jar, Bubble, Pie, Bars, Bell, Gauge }

/**
 * Where a shape's pill eyes sit in the 0..100 box: the centre between them, the gap
 * between their centres, and a size factor.
 */
internal data class EyeSpec(val x: Float, val y: Float, val spacing: Float, val scale: Float)

/** The detailed owl's eye discs, in box units. */
internal object OwlEye {
    const val LEFT_X = 35f
    const val RIGHT_X = 65f
    const val Y = 50f
    const val DISC_R = 13f
    const val IRIS_R = 7f
    const val PUPIL_R = 3.5f

    /** How far the iris moves for a full gaze (|gaze| = 1). */
    const val GAZE_TRAVEL = 4f
}

/** A shape's paths, built once per drawing cache. */
internal class MascotGeometry(
    val body: Path,
    /** The pill eyes of the two-tone (24/32dp) drawing. */
    val eyes: EyeSpec,
    /** The point the head tilts and hops about: where it stands. */
    val pivot: Offset,
    /** The detailed owl's face mask (two discs and the beak's wedge), or null. */
    val mask: Path? = null,
    val beak: Path? = null,
    /** An object's markings, drawn over its body at [detailAlpha], at the detailed size only. */
    val details: Path? = null,
    val detailAlpha: Float = DETAIL_ALPHA,
)

/** How faintly an object's markings sit over its body. */
private const val DETAIL_ALPHA = 0.32f

/**
 * A shape's paths; [value] is its figure where it has one, 0..1: a [MascotShape.Pie]'s
 * slice share, how full a [MascotShape.Jar] is, how much of a [MascotShape.Gauge] is used.
 */
internal fun MascotShape.geometry(value: Float = 0f): MascotGeometry = when (this) {
    MascotShape.Owl -> MascotGeometry(
        // Ear tufts, a round belly: the PennyWise logo owl.
        body = svgPath(
            "M50 26 C40 26 29 20 20 11 C22 21 18 29 16 37 C12 46 12 60 18 72 C25 85 38 91 50 91 " +
                "C62 91 75 85 82 72 C88 60 88 46 84 37 C82 29 78 21 80 11 C71 20 60 26 50 26 Z"
        ),
        eyes = EyeSpec(x = 50f, y = 52f, spacing = 28f, scale = 1.05f),
        pivot = STANDING,
        // One non-zero fill, so every contour winds clockwise like the wedge: a
        // counter-clockwise disc would cancel it where they overlap and leave holes.
        mask = Path().apply {
            addOval(circle(OwlEye.LEFT_X, OwlEye.Y, 18f), Path.Direction.Clockwise)
            addOval(circle(OwlEye.RIGHT_X, OwlEye.Y, 18f), Path.Direction.Clockwise)
            addPath(svgPath("M33 62 L67 62 L50 76 Z"))
        },
        beak = svgPath("M45.5 61 L54.5 61 L50 69 Z"),
    )
    MascotShape.Coin -> MascotGeometry(
        body = Path().apply { addOval(circle(50f, 52f, 38f)) },
        eyes = EyeSpec(x = 50f, y = 48f, spacing = 20f, scale = 1f),
        pivot = STANDING,
        // The milled rim.
        details = Path().apply {
            addOval(circle(50f, 52f, 32.5f))
            addOval(circle(50f, 52f, 29.5f))
            fillType = PathFillType.EvenOdd
        },
    )
    // A till slip with a torn edge.
    MascotShape.Receipt -> MascotGeometry(
        body = svgPath(
            "M28 14 H72 Q76 14 76 18 V80 L71.7 74 L67.3 80 L63 74 L58.7 80 L54.3 74 L50 80 " +
                "L45.7 74 L41.3 80 L37 74 L32.7 80 L28.3 74 L24 80 V18 Q24 14 28 14 Z"
        ),
        eyes = EyeSpec(x = 50f, y = 36f, spacing = 16f, scale = 0.9f),
        pivot = STANDING,
        details = union(roundRect(36f, 52f, 28f, 3.5f, 2f), roundRect(36f, 60f, 18f, 3.5f, 2f)),
    )
    MascotShape.Wallet -> MascotGeometry(
        body = union(roundRect(12f, 27f, 72f, 56f, 11f), roundRect(60f, 43f, 30f, 24f, 9f)),
        eyes = EyeSpec(x = 38f, y = 54f, spacing = 16f, scale = 0.95f),
        pivot = STANDING,
        details = Path().apply { addOval(circle(75f, 55f, 4f)) },
    )
    // Lid, neck and a round-shouldered jar, filled from the bottom as far as it is full.
    MascotShape.Jar -> MascotGeometry(
        body = union(roundRect(33f, 9f, 34f, 10f, 4f), roundRect(38f, 17f, 24f, 10f, 2f), roundRect(20f, 25f, 60f, 66f, 17f)),
        eyes = EyeSpec(x = 50f, y = 50f, spacing = 18f, scale = 1f),
        pivot = STANDING,
        details = jarFillTop(value).takeIf { it < JAR_BOTTOM }?.let { top ->
            Path.combine(
                PathOperation.Intersect,
                roundRect(20f, 25f, 60f, 66f, 17f),
                Path().apply { addRect(Rect(20f, top, 80f, JAR_BOTTOM)) },
            )
        },
        detailAlpha = JAR_FILL_ALPHA,
    )
    MascotShape.Bubble -> MascotGeometry(
        body = union(roundRect(12f, 16f, 76f, 56f, 18f), svgPath("M27 68 L22 88 L45 68 Z")),
        eyes = EyeSpec(x = 50f, y = 43f, spacing = 18f, scale = 1f),
        pivot = STANDING,
    )
    MascotShape.Pie -> pie(value)
    // Three rising bars that overlap into one stepped outline; the eyes sit on the middle one.
    MascotShape.Bars -> MascotGeometry(
        body = union(roundRect(14f, 52f, 25f, 38f, 7f), roundRect(37.5f, 32f, 25f, 58f, 7f), roundRect(61f, 16f, 25f, 74f, 7f)),
        eyes = EyeSpec(x = 50f, y = 50f, spacing = 12f, scale = 0.75f),
        pivot = STANDING,
    )
    // A handbell with its clapper below; it swings from the handle.
    MascotShape.Bell -> MascotGeometry(
        body = union(
            svgPath("M50 15 C33 15 26 30 26 46 V65 L16 77 Q15 80 19 80 H81 Q85 80 84 77 L74 65 V46 C74 30 67 15 50 15 Z"),
            Path().apply { addOval(circle(50f, 12f, 5f)) },
            Path().apply { addOval(circle(50f, 85f, 7f)) },
        ),
        eyes = EyeSpec(x = 50f, y = 50f, spacing = 16f, scale = 0.95f),
        pivot = Offset(50f, 8f),
    )
    // A half-dial on a base; the needle sweeps from left (nothing used) to right (all of it).
    MascotShape.Gauge -> MascotGeometry(
        body = svgPath("M12 76 A38 38 0 0 1 88 76 V82 Q88 88 82 88 H18 Q12 88 12 82 Z"),
        eyes = EyeSpec(x = 50f, y = 52f, spacing = 20f, scale = 0.95f),
        pivot = STANDING,
        details = needle(gaugeNeedleTip(value)),
        detailAlpha = NEEDLE_ALPHA,
    )
}

/** Where a [MascotShape.Jar]'s contents reach for a fill of 0..1: the jar's bottom when empty. */
internal fun jarFillTop(fill: Float): Float = JAR_BOTTOM - JAR_DEPTH * fill.coerceIn(0f, 1f)

private const val JAR_BOTTOM = 91f
private const val JAR_DEPTH = 66f
private const val JAR_FILL_ALPHA = 0.22f

/** The tip of a [MascotShape.Gauge]'s needle for a share used of 0..1: left at none, up at half, right at all. */
internal fun gaugeNeedleTip(used: Float): Offset {
    val tip = polar(180f + 180f * used.coerceIn(0f, 1f), NEEDLE_LENGTH)
    return GAUGE_HUB + tip
}

/** A round-ended needle from the hub to [tip], with the hub's cap. */
private fun needle(tip: Offset): Path {
    val along = tip - GAUGE_HUB
    val side = Offset(-along.y, along.x) * (NEEDLE_WIDTH / 2 / along.getDistance())
    return union(
        Path().apply {
            moveTo(GAUGE_HUB.x + side.x, GAUGE_HUB.y + side.y)
            lineTo(tip.x + side.x, tip.y + side.y)
            lineTo(tip.x - side.x, tip.y - side.y)
            lineTo(GAUGE_HUB.x - side.x, GAUGE_HUB.y - side.y)
            close()
        },
        Path().apply { addOval(circle(tip.x, tip.y, NEEDLE_WIDTH / 2)) },
        Path().apply { addOval(circle(GAUGE_HUB.x, GAUGE_HUB.y, GAUGE_HUB_R)) },
    )
}

private val GAUGE_HUB = Offset(50f, 78f)
private const val GAUGE_HUB_R = 3.4f
private const val NEEDLE_LENGTH = 15f
private const val NEEDLE_WIDTH = 3.2f
private const val NEEDLE_ALPHA = 0.55f

/**
 * A pie with its first slice, [share] of it from twelve o'clock clockwise, pulled out
 * along its middle. The eyes sit on the rest, toward its middle, so however big the
 * slice it never takes an eye.
 */
private fun pie(share: Float): MascotGeometry {
    val sweep = 360f * share.coerceIn(PIE_MIN_SHARE, PIE_MAX_SHARE)
    val start = -90f
    val pull = polar(start + sweep / 2, PIE_PULL)
    val eyes = polar(start + sweep + (360f - sweep) / 2, PIE_EYES_OUT)
    val c = Offset(50f, 54f)
    return MascotGeometry(
        body = Path().apply {
            moveTo(c.x, c.y)
            arcTo(circle(c.x, c.y, PIE_R), start + sweep, 360f - sweep, forceMoveTo = false)
            close()
            moveTo(c.x + pull.x, c.y + pull.y)
            arcTo(circle(c.x + pull.x, c.y + pull.y, PIE_R), start, sweep, forceMoveTo = false)
            close()
        },
        eyes = EyeSpec(x = c.x + eyes.x, y = c.y + eyes.y, spacing = 16f, scale = 0.9f),
        pivot = STANDING,
    )
}

/** The slice is drawn at least this big, to read as a slice, and at most half the pie. */
private const val PIE_MIN_SHARE = 0.05f
private const val PIE_MAX_SHARE = 0.5f
private const val PIE_R = 36f
private const val PIE_PULL = 6f

/** How far from the pie's centre the eyes sit, along the middle of the rest of it. */
private const val PIE_EYES_OUT = 14f

/** A point [r] from the origin at [degrees], clockwise from three o'clock (y down). */
private fun polar(degrees: Float, r: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
}

/** Where every shape stands, so they all tilt and hop about the same point. */
private val STANDING = Offset(50f, 88f)

internal fun circle(x: Float, y: Float, r: Float) = Rect(Offset(x, y), r)

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r))) }

/** One outline for overlapping parts, whatever way each one winds. */
private fun union(vararg parts: Path): Path = parts.reduce { a, b -> Path.combine(PathOperation.Union, a, b) }

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
