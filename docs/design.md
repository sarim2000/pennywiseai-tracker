# PennyWise Design System

**Verdigris.** PennyWise looks like a statement you keep to yourself: cool green-grey ink
and paper, one accent in verdigris (the colour a copper penny turns with age, which also
means money coming in), serif figures for totals and headings, a pace line as the Home
signature, and rows on the page instead of boxes. Compose and Material 3 are the implementation; this is
the look. This document describes what is **actually implemented** — if
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
| `sectionGap` | 24dp | Between two top-level sections |
| `headerToContent` | 8dp | Section header → the content it labels |
| `listGap` | 8dp | Between sibling rows in a plain list |
| `groupedListGap` | 0dp | Between ledger rows: a hairline separates them |
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
`fabScrollClearance`, `hairline` (0.5dp, the rule between rows and around cards), `dividerThickness`.

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

`contentMaxWidth` (640dp) caps every screen's column on tablets and unfolded foldables;
the shared wrapper is `withRouteArgs` in `PennyWiseNavHost`.

### Elevation (`Dimensions.Elevation`)

The app is **flat**. Things separate with hairlines and tonal steps, not shadows. `card` is 0dp. Only genuinely
floating things get elevation: `fab` (6dp), `dialog` (8dp), `bottomBar` (3dp).

### Alpha (`Dimensions.Alpha`)

Only ever apply an alpha to a **strong** colour role (`onSurface`, `onPrimary`,
…). Dimming an already-muted role such as `onSurfaceVariant` stacks two
reductions and drops below the WCAG AA contrast floor — that was the single most
common contrast bug in this codebase. Secondary text should be
`onSurfaceVariant` at full opacity.

---

## Typography

Two voices. **Newsreader** (serif) for what you read for its value: totals, screen and
section titles. **Figtree** (sans) for labels, merchants, running text and the amounts in
rows, where a column has to compare at a glance. `AppFont.LEDGER` is the default; System
and SN Pro use one family for both and step titles up to SemiBold instead.

Newsreader ships in two optical cuts: `LedgerDisplayFamily` for hero sizes,
`LedgerFigureFamily` for 17–24sp. Don't set `Bold` on them: the fonts stop at Medium and
SemiBold, so a heavier weight gets synthesized. Instances are subset to Latin, Latin
Extended-A and currency symbols; other scripts fall back to the system font. Licences are
in `docs/fonts/`.

Big figures go through `PennyWiseText.heroFigure`: the currency is smaller, muted and a
little apart, so the digits lead.

**Which voice for money:** hero and section totals and statement lines (`heroAmount`,
`amountLarge`, `amountMedium`) are serif. Row amounts (`amountRow`) and small figures
(`amountSmall`) are tabular sans.

### Roles — pick the same style for the same job

| Role | Use |
|---|---|
| `displayMedium` | Hero figure (`heroAmount`) |
| `headlineMedium` / `Small` | Screen-level totals, dialog titles |
| `headlineSmall` | Screen title in a top bar (`screenTitle`) |
| `titleLarge` | Serif base for section headers and statement figures |
| `titleMedium` | Heading inside a sheet or dialog |
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
- **Structure:** `screenTitle`, `sectionHeader`, `overline` (date dividers), `fieldLabel`.
- **Charts:** `chartLabel` — one style for every axis tick, value label and
  legend, since Canvas text takes a `TextStyle` rather than a Material role and
  each chart used to declare its own literal.

---

## Shape

`theme/Shape.kt`: `extraSmall` 4 · `small` 6 · `medium` 10 · `large` 14 · `extraLarge` 20.

Reference `MaterialTheme.shapes.*` rather than re-typing `RoundedCornerShape(16.dp)`.
Sheets and cards use `large`; chips, trays and the buttons use `medium`/`small`. Round
is reserved for coins: merchant, category and settings glyphs are circles.

Buttons pass `shapes = pennyWiseButtonShapes()` (cut, not pill). Switches pass
`colors = pennyWiseSwitchColors()` (no outline when off).

---

## Components

### Rows first: `GroupedList` / `GroupedRow` / `GroupedColumn`

The app's one list pattern is a ledger. Rows sit on the page, flush with the section
heading above them, with a hairline above every row but the first.

```kotlin
GroupedList {
    items.forEachIndexed { i, item ->
        GroupedRow(position = ListItemPosition.from(i, items.size), onClick = { … }) {
            IconTile(icon = …, containerColor = …, contentColor = …)
            RowLabels(title = …, subtitle = …)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(Dimensions.Icon.inline))
        }
    }
}
```

`ledgerRule()` draws that hairline; `ListItemPosition.hasRule` decides who gets one.

Rows are transparent, so a row inside a `SwipeToDismissBox` must carry an opaque
`background` behind it or the swipe action shows through at rest.

Helpers:
- `IconTile` — the coin that leads a settings-style row.
- `RowLabels` — title + supporting text, weighted to fill.

### `StatementLine` — one line of a statement

Label left, serif figure right, small print under. A stack of them reads as a table:
Home's Balance and Safe to spend, Analytics' average and top category, Loans' totals. Use
it before reaching for a summary card. From `STACK_FONT_SCALE` the figure takes its own line.

### `PennyWiseCardV2` — only when something must lift off the page

`shapes.large`, `surfaceContainerLow` fill, a hairline edge, no elevation. Forms, prompts
and rule builders use it. `flat = true` drops the sheet and keeps the click handling, for
summaries and single rows.

### `ListItemCardV2` / `TransactionItem` — transaction rows

Leading coin (`Icon.list`), title + one metadata line, trailing amount in
`PennyWiseText.amountRow`. Pass `ruled` to draw the hairline.

`TransactionItemSkeleton` derives its geometry from the same tokens, so rows
don't shift or change height when data arrives.

### `BudgetBar`

A flat 4dp rule filled to the amount used, with a mark across it at today's position in
the window. Fill past the mark means spending ahead of the calendar, and `BudgetPaceNote`
says so in words. Budget and goal
colours the user didn't choose follow the accent.

### The pace line

`BalanceSparkline` under Home's hero: this month's running total against the same days last
month, ending on a dot at today. It's the screen's one flourish and the answer to "am I on
track?". It isn't drawn while amounts are hidden, because its shape gives them away. The
same goes for budget fills, percentages and Analytics' trend chart.

### `FilterChipRow`

One scrolling line that runs out to the screen edge. At a large font it wraps instead.

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

One compact, start-aligned bar with the title in the serif. A screen's create action is
a `+` in that bar, never a floating button. Tab screens dock their main action into the
bottom bar's last slot through `DockNavAction`, so nothing floats over a figure.

### Choices and progress

- **One-of-N choice rows** → `ConnectedChoiceRow` (a tray of `ToggleButton`s, the
  choice inked in). Don't use `SegmentedButton`.
- **Page / section / dialog loading** → `LoadingIndicator()`. Tiny inline
  spinners inside buttons or rows stay `CircularProgressIndicator`.
- **Activity progress** (scan, import, export, download) →
  `LinearWavyProgressIndicator`. **Data meters** (budget used, loan repaid)
  stay flat `LinearProgressIndicator` — the wave signals *work happening*.
- Motion comes from `MotionScheme.standard()` in `PennyWiseTheme`: calm, because nothing
  in a ledger should bounce past the figure it's showing. Read specs from
  `MaterialTheme.motionScheme` instead of hand-rolled `tween`s. **Effects** specs are
  also for bounded data values (progress fractions, chart sweeps). `tween` stays only inside
  `infiniteRepeatable` loops or where a duration is load-bearing.
- **Buttons** pass `shapes = pennyWiseButtonShapes()` (icon buttons:
  `IconButtonDefaults.shapes()`). Rows get the plain ripple.
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
- The app module opts into `ExperimentalMaterial3ExpressiveApi` globally
  (`app/build.gradle.kts`), so no per-file `@OptIn` is needed.

---

## Colour

Three things can colour the app. `ThemeStyle.BRANDED` is the default: the ink and paper
surfaces in `theme/Color.kt` with `AccentColor.VERDIGRIS`. Other accents swap the foreground
only and are nudged to 4.5:1 on every surface (`readableOn`, guarded by
`BrandedThemeContrastTest`). Material You dynamic colour stays an option, and AMOLED swaps
the surface ramp for true black. Always use semantic roles — never hard-code a colour.

Custom `ColorScheme` extensions in `theme/Theme.kt`: `success`, `warning`, `income`,
`expense`, `credit`, `transfer`, `investment`. Check `isAppInDarkTheme()`, not
`isSystemInDarkTheme()`: Appearance can override the system.

What colour means:

- **Spending is ink.** Expense rows are `onSurface` with a true minus (U+2212). Income is
  verdigris; credit, transfer and investment keep their colours. `error` and `warning` are
  for over budget, overdue and negative, so they still mean something. A month-on-month
  comparison is plain text: the words say which way it went.
- **Selection is inked in:** `inverseSurface` / `inverseOnSurface`, in chips and trays.
- **`secondaryContainer` is a soft wash** of the accent for prompts and badges, not selection.
- Colours belong on marks, not containers: a budget's colour lives on its bar.
- Built-in categories use the palette in `CategoryMapping`, whatever hex the database holds
  (`customColor` tells a user's choice from a seed). Display goes through
  `categoryDisplayColor`, which keeps dark colours visible on dark surfaces.

## Money formatting

On screen, `formatCurrency` always shows every minor unit (`JD40.000`, `$12.50`), so one
currency never reads at two precisions and columns line up. With `maskIfHidden = false`
(text leaving the screen) it stays compact (`JD40`). Estimates and rates use
`formatEstimate` (`About JD60 a day`): `.000` there claims a precision it doesn't have.
Net figures use `formatSigned`.

---

## App icon

`drawable/ic_launcher_foreground.xml`: a verdigris ring and a Newsreader P with no fill behind
the letter, so the one drawing is the launcher foreground, the themed monochrome icon and
the notification silhouette. `share_card_logo.webp` is a raster copy for the share card.

## Accessibility checklist

- [ ] 48dp minimum touch target on everything tappable
- [ ] Secondary text is `onSurfaceVariant` at full opacity (no stacked alpha)
- [ ] Light **and** dark theme checked
- [ ] Dynamic colour + PennyWise + AMOLED all checked
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
