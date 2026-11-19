package com.pennywiseai.tracker.presentation.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexHeadingFamily
import com.pennywiseai.tracker.ui.theme.SNProFontFamily
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.pennyWiseColorScheme
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
                title = stringResource(R.string.appearance_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                hazeState = hazeState,
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
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Theme: mode, then the palette previews, then accent when Default is selected.
                Column(
                    modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    ThemeModeSelector(
                        currentMode = themeUiState.isDarkTheme,
                        onModeSelected = { themeViewModel.updateDarkTheme(it) }
                    )

                    val isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

                    ThemePicker(
                        currentStyle = themeUiState.themeStyle,
                        isDark = isDark,
                        isAmoled = themeUiState.isAmoledMode,
                        accentColor = themeUiState.accentColor,
                        onStyleSelected = { themeViewModel.updateThemeStyle(it) }
                    )

                    BlurredAnimatedVisibility(
                        visible = themeUiState.themeStyle == ThemeStyle.BRANDED,
                        enter = pwExpand(),
                        exit = pwCollapse(),
                        modifier = Modifier.zIndex(-1f)
                    ) {
                        AccentPicker(
                            currentAccent = themeUiState.accentColor,
                            isDark = isDark,
                            onAccentSelected = { themeViewModel.updateAccentColor(it) }
                        )
                    }
                }

                SectionHeaderV2(
                    title = stringResource(R.string.appearance_fonts_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                FontSelector(
                    currentFont = themeUiState.appFont,
                    onFontSelected = { themeViewModel.updateAppFont(it) }
                )

                SectionHeaderV2(
                    title = stringResource(R.string.appearance_navigation_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                NavBarStyleSelector(
                    currentStyle = themeUiState.navBarStyle,
                    onStyleSelected = { themeViewModel.updateNavBarStyle(it) }
                )

                // Home always stays, so it isn't listed.
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_bottom_bar_section),
                    subtitle = stringResource(R.string.appearance_bottom_bar_subtitle),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                val hideableTabs = BottomNavItem.hideable
                GroupedList {
                    hideableTabs.forEachIndexed { index, tab ->
                        val shown = tab.route !in themeUiState.hiddenNavTabs
                        PreferenceSwitch(
                            title = stringResource(tab.titleRes),
                            checked = shown,
                            onCheckedChange = { themeViewModel.setNavTabVisible(tab.route, it) },
                            leadingIcon = {
                                IconTile(
                                    icon = tab.icon,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            position = ListItemPosition.from(index, hideableTabs.size)
                        )
                    }
                }

                // AMOLED and blur are conditional, so positions come from what's showing.
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_accessibility_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                val showAmoled = themeUiState.isDarkTheme != false
                val showBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val toggleCount = (if (showAmoled) 1 else 0) + (if (showBlur) 1 else 0) + 1
                GroupedList {
                    if (showAmoled) {
                        PreferenceSwitch(
                            title = stringResource(R.string.appearance_amoled_title),
                            subtitle = stringResource(R.string.appearance_amoled_subtitle),
                            checked = themeUiState.isAmoledMode,
                            onCheckedChange = { themeViewModel.updateAmoledMode(it) },
                            leadingIcon = {
                                IconTile(
                                    icon = Icons.Default.DarkMode,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            position = ListItemPosition.from(0, toggleCount)
                        )
                    }
                    if (showBlur) {
                        PreferenceSwitch(
                            title = stringResource(R.string.appearance_blur_title),
                            subtitle = stringResource(R.string.appearance_blur_subtitle),
                            checked = themeUiState.blurEffectsEnabled,
                            onCheckedChange = { themeViewModel.updateBlurEffects(it) },
                            position = ListItemPosition.from(toggleCount - 2, toggleCount)
                        )
                    }
                    PreferenceSwitch(
                        title = stringResource(R.string.appearance_red_spending_title),
                        subtitle = stringResource(R.string.appearance_red_spending_subtitle),
                        checked = themeUiState.redSpending,
                        onCheckedChange = { themeViewModel.updateRedSpending(it) },
                        position = ListItemPosition.from(toggleCount - 1, toggleCount)
                    )
                }

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
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .padding(start = Spacing.md)
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
            ),
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.appearance_back),
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        }
    }
}

// --- Theme mode (System / Light / Dark) ---

@Composable
private fun ThemeModeSelector(
    currentMode: Boolean?,
    onModeSelected: (Boolean?) -> Unit
) {
    ConnectedChoiceRow(
        options = listOf(null, false, true),
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

// --- Theme picker: a row of mini app mock-ups, each drawn in its own theme (as in Mihon) ---

private val pickerThemes: List<ThemeStyle> =
    listOf(ThemeStyle.DYNAMIC, ThemeStyle.BRANDED) +
        ThemeStyle.entries.filter { it != ThemeStyle.DYNAMIC && it != ThemeStyle.BRANDED }

@StringRes
private fun ThemeStyle.titleRes(): Int = when (this) {
    ThemeStyle.DYNAMIC -> R.string.appearance_style_dynamic
    ThemeStyle.BRANDED -> R.string.appearance_style_default
    ThemeStyle.CATPPUCCIN -> R.string.appearance_theme_catppuccin
    ThemeStyle.GREEN_APPLE -> R.string.appearance_theme_green_apple
    ThemeStyle.LAVENDER -> R.string.appearance_theme_lavender
    ThemeStyle.MIDNIGHT_DUSK -> R.string.appearance_theme_midnight_dusk
    ThemeStyle.NORD -> R.string.appearance_theme_nord
    ThemeStyle.STRAWBERRY_DAIQUIRI -> R.string.appearance_theme_strawberry_daiquiri
    ThemeStyle.TAKO -> R.string.appearance_theme_tako
    ThemeStyle.TEAL_TURQUOISE -> R.string.appearance_theme_teal_turquoise
    ThemeStyle.TIDAL_WAVE -> R.string.appearance_theme_tidal_wave
    ThemeStyle.YIN_YANG -> R.string.appearance_theme_yin_yang
    ThemeStyle.YOTSUBA -> R.string.appearance_theme_yotsuba
    ThemeStyle.TOKYO_NIGHT -> R.string.appearance_theme_tokyo_night
    ThemeStyle.MONOCHROME -> R.string.appearance_theme_monochrome
}

@Composable
private fun ThemePicker(
    currentStyle: ThemeStyle,
    isDark: Boolean,
    isAmoled: Boolean,
    accentColor: AccentColor,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    val themes = remember {
        pickerThemes.filter { it != ThemeStyle.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    }
    // Open on the chosen theme, which may sit past the first screenful.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = themes.indexOf(currentStyle).coerceAtLeast(0))
    LazyRow(
        modifier = Modifier.selectableGroup(),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(themes, key = { it.name }) { style ->
            Column(modifier = Modifier.width(Dimensions.Component.themePreviewWidth).padding(top = Spacing.sm)) {
                val title = stringResource(style.titleRes())
                ThemePreviewCard(
                    scheme = pennyWiseColorScheme(
                        themeStyle = style,
                        darkTheme = isDark,
                        accentColor = accentColor,
                        isAmoledMode = isAmoled
                    ),
                    selected = currentStyle == style,
                    contentDescription = title,
                    onClick = { onStyleSelected(style) }
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2
                )
            }
        }
    }
}

/** A phone-shaped sketch of the app (top bar, balance card, two rows, nav bar) painted in [scheme]. */
@Composable
private fun ThemePreviewCard(
    scheme: ColorScheme,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    MaterialTheme(colorScheme = scheme) {
        val colors = MaterialTheme.colorScheme
        val pill = MaterialTheme.shapes.small
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .border(
                    width = Spacing.xs,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                )
                .padding(Spacing.xs)
                // Inner corner = outer corner − border, so the two stay concentric.
                .clip(RoundedCornerShape(Dimensions.CornerRadius.card))
                .background(colors.background)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sm).height(Dimensions.Icon.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .weight(0.7f)
                        .padding(end = Spacing.xs)
                        .background(colors.onSurface, pill)
                )
                Box(modifier = Modifier.weight(0.3f), contentAlignment = Alignment.CenterEnd) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    }
                }
            }

            // Balance card with secondary/tertiary chips
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    .fillMaxWidth()
                    .background(colors.primaryContainer, pill)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(Spacing.sm)
                        .background(colors.onPrimaryContainer, pill)
                )
                Row(modifier = Modifier.size(width = Dimensions.Icon.medium, height = Dimensions.Icon.tiny).clip(RoundedCornerShape(Dimensions.CornerRadius.small))) {
                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colors.tertiary))
                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colors.secondary))
                }
            }

            // Two transaction rows
            listOf(colors.secondary, colors.tertiary).forEach { dot ->
                Row(
                    modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(Dimensions.Icon.tiny).background(dot, CircleShape))
                    Box(
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .alpha(0.6f)
                            .height(Spacing.sm)
                            .weight(1f)
                            .background(colors.onSurface, pill)
                    )
                }
            }

            // Bottom bar
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.xl)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(Dimensions.Icon.small).background(colors.primary, CircleShape))
                    Box(
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .alpha(0.6f)
                            .height(Dimensions.Icon.small)
                            .weight(1f)
                            .background(colors.onSurface, pill)
                    )
                }
            }
        }
    }
}

// --- Accent swatches for the Default (Rosé Pine) theme ---

@Composable
private fun AccentPicker(
    currentAccent: AccentColor,
    isDark: Boolean,
    onAccentSelected: (AccentColor) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentAccent.ordinal)
    LazyRow(
        modifier = Modifier.selectableGroup(),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(AccentColor.entries, key = { it.name }) { accent ->
            val scheme = pennyWiseColorScheme(ThemeStyle.BRANDED, isDark, accentColor = accent)
            val selected = currentAccent == accent
            // Wallpaper-style swatch: primary on top, secondary | tertiary below.
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatarLarge)
                    .border(
                        width = Spacing.xxs,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(Spacing.xs)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onAccentSelected(accent) })
                    // ponytail: Rosé Pine palette names are proper nouns, so no string resources.
                    .semantics { contentDescription = accent.name.lowercase().replaceFirstChar { it.uppercase() } },
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f).background(scheme.primary))
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Box(modifier = Modifier.fillMaxHeight().weight(1f).background(scheme.secondary))
                        Box(modifier = Modifier.fillMaxHeight().weight(1f).background(scheme.tertiary))
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier
                            .size(Dimensions.Icon.inline)
                            .background(scheme.primary, CircleShape)
                            .padding(Spacing.xxs)
                    )
                }
            }
        }
    }
}

// --- Navigation Bar Style Selector ---

@Composable
private fun NavBarStyleSelector(
    currentStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit
) {
    val options = listOf(
        Triple(NavBarStyle.FLOATING, R.string.appearance_nav_floating, R.string.appearance_nav_floating_subtitle),
        Triple(NavBarStyle.NORMAL, R.string.appearance_nav_normal, R.string.appearance_nav_normal_subtitle),
    )
    GroupedList(modifier = Modifier.selectableGroup()) {
        options.forEachIndexed { index, (style, title, subtitle) ->
            val selected = currentStyle == style
            GroupedRow(
                position = ListItemPosition.from(index, options.size),
                onClick = { onStyleSelected(style) },
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Font Selector ---

/** A font tile: its name and subtitle are drawn in the font itself. */
private data class FontOption(
    val font: AppFont,
    val family: FontFamily,
    val name: String,
    val subtitle: String
)

@Composable
private fun FontSelector(
    currentFont: AppFont,
    onFontSelected: (AppFont) -> Unit
) {
    val options = listOf(
        FontOption(
            AppFont.GOOGLE_SANS_FLEX,
            GoogleSansFlexHeadingFamily,
            "Google Sans Flex",
            stringResource(R.string.appearance_font_google_sans_flex_subtitle)
        ),
        FontOption(
            AppFont.SYSTEM,
            FontFamily.Default,
            stringResource(R.string.appearance_font_system),
            stringResource(R.string.appearance_font_system_subtitle)
        ),
        FontOption(
            AppFont.SN_PRO,
            SNProFontFamily,
            "SN Pro",
            stringResource(R.string.appearance_font_sn_pro_subtitle)
        )
    )
    GroupedList(modifier = Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val selected = currentFont == option.font
            GroupedRow(
                position = ListItemPosition.from(index, options.size),
                onClick = { onFontSelected(option.font) },
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = option.family
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = option.family,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
