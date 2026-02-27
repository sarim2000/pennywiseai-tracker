package com.pennywiseai.tracker.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.ui.components.getCoverGradientColors
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Latte_Blue
import com.pennywiseai.tracker.ui.theme.Latte_Flamingo
import com.pennywiseai.tracker.ui.theme.Latte_Green
import com.pennywiseai.tracker.ui.theme.Latte_Lavender
import com.pennywiseai.tracker.ui.theme.Latte_Mauve
import com.pennywiseai.tracker.ui.theme.Latte_Peach
import com.pennywiseai.tracker.ui.theme.Latte_Pink
import com.pennywiseai.tracker.ui.theme.Latte_Red
import com.pennywiseai.tracker.ui.theme.Latte_Rosewater
import com.pennywiseai.tracker.ui.theme.Latte_Sapphire
import com.pennywiseai.tracker.ui.theme.Latte_Teal
import com.pennywiseai.tracker.ui.theme.Latte_Yellow
import com.pennywiseai.tracker.ui.theme.Macchiato_Blue_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Flamingo_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Green_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Lavender_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Mauve_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Peach_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Pink_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Red_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Rosewater_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Sapphire_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Teal_dim
import com.pennywiseai.tracker.ui.theme.Macchiato_Yellow_dim
import com.pennywiseai.tracker.ui.theme.SNProFontFamily
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel

@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Theme Mode Selector
            SectionHeader(title = "Theme Mode")

            ThemeModeSelector(
                currentMode = themeUiState.isDarkTheme,
                onModeSelected = { themeViewModel.updateDarkTheme(it) }
            )

            // Theme Style Selector
            SectionHeader(title = "Theme Style")

            ThemeStyleSelector(
                currentStyle = themeUiState.themeStyle,
                onStyleSelected = { themeViewModel.updateThemeStyle(it) }
            )

            // Accent Color Picker (only when branded)
            AnimatedVisibility(
                visible = themeUiState.themeStyle == ThemeStyle.BRANDED,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.animateContentSize()
            ) {
                Column {
                    SectionHeader(title = "Accent Color")
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    AccentColorPicker(
                        selectedColor = themeUiState.accentColor,
                        isDark = isDark,
                        onColorSelected = { themeViewModel.updateAccentColor(it) }
                    )
                }
            }

            // AMOLED Black Toggle (only in dark mode)
            AnimatedVisibility(
                visible = themeUiState.isDarkTheme != false,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                SettingsSwitchItem(
                    icon = Icons.Default.DarkMode,
                    iconBgColor = Color(0xFF2D2D3A),
                    iconTint = Color(0xFFB0B0CC),
                    title = "AMOLED Black",
                    subtitle = "Pure black background for OLED screens",
                    checked = themeUiState.isAmoledMode,
                    onCheckedChange = { themeViewModel.updateAmoledMode(it) }
                )
            }

            // Blur Effects Toggle (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsSwitchItem(
                    icon = Icons.Default.BlurOn,
                    iconBgColor = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title = "Blur Effects",
                    subtitle = "Glassmorphism on navigation bars",
                    checked = themeUiState.blurEffectsEnabled,
                    onCheckedChange = { themeViewModel.updateBlurEffects(it) }
                )
            }

            // Navigation Bar Style
            SectionHeader(title = "Navigation Style")

            NavBarStyleSelector(
                currentStyle = themeUiState.navBarStyle,
                onStyleSelected = { themeViewModel.updateNavBarStyle(it) }
            )

            // Cover Style
            SectionHeader(title = "Cover Style")

            CoverStyleSelector(
                currentStyle = themeUiState.coverStyle,
                isDark = isDark,
                onStyleSelected = { themeViewModel.updateCoverStyle(it) }
            )

            // Font Selection
            SectionHeader(title = "Font")

            FontSelector(
                currentFont = themeUiState.appFont,
                onFontSelected = { themeViewModel.updateAppFont(it) }
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ThemeModeSelector(
    currentMode: Boolean?,
    onModeSelected: (Boolean?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        data class ModeOption(
            val label: String,
            val icon: @Composable () -> Unit,
            val value: Boolean?,
            val isFirst: Boolean = false,
            val isLast: Boolean = false
        )

        val options = listOf(
            ModeOption("System", { Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp)) }, null, isFirst = true),
            ModeOption("Light", { Icon(Icons.Default.LightMode, null, Modifier.size(20.dp)) }, false),
            ModeOption("Dark", { Icon(Icons.Default.DarkMode, null, Modifier.size(20.dp)) }, true, isLast = true)
        )

        options.forEach { option ->
            val isSelected = currentMode == option.value
            val shape = RoundedCornerShape(
                topStart = if (option.isFirst) 16.dp else 4.dp,
                topEnd = if (option.isLast) 16.dp else 4.dp,
                bottomStart = if (option.isFirst) 16.dp else 4.dp,
                bottomEnd = if (option.isLast) 16.dp else 4.dp
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
                    .clickable(
                        onClick = { onModeSelected(option.value) },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = if (isSelected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurface

                    Box { option.icon() }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeStyleSelector(
    currentStyle: ThemeStyle,
    onStyleSelected: (ThemeStyle) -> Unit
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        color = if (currentStyle == ThemeStyle.DYNAMIC)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onStyleSelected(ThemeStyle.DYNAMIC) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (currentStyle == ThemeStyle.DYNAMIC)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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

        // Branded/Default Option
        Box(
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = if (currentStyle == ThemeStyle.BRANDED)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable { onStyleSelected(ThemeStyle.BRANDED) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (currentStyle == ThemeStyle.BRANDED)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun AccentColorPicker(
    selectedColor: AccentColor,
    isDark: Boolean,
    onColorSelected: (AccentColor) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs)
    ) {
        items(AccentColor.entries.toList()) { accent ->
            val color = getAccentDisplayColor(accent, isDark)
            val isSelected = selectedColor == accent

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .then(
                        if (isSelected) Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = CircleShape
                        ) else Modifier
                    )
                    .clickable { onColorSelected(accent) },
                contentAlignment = Alignment.Center
            ) {
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

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun NavBarStyleSelector(
    currentStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Normal Option
        Box(
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
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

        // Floating Option
        Box(
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
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
    }
}

@Composable
private fun CoverStyleSelector(
    currentStyle: CoverStyle,
    isDark: Boolean,
    onStyleSelected: (CoverStyle) -> Unit
) {
    LazyRow(
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

@Composable
private fun FontSelector(
    currentFont: AppFont,
    onFontSelected: (AppFont) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // System Default
        Box(
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
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
                    text = "System Font",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Default,
                    color = if (currentFont == AppFont.SYSTEM)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // SN Pro
        Box(
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
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
                    text = "Modern",
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

@Composable
private fun getAccentDisplayColor(accent: AccentColor, isDark: Boolean): Color {
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
