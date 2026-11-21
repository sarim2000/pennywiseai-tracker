package com.pennywiseai.tracker.ui.components.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Long enough to read as a cheer, short enough to be gone before the user reaches for the screen. */
private const val BURST_MILLIS = 1_200
private const val PIECES = 56

/**
 * A one-shot confetti burst with a Confirm haptic, for a moment the user earned (a goal
 * reached, a new badge). Place it last in a screen-filling Box; it draws over the screen
 * and never takes a touch. When [play] turns true it calls [onFinished] (where the caller
 * clears [play]) and bursts once. With animations off only the haptic lands: the screen's
 * own "reached" state is the static badge. Claim the moment first with
 * `UserPreferencesRepository.celebrateOnce`, so it fires once per key, ever.
 */
@Composable
fun Celebration(play: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val animate = rememberAnimationsEnabled()
    val finished by rememberUpdatedState(onFinished)
    val time = remember { Animatable(0f) }
    // The request is consumed as the burst starts, so a rotation mid-burst can't replay it
    // (haptic included): the burst itself lives only in this composition.
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        playing = true
        finished()
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (animate) {
            time.snapTo(0f)
            // A linear clock, not a spring: the pieces' own physics shape the motion.
            time.animateTo(1f, tween(BURST_MILLIS, easing = LinearEasing))
        }
        playing = false
    }
    if (!playing || !animate) return

    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.tertiaryContainer)
    val shapes = confettiShapes()
    val pieces = remember { List(PIECES) { Piece.random(Random) } }
    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        // Fade over the last third so pieces leave rather than vanish.
        val alpha = ((1f - t) * 3f).coerceIn(0f, 1f)
        val origin = Offset(size.width / 2f, size.height * 0.3f)
        val unit = size.width
        pieces.forEach { p ->
            val secs = t * BURST_MILLIS / 1000f
            val x = origin.x + cos(p.angle) * p.speed * unit * secs
            val y = origin.y + sin(p.angle) * p.speed * unit * secs + 0.5f * GRAVITY * unit * secs * secs
            val side = p.size.dp.toPx()
            translate(x - side / 2f, y - side / 2f) {
                rotate(p.spin * t, pivot = Offset(side / 2f, side / 2f)) {
                    scale(side, side, pivot = Offset.Zero) {
                        drawPath(shapes[p.shape % shapes.size], colors[p.color % colors.size].copy(alpha = alpha))
                    }
                }
            }
        }
    }
}

/** Fractions of the screen width per second², so a tablet's burst looks like a phone's. */
private const val GRAVITY = 1.6f

/** Expressive cookies and sparkles, each a path in the unit square (toPath remembers its own). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun confettiShapes(): List<Path> =
    listOf(MaterialShapes.Cookie4Sided, MaterialShapes.Sunny, MaterialShapes.SoftBurst, MaterialShapes.Pill).map { it.toPath() }

private class Piece(
    /** Radians; up and outward (screen y grows down). */
    val angle: Float,
    /** Screen widths per second at launch. */
    val speed: Float,
    val size: Float,
    /** Degrees turned over the whole burst. */
    val spin: Float,
    val color: Int,
    val shape: Int,
) {
    companion object {
        fun random(r: Random) = Piece(
            angle = Math.toRadians(r.nextDouble(-165.0, -15.0)).toFloat(),
            speed = r.nextFloat() * 0.7f + 0.4f,
            size = r.nextFloat() * 6f + 7f,
            spin = (r.nextFloat() - 0.5f) * 1_080f,
            color = r.nextInt(5),
            shape = r.nextInt(4),
        )
    }
}

/** Celebration keys: one per moment, so each fires once on this device. */
object CelebrationKeys {
    /** By uid, not row id: a restore can give another goal the same id. */
    fun goalReached(goalUid: String) = "goal_reached:$goalUid"
    fun achievement(key: String, tier: Int) = "achievement:$key:$tier"
    fun budgetMonth(month: java.time.YearMonth, currency: String) = "budget_month:$month:$currency"

    /** A close weekly guess; [weekStart] is the week's first day ("2026-11-09"). */
    fun weeklyGuess(weekStart: java.time.LocalDate) = "guess:$weekStart"

    /**
     * Claimed with the first visit's tiers. When it's among the fresh claims, those tiers
     * are history (an install or upgrade), recorded without confetti.
     */
    const val ACHIEVEMENTS_SEEDED = "achievements_seeded"

    /** Whether the [fresh] claims from an Achievements visit deserve confetti. */
    fun celebratesAchievements(fresh: Set<String>): Boolean = ACHIEVEMENTS_SEEDED !in fresh && fresh.isNotEmpty()
}
