#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
python3 "$ROOT/tests/generate_stubs.py" "$BUILD/stubs"
mkdir -p "$BUILD/classes"
find "$BUILD/stubs" "$ROOT/runelite-client/src/main/java/net/runelite/client/plugins/mobilemicrobot" -name '*.java' > "$BUILD/sources.txt"
printf '%s\n' "$ROOT/tests/PortableChecks.java" >> "$BUILD/sources.txt"
javac --release 21 -d "$BUILD/classes" @"$BUILD/sources.txt"
java -ea -cp "$BUILD/classes" net.runelite.client.plugins.mobilemicrobot.PortableChecks
