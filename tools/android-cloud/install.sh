#!/bin/sh
set -eu
kit_source=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
kit_target=${ANDROID_CLOUD_INSTALL_DIR:-"$HOME/.local/bin"}
mkdir -p "$kit_target"
if [ "$kit_source/android-cloud" != "$kit_target/android-cloud" ]; then
    cp "$kit_source/android-cloud" "$kit_target/android-cloud"
fi
chmod +x "$kit_target/android-cloud"
printf 'Installed: %s/android-cloud\n' "$kit_target"
printf 'Run: %s/android-cloud up --accept-licenses\n' "$kit_target"
