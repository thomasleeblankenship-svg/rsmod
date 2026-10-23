#!/usr/bin/env python3
"""Compile adapter against built RuneMobile API, with client-only stand-ins.

This is an intermediate API check, NOT :client:compileJava or a runtime test.
First run ./gradlew :runelite-api:compileJava with a JDK on PATH.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
api = root / "runelite-api/build/classes/java/main"
if not (api / "net/runelite/api/Client.class").is_file():
    sys.exit("Build real API classes first: ./gradlew :runelite-api:compileJava")
with tempfile.TemporaryDirectory() as directory:
    build = Path(directory)
    subprocess.run([sys.executable, str(root / "tests/generate_stubs.py"),
                    str(build / "stubs")], check=True)
    sources = [str(p) for p in (build / "stubs").rglob("*.java")
               if not p.relative_to(build / "stubs").as_posix().startswith("net/runelite/api/")]
    sources += [str(p) for p in (root / "runelite-client/src/main/java/net/runelite/client/plugins/mobilemicrobot").glob("*.java")]
    sources.append(str(root / "runelite-client/src/main/java/net/runelite/client/headless/ScriptInputSession.java"))
    subprocess.run(["javac", "--release", "11", "-cp", str(api),
                    "-d", str(build / "classes"), *sources], check=True)
print("PASS: real RuneMobile API surface; client/library stand-ins remain. Full client compilation is still required.")
