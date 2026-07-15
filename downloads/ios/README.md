# Download — iOS app

The iOS app points at the production backend **https://findeme.onrender.com**. Unlike
Android, iOS **cannot** be distributed as a plain "download and tap to install" file: Apple
requires every app to be **code-signed** with an Apple Developer account and delivered
through **TestFlight** or the App Store. There is no way around this — not in this
environment, not in CI. So there is intentionally no `.ipa` sitting in this folder.

Here's exactly how to get it onto a phone.

## Path A — TestFlight (recommended, up to 10,000 testers)
Requirements: an **Apple Developer Program** membership ($99/yr).

1. **One-time secrets** — add these to the repo (Settings ▸ Secrets ▸ Actions) so CI can sign:
   - `APPLE_CERTIFICATE_BASE64` + `APPLE_CERTIFICATE_PASSWORD` (your distribution cert .p12)
   - `APPLE_PROVISIONING_PROFILE_BASE64`
   - `APP_STORE_CONNECT_API_KEY` (issuer id + key id + .p8) for upload
2. Generate the Xcode project and archive:
   ```bash
   cd ../../ios
   brew install xcodegen
   xcodegen generate                 # creates UWBPeerCompass.xcodeproj
   open UWBPeerCompass.xcodeproj      # or archive via xcodebuild / fastlane
   ```
3. In Xcode: select a **real device**, set your Team under Signing & Capabilities, enable the
   **Nearby Interaction** capability, then **Product ▸ Archive ▸ Distribute ▸ TestFlight**.
   The `Release apps` workflow's `ios-ipa` job does the same automatically once the secrets
   above are present.
4. Testers install **TestFlight** from the App Store and open your invite link.

## Path B — Personal device install (free, for yourself, 7-day apps)
With a free Apple ID and Xcode on a Mac:
```bash
cd ../../ios
xcodegen generate
open UWBPeerCompass.xcodeproj
```
Select your iPhone, set your personal team, and **Product ▸ Run**. The app installs for 7
days (Apple's free-provisioning limit), after which re-run to renew.

## Why UWB needs real hardware
NearbyInteraction (the precise arrow) does not work in the Simulator and needs a U1/U2
iPhone (iPhone 11 and later). You need **two** iPhones to see the compass point at each
other. Non-UWB pairs fall back to BLE/GPS automatically.

## Endpoint
The app defaults to `https://findeme.onrender.com`. For local testing, set `BACKEND_HTTP` /
`BACKEND_WS` env vars in the Xcode scheme (see `ios/App/BackendConfig.swift`).
