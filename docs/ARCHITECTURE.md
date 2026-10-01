# Architecture

Balance Point uses a shared libGDX core with thin Android and iOS launchers. The architecture is intentionally compact: boundaries exist to protect behavior and resource ownership, not to manufacture abstraction layers.

## Runtime boundaries

### Application coordinator and motorcycle simulation

`BalancePointGame` owns libGDX lifecycle, high-level ride/session state, the fixed 120 Hz scheduler, input-to-control state, and the coupled motorcycle dynamics that still require one coherent integration step. It orchestrates presentation collaborators but does not own their rendering resources.

The coordinator is intentionally capped by the repository quality gate. New rendering, camera, HUD, audio, asset-loading, or other independently cohesive behavior belongs in its own subsystem instead of growing this class back toward the former all-in-one implementation.

### Scene and rendering

`GameScene` owns 3D world and motorcycle presentation resources: model creation, imported-bike instances, transforms, shadows, road/terrain presentation, and render ordering. It receives a compact bike-state snapshot from the simulation. It does not make handling decisions or duplicate collision math.

`TerrainVisuals` is shared between `BalancePointGame` and `GameScene`, so rendered terrain and wheel contact query the same effective surface.

### Camera

`GameCamera` owns menu, chase, and helmet-camera state, smoothing, framing, and viewport updates. The simulation supplies state snapshots; camera transients never feed back into motorcycle physics.

### HUD and instrument display

`GameHud` owns overlay rendering and the visual representation of touch controls and menus. `InstrumentDisplay` owns the in-world display surface and its presentation state. Neither subsystem owns motorcycle dynamics.

### Motorcycle drivetrain

`MotorcycleDrivetrain` owns engine RPM coupling, gear ratios, shifting, torque output, clutch behavior, and limiter state. Callers provide driven-wheel linear speed, throttle, and fixed-step `dt`; callers do not duplicate gearbox math.

### Terrain

`TerrainVisuals` owns the streamed heightfield representation and is the source of truth for terrain height, slope, traction classification, and rendered terrain geometry. Physics must query this surface instead of maintaining a second terrain approximation.

### Audio

`EngineAudio` owns synthesis state and its audio thread. The game supplies high-level engine state. Audio implementation details must not leak into simulation.

### Dirt-bike asset loading

`DirtBikeMeshLoader` is the stable game-facing loader facade. `ViewerFaithfulDirtBikeLoader` performs GLB parsing, topology decomposition, material construction, and steering-axis derivation. CI validators guard the imported asset's geometry, normalization, steering topology, and neutral-pose assumptions.

## Simulation invariants

- Simulation advances at a fixed 120 Hz step.
- Render frame rate must not directly change physics integration.
- Terrain collision and terrain rendering sample the same effective surface.
- Tire grip is modeled as a force limit; exceeding grip must not silently delete drivetrain power.
- Wheel/terrain contact is solved per wheel; wheelies, jumps, and landings emerge from contact state rather than hard-coded ride modes.
- Airborne pitch behavior preserves launch angular momentum and rear-wheel reaction effects.
- Tunable physical constants require names and units or nearby comments explaining their meaning.
- Presentation state must not feed back into the deterministic simulation path.

## Source-quality rules

- One active implementation for each runtime responsibility. Delete superseded implementations once migration is complete.
- No one-off source-patching scripts or migration workflows after their migration has landed.
- No wildcard Java imports.
- No TODO/FIXME/HACK markers on `main`; use an issue or a descriptive comment explaining a deliberate limitation.
- Runtime Java should not use `System.out`/`System.err`; use libGDX logging where logging is required.
- First-party text uses UTF-8, spaces, and a final newline. LF is the repository default; canonical Windows wrapper CRLF is permitted.
- Deterministic regressions belong in tests or validators.
- The checked-in Gradle wrapper is the authoritative build-tool entry point.

## Testing and validation

`core/src/test` contains deterministic unit/regression tests for pure runtime behavior such as the drivetrain. Asset behavior that depends on source geometry is guarded by independent Python validators rather than by testing implementation details inside the Java loader.

The quality gate additionally checks repository hygiene, required documentation, wrapper pinning, obsolete-file absence, formatting hazards, and architecture size ceilings.

Physics changes should add focused deterministic coverage whenever the affected behavior can be isolated without a graphics context. Runtime behavior that cannot yet be isolated should preserve the existing fixed-step equations during structural refactors and be validated by both mobile builds.

## CI contract

The quality job runs before mobile packaging. It validates repository hygiene, dirt-bike geometry/rig invariants, the pinned Gradle wrapper, and core unit tests. Android and iOS build jobs operate with read-only repository permissions. A separate publish job receives write permission only on pushes to `main`, downloads the successful mobile artifacts, and updates the rolling `dev-build` prerelease.

A successful publish therefore implies the same commit passed quality checks, Android packaging, and iOS packaging.

## Refactoring guidance

Refactor around responsibility and state ownership, not class-count targets. A new boundary is worthwhile when it has a clear contract, removes unrelated state/resource ownership from another class, and can preserve observable behavior during extraction.

For future simulation growth, prefer extracting deterministic physics components only when their inputs/outputs can be made explicit enough to regression-test. Avoid splitting tightly coupled contact and rigid-body equations merely to reduce line count; that can make correctness harder to reason about than a cohesive solver.
