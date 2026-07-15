import { describe, expect, it } from 'vitest';
import {
  assertTransition,
  canTransition,
  InvalidTransitionError,
  isTerminal,
} from '../src/session/stateMachine.js';

describe('session state machine', () => {
  it('allows the happy path', () => {
    expect(canTransition('INVITED', 'ACCEPTED')).toBe(true);
    expect(canTransition('ACCEPTED', 'NEGOTIATED')).toBe(true);
    expect(canTransition('NEGOTIATED', 'ACTIVE')).toBe(true);
    expect(canTransition('ACTIVE', 'ENDED')).toBe(true);
  });

  it('allows decline/expire/fail branches', () => {
    expect(canTransition('INVITED', 'DECLINED')).toBe(true);
    expect(canTransition('INVITED', 'EXPIRED')).toBe(true);
    expect(canTransition('NEGOTIATED', 'EXPIRED')).toBe(true);
    expect(canTransition('ACTIVE', 'FAILED')).toBe(true);
  });

  it('rejects illegal transitions', () => {
    expect(canTransition('INVITED', 'ACTIVE')).toBe(false);
    expect(canTransition('ENDED', 'ACTIVE')).toBe(false);
    expect(canTransition('ACTIVE', 'INVITED')).toBe(false);
  });

  it('marks terminal states', () => {
    for (const s of ['ENDED', 'DECLINED', 'EXPIRED', 'FAILED'] as const)
      expect(isTerminal(s)).toBe(true);
    for (const s of ['INVITED', 'ACCEPTED', 'NEGOTIATED', 'ACTIVE'] as const)
      expect(isTerminal(s)).toBe(false);
  });

  it('assertTransition throws on illegal', () => {
    expect(() => assertTransition('INVITED', 'ACTIVE')).toThrow(InvalidTransitionError);
    expect(() => assertTransition('INVITED', 'ACCEPTED')).not.toThrow();
  });
});
