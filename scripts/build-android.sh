#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo 'Usage: ./scripts/build-android.sh /path/to/runemobile [--compile-only|--debug]' >&2
  exit 2
fi
if [[ $# -eq 2 && "$2" != '--compile-only' && "$2" != '--debug' ]]; then
  echo "Unknown option: $2 (expected --compile-only or --debug)" >&2
  exit 2
fi
REPO="$(cd "$1" && pwd)"
if [[ ! -f "$REPO/runelite-client/build.gradle.kts" ]]; then
  echo 'Cannot find RuneMobile checkout.' >&2
  exit 2
fi
if [[ ! -f "$REPO/runelite-client/src/main/java/net/runelite/client/plugins/mobilemicrobot/MobileMicrobotPlugin.java" ]]; then
  echo 'Run install.sh first.' >&2
  exit 2
fi
cd "$REPO"
./gradlew :client:compileJava
if [[ "${2:-}" == '--compile-only' ]]; then
  echo 'RuneLite client Java compilation passed.'
  exit 0
fi
if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" && ! -f android/local.properties ]]; then
  echo 'Android SDK location not detected. Configure ANDROID_HOME or android/local.properties.' >&2
  exit 2
fi
# Explicitly follow audaro/runemobile staging order; never run bare assemble.
tools/stage-android-jre.sh
./gradlew :client:shadowJar
( cd ios/app && ./build-probes.sh )
android/dev-tools/gpu/stage-android-gpu-jar.sh
# Upstream's RLHD helper defaults to ~/.gradle, which is read-only in cloud tasks.
# Select the project-pinned processor from the active Gradle cache instead.
if [[ -z "${LOMBOK:-}" ]]; then
  LOMBOK="$(python3 - <<'PY'
import os, re
from pathlib import Path
version = re.search(r'^lombok\s*=\s*"([0-9.]+)"', Path('libs.versions.toml').read_text(), re.M)
if not version:
    raise SystemExit('Cannot read project-pinned Lombok version')
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
matches = list((cache / 'caches/modules-2/files-2.1/org.projectlombok/lombok' / version[1]).glob('*/*.jar'))
if len(matches) != 1:
    raise SystemExit('Expected one project-pinned Lombok jar after shadowJar')
print(matches[0])
PY
)"
  export LOMBOK
fi
android/dev-tools/gpu/stage-rlhd-zone-jar.sh
tools/stage-mobile-runtime.sh --no-build --android-only
if [[ "${2:-}" == '--debug' ]]; then
  ( cd android && ./gradlew :app:assembleDebug )
  echo 'Android debuggable APK assembled at android/app/build/outputs/apk/debug/app-debug.apk'
else
  ( cd android && ./gradlew :app:assembleRelease )
  echo 'Android release assembled. Locate APK under android/app/build/outputs/apk/release/.'
fi
echo 'Self-built APK is NOT signed by audaro official release key; do not compare its signature to the upstream release.'
