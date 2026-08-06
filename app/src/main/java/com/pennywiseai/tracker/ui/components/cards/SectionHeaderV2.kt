package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The heading above a section of cards or rows.
 *
 * Two changes from the earlier version, both about hierarchy:
 *
 * - **The title is `onSurface`, not `primary`.** When every section heading is
 *   accent-coloured, none of them stands out and the accent stops meaning
 *   "this is actionable". The accent now belongs to the [action] on the right,
 *   which *is* a control. The heading earns its prominence from weight
 *   (SemiBold) instead.
 * - **A fixed minimum height.** Headers with an action were taller than headers
 *   without one, because a `TextButton` is 40dp tall and bare text is 20dp.
 *   That made the gap above each section vary by whether it happened to have a
 *   "View All" link.
 *
 * The leading slot inherits `onSurfaceVariant`, so an icon passed without an
 * explicit tint reads as supporting rather than competing with the title.
 *
 * The header also carries its own top inset. Callers lay sections out in a
 * `Column` with one uniform `spacedBy`, which gave "gap above the header"
 * and "gap between header and its content" the same value — so a heading
 * floated midway between the section it labels and the one above it. The inset
 * makes the outer gap larger than the inner one, which is what makes a heading
 * read as belonging to what follows it.
 */
@Composable
fun SectionHeaderV2(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    /** Set to `Spacing.none` for the first header on a screen, where the
     *  scaffold's content padding already provides the gap. */
    topSpacing: Dp = Spacing.sm
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topSpacing)
            .defaultMinSize(minHeight = Dimensions.Component.iconButton)
            .semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) { leading() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = title,
                    style = PennyWiseText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (action != null) {
            Row(
                modifier = Modifier.padding(start = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) { action() }
        }
    }
}
