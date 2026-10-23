> Supplied Phase 2 reference. For current evidence see [BUILD_REPORT.md](../BUILD_REPORT.md).

# Compatibility boundary and next required fork-merge work

Checked public sources on 2026-10-22:

- `audaro/runemobile` `master` observed at `a8c4f22f8ac170b654793df2fd85372f31b18c64`
- `chsami/Microbot` `main` observed at `e01e0d77d79b354472a5737b3aaab2449eb034fc`

Both repositories are fast-moving. This is a reference snapshot rather than a verified cherry-pick base; compare commit hashes at local checkout time.

| Microbot feature | Phase-2 source | Missing for true parity |
|---|---|---|
| `Script` scheduled executor superclass | `MobileScript` executed on `GameTick` | Scheduler thread + blocking guards, heartbeat and cleanup, event priorities |
| `Microbot` singleton and injected managers | `MobileMicrobotPlugin` Guice injected services | All static managers, caches, setters and initialization that existing scripts expect |
| `Rs2Inventory` | `Rs2Inventory` instance read-only subset | Widgets, item model cache, click/drop/eat, bank and equipment interaction |
| `Rs2GameObject` | Nearby visible scene objects and mobile clickbox input | Microbot action/menu interaction semantics, reachability, cache, objects other than GameObjects |
| `Rs2Walker` | On-screen minimap tap **within 15 tiles** | Region routing, shortest-path, collision, doors, transports, stalling and recoveries |
| Client input | RuneMobile's `JavaBridge.onClick` | Microbot `Mouse`/keyboard/menu-entry contracts; explicit human-input arbitration |
| Desktop script JARs | Unsupported | Full Microbot package ABI, dependency graph, remapping and version matching |
| Native Android script panel | Existing RuneMobile plugin configuration UI | Kotlin UI, C++/JNI static bridge if independent overlay desired |

## To finish complete parity in a real checkout

1. Checkout pinned/recorded versions of BOTH forks and compare `runelite-api`, `runelite-client`, `runelite-mixins`, gamepack, injected classes, and plugin initialization. A file copy is insufficient because Microbot changes more than the plugin subtree.
2. Port Microbot's base runtime **in dependency order**: injected singleton managers, `Script` executor, player/item/object caches, menu/input adapters, event/busy guard; then APIs and supported scripts. Keep runelite-client interfaces aligned with RuneMobile rev.
3. Bridge client-thread state reads and AWT menu selection through `JavaBridge` or a dedicated mobile adapter. Respect RuneMobile's input targeting/tap queue. Never call native JNI from arbitrary scheduled script workers.
4. Bind independent Kotlin control UI only AFTER the engine-side controls work; `MobileUiBridge` already exposes the configuration UI, so a custom C++ bridge is optional.
5. Port one actual upstream Microbot script and its transitive dependency closure; verify by unit test and Android integration test. Only after that claim "existing script compatibility".
6. Download Android SDK/NDK and staged bionic JRE, build RuneMobile's GPU/117HD/probes/runtime in documented order, produce and sign a debug/test APK, and test on arm64 hardware with a separate development account or local server.

**No current APK or existing Microbot JAR compatibility is claimed by this patch.**
