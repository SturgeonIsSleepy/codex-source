#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/.cache/android-cloud/sdk}"
OUT="$PWD/app/build/motion-check"
mkdir -p "$OUT"
java -m jdk.compiler/com.sun.tools.javac.Main -cp "$SDK/platforms/android-36/android.jar" -d "$OUT" \
  app/src/main/java/com/wanderingwitch/player/CharacterMotion.java \
  app/src/main/java/com/wanderingwitch/player/AudioRhythm.java tools/host-check/MotionCheck.java
java -cp "$OUT" com.wanderingwitch.player.MotionCheck
