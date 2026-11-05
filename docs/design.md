# PennyWise Design System

Material 3 (Expressive), with a small set of app-specific tokens and components
layered on top. This document describes what is **actually implemented** — if
the code and this file disagree, the code in `app/src/main/java/com/pennywiseai/tracker/ui/theme/`
is the source of truth and this file is the bug.

## The one rule

**Never hard-code a dp or sp value in a screen.** Every spacing, size and text
style comes from a token. If nothing fits, add a token — then the next screen
agrees with this one instead of inventing a fourth value for the same idea.

The tokens live in four files:

| File | Holds |
|---|---|
| `theme/Spacing.kt` | The raw 4dp scale + `Spacing.Layout.*` semantic gaps |
| `theme/Dimensions.kt` | Semantic sizes: padding, icons, elevation, component metrics |
| `theme/Type.kt` | The Material type scale (tuned — see below) |
| `theme/TextStyles.kt` | `PennyWiseText.*` — money, row, chart and label styles |

---

## Spacing

### The scale (`Spacing`)

| Token | Value | Use |
|---|---|---|
| `xxs` | 2dp | Hairline separation; grouped-list gutter; stacked meta lines |
| `xs` | 4dp | Tight pairing — a label above its value |
| `sm` | 8dp | Related items inside one block |
| `smd` | 12dp | Between `sm` and `md` — icon-to-label in a row, compact gaps |
| `md` | 16dp | The workhorse: card padding, screen gutters, list gaps |
| `lg` | 24dp | Between distinct blocks inside a section |
| `xl` | 32dp | Between sections |
| `xxl` / `xxxl` | 48 / 64dp | Large vertical breaks |

### Semantic gaps (`Spacing.Layout`)

Prefer these — they name the *role*, so the rhythm can be retuned in one place.

| Token | Value | Use |
|---|---|---|
| `screenHorizontal` | 16dp | Screen edge → content |
| `sectionGap` | 20dp | Between two top-level sections |
| `headerToContent` | 8dp | Section header → the content it labels |
| `listGap` | 8dp | Between sibling rows in a plain list |
| `groupedListGap` | 2dp | Between rows of a connected/grouped block |
| `nestedContent` | 12dp | Content nested inside an already-padded card |
| `scrollBottomPadding` | 24dp | Bottom breathing room in a scrolling list |

### The rhythm that matters

Hierarchy comes from **unequal** gaps. A heading must sit closer to the content
it labels than to the section above it:

```
        ↕ 16dp  (section gap: header's own top inset + the column's spacedBy)
[ Section header ]
        ↕ 8dp   (headerToContent)
[ content ]
```

`SectionHeaderV2` carries its own 8dp top inset (`topSpacing`) precisely so this
works when a screen lays sections out in a `Column` with one uniform `spacedBy`.
Pass `topSpacing = Spacing.none` for the first header on a screen, or where a
parent already supplies the gap.

---

## Dimensions

### Padding (`Dimensions.Padding`)

| Token | Value | Use |
|---|---|---|
| `content` | 16dp | Screen gutter |
| `card` | 16dp | Card interior. Matches `content` so card text lines up with unwrapped text |
| `cardCompact` | 12dp | List rows and dense tiles |
| `listRowVertical` | 12dp | Vertical padding of a two-line row |
| `dialog` | 24dp | Dialog / bottom-sheet interior (M3 spec) |
| `empty` | 32dp | Empty-state block |
| `fab` | 16dp | FAB inset from the screen edge |

### Icon sizes (`Dimensions.Icon`)

Five steps with clear jobs, plus avatar sizes. Anything outside this set reads as
a mistake next to its neighbours.

| Token | Value | Use |
|---|---|---|
| `tiny` | 12dp | Legend swatches, trend arrows glued to text |
| `small` | 16dp | Inline with body text; trailing chevron in a button |
| `inline` | 20dp | Inline with a title; row trailing affordance; dense toolbars |
| `medium` | 24dp | **The default** — app-bar actions, list leading icons |
| `large` | 32dp | Glyph inside a tonal circle; prominent standalone glyph |
| `list` / `avatar` | 40dp | Leading avatar / brand circle in a row (M3 standard) |
| `avatarLarge` | 48dp | Profile headers, settings-row circles |
| `emptyStateGlyph` / `emptyStateContainer` | 32 / 64dp | Empty-state icon + its circle |
| `extraLarge` | 96dp | Whole-screen illustrative icon |

### Component metrics (`Dimensions.Component`)

`minTouchTarget` (48dp) is the accessibility floor for anything tappable —
`GroupedRow` and `ListItemCardV2` enforce it via `defaultMinSize`. `iconButton`
(40dp) is the compact icon-button footprint; pair it with surrounding padding so
the real target still reaches 48dp.

Also here: `progressBarHeight` (8dp — one height for budget, loan and download
tracks), `legendDot` (10dp), `fab` (56dp), `fabBottomInset`,
`fabScrollClearance`, `hairline` (0.5dp), `dividerThickness`.

Tabs move to a `NavigationRail` from `navRailMinWindowWidth`. The Floating nav style is a pill of labelled tabs (`floatingNavHeight` 64dp,
`floatingNavBottomGap` 12dp above the system bar) with a sliding indicator behind
the selected icon (`floatingNavIndicatorWidth` × `floatingNavIndicatorHeight`, a
`primary` tint at `Alpha.selectedIndicator`). All labels share one size, set by
the longest. A tab's one primary action docks beside it as a 64dp `primary`
squircle (`dockedActionCornerRadius` 20dp): the screen calls `DockNavAction(...)`
when `LocalNavDock.current` is non-null and drops its own FAB, so the bar and the
action never stack over content. On Home that action is Add, as on Transactions, so
that spot always means the same thing; Sync sits in the header. Every other FAB is
a standard `FloatingActionButton` in `primary` / `onPrimary`.

The pill is only used where its labels fit; on a narrow phone or at a large font the
full-width bar takes over, with the docked action floating above it.

`contentMaxWidth` (720dp) caps every screen's column on tablets and unfolded foldables;
the shared wrapper is `withRouteArgs` in `PennyWiseNavHost`.

### Elevation (`Dimensions.Elevation`)

The app is **flat**. Containers separate via tonal surfaces
(`surfaceContainerLow` / `High`), not shadows. `card` is 0dp. Only genuinely
floating things get elevation: `fab` (6dp), `dialog` (8dp), `bottomBar` (3dp).

### Alpha (`Dimensions.Alpha`)

Only ever apply an alpha to a **strong** colour role (`onSurface`, `onPrimary`,
…). Dimming an already-muted role such as `onSurfaceVariant` stacks two
reductions and drops below the WCAG AA contrast floor — that was the single most
common contrast bug in this codebase. Secondary text should be
`onSurfaceVariant` at full opacity.

---

## Typography

`theme/Type.kt` is the Material scale with two deliberate departures, both aimed
at making hierarchy readable in a dense, number-heavy UI:

1. **Titles and headlines are `SemiBold`, not `Normal`/`Medium`.** Material's
   defaults leave a `titleMedium` heading nearly indistinguishable from the
   `bodyLarge` under it, so screens read as one flat wall. A weight step
   separates them without needing a size step.
2. **Tracking is tightened at the large end, left wide at the small end.** Big
   figures get negative tracking so digits group into one number; 11–12sp labels
   keep Material's generous tracking, which is what makes small text legible.

### Roles — pick the same style for the same job

| Role | Use |
|---|---|
| `displaySmall`+ | Hero balance on the Home balance card |
| `headlineMedium` / `Small` | Screen-level totals, dialog titles |
| `titleLarge` | Screen title in a top app bar |
| `titleMedium` | Section header, card heading |
| `titleSmall` | Grouped-list heading inside a card |
| `bodyLarge` | Primary row text (merchant, setting name) |
| `bodyMedium` | Supporting row text, descriptions, paragraph copy |
| `bodySmall` | Metadata: timestamps, counts, footnotes |
| `labelLarge` | Buttons, prominent inline actions |
| `labelMedium` / `Small` | Chips, badges, axis ticks, overlines |

### `PennyWiseText` — named styles

Derived from the theme typography (so they follow the user's font choice), for
the jobs the plain roles don't cover:

- **Money:** `heroAmount`, `amountLarge`, `amountMedium`, `amountRow`,
  `amountSmall`. All carry `tnum` (**tabular figures**) so a column of amounts
  lines up instead of shimmering as digits change width. Never ellipsize, marquee
  or clip a figure: wrap or shrink the label beside it, and give the figure
  `maxLines = 1` with `autoSize = PennyWiseText.fit(style)`.
- **Rows:** `rowTitle`, `rowSubtitle`, `metadata`.
- **Structure:** `sectionHeader`, `fieldLabel`.
- **Charts:** `chartLabel` — one style for every axis tick, value label and
  legend, since Canvas text takes a `TextStyle` rather than a Material role and
  each chart used to declare its own literal.

---

## Shape

`theme/Shape.kt`: `extraSmall` 4 · `small` 8 · `medium` 12 · `large` 16 ·
`extraLarge` 28.

Reference `MaterialTheme.shapes.*` rather than re-typing
`RoundedCornerShape(16.dp)`. Cards use `large`; grouped-list interiors use
`extraSmall`; pill shapes use `CircleShape`.

Expressive accents use `MaterialShapes` (opt-in `ExperimentalMaterial3ExpressiveApi`):
merchant avatars (`BrandIcon`, `LetterAvatar`) and the empty-state badge are
`MaterialShapes.Cookie9Sided.toShape()`. Keep these to identity/hero glyphs —
not containers or buttons.

---

## Components

### `PennyWiseCardV2` — the standard card

One card style everywhere: `shapes.large`, `surfaceContainerLow` fill, no
elevation, and — in **dark mode only** — a 0.5dp hairline so the card separates
from an AMOLED-black background where a tonal fill barely registers.

Pass `contentPadding` rather than padding the content yourself, so the ripple on
a clickable card covers the whole surface.

### Grouped lists — `GroupedList` / `GroupedRow` / `GroupedColumn`

The app's one grouped-list pattern: sibling rows share a tonal surface,
separated by a 2dp gutter, with the block's outer corners rounded and interior
corners nearly square. **The shape does the grouping — no dividers.**

```kotlin
GroupedList {
    items.forEachIndexed { i, item ->
        GroupedRow(position = ListItemPosition.from(i, items.size), onClick = { … }) {
            IconTile(icon = …, containerColor = …, contentColor = …)
            RowLabels(title = …, subtitle = …)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(Dimensions.Icon.inline))
        }
    }
}
```

`ListItemPosition.toShape()` in `ui/components/cards/ListItemCardV2.kt` is the
**single source of truth** for grouped corners. It used to exist in four places
(Home, Settings, `PreferenceSwitch`, `ListItemCardV2`) with three different
corner radii and three different gutters — which is why Settings and Appearance
never quite matched. Don't re-add a local copy.

Helpers:
- `IconTile` — the tinted circle that leads a settings-style row (fixed circle
  and glyph sizes, so a 24dp glyph in one row and 20dp in the next can't happen).
- `RowLabels` — title + supporting text, weighted to fill. Supporting text is
  `bodyMedium`/`onSurfaceVariant`, not `bodySmall`: at 12sp the second line of a
  two-line row is genuinely hard to read, and the colour role already carries
  the "secondary" signal.

### `ListItemCardV2` / `TransactionItem` — transaction rows

Leading avatar (`Icon.list`), title + one metadata line, trailing amount in
`PennyWiseText.amountRow`. The subtitle here stays `bodySmall` on purpose: it's a
joined metadata string ("9 Jan · 3:42 PM · Food · Bal ₹1,234"), not prose.

`TransactionItemSkeleton` derives its geometry from the same tokens, so rows
don't shift or change height when data arrives.

### `SectionHeaderV2`

`titleMedium`/SemiBold on **`onSurface`**, not `primary` — when every heading is
accent-coloured none of them stands out and the accent stops meaning "this is
actionable". The accent belongs to the `action` slot on the right, which *is* a
control. A fixed minimum height keeps headers with a "View All" link the same
height as headers without one.

### `PennyWiseEmptyState`

Tonal cookie-shaped icon badge → headline → one line of explanation → optional action.
Spacing is deliberately **uneven** (icon→headline gap larger than
headline→description) so the three read as one grouped unit. The description is
width-capped so centred text doesn't wrap into an awkward shape.

### `PennyWiseScaffold` / `CustomTitleTopAppBar`

All screens use one of these for consistent system-bar and app-bar handling.
App-bar titles use `titleLarge`.
Every screen uses one compact centred bar, opaque so nothing reads through the title.
Large titles pushed the money down a third of the screen and jumped position on scroll.
Home keeps its greeting in the list and fades the small bar in by scroll position.

### Choices and progress (Material 3 Expressive)

- **One-of-N choice rows** → `ConnectedChoiceRow` (a connected `ToggleButton`
  group). Don't use `SegmentedButton`; Expressive replaces it.
- **Page / section / dialog loading** → `LoadingIndicator()`. Tiny inline
  spinners inside buttons or rows stay `CircularProgressIndicator`.
- **Activity progress** (scan, import, export, download) →
  `LinearWavyProgressIndicator`. **Data meters** (budget used, loan repaid)
  stay flat `LinearProgressIndicator` — the wave signals *work happening*.
- Motion comes from `MotionScheme.expressive()` in `PennyWiseTheme`; read
  specs from `MaterialTheme.motionScheme` instead of hand-rolled `tween`s.
  **Spatial** specs (offset, size, scale, rotation) are springs that
  overshoot: `fast` for small parts, `default` for cards/panels, `slow` for
  full-screen moves. **Effects** specs (alpha, colour) don't overshoot — also
  use them for bounded data values (progress fractions, chart sweeps), where
  a bounce past 100% reads as wrong. `tween` stays only inside
  `infiniteRepeatable` loops or where a duration is load-bearing.
- **Buttons morph on press.** Every Material button passes
  `shapes = ButtonDefaults.shapes()` (icon buttons:
  `IconButtonDefaults.shapes()`) unless it sets a deliberate custom `shape`.
- **Tappable list rows morph too.** `GroupedRow` (with `onClick`) and
  `ListItemCardV2` (with `onClick`) render as M3 `SegmentedListItem`s: same
  resting corners, plus the native press/focus morph to `shapes.large`.
  Static rows stay plain surfaces.
- **Chips** use the `shapes =` overload (`FilterChipDefaults.shapes()`), which
  morphs on press and selection. Pass `colors` explicitly; the overload
  otherwise defaults to tonal colours.
- **Single-choice menus** use `SelectableDropdownMenuItem` with
  `MenuDefaults.itemShape(index, count)`, a check in `selectedLeadingIcon`
  and `MenuDefaults.selectableItemColors(containerColor = Color.Transparent)`.
  The transparent container matters: the classic `DropdownMenu` surface
  differs from the item fill, and without it every row reads as a tile.
  Action menus stay `DropdownMenuItem`. Include/exclude lists whose items are
  all ticked by default keep checkbox rows (their icons identify them).
- **Emphasized type** (`*Emphasized`, one weight step heavier, same font) is
  for hero moments only: the Home balance (`PennyWiseText.heroAmount`) and the
  expanded app-bar title.
- The app module opts into `ExperimentalMaterial3ExpressiveApi` globally
  (`app/build.gradle.kts`), so no per-file `@OptIn` is needed.

---

## Colour

Material You dynamic colour on Android 12+, a branded Rose Pine palette
(`ThemeStyle.BRANDED`), and an AMOLED override that swaps the surface ramp for
true black. Always use semantic roles — never hard-code a colour.

Custom `ColorScheme` extensions in `theme/Theme.kt`: `success`, `warning`,
`income`, `expense`, `credit`, `transfer`, `investment` — each resolves
light/dark from the theme in use. Check `isAppInDarkTheme()`, not
`isSystemInDarkTheme()`: Appearance can override the system.

What colour means:

- **Spending is ink.** Expense rows are `onSurface` with a true minus (U+2212).
  Income, credit, transfer and investment keep their colours. `error` and `warning`
  are for over budget, overdue and negative, so they still mean something.
- **Selection is one style:** `secondaryContainer` / `onSecondaryContainer`.
- **Cards are `surfaceContainerLow`.** Don't use saturated containers as decoration.
- A budget's colour lives on its progress bar, not on the card.
- Branded `primary` / `secondary` / `tertiary` are nudged to 4.5:1 on every surface
  (`readableOn`, guarded by `BrandedThemeContrastTest`). `*Container` keep the raw
  accent, so use those for fills.
- Category colours pass through `categoryDisplayColor` for display, which keeps dark
  ones visible on dark surfaces without changing what's stored.

## Money formatting

On screen, `formatCurrency` always shows every minor unit (`JD40.000`, `$12.50`), so one
currency never reads at two precisions and columns line up. With `maskIfHidden = false`
(text leaving the screen) it stays compact (`JD40`). Estimates and rates use
`formatEstimate` (`About JD60 a day`): `.000` there claims a precision it doesn't have.
Net figures use `formatSigned`.

---

## Accessibility checklist

- [ ] 48dp minimum touch target on everything tappable
- [ ] Secondary text is `onSurfaceVariant` at full opacity (no stacked alpha)
- [ ] Light **and** dark theme checked
- [ ] Dynamic colour + branded + AMOLED all checked
- [ ] Font scaling to 200% doesn't clip or overlap
- [ ] Section headers carry `semantics { heading() }` (`SectionHeaderV2` does)
- [ ] Decorative icons pass `contentDescription = null`; meaningful ones don't

## Previews

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentPreview() {
    PennyWiseTheme { /* component */ }
}
```

## Resources

- [Material 3](https://m3.material.io/) · [Material Theme Builder](https://m3.material.io/theme-builder)
- [Material Symbols](https://fonts.google.com/icons)
- [Contrast checker](https://webaim.org/resources/contrastchecker/)
