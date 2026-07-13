# UPI Location-Aware Expense Tracker — Implementation Plan

**Codename:** PaisaTrail
**Target:** Personal Android app, sideloaded APK (no Play Store constraints)
**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35
**Core idea:** Capture every UPI payment passively (notifications + SMS), stamp it with GPS location at the moment of payment, let the user tag it via an instant popup, and visualize spending on maps, graphs, and per-trip summaries.

---

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard, coroutines-first |
| UI | Jetpack Compose + Material 3 | Modern, fast iteration |
| Architecture | MVVM + Repository, single-module to start | Simple; split into `:core`, `:data`, `:feature-*` later if needed |
| DI | Hilt | Boilerplate-free injection for services/receivers |
| Local DB | Room (SQLite) | Local-first requirement |
| Background | Foreground Service (short-lived) + WorkManager | Location fix + retries |
| Location | FusedLocationProviderClient (Play Services Location) | Battery-efficient, best accuracy |
| Charts | Vico (Compose-native) or MPAndroidChart | Category/trend graphs |
| Maps | Google Maps Compose SDK | Payment pins + heatmap |
| Reverse geocoding | Android `Geocoder` (free, offline-capable on many devices); optional Places API later | Merchant-type inference |
| Serialization | kotlinx.serialization | Export/backup as JSON |
| Testing | JUnit5 + Turbine + Robolectric; instrumented tests for services | Parser correctness is critical |

No backend. No login. Everything on-device. Optional encrypted export to file for backup.

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│  CAPTURE LAYER                                           │
│  ┌────────────────────────┐  ┌────────────────────────┐  │
│  │ UpiNotificationListener │  │ SmsBroadcastReceiver   │  │
│  │ (NotificationListener-  │  │ (RECEIVE_SMS)          │  │
│  │  Service)               │  │                        │  │
│  └───────────┬────────────┘  └───────────┬────────────┘  │
│              └──────────┬────────────────┘               │
│                         ▼                                │
│               RawEventIngestor                           │
│         (normalize → parse → dedup gate)                 │
└─────────────────────────┬────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────┐
│  ENRICHMENT LAYER                                        │
│  LocationStamper (foreground service, 1 GPS fix)         │
│  MerchantResolver (VPA→name mapping, learned over time)  │
│  CategoryGuesser (rules + keyword map + reverse geocode) │
└─────────────────────────┬────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────┐
│  STORAGE LAYER — Room                                    │
│  raw_events │ transactions │ merchants │ categories │    │
│  trips │ tag_prompts                                     │
└─────────────────────────┬────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────┐
│  INTERACTION LAYER                                       │
│  TagPromptNotification (heads-up, action buttons)        │
│  Compose UI: Dashboard │ Map │ Transactions │ Trips │    │
│  Review Queue │ Settings                                 │
└──────────────────────────────────────────────────────────┘
```

**Key principle:** capture NEVER blocks on enrichment. A raw event is persisted within milliseconds; location, merchant resolution, and tagging happen asynchronously and update the row later.

---

## 3. Capture Layer — Detailed Logic

### 3.1 Notification Listener (primary source)

**Component:** `UpiNotificationListenerService : NotificationListenerService`

**Package allowlist (configurable in Settings):**
```
com.google.android.apps.nbu.paisa.user   → Google Pay
com.phonepe.app                          → PhonePe
com.dreamplug.androidapp                 → CRED
net.one97.paytm                          → Paytm
in.org.npci.upiapp                       → BHIM
in.amazon.mShop.android.shopping         → Amazon Pay
+ bank apps as needed (HDFC, ICICI, SBI YONO...)
```

**Flow on `onNotificationPosted(sbn)`:**
1. Reject if `sbn.packageName` not in allowlist → return.
2. Extract `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_SUB_TEXT` from `sbn.notification.extras`.
3. Concatenate into a normalized string; run through **debit-signal filter** (must contain ₹/Rs/INR + a paid/sent/debited keyword; must NOT contain received/credited/refund/reminder/offer/cashback keywords → those go to a separate credit/ignore path).
4. Persist to `raw_events` immediately (source=NOTIFICATION, package, full text, `sbn.postTime`).
5. Hand off to `RawEventIngestor` on a coroutine.

**Per-app parser strategy:** one `NotificationParser` interface, implementations per app, registered in a map keyed by package. Each returns `ParsedTxn(amount, payeeName?, vpa?, refId?, direction)` or null.

Example patterns to implement (verify against real notifications on your own phone during Phase 1 — formats drift):
- GPay: `"You paid ₹450 to Sharma Tea Stall"` / `"₹450 paid to <name>"`
- PhonePe: `"Payment of ₹450 to <name> is successful"` / `"Paid ₹450"` (payee sometimes in title)
- CRED: `"₹450 paid via CRED UPI"` (payee often in title)
- Paytm: `"Rs.450 paid to <name>"`

**Amount regex (shared):**
```regex
(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)
```
Normalize by stripping commas → `BigDecimal`. Never use Float/Double for money.

### 3.2 SMS Receiver (fallback + backfill source)

**Component:** `SmsBroadcastReceiver` on `android.provider.Telephony.SMS_RECEIVED` with priority set high. Permissions: `RECEIVE_SMS`, `READ_SMS` (READ_SMS enables historical backfill — see 3.4). Sideloaded, so no policy issue.

**Sender-ID filter:** Indian bank transactional SMS come from IDs like `VM-HDFCBK`, `AD-ICICIB`, `JD-SBIUPI`, `BP-AXISBK`, `VK-KOTAKB`. Match on the last 6 chars against a bank-ID table rather than the full prefix (the 2-letter route prefix varies).

**Per-bank regex library** (implement as data, not code — a table of `(bankId, regex, groupMapping)` rows so new banks are additions, not code changes). Representative shapes:
- HDFC: `Rs.450.00 debited from a/c **1234 on 11-07-26 to VPA sharmatea@ybl (UPI Ref No 6187...)`
- ICICI: `Acct XX123 debited with Rs 450.00 on 11-Jul-26; ABC CAFE credited. UPI:6187...`
- SBI: `Dear UPI user A/C X1234 debited by 450.0 on date 11Jul26 trf to SHARMA TEA Refno 6187...`
- Axis / Kotak / PNB / BOB: similar; each gets its own row.

**Extract:** amount, VPA and/or payee name, **UPI reference number** (critical for dedup), account last-4, timestamp.

**Debit-only gate:** keywords `debited|paid|sent|withdrawn` present AND `credited|received|deposited` absent (unless the credited party is the payee, as in ICICI's format — the regex group mapping handles this per bank).

### 3.3 Deduplication (the hardest part — get this right)

Every payment can generate **2–3 events**: UPI-app notification + bank SMS + sometimes a bank-app notification. Dedup logic in `RawEventIngestor`:

1. **Strong key:** if UPI Ref No present in both events → same ref = same transaction. Merge.
2. **Fuzzy key (when ref missing, e.g. notifications rarely carry it):** match on `(amount exact) AND (timestamp within ±180 s) AND (payee token overlap ≥ 1 token OR one side has no payee)`.
3. **Merge policy:** notification event wins for `payeeName` (human-readable), SMS event wins for `vpa`, `refId`, `bankAccount`. Location comes from whichever event arrived first (earlier = closer to payment moment).
4. Store the merge as one `transactions` row linked to all contributing `raw_events` rows (never delete raw events — they're your audit trail and re-parse source).
5. **Refund matching:** a credit event with same amount + same VPA/payee within 48 h creates a `refund_of` link and flips the transaction status to REFUNDED (excluded from spend totals by default, toggleable).

**Validation invariant to unit-test heavily:** ingesting the same real-world triplet (GPay notif + HDFC SMS + HDFC app notif) in any arrival order must always yield exactly one transaction.

### 3.4 Historical backfill

On first run (and on demand): query `Telephony.Sms.CONTENT_URI` for the last N months, run every message through the same bank-regex pipeline, insert with `location = null, source = BACKFILL`. This instantly reconstructs months of history — your past trips included — just without location.

---

## 4. Enrichment Layer

### 4.1 LocationStamper

**Trigger:** immediately when `RawEventIngestor` creates a new transaction.

**Mechanism:** start a short-lived **foreground service** (type `location`; required on Android 14+ for background-started location work) that:
1. Calls `fusedLocationClient.getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY, cts)` with a **15 s timeout**.
2. On success → write `(lat, lng, accuracyMeters, fixTime)` to the transaction, stop service.
3. On timeout/failure → fall back to `lastLocation`; if that fix is older than 10 min, store it but mark `locationQuality = STALE`. If nothing, mark `MISSING` and enqueue a WorkManager retry once (payments at home usually still resolve via last-known).
4. Reverse-geocode lazily (WorkManager, batched, requires network) → store `placeName, locality, adminArea` on the transaction.

**Permissions:** `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (user grants "Allow all the time" once — fine for sideloaded personal use) + `FOREGROUND_SERVICE_LOCATION` + `POST_NOTIFICATIONS`.

**Online-vs-offline heuristic:** if the payee/VPA matches a known online-merchant list (`razorpay|payu|billdesk|zomato|swiggy|amazon|flipkart|irctc|jio|airtel...`) → mark `paymentContext = ONLINE` and treat location as "where I was," not "where the merchant is." Everything else defaults to `IN_PERSON`. User can flip it in the review UI, and the app learns per-merchant.

### 4.2 MerchantResolver

- Table `merchants(id, canonicalName, vpaSet[], aliasSet[], defaultCategory, isOnline, homeLat, homeLng)`.
- On each transaction: exact VPA match → attach merchant; else fuzzy name match (normalized lowercase, token-set ratio ≥ 0.85); else create a provisional merchant.
- **Learning loop:** when the user tags/renames once, every future payment to the same VPA auto-inherits the name and category. When ≥ 3 in-person payments to a merchant cluster within 150 m, set the merchant's `homeLat/Lng` (median) — later payments with a bad GPS fix can snap to it.

### 4.3 CategoryGuesser (rule cascade, first hit wins)

1. Merchant's learned `defaultCategory`.
2. Keyword map on payee/VPA (`tea|cafe|restaurant|dhaba|zomato → Food`; `petrol|fuel|hpcl|iocl|bpcl → Fuel`; `irctc|redbus|ola|uber|rapido → Travel`; `oyo|hotel|treebo → Stay`; etc. — keep this as an editable table in DB, not hardcoded).
3. Reverse-geocoded place type, if available.
4. Amount+context heuristics (e.g., IN_PERSON, ₹10–₹60, morning → likely Food/chai) — low confidence, always flagged for review.
5. Fallback `Uncategorized` → lands in the Review Queue.

Every guess stores a `confidence` (HIGH/MED/LOW) and `guessSource`, so the UI can show "auto-tagged" vs "needs review."

---

## 5. Tag Popup (Interaction Layer)

On transaction creation, fire a **heads-up notification**:

> **₹450 → Sharma Tea Stall** · near Kotdwar
> `[🍜 Food]  [⛽ Fuel]  [🧳 Travel]  [More…]`

- The 3 inline buttons = top-3 predicted categories for this merchant/context (Android allows max 3 actions). Tapping one writes the tag via a `BroadcastReceiver` **without opening the app** and dismisses the notification.
- `More…` deep-links into the app's tag sheet (full category grid + note + split + "mark as P2P transfer, not expense").
- Auto-dismiss after 10 min → transaction goes to the **Review Queue** with its guessed category applied at LOW confidence. Nothing is ever lost by ignoring the popup.
- Optional `SYSTEM_ALERT_WINDOW` overlay dialog mode for people who want a forced popup; default OFF (notifications are less intrusive and more reliable across OEMs).
- **Trip mode:** when a trip is active (see 7.4), the notification also shows the trip name and tags the transaction to it automatically.

---

## 6. Data Model (Room)

```sql
raw_events(
  id PK, source TEXT{NOTIFICATION,SMS,BACKFILL}, packageOrSender TEXT,
  fullText TEXT, postedAt INTEGER, parsedOk INTEGER, txnId FK NULL)

transactions(
  id PK, amountPaise INTEGER,              -- store money as integer paise
  direction TEXT{DEBIT,CREDIT},
  status TEXT{CONFIRMED,REFUNDED,SUSPECT_DUP},
  payeeNameRaw TEXT, vpa TEXT, upiRef TEXT UNIQUE NULLABLE,
  bankAcctLast4 TEXT, occurredAt INTEGER,
  merchantId FK NULL, categoryId FK NULL,
  tagSource TEXT{USER,AUTO_HIGH,AUTO_LOW,NONE},
  paymentContext TEXT{IN_PERSON,ONLINE,P2P},
  lat REAL NULL, lng REAL NULL, accuracyM REAL NULL,
  locationQuality TEXT{GOOD,STALE,MISSING},
  placeName TEXT NULL, locality TEXT NULL,
  tripId FK NULL, note TEXT NULL, refundOfTxnId FK NULL)

merchants(id PK, canonicalName, defaultCategoryId, isOnline INTEGER,
  homeLat REAL NULL, homeLng REAL NULL)
merchant_vpas(vpa PK, merchantId FK)
merchant_aliases(alias, merchantId FK)

categories(id PK, name, emoji, colorHex, parentId NULL, sortOrder)
  -- seed: Food, Travel, Fuel, Stay, Shopping, Groceries, Bills,
  --        Entertainment, Health, P2P Transfer, Uncategorized

trips(id PK, name, startAt, endAt NULL, autoDetected INTEGER,
  homeGeofenceLat, homeGeofenceLng)

bank_sms_patterns(bankId PK, senderSuffix, regex, groupMap JSON, enabled)
keyword_rules(id PK, pattern, categoryId, priority)
```

**Indexes:** `transactions(occurredAt)`, `transactions(upiRef)`, `transactions(merchantId)`, `transactions(tripId)`, spatial-ish index via `(latBucket, lngBucket)` computed columns for fast map/geo queries (bucket = floor(coord × 100)).

---

## 7. UI / Screen Design (Compose, Material 3, dark-mode default)

### 7.1 Dashboard (home)
- Month spend total + delta vs last month.
- Donut: category split. Bar: daily spend last 30 days. Line: 6-month trend.
- "Needs review" badge → Review Queue.
- Top 5 merchants this month.

### 7.2 Map view (the differentiator)
- Google Map with clustered pins; pin color = category, size ∝ amount.
- Toggle: pins ↔ spend heatmap.
- Date-range + category filters; tap pin → transaction card; tap cluster → zoom/list.
- "Trip playback": animate pins in chronological order along the route — great for your Kotdwar/Chakrata rides.

### 7.3 Transactions list
- Grouped by day; row = emoji, merchant, place ("near Lansdowne"), amount, confidence chip.
- Swipe right = quick re-tag, swipe left = exclude/mark P2P.
- Search + filters (category, trip, merchant, amount range, location radius "within 5 km of point").

### 7.4 Trips
- Manual start/stop, or **auto-detect**: 3+ in-person payments > 80 km from home geofence within 24 h → prompt "Looks like you're on a trip — track it?"
- Trip summary: total, per-category, per-day, distance-ordered map polyline, export as image/PDF for the group.

### 7.5 Review Queue
- All LOW-confidence / uncategorized / suspected-duplicate / refund-pair items.
- One-tap confirm flows; "apply to all future payments to this merchant" checkbox.

### 7.6 Settings
- Source toggles (notifications / SMS / backfill run), app allowlist, bank pattern editor (view + test a pasted SMS against patterns), location on/off, popup style, category editor, keyword rules editor, JSON export/import (with optional passphrase encryption via Jetpack Security), and a **parser debug screen** showing recent raw_events with parse results — indispensable while tuning regexes.

### 7.7 Visual Design System — Minimalist Direction

**Design thesis:** the app is a ledger, not a fintech product. No gradients, no cards-on-cards, no decoration. The *numbers and the map* are the interface; everything else recedes. One accent color, one signature element, generous whitespace, and typography does all the hierarchy work.

**Signature element:** the **amount + place line**. Every transaction everywhere in the app renders as a large tabular-numeral amount with a small muted location line beneath it ("₹450 · near Lansdowne"). This pairing is the product's identity — money anchored to a place — and it repeats consistently from notification to list row to map callout to trip summary.

#### Color tokens (dark-first, light derived)

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | `#0E0F11` | `#FAFAF8` | App background — one flat surface, no elevation stacks |
| `surface` | `#16181B` | `#FFFFFF` | Sheets, dialogs only |
| `ink` | `#E8E8E4` | `#1A1B1E` | Primary text |
| `inkMuted` | `#8A8D93` | `#6B6E76` | Secondary text, place lines, axis labels |
| `hairline` | `#26282C` | `#E7E7E3` | 1 dp dividers — the only "borders" in the app |
| `accent` | `#4C8DFF` | `#2F6FE0` | Single accent: active states, selected chips, CTA, map "you are here" |
| `negative` | `#E5645A` | `#D14840` | Refunds/errors only — never for ordinary debits |

Rules: never more than one accent-colored element per screen region. Debit amounts are `ink`, not red — spending isn't an error state. No shadows; separation via hairlines and spacing only. Corner radius: 12 dp everywhere, one value, no exceptions.

**Category colors** are the sole permitted color moment, and they appear *only* as map pins, donut segments, and 8 dp dots next to category names — never as row backgrounds or chip fills. Use a muted 8-hue set (desaturated tones, e.g. olive, clay, slate-blue, sand, sage, plum, teal, grey) defined once in `categories.colorHex`; the seeded palette must sit at similar lightness so no category screams.

#### Typography

| Role | Face | Usage |
|---|---|---|
| Numerals/display | **IBM Plex Mono** (or JetBrains Mono), tabular figures | All amounts, dates, ref numbers. Amounts: 28 sp list-header / 20 sp row / 44 sp dashboard total, weight 500 |
| Body/UI | **Inter** | Labels, merchant names, settings. 15 sp body, 13 sp secondary |
| Overline | Inter 11 sp, +0.08 em tracking, `inkMuted`, sentence case | Section labels ("This month", "Needs review") |

Monospaced amounts are a functional choice, not styling: columns of money align digit-for-digit, which is exactly how a ledger should read. No italic anywhere; hierarchy comes from size + weight + `ink`/`inkMuted`, never from color.

#### Layout & spacing

- 4 dp grid; screen gutter 20 dp; vertical rhythm 12 / 20 / 32 dp — pick from these three, nothing ad hoc.
- Lists are full-bleed rows separated by hairlines, not floating cards. A row: emoji-free by default (category shown as 8 dp dot), merchant in `ink`, place line in `inkMuted`, amount right-aligned mono.
- Dashboard is a single scrolling column: total → donut → daily bars → top merchants. Charts are naked — no chart backgrounds, no gridlines except a single hairline baseline, no legends (labels sit directly on segments/bars).
- Map screen is 100% map; controls are a single floating filter chip-row at top and nothing else. Bottom-sheet (surface token) slides up for pin details.
- Max one FAB in the whole app (manual add, on Transactions). Everything else is inline.
- Empty states: one line of `inkMuted` text + one accent action ("No payments yet today"). No illustrations.

#### Components (Compose)

- `AmountText(amountPaise, size)` — the canonical mono renderer, formats Indian grouping (₹1,45,300), used everywhere; forbidden to format money any other way.
- `TxnRow`, `PlaceLine`, `CategoryDot`, `HairlineDivider`, `StatBlock` — 5 primitives cover 90% of the UI. If a screen needs a new component, question the screen.
- Chips: outlined hairline, accent fill only when selected, no icons inside chips.
- The tag notification mirrors the app: title = `AmountText` string + payee, actions are plain text labels (Food / Fuel / Travel), no emoji in the minimalist theme (emoji stays as an optional setting since section 7.3 mentions it).

#### Motion

Almost none, and only meaningful: bottom-sheet spring on map pin tap, 150 ms fade on tag confirmation, animated count-up on the dashboard total once per open. No shimmer loaders (data is local and instant — loading states should be rare enough to just be blank-then-content). Respect the system reduced-motion flag by disabling the count-up.

#### Copy rules

Sentence case everywhere. Verbs on buttons say what happens: "Tag as Food", "Start trip", "Export JSON". No exclamation marks, no praise ("Great job!" is banned), no finance-app cheer. Errors state the fact and the fix: "Location was off for this payment. Add it manually?"

#### Design QA checklist (run per screen before calling it done)

1. Exactly ≤ 1 accent element visible? 2. All amounts via `AmountText`? 3. Spacing values ∈ {12, 20, 32}? 4. Any shadow, gradient, or second radius value? Remove it. 5. Does the screen still read at 200% font scale? 6. Dark and light both checked?

---

## 8. Validation & Data-Quality Rules

1. **Amount sanity:** reject parse if amount ≤ 0 or > ₹5,00,000 (configurable) → route to Review as SUSPECT with raw text attached.
2. **Idempotency:** unique constraint on `upiRef`; ingest is `INSERT … ON CONFLICT merge`.
3. **Any-order merge test:** property-based tests feeding event pairs/triplets in all orderings.
4. **Clock skew:** use event `postTime`, not parse time; SMS timestamps from `Telephony` provider.
5. **Timezone:** store epoch millis, render in device zone (matters for trips crossing zones, e.g., a future Ladakh ride).
6. **Location accuracy gate:** if `accuracyM > 500`, mark STALE and don't set merchant `homeLatLng` from it.
7. **P2P detection:** VPA at consumer handles (`@ybl`, `@okaxis`, `@paytm` personal patterns) + name looks like a person + no merchant match → suggest `P2P Transfer` (excluded from spend by default).
8. **Notification redaction:** if extracted text is empty/`Sensitive notification content hidden` (Android 15 behavior on some OEMs), rely on SMS twin; log a counter so you can see how often it happens.
9. **DB migration discipline:** Room migrations from v1, exportSchema=true, migration tests.
10. **Crash safety:** listener/receiver bodies wrapped; a parser exception must never kill capture — log to raw_events with parsedOk=0.

---

## 9. Testing Plan

- **Unit (highest ROI):** parser tests — build a corpus file of 50–100 real anonymized notification texts + SMS bodies (collect from your own phone in week 1); every bank/app pattern gets golden tests. Dedup engine property tests. CategoryGuesser cascade tests.
- **Robolectric:** RawEventIngestor end-to-end with in-memory Room.
- **Instrumented:** NotificationListener with `NotificationManager`-posted fake notifications; LocationStamper with mock FusedLocationProvider; tag-action BroadcastReceiver.
- **Manual matrix:** GPay/PhonePe/CRED × in-person/online × SMS-arrives-first/notification-first × location on/off × Doze mode (payment while screen off) × dual SIM.
- **Field test:** run capture-only build for one week of normal spending before building UI polish — real data will break assumptions and refine regexes.

---

## 10. Build Phases (each phase is a working app)

**Phase 0 — Skeleton (½ day):** project setup, Hilt, Room, empty screens, permissions flow.

**Phase 1 — Capture core (2–3 days):** NotificationListener + SMS receiver + raw_events + parser framework with GPay/PhonePe/CRED + HDFC/ICICI/SBI patterns + dedup engine + unit tests. Debug screen to inspect raw events. *Ship to your phone, collect a week of corpus.*

**Phase 2 — Location + popup (1–2 days):** LocationStamper foreground service, tag heads-up notification with 3 actions, Review Queue basic list.

**Phase 3 — Storage polish + backfill (1 day):** historical SMS backfill, merchants + learning loop, refund matching.

**Phase 4 — UI (3–4 days):** Dashboard charts, Map view with clustering, Transactions list with filters, Settings.

**Phase 5 — Trips + export (1–2 days):** trip auto-detect, trip summary, JSON export/import, encrypted backup.

**Phase 6 — Hardening (ongoing):** OEM battery-killer handling (battery-optimization exemption prompt; you're on-device only so no push fallback needed), more bank patterns, Android 15 redaction handling.

Total: roughly 10–14 focused days for a solid v1.

---

## 11. Known Limitations (accept these up front)

- Online payments get *your* location, not the merchant's (mitigated by ONLINE flag).
- Swiped-away notifications with no SMS twin (rare for bank accounts — RBI mandates SMS alerts) can be missed.
- Some OEMs (Xiaomi/Oppo/Vivo) aggressively kill listener services — the settings flow must walk the user through autostart + battery whitelisting.
- Backfilled history has no location.
- Bank SMS formats change occasionally — the pattern table + debug screen make fixes a 2-minute edit, not a release.
