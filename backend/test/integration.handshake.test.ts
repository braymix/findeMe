import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { Capabilities, Platform } from '../src/session/types.js';
import { registerUser, startTestServer, TestClient, type TestServer } from './helpers.js';

const FULL: Capabilities = { uwb: true, ble: true, gps: true };

function hello(platform: Platform, caps: Capabilities = FULL) {
  return {
    v: 1,
    type: 'presence.hello',
    protocolVersion: '1.0.0',
    platform,
    capabilities: caps,
  } as const;
}

describe('end-to-end rendezvous handshake', () => {
  let s: TestServer;
  beforeEach(async () => {
    s = await startTestServer();
  });
  afterEach(async () => {
    await s.close();
  });

  it('completes UWB handshake between two same-platform peers and ends cleanly', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');

    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    a.send(hello('ios'));
    b.send(hello('ios'));

    // Alice invites Bob.
    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const incoming = (await b.waitForType('session.incoming')) as {
      sessionId: string;
      from: { username: string };
    };
    expect(incoming.from.username).toBe('alice');
    const sessionId = incoming.sessionId;

    // Bob accepts -> both negotiated as UWB.
    b.send({ v: 1, type: 'session.accept', sessionId });
    const aNeg = (await a.waitForType('session.negotiated')) as {
      technology: string;
      role: string;
    };
    const bNeg = (await b.waitForType('session.negotiated')) as {
      technology: string;
      role: string;
    };
    expect(aNeg.technology).toBe('UWB');
    expect(aNeg.role).toBe('controller');
    expect(bNeg.role).toBe('controlee');

    // Both send opaque ranging payloads; server exchanges them.
    a.send({
      v: 1,
      type: 'ranging.ready',
      sessionId,
      rangingPayload: { technology: 'UWB', blob: 'ALICE_TOKEN' },
    });
    b.send({
      v: 1,
      type: 'ranging.ready',
      sessionId,
      rangingPayload: { technology: 'UWB', blob: 'BOB_TOKEN' },
    });

    const aPeer = (await a.waitForType('ranging.peerPayload')) as {
      rangingPayload: { blob: string };
    };
    const bPeer = (await b.waitForType('ranging.peerPayload')) as {
      rangingPayload: { blob: string };
    };
    expect(aPeer.rangingPayload.blob).toBe('BOB_TOKEN'); // each gets the OTHER's token
    expect(bPeer.rangingPayload.blob).toBe('ALICE_TOKEN');

    const aActive = (await a.waitFor(
      (m) => m.type === 'session.state' && m.state === 'ACTIVE',
    )) as { state: string };
    expect(aActive.state).toBe('ACTIVE');
    await b.waitFor((m) => m.type === 'session.state' && m.state === 'ACTIVE');

    // Alice ends the session; Bob is notified.
    a.send({ v: 1, type: 'session.end', sessionId });
    const ended = (await b.waitForType('session.ended')) as { reason: string };
    expect(ended.reason).toBe('peer-ended');

    a.close();
    b.close();
  });

  it('routes a mixed-platform pair to BLE fallback (never UWB)', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    a.send(hello('ios'));
    b.send(hello('android'));

    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const incoming = (await b.waitForType('session.incoming')) as { sessionId: string };
    b.send({ v: 1, type: 'session.accept', sessionId: incoming.sessionId });

    const neg = (await a.waitForType('session.negotiated')) as { technology: string };
    expect(neg.technology).toBe('BLE');
    a.close();
    b.close();
  });

  it('propagates a decline', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    a.send(hello('ios'));
    b.send(hello('ios'));

    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const incoming = (await b.waitForType('session.incoming')) as { sessionId: string };
    b.send({ v: 1, type: 'session.decline', sessionId: incoming.sessionId });

    const ended = (await a.waitForType('session.ended')) as { reason: string };
    expect(ended.reason).toBe('declined');
    a.close();
    b.close();
  });

  it('performs a runtime UWB->BLE downgrade for both peers', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    a.send(hello('ios'));
    b.send(hello('ios'));
    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const incoming = (await b.waitForType('session.incoming')) as { sessionId: string };
    const sessionId = incoming.sessionId;
    b.send({ v: 1, type: 'session.accept', sessionId });
    await a.waitForType('session.negotiated');
    await b.waitForType('session.negotiated');
    a.send({
      v: 1,
      type: 'ranging.ready',
      sessionId,
      rangingPayload: { technology: 'UWB', blob: 'A' },
    });
    b.send({
      v: 1,
      type: 'ranging.ready',
      sessionId,
      rangingPayload: { technology: 'UWB', blob: 'B' },
    });
    await a.waitFor((m) => m.type === 'session.state' && m.state === 'ACTIVE');
    await b.waitFor((m) => m.type === 'session.state' && m.state === 'ACTIVE');

    // Alice loses UWB and reports a downgrade.
    a.send({ v: 1, type: 'technology.report', sessionId, technology: 'BLE', reason: 'uwb-lost' });
    const aDown = (await a.waitForType('technology.downgrade')) as { technology: string };
    const bDown = (await b.waitForType('technology.downgrade')) as { technology: string };
    expect(aDown.technology).toBe('BLE');
    expect(bDown.technology).toBe('BLE');
    a.close();
    b.close();
  });

  it('fails the survivor when a peer disconnects mid-session', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    a.send(hello('ios'));
    b.send(hello('ios'));
    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const incoming = (await b.waitForType('session.incoming')) as { sessionId: string };
    b.send({ v: 1, type: 'session.accept', sessionId: incoming.sessionId });
    await a.waitForType('session.negotiated');

    // Bob drops.
    b.close();
    const ended = (await a.waitForType('session.ended')) as { reason: string };
    expect(ended.reason).toBe('peer-disconnected');
    a.close();
  });

  it('rejects an offline peer invite', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    a.send(hello('ios'));
    a.send({ v: 1, type: 'session.invite', peerId: bob.id });
    const e = (await a.waitForType('error')) as { code: string };
    expect(e.code).toBe('OFFLINE');
    a.close();
  });

  it('emits presence updates to contacts', async () => {
    const alice = await registerUser(s, 'alice');
    const bob = await registerUser(s, 'bob');
    // alice adds bob as a contact so she receives his presence.
    await fetch(`${s.baseUrl}/contacts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${alice.accessToken}` },
      body: JSON.stringify({ username: 'bob' }),
    });
    const a = await TestClient.connect(s.wsUrl, alice.accessToken);
    a.send(hello('ios'));
    const b = await TestClient.connect(s.wsUrl, bob.accessToken);
    b.send(hello('ios'));
    const upd = (await a.waitForType('presence.update')) as { peerId: string; online: boolean };
    expect(upd.peerId).toBe(bob.id);
    expect(upd.online).toBe(true);
    a.close();
    b.close();
  });
});
