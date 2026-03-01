package com.pennywiseai.tracker.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.getCoverGradientColors
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Dawn_Foam
import com.pennywiseai.tracker.ui.theme.Dawn_Foam_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Foam_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Gold
import com.pennywiseai.tracker.ui.theme.Dawn_Gold_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Gold_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Highlight
import com.pennywiseai.tracker.ui.theme.Dawn_Highlight_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Highlight_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Iris
import com.pennywiseai.tracker.ui.theme.Dawn_Iris_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Iris_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Love
import com.pennywiseai.tracker.ui.theme.Dawn_Love_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Love_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Muted
import com.pennywiseai.tracker.ui.theme.Dawn_Muted_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Muted_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Overlay
import com.pennywiseai.tracker.ui.theme.Dawn_Overlay_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Overlay_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Pine
import com.pennywiseai.tracker.ui.theme.Dawn_Pine_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Pine_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Rose
import com.pennywiseai.tracker.ui.theme.Dawn_Rose_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Rose_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Subtle
import com.pennywiseai.tracker.ui.theme.Dawn_Subtle_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Subtle_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Surface
import com.pennywiseai.tracker.ui.theme.Dawn_Surface_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Surface_tertiary
import com.pennywiseai.tracker.ui.theme.Dawn_Text
import com.pennywiseai.tracker.ui.theme.Dawn_Text_secondary
import com.pennywiseai.tracker.ui.theme.Dawn_Text_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Foam
import com.pennywiseai.tracker.ui.theme.RosePine_Foam_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Foam_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Gold
import com.pennywiseai.tracker.ui.theme.RosePine_Gold_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Gold_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Highlight
import com.pennywiseai.tracker.ui.theme.RosePine_Highlight_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Highlight_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Iris
import com.pennywiseai.tracker.ui.theme.RosePine_Iris_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Iris_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Love
import com.pennywiseai.tracker.ui.theme.RosePine_Love_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Love_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Muted
import com.pennywiseai.tracker.ui.theme.RosePine_Muted_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Muted_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Overlay
import com.pennywiseai.tracker.ui.theme.RosePine_Overlay_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Overlay_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Pine
import com.pennywiseai.tracker.ui.theme.RosePine_Pine_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Pine_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Rose
import com.pennywiseai.tracker.ui.theme.RosePine_Rose_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Rose_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Subtle
import com.pennywiseai.tracker.ui.theme.RosePine_Subtle_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Subtle_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Surface
import com.pennywiseai.tracker.ui.theme.RosePine_Surface_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Surface_tertiary
import com.pennywiseai.tracker.ui.theme.RosePine_Text
import com.pennywiseai.tracker.ui.theme.RosePine_Text_secondary
import com.pennywiseai.tracker.ui.theme.RosePine_Text_tertiary
import com.pennywiseai.tracker.ui.theme.SNProFontFamily
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()

    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = "Appearance",
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Theme section: Mode + Style + Accent + AMOLED/Blur toggles
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Theme Mode Selector (System / Light / Dark)
                    ThemeModeSelector(
                        currentMode = themeUiState.isDarkTheme,
                        onModeSelected = { themeViewModel.updateDarkTheme(it) }
                    )

                    // Theme Style Selector (Dynamic / Branded)
                    ThemeStyleSelector(
                        currentStyle = themeUiState.themeStyle,
                        onStyleSelected = { themeViewModel.updateThemeStyle(it) }
                    )

                    // Accent Color Picker with ColorSchemeBox (only when branded)
                    BlurredAnimatedVisibility(
                        visible = themeUiState.themeStyle == ThemeStyle.BRANDED,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier.animateContentSize().zIndex(-1f)
                    ) {
                        val isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

                        LazyRow(
                            contentPadding = PaddingValues(Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(AccentColor.entries) { accent ->
                                val color = getAccentColorForDisplay(accent, isDark)
                                val secondary = getSecondaryColorForDisplay(accent, isDark)
                                val tertiary = getTertiaryColorForDisplay(accent, isDark)
                                val isSelected = themeUiState.accentColor == accent

                                ColorSchemeBox(
                                    accent = color,
                                    secondary = secondary,
                                    tertiary = tertiary,
                                    onClick = { themeViewModel.updateAccentColor(accent) },
                                    isSelected = isSelected
                                )
                            }
                        }
                    }

                    // AMOLED + Blur grouped toggles
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        if (themeUiState.isDarkTheme != false) {
                            PreferenceSwitch(
                                title = "AMOLED Black",
                                subtitle = "Use pure black background for deeper contrast",
                                checked = themeUiState.isAmoledMode,
                                onCheckedChange = { themeViewModel.updateAmoledMode(it) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                color = if (themeUiState.isAmoledMode) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DarkMode,
                                            contentDescription = null,
                                            tint = if (themeUiState.isAmoledMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                padding = PaddingValues(horizontal = Spacing.md),
                                isFirst = themeUiState.isDarkTheme != false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                isSingle = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PreferenceSwitch(
                                title = "Blur Effects",
                                subtitle = "Enable glassmorphism blur effects in UI components",
                                checked = themeUiState.blurEffectsEnabled,
                                onCheckedChange = { themeViewModel.updateBlurEffects(it) },
                                padding = PaddingValues(horizontal = Spacing.md),
                                isLast = themeUiState.isDarkTheme != false,
                                isSingle = themeUiState.isDarkTheme == false
                            )
                        }
                    }
                }

                // Navigation Style Section
                SectionHeaderV2(
                    title = "Navigation",
                    modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md)
                )
                NavBarStyleSelector(
                    currentStyle = themeUiState.navBarStyle,
                    onStyleSelected = { themeViewModel.updateNavBarStyle(it) }
                )

                // Cover Style Section
                SectionHeaderV2(
                    title = "Cover Style",
                    modifier = Modifier.padding(start = Spacing.xl)
                )
                CoverStyleSelector(
                    currentStyle = themeUiState.coverStyle,
                    isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme(),
                    onStyleSelected = { themeViewModel.updateCoverStyle(it) }
                )

                // Font Selection Section
                SectionHeaderV2(
                    title = "Fonts",
                    modifier = Modifier.padding(start = Spacing.xl)
                )
                FontSelector(
                    currentFont = themeUiState.appFont,
                    onFontSelected = { themeViewModel.updateAppFont(it) }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

// --- Navigation back button ---

@Composable
private fun NavigationContent(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .animateContentSize()
            .padding(start = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigateBack,
            ),
    ) {
        IconButton(
            onClick = onNavigateBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// --- Theme Mode Selector (System / Light / Dark) ---

@Composable
private fun ThemeModeSelector(
    currentMode: Boolean?,
    onModeSelected: (Boolean?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        data class ModeOption(
            val label: String,
            val icon: androidx.compose.ui.graphics.vector.ImageVector,
            val value: Boolean?,
            val topStart: Int, val topEnd: Int,
            val bottomStart: Int, val bottomEnd: Int
        )

        val options = listOf(
            ModeOption("System", Icons.Default.AutoAwesome, null, 16, 4, 16, 4),
            ModeOption("Light", Icons.Default.LightMode, false, 4, 4, 4, 4),
            ModeOption("Dark", Icons.Default.DarkMode, true, 4, 16, 4, 16)
        )

        options.forEachIndexed { index, option ->
            val isSelected = currentMode == option.value
            val shape = RoundedCornerShape(
                topStart = option.topStart.dp,
                topEnd = option.topEnd.dp,
                bottomStart = option.bottomStart.dp,
                bottomEnd = option.bottomEnd.dp
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = shape
                    )
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md)
                    .clickable(
                        onClick = { onModeSelected(option.value) },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (index < options.lastIndex) {
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

// --- Theme Style Selector (Dynamic / Branded) ---

@Composable
private fun ThemeStyleSelector(
    currentStyle: ThemeStyle,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Dynamic Option (only on Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                        .background(
                            color = if (currentStyle == ThemeStyle.DYNAMIC)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable { onStyleSelected(ThemeStyle.DYNAMIC) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Dynamic",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentStyle == ThemeStyle.DYNAMIC)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Wallpaper Colors",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentStyle == ThemeStyle.DYNAMIC)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Default/Branded Option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                    .background(
                        color = if (currentStyle == ThemeStyle.BRANDED)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onStyleSelected(ThemeStyle.BRANDED) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStyle == ThemeStyle.BRANDED)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Rose Pine Colors",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentStyle == ThemeStyle.BRANDED)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- ColorSchemeBox ---

@Composable
private fun ColorSchemeBox(
    accent: Color,
    secondary: Color,
    tertiary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    Box(
        modifier = modifier
            .size(110.dp)
            .clip(RoundedCornerShape(Spacing.md))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = accent.copy(0.7f),
                        shape = RoundedCornerShape(Spacing.md)
                    )
                } else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Abc",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .width(27.dp)
                        .background(
                            color = tertiary,
                            shape = RoundedCornerShape(Spacing.sm)
                        )
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .width(47.dp)
                        .background(
                            color = secondary,
                            shape = RoundedCornerShape(Spacing.sm)
                        )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(20.dp)
                    .background(
                        accent,
                        RoundedCornerShape(Spacing.xs)
                    )
            )
        }
    }
}

// --- Navigation Bar Style Selector ---

@Composable
private fun NavBarStyleSelector(
    currentStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit
) {
    Column(
        modifier = Modifier.animateContentSize().fillMaxWidth().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Floating Option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 4.dp,
                            bottomStart = 16.dp, bottomEnd = 4.dp
                        )
                    )
                    .background(
                        color = if (currentStyle == NavBarStyle.FLOATING)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onStyleSelected(NavBarStyle.FLOATING) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Floating",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStyle == NavBarStyle.FLOATING)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Modern & Sleek",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentStyle == NavBarStyle.FLOATING)
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Normal Option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp, topEnd = 16.dp,
                            bottomStart = 4.dp, bottomEnd = 16.dp
                        )
                    )
                    .background(
                        color = if (currentStyle == NavBarStyle.NORMAL)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onStyleSelected(NavBarStyle.NORMAL) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Normal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStyle == NavBarStyle.NORMAL)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Standard M3",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentStyle == NavBarStyle.NORMAL)
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- Cover Style Selector ---

@Composable
private fun CoverStyleSelector(
    currentStyle: CoverStyle,
    isDark: Boolean,
    onStyleSelected: (CoverStyle) -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs)
    ) {
        items(CoverStyle.entries.toList()) { style ->
            val isSelected = currentStyle == style

            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (style == CoverStyle.NONE) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                        } else {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = getCoverGradientColors(style, isDark, forPreview = true)
                                )
                            )
                        }
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .clickable { onStyleSelected(style) },
                contentAlignment = Alignment.Center
            ) {
                if (style == CoverStyle.NONE) {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// --- Font Selector ---

@Composable
private fun FontSelector(
    currentFont: AppFont,
    onFontSelected: (AppFont) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // System Default Option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 4.dp,
                            bottomStart = 16.dp, bottomEnd = 4.dp
                        )
                    )
                    .background(
                        color = if (currentFont == AppFont.SYSTEM)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onFontSelected(AppFont.SYSTEM) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        color = if (currentFont == AppFont.SYSTEM)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "System",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Default,
                        color = if (currentFont == AppFont.SYSTEM)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // SN Pro Option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp, topEnd = 16.dp,
                            bottomStart = 4.dp, bottomEnd = 16.dp
                        )
                    )
                    .background(
                        color = if (currentFont == AppFont.SN_PRO)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onFontSelected(AppFont.SN_PRO) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SN Pro",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SNProFontFamily,
                        color = if (currentFont == AppFont.SN_PRO)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Modern Mono",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = SNProFontFamily,
                        color = if (currentFont == AppFont.SN_PRO)
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- Color lookup functions ---

@Composable
private fun getAccentColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSE -> RosePine_Rose
            AccentColor.IRIS -> RosePine_Iris
            AccentColor.PINE -> RosePine_Pine
            AccentColor.GOLD -> RosePine_Gold
            AccentColor.LOVE -> RosePine_Love
            AccentColor.FOAM -> RosePine_Foam
            AccentColor.MUTED -> RosePine_Muted
            AccentColor.SUBTLE -> RosePine_Subtle
            AccentColor.TEXT -> RosePine_Text
            AccentColor.HIGHLIGHT -> RosePine_Highlight
            AccentColor.SURFACE -> RosePine_Surface
            AccentColor.OVERLAY -> RosePine_Overlay
        }
    } else {
        when (accent) {
            AccentColor.ROSE -> Dawn_Rose
            AccentColor.IRIS -> Dawn_Iris
            AccentColor.PINE -> Dawn_Pine
            AccentColor.GOLD -> Dawn_Gold
            AccentColor.LOVE -> Dawn_Love
            AccentColor.FOAM -> Dawn_Foam
            AccentColor.MUTED -> Dawn_Muted
            AccentColor.SUBTLE -> Dawn_Subtle
            AccentColor.TEXT -> Dawn_Text
            AccentColor.HIGHLIGHT -> Dawn_Highlight
            AccentColor.SURFACE -> Dawn_Surface
            AccentColor.OVERLAY -> Dawn_Overlay
        }
    }
}

@Composable
private fun getSecondaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSE -> RosePine_Rose_secondary
            AccentColor.IRIS -> RosePine_Iris_secondary
            AccentColor.PINE -> RosePine_Pine_secondary
            AccentColor.GOLD -> RosePine_Gold_secondary
            AccentColor.LOVE -> RosePine_Love_secondary
            AccentColor.FOAM -> RosePine_Foam_secondary
            AccentColor.MUTED -> RosePine_Muted_secondary
            AccentColor.SUBTLE -> RosePine_Subtle_secondary
            AccentColor.TEXT -> RosePine_Text_secondary
            AccentColor.HIGHLIGHT -> RosePine_Highlight_secondary
            AccentColor.SURFACE -> RosePine_Surface_secondary
            AccentColor.OVERLAY -> RosePine_Overlay_secondary
        }
    } else {
        when (accent) {
            AccentColor.ROSE -> Dawn_Rose_secondary
            AccentColor.IRIS -> Dawn_Iris_secondary
            AccentColor.PINE -> Dawn_Pine_secondary
            AccentColor.GOLD -> Dawn_Gold_secondary
            AccentColor.LOVE -> Dawn_Love_secondary
            AccentColor.FOAM -> Dawn_Foam_secondary
            AccentColor.MUTED -> Dawn_Muted_secondary
            AccentColor.SUBTLE -> Dawn_Subtle_secondary
            AccentColor.TEXT -> Dawn_Text_secondary
            AccentColor.HIGHLIGHT -> Dawn_Highlight_secondary
            AccentColor.SURFACE -> Dawn_Surface_secondary
            AccentColor.OVERLAY -> Dawn_Overlay_secondary
        }
    }
}

@Composable
private fun getTertiaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSE -> RosePine_Rose_tertiary
            AccentColor.IRIS -> RosePine_Iris_tertiary
            AccentColor.PINE -> RosePine_Pine_tertiary
            AccentColor.GOLD -> RosePine_Gold_tertiary
            AccentColor.LOVE -> RosePine_Love_tertiary
            AccentColor.FOAM -> RosePine_Foam_tertiary
            AccentColor.MUTED -> RosePine_Muted_tertiary
            AccentColor.SUBTLE -> RosePine_Subtle_tertiary
            AccentColor.TEXT -> RosePine_Text_tertiary
            AccentColor.HIGHLIGHT -> RosePine_Highlight_tertiary
            AccentColor.SURFACE -> RosePine_Surface_tertiary
            AccentColor.OVERLAY -> RosePine_Overlay_tertiary
        }
    } else {
        when (accent) {
            AccentColor.ROSE -> Dawn_Rose_tertiary
            AccentColor.IRIS -> Dawn_Iris_tertiary
            AccentColor.PINE -> Dawn_Pine_tertiary
            AccentColor.GOLD -> Dawn_Gold_tertiary
            AccentColor.LOVE -> Dawn_Love_tertiary
            AccentColor.FOAM -> Dawn_Foam_tertiary
            AccentColor.MUTED -> Dawn_Muted_tertiary
            AccentColor.SUBTLE -> Dawn_Subtle_tertiary
            AccentColor.TEXT -> Dawn_Text_tertiary
            AccentColor.HIGHLIGHT -> Dawn_Highlight_tertiary
            AccentColor.SURFACE -> Dawn_Surface_tertiary
            AccentColor.OVERLAY -> Dawn_Overlay_tertiary
        }
    }
}
