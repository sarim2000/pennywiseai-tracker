package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy

@Composable
fun AnimatedCurrencyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    autoSize: TextAutoSize? = null,
) {
    // Hiding/showing amounts is a privacy toggle, not a value change: the mask is
    // drawn outside AnimatedContent so neither side lingers for an exit frame.
    if (AmountPrivacy.MASK in text) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            autoSize = autoSize,
            modifier = modifier,
        )
        return
    }
    // Money settles: a quick effects cross-fade with a few dp of drift, no overshoot.
    val motion = MaterialTheme.motionScheme
    val travelPx = with(LocalDensity.current) { Spacing.xs.roundToPx() }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(motion.fastEffectsSpec()) +
                slideInVertically(motion.fastEffectsSpec()) { travelPx }) togetherWith
                (fadeOut(motion.fastEffectsSpec()) +
                    slideOutVertically(motion.fastEffectsSpec()) { -travelPx }) using
                SizeTransform(clip = false) { _, _ -> motion.fastEffectsSpec() }
        },
        label = "currencyAnimation",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            autoSize = autoSize,
        )
    }
}
