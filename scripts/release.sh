#!/usr/bin/env bash
# Build a signed APK and publish it as a GitHub release. The app's updater picks it up automatically.
# Usage: scripts/release.sh 0.2.0 "Release notes, first line is the headline"
# Needs: ~/.myboo/keystore.properties (release key; BACK IT UP, updates only install over the same key),
#        gh (logged in), and a JDK/Android SDK.
set -euo pipefail
VER="${1:?version like 0.2.0}"; NOTES="${2:-Update $VER}"
cd "$(dirname "$0")/../android"
./gradlew --no-daemon :app:assembleRelease -Pmyboo.version="$VER"
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "signed APK missing (is ~/.myboo/keystore.properties there?)"; exit 1; }
cp "$APK" "app/build/myboo.apk"
cd ..
gh release create "v$VER" "android/app/build/myboo.apk#myboo.apk" --title "MyBoo $VER" --notes "$NOTES"
