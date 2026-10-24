#!/usr/bin/env python3
"""Hermetic CLI regression tests. Synthetic APKs/classes and simulated SDK tools;
real signing/Android packaging are covered by the documented real-APK smoke run.
"""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/verify-mobile-apk.py"
PREFIX = "net/runelite/client/"
RESOURCES = "runelite-client/src/main/resources/"


class ApkVerifierTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="apk verifier ")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.repo = self.root / "source repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.resources = {
            PREFIX + "plugins/microbot/util/walker/collision-map.zip": b"walking collision fixture",
            PREFIX + "plugins/microbot/shortestpath/collision-map.zip": b"pathfinder collision fixture",
        }
        for name, value in self.resources.items():
            path = self.repo / RESOURCES / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value)
        self.git("add", RESOURCES)
        self.git("-c", "user.name=Verifier Test", "-c", "user.email=verifier@example.invalid",
                 "-c", "commit.gpgsign=false", "commit", "-qm", "collision fixture")
        self.commit = self.git("rev-parse", "HEAD").strip()
        self.classes = self.repo / "runelite-client/build/classes/java/main"
        self.class_bytes = {
            PREFIX + name + ".class": b"synthetic compiled bytes " + name.encode()
            for name in ("plugins/mobilemicrobot/MobileMicrobotPlugin", "plugins/microbot/MicrobotRuntime",
                         "headless/JavaBridge", "headless/ScriptInputSession", "headless/ScriptInputSession$Queued")
        }
        for name, value in self.class_bytes.items():
            path = self.classes / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value)
        self.entries = dict(self.class_bytes, **self.resources)
        self.properties()
        self.apk = self.root / "test application.apk"
        self.tools = self.root / "sdk tools"
        self.tools.mkdir()
        self.settings = {
            "apksigner": "Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\n",
            "zipalign": "",
            "aapt": "package: name='dev.audaro.runemobile' versionCode='206' versionName='0.2.6'\n"
                    "sdkVersion:'28'\ntargetSdkVersion:'35'\napplication-debuggable\nnative-code: 'arm64-v8a'\n",
            "exit": {},
        }
        # These executables simulate tool responses and capture exact invocation arguments.
        tool = "#!" + sys.executable + "\n" + '''import json, pathlib, sys
root = pathlib.Path(__file__).parent
name = pathlib.Path(__file__).name
settings = json.loads((root / "settings.json").read_text())
with (root / "calls.jsonl").open("a") as log:
    log.write(json.dumps([name, *sys.argv[1:]]) + "\\n")
if settings.get("mutate_apk") and name == "apksigner":
    with pathlib.Path(sys.argv[-1]).open("ab") as apk:
        apk.write(b"changed during verification")
print(settings[name], end="")
sys.exit(settings["exit"].get(name, 0))
'''
        for name in ("apksigner", "zipalign", "aapt"):
            path = self.tools / name
            path.write_text(tool)
            path.chmod(0o755)
        self.write_settings()
        self.write_apk()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo, text=True, stderr=subprocess.PIPE)

    def properties(self, commit=None, dirty="false"):
        self.entries[PREFIX + "runelite.properties"] = (
            f"runelite.commit={commit or self.commit[:7]}\nrunelite.dirty={dirty}\n").encode()

    def write_settings(self):
        (self.tools / "settings.json").write_text(json.dumps(self.settings))

    def write_apk(self, extra=(), duplicates=False):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w", zipfile.ZIP_DEFLATED) as jar:
            for name, value in self.entries.items():
                jar.writestr(name, value)
        with zipfile.ZipFile(self.apk, "w") as outer:
            outer.writestr("assets/jvm/client-fixture-gpu.jar", data.getvalue())
            for name, value in extra:
                outer.writestr(name, value)
            if duplicates:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    outer.writestr("assets/jvm/client-fixture-gpu.jar", data.getvalue())

    def run_cli(self, *extra, expected=None):
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.apk),
                                 f"--expected-commit={expected or self.commit}",
                                 "--repo", str(self.repo), "--build-tools", str(self.tools), *extra],
                                capture_output=True, text=True, timeout=30)
        return result, json.loads(result.stdout)

    def reject(self, error, *extra, expected=None):
        result, report = self.run_cli(*extra, expected=expected)
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual("failed", report["status"])
        self.assertIn(error, report["error"])
        return report

    def test_pass_records_identity_and_invokes_android_checks(self):
        saved = self.root / "report.json"
        result, report = self.run_cli("--json-out", str(saved))
        self.assertEqual(0, result.returncode, report)
        self.assertEqual("passed", report["status"])
        self.assertEqual(self.commit, report["expected_commit"])
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), report["sha256"])
        self.assertEqual(5, report["packaged_classes_verified"])
        self.assertEqual(sorted(self.resources), report["collision_resources_verified"])
        self.assertEqual("not run", report["device_test"])
        self.assertEqual(report, json.loads(saved.read_text()))
        calls = [json.loads(line) for line in (self.tools / "calls.jsonl").read_text().splitlines()]
        self.assertEqual(["apksigner", "verify", "--verbose", str(self.apk)], calls[0])
        self.assertEqual(["zipalign", "-c", "-P", "16", "4", str(self.apk)], calls[1])
        self.assertEqual(["aapt", "dump", "badging", str(self.apk)], calls[2])

    def test_source_commit_mismatch(self):
        self.properties(commit="0" * 7)
        self.write_apk()
        self.reject("Source commit mismatch")

    def test_dirty_source_is_not_certified(self):
        self.properties(dirty="true")
        self.write_apk()
        self.reject("dirty")

    def test_resource_comparison_uses_expected_git_commit_not_working_tree(self):
        path = self.repo / RESOURCES / next(iter(self.resources))
        path.write_bytes(b"uncommitted resource")
        result, report = self.run_cli()
        self.assertEqual(0, result.returncode, report)
        self.entries[next(iter(self.resources))] = b"uncommitted resource"
        self.write_apk()
        self.reject("Collision resource differs")

    def test_changed_compiled_class_is_rejected(self):
        self.entries[PREFIX + "headless/JavaBridge.class"] = b"changed"
        self.write_apk()
        self.reject("Compiled class differs")

    def test_missing_packaged_class_is_rejected(self):
        del self.entries[PREFIX + "headless/ScriptInputSession$Queued.class"]
        self.write_apk()
        self.reject("Compiled class inventory mismatch")

    def test_extra_stale_packaged_class_is_rejected(self):
        self.entries[PREFIX + "plugins/microbot/Stale.class"] = b"stale"
        self.write_apk()
        self.reject("Compiled class inventory mismatch")

    def test_empty_compilation_output_cannot_pass(self):
        empty = self.root / "empty classes"
        empty.mkdir()
        self.reject("Required compiled class missing", "--classes-dir", str(empty))

    def test_missing_collision_resource_is_rejected(self):
        del self.entries[next(iter(self.resources))]
        self.write_apk()
        self.reject("Collision resource missing")

    def test_token_filename_is_case_insensitive(self):
        self.write_apk(extra=(("assets/ToKeNs.JsOn", b"test fixture only"),))
        self.reject("tokens.json")
        self.assertFalse((self.tools / "calls.jsonl").exists(), "SDK tools must not run on a rejected token bundle")

    def test_duplicate_apk_entries_are_rejected(self):
        self.write_apk(duplicates=True)
        self.reject("Duplicate ZIP entries")

    def test_malformed_apk_is_rejected(self):
        self.apk.write_bytes(b"not a ZIP")
        self.reject("not a zip file")

    def test_v1_only_signing_is_rejected(self):
        self.settings["apksigner"] = "Verified using v1 scheme: true\nVerified using v2 scheme: false\n"
        self.write_settings()
        self.reject("v2 or newer")

    def test_failed_signing_command_is_rejected(self):
        self.settings["exit"]["apksigner"] = 1
        self.write_settings()
        self.reject("apksigner failed")

    def test_failed_alignment_is_rejected(self):
        self.settings["exit"]["zipalign"] = 1
        self.write_settings()
        self.reject("zipalign failed")

    def test_release_apk_is_rejected(self):
        self.settings["aapt"] = self.settings["aapt"].replace("application-debuggable\n", "")
        self.write_settings()
        self.reject("not debuggable")

    def test_wrong_package_is_rejected(self):
        self.settings["aapt"] = self.settings["aapt"].replace("dev.audaro.runemobile", "example.other")
        self.write_settings()
        self.reject("package name")

    def test_wrong_abi_is_rejected(self):
        self.settings["aapt"] = self.settings["aapt"].replace("arm64-v8a", "x86_64")
        self.write_settings()
        self.reject("ARM64-only")

    def test_report_path_cannot_overwrite_input(self):
        before = self.apk.read_bytes()
        self.reject("must not overwrite", "--json-out", str(self.apk))
        self.assertEqual(before, self.apk.read_bytes())

    def test_failed_report_can_be_saved_without_partial_pass(self):
        self.apk.write_bytes(b"invalid ZIP")
        saved = self.root / "failed.json"
        report = self.reject("not a zip file", "--json-out", str(saved))
        self.assertEqual(report, json.loads(saved.read_text()))
        self.assertNotIn("signature_v2_or_newer_verified", report)

    def test_apk_modified_during_external_checks_is_rejected(self):
        self.settings["mutate_apk"] = True
        self.write_settings()
        self.reject("APK changed while verification was running")

    def test_invalid_revision_is_not_interpreted_as_git_option(self):
        self.reject("Git commit hash", expected="--help")


if __name__ == "__main__":
    unittest.main()
