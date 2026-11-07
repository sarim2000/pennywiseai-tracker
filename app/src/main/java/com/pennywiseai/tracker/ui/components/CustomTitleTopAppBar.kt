package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CustomTitleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    isHomeScreen: Boolean = false,
    hasBackButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    userName: String = "",
    profileImageUri: String? = null,
    profileBackgroundColor: Int = 0,
    /**
     * Drives the small bar directly and drops the large one. Home keeps its greeting
     * in the list, so the small bar follows the list's scroll instead.
     */
    collapsedFractionOverride: Float? = null,
) {
    // One compact bar everywhere. A large title pushed the money down a third of the screen
    // and jumped from left to centre on scroll. Callers still attach the large behaviour's
    // nested-scroll connection: its limit starts at -Float.MAX_VALUE and only a large bar
    // would lower it, so left alone it swallows every upward scroll.
    SideEffect { scrollBehaviorLarge.state.heightOffsetLimit = 0f }
    val collapsedFraction = collapsedFractionOverride ?: 1f

    // Regular TopAppBar — fades in as LargeTopAppBar collapses
    RegularTopAppBar(
        scrollBehaviorSmall = scrollBehaviorSmall,
        title = title,
        isHomeScreen = isHomeScreen,
        hasBackButton = hasBackButton,
        actionContent = actionContent,
        navigationContent = navigationContent,
        userName = userName,
        profileImageUri = profileImageUri,
        profileBackgroundColor = profileBackgroundColor,
        collapsedFraction = collapsedFraction,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegularTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    title: String,
    isHomeScreen: Boolean,
    hasBackButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    userName: String = "",
    profileImageUri: String? = null,
    profileBackgroundColor: Int = 0,
    collapsedFraction: Float,
) {
    BlurredAnimatedVisibility(
        visible = collapsedFraction > 0.01f,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val barColors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
        val navigationIcon: @Composable () -> Unit = {
            BlurredAnimatedVisibility(
                visible = hasBackButton,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                navigationContent()
            }
        }
        // Opaque, always: a translucent bar put scrolling text behind the title, and
        // with money on screen that reads as a wrong number. The hairline only shows
        // once content is actually underneath.
        val hairline = MaterialTheme.colorScheme.outlineVariant
        val barModifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = collapsedFraction))
            .drawBehind {
                drawRect(
                    color = hairline.copy(alpha = collapsedFraction),
                    topLeft = Offset(0f, size.height - 1f),
                    size = Size(size.width, 1f)
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .alpha(collapsedFraction)

        if (isHomeScreen) {
            // Home's "title" is an avatar next to a name — it belongs at the
            // start, so this one stays a plain (start-aligned) app bar.
            TopAppBar(
                title = {
                    HomeBarTitle(
                        userName = userName,
                        profileImageUri = profileImageUri,
                        profileBackgroundColor = profileBackgroundColor,
                    )
                },
                colors = barColors,
                navigationIcon = navigationIcon,
                actions = { actionContent() },
                scrollBehavior = scrollBehaviorSmall,
                windowInsets = WindowInsets(0.dp),
                modifier = barModifier
            )
        } else {
            // Start-aligned, in the serif: reads as the heading of a page, and a long
            // title doesn't have to dodge an action on each side.
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = PennyWiseText.screenTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = barColors,
                navigationIcon = navigationIcon,
                actions = { actionContent() },
                scrollBehavior = scrollBehaviorSmall,
                windowInsets = WindowInsets(0.dp),
                modifier = barModifier
            )
        }
    }
}

/** Home's start-aligned title: avatar plus the user's name. */
@Composable
private fun HomeBarTitle(
    userName: String,
    profileImageUri: String?,
    profileBackgroundColor: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.large)
                .clip(CircleShape)
                .background(
                    if (profileBackgroundColor != 0) Color(profileBackgroundColor)
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            val avatarResId = profileImageUri?.let { AvatarHelper.resolveAvatarDrawable(it) }
            if (avatarResId != null) {
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (profileImageUri != null) {
                AsyncImage(
                    model = profileImageUri,
                    contentDescription = null,
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.smd))
        Text(
            text = userName.ifBlank { "PennyWise" },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start,
        )
    }
}
