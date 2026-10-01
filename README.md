# Balance Point

Balance Point is a lightweight 3D motorcycle simulation focused on believable wheelies, terrain interaction, rider balance, and mobile-first performance. The current game runs from one shared libGDX core on Android and iOS.

## Status

Balance Point is under active development. `main` is the only integration branch and is expected to remain buildable. Every push to `main` runs validation, unit tests, Android packaging, and iOS packaging before publishing the rolling development builds.

## Project layout

- `core/` — shared simulation, rendering, terrain, audio, and model-loading code.
- `app/` — Android launcher, resources, and packaged game assets.
- `ios/` — RoboVM / MetalANGLE iOS launcher and packaging configuration.
- `tools/` — deterministic offline tooling and validation scripts.
- `THIRD_PARTY_LICENSES/` — third-party license notices.
- `.github/workflows/` — CI, mobile builds, and rolling development-release publication.

## Requirements

CI uses Java 17 and Gradle 8.11.1. The source is currently compiled for Java 8 compatibility because of the mobile backends. Android requires the Android SDK configured for the versions in `app/build.gradle`; iOS packaging requires macOS with the RoboVM toolchain used by the Gradle build.

## Build

Android debug APK:

```bash
gradle :app:assembleDebug
```

iOS unsigned IPA:

```bash
gradle :ios:createIPA -Probovm.archs=arm64
```

Core regression tests:

```bash
gradle :core:test
```

Repository quality checks:

```bash
python3 tools/quality_gate.py
python3 tools/validate_dirtbike.py
python3 tools/validate_dirtbike_neutral_pose.py
```

## Runtime architecture

`BalancePointGame` is the libGDX application coordinator. Domain-specific behavior is kept in focused collaborators where practical:

- `MotorcycleDrivetrain` — gearbox, clutch coupling, torque curve, and limiter behavior.
- `TerrainVisuals` — streamed terrain, exact render/collision surface sampling, traction classification, and vegetation.
- `EngineAudio` — procedural engine/exhaust audio synthesis.
- `DirtBikeMeshLoader` / `ViewerFaithfulDirtBikeLoader` — validated game-facing model loading and topology-derived steering rig.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for boundaries and maintenance rules.

## Development rules

1. Keep `main` green; do not leave temporary branches or one-off migration scripts in the repository.
2. Preserve fixed-step simulation behavior unless a physics change is intentional and validated.
3. Prefer small cohesive classes over parallel implementations or compatibility copies.
4. Add a regression test or validator whenever a bug can be expressed deterministically.
5. Comments should explain physical assumptions, invariants, and non-obvious tradeoffs — not restate the code.
6. Generated build products, local keys, IDE state, and temporary conversion artifacts do not belong in source control.

## Development builds

CI maintains the prerelease tag `dev-build`. It contains only two rolling files:

- `BalancePoint-latest.apk`
- `BalancePoint-latest-unsigned.ipa`

Actions artifacts remain uniquely named per workflow run for debugging and traceability.

## Licensing

Third-party notices are retained under `THIRD_PARTY_LICENSES/` and alongside attributed assets where required. The replacement dirt-bike model attribution is stored at `app/src/main/assets/models/DirtBike.ATTRIBUTION.txt` and is validated by CI.
