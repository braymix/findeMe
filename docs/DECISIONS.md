# Architectural Decisions (ADR log)

Format: lightweight ADRs. Each entry: context → decision → consequences.
Decisions taken autonomously (per the project's autonomy rules) are marked **[auto]**.

---

## ADR-0001 — Dual native apps + shared rendezvous backend

**Context.** Precise directional ranging (arrow + distance) requires platform-native
UWB APIs: Apple `NearbyInteraction` and Android Jetpack `androidx.core.uwb`. There is
no cross-platform UWB abstraction, and no React-Native/Flutter bridge that exposes the
directional (`.direction`, Angle-of-Arrival) data reliably.

**Decision.** Build two fully native apps (SwiftUI, Jetpack Compose) that share only a
versioned JSON wire protocol and a common backend. No shared UI layer.

**Consequences.** Duplicated UI/session logic across platforms, but full access to
native ranging precision. The `RangingProvider` abstraction is defined **per platform**
with identical semantics so the session/UI logic mirrors on both sides.

---

## ADR-0002 — Backend is a rendezvous broker only (zero measurement retention)

**Context.** Privacy-by-design and GDPR data-minimisation. Distance/direction/position
are highly sensitive.

**Decision.** The backend brokers: auth, contacts, presence, consent, technology
negotiation, and out-of-band exchange of discovery tokens/session parameters over TLS.
It **never** receives ranging measurements or positions. Ranging is strictly P2P.

**Consequences.** Simpler compliance surface; the backend cannot offer server-side
analytics on distance. Session records store only lifecycle state + ephemeral opaque
tokens, purged on session end. See `docs/privacy.md`.

---

## ADR-0003 — Technology negotiation order: UWB > BLE > GPS **[auto]**

**Context.** Not every device has UWB; mixed iOS↔Android pairs have no common UWB.

**Decision.** For a given pair, pick the best **common** technology:
1. `UWB` — only if both devices report UWB capability **and** are the same platform.
2. `BLE` — approximate proximity (RSSI), any platform, requires Bluetooth.
3. `GPS` — outdoor last resort, coarse bearing from two GPS fixes.

Negotiation is computed server-side at `NEGOTIATED` and re-evaluable at runtime
(downgrade only; never silent upgrade mid-session without a new negotiation).

**Consequences.** Mixed-platform pairs never attempt UWB. The chosen technology is a
first-class field in the session and drives adaptive UI (never a fake arrow on BLE/GPS).

---

## ADR-0004 — `RangingProvider` abstraction with three implementations **[auto]**

**Context.** UWB is untestable in CI/simulator.

**Decision.** Each platform defines a `RangingProvider` interface emitting a stream of
`RangingSample { distanceMeters?, azimuthDeg?, elevationDeg?, quality, technology }`.
Implementations: `UwbRangingProvider`, `BleRangingProvider`, `MockRangingProvider`.
All UI/session/fusion logic is tested against the mock (simulated trajectories, noise,
signal loss). Real UWB code paths are marked `// VERIFY-ON-DEVICE`.

**Consequences.** Deterministic, hardware-free testing of everything except the raw UWB
radio path. The mock is the contract; UWB/BLE must conform to it.

---

## ADR-0005 — Auth: email+password (Argon2id) + JWT access/refresh **[auto]**

**Context.** Need accounts + presence; OAuth desired later but out of scope now.

**Decision.** Argon2id password hashing; short-lived access JWT (15 min) + rotating
refresh token (7 days, stored hashed, revocable). An `AuthProvider` seam is left for
future OAuth without implementing it.

**Consequences.** Self-contained auth, no third-party dependency now. Refresh rotation
mitigates token theft.

---

## ADR-0006 — Stack choices **[auto]**

- **Backend:** Fastify 4 + `@fastify/websocket` (single HTTP+WS server), Prisma +
  PostgreSQL, `@fastify/swagger` for OpenAPI, Vitest for tests, `tsx` for dev, native
  `node:crypto`/`argon2`. Rationale: Fastify's schema-first design gives OpenAPI +
  runtime validation for free and colocates WS with HTTP.
- **Android:** Kotlin, Jetpack Compose, Coroutines/Flow, `androidx.core.uwb`,
  `OkHttp` WebSocket, Nordic/AndroidX BLE, ktlint + detekt, JUnit + Turbine.
- **iOS:** Swift 5.9, SwiftUI, `NearbyInteraction`, `CoreBluetooth`, `CoreMotion`,
  optional `ARKit`, `URLSessionWebSocketTask`, XCTest, SwiftLint.

---

## ADR-0007 — Session state machine **[auto]**

`INVITED → ACCEPTED → NEGOTIATED → ACTIVE → ENDED`, with `DECLINED`, `EXPIRED`, and
`FAILED` terminal branches. Each non-terminal state has a timeout. The state machine
lives server-side and is mirrored client-side. Full spec in `shared/protocol.md`.

---

## ADR-0008 — Mixed-platform UWB is not attempted **[auto]**

**Context.** Consumer iOS and Android UWB stacks are not interoperable (different
higher-layer session frameworks). Attempting it wastes battery and confuses UX.

**Decision.** The negotiator hard-gates UWB on `platform(a) == platform(b)`. Mixed pairs
deterministically fall back to BLE (or GPS).

---

## ADR-0009 — Monorepo without a JS workspace root **[auto]**

**Context.** Only the backend + shared are JS/TS; Android/iOS use their own build tools.

**Decision.** No root `package.json` workspace. Each part owns its build. `/shared` is
plain JSON Schema + docs consumed by copying/validation, not an npm package, to avoid
forcing an npm toolchain onto the native builds.

**Consequences.** Simpler native builds; the shared protocol is validated in the backend
test suite (which imports the schemas) as the source of truth.
