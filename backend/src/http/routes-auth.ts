import type { FastifyInstance } from 'fastify';
import { AuthError, AuthService } from '../services/auth.js';

const credsBody = {
  type: 'object',
  required: ['email', 'password'],
  properties: {
    email: { type: 'string', format: 'email' },
    password: { type: 'string', minLength: 8, maxLength: 200 },
  },
} as const;

const registerBody = {
  type: 'object',
  required: ['username', 'email', 'password'],
  properties: {
    username: { type: 'string', minLength: 3, maxLength: 32, pattern: '^[a-zA-Z0-9_.-]+$' },
    email: { type: 'string', format: 'email' },
    password: { type: 'string', minLength: 8, maxLength: 200 },
  },
} as const;

const tokensResponse = {
  type: 'object',
  properties: {
    accessToken: { type: 'string' },
    refreshToken: { type: 'string' },
    user: {
      type: 'object',
      properties: {
        id: { type: 'string' },
        username: { type: 'string' },
        email: { type: 'string' },
      },
    },
  },
} as const;

export async function authRoutes(app: FastifyInstance, auth: AuthService): Promise<void> {
  app.post(
    '/auth/register',
    { schema: { tags: ['auth'], body: registerBody, response: { 201: tokensResponse } } },
    async (req, reply) => {
      const body = req.body as { username: string; email: string; password: string };
      try {
        const tokens = await auth.register(body);
        return reply.code(201).send(tokens);
      } catch (e) {
        return sendAuthError(reply, e);
      }
    },
  );

  app.post(
    '/auth/login',
    { schema: { tags: ['auth'], body: credsBody, response: { 200: tokensResponse } } },
    async (req, reply) => {
      try {
        return await auth.login(req.body as { email: string; password: string });
      } catch (e) {
        return sendAuthError(reply, e);
      }
    },
  );

  app.post(
    '/auth/refresh',
    {
      schema: {
        tags: ['auth'],
        body: {
          type: 'object',
          required: ['refreshToken'],
          properties: { refreshToken: { type: 'string' } },
        },
        response: { 200: tokensResponse },
      },
    },
    async (req, reply) => {
      try {
        return await auth.refresh((req.body as { refreshToken: string }).refreshToken);
      } catch (e) {
        return sendAuthError(reply, e);
      }
    },
  );
}

function sendAuthError(reply: import('fastify').FastifyReply, e: unknown) {
  if (e instanceof AuthError) {
    return reply.code(e.status).send({ code: e.code, message: e.message });
  }
  throw e;
}
