import { describe, expect, it } from 'vitest';
import { isLegalDowngrade, negotiate, type DeviceProfile } from '../src/session/negotiate.js';

const dev = (
  platform: 'ios' | 'android',
  caps: Partial<DeviceProfile['capabilities']>,
): DeviceProfile => ({
  platform,
  capabilities: { uwb: false, ble: false, gps: false, ...caps },
});

describe('negotiate', () => {
  it('picks UWB for same-platform UWB-capable pair', () => {
    expect(
      negotiate(dev('ios', { uwb: true, ble: true }), dev('ios', { uwb: true, ble: true })),
    ).toBe('UWB');
    expect(negotiate(dev('android', { uwb: true }), dev('android', { uwb: true }))).toBe('UWB');
  });

  it('never picks UWB for mixed platforms even if both have UWB (ADR-0008)', () => {
    expect(
      negotiate(dev('ios', { uwb: true, ble: true }), dev('android', { uwb: true, ble: true })),
    ).toBe('BLE');
  });

  it('falls back to BLE when only one side has UWB', () => {
    expect(
      negotiate(dev('ios', { uwb: true, ble: true }), dev('ios', { uwb: false, ble: true })),
    ).toBe('BLE');
  });

  it('falls back to GPS when BLE is unavailable', () => {
    expect(negotiate(dev('ios', { gps: true }), dev('android', { gps: true }))).toBe('GPS');
  });

  it('returns null when there is no common technology', () => {
    expect(negotiate(dev('ios', { uwb: true }), dev('android', { gps: true }))).toBeNull();
  });
});

describe('isLegalDowngrade', () => {
  const bothBleGps = [
    dev('ios', { uwb: true, ble: true, gps: true }),
    dev('ios', { uwb: true, ble: true, gps: true }),
  ] as const;

  it('allows UWB -> BLE', () => {
    expect(isLegalDowngrade('UWB', 'BLE', bothBleGps[0], bothBleGps[1])).toBe(true);
  });
  it('allows BLE -> GPS', () => {
    expect(isLegalDowngrade('BLE', 'GPS', bothBleGps[0], bothBleGps[1])).toBe(true);
  });
  it('rejects upgrades', () => {
    expect(isLegalDowngrade('BLE', 'UWB', bothBleGps[0], bothBleGps[1])).toBe(false);
    expect(isLegalDowngrade('GPS', 'BLE', bothBleGps[0], bothBleGps[1])).toBe(false);
  });
  it('rejects downgrade to a technology a device lacks', () => {
    const a = dev('ios', { uwb: true, ble: true });
    const b = dev('ios', { uwb: true }); // no BLE
    expect(isLegalDowngrade('UWB', 'BLE', a, b)).toBe(false);
  });
});
