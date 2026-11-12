# AGENTS.md — Project Tracker & Architecture

## Overview
- **App Name**: BinCheck (`com.aistudio.bincheck.vqxkpm`)
- **Developer Channel**: `https://t.me/drkingbd` (`@drkingbd`)
- **Target / Compile SDK**: Android 36 (minSdk 24), Edge-to-Edge enabled, Jetpack Compose + Material 3 Expressive Dark Console.
- **Core Purpose**: Production-grade batch pipe-delimited card/token format validator (`NUMBER|MM|YYYY|CVV`), mathematical `x` wildcard Luhn-compliant generator, real-time IIN/BIN scheme inspector, and optional HTTP Webhook/Gateway batch verifier.

## UI & Visual Architecture (Refined Dark Console Luxury)
- **Atmospheric Depth & Surface Hierarchy**:
  - Deep obsidian-slate canvas (`#14161B` to `#17191E`) with subtle radial ambient lighting (`Modifier.drawBehind`) and top specular border hairline (`#2C303A`).
  - **Top Telemetry & Developer Bar**: Displays real-time engine mode badge (`LUHN ENGINE` / `WEBHOOK`), live BIN/IIN brand detection chip (`VISA`, `MASTERCARD`, `AMEX`, etc.), and an interactive **Developer Channel (`@drkingbd`)** pill linking directly to `https://t.me/drkingbd`.
  - **Elevated Main Console Card (`#1D2026`)**:
    - **Recessed IDE-Style Monospace Console (`#111317`)**: Features a dynamic line-number gutter (`01`, `02`...), `JetBrains Mono` typography, exact `453225005536xxx3|08|2028|113` placeholder hint, animated focus border, and quick-action utility chips (`Gen 10x`, `Solve x`, `Paste`, `Clear`).
    - **Dual Full-Width Action Bars (`#1A1D23`)**:
      1. **Run / Stop Bar**: Crisp outlined Emerald Play Triangle (`#10B981`) with subtle glow halo when idle; live progress bar + processed counter + Stop square when active.
      2. **Settings / Tools Bar**: Crisp outlined Royal Blue Gear (`#2563EB`) with subtle glow halo to open the Pattern Generator & Gateway Configuration sheet.
  - **Bottom 3D Results Card (`#1D2026`)**:
    - **3-Tab Header**: `Live` (`#10B981` + animated badge), `Die` (`#E8ECF2` + animated badge), `Unknown` (`#E8ECF2` + animated badge), where the active tab sits on a raised 3D surface (`#22262F`) with a subtle top accent line and elevation shadow.
    - **Results Viewport**: Expandable list of validated records with scheme badge, diagnostic breakdown, latency (`ms`), Copy All, Copy Single Row, and Clear actions.
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
4. **Persistence**:
   - Uses Jetpack `DataStore` (`preferences`) to persist validation settings, custom endpoint configuration, batch delay, and pattern templates.
5. **CI/CD & GitHub Release Automation (`.github/workflows/release.yml`)**:
   - Triggers automatically on version tags (`v*`) or manually via `workflow_dispatch`.
   - Sets up JDK 21, Android SDK, and Gradle 9.3.1, prepares signing keystores (supporting repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` with automatic fallback generation), runs unit tests, builds signed release APK & AAB artifacts with `SHA256SUMS.txt`, and publishes directly to GitHub Releases.
