# Physics V2 Implementation Pass 01 — First Live Plant

Status: **Physics V2 is wired into the live Balance Point gameplay loop on `main`.**

This pass is the transition from the research/coding gate to an executable first plant. The legacy presentation stack remains in place, but the normal motorcycle dynamics path now comes from `PhysicsV2Plant`.

## Runtime architecture now active

The live plant contains:

- 3-D rigid-body translation;
- quaternion chassis attitude and body angular velocity;
- a physical steering degree of freedom driven by steering torque;
- front and rear suspension generalized coordinates;
- independent front and rear wheel angular states;
- coherent front/rear hardpack tire models with vertical compliance, longitudinal slip, lateral/camber response, combined slip and relaxation;
- integrated engine angular speed;
- physical clutch-slip torque;
- Honda primary, gearbox and final-drive ratios;
- engine loss/braking torque;
- equal-and-opposite driveline/wheel reaction torques;
- deterministic 120 Hz stepping and force/moment telemetry.

There is no Physics V2 wheelie mode, jump mode, landing mode or airborne attitude mode. Contact state and Newton-Euler forces determine those outcomes.

## Presentation separation

The imported GLB remains at its existing visual geometry. In particular, its approximately 1.403 m wheelbase is not used as the physical reference wheelbase. Physics V2 retains the 1.48082 m CRF450R reference geometry internally and converts the physical pose to the existing mesh root only at the presentation boundary.

Likewise, the historical `BalancePointGame` COM/art constants remain only where the renderer expects them. They do not feed the V2 force or moment calculation.

## Launch-clutch correction found during validation

The first standalone plant test exposed zero-throttle creeping because the provisional automatic clutch controller retained minimum plate engagement at idle.

The corrected controller opens the clutch at closed throttle/rest and increases plate-force command with throttle request and road speed. Transmitted torque still comes exclusively from the continuous physical clutch slip law. No velocity deadband or direct wheel-force cancellation was introduced.

## Automated validation currently passing

The guarded migration passed the repository quality gate and the full core unit-test suite before it was allowed to commit the live runtime.

Current dedicated V2 tests cover:

- monotone tire vertical load response;
- combined-slip utilization bounded to the common friction envelope;
- clean tire contact release;
- physical engine RPM response to torque/load;
- five-speed gearbox bounds;
- finite drivetrain torque across the operating range;
- flat-ground plant stability;
- physical forward acceleration under throttle;
- airborne wheel/driveline reaction affecting pitch without an air-control mode;
- deterministic fixed-step repeatability.

## Next acceptance work

Before calling the first mobile build a calibrated Physics V2 release, machine validation should be expanded around:

- static axle-load and sag residuals;
- braking load transfer;
- wheelspin/traction saturation;
- wheelie onset versus acceleration and CG uncertainty;
- jump/free-flight momentum conservation;
- landing peak force/travel;
- timestep-halving convergence;
- uncertainty-endpoint sweeps for Gates A-D.

Those tests refine the plant; they do not reopen the pre-runtime research gate.
