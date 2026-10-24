# Collections Tracker — Android Subscription & Cash Ledger

> Modern, offline-first native Android application built with Kotlin, Jetpack Compose (Material 3), Room SQLite, and a custom financial cycle engine for recurring collections and cash flow tracking.

---

## Architecture Overview

Collections Tracker is engineered as an offline-first financial tool for tracking recurring receivables, tenant rent, installment plans, and petty cash ledgers with zero latency.

```text
┌────────────────────────────────────────────────────────┐
│               Jetpack Compose UI (M3)                  │
│         (Reactive State & ViewModels via Flow)         │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
┌─────────────────────────┐ ┌─────────────────────────────┐
│      CycleEngine        │ │      AnalyticsEngine        │
│ (Recurrence, Intervals, │ │   (Cash Flow, Aging, Due    │
│  Grace Period Math)     │ │        Calculations)        │
└────────────┬────────────┘ └─────────────┬───────────────┘
             │                            │
             └─────────────┬──────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│                   AppRepository                        │
│          (Local-First Single Source of Truth)          │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
┌─────────────────────────┐ ┌─────────────────────────────┐
│   AndroidX Room (SQLite)│ │       SyncManager           │
│   Foreign Keys & Index  │ │ (FIFO Sync Queue for Cloud) │
└─────────────────────────┘ └─────────────────────────────┘
```

---

## Key Features

### 🔄 Deterministic Recurring Cycle Engine
- **Flexible Billing Periods**: Supports weekly, monthly, and arbitrary custom-day intervals.
- **Accrual Math**: Computes overdue cycles, paid vs unpaid balances, and future maturity dates automatically without drift.

### 💰 Comprehensive Cash Ledger
- **Cash In & Paid Out**: Track on-the-spot collections, deposits, wages, refunds, and operating expenses.
- **Audit Trails**: Links cash receipts directly to underlying payer records and collection schedule lines.

### 📶 Offline-First Resiliency
- **Local SQLite via Room**: Instantaneous response time, full transactional integrity with foreign key cascades and indices.
- **FIFO Sync Queue**: Mutations write to an append-only `sync_queue` table for conflict-free background synchronization.

### 🌐 Multi-Language & Modern UX
- Native multi-lingual localization support (`Translations.kt`).
- Material 3 theming with dynamic dark mode and edge-to-edge layout styling.

### 🧪 Automated Testing
- **Visual Regression**: Validated with **Roborazzi** screenshot tests for layout integrity.
- **Unit & UI Tests**: Evaluated via **Robolectric** and Jetpack Compose testing rules.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ |
| **UI Framework** | Jetpack Compose, Material 3, Material Icons Extended |
| **Architecture** | MVVM, Kotlin Coroutines, StateFlow, AndroidX Lifecycle |
| **Data Persistence** | AndroidX Room (SQLite), KSP (Kotlin Symbol Processing) |
| **Networking & JSON** | Retrofit, OkHttp, Moshi Kotlin Codegen |
| **Testing** | Robolectric, Roborazzi Compose, JUnit 4, AndroidX Test Runner |

---

## Getting Started

### 1. Prerequisites
- **Android Studio** (Ladybug / Jellyfish or later)
- **JDK 17+**
- Android SDK with API 36 (minSdk 24)

### 2. Building from Source

```bash
# Clone the repository
git clone https://github.com/AMR0KF/Collections-tracker.git
cd Collections-tracker

# Build debug APK
./gradlew assembleDebug

# Run unit and screenshot tests
./gradlew testDebugUnitTest
```

---

## Project Structure

```text
Collections-tracker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── analytics/      # Analytics calculation engine
│   │   │   │   ├── data/           # Room Database, DAOs, and Entities
│   │   │   │   ├── engine/         # Recurring CycleEngine logic
│   │   │   │   ├── sync/           # FIFO Sync queue and network managers
│   │   │   │   ├── ui/             # Compose screens, ViewModels, Translations
│   │   │   │   └── MainActivity.kt # Entry point activity
│   │   │   ├── res/                # XML resources, drawables, and themes
│   │   │   └── AndroidManifest.xml
│   │   └── test/                   # Robolectric & Roborazzi screenshot tests
│   └── build.gradle.kts            # App-level build script & dependencies
├── gradle/                         # Gradle wrapper and version catalog
├── build.gradle.kts                # Root build configuration
└── settings.gradle.kts             # Module declarations
```

---

## License
MIT License.
