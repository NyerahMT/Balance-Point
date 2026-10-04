# Physics V2 Stability Pass 1

## Purpose

This pass responds to first-device testing that exposed non-repeatable / unstable behavior in the initial Physics V2 runtime. The objective is numerical and structural stability before further fidelity work or subjective tuning.

## Changes

- Internally substep the 120 Hz game physics call so the coupled tire, clutch, suspension and rigid-body plant advances at no more than 1/480 s per integration step.
- Remove the acceleration-derived rider auto-brace feedback. Body-forward projected velocity could change solely from chassis rotation and then feed back into rider CG location on the next step.
- Retain an explicit virtual-rider forward brace driven by throttle demand. It changes rider mass position only; it does not apply an anti-wheelie torque or clamp chassis pitch.
- Remove the legacy `MotorcycleRiderDynamics.pitchReactionTorque()` path from Physics V2.
- Remove the provisional hand-expanded wheel gyroscopic chassis cross terms until they can be reintroduced through a validated wheel-orientation / Jacobian formulation with conservation tests.
- Give the front tire aligning moment one owner: the steering degree of freedom. It is no longer also injected directly into the chassis.
- Apply equal-and-opposite chassis reaction for virtual-rider handlebar torque and steering-joint damping.

## New validation

The stabilized plant must pass:

- 120 Hz vs 240 Hz timestep-halving convergence for a straight launch.
- 120 Hz vs 240 Hz timestep-halving convergence for a mild turn.
- 120 Hz vs 240 Hz convergence over deterministic wavy terrain.
- A scripted throttle / steering / rider-input ride without spontaneous angular-rate growth or non-finite orientation.
- Existing launch, wheelie, rideability, suspension, tire, powertrain and deterministic regression tests.

The guarded stability workflow passed before the stabilized plant was committed as `32749ab69c90c31981f6c239145e00adf3af4577`.

## Interpretation

Passing these tests does not prove final motorcycle fidelity. It establishes a materially stronger numerical baseline so future physical features can be added one at a time without hiding instability behind tuning or assists.
