# Direct-control and physics-steering presentation experiment

## Why this branch exists
After the regression-tested turn-release fix, phone feedback still described
mushy controls and settings that only displaced the underlying sloppiness.

Auditing the actual runtime identified **three layers of non-physical latency**:

1. The app explicitly targeted 30 FPS, limiting visible/touch updates despite
   a 120 Hz fixed-step physics plant.
2. The game low-pass-filtered thumb steering demand before sending it to a
   plant that already integrates steering inertia and servo damping; the rider
   fore/aft target was also filtered outside the plant.
3. The bike visual rig synthesized its own steering from roll, thumb input
   and speed, and smoothed that separately; therefore the forks often showed
   a different angle from the actual V3 front wheel.

## Experimental changes
- Request 60 rendered FPS; the physics timestep stays 1/120 second.
- Feed thumb **steering intent** directly to V3, retaining its real steering
  inertia, torque limits, countersteer and tire slip relaxation.
- Feed the rider fore/aft intent directly to V3's existing damped physical
  generalized coordinate, instead of adding another game-layer filter.
- Animate front forks using actual `PhysicsV3MultibodyPlant.steerAngle()` in
  radians. No speed-dependent invented visual countersteer or second render-time
  low-pass filter.
- Add regression tests ensuring the front-fork visual angle is determined only
  by physical steering, including airborne and nonzero-lean conditions.

## Scope and follow-up
This is **not** an overall physics rebuild and does not implement independent
side-to-side rider mass shift yet. The current V3 steering controller still
interprets steer input as an implicit lean request. Its tire model and contact
forces have not been modified in this branch.

Verify fixed-step steering tests and Android/iOS builds, then evaluate the ride
at multiple speeds and confirm the front-fork motion aligns with actual turns.
A following workstream should separate direct bar steering, commanded rider
lateral movement, and bike balance, rather than manipulating more servo gains.
