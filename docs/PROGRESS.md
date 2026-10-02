# Balance Point development progress

**Current overall progress: 32%**

This percentage tracks progress toward the current core target: a polished dirt-bike simulation that can support credible supercross racing while remaining fun for hills, dunes, trails, jumps, wheelies, and open riding. It is not a percentage of every future game feature.

The score is weighted by subsystem. It only moves when working behavior or architecture lands on `main`; planning alone does not count.

| Subsystem | Weight | Current credit | Completion target |
| --- | ---: | ---: | --- |
| Core architecture and tooling | 15% | 13% | Motorcycle is a first-class system with clean simulation, rig, rendering, data, tests, and CI boundaries. |
| Longitudinal dynamics, drivetrain, traction and wheelies | 15% | 11% | Engine/gearbox/rear-wheel dynamics, braking and longitudinal tire behavior work across starts, climbs, wheelies and acceleration. |
| Physical suspension | 15% | 5% | Independent fork and rear suspension states drive axle motion, spring/damper forces, travel limits and the visual rig. |
| Lateral tires, steering and lean | 20% | 1% | Slip-angle/combined-slip behavior, countersteer, lean response and loss/recovery of lateral grip feel motorcycle-like. |
| Rider dynamics | 10% | 1% | Rider mass and body input affect COM, preload, braking, cornering, whoops, jumps and air control. |
| Air, jump and landing dynamics | 10% | 1% | Takeoff, flight attitude, casing, bottoming and landings emerge from the same bike model without canned jump modes. |
| MX/open-terrain riding loop | 10% | 0% | A representative test environment proves berms, whoops, rhythm sections, hills, dunes and trails with the same setup. |
| Tuning, validation and polish | 5% | 0% | Repeatable tests, telemetry, tuning passes, performance work and final-feel validation are in place. |
| **Total** | **100%** | **32%** | |

## Current development direction

The bike should sit between the accessibility of MX-vs-ATV-style riding and the mechanically convincing behavior of a deeper motorcycle simulator. The goal is not maximum difficulty. The goal is a bike whose useful behavior comes from coherent motorcycle dynamics rather than special-case modes.

## Immediate sequence

1. Validate and tune the first physical-suspension build in actual riding.
2. Add suspension telemetry and, if needed, explicit unsprung wheel states/motion-ratio refinement rather than hiding problems in renderer smoothing.
3. Build a lateral tire and steering/lean model around contact-patch forces and countersteer.
4. Promote rider movement from a simple COM offset into a dynamic rider-mass/input system.
5. Refine jump faces, flight attitude and landings against the same suspension/tire model.
6. Add a compact supercross torture-test section before expanding the world.

## Completed in the 29% milestone

- Added a first-class `Motorcycle` aggregate around the imported bike and mechanical visual rig.
- `GameScene` now updates and renders one motorcycle instead of directly owning/rendering imported body, engine, steering and wheel instances.
- Updated the neutral-pose/topology validator so CI enforces the new motorcycle ownership boundary without weakening the GLB geometry checks.

## Completed in the 32% milestone

- Added `MotorcycleSuspension` with independent front/rear compression states in metres, shaft velocity, static sag, asymmetric compression/rebound damping, high-speed compression damping, travel limits and progressive bump stops.
- Replaced the old embedded contact-spring force calculation with front/rear suspension units while retaining finite-radius terrain contact and the existing longitudinal drivetrain/tire model.
- Changed the position constraint from generic tire non-penetration to mechanical bottom-out enforcement so legitimate fork/shock travel is no longer forced out of the chassis.
- Suspension travel now changes contact-patch pitch leverage and is fed directly to the imported swingarm, rear wheel, lower fork sliders and front wheel.
- Removed terrain-following suspension animation from `DirtBikeVisualRig`; the renderer now consumes physics-owned suspension state.
- Added regression tests for static sag, compression/rebound asymmetry, progressive bump-stop behavior, travel/force bounds and full extension.

## Progress rule

A subsystem does not receive full credit because it exists visually or has placeholder math. Full credit means it is integrated, exercised in representative riding, regression-protected where practical, and no longer depends on special-case behavior that contradicts the target motorcycle model.
