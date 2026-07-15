export type Platform = 'ios' | 'android';
export type Technology = 'UWB' | 'BLE' | 'GPS';
export type Role = 'controller' | 'controlee';

export interface Capabilities {
  uwb: boolean;
  ble: boolean;
  gps: boolean;
}

export type SessionState =
  'INVITED' | 'ACCEPTED' | 'NEGOTIATED' | 'ACTIVE' | 'ENDED' | 'DECLINED' | 'EXPIRED' | 'FAILED';

export type EndReason =
  | 'peer-ended'
  | 'declined'
  | 'expired'
  | 'peer-disconnected'
  | 'no-common-technology'
  | 'ranging-failed';
