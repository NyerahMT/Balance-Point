# Architecture

Balance Point uses a shared libGDX core with thin Android and iOS launchers. The architecture is intentionally small: domain boundaries should be explicit, but the project should not accumulate abstraction layers that do not protect behavior.

## Runtime boundaries

### Application coordinator

`BalancePointGame` owns lifecycle and orchestration: initialization, fixed-step scheduling, high-level game state, and sequencing of input, simulation, rendering, camera, and HUD work. New domain logic should not be added here when it naturally belongs to a focused subsystem.

### Motorcycle drivetrain

`MotorcycleDrivetrain` owns engine RPM coupling, gear ratios, shifting, torque output, clutch behavior, and limiter state. Callers provide driven-wheel linear speed, throttle, and fixed-step `dt`; callers do not duplicate gearbox math.

### Terrain

`TerrainVisuals` owns the streamed heightfield representation and is the source of truth for terrain height, slope, traction classification, and rendered terrain geometry. Physics must query this surface instead of maintaining a second terrain approximation.

### Audio

`EngineAudio` owns synthesis state and its audio thread. The game supplies high-level engine state. Audio implementation details must not leak into simulation.

### Dirt-bike asset loading

`DirtBikeMeshLoader` is the stable game-facing loader facade. `ViewerFaithfulDirtBikeLoader` performs GLB parsing, topology decomposition, material construction, and steering-axis derivation. CI validators guard the imported asset's geometry and neutral-pose assumptions.

## Simulation rules

- Simulation advances at a fixed 120 Hz step.
- Render frame rate must not directly change physics integration.
- Terrain collision and terrain rendering must sample the same effective surface.
- Tire grip is modeled as a force limit; exceeding grip must not silently delete drivetrain power.
- Airborne and grounded behavior should transition from contact state rather than unrelated mode flags when practical.
- Tunable physical constants require names and units or nearby comments explaining their meaning.

## Source-quality rules

- One active implementation for each runtime responsibility. Delete superseded implementations once migration is complete.
- No one-off source-patching scripts after their migration has landed.
- No wildcard Java imports.
- No TODO/FIXME/HACK markers on `main`; use an issue or a descriptive comment explaining a deliberate limitation.
- Runtime Java should not use `System.out`/`System.err`; use libGDX logging where logging is required.
- Text files use LF, spaces, UTF-8, and a final newline.
- Deterministic regressions belong in tests or validators.

## CI contract

The quality job runs before mobile packaging. Android and iOS build jobs operate with read-only repository permissions. A separate publish job receives write permission only on pushes to `main`, downloads the successful build artifacts, and updates the rolling `dev-build` prerelease.

## Refactoring guidance

The largest remaining architectural concentration is the application coordinator. Future feature work should preferentially extract cohesive behavior — for example camera control, HUD/input layout, or motorcycle dynamics — rather than growing `BalancePointGame`. Extraction is only worthwhile when the new boundary has a clear state/API contract and preserves deterministic behavior.
