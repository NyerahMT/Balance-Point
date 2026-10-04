# Physics V2 Coding Gate

Status: **authoritative pre-runtime checklist through Research Pass 14.**

This document defines when Balance Point may begin the assists-disabled Physics V2 runtime implementation. It prevents two opposite failures: inventing physical constants to make the bike feel right, and waiting indefinitely for factory/laboratory data that are unlikely to become public.

Research Pass 12 introduced `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY`; Pass 13 closed suspension; Pass 14 closes engine/clutch/drivetrain for first-plant implementation under the same explicit-uncertainty policy.

---

## 1. Allowed parameter states

Every runtime physical parameter must have one of these states:

- `PUBLISHED` — direct manufacturer, standard, or technical source;
- `MEASURED` — direct same-configuration physical measurement;
- `DERIVED` — calculated from accepted published/measured quantities;
- `IDENTIFIED` — estimated by documented system identification with uncertainty;
- `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` — reproducibly derived from relevant published geometry/models, with an explicit interval, centralized replacement point, and validation across that interval;
- `BOUNDED_LOW_SENSITIVITY` — exact value unavailable, but a defensible interval exists and deterministic sensitivity testing proves the uncertainty does not materially change the applicable validation result.

The following do **not** open a gate: plausible, typical, feels-right, copied-from-another-bike-without-transfer-argument, tuned-to-gameplay, or hidden-assist values.

---

## 2. Reference configuration

Reference: **late-2025 US Honda CRF450R production-spec configuration**.

- [x] model/year/spec revision frozen;
- [x] nominal mass and chassis geometry frozen;
- [x] MX33 tire identities frozen;
- [x] Honda primary, gear and final ratios frozen;
- [x] conflicting early launch values retained separately instead of averaged.

---

## 3. Gate A — rigid-body geometry and mass properties

First-plant nominal set is reproduced by `tools/physics_v2_mass_property_estimate.py`.

- [x] nominal wet mass: `112.94 kg`;
- [x] `x_CG = 0.722221 m` from rear contact, implementation uncertainty `+/-0.015 m`;
- [x] `z_CG = 0.657 m`, implementation uncertainty `+/-0.040 m`;
- [x] `Ixx ~= 24.3 kg m^2`, `+/-35%`;
- [x] `Iyy ~= 47.1 kg m^2`, `+/-25%`;
- [x] `Izz ~= 28.7 kg m^2`, `+/-35%`;
- [x] plant hardpoints may enter as explicit photogrammetric/CAD-style engineering estimates;
- [x] products of inertia initialize to zero for the symmetric baseline and remain replaceable;
- [x] wheel/steering/swingarm component inertias may use geometry-based engineering estimates with conservative bounds.

The Pass-11 height sweep showed about an 18% effect on idealized wheelie/rear-lift thresholds, so validation must include the stated CG envelope rather than treating the nominal height as exact.

**Gate A: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 4. Gate B — suspension kinematics and force laws

Research Pass 13 and `tools/physics_v2_gate_b_estimate.py` define the first-plant suspension.

### Front

- [x] Showa 49 mm coil fork, 309.9 mm travel;
- [x] 5.0 N/mm per leg, 10.0 N/mm combined spring rate;
- [x] 2025 valve/seal/oil revisions retained as architecture context;
- [x] nominal equivalent compression damping about `620 N s/m`;
- [x] nominal rebound damping about `930 N s/m`;
- [x] damping envelope tied to off-road bounce-mode damping ratio `0.35-0.55`;
- [x] explicit friction term;
- [x] progressive end-stroke model beginning at 75% travel and reaching about 2.5 kN additional force at full travel, `+/-50%`.

### Rear

- [x] 52 N/mm shock spring;
- [x] approximately 133 mm usable shock stroke;
- [x] 585.2 mm manufacturer swingarm constraint;
- [x] average wheel/shock displacement ratio `2.3299`;
- [x] first-plant local rising-rate curve: about `2.420 -> 2.340 -> 2.086`;
- [x] local motion-ratio uncertainty `+/-0.12`;
- [x] resulting wheel spring rate rises from about `8.88 -> 11.95 kN/m`;
- [x] nominal rear shock compression damping about `3.88 kN s/m`;
- [x] nominal rebound damping about `5.81 kN s/m`;
- [x] damping-force uncertainty `+/-35%`;
- [x] bump-stop begins near 82% shock stroke and reaches about 4.5 kN at full compression, `+/-50%`.

No wheel-rate multiplier, anti-squat multiplier, jump mode, or attitude torque substitutes for physical geometry/forces.

**Gate B: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 5. Gate C — engine, clutch and drivetrain

Research Pass 14 and `tools/physics_v2_drivetrain_estimate.py` define the first-plant powertrain.

### C1. Published/measured anchors

- [x] idle `2,000 +/-100 rpm`;
- [x] primary reduction `2.357`;
- [x] gears `2.133 / 1.706 / 1.421 / 1.211 / 1.043`;
- [x] final reduction `49/13 = 3.76923`;
- [x] overall reductions about `18.950 / 15.156 / 12.624 / 10.759 / 9.266`;
- [x] wet hydraulic multiplate clutch, 8 plates / 6 springs;
- [x] Dirt Rider rear-wheel dyno peak torque `44.61 N m @ 6,900 rpm`;
- [x] Dirt Rider rear-wheel dyno peak power `51.1 hp @ 9,600 rpm`;
- [x] dyno D404 rear-tire test configuration retained so the data are not reused as MX33 transient/tire data.

### C2. First-plant engineering estimates

- [x] bounded dense torque reconstruction anchored to the published peak torque/power; interior shape uncertainty `+/-6%`;
- [x] crank-to-wheel drivetrain efficiency nominal `0.95`, interval `0.93-0.97`;
- [x] nominal pre-loss peak engine torque about `46.96 N m`;
- [x] equivalent crank-referenced engine inertia `0.0065 kg m^2`, interval `0.00325-0.00975 kg m^2`;
- [x] closed-throttle engine-loss map from about `2.5 N m @ 2,000 rpm` to `12.7 N m @ 11,500 rpm`, `+/-40%`;
- [x] clutch capacity nominal `150 N m` at clutch shaft, interval `115-190 N m`;
- [x] nominal peak transmission requirement about `110.7 N m` at the clutch shaft;
- [x] continuous clutch law `T = engagement * Tcap * tanh(delta_omega / omega_scale)`;
- [x] clutch slip scale nominal `20 rad/s`, interval `10-35 rad/s`;
- [x] limiter nominal `11,500 rpm`, interval `11,000-12,000 rpm` until stronger same-generation data replace it.

### C3. Architecture rules

- engine RPM is integrated from `I*omega_dot = torque sum`; it is never interpolated toward a target RPM;
- clutch torque acts equal-and-opposite on engine and gearbox sides;
- engine braking is crank loss torque, not direct negative chassis force;
- gear selection changes physical ratio;
- shift interruption is represented through combustion/clutch state, not a rear-wheel-force multiplier;
- no powertrain parameter is tuned solely to produce a desired wheelie response.

**Gate C: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 6. Gate D — tire/contact physics

Known:

- [x] exact MX33 front/rear identities and nominal sizes;
- [x] separate front/rear architecture required;
- [x] front unloaded geometry catalog band;
- [x] rear tire mass band;
- [x] motocross-specific load-dependent vertical-stiffness shape prior;
- [x] representative hardpack pressure envelope and instrumented-identification methodology defined.

Still required as measured, identified, or bounded engineering-estimate datasets:

- [ ] loaded/effective rolling radii;
- [ ] front/rear vertical `Fz(delta)` behavior;
- [ ] radial damping/transient behavior;
- [ ] rear longitudinal `Fx(kappa,Fz)`;
- [ ] front braking `Fx(kappa,Fz)`;
- [ ] front/rear lateral + camber response `Fy(alpha,gamma,Fz)`;
- [ ] aligning/twisting moments;
- [ ] coherent combined-slip law;
- [ ] relaxation/transient scales;
- [ ] contact-loss/reacquisition continuity validation.

A constant `mu*Fz` model plus unrelated lateral clamp is insufficient.

**Gate D: NOT READY / FINAL PHYSICAL-DATA BLOCKER.**

---

## 7. Gate E — rider/controller

Gate E does not block the first passive no-assist plant if the rider is represented as a fixed/simplified reference mass with no injected attitude torque.

Before gameplay calibration: rider neutral mass/CG, bar/peg attachment coordinates, body motion limits, reduced arm/leg force model, steering-torque envelope, virtual rider separation, and assist-off validation must be completed.

**Gate E: ARCHITECTURE READY; GAMEPLAY CALIBRATION DEFERRED.**

---

## 8. Gate N — numerical integration

Candidate families remain semi-implicit fixed-step with justified substeps and linear-implicit stiff-force treatment.

Required benchmarks include airborne momentum conservation, static sag, tire compliance, full-travel landing, combined-slip transient, timestep-halving convergence, deterministic 120 Hz repeatability, mobile CPU budget, and no NaN/energy blow-up.

**Gate N: BENCHMARK-READY.**

---

## 9. Minimum condition to begin Physics V2 runtime

The runtime coding gate becomes OPEN when:

1. Gate A mass/geometry set exists — **satisfied**;
2. Gate B continuous suspension model exists — **satisfied**;
3. Gate C rotational engine/clutch/drivetrain model exists — **satisfied**;
4. Gate D coherent hardpack tire/contact model exists;
5. every runtime constant has units, provenance/classification, uncertainty where applicable, and one replacement point;
6. validation covers material uncertainty envelopes;
7. there are no special wheelie, jump, landing, or airborne-attitude physics modes.

The first runtime remains one 6-DOF chassis with quaternion attitude, physical steering DOF, fork/swingarm coordinates, wheel angular states, coherent tire forces, engine/clutch/chain rotational dynamics, assists disabled, and deterministic force/moment telemetry.

---

## 10. Current verdict

**OVERALL CODING GATE: CLOSED — ONE PHYSICAL SUBSYSTEM REMAINS.**

**Gate A, Gate B, and Gate C are READY.**

The sole remaining pre-runtime physical subsystem is:

- **Gate D: MX33 hardpack tire/contact behavior.**

Next action: **Gate D.**
