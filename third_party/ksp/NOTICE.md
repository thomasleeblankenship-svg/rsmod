# KSP Chopper: original source (reference copy, not built)

Byte-identical copy of KSP's **Chopper** plugin ("KSP Willow Chopper", v0.1.3) and the two KSP
support services it depends on.

- **Source:** https://github.com/KSPOG/ksppluginsrelease, commit
  `65c0e591741bc156e59392ff1ce637a0b543b92b` (2026-10-22), directories `kspwillowchopper/`,
  `kspsupport/` (`KspBreakAwareScript`, `KspBreakConfig`, `KspBreakService`) and `kspmule/`
  (`KspMuleConfig`, `KspMuleWorkerService`).
- **License:** the source repository has no license file or license notice. The repository owner
  of this project stated on 2026-10-23 that they have the author's permission to copy and adapt it.
  Without that permission this code must not be redistributed.
- **Not compiled.** This folder is outside the Gradle source sets; it is kept as the unmodified
  reference for the mobile port.

## The mobile port

The built copy lives in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/`:

- `kspwillowchopper/**` and `kspsupport/KspBreakAwareScript.java`: unchanged from this copy
  (KSP's own README is left out of the source tree).
- `kspsupport/KspBreakService.java`, `kspsupport/KspBreakConfig.java`, `kspmule/**`: **mobile
  stand-ins, not KSP's code.** KSP's randomized logout breaks with automatic re-login are
  scheduled-break anti-detection behaviour, and its mule service moves items to a collector
  account; RuneMobile ports neither. The stand-ins keep KSP's names so the plugin compiles, never
  pause, log out, log in or trade, and declare no settings.
- APIs the plugin needed were added to the mobile Microbot layer (deposit box, mouse clicks as
  session-gated taps, tile-object reads); see `docs/microbot/COORDINATION.md`.
