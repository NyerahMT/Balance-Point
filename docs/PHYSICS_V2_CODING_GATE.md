# Physics V2 Coding Gate

Status: **authoritative pre-runtime checklist through Research Pass 13.**

This document defines when Balance Point may begin the assists-disabled Physics V2 runtime implementation. It prevents two opposite failures: inventing physical constants to make the bike feel right, and waiting indefinitely for factory/laboratory data that are unlikely to become public.

Research Pass 12 adds a controlled engineering-estimate state. Research Pass 13 applies that state to the 2025 suspension so the project can move forward without pretending estimated linkage or damper values are factory measurements.

---

## 1. Allowed parameter states

Every runtime physical parameter must have one of these states:

- `PUBLISHED` — direct manufacturer, standard, or technical source;
- `MEASURED` — direct same-configuration physical measurement;
- `DERIVED` — calculated from accepted published/measured quantities;
- `IDENTIFIED` — estimated by documented system identification with uncertainty;
- `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` — reproducibly derived from relevant published geometry/models, with an explicit interval, centralized replacement point, and validation across that interval;
- `BOUNDED_LOW_SENSITIVITY` — exact value unavailable, but a defensible interval exists and deterministic sensitivity testing proves the uncertainty does not materially change the applicable validation result.

The following still do **not** open a gate:

- plausible;
- typical;
- feels right;
- copied from another bike without a transfer argument;
- tuned until gameplay looks correct;
- hidden assist authority.

An engineering estimate is acceptable only when the uncertainty is visible to validation. It may never be silently tightened because one nominal value feels better.

---

## 2. Reference configuration gate

Reference: **late-2025 US Honda CRF450R production-spec configuration**.

- [x] model/year frozen;
- [x] nominal mass source frozen;
- [x] wheelbase frozen;
- [x] rake/trail frozen;
- [x] front/rear travel frozen;
- [x] front/rear tire identity frozen;
- [x] drivetrain ratios frozen;
- [x] conflicting early Honda launch specification retained separately rather than averaged.

---

## 3. Gate A — rigid-body geometry and mass properties

### A1. Geometry

- [x] wheelbase;
- [x] rake;
- [x] trail;
- [x] front/rear nominal travel;
- [x] manufacturer-backed swingarm length: **585.2 mm**;
- [x] front triple-clamp offset high-confidence secondary bound;
- [x] plant hardpoints may enter the first implementation as explicit photogrammetric/CAD-style engineering estimates and are replaced as better same-generation geometry is recovered;
- [x] footpeg/handlebar geometry deferred to Gate E while the passive plant uses a fixed reference rider.

### A2. Bike mass-property set for the first plant

Nominal values and uncertainty are defined by Research Pass 12 and reproduced by `tools/physics_v2_mass_property_estimate.py`.

- [x] nominal wet mass: `112.94 kg` — PUBLISHED;
- [x] longitudinal CG prior: `x_CG = 0.722221 m` from rear contact — DERIVED from same-generation Honda data; implementation transfer uncertainty `+/-0.015 m`;
- [x] vertical CG: `z_CG = 0.657 m` — ENGINEERING_ESTIMATE_WITH_UNCERTAINTY from Honda graphical extraction; implementation transfer uncertainty `+/-0.040 m`;
- [x] roll inertia: `Ixx ~= 24.3 kg m^2` — ENGINEERING_ESTIMATE_WITH_UNCERTAINTY, `+/-35%`;
- [x] pitch inertia: `Iyy ~= 47.1 kg m^2` — ENGINEERING_ESTIMATE_WITH_UNCERTAINTY, `+/-25%`;
- [x] yaw inertia: `Izz ~= 28.7 kg m^2` — ENGINEERING_ESTIMATE_WITH_UNCERTAINTY, `+/-35%`;
- [x] nominal products of inertia initialize to zero for the symmetric baseline and remain separately replaceable.

The pitch estimate is not arbitrary. Pass 12 reconstructs a published off-road motorcycle model with the parallel-axis theorem, obtains a whole-bike dimensionless pitch coefficient, and transports that coefficient to the frozen CRF mass and wheelbase.

The Pass-11 `0.60-0.72 m` CG-height sweep remains important: it showed about an 18% effect on idealized wheelie/rear-lift thresholds. That is why the first plant must be tested across the stated CG interval rather than treating the nominal height as truth.

### A3. Component inertias

Wheel, steering/fork, and swingarm/linkage inertias may enter their respective subsystem implementation as geometry-based engineering estimates with conservative intervals. They must remain centralized and replaceable. No component inertia may be tuned solely to produce a desired handling response.

### A4. Gate-A acceptance rule

Gate A is READY when the first-plant mass/geometry set is reproducible, every estimated value has an explicit interval, validation runs at the relevant interval endpoints, and no assist compensates for a mass-property choice.

**Current status: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 4. Gate B — suspension kinematics and force laws

### B1. Front suspension

- [x] Showa 49 mm coil fork and 309.9 mm travel;
- [x] stock spring rate: 5.0 N/mm per leg, 10.0 N/mm combined;
- [x] 2025 Bending Control Valve, seal and oil revisions confirmed;
- [x] first-plant equivalent damping anchor derived from published off-road critical-damping data;
- [x] nominal front compression coefficient: about `620 N s/m`;
- [x] nominal front rebound coefficient: about `930 N s/m`;
- [x] damping envelope tied to off-road bounce-mode damping ratio `0.35-0.55`;
- [x] progressive end-stroke model begins at 75% travel and reaches about 2.5 kN additional force at full travel, `+/-50%`;
- [x] friction remains an explicit Coulomb/Stribeck term rather than being hidden in damping;
- [x] moving/unsprung mass may use same-class engineering estimates with `+/-25%` bounds.

The first damper implementation must be branch-separated and continuous through zero velocity. It may use a smooth digressive/blow-off representation but may not extend one unlimited linear coefficient to motocross shaft velocities.

### B2. Rear suspension

- [x] stock shock spring rate: 52 N/mm;
- [x] shock hardware envelope retained at approximately 133 mm usable stroke;
- [x] 2025-specific one-piece Pro-Link and revised rising-rate intent confirmed by Honda;
- [x] swingarm arc constrained by the 585.2 mm manufacturer length;
- [x] full-stroke average wheel/shock displacement ratio constrained to `2.3299`;
- [x] first-plant local motion-ratio reconstruction defined and reproducible;
- [x] nominal local motion ratio: `2.420` top, `2.340` at 70% shock stroke, `~2.086` at full compression;
- [x] local motion-ratio uncertainty: `+/-0.12`;
- [x] the estimated curve integrates to the frozen 309.88 mm wheel travel over 133 mm shock stroke;
- [x] resulting wheel spring rate rises from about 8.88 kN/m to 11.95 kN/m across travel;
- [x] nominal rear shock damping anchor: about `4.84 kN s/m` at the reference mid-stroke leverage;
- [x] nominal rear shock compression coefficient: about `3.88 kN s/m`;
- [x] nominal rear shock rebound coefficient: about `5.81 kN s/m`;
- [x] nominal damping-force uncertainty: `+/-35%`;
- [x] rear bump-stop begins at 82% shock stroke and reaches about 4.5 kN shock-axis force at full compression, `+/-50%`;
- [x] swingarm/linkage moving mass and inertia may use same-class engineering estimates with conservative bounds.

### B3. Gate-B acceptance rule

Gate B is READY when one continuous suspension model can reproduce static sag, bump response, jump-face compression, whoops, landing and rebound without wheel-rate hacks or attitude torques. Engineering estimates are allowed under the Pass-12 uncertainty policy and must remain centralized, replaceable and telemetry-visible.

Research Pass 13 and `tools/physics_v2_gate_b_estimate.py` provide the first-plant parameterization and uncertainty envelope.

**Current status: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 5. Gate C — engine, clutch and drivetrain

Closed/narrowed:

- [x] primary reduction;
- [x] five gear ratios;
- [x] final reduction;
- [x] idle RPM;
- [x] peak dyno power/torque;
- [x] coarse full dyno trace;
- [x] same-generation dyno repeatability bound;
- [x] clutch architecture and plate/spring count;
- [x] hard lower bound on fully engaged clutch capacity.

Still required:

- [ ] dense validated full-throttle torque curve;
- [ ] equivalent engine rotational inertia estimate/identification;
- [ ] closed-throttle engine-braking/loss map;
- [ ] clutch transmitted-torque versus slip/command model;
- [ ] clutch rotating inertia or justified equivalent;
- [ ] drivetrain loss characterization.

**Current status: PARTIAL / NOT READY.**

---

## 6. Gate D — tire/contact physics

Known:

- [x] exact MX33 front/rear identities and nominal sizes;
- [x] separate front/rear tire architecture required;
- [x] front unloaded geometry catalog band;
- [x] rear tire mass band;
- [x] motocross-specific load-dependent vertical-stiffness shape prior.

Still required as measured, identified, or engineering-estimate datasets with uncertainty:

- [ ] loaded/effective rolling radii;
- [ ] vertical `Fz(delta)` behavior;
- [ ] radial damping/transient behavior;
- [ ] longitudinal hardpack response;
- [ ] lateral/camber response;
- [ ] combined-slip behavior;
- [ ] aligning/twisting moment treatment;
- [ ] relaxation/transient scales.

A constant `mu*Fz` model plus an unrelated lateral clamp is insufficient.

**Current status: NOT READY / largest blocker.**

---

## 7. Gate E — rider and controller

This gate does not block the passive no-assist plant if the first rider is a fixed/simplified reference mass with no injected attitude torque.

Before gameplay-controller calibration:

- [ ] rider neutral mass/CG configuration;
- [ ] footpeg/handlebar attachment coordinates;
- [ ] body motion limits;
- [ ] reduced arm/leg force model;
- [ ] steering-torque envelope;
- [ ] virtual rider separated from physical plant;
- [ ] all assists switchable off.

**Current status: architecture ready; gameplay calibration deferred.**

---

## 8. Gate N — numerical integration

Candidate families remain:

- semi-implicit fixed-step with justified local substeps;
- linear-implicit stiff-force treatment.

Required benchmarks include airborne momentum conservation, static sag, tire compliance, full-travel landing, combined-slip transient, timestep-halving convergence, deterministic 120 Hz repeatability, mobile CPU budget, and no numerical blow-up.

**Current status: BENCHMARK-READY.**

---

## 9. Minimum condition to begin the Physics V2 runtime

The runtime coding gate becomes OPEN when:

1. Gate A supplies a centralized engineering mass/geometry set with uncertainty — **satisfied**;
2. Gate B supplies a continuous suspension kinematic/force model — **satisfied**;
3. Gate C supplies engine inertia/loss and clutch behavior sufficient for rotational dynamics;
4. Gate D supplies one coherent hardpack contact model;
5. every runtime constant has units, provenance/classification, uncertainty where applicable, and a single replacement point;
6. validation covers material uncertainty envelopes;
7. there are no special wheelie, jump, landing, or airborne-attitude physics modes.

The first implementation target remains one 6-DOF rigid-body chassis, quaternion attitude, physical steering, physical fork/swingarm coordinates, wheel angular states, coherent tire/contact forces, engine/clutch/chain rotational dynamics, assists disabled, and deterministic force/moment telemetry.

---

## 10. Current verdict

**OVERALL CODING GATE: CLOSED.**

**Gate A and Gate B are now READY.** We are no longer waiting on unavailable factory mass-property or stock-Showa/linkage datasets for the first plant; both subsystems have reproducible engineering estimates with explicit uncertainty and replacement rules.

Remaining blocking work is concentrated in:

- Gate C: engine inertia/braking, dense torque curve and clutch dynamics;
- Gate D: MX33 hardpack tire/contact behavior.

Next action: **Gate C.**
