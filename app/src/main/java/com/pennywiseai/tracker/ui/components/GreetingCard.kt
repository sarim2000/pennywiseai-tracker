package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun GreetingCard(
    modifier: Modifier = Modifier,
    userName: String = "User",
    profileImageUri: String? = null,
    profileBackgroundColor: Int = 0,
    onAvatarClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    profiles: List<ProfileEntity> = emptyList(),
    selectedProfileId: Long? = null,
    onProfileSelected: (Long?) -> Unit = {},
    isProEntitled: Boolean = false,
    onUpgradeClick: () -> Unit = {}
) {
    val today = LocalDate.now()
    val subtitle = remember(today) {
        val now = today
        val lastDay = now.withDayOfMonth(now.lengthOfMonth())
        val daysLeft = ChronoUnit.DAYS.between(now, lastDay)
        val rawMonth = now.month.name.lowercase()
        val monthName = if (rawMonth.isEmpty()) rawMonth else rawMonth.substring(0, 1).uppercase() + rawMonth.substring(1)

        when {
            daysLeft == 0L -> "Last day of $monthName"
            daysLeft <= 7 -> "$daysLeft days left in $monthName"
            else -> {
                val hour = LocalTime.now().hour
                when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    in 17..21 -> "Good evening"
                    else -> "Good night"
                }
            }
        }
    }

    val avatarBackground = if (profileBackgroundColor != 0) {
        Color(profileBackgroundColor)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Avatar wrapped in a "membership ring" — the only Pro signal,
        // no pill, no label. Solid gold = Pro member; dashed gold = free user
        // (reads as "not yet filled / tap to unlock"). For free users tapping
        // opens the upgrade sheet; Pro members tap through to settings.
        val ringColor = yellow_dark
        Box(
            modifier = Modifier
                .size(52.dp)
                // Clip to a circle before clickable so the tap ripple stays
                // within the ring instead of rendering as a square.
                .clip(CircleShape)
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val dash = if (!isProEntitled) {
                        PathEffect.dashPathEffect(floatArrayOf(stroke * 2.5f, stroke * 2f), 0f)
                    } else null
                    drawCircle(
                        color = ringColor,
                        radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke, pathEffect = dash)
                    )
                }
                .clickable(onClick = if (isProEntitled) onAvatarClick else onUpgradeClick),
            contentAlignment = Alignment.Center
        ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarBackground),
            contentAlignment = Alignment.Center
        ) {
            val avatarResId = profileImageUri?.let { AvatarHelper.resolveAvatarDrawable(it) }
            if (avatarResId != null) {
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = userName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (profileImageUri != null) {
                AsyncImage(
                    model = profileImageUri,
                    contentDescription = userName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = remember(userName) {
                    val parts = userName.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        "${parts.first().first()}${parts.last().first()}".uppercase()
                    } else {
                        userName.trim().take(2).uppercase()
                    }
                }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        // Text Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = userName.ifBlank { "User" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Profile filter button
        if (profiles.isNotEmpty()) {
            var showProfileMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showProfileMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = profileFilterIcon(profiles, selectedProfileId),
                        contentDescription = "Profile filter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProfileFilterDropdown(
                    expanded = showProfileMenu,
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = onProfileSelected,
                    onDismiss = { showProfileMenu = false }
                )
            }
        }

        // Menu button — plain IconButton, no circular background
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

