# Repository Cleanup Audit — 2026-10-16

## Scope and guardrails

The shipping mobile app remains Java/libGDX with Android and iOS launchers,
using `PhysicsV3MultibodyPlant` at runtime. This cleanup removes clearly
superseded source, its isolated tests, historical one-off patch/estimate tools,
and iterative V2 development logs **without replacing live gameplay physics**.
Git history retains all deleted material.

## Removal groups

- **Legacy runtime:** `PhysicsV2Plant`, `PhysicsV2RiderController`,
  and `PhysicsV2Tire`, plus their five V2-only test files.
- **One-shot tools:** seven previously applied source-patching/verification
  scripts and five V2 estimate utilities no longer invoked by mobile CI.
- **Stale documentation:** fifteen `PHYSICS_V2_RESEARCH_PASS_*` logs plus
  five phase-specific V2 reports/gates superseded by V3.

The historical `PhysicsV2Powertrain` class and its test **must remain**:
the V3 plant still calls that implementation. Retained physical-parameter,
calibration, and measurement documents remain useful references.

## Retained / deferred

- **Unreal + Project Chrono:** a separate manually run prototype, not
  linked into the current libGDX mobile runtime. Do not delete this separate
  development effort without explicitly retiring that direction.
- **BalancePointGame:** dead wheel-contact helper methods and orphaned old subsystem
  instances were removed. Remaining crash/recovery and presentation-coupling
  code needs a dedicated behavior-preserving refactor.
- **Older Motorcycle* components:** the core still refers to several such
  components; their actual execution paths need runtime instrumentation.
- **Generated track assets and validators:** remain required by packaging
  and CI or asset loading; do not delete just because they are generated.
- **Third-party notices and attribution:** retain even when refactoring.

## Follow-up

1. Verify the branch with core regression tests, asset validators,
   Android assembly, and iOS packaging in the Mobile Builds PR workflow.
2. Inspect runtime call paths for duplicated simulation state/forces.
3. Build the categorized developer physics menu with live controls and
   diagnostic capture before changing ride behavior.
4. Tune All Out-inspired steering and independent rider lean, with manual
   throttle/rear-brake-driven wheelie balance.

## Second-pass targeted source hygiene

- Removed three orphaned legacy components (`MotorcycleBodyContact`,
  `MotorcycleLateralDynamics`, and `MotorcycleRiderDynamics`) and their tests.
- Removed unused V2-era wheel-surface/contact helper methods, four unused
  contact snapshots, and unused component construction/reset from `BalancePointGame`.
- Renamed stale local `v2Input` to `v3Input` and corrected V3-facing comments.
- Kept active suspension, drivetrain, crash/recovery, render and audio paths intact.
