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

## Phase 4 — iOS
- 🔒 SwiftUI UI + NISession + ARKit flag + RangingProvider (NI/BLE/Mock) + CoreMotion
- 🔒 Unit tests on mock (build requires macOS)

## Phase 5 — Fallback & degradation
- ⬜ Client+server technology selection, runtime downgrade, adaptive UI, quality indicators
- ⬜ Degradation scenario tests (mock)

## Phase 6 — Hardening
- ⬜ Rate limiting, token expiry
- ⬜ `docs/security.md`, `docs/privacy.md`, `docs/device-testing.md`
- ⬜ `docs/HANDOFF.md`
