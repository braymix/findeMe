import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { verifyAccessToken } from '../lib/crypto.js';
import type { Repos } from '../repo/types.js';
import type { Hub } from '../ws/hub.js';

interface AuthedRequest extends FastifyRequest {
  userId: string;
  username: string;
}

/**
 * Bearer-token auth guard as a preHandler. Kept local (no plugin ceremony) so the
 * seam for a future OAuth token verifier stays obvious.
 */
function makeAuthGuard(jwtSecret: string) {
  return async (req: FastifyRequest, reply: FastifyReply) => {
    const header = req.headers.authorization;
    if (!header?.startsWith('Bearer ')) {
      return reply.code(401).send({ code: 'UNAUTHENTICATED', message: 'missing bearer token' });
    }
    try {
      const claims = await verifyAccessToken(header.slice(7), jwtSecret);
      (req as AuthedRequest).userId = claims.sub;
      (req as AuthedRequest).username = claims.username;
    } catch {
      return reply.code(401).send({ code: 'UNAUTHENTICATED', message: 'invalid token' });
    }
  };
}

const contactView = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    username: { type: 'string' },
    online: { type: 'boolean' },
  },
} as const;

export async function contactRoutes(
  app: FastifyInstance,
  repos: Repos,
  hub: Hub,
  jwtSecret: string,
): Promise<void> {
  const guard = makeAuthGuard(jwtSecret);

  app.get(
    '/contacts',
    {
      preHandler: guard,
      schema: { tags: ['contacts'], response: { 200: { type: 'array', items: contactView } } },
    },
    async (req) => {
      const me = (req as AuthedRequest).userId;
      const contacts = await repos.contacts.list(me);
      return contacts.map((c) => ({ id: c.id, username: c.username, online: hub.isOnline(c.id) }));
    },
  );

  app.post(
    '/contacts',
    {
      preHandler: guard,
      schema: {
        tags: ['contacts'],
        body: {
          type: 'object',
          required: ['username'],
          properties: { username: { type: 'string' } },
        },
        response: { 201: contactView },
      },
    },
    async (req, reply) => {
      const me = (req as AuthedRequest).userId;
      const { username } = req.body as { username: string };
      const target = await repos.users.findByUsername(username);
      if (!target) return reply.code(404).send({ code: 'NOT_FOUND', message: 'no such user' });
      if (target.id === me)
        return reply.code(400).send({ code: 'SELF', message: 'cannot add yourself' });
      await repos.contacts.add(me, target.id);
      return reply
        .code(201)
        .send({ id: target.id, username: target.username, online: hub.isOnline(target.id) });
    },
  );

  // QR-code contact add: the QR simply encodes a username, so this reuses the same path.
  app.get(
    '/me',
    {
      preHandler: guard,
      schema: {
        tags: ['contacts'],
        description: 'Returns the caller identity; the client renders username as a QR code.',
        response: {
          200: {
            type: 'object',
            properties: { id: { type: 'string' }, username: { type: 'string' } },
          },
        },
      },
    },
    async (req) => ({
      id: (req as AuthedRequest).userId,
      username: (req as AuthedRequest).username,
    }),
  );
}
