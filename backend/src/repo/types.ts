/**
 * Persistence abstraction. Two implementations exist:
 *  - PrismaRepos  (src/repo/prisma.ts)  — production, PostgreSQL.
 *  - MemoryRepos  (src/repo/memory.ts)  — tests, no DB required.
 *
 * This seam is what lets the end-to-end WS handshake integration test run without
 * a live Postgres (ADR-0009 rationale).
 */

export interface UserRecord {
  id: string;
  username: string;
  email: string;
  passwordHash: string;
  createdAt: Date;
}

export interface RefreshTokenRecord {
  id: string;
  userId: string;
  tokenHash: string;
  expiresAt: Date;
  revokedAt: Date | null;
}

export interface UserRepo {
  create(input: { username: string; email: string; passwordHash: string }): Promise<UserRecord>;
  findById(id: string): Promise<UserRecord | null>;
  findByUsername(username: string): Promise<UserRecord | null>;
  findByEmail(email: string): Promise<UserRecord | null>;
}

export interface ContactRepo {
  add(ownerId: string, contactId: string): Promise<void>;
  list(ownerId: string): Promise<UserRecord[]>;
  exists(ownerId: string, contactId: string): Promise<boolean>;
  /** Ids of users who added `contactId` — used to fan out presence updates. */
  ownerIdsOf(contactId: string): Promise<string[]>;
}

export interface RefreshTokenRepo {
  create(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void>;
  findByHash(tokenHash: string): Promise<RefreshTokenRecord | null>;
  revoke(tokenHash: string): Promise<void>;
}

export interface Repos {
  users: UserRepo;
  contacts: ContactRepo;
  refreshTokens: RefreshTokenRepo;
}
