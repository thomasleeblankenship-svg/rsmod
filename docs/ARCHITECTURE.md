# Phase-2 mobile adapter

```
Android native plugin panel (existing RuneMobile UI)
    | enabled/config toggle
    v
RuneLite PluginManager + ConfigManager
    |
    v
MobileMicrobotPlugin  <-- local status/start/stop MobileScriptBridge
    |
    +-- GameTick/client thread --> MobileScriptContext (per tick)
                                    | Rs2Inventory: read only
                                    | Rs2Player: read only
                                    | Rs2GameObject: scene object scan + click
                                    | Rs2Walker: same-scene minimap tap ONLY
                                    |
                                    +--> JavaBridge.onClick --> RuneMobile AWT queue
    |
    +-- DiagnosticsScript, SceneScanScript, WoodcuttingScript
    |
    +-- logout/connection loss => stop; never background autostart
```

No new network services, dynamically downloaded JARs, JNI exports, or concurrency behind the game engine have been added. The Microbot-inspired helpers are **instance-scoped** and avoid static global dependency state. The upper layer is not the desktop `net.runelite.client.plugins.microbot.*` ABI.
