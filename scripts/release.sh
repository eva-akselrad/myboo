#!/usr/bin/env bash
# Build a signed APK and publish it as a GitHub release. The app's updater picks it up automatically.
# Usage: scripts/release.sh 0.2.0 "Release notes, first line is the headline"
# Needs: ~/.myboo/keystore.properties (release key; BACK IT UP, updates only install over the same key),
#        gh (logged in), and a JDK/Android SDK. Uses android/app/google-services.json if present.
set -euo pipefail
VER="${1:?version like 0.2.0}"; NOTES="${2:-Update $VER}"
cd "$(dirname "$0")/../android"
GS=app/google-services.json; STUB=0
if [ ! -f "$GS" ]; then
  STUB=1
  cat > "$GS" <<'JSON'
{"project_info":{"project_number":"000000000000","project_id":"myboo-stub"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000","android_client_info":{"package_name":"com.myboo.app"}},"api_key":[{"current_key":"stub"}]}],"configuration_version":"1"}
JSON
  echo "NOTE: no google-services.json, building with a stub (push notifications will not work)."
fi
trap '[ "$STUB" = 1 ] && rm -f "$GS"' EXIT
./gradlew --no-daemon :app:assembleRelease -Pmyboo.version="$VER"
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "signed APK missing (is ~/.myboo/keystore.properties there?)"; exit 1; }
cp "$APK" "app/build/myboo.apk"
cd ..
gh release create "v$VER" "android/app/build/myboo.apk#myboo.apk" --title "MyBoo $VER" --notes "$NOTES"
