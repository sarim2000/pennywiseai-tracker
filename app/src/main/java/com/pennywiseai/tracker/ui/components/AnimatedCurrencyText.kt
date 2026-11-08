package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.utils.AmountPrivacy

@Composable
fun AnimatedCurrencyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    // Hiding/showing amounts is a privacy toggle, not a value change: the mask is
    // drawn outside AnimatedContent so neither side lingers for an exit frame.
    if (AmountPrivacy.MASK in text) {
        Text(text = text, style = style, color = color, fontWeight = fontWeight, modifier = modifier)
        return
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            slideInVertically(
                animationSpec = motion.defaultSpatialSpec(),
                initialOffsetY = { it }
            ) togetherWith slideOutVertically(
                animationSpec = motion.defaultSpatialSpec(),
                targetOffsetY = { -it }
            )
        },
        label = "currencyAnimation",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            fontWeight = fontWeight,
        )
    }
}
