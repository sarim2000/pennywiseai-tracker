# PennyWise Design System

Material 3 (Expressive), with a small set of app-specific tokens and components
layered on top. This document describes what is **actually implemented** — if
the code and this file disagree, the code in `core/designsystem/src/main/java/com/pennywiseai/tracker/ui/theme/`
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
| `hero` | 56dp | The merchant avatar heading a detail screen |
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

The Floating nav style is a pill of icon-only tabs (`floatingNavHeight` 64dp,
`floatingNavBottomGap` 12dp above the system bar) with a sliding selected
indicator (`floatingNavIndicatorWidth` × `floatingNavIndicatorHeight`, a
`primary` tint at `Alpha.selectedIndicator`). A tab's one primary action docks
beside it as a 64dp `primary` squircle (`dockedActionCornerRadius` 20dp): the
screen calls `DockNavAction(...)` when `LocalNavDock.current` is non-null and
drops its own FAB, so the bar and the action never stack over content.

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

### Typeface — three choices, Google Sans Flex by default

Settings → Appearance → Fonts picks an `AppFont`; `PennyWiseTheme` maps it to
`getTypography(bodyFamily, headingFamily)`.

| `AppFont` | Families | Script coverage |
|---|---|---|
| `GOOGLE_SANS_FLEX` (**default**) | `GoogleSansFlexFamily` for body, label and money; `GoogleSansFlexHeadingFamily` for display, headline and title | **Latin only.** Arabic, Cyrillic, Devanagari, … fall back per glyph to the system font |
| `SYSTEM` | `FontFamily.Default` for both | Every script |
| `SN_PRO` | `SNProFontFamily` (static files) for both | Latin and Cyrillic |

- **Default and back-compat.** The `app_font` DataStore key is written only when
  the user taps a font, so an absent key means "never chose" and follows
  `AppFont.DEFAULT` (Google Sans Flex); a stored `SYSTEM` or `SN_PRO` is an
  explicit choice and is kept. The font is not part of backups.
- **One variable file.** `core/designsystem/src/main/res/font/google_sans_flex.ttf` is the upstream v5.000
  variable font instanced with fontTools to the `wght` (100–900) and `ROND`
  (0–100) axes, with `slnt` 0, `opsz` 18 and `wdth` 100 pinned: about 300 KB
  instead of 3.9 MB. Its OFL notice ships in `assets/licenses/fonts-OFL.txt`
  (SN Pro's too). A new axis costs size: add it only with a role that uses it.
- **Static instances, one per weight.** Each family declares a `Font` for every
  weight 100–900, each pinning `wght` (and `ROND`). Compose picks the closest
  *declared* weight, so without a 700 entry every `*Emphasized` style would
  render at 600, identical to its base. `GoogleSansFlexTypographyTest` guards it.
- **Roundness.** Headings use `ROND` 100: fully rounded terminals, the
  Expressive voice. Body, labels and money use `ROND` 0, which reads crisper at
  small sizes and in figures. `PennyWiseText` money styles take the body family
  even when they are sized from a heading role (`heroAmount` is
  `displaySmallEmphasized`), so amounts stay `ROND` 0 with `tnum`.
- **Never animate a font axis.** Every axis value is a separate typeface;
  animating `wght` or `ROND` re-lays out the text every frame. Animate size,
  colour or alpha instead.
- No wider (`wdth`) cut for the expanded app-bar title: long translated titles
  already wrap, and the axis would add ~120 KB.
- The picker draws each option in its own family, and its tiles grow with their
  text (no fixed height), so the names still fit at a 200% font scale.

### Roles — pick the same style for the same job

| Role | Use |
|---|---|
| `displaySmall`+ | The one hero figure on a screen (`PennyWiseText.heroAmount`) |
| `headlineLarge` | The Analytics period total (`PennyWiseText.amountHeadline`) |
| `headlineMedium` / `Small` | Screen-level totals, dialog titles |
| `titleLarge` | Screen title in a top app bar |
| `titleMedium` | Card heading |
| `titleSmall` | Section header, grouped-list heading |
| `bodyLarge` | Primary row text (merchant, setting name) |
| `bodyMedium` | Supporting row text, descriptions, paragraph copy |
| `bodySmall` | Metadata: timestamps, counts, footnotes |
| `labelLarge` | Buttons, prominent inline actions |
| `labelMedium` / `Small` | Chips, badges, axis ticks, overlines |

### `PennyWiseText` — named styles

Derived from the theme typography (so they follow the user's font choice), for
the jobs the plain roles don't cover:

- **Money:** `heroAmount`, `amountHeadline`, `amountLarge`, `amountMedium`,
  `amountRow`, `amountSmall`. All carry `tnum` (**tabular figures**) so a
  column of amounts lines up instead of shimmering as digits change width.
  An amount never takes a bare `display*`/`headline*`/`title*` role (rounded,
  proportional digits); when no money style matches the size, use
  `role.money()` (body family + `tnum` at that role's size).
- **Counting figures are tabular.** Any amount or percent that changes in place
  (a settling total, a goal ring counting up) uses a money style or, on a plain
  role, `style.tabular()` — proportional digits jitter sideways every frame.
- **Shrink a long figure; don't size it by string length.** The Analytics
  total, the transaction-detail amount and the Period Review headline pass
  `maxLines = 1` and `TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, style.fontSize)`,
  so a long amount shrinks to fit instead of wrapping mid-number or clipping
  into a smaller, wrong-looking figure.
- **Rows:** `rowTitle`, `rowSubtitle`, `metadata`.
- **Structure:** `sectionHeader`, `fieldLabel`.
- **Charts:** `chartLabel` — one style for every axis tick, value label and
  legend, since Canvas text takes a `TextStyle` rather than a Material role and
  each chart used to declare its own literal.

---

## Shape

`theme/Shape.kt`: `extraSmall` 4 · `small` 8 · `medium` 12 · `large` 16 ·
`largeIncreased` 20 · `extraLarge` 28 · `extraLargeIncreased` 32 ·
`extraExtraLarge` 48 (the last three are the M3 Expressive steps).

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

`titleSmall`/SemiBold on **`onSurface`**, not `primary` — when every heading is
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

All screens use `PennyWiseScaffold` for consistent system-bar and app-bar
handling — no raw `Scaffold`. Pass `title` for its plain top bar, or a
`CustomTitleTopAppBar` through `customTopBar` for the collapsing one. The
scaffold also takes `snackbarHost`; tab screens under the overlaid nav bar pad
it by `Dimensions.Component.bottomBarHeight`.

- `CustomTitleTopAppBar`'s expanded bar is the M3 Expressive
  `LargeFlexibleTopAppBar`; the collapsed bar is a centred single-row bar.
  Expanded heights are the `Dimensions.Component.appBarExpandedHeight*` and
  `homeAppBarExpandedHeight` tokens — no dp literals.
- Titles use `titleLarge` collapsed and `headlineMediumEmphasized` expanded
  (one of the emphasized hero moments below).
- **Subtitle** (optional, on `CustomTitleTopAppBar`): one short line of context the
  screen would otherwise lose on scroll — e.g. Transactions shows the active
  period, because its period chip hides when the list scrolls. It shows in
  both bar states and makes the expanded bar one step taller. Don't repeat the
  title or invent data for it; leave it `null` when there's nothing to add.

### Choices and progress (Material 3 Expressive)

- **One-of-N choice rows** → `ConnectedChoiceRow` (a connected `ToggleButton`
  group). Don't use `SegmentedButton`; Expressive replaces it.
- **Picking a value from a Settings row** → `SettingsChoiceSheet`, a bottom sheet
  of radio rows that opens on the current choice and fits long lists (31 days,
  every currency).
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
- **Motion language.** Generic transitions live in
  `ui/animations/PennyWiseMotion.kt` (`pwEnter`/`pwExit`, `pwFadeIn`/`pwFadeOut`,
  `pwExpand`/`pwCollapse`, `pwPush`/`pwPop`, `StaggeredEntrance`,
  `rememberEntranceDone`, `staggerDelayMs`, `rememberAnimationsEnabled`);
  use them before hand-building a fade/slide/expand. No default-spec
  `fadeIn()`, `expandVertically()`, `animate*AsState` etc. — always pass a
  `motionScheme` spec.
  1. **Chrome moves, money settles.** Position/size/shape use spatial specs
     (`fast` < 48dp parts, `default` cards/sheets/panels, `slow` full screens);
     amounts, percents, arcs, bars, alpha and colour use effects specs. A number
     never passes through a value it doesn't have. An amount's own drift and
     size settle, and a programmatic scroll to a scroll boundary, are effects
     too: an overshooting spring would jiggle the figure or hit the clamp.
     Status colour that follows a counting value (the budget pill and bar
     turning tertiary at 70%, error at 90%) reads the value on screen, not the
     target, and crossfades on `fastEffectsSpec`; text on it uses the matching
     `on*` role.
  2. **One beat per change** — no staggering digits or sibling figures.
  3. **Short travel.** Slides are a fraction of the screen (a few dp for
     content, a quarter of the bar for chrome, a quarter of the screen for a
     screen push), never full-width by default.
  4. **No bounce on data; no scale-in on icons or content** (Material reads
     scale as elevation) — fade them. The one exception is the one-shot Pro
     purchase celebration in `UpgradeSheet`, which springs its icon in on
     `fastSpatialSpec()`. Empty states and success checks don't scale.
  5. **Stagger once**, on a screen's first entrance: `StaggeredEntrance` with
     `delayMs = staggerDelayMs(index)` and `skip = rememberEntranceDone()`
     (saveable). One step and cap for every screen — don't re-type `50L * …`.
     Scrolling lists and returning to a tab don't stagger. A screen that
     arrives on a nav transition doesn't stagger either: the push is its
     entrance (`StaggeredEntrance` reads `LocalNavEntrance`).
  6. **"Remove animations" means content is just there.** Compose zeroes
     durations itself; skip `delay`-based choreography too
     (`rememberAnimationsEnabled()`, re-read on every resume — key `delay`
     effects on it so a pending delay drops when it turns off), and draw
     infinite loops (typing dots) as their static frame.
  7. **Shared elements** (the transaction icon from list to detail) move their
     bounds on `defaultSpatialSpec()` via `sharedElementIcon`, not Compose's
     default bounds spring.
- **Navigation motion.** Both nav hosts (`PennyWiseNavHost` and each tab's
  stack in `MainScreen`) speak one language from `PennyWiseMotion.kt`. A push is
  `pwPush`: the new screen rises a quarter of the screen on the default spatial
  spring as it fades in, and the screen under it fades out. A pop is `pwPop`:
  the top screen sinks a quarter and fades. A back swipe previews with
  `pwPredictivePop`: the top screen stays opaque and shrinks toward 90% under
  the finger (the platform's predictive-back look, the one sanctioned scale on
  content), so the screen it returns to shows around it with no double
  exposure; it fades only once the swipe commits, and cancelling scrubs it
  back. Its linear `tween`s are load-bearing — the finger is the clock. The
  travel is vertical, so it reads the same in RTL. A tab switch is a fast
  effects fade-through, never a slide; Home and Transaction detail cross-fade
  so the brand icon can fly between them. The bottom bar and its docked action
  are chrome above the stacks: they show and hide with their own motion and
  never slide with a screen. A screen's own `BackHandler` (bulk selection,
  App Lock, an editor sub-page) registers after the hosts, so it gets the
  gesture first; a plain `BackHandler` shows no preview, which is right for
  "undo a mode" backs.
- **Lazy list rows animate.** Keyed `items(…, key = …)` rows pass
  `Modifier.animateItem()` on the item's root composable, so rows slide on
  insert, remove and filter instead of jumping. It needs a stable, unique
  key, and must sit on the item root (e.g. the `AnimatedVisibility`, not the
  card inside it). `AnimatedCurrencyText` swaps instantly when either side is
  the privacy mask — hiding amounts isn't a value change. Chat rows pass
  `animateItem(fadeInSpec = null, fadeOutSpec = null)`: every chat row swaps
  in place (the streamed reply for its saved message, the Add card for its
  "Added …" line), so a fade would draw one over or under the other; they
  only slide. The typing/streaming reply shares one keyed slot.
- **"Receipt settled"** (`SettledCheck`): only after the user's own save is
  written — never on load, import or a failed save — the item's avatar gives
  way to a check (fade + cookie→circle morph, no scale), with
  `HapticFeedbackType.Confirm`, held `SETTLED_HOLD_MILLIS`. Transaction detail
  Save and Subscriptions mark-paid swap the avatar; the chat's Add card swaps
  its Cancel/Add buttons for the check. Its "Added …" line is written first
  (a process death in the beat must not lose the acknowledgement); the card
  stays drawn, settled, under it for the beat, then goes. With "Remove
  animations" every settle skips the beat and the check (the chat's would
  hold Send), but the haptic still lands.
- **A screen of sibling money figures enters as one group.** Period Review
  fades its loaded content in once on first visit — fade only, no travel,
  saveable flag — instead of staggering its cards (rules 1, 2 and 5); later
  periods, and coming back, just appear.
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
  for hero moments only: the Home hero amount — this month's spending in the
  expanded `BalanceCard` — the transaction-detail amount, the Period Review
  headline (each currency card's spend; all three are
  `PennyWiseText.heroAmount`), and the expanded app-bar title. Counting
  figures — amounts and percents — are tabular (see Typography).
- The app module opts into `ExperimentalMaterial3ExpressiveApi` globally
  (`app/build.gradle.kts`), so no per-file `@OptIn` is needed.

---

## Colour

Material You dynamic colour on Android 12+, a branded Rose Pine palette
(`ThemeStyle.BRANDED`), and an AMOLED override that swaps the surface ramp for
true black. Always use semantic roles — never hard-code a colour.

Custom `ColorScheme` extensions in `theme/Theme.kt`: `success`, `warning`,
`income`, `expense`, `credit`, `transfer`, `investment`, `loan` — each resolves
light/dark from the app's scheme. Always use these tokens, never pick a
`*_dark` / `*_light` constant yourself. When something else must change with
the theme, check `isAppInDarkTheme()`, not `isSystemInDarkTheme()`: Appearance
can force Light or Dark regardless of the system.

### What colour means

Most transactions are spending, so the common case stays quiet and red keeps
its meaning:

- **Spending is ink** (`onSurface`, or the `on*` role of the container it sits
  on). The sign ("-"), a label or an icon tells it apart, never colour alone.
- **Income** uses `income`, with a "+".
- **Credit, transfer, investment, loan** keep their own tokens.
- **Red (`expense` / `error`) or `warning` only for a problem**: over budget,
  overdue, a negative net or balance, low balance, limit exceeded, errors.
- **Charts keep series colours** (the expense line, legend dots): series need
  to be told apart.
- **Identity colours** (categories, merchants) stay.

**The accent (`primary`) marks what you can act on or what is in a state:**
buttons and text actions, the FAB / docked nav action, the selected nav tab,
chip, toggle or segment, switches and other M3 controls, focus and cursor,
activity progress, and the one hero CTA on a screen. Structure stays neutral:
static headings and labels use `onSurface`, decorative icons (info rows, card
and dialog headers, empty states, banners) and secondary figures use
`onSurfaceVariant`, at full opacity. Semantic roles (income, expense, error…),
chart series and category/merchant identity colours keep their colour. If a
tinted thing can't be tapped and isn't selected or in progress, it shouldn't be
`primary`.

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
