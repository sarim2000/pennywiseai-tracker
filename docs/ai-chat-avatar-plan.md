# PennyWise Mascot Plan

**Status:** In progress (`feat/mascot`) · **Last updated:** 2026-11-15 · **Preview:** [`mascot-preview.html`](mascot-preview.html) (open in a browser: tap, drag, fire transactions, switch theme seeds and modes)

**Goal:** An animated mascot drawn from the PennyWise owl. It becomes the **AI Chat tab icon**. It briefly turns into a finance shape when the user records money (a coin for income, a receipt for an expense), then back into the owl. It also gives the chat and the empty screens a face. Every colour comes from the app theme.

**Reference:** [open-bot-avatar](https://github.com/DominikScholz/open-bot-avatar) (MIT). We take its idea and its timing numbers, not its code: one character = one body `Path` + an eye spec, and one engine animates all of them (blink, gaze, mood blending). It is TypeScript on a browser canvas; we draw with Compose.

**Principles**
- The eyes are the character; the silhouette is the topic.
- The mascot is decoration that explains. It is never a button of its own and never the only carrier of a fact: every number stays in text.
- Quiet by default. No frame loop while idle, one character per surface, reactions are short and never queue.

Agreed with Codex (`gpt-6.1-sol`, high) over two rounds. Its objections that we adopted are marked **(Codex)**.

---

## Decisions

### Shapes

| Shape | Means | Used in | Notes |
|---|---|---|---|
| **Owl** | The assistant | Chat tab, chat hero, thinking indicator, model screen | The default form |
| **Coin** | Income | Tab reaction, chat `month_summary` | |
| **Receipt** | Expense and credit-card purchase | Tab reaction, chat add/update/delete card | `CREDIT` is a card *purchase*, not money in, so no separate card shape **(Codex)** |
| **Wallet** | Transfer, accounts | Tab reaction, Accounts empty state | Arrow silhouettes with eyes read poorly **(Codex)** |
| **Jar** | Investment, goals | Tab reaction, Goals empty state | Fill level = goal progress. Replaces the planned sprout **(Codex)** |
| **SMS bubble** | SMS scan | Tab, while a user-started scan runs in the foreground | Eyes sweep left and right |
| **Pie** | Categories | Chat `spending_by_category` / `TOP_CATEGORIES` | The cut-out slice = top category share |
| **Bars** | Month comparison | Chat `compare_spending` / `COMPARE_MONTH` | Eyes look up when spending rose and down when it fell, with no judgement |
| **Bell** | Bills, subscriptions | Subscriptions / Recurring empty state | Swings if something is due within 3 days |
| **Gauge** | Budget | Budgets empty state | Needle = share of the budget used |

**Dropped:** heart / love, card, transfer arrows, sprout, loans, split. Add one only if users ask. Each shape costs about 10 lines.

### Moods

| Mood | Look | When |
|---|---|---|
| `Calm` | Blink every ~4.2s (0.19s, 20% doubles). Gaze wander only at hero size | Default |
| `Thinking` | ~3° tilt, eyes up and toward line-start, squint, slower blink | `isLoading && currentResponse.isEmpty()` |
| `Happy` | `^` eyes and one short hop: translation only, **no squash or stretch** (`design.md` forbids scaling) | Income recorded; a chat Add saved (`adds` increment); a goal reached, **on the goal screen only** |
| `Worried` | Slanted brows, eyes down, one small shake | **Data only**, on contextual screens: budget ≥ 90%. Never for errors, never on the tab **(Codex)** |
| `Asleep` | Eyes closed, static | Model `NOT_DOWNLOADED` / `DOWNLOADING` / `ERROR` |

- Mood changes blend from the pose currently on screen (420ms smoothstep in the reference; we use `MaterialTheme.motionScheme` effects specs).
- **A body change is a morph, not a crossfade (2026-11-17).** One silhouette bends from the old outline into the new one (`MascotMorph.kt`: each outline as a polar profile, eased ray by ray) on a spatial spring: `defaultSpatialSpec` for the 24/32dp glyph, `slowSpatialSpec` for the 96dp hero. It is shape, not scale: nothing grows. The glyph keeps its tint whole the whole way, where a crossfade thinned it into two half-faded bodies. The eyes slide to the new shape's place and narrow to dots through the middle. At 96dp the old face fades out early and the new one in late, inside the body, and the body colour follows.
- **Arrivals (`MascotArrival.kt`).** Once a morph lands, the shape plays one short move: the coin's shine and sparkles (income), the receipt's check stamp, which stays while it's a receipt (expense), the wallet's lean and glance from line start to line end (transfer), a coin dropping into the jar (investment), the bell's ring. Only on a change, never when a screen first shows a shape; none with animations off.

### Chat tab
- `BottomNavItem.icon: ImageVector` stays as it is, because Appearance settings uses it too. The two icon slots in `PennyWiseBottomNavigation.kt` branch on `BottomNavItem.Chat` and draw `Mascot`. No registry, no navigation redesign **(Codex)**.
- **Sizes:**
  - NORMAL bar: `Dimensions.Icon.medium` (24dp), or `large` (32dp) when both the pill and the labels are hidden.
  - FLOATING bar: 24dp, with the existing slot, indicator, tooltip and ripple.
- **Colour (open question 1):** the user wants the tab coloured like the reference app, so it stands out. Codex wants the normal selection tints (`onSurfaceVariant` when unselected) so it does not compete with the selected tab.
  - **Default plan:** the body uses the theme accent in both states, and the pill marks selection.
  - **When pill and labels are both hidden:** fall back to the muted tint when unselected, because colour would then be the only selection cue.
  - The preview has a "Muted tab" toggle for comparing the two.
- **At 24/32dp:** a two-tone silhouette only, with the eyes cut in the colour behind it. No mask, iris, coin or receipt lines **(Codex)**. The one mark is an arrival's: the receipt's check, cut out like the eyes.
- **Coming back to the bar:** the bar returns from under a pushed screen (Add) already holding the save's reaction. The owl shows for a beat (350ms) and then turns, so the change is seen rather than already done.
- **Behaviour:** no poke, drag, haptic or separate focus target on the tab. The click goes to the parent. The accessible name stays the localized "Chat", and the drawing is decorative.
- **Hidden tab:** if the user hid the Chat tab, the mascot runs no animation and queues nothing.
- **RTL:** the artwork is symmetric and is not mirrored. Directional gaze is mirrored.

### Transaction reactions on the tab

| `TransactionType` | Shape | Mood |
|---|---|---|
| `INCOME` | Coin | Happy (brief) |
| `EXPENSE` | Receipt | Calm |
| `CREDIT` | Receipt | Calm |
| `TRANSFER` | Wallet | Calm |
| `INVESTMENT` | Jar | Calm |

- **Duration:** hold ~3s, then return to the owl.
- **Bursts:** a burst coalesces into the reaction already showing. It never queues a sequence and never extends the hold **(Codex)**.
- **Foreground only:** react only to saves made while the app is in the foreground (`AppForegroundState`). Saves that happened in the background are never replayed when the user returns.
- **SMS scans:** show the SMS bubble only during a foreground scan the user started, with the existing progress UI kept. At most one neutral beat afterwards, and only if new rows were saved. No reaction to rebuilds.
- **Not on the tab:** budget, bill-due and subscription triggers stay on their own screens.

### Event source **(Codex)**
- **No "newest Room row" observer and no universal repository hook.** Many paths insert rows (imports, backups, recurring, autopay, loans), and most of them must not react.
- **One app-scoped `TransactionEvents`:** a `MutableSharedFlow(replay = 0)` carrying `type`, `count` and `origin` only. No amounts, no merchants, no mascot enums. It is not persisted, so the backup format is untouched (constraint 3).
- **Emit only after a confirmed insert.** The DAO's conflict-ignore returns `-1`, so check the result.

| Path | Emit? | Where |
|---|---|---|
| Live SMS / bank notification / taught message | Yes | Success branch of `SmsTransactionProcessor.saveParsedTransaction` |
| Manual add | Yes | `AddTransactionUseCase.execute` |
| Manual transfer | Yes | `AddTransactionUseCase.executeTransfer`, which needs to expose its insert result |
| AI chat Add | Yes, **once** | `ChatViewModel` after the outer `executeAll` batch commits, not from the inner `execute` calls |
| Bulk SMS scan | No per-row event | Observe the scan's `WorkInfo` (saved count vs rebuild) for the bubble |
| CSV / statement import, backup restore / merge | No | — |
| Recurring materialization, autopay, subscription mark-paid, loan repayment, balance adjustment | No (for now) | Scheduled or bookkeeping |

### Colour roles (all from `MaterialTheme.colorScheme`, so dynamic colour, light, dark and AMOLED all follow)

| Part | 24/32dp | 96dp |
|---|---|---|
| Owl body | Tab tint (see above) | `primary` |
| Owl face mask | — | `primaryContainer`, or `surfaceContainerHigh` if it is too close to `primary` in the branded scheme **(Codex)** |
| Owl eye discs / pupils | Cut-outs in the backing colour | Light / dark neutral (`surface` / `onSurface`, swapped in dark) |
| Owl iris, coin | — | `tertiary` |
| Object shapes | Tab tint | `tertiaryContainer` body, `onTertiaryContainer` eyes and details (open question 2) |
| Badge | — | `inverseSurface` / `inverseOnSurface` |

Contrast is checked on dynamic, branded, AMOLED and blurred-bar backgrounds before each milestone ships.

### Where it never appears
- Beside every chat message or on old messages.
- On confirmation-card buttons, or in place of any number.
- In notifications or widgets.
- On populated data screens (Home, Analytics), except in their empty states.

**The Chat tab is the one persistent exception, and it is quiet:** a delay-driven blink plus short reactions.

### Badge
- **Amounts:** only `CurrencyFormatter.formatCurrency(amount, currencyCode)` (constraint 1), so they mask to `••••` when the user hides amounts.
- **Mixed currencies:** never sum across currencies (constraint 2). A mixed-currency month shows a count instead of an amount.
- **Strings:** from `strings.xml`.
- **Motion:** `pwFadeIn`, no scale.

---

## Architecture

```
ui/components/mascot/
  Mascot.kt          // @Composable Mascot(shape, mood, size, modifier, value, tint, interactive)
  MascotShapes.kt    // enum MascotShape + one `when` with Path + EyeSpec per shape
  MascotPose.kt      // pure: mascotPose(mood, from, tMillis, seed, look) -> MascotPose
  ChatTabReactions.kt // the tab's type -> shape table, hold and coalescing
data/events/TransactionEvents.kt   // app-scoped SharedFlow, replay = 0
```

- **Drawing:** `drawWithCache` caches the paths for each size. Time is read only in the draw phase, so no recomposition happens per frame.
- **Two clocks:**
  - Every mascot but a hero: a coroutine `delay(random 4–8s)` followed by a 190ms blink animation. Nothing runs between blinks; frames run only in short bursts while a poke, a drag (and the ~0.3s settle after it), a mood or shape change or a one-shot mood plays out, and stop once the pose settles. Drawing detail goes by size; motion doesn't, so a 96dp empty state is as quiet as the tab.
  - Hero (the empty chat's owl, the playground stage, opted in with `alive`): `withFrameMillis` while it is visible. A `Thinking` mascot also runs one for as long as it thinks.
- **Gating:** every clock runs only when `rememberAnimationsEnabled()`, the lifecycle is `STARTED`, and the mascot is in composition. Otherwise a deliberate static pose is drawn.
- **Licence:** a header comment in `MascotPose.kt` credits open-bot-avatar (MIT) for the timing constants.

---

## Milestones

Each milestone is one PR (M5 may be one PR per screen). Every milestone also needs the checks under [Definition of Done](#definition-of-done-every-milestone).

### M1 — Mascot engine + Chat tab owl
The smallest visible win: the Chat tab becomes the owl that blinks.

- [x] Branch `feat/mascot` from `main`
- [x] `MascotPose.kt`:
  - [x] Blink slots (4.2s / 0.19s / 20% doubles)
  - [x] Gaze wander, clamped inside the eye
  - [x] Mood blend from the on-screen pose
  - [x] Reduced-motion static pose
- [x] `MascotShapes.kt`: `Owl` only for now (body path + eye spec), with the 24/32dp two-tone variant
- [x] `Mascot.kt`:
  - [x] `drawWithCache` drawing
  - [x] Delay-driven blink clock
  - [x] Lifecycle, visibility and animations-enabled gating
  - [x] `clearAndSetSemantics {}` on the drawing
- [x] `PennyWiseBottomNavigation.kt`: branch on `BottomNavItem.Chat` in the NORMAL and FLOATING icon slots
- [x] Tab tint rules:
  - [x] Accent in both states
  - [x] Muted when unselected and both pill and labels are hidden
  - [x] Eye cut-outs in the backing colour
- [ ] Keep the Chat accessible name and tab semantics in both bars (verify with TalkBack)
- [x] Hidden Chat tab: nothing composed, nothing running
- [x] `MascotPoseTest`:
  - [x] Blink closes and reopens on schedule
  - [x] Gaze stays inside the eye
  - [x] A blend starts from the pose on screen
  - [x] Reduced motion gives a static pose
- [ ] Battery check: no frames between blinks (Layout Inspector / `dumpsys gfxinfo`)

### M2 — Transaction reactions on the tab
- [x] `data/events/TransactionEvents.kt` (not `core`: `core` may not import `TransactionType`): `MutableSharedFlow(replay = 0)`, Hilt `@Singleton`, payload `type`, `count`, `origin`. It drops saves made while the app is in the background (`AppForegroundState`) at the source
- [x] Emit from:
  - [x] Success branch of `SmsTransactionProcessor.saveParsedTransaction`
  - [x] `AddTransactionUseCase.execute`
  - [x] `executeTransfer` (expose its insert result)
  - [x] `ChatViewModel` once after the outer `executeAll` (a card of mixed types reacts as a receipt)
- [x] Emit nothing from imports, backup restore / merge, recurring, autopay or loans (a test asserts no event for an import)
- [x] `MascotShapes`: add `Coin`, `Receipt`, `Wallet`, `Jar`, `Bubble`
- [x] Tab collector:
  - [x] Foreground only (`AppForegroundState`)
  - [x] Maps the type to shape and mood (table above)
  - [x] ~3s hold, then the owl
  - [x] Bursts coalesce, nothing queues, the hold never extends
  - [x] Cleared on background or when Chat is hidden
- [x] Body swap is a morph (was an effects crossfade until 2026-11-17). `Happy` hop is translation only.
- [x] SMS bubble while a user-started foreground scan runs (`WorkInfo`). One neutral beat afterwards if new rows were saved.
- [x] Tests:
  - [x] Type → shape mapping
  - [x] Coalescing (three events → one reaction)
  - [x] An event while backgrounded is not replayed
  - [x] `insert` returning `-1` emits nothing
- [ ] On device: each reaction on both bar styles, light and dark, with "Remove animations"; no frames between reactions once the owl is back

### M3 — Chat surfaces
- [x] Empty-chat hero (96dp owl, `Calm`), above the welcome message:
  - [x] Tap = poke (quick blink + damped ±6° wobble, no haptic)
  - [x] Drag = look around (tanh saturation, back to centre ~0.3s after release)
  - [x] Eyes look at the input while it has text
- [x] Hide the hero as soon as the first request starts
- [x] `TypingIndicator`: the dots become a 32dp `Thinking` owl. Keep the `THINKING_LINES` text and the `REPLY_KEY` slot.
- [x] Model screen: `Asleep` owl replaces `CloudDownload`. It wakes once on `LOADING` (the `LOADING` screen's owl takes the `LoadingIndicator`'s place and thinks).
- [x] `Happy` on the hero when `adds` increments after entry, never on navigation, rotation or restored history (same rule as `addsAtEntry`). Today an Add always follows a request, which has already hidden the hero, so this is wired but cannot show yet; M4's reply mascot is where a saved Add shows.
- [ ] **Measure:** tokens/s and time-to-first-token with and without the mascot, on the M0 phone from [`ai-chat-improvement-plan.md`](ai-chat-improvement-plan.md). If there is a measurable difference, the Thinking owl is static during generation.

### M4 — Shapes for chat replies
- [x] Transient `replyTopic` (shape + mood + value) in `ChatUiState`, set from `handleToolCall` and `QuickAction`. Not stored in Room or backup.
  - `LlmRepository.lastReply` (a `StateFlow<ReplySubject?>`, data-layer, no mascot enums) names the saved line and the tool behind it, set just before the line is saved so the screen knows its subject when it shows. `ReplyTopic.kt` maps it to shape, mood, value and whether it saved. Each `QuickAction` names the tool that answers the same question.
- [x] `MascotShapes`: add `Pie` (slice = top category share) and `Bars` (gaze follows the direction of change)
  - `spending_by_category` slices by the asked category's share of the month; `TOP_CATEGORIES` by the top one's share of its own currency. Drawn between 5% and 50%; the eyes move onto the rest of the pie.
  - Bars look up only when spending rose in every currency, down only when it fell in every one, ahead otherwise.
- [x] 32dp mascot at the start of the **latest** assistant reply only. Space is reserved for the whole reply so the text does not reflow. It is removed when a new request starts.
  - The streamed reply keeps the slot empty; the saved one lands in place and its mascot fades in. When a new request starts, the slot closes toward the start on a spatial spec.
  - A saved Add's line is `Happy` for the same beat as the hero (`addsAtEntry` rule), then calm.
- [x] Receipt badge "Saved" on a confirmed Add, the only badge a reply wears. It sits by the reply's time, fades in, and is silent to TalkBack.
  - No coin badge with the month total or a currency count: the reply's text already states the figure, so on a phone it only repeated it beside the time.
- [x] Test: every tool / `QuickAction` maps to a shape

### M5 — Empty states outside chat
- [x] `PennyWiseEmptyState`: optional `mascot: (@Composable () -> Unit)? = null`. Screens that don't pass it are unchanged.
  - The mascot takes the icon badge's place and fades in on the same effects spec, with no morph or scale. Every empty-state mascot is 96dp and interactive: a tap pokes it, a drag has it watch the finger. The drag now only watches the pointer and consumes nothing, so a list or scrolling column under a mascot still scrolls (the chat hero gets the same).
- [x] Start with **Goals** (jar, fill = progress) to judge the feel, then:
  - The Goals empty card on the Budgets screen shows an empty jar beside its text: with no goal there is no progress, so the fill stays at zero. With no budgets either, the card keeps its icon so the budget card's gauge is the screen's one mascot.
  - [x] Subscriptions / Recurring (bell)
  - [x] Accounts (wallet)
  - [x] Budgets (gauge): the needle rests at zero, nothing is budgeted yet
  - [x] Unrecognized SMS (bubble)
- [x] `MascotShapes`: add `Bell` and `Gauge`
  - The bell swings from its handle when poked (±14°, rung out in 1.6s). The gauge's needle follows `value` (share used, 0..1) and pins at the end of the dial when over. A jar's `value` fills it from the bottom.
- [ ] Data moods: bell swings if something is due ≤ 3 days; gauge `Worried` at ≥ 90%; jar `Happy` once on the goal screen when a goal reaches 100%
  - Not wired: each needs data that an empty state by definition doesn't have. An empty Subscriptions or Recurring screen has nothing due, an empty Budgets screen has no budget to be 90% through, and the Goals empty card has no goal to reach. The goal detail screen is populated, where the mascot never appears. The shapes take the figures (`value`, a poke rings the bell), so a later surface that has the data can pass them.
- [ ] Badges (percent / days / count, plus amounts only via `CurrencyFormatter`)
  - Not added, for the same reason: an empty state has no figure to badge, and a "0" badge would only repeat the headline.

### M6 — Developer playground
- [x] A screen behind Developer mode showing every shape × mood with poke and drag, for on-device QA screenshots in both themes. Skip it if the HTML preview plus emulator checks prove enough.
  - Settings → Developer → **Mascot playground**, a row that shows only while Developer mode is on. A fixed stage on top shows the pose at the sizes the app draws: the 96dp hero to poke and drag, the chat's 32dp glyph and the tab's 24dp glyph in its pill. A value slider shows for the shapes that draw a figure (pie slice, jar fill, gauge needle up to 125% to see it pin, bars -100..100%). Below it, every shape × mood at 40dp; a tap puts it on the stage, so a mood change blends and a shape change morphs as it would in the app.
  - Reaction buttons, one per `TransactionType`, announce a save through `TransactionEvents` as a manual add does (nothing is written). The bottom bar stays up on this screen, so each plays on the real Chat tab, and a second tap inside a reaction shows the coalescing.
- [ ] On device: every cell in light and dark, with "Remove animations", and each reaction on both bar styles

---

## Definition of Done (every milestone)
- [ ] `./init.sh app` passes
- [ ] On-device check with the **emulator-verify** skill:
  - [ ] Light, dark, AMOLED and dynamic colour
  - [ ] Arabic (RTL)
  - [ ] "Remove animations"
  - [ ] Hidden amounts (badges show `••••`)
  - [ ] Both nav bar styles, with pill / labels on and off
- [ ] TalkBack: the Chat tab reads "Chat, tab, selected/not selected" and the mascot itself is silent
- [ ] Screenshots of the result in the PR

## Risks

| Risk | Mitigation |
|---|---|
| Battery: the tab is always on screen | No frame clock between blinks; delay-driven blink; gated on lifecycle and visibility |
| Slows on-device generation | Draw-phase reads only; measure in M3; static fallback |
| 24dp legibility and tab recognition | Two-tone silhouette; same footprint, eyes and label in every shape; check on a real phone |
| Reacting to rows the user didn't just make | Explicit emitters only; foreground only; no replay; import and restore paths excluded and tested |
| Theme roles collide (`primary` ≈ `primaryContainer`) | Fallback mask role; contrast check across schemes |
| Noise | One character per surface; coalesced reactions; none on data screens |

## Decisions taken (2026-11-15)
1. **Tab colour:** theme accent in both states; muted when unselected only when both pill and labels are hidden.
2. **Shape colour role:** `tertiaryContainer` / `onTertiaryContainer` at 96dp; tab tint at 24/32dp.
3. **`Happy` hop:** kept (translation only).
4. **Platforms:** Android only.
