# PaiseTrail

A UPI location-aware expense tracker for Android. PaiseTrail listens for UPI payment
notifications and bank debit SMS on-device, automatically categorizes and geo-tags each
transaction, and turns that into a dashboard, spend insights, and trip-based expense grouping —
without sending your transaction data anywhere by default.

## Features

- **Automatic capture** — reads UPI app notifications (GPay, PhonePe, Paytm, CRED) and bank debit
  SMS via a data-driven, per-bank regex pattern table (no code change needed to add a new bank).
- **Auto-categorization cascade** — merchant-learned category (from your own past tagging) →
  keyword rules → on-device AI, in that order, so the app gets smarter about your spending the
  more you use it.
- **AI-assisted tagging** — an "Auto-tag with AI" pass that tries on-device Gemini Nano (via
  Android's AICore, on phones where Google has enabled that feature) and falls back to an offline
  similarity match against your own already-tagged history when Nano isn't available.
- **Trips** — auto-detects clusters of in-person spending away from home and groups them into
  trips with their own map, category breakdown, and daily spend chart.
- **Map view** — every located transaction plotted and clustered, with trip-tagged pins visually
  distinguished, filterable by category.
- **Dashboard & Insights** — month-to-date total, category breakdown (donut chart, tap a slice to
  filter the transaction list), a 7-day and a 30-day spend chart, a 6-month trend, top merchants,
  per-category monthly budgets with progress/alerts, and a linear burn-rate month-end spend
  forecast.
- **Review queue** — anything the auto-categorization cascade wasn't confident about, one tap to
  confirm or correct.
- **Data ownership** — export/import your transactions and your bank SMS patterns as JSON, and a
  from-JSON auto-tagger that learns merchant→category rules from a previously labeled export.
- **Everything is editable from the app** — categories, merchants, bank SMS patterns (including a
  "suggest a pattern from example messages" tool), and budgets are all managed from Settings, no
  code change or rebuild required.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (DI), Room (SQLite), WorkManager (background backfill/enrichment)
- Google Maps Compose (clustering, custom map style)
- ML Kit GenAI Prompt API (on-device Gemini Nano, optional/best-effort)
- kotlinx.serialization (JSON export/import), kotlinx.coroutines

## Getting started

1. Clone the repo and open it in Android Studio (a recent version supporting AGP 9.x / Kotlin
   2.3.x).
2. Create a `local.properties` file in the project root (never committed) with a Google Maps API
   key:
   ```properties
   MAPS_API_KEY=your_key_here
   ```
   The app builds and runs without a key — the Map screen just shows a placeholder instead of a
   live map.
3. Grant the two runtime permissions from Settings inside the app: notification access (to read
   UPI app notifications) and SMS (to read bank debit SMS). Neither is requested at first launch;
   the app is fully inert until you grant them.
4. Build and run:
   ```
   ./gradlew assembleDebug
   ```

## Testing

```
./gradlew testDebugUnitTest
```

Unit tests cover the SMS/notification parsers, the category auto-tagging cascade (keyword rules,
merchant learning, the offline AI-fallback classifier), the bank-pattern regex inducer, refund/
self-transfer detection, trip management, and the JSON export/import round-trip — all against
in-memory fake DAOs, no device or emulator required.

## Project structure

```
app/src/main/java/com/paisetrail/app/
├── capture/       SMS + notification listeners and per-app/per-bank parsers
├── enrich/        category/merchant/location enrichment cascade, incl. enrich/ai (AI tagger)
├── trips/         trip auto-detection and management
├── export/        JSON export/import (transactions and bank patterns)
├── data/db/       Room entities, DAOs, database
├── di/            Hilt modules
└── ui/            Compose screens, navigation, shared components, theme
```

## Privacy

Capture, parsing, categorization, and storage all happen on-device by default. The optional
"Auto-tag with AI" feature tries on-device Gemini Nano first (which never leaves the device); its
fallback is a purely local similarity match — no network call, no data ever sent off the phone,
either way.
