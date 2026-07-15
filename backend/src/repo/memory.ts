import { randomUUID } from 'node:crypto';
import type {
  ContactRepo,
  RefreshTokenRecord,
  RefreshTokenRepo,
  Repos,
  UserRecord,
  UserRepo,
} from './types.js';

class MemoryUserRepo implements UserRepo {
  private byId = new Map<string, UserRecord>();

  async create(input: {
    username: string;
    email: string;
    passwordHash: string;
  }): Promise<UserRecord> {
    if (await this.findByUsername(input.username)) throw new UniqueViolation('username');
    if (await this.findByEmail(input.email)) throw new UniqueViolation('email');
    const rec: UserRecord = { id: randomUUID(), createdAt: new Date(), ...input };
    this.byId.set(rec.id, rec);
    return rec;
  }
  async findById(id: string): Promise<UserRecord | null> {
    return this.byId.get(id) ?? null;
  }
  async findByUsername(username: string): Promise<UserRecord | null> {
    for (const u of this.byId.values()) if (u.username === username) return u;
    return null;
  }
  async findByEmail(email: string): Promise<UserRecord | null> {
    for (const u of this.byId.values()) if (u.email === email) return u;
    return null;
  }
}

class MemoryContactRepo implements ContactRepo {
  private edges = new Set<string>(); // `${owner}:${contact}`
  constructor(private readonly users: MemoryUserRepo) {}

  private key(o: string, c: string) {
    return `${o}:${c}`;
  }
  async add(ownerId: string, contactId: string): Promise<void> {
    this.edges.add(this.key(ownerId, contactId));
  }
  async exists(ownerId: string, contactId: string): Promise<boolean> {
    return this.edges.has(this.key(ownerId, contactId));
  }
  async list(ownerId: string): Promise<UserRecord[]> {
    const out: UserRecord[] = [];
    for (const e of this.edges) {
      const [o, c] = e.split(':');
      if (o === ownerId && c) {
        const u = await this.users.findById(c);
        if (u) out.push(u);
      }
    }
    return out;
  }
  async ownerIdsOf(contactId: string): Promise<string[]> {
    const out: string[] = [];
    for (const e of this.edges) {
      const [o, c] = e.split(':');
      if (c === contactId && o) out.push(o);
    }
    return out;
  }
}

class MemoryRefreshTokenRepo implements RefreshTokenRepo {
  private byHash = new Map<string, RefreshTokenRecord>();
  async create(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void> {
    this.byHash.set(input.tokenHash, {
      id: randomUUID(),
      revokedAt: null,
      ...input,
    });
  }
  async findByHash(tokenHash: string): Promise<RefreshTokenRecord | null> {
    return this.byHash.get(tokenHash) ?? null;
  }
  async revoke(tokenHash: string): Promise<void> {
    const rec = this.byHash.get(tokenHash);
    if (rec) rec.revokedAt = new Date();
  }
}

export class UniqueViolation extends Error {
  constructor(public readonly field: string) {
    super(`unique violation on ${field}`);
    this.name = 'UniqueViolation';
  }
}

export function createMemoryRepos(): Repos {
  const users = new MemoryUserRepo();
  return {
    users,
    contacts: new MemoryContactRepo(users),
    refreshTokens: new MemoryRefreshTokenRepo(),
  };
}
