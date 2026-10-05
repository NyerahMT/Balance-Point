# Balance Point: Unreal + Project Chrono migration

## Decision

The Java/libGDX runtime is frozen as the playable legacy baseline while the next runtime is built under `unreal/`.
The new motorcycle dynamics must use the actual Project Chrono C++ solver. Do not create another hand-written
`PhysicsV4` approximation.

Baseline before migration: `294716e49019950c3b0861f1165e20a33d290466`.

## Architecture

`Unreal presentation/input` -> `BalancePointChrono UE module` -> `bp::ChronoBike` -> `Project Chrono 10.0.0`

The pure `bp::ChronoBike` class has no Unreal dependency. The headless CMake target compiles the exact same source
that Unreal consumes. This prevents the validation model and shipped model from drifting apart.

## Mechanical topology (first milestone)

- main frame rigid body
- swingarm revolute joint
- rear wheel revolute axle
- raked steering axis driven by steering torque
- split upper/lower telescopic fork with prismatic joint
- front and rear TSDA spring/damper links
- front wheel revolute axle
- rotating bodies with Chrono gyroscopic torque enabled
- SMC unilateral ground contact
- external analytical Pacejka-style longitudinal/lateral/camber tire force with relaxation
- no airborne mode and no anti-wheelie torque

The default geometry starts from the 2026 Honda CRF450R public specifications plus the measured/derived Balance
Point parameters already collected. Exact mass partition and suspension attachment geometry remain calibration data,
not Honda-published values.

## Hard gates before legacy runtime is retired

1. Headless Chrono build passes on every physics change.
2. Raw steering torque produces the correct steering-axis response with real rake/trail geometry.
3. Straight-line stability and roll perturbation behavior are validated against a reference motorcycle model.
4. Front normal load can go continuously to zero without a state-machine transition.
5. Free-flight conserves linear/angular momentum except gravity and explicit internal wheel/drivetrain reactions.
6. Re-contact/landing remains stable under timestep halving.
7. Unreal renders the exact Chrono state; Chaos does not run a second motorcycle simulation underneath it.
8. iOS arm64 Chrono static build is proven before the old iOS runtime is removed.

## Explicit non-goals for milestone 1

- menus and HUD migration
- final terrain art
- final rider animation
- final engine audio
- final gearbox/engine model
- wheelie assists

Those wait until the steering/chassis model proves itself.
