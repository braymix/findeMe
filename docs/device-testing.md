# On-device test matrix & manual checklist

UWB and BLE ranging **cannot** be validated in CI, a simulator, or an emulator. Everything
marked `VERIFY-ON-DEVICE` in the code must be checked on real hardware using this checklist.

## Device matrix

| Pair | Devices | Expected technology | What to verify |
|------|---------|---------------------|----------------|
| iOS ↔ iOS (UWB) | 2× iPhone 11+ (U1) or 15+ (U2) | **UWB** | Precise arrow + distance; arrow tracks as you rotate the phone. |
| Android ↔ Android (UWB) | 2× Pixel 6 Pro+/Galaxy S21+ with UWB | **UWB** | Same as above via Jetpack UWB. |
| iOS ↔ Android | any iPhone + any Android | **BLE** (never UWB) | Proximity ring only, no arrow; confirms ADR-0008 routing. |
| UWB ↔ non-UWB (same OS) | e.g. iPhone 15 + iPhone SE | **BLE** | Falls back; no crash; ring UI. |
| non-UWB ↔ non-UWB | 2× budget phones | **BLE**, else **GPS** | Fallback ladder; outdoors GPS bearing. |

## Capability & permission checks
- [ ] Non-UWB device installs and runs (UWB feature `required=false`); lands in fallback.
- [ ] iOS: Nearby Interaction prompt appears **after** the priming screen, with our copy.
- [ ] Android 12+: BLE scan/advertise/connect + fine-location prompts appear after priming.
- [ ] Denying a permission degrades gracefully (e.g. no BLE ⇒ GPS or clear message), no crash.

## Rendezvous & session flow (real backend)
- [ ] Register/login on both devices; each appears online in the other's peer list.
- [ ] A invites B → B sees the consent dialog → accept → both reach the compass.
- [ ] Decline path returns A to the peer list with a clear reason.
- [ ] Invite timeout (~30 s no answer) expires cleanly on both sides.
- [ ] Ending on either side returns both to the peer list and stops ranging.
- [ ] Backgrounding the app stops ranging (check radios/logs); foreground resumes/recovers.
- [ ] Kill B's app mid-session → A shows "peer disconnected" and returns to the list.

## Ranging quality (UWB)
- [ ] Distance accuracy within ~±10 cm at 1-5 m (tape-measure check).
- [ ] Arrow points correctly at 0°/±90°/180° relative bearings.
- [ ] Arrow stays glued to the peer as you rotate in place (fusion working).
- [ ] Optional ARKit EDM flag improves direction at close range / off-axis.

## Degradation (UWB → BLE)
- [ ] Occlude/withdraw UWB (walk behind a wall) → after a few seconds the UI switches to the
      proximity ring and the tech badge flips to **BLE** on **both** devices.
- [ ] No upgrade flapping: it does not silently jump back to UWB mid-session.

## VERIFY-ON-DEVICE items (from the code)
- [ ] iOS `NIRangingProvider`: `NISession` setup, discovery-token exchange, direction axis
      convention, ARKit EDM fusion.
- [ ] Android `UwbRangingProvider`: controller/controlee setup, exact `RangingParameters`
      (complex channel, preamble, session key, STS), session-param blob format.
- [ ] BLE `txPowerDbm` per-model RSSI-at-1m calibration (both platforms).

## Calibration notes
Record the measured RSSI at 1 m for each phone model and update `txPowerDbm`. Note the
path-loss exponent used indoors vs outdoors; the log-distance model lives in
`BleDistanceModel` (both platforms) and is the single place to tune.
