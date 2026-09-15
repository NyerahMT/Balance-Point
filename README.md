# Balance Point

A lightweight Android wheelie game built around one thing first: making the balance point feel right.

## v0.1 prototype

- Landscape, 30 FPS target
- Vanilla Android `SurfaceView`/`Canvas` renderer
- Fixed 120 Hz motorcycle physics
- Throttle on the right side of the screen
- Rear brake on the left side
- Rear-drive pitch torque, wheel-ground spring/damper contact, aerodynamic drag
- Wheelie timer, best time, speed, and pitch angle HUD
- Procedural placeholder bike/rider/environment — no texture assets yet
- No third-party runtime dependencies and no native libraries

The first prototype intentionally keeps steering, traffic, progression, tuning, audio, and 3D assets out. The goal is to validate performance and wheelie control on the GMEE Connect Pro before expanding the game.

## Build

The project uses Android Gradle Plugin 8.8.2 / Gradle 8.10.2 and requires JDK 17 to build. GitHub Actions installs the exact Gradle version automatically; Android Studio can also import the project directly.

```bash
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Device target

The runtime target is deliberately conservative: Android 6.0+ (`minSdk 23`), a 30 FPS render cap, no game engine, no NDK/native ABI requirement, and no external assets in the initial build.

## Test goal

The first GMEE test should answer only three questions:

1. Does it install and launch reliably?
2. Does it hold 30 FPS without obvious hitching?
3. Can you consistently lift the front wheel and catch a loop-out with the rear brake?

If those are yes, the next pass is visual polish, sound, rider movement, and a better motorcycle model.
