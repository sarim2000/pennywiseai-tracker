package com.pennywiseai.tracker.ui.components

import android.os.Build
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.animations.pwFadeIn
import com.pennywiseai.tracker.ui.animations.pwFadeOut
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.LocalBlurEffects
import com.pennywiseai.tracker.ui.effects.frostedBlur
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CustomTitleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    subtitle: String? = null,
    /** Small line above the expanded title ("November" over "Activity"). */
    overline: String? = null,
    isHomeScreen: Boolean = false,
    hasBackButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    extraInfoCard: @Composable () -> Unit = {},
    userName: String = "",
    profileImageUri: String? = null,
    profileBackgroundColor: Int = 0,
    hazeState: HazeState = HazeState(),
    blurEffects: Boolean = LocalBlurEffects.current
) {
    val collapsedFraction = scrollBehaviorLarge.state.collapsedFraction

    // LargeTopAppBar — only when we have a separate large behavior
    if (scrollBehaviorLarge != scrollBehaviorSmall) {
        LargerTopAppBar(
            scrollBehaviorLarge = scrollBehaviorLarge,
            title = title,
            subtitle = subtitle,
            overline = overline,
            isHomeScreen = isHomeScreen,
            hasBackButton = hasBackButton,
            collapsedFraction = collapsedFraction,
            actionContent = actionContent,
            navigationContent = navigationContent,
            extraInfoCard = extraInfoCard,
            hazeState = hazeState,
            blurEffects = blurEffects,
            themeColors = MaterialTheme.colorScheme
        )
    }

    // Regular TopAppBar — fades in as LargeTopAppBar collapses
    RegularTopAppBar(
        scrollBehaviorSmall = scrollBehaviorSmall,
        title = title,
        subtitle = subtitle,
        isHomeScreen = isHomeScreen,
        hasBackButton = hasBackButton,
        actionContent = actionContent,
        navigationContent = navigationContent,
        userName = userName,
        profileImageUri = profileImageUri,
        profileBackgroundColor = profileBackgroundColor,
        collapsedFraction = if (scrollBehaviorLarge != scrollBehaviorSmall) collapsedFraction else 1f,
        modifier = modifier,
        hazeState = hazeState,
        blurEffects = blurEffects
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargerTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    subtitle: String?,
    overline: String?,
    isHomeScreen: Boolean,
    hasBackButton: Boolean = false,
    collapsedFraction: Float,
    extraInfoCard: @Composable () -> Unit = {},
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    hazeState: HazeState,
    blurEffects: Boolean = true,
    themeColors: ColorScheme,
) {
    // The M3 Expressive flexible bar. Without a subtitle it lays out exactly
    // like the LargeTopAppBar it replaced (same internals and title padding);
    // our title sets its own emphasized style, so the bar's default type
    // doesn't apply.
    val barModifier = Modifier
        .fillMaxWidth()
        .then(
            if (blurEffects) Modifier.frostedBlur(
                hazeState,
                blurRadius = 10.dp,
                edgeTreatment = BlurredEdgeTreatment.Rectangle,
                progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f),
            ) else Modifier
        )
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    themeColors.background,
                    Color.Transparent
                )
            )
        )
        .windowInsetsPadding(WindowInsets.statusBars)
        .alpha(1f - collapsedFraction)

    if (!hasBackButton) {
        // A tab root has nothing for the M3 bar's fixed 64dp top row (no back button), so it
        // gets its own header: title row right under the status bar, actions and avatar on
        // that row, as in the redesign mock.
        TabRootHeader(scrollBehavior = scrollBehaviorLarge, modifier = barModifier) {
            TitleForLargeTopAppBar(
                title = title,
                overline = overline,
                trailing = actionContent,
                isHomeScreen = isHomeScreen,
                // The bar's 4dp inset, so the title and the avatar both sit on the 16dp content
                // margin. Home's greeting (extraInfoCard) brings its own content padding.
                modifier = modifier.padding(horizontal = Spacing.xs),
                extraInfoCard = extraInfoCard,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
            }
        }
        return
    }

    LargeFlexibleTopAppBar(
        title = {
            TitleForLargeTopAppBar(
                title = title,
                overline = overline,
                isHomeScreen = isHomeScreen,
                modifier = modifier,
                extraInfoCard = extraInfoCard,
            )
        },
        subtitle = subtitle?.let {
            {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Lines up with the title's start inset.
                    modifier = Modifier.padding(start = Spacing.smd)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        navigationIcon = {
            BlurredAnimatedVisibility(
                visible = hasBackButton && !isHomeScreen,
                enter = pwFadeIn(),
                exit = pwFadeOut()
            ) {
                navigationContent()
            }
        },
        actions = {
            BlurredAnimatedVisibility(
                visible = !isHomeScreen,
                enter = pwFadeIn(),
                exit = pwFadeOut()
            ) {
                // Lay multiple actions (e.g. Edit + overflow) out horizontally. The
                // visibility wrapper renders into a Box, not a RowScope, so without
                // this Row the icons stack on top of each other in the expanded bar.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    actionContent()
                }
            }
        },
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = when {
            isHomeScreen -> Dimensions.Component.homeAppBarExpandedHeight
            subtitle != null || overline != null -> Dimensions.Component.appBarExpandedHeightWithSubtitle
            else -> Dimensions.Component.appBarExpandedHeight
        },
        windowInsets = WindowInsets(0.dp),
        scrollBehavior = scrollBehaviorLarge,
        modifier = barModifier,
    )
}

/**
 * A tab root's expanded header. It collapses with [scrollBehavior] like the M3 bar does
 * (height shrinks by the scrolled offset, content slides up under the clip), so the regular
 * bar still fades in on top once it's gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabRootHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = scrollBehavior.state
    Column(
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val limit = -placeable.height.toFloat()
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
                val height = (placeable.height + state.heightOffset).roundToInt().coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
            }
            .padding(top = Dimensions.Component.appBarTabTopRow, bottom = Spacing.md),
    ) {
        // The M3 bar provides this for its slots; without it titles and action icons
        // fall back to black, unreadable in dark theme.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
private fun TitleForLargeTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    overline: String? = null,
    trailing: @Composable () -> Unit = {},
    isHomeScreen: Boolean,
    extraInfoCard: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        BlurredAnimatedVisibility(
            visible = !isHomeScreen,
            enter = pwFadeIn(),
            exit = pwFadeOut()
        ) {
            // Overline + big bold title, with a tab's avatar (Settings) on the same row.
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.smd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (overline != null) {
                        Text(
                            text = overline,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Action icons take the M3 bar's onSurfaceVariant, as Home's header icons do.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { trailing() }
                TabProfileButton()
            }
        }
        extraInfoCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegularTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    title: String,
    subtitle: String?,
    isHomeScreen: Boolean,
    hasBackButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    userName: String = "",
    profileImageUri: String? = null,
    profileBackgroundColor: Int = 0,
    collapsedFraction: Float,
    hazeState: HazeState,
    blurEffects: Boolean = true
) {
    BlurredAnimatedVisibility(
        visible = collapsedFraction > 0.01f,
        enter = pwFadeIn(),
        exit = pwFadeOut()
    ) {
        val barColors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
        val navigationIcon: @Composable () -> Unit = {
            BlurredAnimatedVisibility(
                visible = hasBackButton,
                enter = pwFadeIn(),
                exit = pwFadeOut()
            ) {
                navigationContent()
            }
        }
        val barModifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEffects) Modifier.frostedBlur(
                    hazeState,
                    blurRadius = 10.dp,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f),
                ) else Modifier
            )
            .background(
                // The fade relies on the blur to hide what scrolls under it. Without
                // blur (Android 11 and older, or turned off) content showed sharp
                // behind the back button and title, so the bar is opaque there.
                if (blurEffects && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            Color.Transparent
                        )
                    )
                } else {
                    SolidColor(MaterialTheme.colorScheme.background)
                }
            )
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
                actions = {
                    actionContent()
                    TabProfileButton()
                },
                scrollBehavior = scrollBehaviorSmall,
                windowInsets = WindowInsets(0.dp),
                modifier = barModifier
            )
        } else {
            // CenterAlignedTopAppBar centres against the *bar*, not against
            // whatever is left over beside the navigation icon. This used to
            // be a hand-rolled -26dp nudge applied only when the caller
            // claimed to have no action button — so the nine screens that
            // claimed an action button while rendering none ended up with the
            // title visibly off to the right.
            val barTitle: @Composable () -> Unit = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            if (subtitle == null) {
                CenterAlignedTopAppBar(
                    title = barTitle,
                    colors = barColors,
                    navigationIcon = navigationIcon,
                    actions = {
                        actionContent()
                        TabProfileButton()
                    },
                    scrollBehavior = scrollBehaviorSmall,
                    windowInsets = WindowInsets(0.dp),
                    modifier = barModifier
                )
            } else {
                // Same centred layout (both share M3's single-row bar), plus
                // the subtitle line under the title.
                TopAppBar(
                    title = barTitle,
                    subtitle = {
                        Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    titleHorizontalAlignment = Alignment.CenterHorizontally,
                    colors = barColors,
                    navigationIcon = navigationIcon,
                    actions = {
                        actionContent()
                        TabProfileButton()
                    },
                    scrollBehavior = scrollBehaviorSmall,
                    windowInsets = WindowInsets(0.dp),
                    modifier = barModifier
                )
            }
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
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Start,
        )
    }
}
