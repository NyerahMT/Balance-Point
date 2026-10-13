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

## Gameplay steering/lean input (first implementation)

The Unreal-facing component defaults to `UseLeanControl=true`: the turn axis
requests a speed-dependent bike lean, not a constant handlebar torque. The
controller countersteers into the requested lean, allows the physics to generate
cornering yaw via tire contact, and supplies steady-state bar torque to maintain
the turn. At walking speed, the same stick directly requests bar angle. Releasing
the stick requests upright without directly changing the frame's orientation.
It applies only torque to the existing Chrono steering joint.

`UseLeanControl=false` preserves raw handlebar torque for engineering diagnostics.
Both modes obey `MaxHandlebarTorqueNm` in the Unreal component. The positive
Unreal turn axis is mapped to Chrono's right-hand/left-positive coordinate system.
`BikeLeanDeg`, `TargetLeanDeg`, and `AppliedSteeringTorqueNm` are exposed for
in-game tuning. When the front tire is airborne, only bar-rate damping is used:
the controller cannot magically roll the bike toward a desired lean.

`bp_ride_steering_test` covers the control mapping independently of the heavy
Chrono build; `bp_chrono_smoke` exercises lean mode on the physical assembly.
These checks do not certify game feel: controlled MX-track riding, steering
polarity, steady-state cornering and timestep/landing validation must pass
before removing the legacy mobile simulation. Avoid mistaking a stable controller
unit test for a validated vehicle model.
