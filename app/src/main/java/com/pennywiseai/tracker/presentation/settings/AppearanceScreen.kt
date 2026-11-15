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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
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
                // Theme section: Mode + Style + Accent + AMOLED/Blur toggles
                Column(
                    modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Theme Mode Selector (System / Light / Dark)
                    ThemeModeSelector(
                        currentMode = themeUiState.isDarkTheme,
                        onModeSelected = { themeViewModel.updateDarkTheme(it) }
                    )

                    val isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

                    // Theme picker (Dynamic / Default / Mihon palettes)
                    ThemePicker(
                        currentStyle = themeUiState.themeStyle,
                        isDark = isDark,
                        isAmoled = themeUiState.isAmoledMode,
                        accentColor = themeUiState.accentColor,
                        onStyleSelected = { themeViewModel.updateThemeStyle(it) }
                    )

                    // Accent swatches (only for the Default theme)
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

                    // AMOLED + Blur grouped toggles. Both rows are conditional,
                    // so positions are derived from which ones are actually
                    // showing — a hand-maintained isFirst/isLast pair got this
                    // wrong whenever only one row was visible.
                    val showAmoled = themeUiState.isDarkTheme != false
                    val showBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val toggleCount = (if (showAmoled) 1 else 0) + (if (showBlur) 1 else 0)
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
                                        containerColor = if (themeUiState.isAmoledMode) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (themeUiState.isAmoledMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else MaterialTheme.colorScheme.onSurfaceVariant
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
                                position = ListItemPosition.from(toggleCount - 1, toggleCount)
                            )
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
                                    containerColor = if (shown) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (shown) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            position = ListItemPosition.from(index, hideableTabs.size)
                        )
                    }
                }

                // Cover Style Section
                SectionHeaderV2(
                    title = stringResource(R.string.appearance_cover_section),
                    modifier = Modifier.padding(start = Dimensions.Padding.content)
                )
                CoverStyleSelector(
                    currentStyle = themeUiState.coverStyle,
                    isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme(),
                    onStyleSelected = { themeViewModel.updateCoverStyle(it) }
                )

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
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
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

// --- Theme mode (System / Light / Dark): a segmented row, as in Mihon ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    currentMode: Boolean?,
    onModeSelected: (Boolean?) -> Unit
) {
    val options = listOf(
        null to R.string.appearance_mode_system,
        false to R.string.appearance_mode_light,
        true to R.string.appearance_mode_dark
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
    ) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = currentMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) {
                Text(stringResource(label))
            }
        }
    }
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
            Column(modifier = Modifier.width(114.dp).padding(top = Spacing.sm)) {
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
                    width = 4.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = RoundedCornerShape(17.dp)
                )
                .padding(4.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.background)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .weight(0.7f)
                        .padding(end = 4.dp)
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
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .fillMaxWidth()
                    .background(colors.primaryContainer, pill)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(8.dp)
                        .background(colors.onPrimaryContainer, pill)
                )
                Row(modifier = Modifier.size(width = 24.dp, height = 12.dp).clip(RoundedCornerShape(4.dp))) {
                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colors.tertiary))
                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colors.secondary))
                }
            }

            // Two transaction rows
            listOf(colors.secondary, colors.tertiary).forEach { dot ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).background(dot, CircleShape))
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .alpha(0.6f)
                            .height(8.dp)
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
                        .height(32.dp)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(17.dp).background(colors.primary, CircleShape))
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
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
                    .size(48.dp)
                    .border(
                        width = 3.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(5.dp)
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
                            .size(20.dp)
                            .background(scheme.primary, CircleShape)
                            .padding(2.dp)
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
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).fillMaxWidth().padding(horizontal = Spacing.md),
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
                        text = stringResource(R.string.appearance_nav_floating),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStyle == NavBarStyle.FLOATING)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.appearance_nav_floating_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentStyle == NavBarStyle.FLOATING)
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = stringResource(R.string.appearance_nav_normal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStyle == NavBarStyle.NORMAL)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.appearance_nav_normal_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentStyle == NavBarStyle.NORMAL)
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                    .clip(MaterialTheme.shapes.medium)
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
                            shape = MaterialTheme.shapes.medium
                        ) else Modifier
                    )
                    .clickable { onStyleSelected(style) },
                contentAlignment = Alignment.Center
            ) {
                if (style == CoverStyle.NONE) {
                    Text(
                        text = stringResource(R.string.appearance_cover_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.appearance_cover_selected),
                        tint = Color.White,
                        modifier = Modifier.size(Dimensions.Icon.medium)
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
    val outer = MaterialTheme.shapes.large.topStart
    val inner = MaterialTheme.shapes.extraSmall.topStart
    // No fixed height: tiles grow with their text and share the tallest one's
    // height, so wrapped names still fit at a 200% font scale.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .height(IntrinsicSize.Min)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        options.forEachIndexed { index, option ->
            val selected = currentFont == option.font
            val start = if (index == 0) outer else inner
            val end = if (index == options.lastIndex) outer else inner
            val shape = RoundedCornerShape(
                topStart = start, topEnd = end, bottomEnd = end, bottomStart = start
            )
            val nameColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onFontSelected(option.font) }
                    )
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterVertically)
            ) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = option.family,
                    color = nameColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = option.family,
                    color = if (selected) nameColor.copy(alpha = Dimensions.Alpha.subtitle)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
