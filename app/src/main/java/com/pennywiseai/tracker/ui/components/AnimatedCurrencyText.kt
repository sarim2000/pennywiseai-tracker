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

@Composable
fun AnimatedCurrencyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
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
