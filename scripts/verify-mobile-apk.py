#!/usr/bin/env python3
"""Verify a RuneMobile debug APK against an explicit Git revision and compiled classes.

Uses Python's standard library plus Android SDK build-tools. Never extracts or
modifies the APK, downloads dependencies, or treats queued input as device proof.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import sys
import zipfile


class VerificationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def command(args, cwd=None):
    try:
        result = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise VerificationError(f"Could not run {Path(args[0]).name}: {type(error).__name__}") from error
    require(result.returncode == 0, f"{Path(args[0]).name} failed (exit {result.returncode})")
    return result.stdout


def git(repo, *args):
    return command(["git", *args], cwd=repo)


def unique_entries(archive, label):
    names = archive.namelist()
    require(len(names) == len(set(names)), f"Duplicate ZIP entries in {label}")
    return names


def selected_class(name):
    return name.endswith(".class") and (
        name.startswith("net/runelite/client/plugins/microbot/")
        or name.startswith("net/runelite/client/plugins/mobilemicrobot/")
        or re.fullmatch(r"net/runelite/client/headless/(JavaBridge|ScriptInputSession)(\$[^/]+)?\.class", name)
    )


def build_tools(explicit):
    if explicit:
        candidates = [Path(explicit).expanduser().resolve()]
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        require(sdk, "Set ANDROID_HOME/ANDROID_SDK_ROOT or provide --build-tools")
        root = Path(sdk) / "build-tools"
        candidates = sorted(
            (p for p in root.glob("*") if re.fullmatch(r"\d+\.\d+\.\d+", p.name)),
            key=lambda p: tuple(map(int, p.name.split("."))), reverse=True,
        )
    for path in candidates:
        if all((path / name).is_file() for name in ("apksigner", "zipalign", "aapt")):
            return path
    raise VerificationError("Android build-tools missing; provide --build-tools with apksigner, zipalign and aapt")


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def resolve_commit(repo, value):
    require(re.fullmatch(r"[0-9a-fA-F]{7,40}", value), "--expected-commit must be a 7–40 character Git commit hash")
    return git(repo, "rev-parse", "--verify", f"{value}^{{commit}}").strip()


def verify_artifact(args):
    artifact = args.apk.resolve()
    require(artifact.is_file(), f"Artifact ZIP does not exist: {artifact}")
    require(not args.json_out or args.json_out.resolve() != artifact, "JSON output must not overwrite the artifact ZIP")
    initial = artifact.stat()
    commit = resolve_commit(args.repo.resolve(), args.expected_commit)
    with zipfile.ZipFile(artifact) as archive:
        names = unique_entries(archive, "artifact ZIP")
        apks = [n for n in names if not n.endswith("/") and n.lower().endswith(".apk")]
        require(len(apks) == 1, "Artifact must contain exactly one APK")
        member = PurePosixPath(apks[0])
        require(not member.is_absolute() and ".." not in member.parts and "\\" not in apks[0],
                "Unsafe APK member path in artifact")
        source = str(member.parent / "SOURCE_COMMIT.txt")
        sums = str(member.parent / "SHA256SUMS.txt")
        require(source in names and sums in names, "Artifact needs SOURCE_COMMIT.txt and SHA256SUMS.txt beside the APK")
        require(all(archive.getinfo(n).file_size <= 65536 for n in (source, sums)), "Artifact metadata is unexpectedly large")
        declared = archive.read(source).decode("utf-8").strip().lower()
        require(re.fullmatch(r"[0-9a-f]{40}", declared) and declared == commit,
                "Artifact SOURCE_COMMIT.txt does not match the expected source revision")
        matching = []
        for line in archive.read(sums).decode("utf-8").splitlines():
            if not line.strip():
                continue
            entry = re.fullmatch(r"([0-9a-fA-F]{64}) [ *](.+)", line)
            require(entry, "Invalid SHA256SUMS.txt entry")
            if entry[2] in (member.name, str(member)):
                matching.append(entry[1].lower())
        require(len(matching) == 1, "SHA256SUMS.txt must identify the artifact APK exactly once")
        with tempfile.TemporaryDirectory(prefix="runemobile-apk-verify-") as directory:
            temporary = Path(directory) / "app.apk"
            # Copy a single member to a fixed temporary name; never extract member paths.
            with archive.open(apks[0]) as source_stream, temporary.open("wb") as output:
                shutil.copyfileobj(source_stream, output, 1024 * 1024)
            require(file_sha256(temporary) == matching[0], "Artifact APK checksum does not match SHA256SUMS.txt")
            nested = argparse.Namespace(**dict(vars(args), apk=temporary, json_out=None))
            report = verify(nested)
    digest = file_sha256(artifact)
    final = artifact.stat()
    require((initial.st_ino, initial.st_size, initial.st_mtime_ns) ==
            (final.st_ino, final.st_size, final.st_mtime_ns), "Artifact ZIP changed while verification was running")
    report.update(artifact_zip=str(artifact), artifact_zip_bytes=final.st_size,
                  artifact_zip_sha256=digest, apk=str(member),
                  artifact_checksum_verified=True, source_commit_file_verified=True)
    return report


def verify(args):
    repo = args.repo.resolve()
    apk = args.apk.resolve()
    classes = (args.classes_dir or repo / "runelite-client/build/classes/java/main").resolve()
    require(apk.is_file(), f"APK does not exist: {apk}")
    initial = apk.stat()
    require(not args.json_out or args.json_out.resolve() != apk, "JSON output must not overwrite the APK")
    commit = resolve_commit(repo, args.expected_commit)
    expected = {p.relative_to(classes).as_posix(): p for p in classes.rglob("*.class")
                if selected_class(p.relative_to(classes).as_posix())}
    for name in ("plugins/mobilemicrobot/MobileMicrobotPlugin", "plugins/microbot/MicrobotRuntime",
                 "headless/JavaBridge", "headless/ScriptInputSession"):
        require(f"net/runelite/client/{name}.class" in expected,
                f"Required compiled class missing: {name}; compile the expected revision or use --classes-dir")
    resource_root = "runelite-client/src/main/resources/"
    resources = [p for p in git(repo, "ls-tree", "-r", "--name-only", commit, "--", resource_root).splitlines()
                 if "collision" in p.lower()]
    require(resources, "Expected revision has no tracked collision resources")
    with zipfile.ZipFile(apk) as outer:
        names = unique_entries(outer, "APK")
        require(not any(Path(n).name.lower() == "tokens.json" for n in names), "APK bundles tokens.json")
        jars = [n for n in names if n.startswith("assets/jvm/") and n.endswith("-gpu.jar")]
        require(len(jars) == 1, "Expected exactly one assets/jvm/*-gpu.jar")
        with zipfile.ZipFile(io.BytesIO(outer.read(jars[0]))) as jar:
            entries = unique_entries(jar, "GPU client jar")
            properties = dict(line.split("=", 1) for line in jar.read("net/runelite/client/runelite.properties").decode().splitlines()
                              if "=" in line and not line.startswith(("#", "!")))
            embedded = properties.get("runelite.commit", "").strip().lower()
            require(re.fullmatch(r"[0-9a-f]{7,40}", embedded) and commit.startswith(embedded),
                    f"Source commit mismatch: APK={embedded or 'missing'}, expected={commit}")
            require(properties.get("runelite.dirty", "").strip() == "false", "Packaged client is dirty or lacks runelite.dirty=false")
            packaged = {n for n in entries if selected_class(n)}
            missing = sorted(set(expected) - packaged)
            extra = sorted(packaged - set(expected))
            require(not missing and not extra,
                    f"Compiled class inventory mismatch: missing={missing[:5]}, extra={extra[:5]}")
            for name, path in sorted(expected.items()):
                require(jar.read(name) == path.read_bytes(), f"Compiled class differs: {name}")
            for path in resources:
                name = path[len(resource_root):]
                require(name in entries, f"Collision resource missing: {name}")
                # Binary Git output, never shell interpolation or a working-tree substitute.
                result = subprocess.run(["git", "show", f"{commit}:{path}"], cwd=repo,
                                        capture_output=True, timeout=120)
                require(result.returncode == 0, f"Cannot read collision resource at expected commit: {name}")
                require(jar.read(name) == result.stdout, f"Collision resource differs: {name}")
    tools = build_tools(args.build_tools)
    signature = command([str(tools / "apksigner"), "verify", "--verbose", str(apk)])
    require(re.search(r"Verified using v(?:2|3(?:\.1)?) scheme[^\n]*:\s*true", signature),
            "APK needs a verified v2 or newer signing scheme")
    command([str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk)])
    badging = command([str(tools / "aapt"), "dump", "badging", str(apk)])
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", badging, re.M)
    require(package and package[1] == "dev.audaro.runemobile", "Unexpected or missing RuneMobile package name")
    require(re.search(r"^application-debuggable\s*$", badging, re.M), "APK is not debuggable")
    abi = re.search(r"^native-code:\s*(.*)$", badging, re.M)
    require(abi and re.findall(r"'([^']+)'", abi[1]) == ["arm64-v8a"], "Expected ARM64-only native libraries")
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    require(minimum and target, "Missing numeric minimum/target SDK metadata")
    digest = file_sha256(apk)
    final = apk.stat()
    require((initial.st_ino, initial.st_size, initial.st_mtime_ns) ==
            (final.st_ino, final.st_size, final.st_mtime_ns), "APK changed while verification was running")
    return {
        "status": "passed", "apk": str(apk), "bytes": apk.stat().st_size, "sha256": digest,
        "expected_commit": commit, "embedded_commit": embedded,
        "compiled_classes_dir": str(classes), "packaged_classes_verified": len(expected),
        "collision_resources_verified": [p[len(resource_root):] for p in resources],
        "package": package[1], "version_code": package[2], "version_name": package[3],
        "min_sdk": int(minimum[1]), "target_sdk": int(target[1]), "abi": "arm64-v8a",
        "debuggable": True, "signature_v2_or_newer_verified": True, "alignment_16kb_verified": True,
        "tokens_json_bundled": False, "build_tools": str(tools), "device_test": "not run",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="APK, or downloaded artifact ZIP with --artifact-zip")
    parser.add_argument("--artifact-zip", action="store_true", help="Check source/checksum files and verify the APK inside a GitHub artifact ZIP")
    parser.add_argument("--expected-commit", required=True, help="Explicit APK source hash; HEAD is never guessed")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--classes-dir", type=Path, help="Matching compiled classes (defaults to client build/classes/java/main)")
    parser.add_argument("--build-tools", help="Android SDK build-tools directory; otherwise use SDK environment variables")
    parser.add_argument("--json-out", type=Path, help="Also save the JSON report here")
    args = parser.parse_args()
    try:
        report = verify_artifact(args) if args.artifact_zip else verify(args)
    except (VerificationError, OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.TimeoutExpired) as error:
        # Do not dump embedded properties, archive contents, subprocess output or environment values.
        report = {"status": "failed", "error": str(error)}
    output = json.dumps(report, indent=2) + "\n"
    print(output, end="")
    if args.json_out and args.json_out.resolve() != args.apk.resolve():
        try:
            args.json_out.write_text(output)
        except OSError as error:
            print(f"Could not save JSON report: {type(error).__name__}", file=sys.stderr)
            return 1
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
