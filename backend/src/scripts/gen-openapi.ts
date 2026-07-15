import { writeFileSync } from 'node:fs';
import { buildApp } from '../app.js';
import { loadConfig } from '../config.js';
import { createMemoryRepos } from '../repo/memory.js';

// Generates openapi.json without needing a database (uses in-memory repos).
async function main(): Promise<void> {
  const { app } = await buildApp({ config: loadConfig(), repos: createMemoryRepos() });
  await app.ready();
  const spec = app.swagger();
  writeFileSync('openapi.json', JSON.stringify(spec, null, 2));
  await app.close();
  console.log('wrote openapi.json');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
