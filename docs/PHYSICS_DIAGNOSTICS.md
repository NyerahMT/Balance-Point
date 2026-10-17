# Physics Lab Diagnostics & Replay

## Purpose

Capture the exact commands and corresponding state of the **live V3** motorcycle
without trying to infer a bug from screenshots or descriptions alone. The
record/replay path uses the same 120 Hz physics solver as the mobile game.

## Using the developer build

1. In the game, pause and open **PHYSICS DEV**.
2. Tap **RECORD**: the game starts a fresh ride from a reset and begins capturing.
3. Reproduce the problem, then pause and reopen **PHYSICS DEV**.
4. Tap **EXPORT** to stop recording and save a CSV file under the app's local
   Documents area with the name `BalancePoint-Capture-<timestamp>.csv`.
   On iOS, the app requests Files document sharing; confirm visibility on-device.
5. Tap **REPLAY** to reset and play back the captured fixed-step inputs. Replay
   automatically stops at the end of the trace. It can also be interrupted by
   the upper-left pause control.
6. Modify a tuning value and **REPLAY** again to compare the same rider inputs
   against new steering/suspension/wheelie corrections.

Recording is bounded to 90 seconds (10,800 fixed steps) and includes 29
telemetry columns plus the tuning settings used at capture start. Gear shifts
are captured as events and reproduced. The editor locks tuning changes while
a recording is active to avoid invalid comparisons.

## Interpreting replay results

- **Position error** is the largest distance between replay and original
  captured position at corresponding fixed steps, in metres.
- **Roll error** is the largest absolute roll-angle deviation, in radians.
- **Roll/yaw rate** and **requested roll** capture the angular-momentum
  overshoot and controller request during turn release.
- The CSV exposes applied steering-controller torque, requested countersteer,
  low-speed roll assistance and wheelie-catch torque, as well as rider commands,
  contact loads, tire utilization, suspension compression and vehicle motion.
- A zero-difference replay using unchanged settings and identical terrain
  indicates deterministic reproduction within the tested build. Nonzero
  differences after changing tuning are expected, not automatically defects.

## Boundaries

This is a **reset-based deterministic input replay**, not a serialized
whole-world snapshot or a video recording. Playback assumes the same terrain
version and initial conditions. Capture does not include camera gestures,
graphical state or a record of mid-session developer edits. Public-release
gating, mobile Files transfer verification, long-term diagnostic file format,
and touch-layout validation remain open tasks.
