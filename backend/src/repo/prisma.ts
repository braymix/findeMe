import { PrismaClient } from '@prisma/client';
import { UniqueViolation } from './memory.js';
import type {
  ContactRepo,
  RefreshTokenRecord,
  RefreshTokenRepo,
  Repos,
  UserRecord,
  UserRepo,
} from './types.js';

// Prisma throws P2002 on unique-constraint violations; normalise to UniqueViolation.
function isP2002(e: unknown): e is { code: string; meta?: { target?: string[] } } {
  return typeof e === 'object' && e !== null && (e as { code?: string }).code === 'P2002';
}

class PrismaUserRepo implements UserRepo {
  constructor(private readonly db: PrismaClient) {}
  async create(input: {
    username: string;
    email: string;
    passwordHash: string;
  }): Promise<UserRecord> {
    try {
      return await this.db.user.create({ data: input });
    } catch (e) {
      if (isP2002(e)) throw new UniqueViolation(e.meta?.target?.[0] ?? 'unique');
      throw e;
    }
  }
  findById(id: string) {
    return this.db.user.findUnique({ where: { id } });
  }
  findByUsername(username: string) {
    return this.db.user.findUnique({ where: { username } });
  }
  findByEmail(email: string) {
    return this.db.user.findUnique({ where: { email } });
  }
}

class PrismaContactRepo implements ContactRepo {
  constructor(private readonly db: PrismaClient) {}
  async add(ownerId: string, contactId: string): Promise<void> {
    await this.db.contact.upsert({
      where: { ownerId_contactId: { ownerId, contactId } },
      create: { ownerId, contactId },
      update: {},
    });
  }
  async exists(ownerId: string, contactId: string): Promise<boolean> {
    const c = await this.db.contact.findUnique({
      where: { ownerId_contactId: { ownerId, contactId } },
    });
    return c !== null;
  }
  async list(ownerId: string): Promise<UserRecord[]> {
    const rows = await this.db.contact.findMany({
      where: { ownerId },
      include: { contact: true },
    });
    return rows.map((r) => r.contact);
  }
  async ownerIdsOf(contactId: string): Promise<string[]> {
    const rows = await this.db.contact.findMany({
      where: { contactId },
      select: { ownerId: true },
    });
    return rows.map((r) => r.ownerId);
  }
}

class PrismaRefreshTokenRepo implements RefreshTokenRepo {
  constructor(private readonly db: PrismaClient) {}
  async create(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void> {
    await this.db.refreshToken.create({ data: input });
  }
  findByHash(tokenHash: string): Promise<RefreshTokenRecord | null> {
    return this.db.refreshToken.findUnique({ where: { tokenHash } });
  }
  async revoke(tokenHash: string): Promise<void> {
    await this.db.refreshToken.updateMany({
      where: { tokenHash },
      data: { revokedAt: new Date() },
    });
  }
}

export function createPrismaRepos(db: PrismaClient): Repos {
  return {
    users: new PrismaUserRepo(db),
    contacts: new PrismaContactRepo(db),
    refreshTokens: new PrismaRefreshTokenRepo(db),
  };
}
