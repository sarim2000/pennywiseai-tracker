package com.pennywiseai.tracker.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The app's frosted-glass blur over the [state]'s sources: no noise, transparent background, and
 * [tint] at 70% of its alpha (Haze 1.x `HazeDefaults.tint`). [Color.Unspecified] means no tint.
 */
@Composable
fun Modifier.frostedBlur(
    state: HazeState,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 20.dp,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
    progressive: HazeProgressive? = null,
): Modifier {
    // Haze 2 styles compare by identity, so remember it or every recomposition re-resolves it.
    val style = remember(tint, blurRadius, edgeTreatment, progressive) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            noiseFactor(0f)
            if (tint.isSpecified) {
                colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = tint.alpha * 0.7f))))
            }
            blurredEdgeTreatment(edgeTreatment)
            progressive(progressive)
        }
    }
    val input = remember(state) { HazeInput.Sources(state) }
    return hazeBlur(input = input, style = style)
}
