# Privacy & GDPR — privacy by design

uwb-peer-compass is built so the most sensitive data (a person's real-time distance,
direction, and position) is **never collected by the backend**. Ranging happens directly
between the two phones; the server is only a consent broker.

## Data map

| Data | Where it lives | Retention | Notes |
|------|----------------|-----------|-------|
| Email, username, Argon2id password hash | Backend PostgreSQL | Until account deletion | Password is only ever stored hashed (Argon2id). |
| Contact edges (who added whom) | Backend PostgreSQL | Until removed / account deletion | Needed for the peer list + presence fan-out. |
| Refresh tokens (SHA-256 hash only) | Backend PostgreSQL | Until expiry/rotation/revocation | Raw token never stored. |
| Presence (online/offline) | Backend memory only | Duration of the WS connection | Never written to disk. |
| Session lifecycle state + ephemeral opaque tokens | Backend **memory only** | Duration of the session; purged on end | No measurements; tokens are opaque blobs relayed verbatim. |
| **Distance / direction / position** | **On the two devices only** | Not persisted anywhere by default | The backend has no field to store these — by construction. |

## GDPR alignment

- **Lawful basis (Art. 6(1)(a) & (b)).** Account data is processed to provide the service
  (contract). Every ranging session additionally requires **explicit, per-session consent**
  from the person being located — the invitee must tap *Accept*. Consent is freely given,
  specific, and revocable at any time by ending the session.
- **Data minimisation (Art. 5(1)(c)).** The server stores only what presence + rendezvous
  require. Measurements and positions are structurally excluded (no schema, no endpoint).
- **Storage limitation (Art. 5(1)(e)).** Sessions and presence are ephemeral (memory only).
  Refresh tokens expire (7 days) and rotate on use.
- **Purpose limitation.** The relayed `rangingPayload` is opaque to the server and used
  solely to bootstrap the P2P ranging channel.
- **Transparency (Arts. 12-13).** Permission-priming screens explain, before the OS prompt,
  what each sensor is for and state plainly that location never leaves the device.
- **Rights (Arts. 15-17).** Because durable personal data is limited to account + contacts,
  export and erasure are straightforward: deleting the `User` row cascades to contacts and
  refresh tokens (see `prisma/schema.prisma` `onDelete: Cascade`). *A self-service
  export/delete endpoint is a recommended next step — see docs/HANDOFF.md.*
- **Security of processing (Art. 32).** TLS in transit, Argon2id at rest for passwords,
  short-lived access tokens, rotating refresh tokens, rate-limited auth. See docs/security.md.

## Design consequences

- The backend **cannot** offer "distance history", heatmaps, or last-seen-location — there
  is deliberately nowhere to put that data.
- A session is always **voluntary, visible, and terminable** from both sides; either party
  ending it stops ranging on both devices immediately.
- Ranging is stopped when the app is backgrounded (client requirement), further limiting
  incidental collection.
