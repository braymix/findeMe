import type { Capabilities, Platform } from '../session/types.js';
import type { ServerMessage } from './messages.js';

export interface Connection {
  userId: string;
  username: string;
  platform?: Platform;
  capabilities?: Capabilities;
  lastHeartbeat: number;
  send(msg: ServerMessage): void;
  close(): void;
}

/**
 * In-memory registry of live WS connections keyed by userId. A user has at most one
 * active connection (a new one replaces the old). Presence = "has a live connection
 * that sent presence.hello and is heartbeating".
 */
export class Hub {
  private conns = new Map<string, Connection>();

  add(conn: Connection): void {
    const existing = this.conns.get(conn.userId);
    if (existing && existing !== conn) existing.close();
    this.conns.set(conn.userId, conn);
  }

  remove(userId: string, conn?: Connection): void {
    const current = this.conns.get(userId);
    if (conn && current !== conn) return; // stale close, ignore
    this.conns.delete(userId);
  }

  get(userId: string): Connection | undefined {
    return this.conns.get(userId);
  }

  isOnline(userId: string): boolean {
    return this.conns.has(userId);
  }

  sendTo(userId: string, msg: ServerMessage): boolean {
    const c = this.conns.get(userId);
    if (!c) return false;
    c.send(msg);
    return true;
  }
}
