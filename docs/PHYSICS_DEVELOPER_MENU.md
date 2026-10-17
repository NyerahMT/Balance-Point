# Physics Developer Menu — First Implementation

The in-game editor opens from **Pause → PHYSICS DEV** and tunes the active
`PhysicsV3MultibodyPlant` directly. This menu is initially meant for developer
builds; it should be build-gated before publishing a public release.

## Categories and subsystems

- Steering & Balance → Steering / Countersteering / Stabilization
- Rider Movement → Weight transfer
- Suspension → Front fork / Rear shock
- Wheelies → Wheelie catch

The initial editor has fifteen bounded, live physics parameters with raw
numeric values, original-V3 defaults, and a two-step category/subsystem menu.
These are actual solver coefficients, not visual-only UI settings.

## Behavior

- Parameters take effect immediately in the V3 equations after resuming.
- The physical simulation keeps its fixed 120 Hz step.
- RESET restores original V3 defaults.
- SAVE / LOAD A and B manage two comparison presets.
- Active values and both presets persist in app preferences across restarts.
- The menu pauses the ride so controls are not competing for touch inputs.
- A read-only snapshot displays speed, front/rear contact load, roll angle,
  steering angle, and both tire-utilization measurements from V3 telemetry.

## Testing and remaining work

`PhysicsTuningTest` checks original defaults, value bounds, invalid values,
category lookup, and preset isolation. Mobile CI must pass before merging.
Device testing is still needed for touch targets, layout and readability.

Time-series telemetry recording and input replay, advanced tire/air parameters,
individual parameter explanations, exportable config files, and actual
release-build gating remain separate development tasks.
