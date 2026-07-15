# Progress log

Legend: ✅ done · 🚧 partial · ⬜ not started · 🔒 blocked (needs hardware/macOS)

## Phase 0 — Bootstrap monorepo & tooling ✅
- ✅ Monorepo layout (`backend`, `android`, `ios`, `shared`, `docs`, `scripts`)
- ✅ Root README with build instructions for all three parts
- ✅ `docs/DECISIONS.md` initial ADRs (9 ADRs)
- ✅ Root `.gitignore`
- ✅ CI GitHub Actions (backend test+lint, Android build+test, iOS lint/build on macOS)
- ✅ Tooling configs (ESLint/Prettier for backend; ktlint/detekt for Android; SwiftLint for iOS)

## Phase 1 — Backend ✅ (45 tests passing)
- ✅ Auth (register/login/refresh, Argon2id, JWT access + rotating refresh)
- ✅ Contacts (add by username / QR-encoded username, `/me`, presence flag)
- ✅ Presence via WS heartbeat + watchdog + contact fan-out
- ✅ Session rendezvous state machine + timeouts (invite/setup/active)
- ✅ Technology negotiation (UWB>BLE>GPS, intra-platform UWB) + opaque token exchange
- ✅ Integration test: two WS clients complete a full handshake end-to-end
- ✅ Docker Compose (Postgres 16)
- ✅ OpenAPI generation (`npm run openapi:gen`, served at `/docs`)
- ✅ Auth rate limiting (fixed-window, Phase 6 bonus)
- ✅ Repository abstraction (Prisma impl + in-memory impl) → DB-free tests

## Phase 2 — Shared protocol ✅
- ✅ JSON Schema for all WS messages (`shared/schemas/messages.schema.json`) + version file
- ✅ `protocol.md` with Mermaid state + sequence diagrams incl. error cases
       (decline, timeout, disconnect, downgrade, no-common-tech)
- ✅ Schema validated in backend test-suite (source of truth)

## Phase 3 — Android ✅ (core: 24 tests passing; app: SDK-gated)
- ✅ Compose UI (login, peer list w/ presence, incoming-invite consent dialog,
       permission rationale, adaptive compass screen)
- ✅ WS client (`RendezvousClient`, OkHttp) + Kotlin protocol models
- ✅ `RangingProvider` abstraction + UWB (Jetpack) / BLE (RSSI) / Mock implementations
- ✅ Sensor fusion (`CompassFusion` world-anchored, rotation-vector `AndroidHeadingSource`)
- ✅ `DegradationPolicy` (runtime downgrade recommendation) + `SessionStateMachine` mirror
- ✅ Unit tests in `core` (fusion, mock trajectories/noise/dropout, RSSI model,
       degradation, state machine) — **built & green with system Gradle here**
- 🔒 `./gradlew assembleDebug` requires the Android SDK (documented in docs/android-build.md);
       GitHub Actions android job runs it. UWB paths marked `// VERIFY-ON-DEVICE`.

## Phase 4 — iOS ✅ (code + tests written; build requires macOS/Swift)
- ✅ `UWBCompassCore` SwiftPM package: RangingProvider, MockRangingProvider (AsyncStream,
       deterministic SplitMix64 PRNG, dropout windows), CompassFusion, DegradationPolicy,
       SessionStateMachine, BleDistanceModel — exact mirror of android/core.
- ✅ XCTest suite mirroring the Android tests (fusion, mock, RSSI, degradation, state machine).
- ✅ SwiftUI app: LoginView, PeerListView (presence + incoming-invite consent alert),
       adaptive CompassView (precise arrow for UWB, hot/cold ring for BLE/GPS).
- ✅ NIRangingProvider (NearbyInteraction, ARKit EDM flag), BleRangingProvider (CoreBluetooth),
       HeadingSource (CoreLocation), RendezvousClient (URLSessionWebSocketTask).
- ✅ Info.plist with NSNearbyInteraction / Bluetooth / Location usage strings; SwiftLint config.
- 🔒 Cannot compile here (no Swift toolchain / Xcode). Steps in docs/ios-build.md;
       UWB paths marked VERIFY-ON-DEVICE. GitHub Actions ios job runs swift build on macOS.

## Phase 5 — Fallback & degradation ✅
- ✅ Server technology selection (`negotiate`) + legal runtime downgrade (`isLegalDowngrade`)
- ✅ Client selection + downgrade recommendation (`DegradationPolicy`, both platforms)
- ✅ Adaptive UI (arrow vs proximity ring) + quality indicators (both platforms)
- ✅ Degradation scenario tests with the mock (Android core + iOS core + backend integration)

## Phase 6 — Hardening ✅
- ✅ Auth rate limiting (fixed-window per-IP) + tested
- ✅ Token expiry + refresh rotation/revocation (Phase 1) 
- ✅ `docs/security.md` (threat model, controls, residual risks)
- ✅ `docs/privacy.md` (GDPR: lawful basis, zero measurement retention, consent)
- ✅ `docs/device-testing.md` (device matrix + manual checklist + VERIFY-ON-DEVICE list)
- ✅ `docs/HANDOFF.md` (status, run steps, hardware requirements, next steps)
- ✅ OpenAPI spec generated (`backend/openapi.json`)
