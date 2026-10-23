#!/usr/bin/env bash
set -euo pipefail
MODE=install
if [[ "${1:-}" == '--upgrade' ]]; then MODE=upgrade; shift; fi
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 [--upgrade] /path/to/runemobile" >&2
  exit 2
fi
REPO="$(cd "$1" 2>/dev/null && pwd)"
if [[ ! -f "$REPO/runelite-client/build.gradle.kts" || ! -f "$REPO/runelite-client/src/main/java/net/runelite/client/headless/JavaBridge.java" ]]; then
  echo 'This does not look like an audaro/runemobile source checkout.' >&2
  exit 2
fi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/runelite-client/src/main/java/net/runelite/client/plugins/mobilemicrobot"
DEST="$REPO/runelite-client/src/main/java/net/runelite/client/plugins/mobilemicrobot"
if [[ -e "$DEST" ]]; then
  if [[ "$MODE" != upgrade ]]; then
    echo "Existing mobilemicrobot sources at: $DEST" >&2
    echo 'Use --upgrade to back up and replace the previous starter, or merge manually.' >&2
    exit 1
  fi
  if [[ ! -f "$DEST/MobileMicrobotPlugin.java" ]] || ! grep -q 'package net.runelite.client.plugins.mobilemicrobot;' "$DEST/MobileMicrobotPlugin.java"; then
    echo 'Refusing to replace an unrecognized package. Merge changes manually.' >&2
    exit 1
  fi
  BACKUP="$REPO/runelite-client/build/mobilemicrobot-backup-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$(dirname "$BACKUP")"
  mv "$DEST" "$BACKUP"
  echo "Backed up old Java sources to: $BACKUP"
fi
mkdir -p "$DEST"
cp "$SRC"/*.java "$DEST"/
echo "Installed $(find "$DEST" -maxdepth 1 -name '*.java' | wc -l) Java files."
echo 'Compile with: ./gradlew :client:compileJava'
echo 'Or build Android using: /path/to/phase2/scripts/build-android.sh /path/to/runemobile'
