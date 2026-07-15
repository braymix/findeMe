import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { registerUser, startTestServer, type TestServer } from './helpers.js';

describe('HTTP surface', () => {
  let s: TestServer;
  afterEach(async () => {
    if (s) await s.close();
  });

  it('health check responds', async () => {
    s = await startTestServer();
    const res = await fetch(`${s.baseUrl}/health`);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: 'ok' });
  });

  it('rejects unauthenticated contact access', async () => {
    s = await startTestServer();
    const res = await fetch(`${s.baseUrl}/contacts`);
    expect(res.status).toBe(401);
  });

  it('adds a contact by username and lists it', async () => {
    s = await startTestServer();
    const alice = await registerUser(s, 'alice');
    await registerUser(s, 'bob');
    const add = await fetch(`${s.baseUrl}/contacts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${alice.accessToken}` },
      body: JSON.stringify({ username: 'bob' }),
    });
    expect(add.status).toBe(201);
    const list = await fetch(`${s.baseUrl}/contacts`, {
      headers: { authorization: `Bearer ${alice.accessToken}` },
    });
    const contacts = (await list.json()) as { username: string }[];
    expect(contacts.map((c) => c.username)).toContain('bob');
  });

  describe('rate limiting', () => {
    beforeEach(async () => {
      s = await startTestServer({ rateLimitAuthPerMin: 3 });
    });
    it('429s after exceeding the auth budget', async () => {
      const attempt = () =>
        fetch(`${s.baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ email: 'nobody@example.com', password: 'password123' }),
        });
      const statuses: number[] = [];
      for (let i = 0; i < 5; i++) statuses.push((await attempt()).status);
      expect(statuses.filter((x) => x === 429).length).toBeGreaterThan(0);
    });
  });
});
