#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PATH="${CARGO_HOME:-$HOME/.cargo}/bin:$PATH"
case "${1:-release}" in
  debug) target=assembleDebug ;;
  fast) target=assembleFastRelease ;;
  release) target=assembleRelease ;;
  *) echo 'Usage: tools/build.sh [debug|fast|release]' >&2; exit 2 ;;
esac
command -v cargo >/dev/null || { echo 'Install Rust and cargo-ndk first.' >&2; exit 1; }
command -v cmake >/dev/null || { echo 'CMake must be available on PATH.' >&2; exit 1; }
./gradlew "$target" --console=plain
