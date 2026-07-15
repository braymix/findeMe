import type { FastifyInstance } from 'fastify';

/**
 * Minimal fixed-window in-memory rate limiter for the auth endpoints (Phase 6
 * hardening). Keyed by client IP. Deliberately dependency-free; for a multi-instance
 * deployment swap the Map for a shared store (Redis) — see docs/security.md.
 */
export function registerAuthRateLimit(app: FastifyInstance, perMinute: number): void {
  const windowMs = 60_000;
  const hits = new Map<string, { count: number; resetAt: number }>();

  app.addHook('onRequest', async (req, reply) => {
    if (!req.url.startsWith('/auth/')) return;
    const key = req.ip;
    const now = Date.now();
    const entry = hits.get(key);
    if (!entry || entry.resetAt < now) {
      hits.set(key, { count: 1, resetAt: now + windowMs });
      return;
    }
    entry.count += 1;
    if (entry.count > perMinute) {
      const retryAfter = Math.ceil((entry.resetAt - now) / 1000);
      return reply
        .code(429)
        .header('retry-after', String(retryAfter))
        .send({ code: 'RATE_LIMITED', message: 'too many requests' });
    }
  });

  // Opportunistic cleanup so the map does not grow unbounded.
  const sweeper = setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.resetAt < now) hits.delete(k);
  }, windowMs);
  sweeper.unref?.();
  app.addHook('onClose', async () => clearInterval(sweeper));
}
