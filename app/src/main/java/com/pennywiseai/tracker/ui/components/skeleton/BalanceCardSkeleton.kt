package com.pennywiseai.tracker.ui.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.shimmer
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun BalanceCardSkeleton(
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh

    PennyWiseCardV2(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large balance number placeholder
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                    .background(placeholderColor)
                    .shimmer()
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Small change indicator pill
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.full))
                    .background(placeholderColor)
                    .shimmer()
            )
        }
    }
}
