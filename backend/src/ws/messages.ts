import type {
  Capabilities,
  Platform,
  Role,
  SessionState,
  Technology,
  EndReason,
} from '../session/types.js';

export const PROTOCOL_MAJOR = 1;

export interface RangingPayload {
  technology: Technology;
  blob: string;
  [k: string]: unknown;
}

// ---- Client -> Server ----
export type ClientMessage =
  | {
      v: 1;
      type: 'presence.hello';
      protocolVersion: string;
      platform: Platform;
      capabilities: Capabilities;
    }
  | { v: 1; type: 'presence.heartbeat' }
  | { v: 1; type: 'session.invite'; peerId: string }
  | { v: 1; type: 'session.accept'; sessionId: string }
  | { v: 1; type: 'session.decline'; sessionId: string }
  | { v: 1; type: 'session.end'; sessionId: string }
  | { v: 1; type: 'ranging.ready'; sessionId: string; rangingPayload: RangingPayload }
  | { v: 1; type: 'technology.report'; sessionId: string; technology: Technology; reason: string };

// ---- Server -> Client ----
export type ServerMessage =
  | { v: 1; type: 'presence.update'; peerId: string; online: boolean }
  | { v: 1; type: 'session.state'; sessionId: string; state: SessionState }
  | { v: 1; type: 'session.incoming'; sessionId: string; from: { id: string; username: string } }
  | { v: 1; type: 'session.negotiated'; sessionId: string; technology: Technology; role: Role }
  | { v: 1; type: 'ranging.peerPayload'; sessionId: string; rangingPayload: RangingPayload }
  | { v: 1; type: 'technology.downgrade'; sessionId: string; technology: Technology }
  | { v: 1; type: 'session.ended'; sessionId: string; reason: EndReason }
  | { v: 1; type: 'error'; code: string; message: string; sessionId?: string };

type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;

export function serverMsg(m: DistributiveOmit<ServerMessage, 'v'>): ServerMessage {
  return { v: 1, ...m } as ServerMessage;
}
