import { beforeEach, describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config.js';
import { createMemoryRepos } from '../src/repo/memory.js';
import { AuthError, AuthService } from '../src/services/auth.js';

const config = { ...loadConfig(), jwtSecret: 'test-secret-test-secret-test-secret-1234' };

describe('AuthService', () => {
  let auth: AuthService;
  beforeEach(() => {
    auth = new AuthService(createMemoryRepos(), config);
  });

  it('registers and returns tokens', async () => {
    const t = await auth.register({
      username: 'alice',
      email: 'A@Example.com',
      password: 'password123',
    });
    expect(t.accessToken).toBeTruthy();
    expect(t.refreshToken).toBeTruthy();
    expect(t.user.username).toBe('alice');
    expect(t.user.email).toBe('a@example.com'); // normalised
  });

  it('rejects duplicate username/email', async () => {
    await auth.register({ username: 'bob', email: 'bob@example.com', password: 'password123' });
    await expect(
      auth.register({ username: 'bob', email: 'other@example.com', password: 'password123' }),
    ).rejects.toBeInstanceOf(AuthError);
  });

  it('logs in with correct credentials and rejects wrong ones', async () => {
    await auth.register({ username: 'carol', email: 'carol@example.com', password: 'password123' });
    const ok = await auth.login({ email: 'carol@example.com', password: 'password123' });
    expect(ok.accessToken).toBeTruthy();
    await expect(
      auth.login({ email: 'carol@example.com', password: 'wrong' }),
    ).rejects.toBeInstanceOf(AuthError);
    await expect(
      auth.login({ email: 'nobody@example.com', password: 'password123' }),
    ).rejects.toBeInstanceOf(AuthError);
  });

  it('rotates refresh tokens and invalidates the old one', async () => {
    const t = await auth.register({
      username: 'dave',
      email: 'dave@example.com',
      password: 'password123',
    });
    const refreshed = await auth.refresh(t.refreshToken);
    expect(refreshed.accessToken).toBeTruthy();
    // old token now revoked
    await expect(auth.refresh(t.refreshToken)).rejects.toBeInstanceOf(AuthError);
    // new token still works
    const again = await auth.refresh(refreshed.refreshToken);
    expect(again.accessToken).toBeTruthy();
  });
});
