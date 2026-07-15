# Security — threat model & hardening

## Assets
1. User credentials (email/password hash).
2. Session/access tokens.
3. The integrity of the rendezvous (only consenting peers should range each other).
4. Users' physical location — protected structurally by keeping it off the server (see
   docs/privacy.md).

## Trust boundaries
- **Client ↔ backend:** untrusted clients over TLS. All input is schema-validated
  (Fastify JSON schemas for HTTP; a typed dispatcher + protocol-version gate for WS).
- **Device ↔ device:** the ranging channel (UWB/BLE) is out of the backend's trust scope;
  the backend only brokers the opaque bootstrap tokens.

## Controls in place
| Control | Implementation |
|---------|----------------|
| Password storage | Argon2id (`argon2` lib), per-password salt. |
| Access tokens | HS256 JWT, 15-min TTL, `sub` + `username` claims (`jose`). |
| Refresh tokens | Opaque 32-byte random; only SHA-256 stored; **rotated** on every use; old token revoked (reuse ⇒ rejected). |
| Transport | TLS terminated at the edge/proxy; WS shares the same origin. |
| Auth rate limiting | Fixed-window per-IP limiter on `/auth/*` (`src/http/rateLimit.ts`). |
| Input validation | Fastify schemas on every HTTP route; WS messages gated on `v` (protocol major) and dispatched by exact type; unknown ⇒ `error`. |
| Authorization on sessions | Every session action checks the caller is a participant and in the right role (only invitee accepts/declines; only participants end); enforced in `SessionManager`. |
| Session token exposure | Discovery tokens are relayed verbatim and never logged; sessions are memory-only and dropped on end/disconnect. |
| Measurement exfiltration | Not possible via the backend — there is no schema/endpoint that accepts distance/direction/position. |
| WS auth | Access JWT required as a query param on connect; invalid ⇒ close 1008. |
| Presence abuse | A user has at most one live connection; a new connection replaces the old. |
| Heartbeat / zombie connections | 15 s heartbeat with a 45 s watchdog closing stale sockets. |

## Residual risks & recommended next steps
- **WS token in query string** may appear in proxy logs. *Next step:* move to a
  `Sec-WebSocket-Protocol` bearer or a short-lived one-time ticket. (Marked in HANDOFF.)
- **Rate limiter is per-instance (in-memory).** For horizontal scaling, back it with Redis.
- **No account lockout / breach-password check.** Consider adding after repeated failures.
- **JWT secret rotation** is manual. Add a key-id (`kid`) header + rotation schedule.
- **Invite spam:** an online user can be invited repeatedly. Consider per-pair invite
  cooldowns and a block list.
- **CSRF:** not applicable (no cookies; bearer tokens only), but ensure clients store tokens
  in secure storage (Keychain / EncryptedSharedPreferences), not plaintext.
- **Dependency hygiene:** run `npm audit` / Gradle dependency checks in CI on a schedule.

## Attack-surface summary
- HTTP: `/auth/register`, `/auth/login`, `/auth/refresh`, `/contacts` (GET/POST), `/me`,
  `/health`. All except `/health` and `/auth/*` require a valid access token.
- WS: `/ws` (authenticated). Eight client message types, all validated and authorized.
- No file uploads, no server-side rendering, no admin surface.
