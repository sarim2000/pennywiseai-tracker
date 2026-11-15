package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/** The silhouettes the mascot can take. The owl is the assistant; the rest arrive with their features. */
enum class MascotShape { Owl }

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
)

internal fun MascotShape.geometry(): MascotGeometry = when (this) {
    MascotShape.Owl -> MascotGeometry(
        // Ear tufts, a round belly: the PennyWise logo owl.
        body = svgPath(
            "M50 26 C40 26 29 20 20 11 C22 21 18 29 16 37 C12 46 12 60 18 72 C25 85 38 91 50 91 " +
                "C62 91 75 85 82 72 C88 60 88 46 84 37 C82 29 78 21 80 11 C71 20 60 26 50 26 Z"
        ),
        eyes = EyeSpec(x = 50f, y = 52f, spacing = 28f, scale = 1.05f),
        pivot = Offset(50f, 88f),
        // One non-zero fill, so every contour winds clockwise like the wedge: a
        // counter-clockwise disc would cancel it where they overlap and leave holes.
        mask = Path().apply {
            addOval(circle(OwlEye.LEFT_X, OwlEye.Y, 18f), Path.Direction.Clockwise)
            addOval(circle(OwlEye.RIGHT_X, OwlEye.Y, 18f), Path.Direction.Clockwise)
            addPath(svgPath("M33 62 L67 62 L50 76 Z"))
        },
        beak = svgPath("M45.5 61 L54.5 61 L50 69 Z"),
    )
}

internal fun circle(x: Float, y: Float, r: Float) = Rect(Offset(x, y), r)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
