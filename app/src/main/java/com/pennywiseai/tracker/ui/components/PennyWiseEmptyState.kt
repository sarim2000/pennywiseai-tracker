package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The app's empty state: tonal icon circle, headline, one line of explanation,
 * and an optional primary action.
 *
 * Spacing here is deliberately uneven rather than a single uniform gap — the
 * headline belongs to the icon above it, and the description belongs to the
 * headline, so the icon→headline gap is larger than the headline→description
 * gap. A uniform `spacedBy` made all three read as three unrelated items.
 *
 * The description is capped at ~40 characters per line ([DESCRIPTION_MAX_WIDTH])
 * because centred text set to the full width of a phone wraps into a shape
 * that's hard to read; a measure closer to a paragraph reads better.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PennyWiseEmptyState(
    icon: ImageVector,
    headline: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    ghostContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.empty),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // One-shot entrance: the badge fades in while its circle settles into the
        // cookie, once per placement, never looping. With animations off the
        // system's zero duration scale lands it on the first frame.
        val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
        var entered by rememberSaveable { mutableStateOf(false) }
        val settle = remember { Animatable(if (entered) 1f else 0f) }
        val fade = remember { Animatable(if (entered) 1f else 0f) }
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        LaunchedEffect(Unit) {
            if (entered) return@LaunchedEffect
            entered = true
            launch { fade.animateTo(1f, effects) }
            settle.animateTo(1f, spatial)
        }
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.emptyStateContainer)
                .graphicsLayer {
                    // No scale-in (design.md motion rule 4): it fades while the shape
                    // settles, clamped so the spring's overshoot stays between the shapes.
                    alpha = fade.value
                    shape = MorphShape(morph, settle.value.coerceIn(0f, 1f))
                    clip = true
                }
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.emptyStateGlyph),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md)
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            // No extra alpha: onSurfaceVariant is already the muted role, and
            // dimming it further pushed this below the AA contrast floor.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Spacing.xs)
                .widthIn(max = DESCRIPTION_MAX_WIDTH)
        )

        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.padding(top = Spacing.md),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(actionLabel)
            }
        }

        if (ghostContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .alpha(Dimensions.Alpha.ghost)
            ) {
                ghostContent()
            }
        }
    }
}

private val DESCRIPTION_MAX_WIDTH = 280.dp
