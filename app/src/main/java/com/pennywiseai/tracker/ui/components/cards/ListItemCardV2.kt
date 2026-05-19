package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.ui.theme.Spacing

enum class ListItemPosition {
    Top,
    Middle,
    Bottom,
    Single;

    companion object {
        fun from(index: Int, size: Int): ListItemPosition {
            return when {
                size <= 1 -> Single
                index == 0 -> Top
                index == size - 1 -> Bottom
                else -> Middle
            }
        }
    }
}

@Composable
fun ListItemPosition.toShape(): CornerBasedShape {
    val large = MaterialTheme.shapes.large
    val small = MaterialTheme.shapes.extraSmall

    return when (this) {
        ListItemPosition.Top -> large.copy(
            bottomStart = small.bottomStart,
            bottomEnd = small.bottomEnd
        )
        ListItemPosition.Middle -> small
        ListItemPosition.Bottom -> large.copy(
            topStart = small.topStart,
            topEnd = small.topEnd
        )
        ListItemPosition.Single -> large
    }
}

@Composable
fun ListItemCardV2(
    title: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: Dp = Spacing.md,
    onClick: (() -> Unit)? = null
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        contentPadding = contentPadding,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(Spacing.md))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
