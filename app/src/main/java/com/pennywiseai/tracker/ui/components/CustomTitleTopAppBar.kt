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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.LocalBlurEffects
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeDefaults.tint
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CustomTitleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    isHomeScreen: Boolean = false,
    hasBackButton: Boolean = false,
    hasActionButton: Boolean = false,
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
        isHomeScreen = isHomeScreen,
        hasBackButton = hasBackButton,
        hasActionButton = hasActionButton,
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

@Composable
private fun Modifier.animatedOffsetModifier(
    hasBackButton: Boolean,
    hasActionButton: Boolean = false,
    isHomeScreen: Boolean = false,
): Modifier {
    val targetOffsetX = when {
        hasBackButton && hasActionButton -> 0.dp
        isHomeScreen -> 0.dp
        hasBackButton -> (-26).dp
        else -> 0.dp
    }

    val density = LocalDensity.current
    val targetOffsetXPx = with(density) { targetOffsetX.toPx() }

    val transition = updateTransition(
        targetState = Triple(hasBackButton, false, targetOffsetXPx),
        label = "offsetTransition"
    )

    val animatedOffsetX by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "offsetX"
    ) { (_, _, offset) -> offset }

    return this
        .fillMaxWidth()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(x = animatedOffsetX.toInt(), y = 0)
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
private fun LargerTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
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
    LargeTopAppBar(
        title = {
            TitleForLargeTopAppBar(
                title = title,
                isHomeScreen = isHomeScreen,
                modifier = modifier,
                extraInfoCard = extraInfoCard,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        navigationIcon = {
            BlurredAnimatedVisibility(
                visible = hasBackButton && !isHomeScreen,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                navigationContent()
            }
        },
        actions = {
            BlurredAnimatedVisibility(
                visible = !isHomeScreen,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                actionContent()
            }
        },
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = if (isHomeScreen) 150.dp else 110.dp,
        windowInsets = WindowInsets(0.dp),
        scrollBehavior = scrollBehaviorLarge,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = tint(backgroundColor),
                            blurRadius = 10.dp,
                            noiseFactor = -1f,
                        )
                        progressive =
                            HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    }
                ) else Modifier
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .alpha(1f - collapsedFraction)
    )
}

@Composable
private fun TitleForLargeTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    isHomeScreen: Boolean,
    extraInfoCard: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlurredAnimatedVisibility(
            visible = !isHomeScreen,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp)
            )
        }
        extraInfoCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
private fun RegularTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    title: String,
    isHomeScreen: Boolean,
    hasBackButton: Boolean = false,
    hasActionButton: Boolean = false,
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
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        TopAppBar(
            title = {
                if (isHomeScreen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = userName.ifBlank { "PennyWise" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                        )
                    }
                } else {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.animatedOffsetModifier(
                            hasBackButton = hasBackButton,
                            hasActionButton = hasActionButton,
                            isHomeScreen = isHomeScreen,
                        )
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            navigationIcon = {
                BlurredAnimatedVisibility(
                    visible = hasBackButton,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    navigationContent()
                }
            },
            actions = {
                actionContent()
            },
            scrollBehavior = scrollBehaviorSmall,
            windowInsets = WindowInsets(0.dp),
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (blurEffects) Modifier.hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                blurRadius = 10.dp,
                                noiseFactor = -1f,
                            )
                            progressive =
                                HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                    ) else Modifier
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .alpha(collapsedFraction)
        )
    }
}
