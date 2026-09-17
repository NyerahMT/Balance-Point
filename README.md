# Balance Point

**Balance Point** is a lightweight 3D motorcycle game focused on making wheelies, steering, braking, and bike control feel satisfying on mobile hardware.

The project started as a small wheelie-physics prototype and has grown into a shared **libGDX** game targeting both Android and iOS. The long-term direction is a low-poly highway racer where wheelies become a risk/reward mechanic rather than the entire game.

## Current state

Balance Point currently includes:

- 3D libGDX rendering on Android and iOS
- Fixed-step motorcycle physics at **120 Hz**
- 30 FPS mobile render target
- Throttle and rear-brake controls
- Speed-sensitive steering with reduced airborne authority
- Wheelie balance-point physics with progressive pitch-rate damping
- Chase camera and horizon-stabilized helmet camera
- Drag-to-look camera control
- Endless lightweight road/environment rendering
- Wheelie timer, best time, speed, pitch, front-load, and FPS HUD
- Shared game logic in the `core` module
- Native Android launcher and RoboVM/MetalANGLE iOS launcher
- A real dirt-bike visual asset loaded directly from **standard GLB 2.0**

The physics bike and visual bike are intentionally separate. Visual model changes should not alter the underlying motorcycle handling model.

## Dirt-bike asset pipeline

The current bike asset is:

```text
app/src/main/assets/models/DirtBike.glb
```

It is a normal binary glTF 2.0 file. The runtime loader reads the GLB mesh/accessor data directly and converts it into standard libGDX `Model` instances at startup.

The GLB contains named parts including:

```text
Body_GEO
Engine_GEO
FrontShock_GEO
FrontWheel_GEO
Handle_GEO
RearWheel_GEO
RearWheelChain_GEO
LeftLeaver_GEO
RightLeaver_GEO
```

The exported wheelbase is **1.45 m**, matching the current physics model.

The old custom BPQ/Base64 mesh-chunk pipeline has been retired.

## Controls

### Left side

- Left arrow — steer left
- Right arrow — steer right

### Right side

- Vertical slider — throttle
- Brake button — rear brake

### Camera

- Drag the center of the screen — free look
- Camera button — switch between chase and helmet views

## Physics

The current handling model includes:

- Rear-wheel drive force
- Power-limited acceleration
- Tire traction limits
- Longitudinal load transfer
- Front-wheel unloading and lift-off
- Wheelie pitch dynamics
- Nonlinear balance-point damping
- Rear-brake wheelie recovery
- Aerodynamic and rolling drag
- Speed-sensitive steering
- Lean response from lateral acceleration

The goal is not a full rigid-body simulator; it is a lightweight model that feels believable while remaining controllable and performant on mobile devices.

## Project layout

```text
core/   Shared gameplay, physics, rendering, controls, and model loading
app/    Android launcher, packaging, and shared mobile assets
ios/    RoboVM / MetalANGLE iOS launcher and packaging
tools/  Build-time validation utilities
```

## Build requirements

- **JDK 17**
- **Gradle 8.11.1**
- **libGDX 1.14.2**
- Android compile SDK 36
- RoboVM 2.3.23 for iOS

GitHub Actions builds both platforms automatically from `main`.

### Android

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### iOS

```bash
gradle :ios:createIPA -Probovm.archs=arm64
```

The CI build is unsigned and intended for external signing/sideloading.

## Test builds

The `dev-build` GitHub release is automatically updated from `main` with the latest Android APK and unsigned iOS IPA after successful builds.

## Direction

The next major gameplay step is turning the prototype into a lightweight low-poly highway racer:

- Multi-lane endless highway
- Pooled traffic cars and trucks
- Near-miss scoring
- High-speed bonuses
- Wheelie multipliers through traffic
- Better roadside scenery, barriers, signs, overpasses, and city elements
- Crash/reset behavior suitable for traffic gameplay

Garage systems, currency, upgrades, weather, and larger progression systems can come later. The immediate priority remains **bike feel + traffic gameplay + visual polish**.

## Development philosophy

Balance Point is being built around three constraints:

1. **Feel first** — motorcycle control has to be fun before adding systems around it.
2. **Mobile first** — avoid unnecessary runtime weight and keep lower-end hardware in mind.
3. **Standard assets** — use normal formats such as GLB instead of custom asset pipelines unless there is a clear performance reason not to.
