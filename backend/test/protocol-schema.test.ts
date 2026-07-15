import Ajv from 'ajv';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const here = dirname(fileURLToPath(import.meta.url));
const schema = JSON.parse(
  readFileSync(resolve(here, '../../shared/schemas/messages.schema.json'), 'utf8'),
);

// draft-07; allow the format keyword to be lenient.
const ajv = new Ajv({ allErrors: true, strict: false });
const validate = ajv.compile(schema);

describe('shared wire-protocol schema', () => {
  const valid = [
    {
      v: 1,
      type: 'presence.hello',
      protocolVersion: '1.0.0',
      platform: 'ios',
      capabilities: { uwb: true, ble: true, gps: true },
    },
    { v: 1, type: 'presence.heartbeat' },
    { v: 1, type: 'session.invite', peerId: 'u1' },
    { v: 1, type: 'session.accept', sessionId: 's1' },
    {
      v: 1,
      type: 'ranging.ready',
      sessionId: 's1',
      rangingPayload: { technology: 'UWB', blob: 'AAAA' },
    },
    { v: 1, type: 'technology.report', sessionId: 's1', technology: 'BLE', reason: 'uwb-lost' },
    { v: 1, type: 'session.negotiated', sessionId: 's1', technology: 'UWB', role: 'controller' },
    {
      v: 1,
      type: 'ranging.peerPayload',
      sessionId: 's1',
      rangingPayload: { technology: 'UWB', blob: 'BBBB' },
    },
    { v: 1, type: 'technology.downgrade', sessionId: 's1', technology: 'BLE' },
    { v: 1, type: 'session.ended', sessionId: 's1', reason: 'peer-ended' },
    { v: 1, type: 'session.incoming', sessionId: 's1', from: { id: 'u1', username: 'alice' } },
    { v: 1, type: 'error', code: 'X', message: 'y' },
  ];

  for (const m of valid) {
    it(`accepts ${m.type}`, () => {
      const ok = validate(m);
      if (!ok) console.error(validate.errors);
      expect(ok).toBe(true);
    });
  }

  const invalid = [
    { v: 2, type: 'presence.heartbeat' }, // wrong major
    { v: 1, type: 'session.invite' }, // missing peerId
    { v: 1, type: 'session.negotiated', sessionId: 's1', technology: 'WIFI', role: 'controller' }, // bad tech
    { v: 1, type: 'session.ended', sessionId: 's1', reason: 'bogus' }, // bad reason
  ];

  for (const [i, m] of invalid.entries()) {
    it(`rejects invalid #${i}`, () => {
      expect(validate(m)).toBe(false);
    });
  }
});
