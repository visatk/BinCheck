# AGENTS.md — Project Tracker & Architecture

## Overview
- **App Name**: BinCheck (`com.aistudio.bincheck.vqxkpm`)
- **Developer Channel**: `https://t.me/drkingbd` (`@drkingbd`)
- **Target / Compile SDK**: Android 36 (minSdk 24), Edge-to-Edge enabled (`SystemBarStyle.dark`/`light` adaptive), Jetpack Compose + Material 3 Expressive with Material You Dynamic Color.
- **Core Purpose**: Production-grade batch pipe-delimited card/token format validator (`NUMBER|MM|YYYY|CVV`), mathematical `x` wildcard Luhn-compliant generator, real-time IIN/BIN scheme inspector, and optional HTTP Webhook/Gateway batch verifier.

## UI & Visual Architecture (Material 3 Expressive + Dynamic Color)
- **Material 3 Theme System (`Color.kt`, `Shape.kt`, `Type.kt`, `Theme.kt`) & Lightweight Icons (`AppIcons.kt`)**:
  - **Lightweight Iconography**: Replaced the ~28 MB `material-icons-extended` dependency with `material-icons-core` + custom curated `AppIcons` vector paths (~4 KB total) to keep APK size minimal and builds fast.
  - **Dynamic Color (Android 12+)**: Uses `dynamicDarkColorScheme` and `dynamicLightColorScheme` with curated obsidian-emerald dark & crisp light tonal surface container fallbacks (`surfaceContainerLowest` through `surfaceContainerHighest`) for Android < 12 or when toggled off.
  - **Appearance Preferences**: User-selectable `System` / `Dark` / `Light` theme mode (`SingleChoiceSegmentedButtonRow`) and Material You Dynamic Color `Switch` in Settings & Drawer, persisted via Jetpack `DataStore`.
  - **Clean Low-Noise Workspace (`TelemetryComponents.kt`, `ConsoleComponents.kt`, `ResultsComponents.kt`)**: Pure M3 canvas without decorative blobs, hairline tonal borders, cohesive monochrome-plus-accent mini toolbar pills, borderless inline Live BIN badge, 3-tab Settings Studio (`Engine`, `Gateway`, `System`) with sticky action bar, and concise `@drkingbd` links in the telemetry strip, Drawer, and System tab.

## Validation & Processing Engine (100% Production Logic, Zero Mock Data)
1. **Parser & Mathematical Luhn Wildcard Solver**:
   - Parses multi-line pipe/slash/colon separated entries: `PAN|MM|YY(YY)|CVV`.
   - Mathematically solves `x` or `X` wildcards at any position in the PAN (e.g., `453225005536xxx3|08|2028|113`) so the generated 16-digit PAN strictly satisfies the ISO/IEC 7812 Luhn Mod-10 checksum.
2. **Deterministic Local Engine**:
   - **Live**: Passes Luhn Mod-10 checksum, valid network IIN/BIN prefix & length (Visa, Mastercard, Amex, Discover, JCB, Diners, UnionPay, Maestro), non-expired `MM|YYYY` date against current system clock, and network-accurate CVV length.
   - **Die**: Fails Luhn checksum, invalid PAN length for network, expired `MM|YYYY`, or invalid CVV digits.
   - **Unknown**: Incomplete format, unexpanded wildcard in strict mode, or HTTP timeout/unreachable in Webhook mode.
3. **Multiple Validation APIs & Custom HTTP Payload Builder**:
   - **External APIs with Enforced Rate-Limit Delays**: `Chkr.cc` (`10s` fixed delay), `Payate` (`3s` fixed delay), and `Multi-API Failover` (`5s` fixed delay) run sequentially (`1x`) to respect endpoint rate limits; `LUHN` and `Custom HTTP` support user-configurable delays (`0ms`–`10s` UI slider/presets) and `1x`–`8x` concurrency.
   - **Custom HTTP Payload Templates**: Supports one-tap `JSON Card`, `Lista JSON`, and `Form Body` presets plus `{lista}`, `{card}`, `{mm}`, `{yy}`, `{yyyy}`, `{cvv}`, `{scheme}`, and `{bin}` substitution in URL, headers, and request body (`HttpRequestMethod.AUTO` automatically selects `POST_JSON` when a payload template is present).
4. **Telegram Bot Webhook (`TelegramWebhookClient` + `TelegramWebhookSectionCard`)**:
   - Sends Live-only or All-checked validation alerts via the Telegram Bot API (`/sendMessage`) using `BuildConfig.TELEGRAM_BOT_TOKEN` injected from AI Studio **Secrets** (never hardcoded), with `/getMe` + `/getUpdates` Chat ID auto-detection and one-tap test alert verification.
5. **Concurrent Batch Worker (`BatchValidationWorker`)**:
   - Uses `WorkManager` (`CoroutineWorker`) with bounded `Semaphore` concurrency (`1x`–`8x`, configurable in Settings) and `async`/`awaitAll` to validate uploaded `.txt`/`.html` or console entries simultaneously against the configured API/engine and dispatch Telegram webhook alerts, streaming progress via `setProgress`.
6. **Local Persistence & SQLite Pagination (`DataStore` + `Room` v2 WAL)**:
   - Uses Jetpack `DataStore` (`preferences`) to persist validation settings, concurrency, custom HTTP payload & GZIP configuration, Telegram webhook Chat ID & filters, batch delay, pattern templates, and high-traffic toggles (`deduplicateBatchInputs`, `wifiOnlyBatch`, `batterySaverMode`).
   - Uses `Room` (`AppDatabase` v2 with `JournalMode.WRITE_AHEAD_LOGGING`, `MIGRATION_1_2` for `bin_cache`, `ValidationHistoryDao`, `ValidationHistoryRepository`) for 7-day persistent BIN caching, chunked batch queueing (`500`/txn), 5,000-row retention trimming, and SQLite `LIMIT`/`OFFSET` paginated history filtering (`DEFAULT_PAGE_SIZE = 40`).
7. **High-Traffic Network, Caching & Resilience (`NetworkClientProvider`, `NetworkResilience`)**:
   - **Shared HTTP Pool & Disk Cache**: Single `OkHttpClient` with `ConnectionPool(16, 5m)`, `Dispatcher(64/12)`, and 10 MiB disk `Cache` + transparent `gzip` response handling and optional `Content-Encoding: gzip` request compression via `GzipSink`.
   - **Caching, Debounce & Single-Flight Deduplication**: Bounded `TtlLruCache`, `RequestDeduplicator` (`CompletableDeferred` coalescing), debounced BIN auto-lookup (`400ms`) & history SQLite search (`220ms`), plus intra-batch line deduplication in `BatchValidationWorker`.
   - **Backoff & Circuit Breaker**: `RetryAndBackoffPolicy` (exponential backoff with jitter + HTTP `Retry-After` / Telegram `retry_after` parsing) and `HostCircuitBreaker` (`CLOSED -> OPEN -> HALF_OPEN`, 5 failures / 20s cooldown) for automatic fail-fast and Multi-API failover.
   - **Battery & Data Saver**: `WorkManager` `UNMETERED` / `CONNECTED` + `RequiresBatteryNotLow` constraints, `BackoffPolicy.EXPONENTIAL`, auto concurrency cap (`<= 2x`) on Power Save / Background Data Restriction, throttled `setProgress` updates, and Telegram hit digest batching.
8. **CI/CD & GitHub Release Automation (`.github/workflows/release.yml`)**:
   - Triggers automatically on version tags (`v*`) or manually via `workflow_dispatch`.
   - Sets up JDK 21, Android SDK, and Gradle 9.3.1, prepares signing keystores (supporting repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` with automatic fallback generation), runs unit tests, builds signed release APK & AAB artifacts with `SHA256SUMS.txt`, and publishes directly to GitHub Releases.
