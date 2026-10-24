# Verify a RuneMobile debug APK

`scripts/verify-mobile-apk.py` checks a local APK without modifying it, extracting
files into the checkout, downloading tools or changing the CI workflow. It uses
Python 3's standard library and Android SDK `apksigner`, `zipalign` and `aapt`.

## Run

Use the source commit printed in the APK artifact's `SOURCE_COMMIT.txt`, not the
current branch's HEAD. Keep the matching compiled output from that source build.

```sh
source scripts/activate-java.sh
python3 scripts/verify-mobile-apk.py /path/to/app-debug.apk \
  --expected-commit FULL_OR_SHORT_COMMIT \
  --json-out /tmp/apk-verification.json
```

For outputs retained outside this checkout:

```sh
python3 scripts/verify-mobile-apk.py /path/to/app-debug.apk \
  --expected-commit FULL_OR_SHORT_COMMIT \
  --repo /path/to/source-checkout \
  --classes-dir /path/to/matching/classes/java/main \
  --build-tools /path/to/android-sdk/build-tools/35.0.0
```

`--expected-commit` is required and accepts a 7–40 digit hexadecimal Git commit
hash available in the repository. The tool resolves it to a full commit. The
compiled output defaults to `runelite-client/build/classes/java/main`. SDK tools
come from `--build-tools` or the latest complete stable build-tools installation
under `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

JSON is always printed to stdout. `--json-out` also saves it to an existing parent
directory. Success exits 0; verification failures exit 1; invalid CLI arguments
exit 2. A failed report has `status: failed` and an error, not a partial pass.
The JSON output path cannot overwrite the input APK.

## Checks

- Exactly one `assets/jvm/*-gpu.jar`; no duplicate entries in either ZIP.
- No file named `tokens.json` anywhere in the APK, case-insensitive.
- Exact embedded `runelite.commit` prefix match and `runelite.dirty=false`.
- Matching class inventory and byte equality for all compiled Microbot and
  mobile plugin classes, plus JavaBridge and ScriptInputSession/nested classes.
  Required host/runtime/input classes prevent an empty output directory passing.
- Every tracked collision resource at the **expected Git commit**, checked by
  byte equality. A newer working-tree resource cannot silently replace it.
- Valid APK signature using v2 or newer, checked by `apksigner`.
- 16 KB native-library ZIP alignment checked by `zipalign`.
- RuneMobile package ID, debuggable flag and ARM64-only native code checked by
  `aapt`; version and minimum/target SDK values recorded in the report.
- File size and SHA256 calculated from the APK itself; an APK replaced or
  modified during verification is rejected.

External tools have a 120-second command timeout. Their output, the environment
and embedded properties are not dumped into failure reports.

## Interpretation and limits

The result proves equality with the supplied compiled files and committed
collision resources. It does not prove those class files were compiled from the
specified source revision; retain build logs and compiled outputs together.
Embedded commit metadata alone is not an attestation. A valid signature does not
mean the APK was signed by the official upstream release key.

A dirty client is rejected, even if the difference might only have been an
untracked documentation file. The tool does not guess what changed or silently
waive that check. Commit/stash intended source changes, make sure the build's
checkout is clean, refresh client resources after the commit, then follow the
ordered Android build helper. Do not edit the APK's properties to make it pass.

This is packaging validation. It does not install the APK or establish login,
rendering, touch, script or transport success on a device. The JSON explicitly
records `device_test: not run`. GitHub's independent build needs its own SHA and
matching source/output evidence; a local APK's checksum does not identify it.

## Repeatable regression tests

```sh
python3 tests/test_verify_mobile_apk.py -v
```

The suite has 22 hermetic CLI tests. It creates temporary Git repositories,
synthetic APK/class/resource fixtures and simulated Android SDK executables.
It requires Python 3 and Git, but no SDK, Gradle, Android device or network.
Temporary files are removed after each test, and source files are untouched.

Coverage includes the successful JSON/command path, wrong/dirty source metadata,
class inventory and content mismatches, resources from the requested revision
rather than a changed working tree, malformed/duplicate ZIPs, token bundles,
signing/alignment command failures, v1-only signing, wrong package/ABI, missing
debug flag, input-overwrite protection, saved failure reports and a package
modified while verification runs. Paths deliberately contain spaces.

These tests simulate SDK results to exercise rejection and reporting behavior;
they do not validate real signatures or ZIP alignment. Keep the real-APK smoke
check below as separate evidence. This suite does not change or run the Java
runtime, walker or device tests.

## Validation evidence

Nine CLI checks passed: one real clean debug APK plus eight rejection cases
(wrong revision, dirty retained client, malformed ZIP, bundled token file,
report path equal to input APK, changed class bytes, changed collision resource,
and a missing required packaged class). The overwrite case also confirmed the
APK's SHA256 stayed unchanged.

The passing APK was built from `bbbee9cf06181b29fc734c28fa53415c33c4cd76`:
951 selected classes and both collision archives matched; SDK tools verified
signing, alignment, package/debug/ABI metadata. Device testing was not run.
Retained locally at `/workspace/artifacts/runemobile-verifier-bbbee9c-arm64.apk`.
SHA256: `7b8359d896882273291468483077966a99122c80e97e1ff05e3a1cee506d9f42`.
Local validation details: `/workspace/tooling/apk-verifier-validation.json`.
No Java runtime code was changed, and no additional full Java test result is
claimed for this tooling task.

The older untracked local helper did not check the dirty flag; this script
intentionally adds that requirement. The retained `088bc347` APK is rejected
because its embedded client has `runelite.dirty=true`, even though the older
helper had passed its class/resource comparison.
