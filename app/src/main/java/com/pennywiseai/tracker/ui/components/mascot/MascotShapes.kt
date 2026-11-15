package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The silhouettes the mascot can take. The owl is the assistant; the others are what it
 * becomes for a moment: a coin for income, a receipt for an expense or card purchase, a
 * wallet for a transfer, a jar for an investment, a message bubble for an SMS scan.
 */
enum class MascotShape { Owl, Coin, Receipt, Wallet, Jar, Bubble }

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
    /** An object's markings, drawn faintly over its body at the detailed size only. */
    val details: Path? = null,
)

internal fun MascotShape.geometry(): MascotGeometry = when (this) {
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
    // Lid, neck and a round-shouldered jar.
    MascotShape.Jar -> MascotGeometry(
        body = union(roundRect(33f, 9f, 34f, 10f, 4f), roundRect(38f, 17f, 24f, 10f, 2f), roundRect(20f, 25f, 60f, 66f, 17f)),
        eyes = EyeSpec(x = 50f, y = 50f, spacing = 18f, scale = 1f),
        pivot = STANDING,
    )
    MascotShape.Bubble -> MascotGeometry(
        body = union(roundRect(12f, 16f, 76f, 56f, 18f), svgPath("M27 68 L22 88 L45 68 Z")),
        eyes = EyeSpec(x = 50f, y = 43f, spacing = 18f, scale = 1f),
        pivot = STANDING,
    )
}

/** Where every shape stands, so they all tilt and hop about the same point. */
private val STANDING = Offset(50f, 88f)

internal fun circle(x: Float, y: Float, r: Float) = Rect(Offset(x, y), r)

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r))) }

/** One outline for overlapping parts, whatever way each one winds. */
private fun union(vararg parts: Path): Path = parts.reduce { a, b -> Path.combine(PathOperation.Union, a, b) }

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
