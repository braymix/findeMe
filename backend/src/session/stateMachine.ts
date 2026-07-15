import type { SessionState } from './types.js';

/**
 * Pure session state machine (ADR-0007). No I/O, no timers — those live in the
 * SessionManager. This module only answers: "is this transition legal?".
 *
 *   INVITED → ACCEPTED | DECLINED | EXPIRED
 *   ACCEPTED → NEGOTIATED | EXPIRED | FAILED
 *   NEGOTIATED → ACTIVE | EXPIRED | FAILED
 *   ACTIVE → ENDED | FAILED           (self-loops for relay/downgrade are not transitions)
 *   ENDED | DECLINED | EXPIRED | FAILED → (terminal)
 */
const TRANSITIONS: Record<SessionState, readonly SessionState[]> = {
  INVITED: ['ACCEPTED', 'DECLINED', 'EXPIRED', 'FAILED'],
  ACCEPTED: ['NEGOTIATED', 'EXPIRED', 'FAILED'],
  NEGOTIATED: ['ACTIVE', 'EXPIRED', 'FAILED'],
  ACTIVE: ['ENDED', 'FAILED'],
  ENDED: [],
  DECLINED: [],
  EXPIRED: [],
  FAILED: [],
};

const TERMINAL: ReadonlySet<SessionState> = new Set<SessionState>([
  'ENDED',
  'DECLINED',
  'EXPIRED',
  'FAILED',
]);

export function canTransition(from: SessionState, to: SessionState): boolean {
  return TRANSITIONS[from].includes(to);
}

export function isTerminal(state: SessionState): boolean {
  return TERMINAL.has(state);
}

export class InvalidTransitionError extends Error {
  constructor(
    public readonly from: SessionState,
    public readonly to: SessionState,
  ) {
    super(`Illegal session transition ${from} -> ${to}`);
    this.name = 'InvalidTransitionError';
  }
}

export function assertTransition(from: SessionState, to: SessionState): void {
  if (!canTransition(from, to)) throw new InvalidTransitionError(from, to);
}
