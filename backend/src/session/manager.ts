import type { Config } from '../config.js';
import type { Hub } from '../ws/hub.js';
import { serverMsg, type RangingPayload } from '../ws/messages.js';
import { isLegalDowngrade, negotiate, type DeviceProfile } from './negotiate.js';
import { assertTransition, isTerminal } from './stateMachine.js';
import type { EndReason, Role, SessionState, Technology } from './types.js';

interface Session {
  id: string;
  inviterId: string;
  inviteeId: string;
  state: SessionState;
  technology?: Technology;
  timer?: ReturnType<typeof setTimeout>;
  readyPayloads: Map<string, RangingPayload>;
}

export class SessionError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'SessionError';
  }
}

/**
 * Owns the lifecycle of every ranging rendezvous. Purely in-memory and ephemeral —
 * nothing here is persisted (ADR-0002). Relays opaque rangingPayloads between the two
 * peers but never inspects/stores them.
 */
export class SessionManager {
  private sessions = new Map<string, Session>();
  private byUser = new Map<string, Set<string>>(); // userId -> sessionIds

  constructor(
    private readonly hub: Hub,
    private readonly config: Config,
    private readonly newId: () => string,
  ) {}

  /** Test/introspection helper. */
  getState(sessionId: string): SessionState | undefined {
    return this.sessions.get(sessionId)?.state;
  }

  getTechnology(sessionId: string): Technology | undefined {
    return this.sessions.get(sessionId)?.technology;
  }

  private index(session: Session): void {
    this.sessions.set(session.id, session);
    for (const uid of [session.inviterId, session.inviteeId]) {
      let set = this.byUser.get(uid);
      if (!set) this.byUser.set(uid, (set = new Set()));
      set.add(session.id);
    }
  }

  private drop(session: Session): void {
    if (session.timer) clearTimeout(session.timer);
    this.sessions.delete(session.id);
    this.byUser.get(session.inviterId)?.delete(session.id);
    this.byUser.get(session.inviteeId)?.delete(session.id);
  }

  private transition(session: Session, to: SessionState): void {
    assertTransition(session.state, to);
    session.state = to;
    if (session.timer) {
      clearTimeout(session.timer);
      session.timer = undefined;
    }
  }

  private peerOf(session: Session, userId: string): string {
    return userId === session.inviterId ? session.inviteeId : session.inviterId;
  }

  private profile(userId: string): DeviceProfile | null {
    const c = this.hub.get(userId);
    if (!c || !c.platform || !c.capabilities) return null;
    return { platform: c.platform, capabilities: c.capabilities };
  }

  // ---- lifecycle ----

  invite(inviterId: string, inviteeId: string): Session {
    if (inviterId === inviteeId) throw new SessionError('SELF', 'cannot invite yourself');
    if (!this.hub.isOnline(inviteeId)) throw new SessionError('OFFLINE', 'peer is offline');
    const inviter = this.hub.get(inviterId);
    if (!inviter) throw new SessionError('OFFLINE', 'not connected');

    const session: Session = {
      id: this.newId(),
      inviterId,
      inviteeId,
      state: 'INVITED',
      readyPayloads: new Map(),
    };
    this.index(session);

    this.hub.sendTo(
      inviteeId,
      serverMsg({
        type: 'session.incoming',
        sessionId: session.id,
        from: { id: inviterId, username: inviter.username },
      }),
    );
    this.hub.sendTo(
      inviterId,
      serverMsg({ type: 'session.state', sessionId: session.id, state: 'INVITED' }),
    );

    session.timer = setTimeout(() => this.expire(session.id), this.config.inviteTimeoutMs);
    return session;
  }

  accept(sessionId: string, byUserId: string): void {
    const s = this.require(sessionId, byUserId);
    if (byUserId !== s.inviteeId) throw new SessionError('FORBIDDEN', 'only invitee can accept');
    this.transition(s, 'ACCEPTED');
    this.broadcastState(s, 'ACCEPTED');
    this.negotiateAndAdvance(s);
  }

  decline(sessionId: string, byUserId: string): void {
    const s = this.require(sessionId, byUserId);
    if (byUserId !== s.inviteeId) throw new SessionError('FORBIDDEN', 'only invitee can decline');
    this.transition(s, 'DECLINED');
    this.end_(s, 'declined');
  }

  private negotiateAndAdvance(s: Session): void {
    const a = this.profile(s.inviterId);
    const b = this.profile(s.inviteeId);
    if (!a || !b) return this.fail(s, 'peer-disconnected');

    const tech = negotiate(a, b);
    if (!tech) return this.fail(s, 'no-common-technology');

    s.technology = tech;
    this.transition(s, 'NEGOTIATED');
    this.broadcastState(s, 'NEGOTIATED');

    // Inviter is controller, invitee is controlee (ADR mapping; VERIFY-ON-DEVICE on clients).
    this.sendNegotiated(s, s.inviterId, 'controller');
    this.sendNegotiated(s, s.inviteeId, 'controlee');

    s.timer = setTimeout(() => this.expire(s.id), this.config.setupTimeoutMs);
  }

  private sendNegotiated(s: Session, userId: string, role: Role): void {
    this.hub.sendTo(
      userId,
      serverMsg({
        type: 'session.negotiated',
        sessionId: s.id,
        technology: s.technology!,
        role,
      }),
    );
  }

  rangingReady(sessionId: string, byUserId: string, payload: RangingPayload): void {
    const s = this.require(sessionId, byUserId);
    if (s.state !== 'NEGOTIATED') throw new SessionError('BAD_STATE', 'not negotiating');
    s.readyPayloads.set(byUserId, payload);
    if (s.readyPayloads.size < 2) return;

    // Both ready: exchange opaque payloads and go ACTIVE.
    const invPayload = s.readyPayloads.get(s.inviterId)!;
    const inviteePayload = s.readyPayloads.get(s.inviteeId)!;
    this.hub.sendTo(
      s.inviteeId,
      serverMsg({ type: 'ranging.peerPayload', sessionId: s.id, rangingPayload: invPayload }),
    );
    this.hub.sendTo(
      s.inviterId,
      serverMsg({ type: 'ranging.peerPayload', sessionId: s.id, rangingPayload: inviteePayload }),
    );

    this.transition(s, 'ACTIVE');
    this.broadcastState(s, 'ACTIVE');
    s.timer = setTimeout(() => this.end(s.id, s.inviterId), this.config.activeMaxMs);
  }

  technologyReport(sessionId: string, byUserId: string, target: Technology, _reason: string): void {
    const s = this.require(sessionId, byUserId);
    if (s.state !== 'ACTIVE' || !s.technology) throw new SessionError('BAD_STATE', 'not active');
    const a = this.profile(s.inviterId);
    const b = this.profile(s.inviteeId);
    if (!a || !b) return this.fail(s, 'peer-disconnected');
    if (!isLegalDowngrade(s.technology, target, a, b)) {
      throw new SessionError('ILLEGAL_DOWNGRADE', `cannot downgrade ${s.technology} -> ${target}`);
    }
    s.technology = target;
    for (const uid of [s.inviterId, s.inviteeId]) {
      this.hub.sendTo(
        uid,
        serverMsg({ type: 'technology.downgrade', sessionId: s.id, technology: target }),
      );
    }
  }

  end(sessionId: string, byUserId: string): void {
    const s = this.sessions.get(sessionId);
    if (!s || isTerminal(s.state)) return;
    if (byUserId !== s.inviterId && byUserId !== s.inviteeId) {
      throw new SessionError('FORBIDDEN', 'not a participant');
    }
    this.transition(s, 'ENDED');
    // Notify the peer, echo state to the ender.
    this.hub.sendTo(
      this.peerOf(s, byUserId),
      serverMsg({ type: 'session.ended', sessionId: s.id, reason: 'peer-ended' }),
    );
    this.hub.sendTo(
      byUserId,
      serverMsg({ type: 'session.state', sessionId: s.id, state: 'ENDED' }),
    );
    this.drop(s);
  }

  /** Called when a user's WS disconnects: fail every non-terminal session they're in. */
  onDisconnect(userId: string): void {
    const ids = [...(this.byUser.get(userId) ?? [])];
    for (const id of ids) {
      const s = this.sessions.get(id);
      if (!s || isTerminal(s.state)) continue;
      this.fail(s, 'peer-disconnected');
    }
  }

  // ---- terminal helpers ----

  private expire(sessionId: string): void {
    const s = this.sessions.get(sessionId);
    if (!s || isTerminal(s.state)) return;
    this.transition(s, 'EXPIRED');
    this.end_(s, 'expired');
  }

  private fail(s: Session, reason: EndReason): void {
    if (isTerminal(s.state)) return;
    // FAILED is reachable from any non-terminal state.
    s.state = 'FAILED';
    if (s.timer) clearTimeout(s.timer);
    this.end_(s, reason);
  }

  private end_(s: Session, reason: EndReason): void {
    for (const uid of [s.inviterId, s.inviteeId]) {
      this.hub.sendTo(uid, serverMsg({ type: 'session.ended', sessionId: s.id, reason }));
    }
    this.drop(s);
  }

  private broadcastState(s: Session, state: SessionState): void {
    for (const uid of [s.inviterId, s.inviteeId]) {
      this.hub.sendTo(uid, serverMsg({ type: 'session.state', sessionId: s.id, state }));
    }
  }

  private require(sessionId: string, byUserId: string): Session {
    const s = this.sessions.get(sessionId);
    if (!s) throw new SessionError('NOT_FOUND', 'session not found');
    if (byUserId !== s.inviterId && byUserId !== s.inviteeId) {
      throw new SessionError('FORBIDDEN', 'not a participant');
    }
    return s;
  }
}
