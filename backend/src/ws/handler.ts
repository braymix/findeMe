import type { FastifyInstance } from 'fastify';
import type { WebSocket } from 'ws';
import { verifyAccessToken } from '../lib/crypto.js';
import type { Repos } from '../repo/types.js';
import { SessionError, type SessionManager } from '../session/manager.js';
import type { Capabilities, Platform } from '../session/types.js';
import type { Connection, Hub } from './hub.js';
import { PROTOCOL_MAJOR, serverMsg, type ClientMessage, type ServerMessage } from './messages.js';

const HEARTBEAT_GRACE_MS = 45_000; // 3 missed 15s beats.

export interface WsDeps {
  hub: Hub;
  sessions: SessionManager;
  repos: Repos;
  jwtSecret: string;
}

export function registerWsRoutes(app: FastifyInstance, deps: WsDeps): void {
  app.get('/ws', { websocket: true }, async (socket: WebSocket, req) => {
    const token = (req.query as { token?: string }).token;
    let userId: string;
    let username: string;
    try {
      if (!token) throw new Error('missing token');
      const claims = await verifyAccessToken(token, deps.jwtSecret);
      userId = claims.sub;
      username = claims.username;
    } catch {
      socket.send(JSON.stringify(err('UNAUTHENTICATED', 'invalid or missing token')));
      socket.close(1008, 'unauthenticated');
      return;
    }

    const conn: Connection = {
      userId,
      username,
      lastHeartbeat: Date.now(),
      send: (m: ServerMessage) => {
        if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(m));
      },
      close: () => socket.close(),
    };
    deps.hub.add(conn);

    // Heartbeat watchdog.
    const watchdog = setInterval(() => {
      if (Date.now() - conn.lastHeartbeat > HEARTBEAT_GRACE_MS) {
        socket.close(1001, 'heartbeat timeout');
      }
    }, 15_000);

    socket.on('message', (raw) => {
      let msg: ClientMessage;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return conn.send(err('BAD_JSON', 'malformed message'));
      }
      if (typeof msg !== 'object' || msg === null || (msg as { v?: number }).v !== PROTOCOL_MAJOR) {
        return conn.send(err('UNSUPPORTED_PROTOCOL', `expected protocol v${PROTOCOL_MAJOR}`));
      }
      handleMessage(conn, msg, deps).catch((e) => {
        if (e instanceof SessionError) {
          conn.send(err(e.code, e.message));
        } else {
          app.log.error(e);
          conn.send(err('INTERNAL', 'internal error'));
        }
      });
    });

    const teardown = () => {
      clearInterval(watchdog);
      deps.hub.remove(userId, conn);
      deps.sessions.onDisconnect(userId);
      void broadcastPresence(deps, userId, false);
    };
    socket.on('close', teardown);
    socket.on('error', teardown);
  });
}

async function handleMessage(conn: Connection, msg: ClientMessage, deps: WsDeps): Promise<void> {
  switch (msg.type) {
    case 'presence.hello':
      conn.platform = msg.platform as Platform;
      conn.capabilities = msg.capabilities as Capabilities;
      conn.lastHeartbeat = Date.now();
      await broadcastPresence(deps, conn.userId, true);
      return;
    case 'presence.heartbeat':
      conn.lastHeartbeat = Date.now();
      return;
    case 'session.invite':
      deps.sessions.invite(conn.userId, msg.peerId);
      return;
    case 'session.accept':
      deps.sessions.accept(msg.sessionId, conn.userId);
      return;
    case 'session.decline':
      deps.sessions.decline(msg.sessionId, conn.userId);
      return;
    case 'session.end':
      deps.sessions.end(msg.sessionId, conn.userId);
      return;
    case 'ranging.ready':
      deps.sessions.rangingReady(msg.sessionId, conn.userId, msg.rangingPayload);
      return;
    case 'technology.report':
      deps.sessions.technologyReport(msg.sessionId, conn.userId, msg.technology, msg.reason);
      return;
    default:
      conn.send(err('UNKNOWN_TYPE', `unknown message type`));
  }
}

/** Tell each online user who has `userId` as a contact about the presence change. */
async function broadcastPresence(deps: WsDeps, userId: string, online: boolean): Promise<void> {
  const owners = await deps.repos.contacts.ownerIdsOf(userId);
  for (const ownerId of owners) {
    deps.hub.sendTo(ownerId, serverMsg({ type: 'presence.update', peerId: userId, online }));
  }
}

function err(code: string, message: string): ServerMessage {
  return { v: 1, type: 'error', code, message };
}
