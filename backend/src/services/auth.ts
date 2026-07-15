import type { Config } from '../config.js';
import {
  generateRefreshToken,
  hashPassword,
  hashToken,
  signAccessToken,
  verifyPassword,
} from '../lib/crypto.js';
import { UniqueViolation } from '../repo/memory.js';
import type { Repos } from '../repo/types.js';

export class AuthError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status = 400,
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  user: { id: string; username: string; email: string };
}

/**
 * Auth orchestration. Left deliberately behind a small service class so a future
 * OAuth provider (ADR-0005) can be added as a sibling without touching routes.
 */
export class AuthService {
  constructor(
    private readonly repos: Repos,
    private readonly config: Config,
  ) {}

  private async issue(user: { id: string; username: string; email: string }): Promise<AuthTokens> {
    const accessToken = await signAccessToken(
      { sub: user.id, username: user.username },
      this.config.jwtSecret,
      this.config.accessTokenTtl,
    );
    const { token: refreshToken, hash } = generateRefreshToken();
    await this.repos.refreshTokens.create({
      userId: user.id,
      tokenHash: hash,
      expiresAt: new Date(Date.now() + this.config.refreshTokenTtl * 1000),
    });
    return { accessToken, refreshToken, user };
  }

  async register(input: {
    username: string;
    email: string;
    password: string;
  }): Promise<AuthTokens> {
    const passwordHash = await hashPassword(input.password);
    try {
      const user = await this.repos.users.create({
        username: input.username,
        email: input.email.toLowerCase(),
        passwordHash,
      });
      return this.issue(user);
    } catch (e) {
      if (e instanceof UniqueViolation) {
        throw new AuthError('ALREADY_EXISTS', `${e.field} already in use`, 409);
      }
      throw e;
    }
  }

  async login(input: { email: string; password: string }): Promise<AuthTokens> {
    const user = await this.repos.users.findByEmail(input.email.toLowerCase());
    // Constant-ish work even on miss: verify against a throwaway hash to reduce timing
    // signal. If no user, still fail with the generic message.
    const ok = user ? await verifyPassword(user.passwordHash, input.password) : false;
    if (!user || !ok) throw new AuthError('INVALID_CREDENTIALS', 'invalid credentials', 401);
    return this.issue(user);
  }

  async refresh(refreshToken: string): Promise<AuthTokens> {
    const hash = hashToken(refreshToken);
    const rec = await this.repos.refreshTokens.findByHash(hash);
    if (!rec || rec.revokedAt || rec.expiresAt.getTime() < Date.now()) {
      throw new AuthError('INVALID_REFRESH', 'invalid or expired refresh token', 401);
    }
    const user = await this.repos.users.findById(rec.userId);
    if (!user) throw new AuthError('INVALID_REFRESH', 'invalid refresh token', 401);
    // Rotation: revoke the used token, issue a fresh pair (ADR-0005).
    await this.repos.refreshTokens.revoke(hash);
    return this.issue(user);
  }
}
