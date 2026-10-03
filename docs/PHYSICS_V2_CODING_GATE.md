# Physics V2 Coding Gate

Status: **authoritative pre-runtime checklist.**

This document defines the exact condition under which Balance Point may begin the assists-disabled Physics V2 runtime implementation. It exists to prevent two failure modes:

1. starting the rewrite while high-sensitivity physical parameters are still being guessed;
2. delaying forever while waiting for low-sensitivity factory data that do not materially affect the first validation stage.

No runtime Physics V2 code should replace the current plant until this checklist reaches **OPEN**.

---

## 1. Allowed parameter states

Every runtime-required physical parameter must be in one of these states:

- `PUBLISHED` — direct manufacturer/standard/technical source;
- `MEASURED` — direct same-configuration physical measurement;
- `DERIVED` — calculated from accepted published/measured quantities;
- `IDENTIFIED` — estimated by a documented system-identification experiment with uncertainty;
- `BOUNDED_LOW_SENSITIVITY` — exact value unavailable, but a defensible interval exists and deterministic sensitivity testing proves the uncertainty does not materially alter the applicable validation result.

The following do **not** open a gate:

- plausible;
- typical;
- feels right;
- copied from a different bike without an uncertainty classification;
- tuned until gameplay looks correct;
- hidden assist authority.

---

## 2. Reference configuration gate

Reference: **late-2025 US Honda CRF450R production-spec configuration**.

Required before coding starts:

- [x] model/year frozen;
- [x] nominal mass source frozen;
- [x] wheelbase frozen;
- [x] rake/trail frozen;
- [x] front/rear travel frozen;
- [x] front/rear tire identity frozen;
- [x] drivetrain ratios frozen;
- [x] conflicting early Honda launch specification retained separately rather than averaged.

Current reference nominal values are documented in Research Pass 06.

---

## 3. Gate A — rigid-body geometry and mass properties

### A1. Required geometry

- [x] wheelbase;
- [x] rake;
- [x] trail;
- [x] front/rear nominal travel;
- [x] swingarm length manufacturer prior;
- [x] front triple-clamp offset high-confidence secondary bound;
- [ ] exact chassis-reference coordinates for steering axis, swingarm pivot, front axle/fork relation, footpegs and handlebar sufficient for force application;

### A2. Required bike mass properties

- [x] total nominal wet mass;
- [x] same-generation longitudinal static-load/CG prior;
- [ ] bike-only CG height;
- [ ] roll inertia or full inertia tensor about bike CG;
- [ ] pitch inertia or full inertia tensor about bike CG;
- [ ] yaw inertia or full inertia tensor about bike CG;

### A3. Required component inertias

- [ ] front complete wheel rotational inertia;
- [ ] rear complete wheel rotational inertia;
- [ ] steering/fork assembly inertia about steering axis;
- [ ] swingarm/linkage generalized inertia;

### A4. Gate-A acceptance rule

Gate A is READY when:

- CG height and pitch inertia are `MEASURED`, `IDENTIFIED`, or tightly defensible enough that wheelie/jump/load-transfer predictions are not dominated by their uncertainty;
- roll/yaw inertia and steering/swingarm inertias are either identified or proven `BOUNDED_LOW_SENSITIVITY` for the first validation suite;
- all force-application hardpoints required by the plant have a defined coordinate and provenance.

**Current status: NOT READY.**

---

## 4. Gate B — suspension kinematics and force laws

### B1. Front suspension

- [x] fork type;
- [x] fork travel;
- [x] stock spring rate per leg;
- [x] bare parallel coil rate derived;
- [x] 2025 friction/damping redesign confirmed;
- [ ] absolute compression force-vs-velocity law;
- [ ] absolute rebound force-vs-velocity law;
- [ ] friction-force model/magnitude;
- [ ] end-stroke/air-spring/bottoming law;
- [ ] moving/unsprung mass partition.

### B2. Rear suspension

- [x] stock shock spring rate;
- [x] free/installed spring-length distinction documented;
- [x] shock shaft/main-piston envelope;
- [x] 2025-specific linkage identity and pullrod measurement;
- [ ] local axle-travel-to-shock-travel motion-ratio curve;
- [ ] compression force-vs-shock-velocity law;
- [ ] rebound force-vs-shock-velocity law;
- [ ] bottoming/bump-stop law;
- [ ] swingarm/linkage mass/inertia partition.

### B3. Gate-B acceptance rule

Gate B is READY when:

- the Pro-Link local motion ratio is `MEASURED`, `DERIVED`, or `IDENTIFIED` across the usable stroke;
- front/rear damping curves are identified well enough that sag, single-bump, whoop and landing tests are not dominated by damping uncertainty;
- bottoming/end-stop behavior is physically bounded and continuous;
- no wheel-rate or anti-squat multiplier substitutes for actual geometry.

**Current status: NOT READY.**

---

## 5. Gate C — engine, clutch and drivetrain

### C1. Already closed

- [x] primary reduction;
- [x] five gear ratios;
- [x] final reduction;
- [x] idle RPM;
- [x] peak dyno power;
- [x] peak dyno torque;
- [x] coarse full dyno trace for research;
- [x] clutch architecture and plate/spring count;
- [x] hard lower bound on fully engaged clutch torque capacity;
- [x] engine-inertia differential identification method defined.

### C2. Still required

- [ ] pixel/dense validated full-throttle torque curve;
- [ ] equivalent engine rotational inertia;
- [ ] closed-throttle engine-braking/loss torque map;
- [ ] clutch transmitted-torque versus slip/command model;
- [ ] clutch rotating inertia or justified equivalent;
- [ ] drivetrain efficiency/loss characterization sufficient for acceleration validation.

### C3. Gate-C acceptance rule

Gate C is READY when:

- engine RPM is generated by rotational dynamics rather than a target-RPM controller;
- launch/shift behavior can be predicted from identified clutch/engine states;
- measured/identified torque and loss data are sufficient to reproduce RPM-vs-wheel-speed and straight-line acceleration without hidden longitudinal-force scaling.

**Current status: PARTIAL / NOT READY.**

---

## 6. Gate D — tire/contact physics

### D1. Geometry and vertical behavior

- [x] exact front/rear tire identities and nominal sizes;
- [x] separate front/rear tire architecture required;
- [x] front MX33 unloaded geometry catalog band;
- [x] exact rear MX33 tire mass band from retailer measurements;
- [x] motocross-specific load-dependent vertical-stiffness shape prior;
- [ ] actual front loaded/effective rolling radius at reference pressure;
- [ ] actual rear loaded/effective rolling radius at reference pressure;
- [ ] front vertical `Fz(delta)` curve;
- [ ] rear vertical `Fz(delta)` curve;
- [ ] radial damping/transient behavior.

### D2. Longitudinal/lateral force surface

- [ ] rear `Fx(kappa,Fz)` hardpack surface;
- [ ] front braking `Fx(kappa,Fz)` hardpack surface sufficient for braking validation;
- [ ] front `Fy(alpha,gamma,Fz)` hardpack surface;
- [ ] rear `Fy(alpha,gamma,Fz)` hardpack surface;
- [ ] aligning/twisting moment treatment required by chosen formulation;
- [ ] coherent combined-slip calibration;
- [ ] relaxation/transient scale(s).

### D3. Gate-D acceptance rule

Gate D is READY when a single coherent tire/contact layer can pass all of the following without independent grip hacks:

- straight acceleration/wheelspin;
- braking and lock/recovery;
- steady-radius cornering;
- steering transient/slalom;
- throttle-on combined slip;
- camber-sensitive lateral response;
- contact-loss/reacquisition continuity.

No constant `mu*Fz` model plus unrelated lateral clamp can satisfy Gate D.

**Current status: NOT READY / largest blocker.**

---

## 7. Gate E — rider and controller

This gate does **not** block creation of the passive/no-assist physical plant if the rider is initially represented as a fixed or simplified reference mass for validation, provided that simplification is explicit and does not inject attitude-control torque.

Before gameplay controller tuning:

- [ ] rider neutral CG and mass configuration;
- [ ] footpeg and handlebar attachment coordinates;
- [ ] fore/aft, vertical and lateral motion limits;
- [ ] effective arm/leg force response or a documented reduced model;
- [ ] steering-torque envelope;
- [ ] virtual-rider controller separated from physical plant;
- [ ] all assists switchable off.

**Current status: architecture ready; gameplay calibration deferred.**

---

## 8. Gate N — numerical integration

Literature survey is complete enough to benchmark two families:

- semi-implicit fixed-step stepping with justified local substeps;
- linear-implicit stiff-force treatment, Rosenbrock/LSRT-style family.

Required benchmark cases:

- [ ] zero-force airborne momentum conservation;
- [ ] static sag;
- [ ] tire vertical compliance;
- [ ] full-travel landing;
- [ ] combined-slip transient;
- [ ] timestep-halving convergence;
- [ ] deterministic 120 Hz outer-clock repeatability;
- [ ] mobile CPU budget;
- [ ] no NaN/Inf or unbounded numerical energy growth.

Gate N does not require a production solver choice before the physical equations are written, but the benchmark harness must exist before claiming the plant numerically validated.

**Current status: BENCHMARK-READY.**

---

## 9. Minimum condition to say “it is time to code”

The runtime coding gate becomes **OPEN** only when all of the following are true:

1. Gate A has a defensible initial CG/inertia set with high-sensitivity uncertainty controlled.
2. Gate B has a defensible 2025 Pro-Link motion-ratio curve and front/rear force-law calibration sufficient for the first suspension tests.
3. Gate C has an initial identified engine inertia, loss/braking model and clutch slip law.
4. Gate D has a defensible initial hardpack tire dataset covering vertical, longitudinal, lateral/camber, combined-slip and transient behavior.
5. Every runtime physical constant has provenance and units.
6. Any remaining uncertain physical constant is explicitly `BOUNDED_LOW_SENSITIVITY`, not merely guessed.
7. The implementation plan contains **no special wheelie, jump, landing or airborne-attitude physics modes**.

At that moment the first implementation target is:

- one 6-DOF rigid-body chassis;
- quaternion attitude;
- physical steering DOF;
- physical fork and swingarm coordinates;
- front/rear wheel angular states;
- coherent tire/contact forces;
- engine/clutch/chain rotational dynamics;
- assists disabled;
- deterministic force/moment telemetry for validation.

Only after that plant passes validation may the virtual rider and gameplay assists be tuned.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

The remaining blockers are not architectural uncertainty. They are specific high-sensitivity calibration datasets:

- CRF CG/inertia;
- 2025 Pro-Link motion ratio;
- stock Showa force laws;
- MX33 hardpack tire force/transient data;
- engine inertia/braking and clutch slip law.

This checklist is the definition used to decide when the project transitions from research into the Physics V2 runtime rewrite.
