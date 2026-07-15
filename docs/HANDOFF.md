# Handoff — uwb-peer-compass

## What this is
A peer-to-peer directional compass ("Find My" between two phones running the same app),
with a privacy-first rendezvous backend and two native apps that range directly over
UWB → BLE → GPS depending on hardware.

## Status by phase (see docs/PROGRESS.md for detail)

| Phase | Area | Status |
|-------|------|--------|
| 0 | Monorepo, tooling, CI, ADRs | ✅ done |
| 1 | Backend (auth, contacts, presence, session state machine, negotiation, token exchange) | ✅ done, **45 tests green** |
| 2 | Shared protocol (JSON Schema + Mermaid diagrams) | ✅ done, schema tested |
| 3 | Android (Compose UI, providers, fusion) | ✅ core **26 tests green**; app SDK-gated |
| 4 | iOS (SwiftUI, providers, fusion) | ✅ code + XCTests written; build needs macOS |
| 5 | Fallback & runtime degradation | ✅ server + both clients + mock scenario tests |
| 6 | Hardening (rate limit, token expiry) + docs | ✅ done |

## What runs here (Linux CI), verified
- **Backend:** `cd backend && npm install && npx prisma generate && npm run build &&
  npm run lint && npm test` → 45 passing tests incl. a two-client end-to-end WS handshake,
  decline, downgrade, and disconnect. OpenAPI at `backend/openapi.json`.
- **Android core:** `cd android/core && gradle test` → 26 passing JUnit tests (fusion, mock
  trajectories/noise/dropout, RSSI model, degradation, state machine).

## What REQUIRES physical hardware / specific OS (cannot be built here)
- **Android app** (`android/app`): needs the Android SDK. `cd android && ./gradlew
  assembleDebug testDebugUnitTest` on a machine with the SDK. See docs/android-build.md.
- **iOS** (everything): needs macOS + Xcode 15. `cd ios && swift test` for the core package;
  full app via an Xcode project per docs/ios-build.md. Build on **two U1/U2 devices**.
- **Any real UWB/BLE ranging:** must be validated on the device matrix in
  docs/device-testing.md (search the code for `VERIFY-ON-DEVICE`).

## How to run the whole thing (developer machine)
1. `cd backend && cp .env.example .env && docker compose up -d && npm install &&
   npm run db:migrate && npm run dev` — backend on `:3000`.
2. Android: open `android/` in Android Studio, point `BACKEND_HTTP/WS` at your host,
   run on a UWB device. Repeat on a second device.
3. iOS: create the Xcode project (docs/ios-build.md), add the `UWBCompassCore` package and
   `ios/App` sources, run on two U1/U2 iPhones.
4. Register two accounts, add each other as contacts, invite → accept → compass.

## Architecture in one paragraph
Two native apps share a versioned JSON protocol (`/shared`) and a Fastify rendezvous
backend. The backend brokers consent + relays **opaque** discovery tokens but never sees
measurements (ADR-0002). Each platform implements the same `RangingProvider` abstraction
with UWB/BLE/Mock backends (ADR-0004); all UI/session/fusion logic is tested against the
mock. Technology is negotiated UWB→BLE→GPS, UWB intra-platform only (ADR-0003/0008), with
one-way runtime downgrade.

## Recommended next steps (prioritised)
1. **iOS Xcode project + CI:** commit an `.xcodeproj`/`project.yml` (XcodeGen) so `xcodebuild`
   runs in the macOS CI job, not just `swift build`.
2. **On-device UWB bring-up:** implement and validate the `VERIFY-ON-DEVICE` seams
   (NISession token exchange; Jetpack controller/controlee params). This is the main
   remaining unknown.
3. **WS auth hardening:** replace the `?token=` query param with a one-time connect ticket
   (docs/security.md).
4. **GDPR self-service:** add account export + delete endpoints (schema already cascades).
5. **Group mode:** flesh out the stub behind the feature flag (server fan-out + UI).
6. **Scale-out:** move presence + rate limiting to Redis; make the session manager
   horizontally shardable (sticky by session id) if a single instance is outgrown.
7. **Observability:** structured logs (no token/PII), metrics on session funnel
   (invite→accept→active→end) without recording any measurement.

## Key files to read first
- `shared/protocol.md` — the contract.
- `backend/src/session/manager.ts` — the rendezvous heart.
- `android/core/src/main/kotlin/com/uwbcompass/core/` and
  `ios/Sources/UWBCompassCore/` — the shared, tested domain logic (mirrors of each other).
- `docs/DECISIONS.md` — why things are the way they are.
