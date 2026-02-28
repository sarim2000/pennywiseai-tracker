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
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.components.getCoverGradientColors
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Latte_Blue
import com.pennywiseai.tracker.ui.theme.Latte_Blue_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Blue_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Flamingo
import com.pennywiseai.tracker.ui.theme.Latte_Flamingo_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Flamingo_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Green
import com.pennywiseai.tracker.ui.theme.Latte_Green_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Green_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Lavender
import com.pennywiseai.tracker.ui.theme.Latte_Lavender_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Lavender_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Mauve
import com.pennywiseai.tracker.ui.theme.Latte_Mauve_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Mauve_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Peach
import com.pennywiseai.tracker.ui.theme.Latte_Peach_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Peach_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Pink
import com.pennywiseai.tracker.ui.theme.Latte_Pink_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Pink_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Red
import com.pennywiseai.tracker.ui.theme.Latte_Red_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Red_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Rosewater
import com.pennywiseai.tracker.ui.theme.Latte_Rosewater_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Rosewater_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Sapphire
import com.pennywiseai.tracker.ui.theme.Latte_Sapphire_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Sapphire_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Teal
import com.pennywiseai.tracker.ui.theme.Latte_Teal_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Teal_tertiary
import com.pennywiseai.tracker.ui.theme.Latte_Yellow
import com.pennywiseai.tracker.ui.theme.Latte_Yellow_secondary
import com.pennywiseai.tracker.ui.theme.Latte_Yellow_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Blue_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Blue_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Blue_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Flamingo_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Flamingo_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Flamingo_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Green_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Green_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Green_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Lavender_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Lavender_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Lavender_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Mauve_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Mauve_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Mauve_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Peach_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Peach_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Peach_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Pink_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Pink_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Pink_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Red_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Red_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Red_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Rosewater_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Rosewater_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Rosewater_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Sapphire_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Sapphire_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Sapphire_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Teal_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Teal_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Teal_dim_tertiary
import com.pennywiseai.tracker.ui.theme.Macchiato_Yellow_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Yellow_dim_secondary
import com.pennywiseai.tracker.ui.theme.Macchiato_Yellow_dim_tertiary
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
                SectionHeader(
                    title = "Navigation",
                    modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md)
                )
                NavBarStyleSelector(
                    currentStyle = themeUiState.navBarStyle,
                    onStyleSelected = { themeViewModel.updateNavBarStyle(it) }
                )

                // Cover Style Section
                SectionHeader(
                    title = "Cover Style",
                    modifier = Modifier.padding(start = Spacing.xl)
                )
                CoverStyleSelector(
                    currentStyle = themeUiState.coverStyle,
                    isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme(),
                    onStyleSelected = { themeViewModel.updateCoverStyle(it) }
                )

                // Font Selection Section
                SectionHeader(
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
                        text = "Catppuccin Colors",
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
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim
            AccentColor.PINK -> Macchiato_Pink_dim
            AccentColor.MAUVE -> Macchiato_Mauve_dim
            AccentColor.RED -> Macchiato_Red_dim
            AccentColor.PEACH -> Macchiato_Peach_dim
            AccentColor.YELLOW -> Macchiato_Yellow_dim
            AccentColor.GREEN -> Macchiato_Green_dim
            AccentColor.TEAL -> Macchiato_Teal_dim
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim
            AccentColor.BLUE -> Macchiato_Blue_dim
            AccentColor.LAVENDER -> Macchiato_Lavender_dim
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater
            AccentColor.FLAMINGO -> Latte_Flamingo
            AccentColor.PINK -> Latte_Pink
            AccentColor.MAUVE -> Latte_Mauve
            AccentColor.RED -> Latte_Red
            AccentColor.PEACH -> Latte_Peach
            AccentColor.YELLOW -> Latte_Yellow
            AccentColor.GREEN -> Latte_Green
            AccentColor.TEAL -> Latte_Teal
            AccentColor.SAPPHIRE -> Latte_Sapphire
            AccentColor.BLUE -> Latte_Blue
            AccentColor.LAVENDER -> Latte_Lavender
        }
    }
}

@Composable
private fun getSecondaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_secondary
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_secondary
            AccentColor.PINK -> Macchiato_Pink_dim_secondary
            AccentColor.MAUVE -> Macchiato_Mauve_dim_secondary
            AccentColor.RED -> Macchiato_Red_dim_secondary
            AccentColor.PEACH -> Macchiato_Peach_dim_secondary
            AccentColor.YELLOW -> Macchiato_Yellow_dim_secondary
            AccentColor.GREEN -> Macchiato_Green_dim_secondary
            AccentColor.TEAL -> Macchiato_Teal_dim_secondary
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_secondary
            AccentColor.BLUE -> Macchiato_Blue_dim_secondary
            AccentColor.LAVENDER -> Macchiato_Lavender_dim_secondary
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater_secondary
            AccentColor.FLAMINGO -> Latte_Flamingo_secondary
            AccentColor.PINK -> Latte_Pink_secondary
            AccentColor.MAUVE -> Latte_Mauve_secondary
            AccentColor.RED -> Latte_Red_secondary
            AccentColor.PEACH -> Latte_Peach_secondary
            AccentColor.YELLOW -> Latte_Yellow_secondary
            AccentColor.GREEN -> Latte_Green_secondary
            AccentColor.TEAL -> Latte_Teal_secondary
            AccentColor.SAPPHIRE -> Latte_Sapphire_secondary
            AccentColor.BLUE -> Latte_Blue_secondary
            AccentColor.LAVENDER -> Latte_Lavender_secondary
        }
    }
}

@Composable
private fun getTertiaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_tertiary
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_tertiary
            AccentColor.PINK -> Macchiato_Pink_dim_tertiary
            AccentColor.MAUVE -> Macchiato_Mauve_dim_tertiary
            AccentColor.RED -> Macchiato_Red_dim_tertiary
            AccentColor.PEACH -> Macchiato_Peach_dim_tertiary
            AccentColor.YELLOW -> Macchiato_Yellow_dim_tertiary
            AccentColor.GREEN -> Macchiato_Green_dim_tertiary
            AccentColor.TEAL -> Macchiato_Teal_dim_tertiary
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_tertiary
            AccentColor.BLUE -> Macchiato_Blue_dim_tertiary
            AccentColor.LAVENDER -> Macchiato_Lavender_dim_tertiary
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater_tertiary
            AccentColor.FLAMINGO -> Latte_Flamingo_tertiary
            AccentColor.PINK -> Latte_Pink_tertiary
            AccentColor.MAUVE -> Latte_Mauve_tertiary
            AccentColor.RED -> Latte_Red_tertiary
            AccentColor.PEACH -> Latte_Peach_tertiary
            AccentColor.YELLOW -> Latte_Yellow_tertiary
            AccentColor.GREEN -> Latte_Green_tertiary
            AccentColor.TEAL -> Latte_Teal_tertiary
            AccentColor.SAPPHIRE -> Latte_Sapphire_tertiary
            AccentColor.BLUE -> Latte_Blue_tertiary
            AccentColor.LAVENDER -> Latte_Lavender_tertiary
        }
    }
}
