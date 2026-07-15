import type { Capabilities, Platform, Technology } from './types.js';

export interface DeviceProfile {
  platform: Platform;
  capabilities: Capabilities;
}

/**
 * Pick the best common ranging technology for a pair of devices (ADR-0003).
 *
 * Order: UWB > BLE > GPS.
 * UWB is INTRA-PLATFORM ONLY — mixed iOS/Android pairs never get UWB (ADR-0008),
 * because consumer UWB session stacks are not interoperable across the two OSes.
 *
 * Returns null when there is no common technology (session must FAIL).
 * This function is pure and fully unit-tested (no hardware required).
 */
export function negotiate(a: DeviceProfile, b: DeviceProfile): Technology | null {
  if (a.capabilities.uwb && b.capabilities.uwb && a.platform === b.platform) {
    return 'UWB';
  }
  if (a.capabilities.ble && b.capabilities.ble) {
    return 'BLE';
  }
  if (a.capabilities.gps && b.capabilities.gps) {
    return 'GPS';
  }
  return null;
}

/**
 * Validate a proposed runtime downgrade. A downgrade is legal only when the target
 * technology is strictly lower priority than the current one AND is a common
 * capability of both devices. Upgrades are never accepted at runtime (ADR-0003).
 */
const PRIORITY: Record<Technology, number> = { UWB: 3, BLE: 2, GPS: 1 };

export function isLegalDowngrade(
  current: Technology,
  target: Technology,
  a: DeviceProfile,
  b: DeviceProfile,
): boolean {
  if (PRIORITY[target] >= PRIORITY[current]) return false;
  if (target === 'BLE') return a.capabilities.ble && b.capabilities.ble;
  if (target === 'GPS') return a.capabilities.gps && b.capabilities.gps;
  return false;
}
