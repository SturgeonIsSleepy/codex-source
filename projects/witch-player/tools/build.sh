#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/.cache/android-cloud/sdk}"
TOOLS="$SDK/build-tools/36.0.0"
OUT="$PWD/app/build/${WITCH_TEST_BUILD:+test-}manual"
rm -rf -- "$OUT/classes" "$OUT/dex" "$OUT/gen" "$OUT/compiled"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$OUT/compiled"
python3 - "$OUT/AndroidManifest.xml" <<'PYMANIFEST'
import sys
from pathlib import Path
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
manifest = manifest.replace('<manifest ', '<manifest package="com.wanderingwitch.player" android:versionCode="10" android:versionName="1.8" ', 1)
import os
if os.environ.get('WITCH_TEST_BUILD'):
    manifest = manifest.replace('</manifest>', '<instrumentation android:name="com.wanderingwitch.player.PlayerIntegrationTest" android:targetPackage="com.wanderingwitch.player" /></manifest>')
Path(sys.argv[1]).write_text(manifest)
PYMANIFEST
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/compiled"
"$TOOLS/aapt2" link -o "$OUT/resources.apk" -I "$SDK/platforms/android-36/android.jar" \
  --manifest "$OUT/AndroidManifest.xml" --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 36 \
  -A app/src/main/assets "$OUT"/compiled/*.flat
mapfile -t sources < <(find app/src/main/java "$OUT/gen" -name '*.java')
if [ -n "${WITCH_TEST_BUILD:-}" ]; then
  mapfile -t test_sources < <(find app/src/androidTest/java -name '*.java')
  sources+=("${test_sources[@]}")
fi
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -source 17 -target 17 \
  -classpath "$SDK/platforms/android-36/android.jar" -d "$OUT/classes" "${sources[@]}"
java -m jdk.jartool/sun.tools.jar.Main --create --file "$OUT/classes.jar" -C "$OUT/classes" .
"$TOOLS/d8" --min-api 26 --lib "$SDK/platforms/android-36/android.jar" --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/resources.apk" "$OUT/unsigned.apk"
python3 - "$OUT" <<'PY'
import sys, zipfile
from pathlib import Path
out = Path(sys.argv[1])
with zipfile.ZipFile(out / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
    for dex in (out / 'dex').glob('*.dex'):
        archive.write(dex, dex.name)
PY
"$TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$PWD/app/build/debug.jks" ]; then
  keytool -genkeypair -keystore "$PWD/app/build/debug.jks" -storepass android -keypass android -alias androiddebugkey \
    -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks "$PWD/app/build/debug.jks" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/witch-player.apk" "$OUT/aligned.apk"
"$TOOLS/apksigner" verify "$OUT/witch-player.apk"
echo "APK: $OUT/witch-player.apk"
