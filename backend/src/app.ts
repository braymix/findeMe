import fastifyWebsocket from '@fastify/websocket';
import fastifySwagger from '@fastify/swagger';
import Fastify, { type FastifyInstance } from 'fastify';
import type { Config } from './config.js';
import { authRoutes } from './http/routes-auth.js';
import { contactRoutes } from './http/routes-contacts.js';
import { registerAuthRateLimit } from './http/rateLimit.js';
import { generateSessionId } from './lib/crypto.js';
import type { Repos } from './repo/types.js';
import { SessionManager } from './session/manager.js';
import { AuthService } from './services/auth.js';
import { Hub } from './ws/hub.js';
import { registerWsRoutes } from './ws/handler.js';

export interface BuiltApp {
  app: FastifyInstance;
  hub: Hub;
  sessions: SessionManager;
}

export async function buildApp(opts: {
  config: Config;
  repos: Repos;
  logger?: boolean;
}): Promise<BuiltApp> {
  const { config, repos } = opts;
  const app = Fastify({ logger: opts.logger ?? false });

  const hub = new Hub();
  const sessions = new SessionManager(hub, config, generateSessionId);
  const auth = new AuthService(repos, config);

  await app.register(fastifySwagger, {
    openapi: {
      info: { title: 'uwb-peer-compass rendezvous API', version: '1.0.0' },
      tags: [
        { name: 'auth', description: 'Registration, login, refresh' },
        { name: 'contacts', description: 'Contact list and identity' },
      ],
    },
  });

  await app.register(fastifyWebsocket);

  registerAuthRateLimit(app, config.rateLimitAuthPerMin);

  app.get('/health', { schema: { hide: true } }, async () => ({ status: 'ok' }));

  await app.register(async (instance) => {
    await authRoutes(instance, auth);
    await contactRoutes(instance, repos, hub, config.jwtSecret);
  });

  await app.register(async (instance) => {
    registerWsRoutes(instance, { hub, sessions, repos, jwtSecret: config.jwtSecret });
  });

  return { app, hub, sessions };
}
