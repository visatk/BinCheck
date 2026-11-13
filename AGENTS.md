# AGENTS.md — Project Tracker & Architecture

## Overview
- **App Name**: BinCheck (`com.aistudio.bincheck.vqxkpm`)
- **Developer Channel**: `https://t.me/drkingbd` (`@drkingbd`)
- **Target / Compile SDK**: Android 36 (minSdk 24), Edge-to-Edge enabled (`SystemBarStyle.dark`/`light` adaptive), Jetpack Compose + Material 3 Expressive with Material You Dynamic Color.
- **Core Purpose**: Production-grade batch pipe-delimited card/token format validator (`NUMBER|MM|YYYY|CVV`), mathematical `x` wildcard Luhn-compliant generator, real-time IIN/BIN scheme inspector, and optional HTTP Webhook/Gateway batch verifier.

## UI & Visual Architecture (Material 3 Expressive + Dynamic Color)
- **Material 3 Theme System (`Color.kt`, `Shape.kt`, `Type.kt`, `Theme.kt`)**:
  - **Dynamic Color (Android 12+)**: Uses `dynamicDarkColorScheme` and `dynamicLightColorScheme` with curated obsidian-emerald dark & crisp light tonal surface container fallbacks (`surfaceContainerLowest` through `surfaceContainerHighest`) for Android < 12 or when toggled off.
  - **Appearance Preferences**: User-selectable `System` / `Dark` / `Light` theme mode (`SingleChoiceSegmentedButtonRow`) and Material You Dynamic Color `Switch` in Settings, persisted via Jetpack `DataStore`.
  - **Semantic Status Provider (`LocalConsoleColors`)**: Harmonized high-contrast accents for `Live`, `Die`, `Unknown`, and `@drkingbd` Telegram brand surfaces across both dark and light themes.
  - **Top Telemetry & Developer Bar (`TelemetryComponents.kt`)**: Displays real-time engine mode badge (`LUHN` / `WEBHOOK`), live BIN/IIN brand detection chip, Room History counter pill, and interactive **Developer Channel (`@drkingbd`)** pill linking directly to `https://t.me/drkingbd`.
  - **Elevated Main Console Card (`ConsoleComponents.kt`)**:
    - **Recessed IDE-Style Monospace Console (`surfaceContainerLowest`)**: Dynamic line-number gutter (`01`, `02`...), `JetBrains Mono` typography, exact `453225005536xxx3|08|2028|113` placeholder hint, animated focus border, and quick-action M3 tonal pills (`File`, `Gen 10x`, `Solve x`, `Paste`, `Clear`).
    - **Zero-Permission File Picker (`FileImportBottomSheet.kt` + `FileTextExtractor.kt`)**: Uses Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) to select `.txt` and `.html`/`.htm` files from device storage, strips HTML tags/scripts/entities, previews extracted lines, and supports Append/Replace + Smart Delimited Record filtering.
    - **Adaptive Responsive Layout (`BoxWithConstraints` + M3 `Scaffold` + `SnackbarHost`)**: Switches dynamically between a centered single-column layout on compact portrait screens and a side-by-side two-pane layout (`Console` left / `Results` right) on landscape, foldables, and tablets (`maxWidth >= 680.dp`).
    - **Dual Full-Width M3 Action Bars (`surfaceContainerHigh`)**:
      1. **Run / Stop Bar**: Crisp outlined Play Triangle (`colorScheme.primary`) with Material ripple feedback when idle; live `LinearProgressIndicator` + processed counter + Stop square when active.
      2. **Settings / Tools Bar**: Crisp outlined Gear (`colorScheme.secondary`) opening the Pattern Generator, Appearance & Gateway Configuration sheet.
  - **Bottom 3D Results Card (`ResultsComponents.kt`)**:
    - **3-Tab Header**: `Live`, `Die`, `Unknown` with tonal container elevation (`surfaceContainerHighest`), top semantic accent stripe, and animated count badges.
    - **Results Viewport**: Expandable list of validated records with scheme badge, diagnostic breakdown, latency (`ms`), M3 `FilledTonalButton` Copy All, Clear, and single-row copy/remove actions.
  - **History Log (`HistoryLogBottomSheet.kt`)**: M3 `FilterChip` filters (`All`, `Success`, `Failure`), live search, and local Room audit trail.
  - **Developer Channel Footer & Settings Integration**:
    - Direct one-tap intent launcher (`Intent.ACTION_VIEW`) for `https://t.me/drkingbd` on the main screen footer and inside the Settings bottom sheet.

## Validation & Processing Engine (100% Production Logic, Zero Mock Data)
1. **Parser & Mathematical Luhn Wildcard Solver**:
   - Parses multi-line pipe/slash/colon separated entries: `PAN|MM|YY(YY)|CVV`.
   - Mathematically solves `x` or `X` wildcards at any position in the PAN (e.g., `453225005536xxx3|08|2028|113`) so the generated 16-digit PAN strictly satisfies the ISO/IEC 7812 Luhn Mod-10 checksum.
2. **Deterministic Local Engine**:
   - **Live**: Passes Luhn Mod-10 checksum, valid network IIN/BIN prefix & length (Visa, Mastercard, Amex, Discover, JCB, Diners, UnionPay, Maestro), non-expired `MM|YYYY` date against current system clock, and network-accurate CVV length.
   - **Die**: Fails Luhn checksum, invalid PAN length for network, expired `MM|YYYY`, or invalid CVV digits.
   - **Unknown**: Incomplete format, unexpanded wildcard in strict mode, or HTTP timeout/unreachable in Webhook mode.
3. **Optional Custom HTTP Endpoint Mode**:
   - Uses `OkHttpClient` to send real HTTP requests (`POST` JSON or `GET` query) to a user-configured endpoint URL.
4. **Concurrent Batch Worker (`BatchValidationWorker`)**:
   - Uses `WorkManager` (`CoroutineWorker`) with bounded `Semaphore` concurrency (`1x`–`8x`, configurable in Settings) and `async`/`awaitAll` to validate uploaded `.txt`/`.html` or console entries simultaneously against the configured HTTP Webhook/API endpoint or local engine, streaming progress via `setProgress`.
5. **Local Persistence (`DataStore` + `Room` SQLite)**:
   - Uses Jetpack `DataStore` (`preferences`) to persist validation settings, concurrency, custom endpoint configuration, batch delay, and pattern templates.
   - Uses `Room` (`AppDatabase`, `ValidationHistoryDao`, `ValidationHistoryRepository`) to queue large batches safely and persist a local **Validation History Log** viewable and filterable by `All`, `Success`, and `Failure` status (`HistoryLogBottomSheet.kt`).
6. **CI/CD & GitHub Release Automation (`.github/workflows/release.yml`)**:
   - Triggers automatically on version tags (`v*`) or manually via `workflow_dispatch`.
   - Sets up JDK 21, Android SDK, and Gradle 9.3.1, prepares signing keystores (supporting repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` with automatic fallback generation), runs unit tests, builds signed release APK & AAB artifacts with `SHA256SUMS.txt`, and publishes directly to GitHub Releases.
