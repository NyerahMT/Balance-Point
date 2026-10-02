# Balance Point development progress

**Current overall progress: 28%**

This percentage tracks progress toward the current core target: a polished dirt-bike simulation that can support credible supercross racing while remaining fun for hills, dunes, trails, jumps, wheelies, and open riding. It is not a percentage of every future game feature.

The score is weighted by subsystem. It only moves when working behavior or architecture lands on `main`; planning alone does not count.

| Subsystem | Weight | Current credit | Completion target |
| --- | ---: | ---: | --- |
| Core architecture and tooling | 15% | 12% | Motorcycle is a first-class system with clean simulation, rig, rendering, data, tests, and CI boundaries. |
| Longitudinal dynamics, drivetrain, traction and wheelies | 15% | 11% | Engine/gearbox/rear-wheel dynamics, braking and longitudinal tire behavior work across starts, climbs, wheelies and acceleration. |
| Physical suspension | 15% | 2% | Independent fork and rear suspension states drive axle motion, spring/damper forces, travel limits and the visual rig. |
| Lateral tires, steering and lean | 20% | 1% | Slip-angle/combined-slip behavior, countersteer, lean response and loss/recovery of lateral grip feel motorcycle-like. |
| Rider dynamics | 10% | 1% | Rider mass and body input affect COM, preload, braking, cornering, whoops, jumps and air control. |
| Air, jump and landing dynamics | 10% | 1% | Takeoff, flight attitude, casing, bottoming and landings emerge from the same bike model without canned jump modes. |
| MX/open-terrain riding loop | 10% | 0% | A representative test environment proves berms, whoops, rhythm sections, hills, dunes and trails with the same setup. |
| Tuning, validation and polish | 5% | 0% | Repeatable tests, telemetry, tuning passes, performance work and final-feel validation are in place. |
| **Total** | **100%** | **28%** | |

## Current development direction

The bike should sit between the accessibility of MX-vs-ATV-style riding and the mechanically convincing behavior of a deeper motorcycle simulator. The goal is not maximum difficulty. The goal is a bike whose useful behavior comes from coherent motorcycle dynamics rather than special-case modes.

## Immediate sequence

1. Finish migrating imported-bike ownership out of `GameScene` into a first-class `Motorcycle`/rig boundary.
2. Replace terrain-following visual suspension with actual front and rear suspension simulation states.
3. Feed physical suspension travel directly into the mechanical rig.
4. Build a lateral tire and steering/lean model around contact-patch forces and countersteer.
5. Promote rider movement from a simple COM offset into a dynamic rider-mass/input system.
6. Add a compact supercross torture-test section before expanding the world.

## Progress rule

A subsystem does not receive full credit because it exists visually or has placeholder math. Full credit means it is integrated, exercised in representative riding, regression-protected where practical, and no longer depends on special-case behavior that contradicts the target motorcycle model.
