package com.pennywiseai.tracker.ui.screens.settings

import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pennywiseai.tracker.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()

    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.appearance_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                hasBackButton = true,
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
                    .overScrollVertical()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Theme section: Mode + AMOLED/Blur toggles
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_theme_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content),
                    topSpacing = Spacing.none
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Theme Mode Selector (System / Light / Dark)
                    ThemeModeSelector(
                        currentMode = themeUiState.isDarkTheme,
                        onModeSelected = { themeViewModel.updateDarkTheme(it) }
                    )

                    // AMOLED + Blur grouped toggles. Both rows are conditional,
                    // so positions are derived from which ones are actually
                    // showing — a hand-maintained isFirst/isLast pair got this
                    // wrong whenever only one row was visible.
                    val showAmoled = themeUiState.isDarkTheme != false
                    val showBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val toggleCount = (if (showAmoled) 1 else 0) + (if (showBlur) 1 else 0)
                    GroupedList(modifier = Modifier.padding(horizontal = Spacing.md)) {
                        if (showAmoled) {
                            PreferenceSwitch(
                                title = stringResource(R.string.appearance_amoled_title),
                                subtitle = stringResource(R.string.appearance_amoled_subtitle),
                                checked = themeUiState.isAmoledMode,
                                onCheckedChange = { themeViewModel.updateAmoledMode(it) },
                                leadingIcon = { NeutralIconTile(Icons.Default.DarkMode) },
                                position = ListItemPosition.from(0, toggleCount)
                            )
                        }

                        if (showBlur) {
                            PreferenceSwitch(
                                title = stringResource(R.string.appearance_blur_title),
                                subtitle = stringResource(R.string.appearance_blur_subtitle),
                                checked = themeUiState.blurEffectsEnabled,
                                onCheckedChange = { themeViewModel.updateBlurEffects(it) },
                                leadingIcon = { NeutralIconTile(Icons.Default.BlurOn) },
                                position = ListItemPosition.from(toggleCount - 1, toggleCount)
                            )
                        }
                    }
                }

                // Colours section: Style + Accent
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_colours_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
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
                            contentPadding = PaddingValues(horizontal = Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(AccentColor.entries) { accent ->
                                val color = getAccentColorForDisplay(accent, isDark)
                                val secondary = getSecondaryColorForDisplay(accent, isDark)
                                val tertiary = getTertiaryColorForDisplay(accent, isDark)
                                val isSelected = themeUiState.accentColor == accent

                                ColorSchemeBox(
                                    name = accentColorName(accent),
                                    accent = color,
                                    secondary = secondary,
                                    tertiary = tertiary,
                                    onClick = { themeViewModel.updateAccentColor(accent) },
                                    isSelected = isSelected
                                )
                            }
                        }
                    }
                }

                // Navigation Style Section
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_navigation_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                NavBarStyleSelector(
                    currentStyle = themeUiState.navBarStyle,
                    onStyleSelected = { themeViewModel.updateNavBarStyle(it) }
                )

                // Bottom bar tabs (#912). Home always stays, so it isn't listed.
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_bottom_bar_section),
                    subtitle = stringResource(R.string.appearance_bottom_bar_subtitle),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                val hideableTabs = BottomNavItem.hideable
                GroupedList(modifier = Modifier.padding(horizontal = Spacing.md)) {
                    hideableTabs.forEachIndexed { index, tab ->
                        val shown = tab.route !in themeUiState.hiddenNavTabs
                        PreferenceSwitch(
                            title = stringResource(tab.titleRes),
                            checked = shown,
                            onCheckedChange = { themeViewModel.setNavTabVisible(tab.route, it) },
                            leadingIcon = { NeutralIconTile(tab.icon) },
                            position = ListItemPosition.from(index, hideableTabs.size)
                        )
                    }
                }

                // Cover Style Section
                // Folded: nine previews outweighed every setting around them.
                var showCovers by rememberSaveable { mutableStateOf(false) }
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_cover_section),
                    subtitle = coverStyleName(themeUiState.coverStyle),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                    action = {
                        TextButton(onClick = { showCovers = !showCovers }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(if (showCovers) R.string.appearance_cover_done else R.string.appearance_cover_change))
                        }
                    }
                )
                AnimatedVisibility(visible = showCovers) {
                    CoverStyleSelector(
                        currentStyle = themeUiState.coverStyle,
                        isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme(),
                        onStyleSelected = { themeViewModel.updateCoverStyle(it) }
                    )
                }

                // Font Selection Section
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_fonts_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigateBack,
            ),
    ) {
        IconButton(
            onClick = onNavigateBack,
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.appearance_back)
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
    ConnectedChoiceRow(
        options = listOf<Boolean?>(null, false, true),
        selected = currentMode,
        onSelect = onModeSelected,
        label = { mode ->
            stringResource(
                when (mode) {
                    null -> R.string.appearance_mode_system
                    false -> R.string.appearance_mode_light
                    true -> R.string.appearance_mode_dark
                }
            )
        },
        modifier = Modifier.padding(horizontal = Spacing.md)
    )
}

// --- Theme Style Selector (Dynamic / Rose Pine) ---

@Composable
private fun ThemeStyleSelector(
    currentStyle: ThemeStyle,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    ConnectedChoiceRow(
        // Dynamic colour needs Android 12+.
        options = buildList<ThemeStyle> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(ThemeStyle.DYNAMIC)
            add(ThemeStyle.BRANDED)
        },
        selected = currentStyle,
        onSelect = onStyleSelected,
        modifier = Modifier.padding(horizontal = Spacing.md)
    ) { style ->
        if (style == ThemeStyle.DYNAMIC) {
            ChoiceLabels(
                title = stringResource(R.string.appearance_style_dynamic),
                subtitle = stringResource(R.string.appearance_style_dynamic_subtitle)
            )
        } else {
            ChoiceLabels(
                title = stringResource(R.string.appearance_style_default),
                subtitle = stringResource(R.string.appearance_style_default_subtitle)
            )
        }
    }
}

/** Title + supporting text inside a choice button; colour comes from the button's state. */
@Composable
private fun ChoiceLabels(
    title: String,
    subtitle: String,
    fontFamily: FontFamily? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center
        )
    }
}

/** A row's leading icon; the row's switch carries the on/off state. */
@Composable
private fun NeutralIconTile(icon: ImageVector) {
    IconTile(
        icon = icon,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// --- Named tiles ---

private val PaletteTileSize = 110.dp
private val CoverTileWidth = 80.dp
private val CoverTileHeight = 56.dp

/**
 * A preview tile with its name underneath, tappable as one. The column widens with the font
 * scale so a long name wraps between words instead of inside one.
 */
@Composable
private fun NamedTile(
    name: String,
    tileWidth: Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
    tile: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .width(tileWidth * LocalDensity.current.fontScale.coerceAtLeast(1f))
            .clip(RoundedCornerShape(Dimensions.CornerRadius.card))
            .selectable(selected = isSelected, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        tile()
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The mark on a selected tile. In the corner so it never covers the tile's preview. */
@Composable
private fun SelectedMark(modifier: Modifier = Modifier) {
    IconTile(
        icon = Icons.Default.Check,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        size = Dimensions.Icon.medium,
        glyphSize = Dimensions.Icon.small,
        contentDescription = stringResource(R.string.appearance_cover_selected),
        modifier = modifier.padding(Spacing.xs)
    )
}

// --- ColorSchemeBox ---

@Composable
private fun ColorSchemeBox(
    name: String,
    accent: Color,
    secondary: Color,
    tertiary: Color,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    NamedTile(
        name = name,
        tileWidth = PaletteTileSize,
        isSelected = isSelected,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .size(PaletteTileSize)
                .clip(RoundedCornerShape(Spacing.md))
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = Spacing.xxs,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(Spacing.md)
                        )
                    } else Modifier
                )
                .background(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
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
            if (isSelected) {
                SelectedMark(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun accentColorName(accent: AccentColor): String = stringResource(
    when (accent) {
        AccentColor.ROSE -> R.string.appearance_accent_rose
        AccentColor.IRIS -> R.string.appearance_accent_iris
        AccentColor.PINE -> R.string.appearance_accent_pine
        AccentColor.GOLD -> R.string.appearance_accent_gold
        AccentColor.LOVE -> R.string.appearance_accent_love
        AccentColor.FOAM -> R.string.appearance_accent_foam
        AccentColor.MUTED -> R.string.appearance_accent_muted
        AccentColor.SUBTLE -> R.string.appearance_accent_subtle
        AccentColor.TEXT -> R.string.appearance_accent_text
        AccentColor.HIGHLIGHT -> R.string.appearance_accent_highlight
        AccentColor.SURFACE -> R.string.appearance_accent_surface
        AccentColor.OVERLAY -> R.string.appearance_accent_overlay
    }
)

// --- Navigation Bar Style Selector ---

@Composable
private fun NavBarStyleSelector(
    currentStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit
) {
    ConnectedChoiceRow(
        options = listOf(NavBarStyle.FLOATING, NavBarStyle.NORMAL),
        selected = currentStyle,
        onSelect = onStyleSelected,
        modifier = Modifier.padding(horizontal = Spacing.md)
    ) { style ->
        if (style == NavBarStyle.FLOATING) {
            ChoiceLabels(
                title = stringResource(R.string.appearance_nav_floating),
                subtitle = stringResource(R.string.appearance_nav_floating_subtitle)
            )
        } else {
            ChoiceLabels(
                title = stringResource(R.string.appearance_nav_normal),
                subtitle = stringResource(R.string.appearance_nav_normal_subtitle)
            )
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
    // Wraps, so no option is left half off the screen.
    FlowRow(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CoverStyle.entries.forEach { style ->
            val isSelected = currentStyle == style

            NamedTile(
                name = coverStyleName(style),
                tileWidth = CoverTileWidth,
                isSelected = isSelected,
                onClick = { onStyleSelected(style) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CoverTileHeight)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.card))
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
                                width = Spacing.xxs,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(Dimensions.CornerRadius.card)
                            ) else Modifier
                        )
                ) {
                    if (isSelected) {
                        SelectedMark(Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}

@Composable
private fun coverStyleName(style: CoverStyle): String = stringResource(
    when (style) {
        CoverStyle.NONE -> R.string.appearance_cover_none
        CoverStyle.AURORA -> R.string.appearance_cover_aurora
        CoverStyle.SUNSET -> R.string.appearance_cover_sunset
        CoverStyle.OCEAN -> R.string.appearance_cover_ocean
        CoverStyle.FOREST -> R.string.appearance_cover_forest
        CoverStyle.LAVENDER_MIST -> R.string.appearance_cover_lavender_mist
        CoverStyle.MIDNIGHT -> R.string.appearance_cover_midnight
        CoverStyle.ROSE_GOLD -> R.string.appearance_cover_rose_gold
        CoverStyle.NORTHERN_LIGHTS -> R.string.appearance_cover_northern_lights
    }
)

// --- Font Selector ---

@Composable
private fun FontSelector(
    currentFont: AppFont,
    onFontSelected: (AppFont) -> Unit
) {
    ConnectedChoiceRow(
        options = listOf(AppFont.SYSTEM, AppFont.SN_PRO),
        selected = currentFont,
        onSelect = onFontSelected,
        modifier = Modifier.padding(horizontal = Spacing.md)
    ) { font ->
        // Each option previews itself in its own font.
        if (font == AppFont.SYSTEM) {
            ChoiceLabels(
                title = stringResource(R.string.appearance_font_default),
                subtitle = stringResource(R.string.appearance_font_default_subtitle),
                fontFamily = FontFamily.Default
            )
        } else {
            ChoiceLabels(
                title = "SN Pro",
                subtitle = stringResource(R.string.appearance_font_sn_pro_subtitle),
                fontFamily = SNProFontFamily
            )
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
