#!/usr/bin/env bash
# Wakes the MotivBot pipeline on Hugging Face Spaces, builds the iOS app,
# and launches it in the iOS Simulator.
#
# Usage:
#   ./run_ios.sh                        # default simulator: iPhone 17
#   SIMULATOR="iPhone 16 Pro" ./run_ios.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SIMULATOR="${SIMULATOR:-iPhone 17}"
SPACE_URL="https://flightmaestro-motivbot-pipeline.hf.space"
DERIVED_DATA="$ROOT/.build/DerivedData"
mkdir -p "$ROOT/.build"

# 1. Wake the Space in the background; a sleeping Space answers 503 for up to a minute.
curl -s -o /dev/null -m 90 "$SPACE_URL/gradio_api/info" &

# 2. Simulator
SIM_ID="$(xcrun simctl list devices available -j | python3 -c '
import json, sys
name = sys.argv[1]
for runtime, devices in json.load(sys.stdin)["devices"].items():
    for d in devices:
        if d["name"] == name and "iOS" in runtime:
            print(d["udid"]); sys.exit()
' "$SIMULATOR")"
if [[ -z "$SIM_ID" ]]; then
    echo "No available simulator named \"$SIMULATOR\"" >&2
    exit 1
fi
echo "Booting $SIMULATOR ($SIM_ID)"
xcrun simctl boot "$SIM_ID" 2>/dev/null || true
open -a Simulator

# 3. Build
echo "Building MotivBot..."
xcodebuild -project "$ROOT/MotivBot.xcodeproj" -scheme MotivBot -configuration Debug \
    -destination "id=$SIM_ID" -derivedDataPath "$DERIVED_DATA" build -quiet

APP="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/MotivBot.app"
BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$APP/Info.plist")"

# 4. Install and launch
xcrun simctl install "$SIM_ID" "$APP"
xcrun simctl launch --terminate-running-process "$SIM_ID" "$BUNDLE_ID"
