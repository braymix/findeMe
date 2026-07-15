import { WebSocket } from 'ws';
import { buildApp, type BuiltApp } from '../src/app.js';
import { loadConfig, type Config } from '../src/config.js';
import { createMemoryRepos } from '../src/repo/memory.js';
import type { Repos } from '../src/repo/types.js';
import type { ClientMessage, ServerMessage } from '../src/ws/messages.js';

export interface TestServer extends BuiltApp {
  baseUrl: string;
  wsUrl: string;
  repos: Repos;
  config: Config;
  close(): Promise<void>;
}

export async function startTestServer(overrides: Partial<Config> = {}): Promise<TestServer> {
  const config = {
    ...loadConfig(),
    ...overrides,
    jwtSecret: 'test-secret-test-secret-test-secret-1234',
  };
  const repos = createMemoryRepos();
  const built = await buildApp({ config, repos });
  await built.app.listen({ port: 0, host: '127.0.0.1' });
  const addr = built.app.server.address();
  if (!addr || typeof addr === 'string') throw new Error('no address');
  const baseUrl = `http://127.0.0.1:${addr.port}`;
  return {
    ...built,
    repos,
    config,
    baseUrl,
    wsUrl: `ws://127.0.0.1:${addr.port}/ws`,
    close: () => built.app.close(),
  };
}

export async function registerUser(
  s: TestServer,
  username: string,
): Promise<{ id: string; accessToken: string; username: string }> {
  const res = await fetch(`${s.baseUrl}/auth/register`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, email: `${username}@example.com`, password: 'password123' }),
  });
  if (res.status !== 201) throw new Error(`register failed: ${res.status} ${await res.text()}`);
  const body = (await res.json()) as { accessToken: string; user: { id: string } };
  return { id: body.user.id, accessToken: body.accessToken, username };
}

/**
 * A small promise-based WS test client that buffers incoming server messages and
 * lets a test `await` the next message matching a predicate.
 */
export class TestClient {
  private ws: WebSocket;
  private buffer: ServerMessage[] = [];
  private waiters: { pred: (m: ServerMessage) => boolean; resolve: (m: ServerMessage) => void }[] =
    [];

  private constructor(ws: WebSocket) {
    this.ws = ws;
    ws.on('message', (raw) => {
      const msg = JSON.parse(raw.toString()) as ServerMessage;
      const idx = this.waiters.findIndex((w) => w.pred(msg));
      if (idx >= 0) {
        const [w] = this.waiters.splice(idx, 1);
        w!.resolve(msg);
      } else {
        this.buffer.push(msg);
      }
    });
  }

  static async connect(wsUrl: string, accessToken: string): Promise<TestClient> {
    const ws = new WebSocket(`${wsUrl}?token=${encodeURIComponent(accessToken)}`);
    await new Promise<void>((resolve, reject) => {
      ws.once('open', () => resolve());
      ws.once('error', reject);
    });
    return new TestClient(ws);
  }

  send(msg: ClientMessage): void {
    this.ws.send(JSON.stringify(msg));
  }

  /** Resolve with the next (or buffered) message matching `pred`, else reject on timeout. */
  waitFor(pred: (m: ServerMessage) => boolean, timeoutMs = 3000): Promise<ServerMessage> {
    const idx = this.buffer.findIndex(pred);
    if (idx >= 0) return Promise.resolve(this.buffer.splice(idx, 1)[0]!);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('waitFor timeout')), timeoutMs);
      this.waiters.push({
        pred,
        resolve: (m) => {
          clearTimeout(timer);
          resolve(m);
        },
      });
    });
  }

  waitForType<T extends ServerMessage['type']>(
    type: T,
    timeoutMs?: number,
  ): Promise<ServerMessage> {
    return this.waitFor((m) => m.type === type, timeoutMs);
  }

  close(): void {
    this.ws.close();
  }
}
