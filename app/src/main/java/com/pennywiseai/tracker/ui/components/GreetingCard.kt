package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.utils.AppDates
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
    // Shows an Add button when set (the floating nav bar docks Sync instead
    // of Home keeping an Add FAB).
    onAddClick: (() -> Unit)? = null,
    // The voice mic, where the phone can listen: a cash spend said, or a question.
    onVoiceClick: (() -> Unit)? = null,
    profiles: List<ProfileEntity> = emptyList(),
    selectedProfileId: Long? = null,
    onProfileSelected: (Long?) -> Unit = {},
    isProEntitled: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    // The last day of the user's current budget cycle. When null, the
    // subtitle falls back to the calendar month's end. The "X days left in
    // <month>" line then tracks the cycle (e.g. "4 days left in October" on
    // Oct 20 with startDay=25, where the cycle ends Oct 24).
    cycleEnd: LocalDate? = null,
    /** A line under the name, e.g. when SMS were last read. */
    status: (@Composable () -> Unit)? = null,
) {
    val today = LocalDate.now()
    val (daysLeft, hour) = remember(today, cycleEnd) {
        // Prefer the cycle's end over the calendar month's end so the
        // "X days left" hint lines up with the budget / spending windows.
        val lastDay = cycleEnd ?: today.withDayOfMonth(today.lengthOfMonth())
        ChronoUnit.DAYS.between(today, lastDay).toInt() to LocalTime.now().hour
    }
    val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, AppDates.locale())
    val subtitle = when {
        daysLeft == 0 -> stringResource(R.string.greeting_last_day_of_month, monthName)
        daysLeft <= 7 -> pluralStringResource(R.plurals.greeting_days_left_in_month, daysLeft, daysLeft, monthName)
        else -> stringResource(
            when (hour) {
                in 5..11 -> R.string.greeting_good_morning
                in 12..16 -> R.string.greeting_good_afternoon
                in 17..21 -> R.string.greeting_good_evening
                else -> R.string.greeting_good_night
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Greeting over a big name: the Expressive header every tab shares.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = userName.ifBlank { stringResource(R.string.greeting_default_user_name) },
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (profiles.isNotEmpty()) {
                var showProfileMenu by remember { mutableStateOf(false) }
                Box {
                    HeaderIcon(
                        icon = profileFilterIcon(profiles, selectedProfileId),
                        label = stringResource(R.string.greeting_profile_filter),
                        onClick = { showProfileMenu = true }
                    )
                    ProfileFilterDropdown(
                        expanded = showProfileMenu,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelected = onProfileSelected,
                        onDismiss = { showProfileMenu = false }
                    )
                }
            }
            if (onVoiceClick != null) {
                HeaderIcon(Icons.Default.Mic, stringResource(R.string.quick_add_voice_title), onVoiceClick)
            }
            if (onAddClick != null) {
                HeaderIcon(Icons.Default.Add, stringResource(R.string.home_fab_add), onAddClick)
            }
            HeaderIcon(Icons.Default.MoreHoriz, stringResource(R.string.greeting_more_options), onMenuClick)

            Spacer(modifier = Modifier.width(Spacing.xs))

            // The avatar in a cookie shape, edged by a "membership ring" — the only Pro
            // signal, no pill, no label. Solid gold = Pro member; dashed gold = free user
            // (reads as "not yet filled / tap to unlock"). For free users tapping
            // opens the upgrade sheet; Pro members tap through to settings.
            val ringColor = yellow_dark
            val cookie = MaterialShapes.Cookie9Sided.toShape()
            ProfileAvatar(
                name = userName,
                imageUri = profileImageUri,
                backgroundColor = profileBackgroundColor,
                size = Dimensions.Icon.avatarLarge,
                shape = cookie,
                contentDescription = userName,
                modifier = Modifier
                    .drawWithContent {
                        drawContent()
                        // Clipped to the cookie, so half the stroke shows: draw it double.
                        val stroke = Dimensions.Component.avatarRingWidth.toPx() * 2
                        val dash = if (!isProEntitled) {
                            PathEffect.dashPathEffect(floatArrayOf(stroke * 1.25f, stroke), 0f)
                        } else null
                        drawOutline(
                            outline = cookie.createOutline(size, layoutDirection, this),
                            color = ringColor,
                            style = Stroke(width = stroke, pathEffect = dash)
                        )
                    }
                    .clickable(onClick = if (isProEntitled) onAvatarClick else onUpgradeClick)
            )
        }
        status?.invoke()
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Dimensions.Component.minTouchTarget),
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
