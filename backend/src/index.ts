import { PrismaClient } from '@prisma/client';
import { buildApp } from './app.js';
import { loadConfig } from './config.js';
import { createPrismaRepos } from './repo/prisma.js';

async function main(): Promise<void> {
  const config = loadConfig();
  const db = new PrismaClient();
  await db.$connect();
  const repos = createPrismaRepos(db);

  const { app } = await buildApp({ config, repos, logger: true });

  const shutdown = async (signal: string) => {
    app.log.info(`received ${signal}, shutting down`);
    await app.close();
    await db.$disconnect();
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));

  await app.listen({ port: config.port, host: config.host });
  app.log.info(`uwb-peer-compass backend listening on ${config.host}:${config.port}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
