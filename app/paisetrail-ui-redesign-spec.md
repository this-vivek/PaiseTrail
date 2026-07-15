# PaiseTrail UI Redesign Spec — "Premium Fintech" v2

**Audience:** Claude Code (Sonnet) working inside the PaiseTrail repo.
**Scope:** UI layer only. Do not change ViewModels, DAOs, capture/enrich logic, or navigation arguments. All existing unit tests must keep passing (`./gradlew testDebugUnitTest`). Screens read from the same state objects they read from today.

---

## 0. Design intent (read first)

PaiseTrail v1 is a flat minimalist "ledger". v2 is a **premium fintech instrument**: deep layered dark surfaces, one signature living gradient, frosted-glass chrome, jewel-toned data color, and confident spring-based motion. Think "private banking app built by a design studio", not "Material 3 template" and not "crypto app with neon on black".

**The one signature element — the Aurora.** The Dashboard hero is a large rounded card whose background is an animated multi-stop radial/linear gradient mesh ("aurora") whose *hue temperature is driven by budget health*: calm teal/indigo when month-to-date spend is comfortably under total budget, shifting through violet to warm amber as spend approaches budget, and to a deep ember red when over. The aurora drifts slowly (60–90s loop, two gradient centers orbiting via `rememberInfiniteTransition`). This same hue token (`auroraTint`) subtly tints the bottom-nav glass and the month forecast chip, so the whole app quietly "knows" how the month is going. Everything else in the app stays disciplined so the aurora is the memorable thing.

**Hard rules:**
- No pure black `#000000` and no pure white text. No acid-green accents.
- Gradients appear ONLY in: the Aurora hero, chart fills (as low-alpha fades to transparent), the budget progress bars, and the trip card image scrims. Never on buttons, list rows, or text.
- Glass (blur) appears ONLY in: bottom navigation bar, top app bars when content scrolls under them, bottom sheets, and the map overlay panels. Never for list rows or cards.
- Debit amounts render in `ink`, never in red — spending is not an error (keep this v1 rule).
- Every color used must come from `PaisaColors` v2 or `CategoryPalette` v2 below. Delete a color rather than inventing one inline.

---

## 1. Dependencies to add (Phase 0)

Add to `gradle/libs.versions.toml` + `app/build.gradle.kts`:

```toml
haze = "1.2.2"                     # dev.chrisbanes.haze — real backdrop blur ("glass")
haze-materials = "1.2.2"           # HazeMaterials presets
androidx-compose-animation = ""    # already in BOM — needs SharedTransitionLayout (Compose 1.7+, BOM 2024.12.01 has it)
lottie-compose = "6.6.0"           # only for empty states / success ticks (optional, Phase 4)
```

```toml
[libraries]
haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
haze-materials = { group = "dev.chrisbanes.haze", name = "haze-materials", version.ref = "haze" }
```

**Glass implementation note:** use Haze (`hazeSource` on the scrolling content, `hazeEffect` on the chrome). On API < 31 Haze automatically falls back to a translucent scrim — acceptable; do not hand-roll `RenderEffect`. minSdk stays 26.

**Fonts:** bundle three variable TTFs in `res/font/` (download from Google Fonts):
- `space_grotesk` (display + all money amounts — geometric, distinctive numerals; use tabular figures via `fontFeatureSettings = "tnum"`)
- `inter` (body/UI)
- Keep system font out of visible UI.

Enable shared elements: opt-in annotation `@OptIn(ExperimentalSharedTransitionApi::class)` where used.

---

## 2. Design tokens v2 (rewrite `ui/theme/`)

### 2.1 Color — `Color.kt`

Replace `PaisaColors` with this expanded token set. Dark is primary; light is derived and must be kept working, but design decisions are dark-first.

```kotlin
data class PaisaColors(
    // Layered depth (replaces flat bg/surface pair)
    val bg: Color,          // deepest layer, behind everything
    val surface1: Color,    // cards
    val surface2: Color,    // nested content on cards, chips
    val surfaceGlass: Color,// tint applied over blur (with alpha)
    // Ink
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,    // tertiary labels, disabled
    val hairline: Color,
    // Brand
    val accent: Color,          // primary interactive (indigo)
    val accentAlt: Color,       // secondary interactive endpoint for the aurora (aqua)
    val positive: Color,        // credits/refunds, under-budget
    val negative: Color,        // errors, over-budget ONLY (never debit amounts)
    val warning: Color,         // near-budget amber
    // Aurora endpoints (hero gradient interpolates across these by budget health)
    val auroraCalm: Color, val auroraMid: Color, val auroraHot: Color,
    val isDark: Boolean,
)

val DarkPaisaColors = PaisaColors(
    bg = Color(0xFF0A0C12),          // ink-navy, not grey-black
    surface1 = Color(0xFF12151E),
    surface2 = Color(0xFF1A1E2A),
    surfaceGlass = Color(0x8C12151E), // 55% alpha over blur
    ink = Color(0xFFEDEEF2),
    inkMuted = Color(0xFF9AA0B0),
    inkFaint = Color(0xFF5D6373),
    hairline = Color(0xFF232838),
    accent = Color(0xFF6E7BFF),      // indigo
    accentAlt = Color(0xFF54D6C8),   // aqua
    positive = Color(0xFF4CC38A),
    negative = Color(0xFFE5645A),
    warning = Color(0xFFE8A33D),
    auroraCalm = Color(0xFF2B4C8C),  // deep blue → teal glow
    auroraMid = Color(0xFF6E4FA3),   // violet
    auroraHot = Color(0xFF9C4A3C),   // ember
    isDark = true,
)
// LightPaisaColors: bg 0xFFF6F6FA, surface1 0xFFFFFFFF, surface2 0xFFEFF0F6,
// ink 0xFF171A22, accent 0xFF4A58E8, same aurora hues at higher lightness/lower saturation.
```

### 2.2 Category palette v2 — jewel tones

Replace the desaturated eight with richer jewel tones at matched lightness (still 8, same seeding order Food→Entertainment, Health/P2P/Uncategorized reuse grey/slate):

```
amber   0xFFE0A458   (Food)        coral  0xFFE07A5F  (Travel)
azure   0xFF5B8DEF   (Fuel)        gold   0xFFCDB04E  (Stay)
emerald 0xFF57B894   (Shopping)    lilac  0xFFA78BDB  (Groceries)
cyan    0xFF4FBBD1   (Bills)       rose   0xFFD46A9B  (Entertainment)
grey    0xFF9AA0B0
```

Every category color usage gets a standard treatment: dot/pin/donut slice at 100%, backgrounds at 14% alpha (`color.copy(alpha = 0.14f)`), never as text color for amounts.

### 2.3 Typography — `Type.kt`

```
display   Space Grotesk Medium   34/40, -0.5sp tracking   (hero amount uses 44/48)
title     Space Grotesk Medium   22/28
label     Inter SemiBold         13/16, +0.6sp tracking, ALL-CAPS eyebrows
body      Inter Regular          15/22
bodyBold  Inter SemiBold         15/22
caption   Inter Regular          12/16, inkMuted
amount    Space Grotesk Medium, tabular figures ("tnum"), sizes L 28 / M 17 / S 14
```

`AmountText.kt` must switch to the `amount` styles and add a paise treatment: rupee symbol and paise at 0.62× size, `inkMuted`, baseline-aligned (e.g. **₹1,842**.50 with ₹ and .50 smaller/muted).

### 2.4 Shape & elevation — `Shape.kt`

```
cardRadius 24.dp   sheetRadius 28.dp (top corners)   chipRadius 12.dp
buttonRadius 16.dp pillRadius 999.dp
```

Depth is expressed by **surface layering + a 1px inner top-edge highlight** (`hairline` at 40% alpha drawn as a top border on cards), NOT by Material elevation shadows. Allowed shadow: a single soft ambient shadow on the Aurora hero and on FABs (`shadowElevation = 8.dp`, spot color = current `auroraTint` at 25% alpha).

### 2.5 Motion — new file `ui/theme/Motion.kt`

```kotlin
object PaisaMotion {
    val springDefault = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val springBouncy  = spring<Float>(dampingRatio = 0.65f, stiffness = 300f) // sheets, pills
    val springGentle  = spring<Float>(dampingRatio = 1f, stiffness = 200f)    // charts
    const val chartDrawMs = 700      // chart draw-in duration
    const val staggerMs = 40         // per-item list stagger cap at 8 items
}
```

Motion rules:
- **Screen transitions (NavGraph):** forward = slide-in-from-right 24dp + fade over springDefault; back = mirrored. Bottom-nav tab switches = fade-through (fade out 90ms, fade in 210ms), no slide.
- **Shared elements:** wrap the NavHost in `SharedTransitionLayout`. Shared bounds: (a) TxnRow → TransactionDetail: the merchant name + amount + category dot travel; (b) Trip card → TripDetail: the whole card grows into the detail header; (c) Dashboard donut → Insights donut (same key `"donut"`).
- **Numbers tick:** hero amount and stat blocks use `AnimatedContent` with a vertical slide per-digit or a simple `animateIntAsState` count-up on first composition (400ms, gentle spring). Re-use one `TickerAmount` composable.
- **Charts draw in:** line/area charts animate a `phase` 0→1 clipping the path (`PathMeasure` or clip rect); donut sweeps from 0→360 with per-slice stagger; bars grow from baseline with 30ms stagger.
- **Lists stagger:** first 8 visible items fade+rise 12dp with 40ms stagger on screen entry only (not on scroll).
- **Press feedback:** cards/rows scale to 0.98 with springDefault (`Modifier.pointerInput` + `graphicsLayer`), plus `HapticFeedbackType.TextHandleMove` on long-press actions.
- Respect reduced motion: if `LocalContext` animator duration scale is 0 (`Settings.Global.ANIMATOR_DURATION_SCALE`), skip staggers/count-ups and jump to end states. Centralize this check in `PaisaMotion.reduceMotion(context)`.

---

## 3. Core component rebuild (`ui/components/`)

Rebuild these shared components first; screens then consume them.

1. **`PaisaCard`** (new): surface1 background, 24dp radius, 1px top-edge highlight, 20dp inner padding, optional press-scale. All screens replace ad-hoc `Column + background` with this.
2. **`GlassBar`** (new): wraps Haze — used by bottom nav, top bars, sheet handles. Params: `hazeState`, tint = `surfaceGlass`, hairline bottom/top border.
3. **`TickerAmount`** (new) + updated **`AmountText`** (paise treatment, tabular figures).
4. **`Charts.kt` rewrite**: keep Vico only if trivial; otherwise custom Canvas:
   - `AuroraAreaChart` — line with gradient fill fading to transparent (accent→transparent 12% alpha), draw-in animation, drag scrubbing that shows a vertical hairline + floating glass tooltip (date + amount) and haptic tick per point.
   - `DonutChart` — 12dp stroke, rounded caps, 2dp gaps, sweep-in animation, tap-a-slice → slice thickens to 16dp + center label swaps to that category (existing tap-to-filter behavior preserved).
   - `BarsChart` — 6-month trend, rounded 6dp tops, grow-in stagger, current month in `accent`, past in `surface2` with category… keep single-color.
   - `SparkLine` — tiny 48×20dp inline line for merchant/trip rows.
5. **`TxnRow` redesign**: 64dp min height; leading 40dp rounded-square (14dp radius) tile in category color @14% alpha containing the category's icon (see §5 feature: category icons) or its initial letter in the category color; merchant name `bodyBold` + place/relative-time line `caption`; trailing `AmountText` M + a small source glyph (UPI app / SMS) at `inkFaint`. Credits get `positive` amount + a tiny ↓ badge. No hairline between rows — use 4dp gaps and grouped date headers (sticky, `label` style eyebrow like "TODAY", "FRI 10 JUL").
6. **`StatBlock` redesign**: value in `amount L`, label eyebrow above in `label` style, optional delta chip (▲/▼ + % in positive/negative @ 14% bg).
7. **`CategoryPickerSheet`**: glass sheet (28dp top radius, Haze), grid of category tiles (icon + name, selected = accent ring), spring slide-up.
8. **`FilterUi`**: horizontally scrolling pill chips; selected pill = ink bg with bg-colored text (inverted), unselected = surface2; pill selection animates with a shared "sliding highlight" where feasible.
9. **`EmptyState`** (new): centered illustration slot (simple vector, `inkFaint` strokes), one-line title, one-line body, primary button. Every screen must use it — no bare "No data" text.
10. **`MapPin` v2**: teardrop replaced by a circular pin — category-colored disc with white icon, 2dp bg ring; trip-tagged pins get a second outer ring in `accent`. Cluster markers: dark glass circle with count in `ink` and a thin arc showing dominant category color.

---

## 4. Screen-by-screen spec

### 4.1 App shell & navigation (`MainActivity` / `NavGraph`)
- Edge-to-edge everywhere (`enableEdgeToEdge()`), transparent system bars, content draws behind them with proper insets.
- **Bottom nav v2:** floating glass pill (GlassBar), 16dp from screen edges/bottom, 5 items (Home, Transactions, Map, Trips, Settings). Selected item = filled icon + label; unselected = outline icon only. A small pill indicator slides between items with springBouncy. The bar's glass tint mixes 8% of the current `auroraTint`. Bar auto-hides on scroll-down, returns on scroll-up (spring).
- Top app bars: transparent over content; when content scrolls under, a GlassBar fades in behind the title (Haze).

### 4.2 Dashboard (`dashboard/DashboardScreen.kt`)
Top → bottom:
1. Greeting row: "July" eyebrow + settings-free (settings lives in tab); right side = review-queue badge if pending (count in a small `warning`-tinted pill, tap → ReviewQueue).
2. **Aurora hero card** (the signature): month-to-date total as `TickerAmount` 44sp; below it a one-line forecast "On track · ~₹42,300 by month end" (or "Trending over by ₹3,100" in warning) from the existing `SpendForecast`; a slim budget progress bar (gradient accent→accentAlt, thumb glow) if budgets are set; the animated aurora gradient behind everything, hue by budget health as defined in §0. 7-day mini `SparkLine` bottom-right at 30% alpha.
3. "This month" section: `DonutChart` (left, ~160dp) + top-4 category legend rows (dot, name, amount, % — tap navigates with existing `Transactions.createRoute(categoryName)`), "All categories →" text button.
4. "Trends": `AuroraAreaChart` 30-day, then `BarsChart` 6-month, each in a PaisaCard with an eyebrow label.
5. "Top merchants": rows with 28dp initial-tile, name, txn count caption, amount + `SparkLine`.
6. Bottom spacer for the floating nav.
All sections stagger-rise on first entry.

### 4.3 Insights (`insights/InsightsScreen.kt`)
Re-lay as a vertical feed of PaisaCards: budget list (per-category progress bars in category color @14% track, filled in category color, over-budget fill switches to `negative` and the row shakes 2px once on entry), weekday-vs-weekend split (two StatBlocks), largest single spend, and the donut with shared element key `"donut"` from Dashboard.

### 4.4 Transactions (`transactions/TransactionsScreen.kt`)
- Pinned glass top bar: title + total-for-current-filter as a small TickerAmount.
- `FilterUi` pills (category/source/date-range as today).
- Grouped list per TxnRow v2 with sticky date eyebrows; swipe-left on a row reveals a category quick-assign action (icon buttons in category colors), swipe uses spring return.
- Scroll-to-top FAB (glass circle, appears after 8 items).

### 4.5 Transaction detail (`transactions/TransactionDetailScreen.kt`)
- Arrives via shared element (merchant+amount morph from the row).
- Header: 56dp category tile, merchant `title`, amount `TickerAmount L`, chips for source app + trip tag.
- A 140dp-tall static mini-map (if located) with the v2 pin, tap → Map centered there; below, a clean key-value ledger (time, category — tap opens CategoryPickerSheet, place line, UPI ref, notes) separated by hairlines inside a single PaisaCard.
- Destructive actions in an overflow menu, not inline.

### 4.6 Map (`map/MapScreen.kt`)
- Full-bleed map, glass chrome floating over it: top = category filter pills on a GlassBar; bottom-left = month selector chip; keep existing dark `PaisaMapStyle` but update it to the ink-navy bg hue (#0A0C12 family) so map and app feel continuous.
- v2 pins/clusters (§3.10). Tapping a pin: a glass bottom card slides up (springBouncy) with the TxnRow for that transaction; tap again → TransactionDetail (shared element from the card).
- Camera moves animated (`animateCamera`), never jump-cut.

### 4.7 Trips (`trips/TripsScreen.kt` + `TripDetailScreen.kt`)
- Trip list: large PaisaCards, top half = a static map snapshot of the trip's bounding box, dark-styled, with a bottom scrim gradient (bg → transparent); overlaid title (place, dates eyebrow) and total amount; bottom half = 3 mini stats (days, txns, top category). Card → detail = shared element grow.
- Trip detail: header continues the map (now interactive, pins for each txn), then daily-spend `BarsChart`, category donut (small), and the txn list. Auto-detected banner ("Detected 8–11 Jul · Kotdwar") with confirm/edit actions if unconfirmed.

### 4.8 Review queue (`reviewqueue/`)
Turn it into a **card-stack triage**: one transaction at a time as a large centered PaisaCard (merchant, amount, place, suggested category as a highlighted chip plus 4 alternates). Tap a category → card flies off right with spring + haptic, next card springs in from 0.95 scale. Progress dots on top ("3 of 7"). Keep a "list view" toggle in the top bar that renders the old list for bulk work. This is the app's most repeated interaction — make it feel great.

### 4.9 Settings (`settings/SettingsScreen.kt`)
- Sectioned PaisaCards with eyebrow labels: Capture (permission rows with live status pills — `positive` "Active" / `warning` "Grant"), Data (export/import), Manage (categories/merchants/bank patterns), Debug (collapsed behind a "Developer" expander).
- Each row: 24dp icon in a 36dp surface2 tile, title + caption, chevron.

### 4.10 Management screens (categories / merchants / bank patterns)
- Categories: grid of tiles (icon, name, color ring), tap → edit sheet (glass) with icon picker + color picker limited to CategoryPalette; drag to reorder.
- Merchants & bank patterns: keep list layouts but on TxnRow-style rows + EmptyState; the "suggest pattern from example" tool gets a proper two-step sheet (paste SMS → highlighted capture groups preview in accent).

### 4.11 Debug screens
Minimal effort: wrap in PaisaCards, correct tokens, no motion work.

---

## 5. New features to add (in priority order)

Implement 1–3 as part of this redesign; 4–6 are fast follows behind the same design system.

1. **Onboarding flow (3 screens, first launch).** Today the app is inert until two scary permissions are granted from Settings — this is the #1 UX gap. Screens: (a) value prop over a slow aurora, (b) notification access — explain exactly what is read (UPI payment notifications, parsed on-device, nothing leaves the phone) with a live example card, deep-link button to the system toggle; (c) SMS access, same treatment, skippable. Detect grant on resume and celebrate with a small tick animation. Store `onboardingDone` in DataStore.
2. **Category icons.** Add an `icon: String` (material icon name) to the category entity default seed + management UI (§4.10); TxnRow/donut legend/pins consume it. Pure UI + one Room migration.
3. **"Safe to spend" number.** If budgets exist: `(total budget − MTD spend) / days remaining` surfaced as a secondary line on the Aurora hero ("₹610/day for the next 18 days"). Reuses existing budget + forecast code, no new data.
4. **Subscription/recurring detection.** Flag merchants with ~same amount at ~monthly cadence (± 3 days, ± 5% amount) from existing txn history; new "Recurring" card on Insights listing them with next-expected date. Pure query + heuristic in `enrich/`, unit-testable with fake DAOs like the rest.
5. **Trip split & settle-up.** Trips already exist and the primary user takes group trips: allow tagging participants (local-only names) per trip txn, equal/custom splits, and a settle-up summary ("Rohit owes you ₹1,240") with a share-as-text action. All offline, no accounts.
6. **Home-screen widget (Glance).** MTD amount + budget bar + last transaction. High daily-visibility payoff, isolated module.

Also worth a later look: biometric app lock (financial data on a sideloaded app), monthly shareable recap card, and full-text search in Transactions.

---

## 6. Implementation order for Claude Code

Work in these phases; compile and run `testDebugUnitTest` after each.

- **Phase 0 — Foundations:** add Haze + fonts; rewrite `Color.kt`, `Type.kt`, `Shape.kt`, add `Motion.kt`; keep old token names temporarily aliased so the app still compiles, then migrate call sites.
- **Phase 1 — Components:** PaisaCard, GlassBar, TickerAmount/AmountText, Charts rewrite, TxnRow, StatBlock, FilterUi, EmptyState, MapPin, CategoryPickerSheet.
- **Phase 2 — Shell + core screens:** edge-to-edge, floating glass bottom nav, nav transitions; Dashboard (incl. Aurora hero), Transactions, TransactionDetail.
- **Phase 3 — Motion pass:** SharedTransitionLayout wiring (row→detail, trip→detail, donut), list staggers, chart draw-ins, review-queue card stack, map polish.
- **Phase 4 — Remaining screens + features:** Insights, Trips, Settings, management screens; then features §5.1–5.3.

**Acceptance criteria:**
- No color literal outside `Color.kt`/`CategoryPalette`; no gradient/glass outside the allowed surfaces (§0).
- Text contrast ≥ 4.5:1 against its actual surface (check inkMuted on surface2, and everything on the aurora — add a 20% bg scrim under hero text if needed).
- 60fps scroll on the transaction list (no blur inside lazy items; Haze sources only on screen-level containers).
- Light theme compiles and is usable (derived tokens), even though dark is the flagship.
- Reduced-motion path verified (§2.5).
- All existing unit tests green; no ViewModel/DAO signature changes.
