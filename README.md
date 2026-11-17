<div align="center">

<img src="app/src/main/res/drawable/bincheck_launcher_icon_1794915006996.png" alt="BinCheck App Icon" width="112" height="112" />

# BinCheck

**High-Precision Android Batch Validator, Mathematical Luhn Wildcard Solver, IIN/BIN Inspector & Programmable Custom Config Studio**

[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024–36)-10B981?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Design](https://img.shields.io/badge/Material%203-Expressive%20%2B%20Dynamic%20Color-22D3EE?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Storage](https://img.shields.io/badge/Room%20WAL%20v2-DataStore%20%2B%20WorkManager-0EA5E9?style=for-the-badge)](https://developer.android.com/jetpack)
[![Telegram](https://img.shields.io/badge/Channel-%40drkingbd-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/drkingbd)

`com.aistudio.bincheck.vqxkpm` • Edge-to-Edge M3 • Zero Mock Data • Offline-First + Programmable HTTP Pipelines

</div>

---

## Table of Contents

- [Architecture & Validation Pipeline](#-architecture--validation-pipeline)
- [Core Capabilities](#-core-capabilities)
  - [1. Mathematical Luhn Mod-10 Wildcard Solver](#-1-mathematical-luhn-mod-10-wildcard-solver)
  - [2. Custom Config Studio (OB/Loli-Class Config Maker)](#-2-custom-config-studio-obloli-class-config-maker)
  - [3. Validation Engines & Rate-Limit Pacing](#-3-validation-engines--rate-limit-pacing)
  - [4. List & Format Toolkit Studio](#-4-list--format-toolkit-studio)
  - [5. High-Traffic Network Resilience & Eco Mode](#-5-high-traffic-network-resilience--eco-mode)
  - [6. Telegram Bot Webhook Alerts](#-6-telegram-bot-webhook-alerts)
- [UI & Material 3 Expressive Polish](#-ui--material-3-expressive-polish)
- [Project Structure](#-project-structure)
- [Configuration & Secrets](#-configuration--secrets)
- [Building, Linting & Testing](#-building-linting--testing)
- [CI/CD Release Automation](#-cicd-release-automation)

---

## Architecture & Validation Pipeline

```text
+---------------------------------------------------------------------------------------+
| 1. INPUT & SANITIZATION LAYER                                                         |
|    Monospace Batch Console  |  File Importer (.txt/.csv/.json/.html)  |  List Studio  |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| 2. NORMALIZER & MATHEMATICAL WILDCARD SOLVER (CardValidatorEngine)                    |
|    • Splits delimited lines ( |  /  :  ; ) into [PAN | MM | YYYY | CVV]               |
|    • Detects 'x'/'X' wildcards at ANY PAN index (including fixed check digits)        |
|    • Computes exact ISO/IEC 7812 Luhn Mod-10 replacement digits on device             |
+---------------------------------------------------------------------------------------+
                                           |
                    +----------------------+----------------------+
                    |                                             |
                    v (Deterministic / Pre-Flight Gate)           v (Programmable Gateway / Blocks)
+-------------------------------------------+   +-----------------------------------------------+
| 3A. DETERMINISTIC LOCAL VALIDATOR         |   | 3B. CUSTOM CONFIG STUDIO & GATEWAY ADAPTERS   |
|     • Scheme IIN/BIN prefix & length      |   |     • S1 Pre-Request Handshake (CSRF/Session) |
|     • ISO/IEC 7812 Mod-10 checksum        |   |     • S2 Main HTTP Request + Proxy Rotation   |
|     • System-clock expiry verification    |   |     • 9-Category Ordered Block Pipeline       |
|     • Network-accurate CVV (3 vs 4 digit) |   |     • 6-Mode Parser & 14-Condition Keycheck   |
+-------------------------------------------+   +-----------------------------------------------+
                    |                                             |
                    +----------------------+----------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| 4. CONCURRENT WORKER, SQLITE WAL & DELIVERY                                           |
|    • WorkManager CoroutineWorker w/ Semaphore pool (1x–8x) & chunked SQLite queue     |
|    • Room v2 WAL (`validation_history` + 7-day `bin_cache`) & incremental delta sync  |
|    • Live / Die / Unknown UI buckets + Batched Telegram Bot API (`/sendMessage`)      |
+---------------------------------------------------------------------------------------+
```

---

## Core Capabilities

### 1. Mathematical Luhn Mod-10 Wildcard Solver
- Accepts multi-line records formatted with `|`, `/`, `:`, or `;` (`PAN|MM|YYYY|CVV` or `PAN|MM|YY|CVV`) as well as `email:pass` / `user:pass` combos in Custom Config Studio.
- Mathematically solves `x` / `X` wildcards anywhere in the PAN—even when the trailing Luhn digit is pinned (e.g., `453225005536xxx3|08|2028|113`)—guaranteeing valid Mod-10 checksums without brute force.
- Supports `xx` month, `xxxx` future year, and network-accurate CVV validation (`4` digits for **Amex**, `3` digits for **Visa, Mastercard, Discover, JCB, Diners, UnionPay, Maestro**).

### 2. Custom Config Studio (OB/Loli-Class Config Maker)
A full 6-tab visual request and pipeline IDE inside the app (`CustomConfigStudioBottomSheet`):

| Studio Tab | Capabilities |
| :--- | :--- |
| **Request** | Configure Step 1 Pre-Request Handshake (CSRF/Auth/Cookies) chained into Step 2 Main Request (`GET`/`POST`/`PUT`/`DELETE`/`PATCH`/`HEAD`), custom headers, JSON/Form/Raw/Multipart body templates, User-Agent, GZIP compression, timeouts, retries, and live resolved request preview. |
| **Blocks** | Compose multi-step pipelines across **9 categories** (*Browser, Captchas, Conditions, Functions, Interop, Parsing, Requests, Selenium, Utility*) with interactive per-block inspector, `onFalse` condition branching (`CONTINUE`, `SKIP_NEXT`, `STOP_DIE`, `STOP_UNKNOWN`), 1-tap stack recipes, and live **Dry-Run Stack** execution trace. |
| **Parse** | **6 extraction modes** (`JSON` dot-path + array index, `Left/Right`, `Regex Group`, `HTTP Header`, `Set-Cookie`, `HTML/CSS` attribute selectors), recursive matching, fallback defaults, in-place rule editing, and **Smart Auto-Discovered JSON Keys**. |
| **Keycheck** | Ordered **14-condition** decision evaluator (`Contains`, `Contains Any`, `Contains All`, `Not Contains`, `Equals`, `Not Equals`, `Starts With`, `Ends With`, `Regex`, `Status Code`, `Status Range`, `Key Exists`, `Greater Than`, `Less Than`) with step-by-step live debugger trace. |
| **Proxy** | Multi-protocol proxy pool (`HTTP` / `HTTPS` / `SOCKS5`) supporting `host:port`, `host:port:user:pass`, and `scheme://user:pass@host:port`, real-time diagnostics, and **Round-Robin**, **Random**, or **Sticky** rotation. |
| **Profiles** | Searchable saved user configs, built-in starter templates, and **Universal Importer & Exporter** supporting Chrome DevTools `cURL`, JavaScript `fetch()`, Burp/HTTP Toolkit Raw HTTP, OpenBullet/SilverBullet **LoliScript**, and `.json` files. |

<details>
<summary><strong>Supported Dynamic Interpolation Tokens (28+)</strong></summary>

- **Card & Combo**: `{lista}`, `{card}`, `{card_spaced}`, `{card_dashed}`, `{pan4}`, `{bin}`, `{bin8}`, `{mm}`, `{mm_no_pad}`, `{yy}`, `{yyyy}`, `{cvv}`, `{scheme}`, `{scheme_lower}`, `{email}`, `{user}`, `{pass}`
- **Chained & Captured**: `{cap:varName}`, `{var:varName}`, `{cookie}`, `{STATUS_CODE}`, `<SOURCE>`
- **Generators & Crypto**: `{uuid}`, `{timestamp}`, `{timestamp_ms}`, `{iso_date}`, `{random6}`, `{random_hex16}`, `{random_ua}`, `{base64_lista}`, `{base64_combo}`, `{sha256_card}`, `{md5_lista}`

</details>

### 3. Validation Engines & Rate-Limit Pacing

| Engine Mode | Behavior | Concurrency & Pacing |
| :--- | :--- | :--- |
| **`LUHN`** *(Offline)* | Deterministic BIN prefix, length, Luhn Mod-10, expiry & CVV check | `1x–8x` • `0ms–30s` user-configurable |
| **`SANDBOX`** | Built-in test card scenario validator & metadata resolver | `1x–8x` • `0ms–30s` user-configurable |
| **`CHKR.CC`** | Live gateway probe via `https://api.chkr.cc/` + BIN enrichment | `1x` sequential • Enforced `10s` rate-limit delay |
| **`PAYATE`** | Gateway probe via `https://mock.payate.com/api.php` | `1x` sequential • Enforced `3s` rate-limit delay |
| **`MULTI API`** | Automatic `Chkr.cc -> Payate` failover on transient/unknown failures | `1x` sequential • Enforced `5s` rate-limit delay |
| **`CUSTOM HTTP`** | Full Custom Config Studio pipeline + 2-Step HTTP + Block Executor | `1x–8x` • Proxy pool + user-configurable pacing |

### 4. List & Format Toolkit Studio
- **Clean**: Extract valid card strings from noisy HTML/JSON/logs, deduplicate by 12–19 digit PAN or exact normalized line, prune expired `MM/YYYY` rows, strip failed Luhn checksums, and solve `x` wildcards in place.
- **Format**: Instant conversion across `PAN|MM|YYYY|CVV`, `PAN|MM|YY|CVV`, `PAN:MM:YYYY:CVV`, `PAN/MM/YY/CVV`, CSV, `PAN Only`, and `Unique 6-Digit BINs`, plus flexible sorting.
- **Filter**: Filter batches by network scheme, comma-separated BIN prefixes, and minimum expiry year with real-time input/output diff telemetry.

### 5. High-Traffic Network Resilience & Eco Mode
- **Shared OkHttp Pool**: Single pooled `OkHttpClient` (`ConnectionPool(16, 5m)`, `Dispatcher(64 max / 12 per host)`, 10 MiB disk cache, bounded 1 MiB response reader, transparent GZIP).
- **Resilience Primitives**: Bounded `TtlLruCache` (5m L1 verdicts + 7d Room `bin_cache`), `RequestDeduplicator` single-flight coalescing, `HostCircuitBreaker` (trips after 5 consecutive transient faults for a 20s cooldown), and `RetryAndBackoffPolicy` respecting `Retry-After` / Telegram `retry_after`.
- **Battery & Data Saver**: `WorkManager` `UNMETERED` / `CONNECTED` constraints, `RequiresBatteryNotLow`, automatic `<= 2x` concurrency throttling under OS Power Save or Background Data Saver, and snapshot file cleanup on worker completion.

### 6. Telegram Bot Webhook Alerts
- Sends Live-only or All-checked validation digests via `/sendMessage` using `BuildConfig.TELEGRAM_BOT_TOKEN` injected cleanly from **Secrets** (never hardcoded).
- Supports one-tap **Auto-Detect Chat** (`/getMe` + `/getUpdates`) and **Send Test Alert** inside **Settings → Gateway**.

---

## UI & Material 3 Expressive Polish

- **Material You Dynamic Color (Android 12+)** paired with curated **Obsidian-Emerald Dark** & **Crisp Light** tonal palettes (`surfaceContainerLowest` through `surfaceContainerHighest`).
- **Custom Adaptive Iconography**: Obsidian-emerald shield-chip emblem (`ic_launcher_foreground` in 66dp safe zone + `#0A131A` `ic_launcher_background` + Android 13+ monochrome themed layer + multi-density square and circular launcher PNGs).
- **Adaptive Responsive Scaffold**: Seamless 1-pane handheld layout and 2-pane tablet/foldable workspace (`NavigationBar` / `ModalNavigationDrawer` -> `PermanentNavigationDrawer`).
- **Lightweight Icon Bundle**: Custom curated `AppIcons` vector paths replacing `material-icons-extended` (~28 MB saved).

---

## Project Structure

```text
app/src/main/java/com/example/
├── BinCheckApplication.kt                     # App initialization & WorkManager/Resilience hooks
├── MainActivity.kt                            # Edge-to-edge host, adaptive system bars & splash coordinator
├── analytics/
│   └── FirebaseMetrics.kt                     # Safe Firebase Analytics, Crashlytics & Perf telemetry wrapper
├── data/
│   ├── ValidatorPreferencesRepository.kt      # DataStore preferences (engine, HTTP, Telegram, theme, profiles)
│   └── local/
│       ├── AppDatabase.kt                     # Room v2 SQLite DB (WAL mode + MIGRATION_1_2)
│       ├── BatchQueueItemEntity.kt            # Chunked WorkManager input queue entity
│       ├── BinCacheDao.kt / BinCacheEntity.kt # 7-day persistent IIN/BIN metadata cache
│       ├── ValidationHistoryDao.kt            # Indexed SQLite history, counters & incremental delta queries
│       ├── ValidationHistoryEntity.kt         # History record entity & filter enum
│       └── ValidationHistoryRepository.kt     # Chunked queueing (500/txn), 5k trim & LIMIT/OFFSET paging
├── domain/
│   ├── BinLookupService.kt                    # Multi-provider BIN resolver + L1/L2 cache
│   ├── CardValidatorEngine.kt                 # ISO/IEC 7812 scheme detection, Luhn solver & validation router
│   ├── ConfigBlockModels.kt                   # 9-category / 44+ block schemas, recipes & JSON serialization
│   ├── ConfigBlockExecutor.kt                 # Ordered multi-step block pipeline & onFalse branching engine
│   ├── CustomConfigStudioEngine.kt            # 2-Step tokens, 6-mode parser, 14-mode keycheck, cURL/Loli I/O
│   ├── CustomHttpClient.kt                    # Custom HTTP pipeline executor, GZIP request body & prober
│   ├── FileTextExtractor.kt                   # Streaming .txt/.csv/.json/.html card record extractor
│   ├── ListToolkitEngine.kt                   # Clean, dedupe, expiry prune, format & filter transformations
│   ├── NetworkClientProvider.kt               # Shared OkHttp pool, timeout clients, proxy authenticator & net info
│   ├── NetworkResilience.kt                   # TtlLruCache, RequestDeduplicator, CircuitBreaker & RetryPolicy
│   ├── SandboxGatewayClient.kt / SandboxTestRegistry.kt
│   ├── TelegramWebhookClient.kt               # Telegram Bot API client, chat auto-discovery & digest sender
│   └── ValidationApiProviders.kt              # Chkr.cc, Payate & Multi-API Failover adapters
├── ui/
│   ├── AppIcons.kt                            # Lightweight curated vector icons (~4 KB)
│   ├── AppNavigationDrawer.kt                 # Adaptive navigation drawer with live metrics & theme controls
│   ├── BinLookupBottomSheet.kt                # Real-time 6–8 digit IIN/BIN lookup sheet
│   ├── ConsoleComponents.kt                   # Line-numbered monospace console & M3 SplitButton controls
│   ├── CustomConfigBlocksTab.kt               # 9-category block palette, stack editor & dry-run trace viewer
│   ├── CustomConfigStudioBottomSheet.kt       # 6-tab Custom Config Studio modal host & state saver
│   ├── CustomConfigStudioDialogs.kt           # Universal request importer & custom token helper dialogs
│   ├── CustomConfigStudioTabs.kt              # Request, Parse, Keycheck, Proxy & Profiles studio tabs
│   ├── CustomHttpSectionCard.kt               # Quick HTTP preset card in Settings
│   ├── FileImportBottomSheet.kt               # File preview & filtered import dialog
│   ├── HistoryLogBottomSheet.kt               # Paginated SQLite history log with debounced search
│   ├── ListToolkitBottomSheet.kt              # 3-tab List & Format Toolkit sheet
│   ├── MetricsTelemetrySectionCard.kt         # Runtime metrics & telemetry summary card
│   ├── NetworkOptimizationSectionCard.kt      # High-traffic caching, circuit breaker & eco controls
│   ├── ResultsComponents.kt                   # Live / Die / Unknown segmented buckets & expandable hit cards
│   ├── SettingsBottomSheet.kt / SettingsSectionCards.kt
│   ├── SplashOverlay.kt                       # Memory-downsampled hardware-accelerated splash overlay
│   ├── StartupPermissionsHandler.kt           # API-guarded runtime notification permission handler
│   ├── TelegramWebhookSectionCard.kt          # Telegram bot setup, Chat ID auto-detect & test card
│   ├── TelemetryComponents.kt                 # Live BIN telemetry strip & @drkingbd channel pill
│   ├── ValidatorScreen.kt                     # Adaptive 1-pane / 2-pane scaffold
│   ├── ValidatorViewModel.kt                  # MVVM state holder, debounced flows & delta WorkManager sync
│   └── theme/                                 # Color, Shape, Type & Dynamic Theme definitions
└── worker/
    └── BatchValidationWorker.kt               # CoroutineWorker w/ Semaphore concurrency, snapshot isolation & digests
```

---

## Configuration & Secrets

BinCheck reads optional build-time credentials via the Secrets Gradle Plugin (`.env` falling back to `.env.example`) and exposes them safely through `BuildConfig`:

1. Set secrets in **AI Studio Secrets** (or a local `.env` ignored by git — never commit secrets or create `local.properties`):
   ```properties
   TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
   GEMINI_API_KEY=
   ```
2. Open **Studio Settings → Gateway → Telegram Webhook**, tap **Auto-Detect Chat** after messaging `/start` to your bot (or enter a numeric Chat ID / `@channelusername`), and tap **Send Test Alert**.

---

## Building, Linting & Testing

> Use `gradle` directly in this environment (`./gradlew` for standard local/CI shells).

- **Build Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Android Lint (minSdk 24–36 verification)**:
  ```bash
  gradle :app:lintDebug
  ```
- **Run Full Unit & Robolectric UI Test Suite (78 tests)**:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## CI/CD Release Automation

GitHub Actions (`.github/workflows/release.yml`) automates verification and release publishing:
- Runs `testDebugUnitTest` and `lintDebug` before packaging.
- Builds signed Release `.apk` and `.aab` bundles with R8 code shrinking, resource shrinking, and `SHA256SUMS.txt` checksums.
- Supports repository signing secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) with deterministic CI fallback generation and publishes directly to GitHub Releases on `v*` tags or manual `workflow_dispatch`.

---

<div align="center">

Built for speed, precision, and clean Android engineering • Follow updates at **[@drkingbd](https://t.me/drkingbd)**

</div>
