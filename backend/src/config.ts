export interface Config {
  nodeEnv: string;
  port: number;
  host: string;
  jwtSecret: string;
  accessTokenTtl: number;
  refreshTokenTtl: number;
  inviteTimeoutMs: number;
  setupTimeoutMs: number;
  activeMaxMs: number;
  rateLimitAuthPerMin: number;
}

function num(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const n = Number(raw);
  if (Number.isNaN(n)) throw new Error(`Env ${name} must be a number, got "${raw}"`);
  return n;
}

export function loadConfig(): Config {
  const jwtSecret = process.env.JWT_SECRET ?? 'dev-only-insecure-secret-change-me-please-32bytes+';
  if (jwtSecret.length < 32) {
    throw new Error('JWT_SECRET must be at least 32 characters');
  }
  return {
    nodeEnv: process.env.NODE_ENV ?? 'development',
    port: num('PORT', 3000),
    host: process.env.HOST ?? '0.0.0.0',
    jwtSecret,
    accessTokenTtl: num('ACCESS_TOKEN_TTL', 900),
    refreshTokenTtl: num('REFRESH_TOKEN_TTL', 604800),
    inviteTimeoutMs: num('INVITE_TIMEOUT_MS', 30000),
    setupTimeoutMs: num('SETUP_TIMEOUT_MS', 20000),
    activeMaxMs: num('ACTIVE_MAX_MS', 900000),
    rateLimitAuthPerMin: num('RATE_LIMIT_AUTH_PER_MIN', 10),
  };
}
