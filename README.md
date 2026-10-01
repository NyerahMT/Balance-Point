# Balance Point

Balance Point is a lightweight 3D motorcycle simulation focused on believable wheelies, terrain interaction, rider balance, and mobile-first performance. Android and iOS share one libGDX simulation core.

## Status

Balance Point is under active development. `main` is the integration branch and is expected to remain buildable. Every push to `main` runs repository checks, deterministic asset validation, core regression tests, Android packaging, and iOS packaging before the rolling development release is updated.

## Project layout

- `core/` — shared simulation and platform-independent runtime code.
- `app/` — Android launcher, resources, and packaged game assets.
- `ios/` — RoboVM / MetalANGLE iOS launcher and packaging configuration.
- `tools/` — deterministic offline tooling and validation scripts.
- `docs/` — architecture and maintenance rules.
- `THIRD_PARTY_LICENSES/` — third-party license notices.
- `.github/workflows/` — CI, mobile builds, and rolling development-release publication.

## Requirements

The repository pins Gradle 8.11.1 through the Gradle wrapper and CI uses Java 17. Runtime source remains Java 8 compatible for the mobile backends. Android builds require the Android SDK configured for the versions in `app/build.gradle`; iOS packaging requires macOS and the RoboVM toolchain resolved by Gradle.

Do not depend on a globally installed Gradle. Use the checked-in wrapper so local builds and CI execute the same Gradle release.

## Build

Android debug APK:

```bash
./gradlew :app:assembleDebug
```

iOS unsigned IPA:

```bash
./gradlew :ios:createIPA -Probovm.archs=arm64
```

Core regression tests:

```bash
./gradlew :core:test
```

Repository and asset validation:

```bash
python3 tools/quality_gate.py
python3 tools/validate_dirtbike.py
python3 tools/validate_dirtbike_neutral_pose.py
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Runtime architecture

`BalancePointGame` is the application coordinator and owns high-level ride/session state plus the fixed-step motorcycle simulation. Presentation and specialized subsystems are separated from it:

- `GameScene` — world and motorcycle model ownership, transforms, shadows, and 3D rendering.
- `GameCamera` — menu, chase, and helmet-camera state and smoothing.
- `GameHud` — touch controls, menu/HUD presentation, and overlay rendering.
- `InstrumentDisplay` — in-world instrument display state and drawing.
- `MotorcycleDrivetrain` — gearbox, clutch coupling, torque curve, and limiter behavior.
- `TerrainVisuals` — streamed terrain plus the shared render/collision surface and traction classification.
- `EngineAudio` — procedural engine/exhaust audio synthesis and audio-thread ownership.
- `DirtBikeMeshLoader` / `ViewerFaithfulDirtBikeLoader` — validated model loading and topology-derived steering rig.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for boundaries, invariants, and maintenance rules.

## Engineering rules

1. Keep `main` green and do not leave migration workflows, temporary branches, or one-off source-patching scripts in the repository.
2. Preserve the fixed 120 Hz simulation contract unless a physics change is deliberate and regression-tested.
3. Keep one active implementation for each runtime responsibility; delete superseded implementations after migration.
4. Add a regression test or deterministic validator whenever a bug can be expressed without rendering a frame.
5. Comments should explain physical assumptions, invariants, and non-obvious tradeoffs rather than restating code.
6. Use the shared `TerrainVisuals` surface for both rendering and collision instead of introducing parallel height approximations.
7. Generated build products, local credentials, IDE state, and temporary conversion artifacts do not belong in source control.

The repository quality gate enforces core hygiene rules and architecture ceilings so the application coordinator cannot silently grow back into the previous all-in-one design.

## CI and development builds

Build/test jobs run with read-only repository permissions. Only the final publish job receives `contents: write`, and only for successful pushes to `main` after quality, Android, and iOS jobs all pass.

CI maintains the prerelease tag `dev-build`. It publishes only these rolling files:

- `BalancePoint-latest.apk`
- `BalancePoint-latest-unsigned.ipa`

Actions artifacts remain uniquely named per workflow run for debugging and traceability.

The repository contains a stable **debug-only** Android signing key used for development APK continuity. It is intentionally not a production credential and must never be reused for a release build.

## Licensing

Third-party notices are retained under `THIRD_PARTY_LICENSES/` and alongside attributed assets where required. The dirt-bike model attribution is stored at `app/src/main/assets/models/DirtBike.ATTRIBUTION.txt` and its geometry/rig invariants are checked by CI.
