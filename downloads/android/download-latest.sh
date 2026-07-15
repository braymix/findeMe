#!/usr/bin/env bash
# Downloads the latest built Android APK from this repo's GitHub Releases into this folder.
# Requires either the GitHub CLI (`gh`) or `curl`.
set -euo pipefail

REPO="${REPO:-braymix/findeMe}"
ASSET="uwb-peer-compass.apk"
cd "$(dirname "$0")"

if command -v gh >/dev/null 2>&1; then
  echo "Fetching latest '$ASSET' from $REPO via gh..."
  gh release download --repo "$REPO" --pattern "$ASSET" --clobber
else
  echo "gh not found, falling back to curl (public releases only)..."
  url=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
        | grep -o "https://[^\"]*$ASSET" | head -n1)
  if [ -z "$url" ]; then
    echo "No release asset named $ASSET found. Push a tag (e.g. v1.0.0) to trigger a build." >&2
    exit 1
  fi
  curl -fL "$url" -o "$ASSET"
fi

echo "Saved ./$ASSET — copy it to your Android phone and install."
