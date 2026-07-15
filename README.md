# uwb-peer-compass

A peer-to-peer "Find My"-style directional compass between two smartphones running
the same app. It shows an **arrow + distance** pointing at another phone, using the
most precise ranging technology the hardware pair supports:

**UWB** (Ultra-Wideband, precise arrow + distance) → **BLE RSSI** (approximate
proximity) → **GPS** (outdoor last resort).

The backend is a pure *rendezvous* service: it brokers consent and exchanges the
out-of-band discovery tokens/session parameters, but **never** receives any
distance, direction, or position data. Ranging happens directly device-to-device.

## Repository layout

| Path        | Contents                                                             |
|-------------|---------------------------------------------------------------------|
| `/backend`  | Fastify + WebSocket + Prisma (PostgreSQL) rendezvous server.         |
| `/ios`      | SwiftUI app using `NearbyInteraction` (+ optional ARKit EDM).        |
| `/android`  | Jetpack Compose app using `androidx.core.uwb`.                       |
| `/shared`   | Versioned wire protocol (JSON Schemas + `protocol.md`).             |
| `/docs`     | ADRs, decisions, privacy/security, device-testing matrix, handoff.  |
| `/scripts`  | Setup, lint and test runners.                                       |

## Hard constraints (read `/docs/DECISIONS.md` for the full list)

- **No consumer UWB across iOS↔Android.** Mixed pairs are automatically routed to
  the BLE/GPS fallback. UWB stays intra-platform (iOS↔iOS, Android↔Android).
- **UWB cannot be tested in CI or a simulator.** All UI and session logic is tested
  against a `MockRangingProvider`. Real UWB paths are marked `// VERIFY-ON-DEVICE`
  and listed in `docs/device-testing.md`.
- **Zero retention** of any measurement on the backend. Session tokens are ephemeral.

## Quick start

### Backend (fully runnable & tested here)

```bash
cd backend
cp .env.example .env
docker compose up -d          # Postgres
npm install
npm run db:migrate            # apply Prisma schema
npm run dev                   # http://localhost:3000, WS at /ws
npm test                      # unit + integration
npm run lint
```

OpenAPI is served at `http://localhost:3000/docs` and written to
`backend/openapi.json` via `npm run openapi:gen`.

### Android

```bash
cd android
./gradlew assembleDebug       # build
./gradlew testDebugUnitTest   # unit tests (fusion + state machine + mock provider)
./gradlew ktlintCheck detekt  # lint
```

### iOS

iOS **must be built on macOS with Xcode 15+** — it cannot be compiled in this Linux
environment. All source, tests and manual verification steps are in
[`docs/ios-build.md`](docs/ios-build.md).

## Documentation index

- [`docs/DECISIONS.md`](docs/DECISIONS.md) — architectural decisions & rationale.
- [`shared/protocol.md`](shared/protocol.md) — the wire protocol + sequence diagrams.
- [`docs/privacy.md`](docs/privacy.md) — GDPR / privacy-by-design.
- [`docs/security.md`](docs/security.md) — threat model & hardening.
- [`docs/device-testing.md`](docs/device-testing.md) — on-device manual test matrix.
- [`docs/HANDOFF.md`](docs/HANDOFF.md) — project status & next steps.
- [`docs/PROGRESS.md`](docs/PROGRESS.md) — per-phase progress log.
