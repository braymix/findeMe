# Download — Android app

The Android app (`uwb-peer-compass.apk`) is **built automatically** by CI and points at the
production backend **https://findeme.onrender.com**. It is not committed to the repo as a
binary (git is the wrong place for build outputs); instead every release publishes it.

> **Why there's no `.apk` file sitting here:** an APK must be compiled with the Android SDK,
> which can't run in every environment. The build is done by GitHub Actions
> (`.github/workflows/release.yml`) so the artifact is always reproducible and up to date.

## Get the app

### Option A — from a GitHub Release (recommended)
1. Create a release by pushing a tag from `main`:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
   The `Release apps` workflow builds `uwb-peer-compass.apk` and attaches it to the
   **Releases** page.
2. On your Android phone open the Releases page and download `uwb-peer-compass.apk`, or run
   the helper from a computer with the GitHub CLI:
   ```bash
   ./download-latest.sh          # saves uwb-peer-compass.apk into this folder
   ```

### Option B — build it yourself
```bash
cd ../../android
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## Install on the phone
1. Copy the APK to the device (or download it directly on the phone).
2. Enable **Install unknown apps** for your browser/file manager
   (Settings ▸ Apps ▸ Special access ▸ Install unknown apps).
3. Tap the APK and install. Open **UWB Peer Compass**, register an account, add a contact,
   and start a session. The app talks to `https://findeme.onrender.com` out of the box.

## Notes
- This CI build is signed with the debug key unless you add your own keystore secrets
  (`ANDROID_KEYSTORE_*`) — fine for testing/sideloading, replace it with your key before a
  Play Store release.
- UWB works only on UWB-capable phones; others automatically use the BLE/GPS fallback.
- Requires two phones running the app to see the compass in action.
