# Physics V2 Coding Gate

Status: **authoritative pre-runtime checklist through Research Pass 15.**

Reference: **late-2025 US Honda CRF450R production-spec configuration**.

Passes 12-15 use `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` where exact same-bike factory/bench data are unavailable. An estimate is allowed only when it is reproducibly derived from relevant evidence, carries an explicit uncertainty interval, has one centralized replacement point, is visible in telemetry, and is validated across material uncertainty. Plausible/typical/feels-right values and hidden assists remain prohibited.

---

## 1. Gate A — geometry and mass properties

First-plant set from `tools/physics_v2_mass_property_estimate.py`:

- [x] wet mass `112.94 kg`;
- [x] longitudinal CG `x ~= 0.722221 m` from rear contact, implementation transfer uncertainty `+/-0.015 m`;
- [x] vertical CG `z ~= 0.657 m`, `+/-0.040 m`;
- [x] roll inertia `Ixx ~= 24.3 kg m^2`, `+/-35%`;
- [x] pitch inertia `Iyy ~= 47.1 kg m^2`, `+/-25%`;
- [x] yaw inertia `Izz ~= 28.7 kg m^2`, `+/-35%`;
- [x] chassis/plant hardpoints may use explicit photogrammetric/CAD-style engineering estimates;
- [x] wheel/steering/swingarm component inertias may use geometry-based engineering estimates with conservative bounds;
- [x] mass-property uncertainty must be included in validation rather than tuned away.

Pass 11 showed the broad public CG-height envelope can move idealized wheelie/rear-lift thresholds by about 18%, so the uncertainty remains physically meaningful.

**Gate A: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 2. Gate B — suspension

Research Pass 13 and `tools/physics_v2_gate_b_estimate.py` define the first-plant suspension.

### Front

- [x] Showa 49 mm coil fork, `309.9 mm` travel;
- [x] `5.0 N/mm` each leg / `10.0 N/mm` combined spring rate;
- [x] nominal compression damping about `620 N s/m`;
- [x] nominal rebound damping about `930 N s/m`;
- [x] damping envelope tied to off-road damping-ratio evidence;
- [x] explicit friction term;
- [x] progressive end-stroke term beginning around 75% travel, about 2.5 kN additional force at full travel, `+/-50%`.

### Rear

- [x] `52 N/mm` shock spring;
- [x] about `133 mm` usable shock stroke;
- [x] manufacturer `585.2 mm` swingarm constraint;
- [x] average wheel/shock ratio `2.3299`;
- [x] first-plant rising-rate curve about `2.420 -> 2.340 -> 2.086`;
- [x] local ratio uncertainty `+/-0.12`;
- [x] wheel spring rate rises about `8.88 -> 11.95 kN/m`;
- [x] nominal shock compression/rebound damping about `3.88 / 5.81 kN s/m`, `+/-35%`;
- [x] rear bump-stop begins near 82% stroke and reaches about 4.5 kN at full compression, `+/-50%`.

No wheel-rate multiplier, anti-squat multiplier, landing mode, or attitude torque may substitute for suspension geometry/forces.

**Gate B: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 3. Gate C — engine, clutch and drivetrain

Research Pass 14 and `tools/physics_v2_drivetrain_estimate.py` define the first-plant powertrain.

### Published/measured anchors

- [x] idle `2,000 +/-100 rpm`;
- [x] primary `2.357`;
- [x] gears `2.133 / 1.706 / 1.421 / 1.211 / 1.043`;
- [x] final `49/13 = 3.76923`;
- [x] wet hydraulic multiplate clutch, 8 plates / 6 springs;
- [x] rear-wheel dyno anchors `44.61 N m @ 6,900 rpm` and `51.1 hp @ 9,600 rpm`.

### First-plant engineering set

- [x] dense torque reconstruction anchored to measured data, interior shape uncertainty `+/-6%`;
- [x] drivetrain efficiency nominal `0.95`, interval `0.93-0.97`;
- [x] equivalent crank-referenced inertia `0.0065 kg m^2`, interval `0.00325-0.00975`;
- [x] closed-throttle loss map about `2.5 N m @ 2,000 rpm` to `12.7 N m @ 11,500 rpm`, `+/-40%`;
- [x] clutch capacity nominal `150 N m` at clutch shaft, interval `115-190 N m`;
- [x] continuous clutch law `T = engagement*Tcap*tanh(delta_omega/omega_scale)`;
- [x] slip scale nominal `20 rad/s`, interval `10-35 rad/s`;
- [x] engine RPM is an integrated rotational state; limiter/shift logic acts through torque/clutch state rather than overwriting RPM or rear-wheel force.

**Gate C: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 4. Gate D — tire/contact physics

Research Pass 15 and `tools/physics_v2_gate_d_estimate.py` define the first-plant effective hardpack MX33 contact model.

### D1. Geometry and vertical behavior

- [x] front/rear MX33 identities and nominal sizes frozen;
- [x] separate front/rear parameter sets;
- [x] reference hardpack pressure `100 kPa` with source evidence spanning roughly `80-100 kPa`;
- [x] front unloaded radius nominal `0.3538 m`, catalog interval `0.3526-0.3550 m`;
- [x] rear unloaded radius nominal `0.3373 m`, first-plant interval `0.329-0.345 m`;
- [x] monotone nonlinear `Fz(delta)` tables generated from motocross-specific load-dependent stiffness constraints;
- [x] front/rear effective stiffness anchors at 960 N: `105 / 115 kN/m`;
- [x] vertical-force uncertainty `+/-35%`;
- [x] radial damping nominal `550 / 650 N s/m`, `+/-60%`;
- [x] loaded/effective rolling radius generated separately from vertical compliance with `+/-6 mm` implementation uncertainty.

### D2. Longitudinal, lateral and camber behavior

First-plant pure-force laws are smooth brush-style saturation functions with mild load sensitivity.

- [x] front/rear nominal hardpack `mu_x` at 1 kN: `0.95 / 1.05`;
- [x] front/rear nominal `mu_y`: `0.90 / 0.95`;
- [x] friction level uncertainty `+/-22%`;
- [x] normalized longitudinal slip stiffness front/rear: `11 / 10`, `+/-30%`;
- [x] normalized lateral stiffness front/rear: `9 / 8 rad^-1`, `+/-30%`;
- [x] explicit camber contribution with equivalent-slip gains `0.32 / 0.28`, `+/-50%`;
- [x] the same laws handle drive, braking and sign reversal without special wheelspin/lock modes.

### D3. Combined slip and moments

- [x] one normalized friction ellipse resolves combined longitudinal/lateral demand;
- [x] no independent post-solve longitudinal/lateral grip clamps;
- [x] self-aligning moment uses utilization-dependent pneumatic trail, nominal `30 mm` front / `20 mm` rear at low utilization;
- [x] explicit overturning/camber moment term;
- [x] all moments go to zero with contact load.

### D4. Transients and contact state

- [x] first-order relaxation states;
- [x] nominal longitudinal relaxation lengths `0.14 / 0.16 m` front/rear;
- [x] nominal lateral relaxation lengths `0.20 / 0.24 m`;
- [x] relaxation uncertainty `+/-50%`;
- [x] low-speed regularization required;
- [x] contact loss sets normal/tangential forces and moments to zero without a special airborne tire mode;
- [x] reacquisition builds forces through the same normal/contact/relaxation equations without a landing grip multiplier;
- [x] estimator validates monotone vertical response and combined-slip utilization <= 1.

Exact public MX33 force-rig data remain unavailable. These values are not labeled as Dunlop measurements; they are a coherent, uncertainty-bounded first-plant hardpack dataset with direct replacement points.

**Gate D: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

---

## 5. Gate E — rider/controller

Gate E does not block the first passive no-assist plant. A fixed/simplified reference rider may be used initially provided it injects no hidden attitude-control torque.

Before gameplay calibration: rider mass/CG, bar/peg coordinates, body motion limits, steering-torque envelope, reduced arm/leg response, virtual-rider separation, and assists-off validation remain required.

**Gate E: ARCHITECTURE READY; GAMEPLAY CALIBRATION DEFERRED.**

---

## 6. Gate N — numerical integration and validation

The implementation must benchmark:

- zero-force airborne linear/angular momentum conservation;
- static sag and axle loads;
- tire vertical compliance;
- full-travel landing;
- straight acceleration and wheelspin;
- braking/lock/recovery;
- steady turn and steering transient;
- combined-slip throttle-on cornering;
- contact loss/reacquisition;
- wheelie onset;
- jump takeoff pitch and ballistic flight;
- timestep-halving convergence;
- deterministic 120 Hz outer-clock repeatability;
- mobile CPU budget;
- no NaN/Inf or unbounded numerical-energy growth.

**Gate N: BENCHMARK-READY; benchmark harness is part of implementation.**

---

## 7. Runtime architecture now authorized

The first Physics V2 implementation is authorized to build:

- one 6-DOF Newton-Euler chassis;
- quaternion attitude;
- physical steering DOF;
- physical fork and swingarm coordinates;
- front/rear wheel angular states;
- Gate-D coherent tire/contact layer;
- Gate-C engine/clutch/gearbox/chain rotational dynamics;
- fixed/simplified reference rider for passive validation;
- assists disabled;
- deterministic force/moment/state telemetry.

Jumps, wheelies, stoppies and landings remain outcomes of the same equations. There are **no special wheelie, jump, landing or airborne-attitude physics modes**.

---

## 8. Verdict

# **OVERALL PHYSICS V2 CODING GATE: OPEN**

Gates A, B, C and D all supply complete first-plant physical parameterizations with explicit uncertainty and centralized replacement points.

The next task is no longer parameter research. It is **5/5: implement the Physics V2 plant, build the deterministic validation harness, pass the machine tests, wire it to the existing controls/rendering, then generate the first user-test build.**
