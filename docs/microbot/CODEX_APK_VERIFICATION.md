# Codex APK verification

Verified source: `088bc3472c5bbc70a058e9788ac4d5a14a7651a7` on `feat/android-microbot-compat`.
This includes hot-air-balloon transport execution, the latest scene/NPC/player
read APIs and the combined input/lifecycle recovery regression tests.

## Source and package checks

- 560 focused tests, zero failures/errors/skips; client compilation,
  Checkstyle and PMD passed against this snapshot.
- Ordered JRE/client/probe/GPU/RLHD/runtime staging and Android debug assembly passed.
- APK v2 signature and 16 KB native-library ZIP alignment passed.
- Package `dev.audaro.runemobile`, version0.2.6/code206, ARM64 Android9+
  (min SDK28, target35); application-debuggable confirmed.
- All 686 selected compiled Microbot/mobile/input classes
  match the packaged GPU jar. Collision resources and embedded commit `088bc34`
  match source.
- No bundled `tokens.json`.
- No phone installation, launch or live-game result is claimed.

## Verified local artifact

File: `/workspace/artifacts/runemobile-woodcutting-debug-088bc34-arm64.apk`

Bytes: 169992406

SHA256: `fd5ff84bad7803dfa7ed12b1b9340e52efdb25b72a4481d6231ac2c19065ebd2`

The local generic debug alias points to this APK. Previous versioned local
artifacts remain available. GitHub builds have their own signing key and SHA;
do not identify the remote APK using this local checksum.

## Browser delivery

GitHub run for this snapshot (cancelled before artifact upload):
https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/40046402646

Runs 56, 57 and 58 were cancelled by newer shared-branch pushes. Run 58
completed setup and contract preflight, then its runtime-check step was cancelled;
APK assembly, verification and upload were skipped. There is no new GitHub
artifact URL for this verified local snapshot. A request to the CI owner in
COORDINATION.md asks to let a build finish while the branch is active.
Do not infer remote upload success from the local APK.

## Reproduce and evidence

Activate `scripts/activate-java.sh`, run the focused command in
`COORDINATION.md`, refresh `:client:processResources --rerun-tasks` after the
implementation commit, then run:

```sh
bash scripts/build-android.sh /workspace/rsmod --debug
```

Logs under `/workspace/tooling/`: `codex-release-snapshot-checks.log`,
`codex-release-snapshot-resources.log`, `codex-release-snapshot-build.log`,
`codex-release-snapshot-signature.log`, `codex-release-snapshot-alignment.log`,
`codex-release-snapshot-badging.log`, `debug-apk-verified.json`.

No runtime, CI, transport or shared-fixture edits were made in this task.
