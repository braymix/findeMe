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

## Phase 3 — Android
- ⬜ Compose UI (login, peer list, session request/accept, compass)
- ⬜ WS client
- ⬜ `RangingProvider` (UWB / BLE / Mock)
- ⬜ Sensor fusion (rotation vector)
- ⬜ Unit tests (fusion + state machine + mock)

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
