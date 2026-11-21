package com.pennywiseai.tracker.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexAxes
import com.pennywiseai.tracker.ui.theme.LocalGoogleSansFlexActive
import com.pennywiseai.tracker.ui.theme.withFlexAxes

/**
 * Grade added while a label is pressed but not selected: a quarter of the
 * selected emphasis, enough to answer the finger without reading as selection.
 */
private const val PRESS_GRADE = 20f

/**
 * [style] with a selected (or pressed) label drawn heavier through Google Sans
 * Flex's `GRAD` axis, animated with the motion scheme's fast effects spec.
 *
 * Grade thickens strokes without changing any advance width, so the label and
 * its neighbours never shift while it animates — unlike a `fontWeight` swap.
 * Pass the theme's style (e.g. `LocalTextStyle.current` inside a chip), not
 * one already passed through `withFlexAxes`.
 *
 * Returns [style] unchanged when the typography isn't Google Sans Flex (system
 * font, SN Pro, a non-Latin locale); those keep whatever selection cue the
 * component already has. Under reduced motion the grade jumps to its end value.
 */
@Composable
fun emphasisStyle(style: TextStyle, selected: Boolean, pressed: Boolean = false): TextStyle {
    if (!LocalGoogleSansFlexActive.current) return style
    val target = when {
        selected -> GoogleSansFlexAxes.EMPHASIS_GRADE
        pressed -> PRESS_GRADE
        else -> 0f
    }
    val grade = if (rememberAnimationsEnabled()) {
        animateFloatAsState(
            targetValue = target,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "emphasisGrade"
        ).value
    } else {
        target
    }
    return style.withFlexAxes(gradeBoost = grade)
}
