# BinCheck (`com.aistudio.bincheck.vqxkpm`)

**BinCheck** is a production-grade Android batch card/token format validator, mathematical `x`-wildcard Luhn Mod-10 solver, real-time IIN/BIN inspector, list sanitization studio, and multi-gateway HTTP/Telegram webhook verifier built with **Kotlin**, **Jetpack Compose (Material 3 Expressive + Dynamic Color)**, **WorkManager**, **DataStore**, **Room (WAL SQLite)**, and **OkHttp**.

- **Developer Channel**: [@drkingbd](https://t.me/drkingbd) (`https://t.me/drkingbd`)
- **Min / Target / Compile SDK**: `24` / `36` / `36`

---

## Core System Logic & Placement Branching

Stripping away the high-fidelity Material 3 Expressive UI, the low-fidelity block diagram below maps BinCheck's core validation pipeline, showing how raw input values are normalized, mathematically solved, and branched into **Standard Local Placements** vs. **Remote Gateway / Merchandising Placements**:

```text
+-----------------------------------------------------------------------------------+
| 1. RAW INPUT INGESTION                                                            |
|    Console Editor  |  File Import (.txt/.csv/.json/.html)  |  List & Format Studio |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 2. TOKENIZER & WILDCARD SOLVER (CardValidatorEngine)                              |
|    Normalize Delimiters ( |  /  :  ; )  -->  Extract [PAN] [MM] [YYYY] [CVV]      |
|                                                                                   |
|    Contains 'x'/'X' mask? (e.g. 453225005536xxx3|08|2028|113)                     |
|      +-- Auto-Solve ON  --> Solve exact Mod-10 check digit at last 'x' index      |
|      +-- Auto-Solve OFF --> Branch immediately to [UNKNOWN] bucket                |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 3. DETERMINISTIC PRE-FLIGHT GATE (Zero-Network Fast Path)                         |
|    [a] Numeric Field Check      --> Non-digit token?       ==> [UNKNOWN]          |
|    [b] ISO/IEC 7812 BIN & Len   --> Invalid prefix/length? ==> [DIE]              |
|    [c] Luhn Mod-10 Checksum     --> Sum % 10 != 0?         ==> [DIE]              |
|    [d] System Clock Expiry Gate --> Expired MM/YYYY?       ==> [DIE]              |
|    [e] Network CVV Length       --> AMEX(4) vs Others(3)?  ==> [DIE]              |
+-----------------------------------------------------------------------------------+
                                         |
                          (Passes All Pre-Flight Checks)
                                         |
             +---------------------------+---------------------------+
             |                                                       |
             v (Standard Local Placement)                            v (Merchandising / Gateway Placement)
+-----------------------------------------+   +-----------------------------------------------------+
| 4A. STANDARD DETERMINISTIC PLACEMENT    |   | 4B. REMOTE GATEWAY & MERCHANDISING PLACEMENT        |
|     Mode: LOCAL_DETERMINISTIC (LUHN)    |   |     Modes:                                          |
|                                         |   |       • CHKR_CC        (10s fixed rate-limit delay) |
|     • Zero network latency (~1 ms)      |   |       • PAYATE_MOCK    (3s fixed rate-limit delay)  |
|     • Runs up to 8x worker concurrency  |   |       • MULTI_API_AUTO (5s delay: Chkr -> Payate)   |
|     • Immediate verdict classification  |   |       • REMOTE_WEBHOOK (Custom HTTP + GZIP payload) |
|                                         |   |                                                     |
|                                         |   |     Resilience Layer (NetworkResilience):           |
|                                         |   |       1. TtlLruCache (5m verdict / 7d Room BIN)     |
|                                         |   |       2. RequestDeduplicator (single-flight)        |
|                                         |   |       3. HostCircuitBreaker (5 fails -> 20s open)   |
|                                         |   |       4. RetryAndBackoffPolicy (Retry-After + exp)  |
+-----------------------------------------+   +-----------------------------------------------------+
             |                                                       |
             +---------------------------+---------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 5. VERDICT CLASSIFICATION & PERSISTENCE                                           |
|                                                                                   |
|    +------------------+       +------------------+       +-------------------+    |
|    |   [LIVE] Bucket  |       |   [DIE] Bucket   |       | [UNKNOWN] Bucket  |    |
|    +------------------+       +------------------+       +-------------------+    |
|             |                          |                           |              |
|             +--------------------------+---------------------------+              |
|                                        |                                          |
|    • Incremental Delta Flush --> Room SQLite WAL (`validation_history` + paging)  |
|    • Live BIN Enrichment     --> Multi-provider IIN lookup (`bin_cache` 7d TTL)   |
|    • Telegram Bot Webhook    --> Batched Hit Digest (`/sendMessage` w/ backoff)   |
+-----------------------------------------------------------------------------------+
```

---

## Key Features

### 1. Mathematical Luhn Mod-10 Wildcard Solver & Generator
- Parses multi-line entries delimited by `|`, `/`, `:`, or `;` (`PAN|MM|YYYY|CVV` or `PAN|MM|YY|CVV`).
- Solves `x` or `X` wildcards at **any** position in the PAN (even when the final digit is fixed, e.g., `453225005536xxx3|08|2028|113`) by computing the exact replacement digit (`0..9`) that satisfies the ISO/IEC 7812 Luhn Mod-10 equation.
- Supports `xx` month, `xxxx` future year, and network-aware CVV length (`4` digits for AMEX, `3` digits for Visa, Mastercard, Discover, JCB, Diners, UnionPay, Maestro).

### 2. Multi-Engine Validation & Enforced Rate-Limit Pacing
- **Local Deterministic (`LUHN`)**: Offline structural, IIN/BIN prefix, length, expiry, CVV, and Luhn Mod-10 verification with configurable `0ms–30s` pacing and `1x–8x` concurrency.
- **Chkr.cc (`CHKR.CC`)**: Live gateway check against `https://api.chkr.cc/` with an enforced **10-second** inter-request pacing delay and embedded BIN metadata caching.
- **Payate (`PAYATE`)**: Gateway check against `https://mock.payate.com/api.php` with an enforced **3-second** inter-request pacing delay.
- **Multi-API Failover (`MULTI API`)**: Queries `Chkr.cc` first and automatically fails over to `Payate` on transient/unknown responses with an enforced **5-second** delay.
- **Custom HTTP (`CUSTOM HTTP`)**: Configurable `GET`, `POST JSON`, `POST Form`, or `AUTO` requests with one-tap payload presets (`JSON Card`, `Lista JSON`, `Form Body`), variable interpolation (`{lista}`, `{card}`, `{mm}`, `{yy}`, `{yyyy}`, `{cvv}`, `{scheme}`, `{bin}`), custom headers, custom Live/Die keyword matchers, and optional `Content-Encoding: gzip` request body compression.

### 3. List & Format Toolkit Studio (`ListToolkitEngine`)
- **Clean Tab**: Strip messy HTML/JSON/logs to extract raw card rows, deduplicate by 12–19 digit PAN or exact normalized line, prune expired `MM/YYYY` rows, drop invalid Luhn checksums, and solve `x` wildcards in place.
- **Format Tab**: Convert between `PAN|MM|YYYY|CVV`, `PAN|MM|YY|CVV`, `PAN:MM:YYYY:CVV`, `PAN/MM/YY/CVV`, CSV (`pan,month,year,cvv`), `PAN Only`, or `Unique 6-Digit BINs`, plus sorting (`Keep Order`, `By PAN / BIN`, `By Scheme`, `By Expiry Date`).
- **Filter Tab**: Filter lists by card scheme (`VISA`, `MASTERCARD`, `AMEX`, `DISCOVER`, `JCB`, `DINERS`, `UNIONPAY`, `MAESTRO`), BIN prefix comma list (`4532, 5412`), and minimum expiry year with real-time input/output diff telemetry.

### 4. High-Traffic Network Resilience & Battery/Data Saver
- **Shared Connection Pool & Disk Cache**: Single `OkHttpClient` with `ConnectionPool(16, 5m)`, `Dispatcher(maxRequests = 64, maxRequestsPerHost = 12)`, cached timeout-scoped clients, bounded 1 MiB response reader, and 10 MiB HTTP disk cache.
- **Single-Flight Coalescing & Bounded LRU Caching**: `RequestDeduplicator` prevents duplicate concurrent calls for identical keys; `TtlLruCache` caches verdicts and BIN lookups in memory alongside a 7-day Room `bin_cache` table.
- **Host Circuit Breaker & Exponential Backoff**: `HostCircuitBreaker` (`CLOSED -> OPEN -> HALF_OPEN`) trips after 5 consecutive transient failures for a 20s cooldown to prevent server overload and battery drain; `RetryAndBackoffPolicy` honors HTTP `Retry-After` headers and Telegram `retry_after` JSON fields with jittered exponential backoff.
- **Bounded Worker Pool & Incremental Delta Sync**: `BatchValidationWorker` processes items with a fixed `1x–8x` coroutine worker pool, flushes SQLite batches incrementally, and streams delta row IDs to `ValidatorViewModel` (`getBatchResultsAfterId`) without re-querying the full history table.

### 5. Telegram Bot Webhook Integration (`TelegramWebhookClient`)
- Dispatches real-time or batched hit digests via Telegram Bot API (`/sendMessage`) using `BuildConfig.TELEGRAM_BOT_TOKEN`.
- Includes one-tap **Auto-Detect Chat** (`/getMe` + `/getUpdates`) and **Send Test Alert** verification directly in **Studio Settings → Gateway**.

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                      # Edge-to-edge host, adaptive system bars, splash & permission orchestration
├── data/
│   ├── ValidatorPreferencesRepository.kt # DataStore preferences for engine, HTTP, Telegram, theme & eco settings
│   └── local/
│       ├── AppDatabase.kt               # Room v2 database (WRITE_AHEAD_LOGGING + MIGRATION_1_2)
│       ├── BinCacheDao.kt               # 7-day persistent IIN/BIN metadata cache
│       ├── ValidationHistoryDao.kt      # Indexed SQLite history, summary counters & incremental batch delta queries
│       ├── ValidationHistoryEntity.kt   # History entity & filter enum
│       └── ValidationHistoryRepository.kt # Chunked inserts (500/txn), 5,000-row retention trim & pagination
├── domain/
│   ├── BinLookupService.kt              # Multi-provider BIN lookup (handyapi, binlist, openpublic) + L1/L2 cache
│   ├── CardValidatorEngine.kt           # ISO/IEC 7812 scheme detection, Luhn Mod-10 solver & engine router
│   ├── CustomHttpClient.kt              # Custom HTTP template interpolation, GZIP request body & endpoint prober
│   ├── FileTextExtractor.kt             # Streaming .txt/.csv/.json/.html parser with regex card extraction
│   ├── ListToolkitEngine.kt             # List cleaning, deduplication, expiry pruning, formatting & filtering
│   ├── NetworkClientProvider.kt         # Shared OkHttp pool, timeout client cache, GZIP & network status inspector
│   ├── NetworkResilience.kt             # TtlLruCache, RequestDeduplicator, HostCircuitBreaker & RetryAndBackoffPolicy
│   ├── TelegramWebhookClient.kt         # Telegram Bot API client, chat auto-discovery & digest formatter
│   └── ValidationApiProviders.kt        # Chkr.cc, Payate & Multi-API Failover adapters
├── ui/
│   ├── AppIcons.kt                      # Lightweight custom vector icons (~4 KB, zero material-icons-extended bloat)
│   ├── AppPermissionHelper.kt           # Runtime permission inspection & startup prompt effect
│   ├── BinLookupBottomSheet.kt          # Real-time 6–8 digit BIN inspector bottom sheet
│   ├── ConsoleComponents.kt             # Monospace line-numbered batch console & Material 3 SplitButton controls
│   ├── FileImportBottomSheet.kt         # File picker preview & delimited record filter sheet
│   ├── HistoryLogBottomSheet.kt         # Paginated SQLite history viewer with debounced search & filter chips
│   ├── ListToolkitBottomSheet.kt        # 3-tab List & Format Toolkit Studio bottom sheet
│   ├── NavigationComponents.kt          # Adaptive ModalNavigationDrawer / PermanentNavigationDrawer & TopBar
│   ├── ResultsComponents.kt             # Live / Die / Unknown segmented buckets & expandable result cards
│   ├── SettingsComponents.kt            # 3-tab Studio Settings (Engine, Gateway, System)
│   ├── SplashOverlay.kt                 # Hardware-accelerated, memory-downsampled startup splash overlay
│   ├── TelemetryComponents.kt           # Live BIN telemetry strip & developer channel pill
│   ├── ValidatorScreen.kt               # Adaptive 1-pane / 2-pane responsive scaffold
│   ├── ValidatorViewModel.kt            # MVVM state holder, debounced streams & incremental WorkManager sync
│   └── theme/                           # Material 3 Expressive typography, shapes, dynamic & fallback color schemes
└── worker/
    └── BatchValidationWorker.kt         # CoroutineWorker with bounded worker pool, rate-limit pacing & Telegram digest
```

---

## Configuration & Secrets

BinCheck reads optional secrets at build time from `.env` (falling back to `.env.example` if `.env` is absent) and exposes them via `BuildConfig`:

1. Copy or configure `.env` (or set keys in the **AI Studio Secrets panel**):
   ```properties
   TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
   GEMINI_API_KEY=
   ```
2. Open **Studio Settings → Gateway → Telegram Webhook**, tap **Auto-Detect Chat** (after sending `/start` to your bot) or enter your numeric Chat ID / `@channelusername`, and tap **Send Test Alert**.

---

## Building & Running Tests

- **Assemble Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## CI/CD & Release Workflow

The repository includes an automated GitHub Actions release pipeline at `.github/workflows/release.yml`:
- Triggers on version tags (`v*`) or manual `workflow_dispatch`.
- Configures JDK 21, Android SDK, and Gradle, runs unit tests, builds signed release `.apk` and `.aab` bundles with `SHA256SUMS.txt`, and publishes artifacts to GitHub Releases.
