# Physics V2 Coding Gate

Status: **authoritative pre-runtime checklist through Research Pass 11.**

This document defines the condition under which Balance Point may begin the
assists-disabled Physics V2 runtime implementation. It prevents two failure
modes:

1. starting the rewrite while high-sensitivity physical parameters are guessed;
2. waiting forever for low-sensitivity factory data that do not affect the
   first validation stage.

No runtime Physics V2 code should replace the current plant until this
checklist reaches **OPEN**.

Raw measurement and identification artifacts required by this gate must follow
`docs/PHYSICS_V2_CALIBRATION_DATASET_SPEC.md`.

Research Passes 10 and 11 materially change Gate A:

- Honda Figure 9 gives a reproducible adjacent-generation CRF450R graphical
  CG-height prior near **0.657 m**;
- the graphical longitudinal value is not promoted because it conflicts with
  the stronger same-generation 25YM static-load prior;
- a deterministic `0.60-0.72 m` height sweep changes idealized wheelie and
  rear-lift thresholds by about **18.25%**;
- therefore CG height is explicitly `HIGH_SENSITIVITY_UNRESOLVED`, not
  `BOUNDED_LOW_SENSITIVITY`;
- same-configuration CG-height identification should target roughly
  **+/-0.015 m or better**;
- whole-bike pitch inertia should target **+/-5% preferred**, with wider
  uncertainty accepted only if a later deterministic test proves it harmless.

---

## 1. Allowed parameter states

Every runtime-required physical parameter must be in one of these states:

- `PUBLISHED` — direct manufacturer, standard, or technical source;
- `MEASURED` — direct same-configuration physical measurement;
- `DERIVED` — calculated from accepted published/measured quantities;
- `IDENTIFIED` — estimated by documented system identification with uncertainty;
- `BOUNDED_LOW_SENSITIVITY` — exact value unavailable, but a defensible interval
  exists and deterministic sensitivity testing proves the uncertainty does not
  materially change the applicable validation result.

The following do **not** open a gate:

- plausible;
- typical;
- feels right;
- copied from another bike without uncertainty classification;
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
- [x] conflicting early Honda launch specification retained separately rather
      than averaged.

---

## 3. Gate A — rigid-body geometry and mass properties

### A1. Required geometry

- [x] wheelbase;
- [x] rake;
- [x] trail;
- [x] front/rear nominal travel;
- [x] manufacturer-backed swingarm length: **585.2 mm**;
- [x] front triple-clamp offset high-confidence secondary bound;
- [ ] exact chassis-reference coordinates for the steering axis, swingarm
      pivot, front axle/fork relation, and other plant force-application
      hardpoints.

Footpeg/handlebar geometry may remain in Gate E while the first passive plant
uses a fixed reference rider; it becomes mandatory before active rider forces
are validated.

### A2. Required bike mass properties

- [x] total nominal wet mass;
- [x] same-generation longitudinal static-load/CG prior;
- [x] internally consistent European 25YM longitudinal derivation retained as
      a prior: `x_CG = 0.722221 m` from rear contact for the 113 kg / 1.483 m /
      48.7:51.3 configuration;
- [x] adjacent-generation Honda graphical CG-height prior extracted and
      reproducible: `z_CG ~= 0.657 m`, research use only;
- [x] broad CG-height envelope sensitivity tested;
- [ ] same-configuration bike-only CG height at approximately +/-15 mm or
      better uncertainty;
- [ ] pitch inertia or full inertia tensor about bike CG, with pitch inertia
      preferably within +/-5%;
- [ ] roll inertia or defensible low-sensitivity interval;
- [ ] yaw inertia or defensible low-sensitivity interval.

The Pass-11 sweep is decisive: the `0.60-0.72 m` height envelope changes the
idealized wheelie threshold, rear-lift threshold, and longitudinal pitch moment
arm by roughly 18%. CG height therefore remains a high-sensitivity blocker.

### A3. Required component inertias

- [ ] front complete wheel rotational inertia;
- [ ] rear complete wheel rotational inertia;
- [ ] steering/fork assembly inertia about steering axis;
- [ ] swingarm/linkage generalized inertia.

### A4. Gate-A acceptance rule

Gate A is READY when:

- same-configuration CG height and pitch inertia are `MEASURED` or `IDENTIFIED`
  to the required uncertainty, or a later deterministic validation establishes
  a tighter defensible low-sensitivity bound;
- roll/yaw and component inertias are identified or proven
  `BOUNDED_LOW_SENSITIVITY` for the first validation suite;
- every plant force-application hardpoint has a coordinate and provenance.

**Current status: NOT READY — analysis complete to the public-data ceiling;
high-sensitivity mass properties require same-configuration identification.**

---

## 4. Gate B — suspension kinematics and force laws

### B1. Front suspension

- [x] fork type;
- [x] fork travel;
- [x] stock spring rate per leg;
- [x] fork spring free-length secondary measurement;
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
- [x] rear spring free-length secondary measurement;
- [x] shock shaft/main-piston envelope;
- [x] 2025-specific linkage identity and pullrod measurement;
- [x] rigid swingarm arc constrained by the 585.2 mm swingarm length;
- [x] full-stroke average wheel/shock displacement ratio constrained to about
      2.330 as a compatibility bound, not a local constant ratio;
- [ ] local axle-travel-to-shock-travel motion-ratio curve;
- [ ] compression force-vs-shock-velocity law;
- [ ] rebound force-vs-shock-velocity law;
- [ ] bottoming/bump-stop law;
- [ ] swingarm/linkage mass/inertia partition.

### B3. Gate-B acceptance rule

Gate B is READY when:

- the 2025 Pro-Link local motion ratio is `MEASURED`, `DERIVED`, or `IDENTIFIED`
  across usable stroke;
- front/rear damping curves are identified well enough that sag, bump, whoop,
  and landing tests are not dominated by damping uncertainty;
- bottoming/end-stop behavior is physically bounded and continuous;
- no wheel-rate or anti-squat multiplier substitutes for geometry.

**Current status: NOT READY.**

---

## 5. Gate C — engine, clutch and drivetrain

### C1. Already closed/narrowed

- [x] primary reduction;
- [x] five gear ratios;
- [x] final reduction;
- [x] idle RPM;
- [x] peak dyno power;
- [x] peak dyno torque;
- [x] coarse full dyno trace for research;
- [x] same-generation dyno repeatability/configuration bound recorded;
- [x] clutch architecture and plate/spring count;
- [x] hard lower bound on fully engaged clutch torque capacity;
- [x] engine-inertia differential identification method defined.

### C2. Still required

- [ ] dense validated full-throttle torque curve;
- [ ] equivalent engine rotational inertia;
- [ ] closed-throttle engine-braking/loss torque map;
- [ ] clutch transmitted-torque versus slip/command model;
- [ ] clutch rotating inertia or justified equivalent;
- [ ] drivetrain loss characterization sufficient for acceleration validation.

### C3. Gate-C acceptance rule

Gate C is READY when:

- engine RPM is generated by rotational dynamics rather than a target-RPM
  controller;
- launch/shift behavior can be predicted from identified clutch/engine states;
- torque/loss data reproduce RPM-vs-wheel-speed and straight-line acceleration
  without hidden longitudinal-force scaling.

**Current status: PARTIAL / NOT READY.**

---

## 6. Gate D — tire/contact physics

### D1. Geometry and vertical behavior

- [x] exact front/rear tire identities and nominal sizes;
- [x] separate front/rear tire architecture required;
- [x] front MX33 unloaded geometry catalog band;
- [x] rear MX33 tire mass band from retailer measurements;
- [x] motocross-specific load-dependent vertical-stiffness shape prior;
- [ ] actual front loaded/effective rolling radius at reference pressure;
- [ ] actual rear loaded/effective rolling radius at reference pressure;
- [ ] front vertical `Fz(delta)` curve;
- [ ] rear vertical `Fz(delta)` curve;
- [ ] radial damping/transient behavior.

### D2. Longitudinal/lateral force surface

- [ ] rear `Fx(kappa,Fz)` hardpack surface;
- [ ] front braking `Fx(kappa,Fz)` hardpack surface;
- [ ] front `Fy(alpha,gamma,Fz)` hardpack surface;
- [ ] rear `Fy(alpha,gamma,Fz)` hardpack surface;
- [ ] aligning/twisting moment treatment;
- [ ] coherent combined-slip calibration;
- [ ] relaxation/transient scale(s).

### D3. Gate-D acceptance rule

One coherent tire/contact layer must pass, without independent grip hacks:

- straight acceleration/wheelspin;
- braking and lock/recovery;
- steady-radius cornering;
- steering transient/slalom;
- throttle-on combined slip;
- camber-sensitive lateral response;
- contact-loss/reacquisition continuity.

A constant `mu*Fz` model plus an unrelated lateral clamp is insufficient.

**Current status: NOT READY / largest blocker.**

---

## 7. Gate E — rider and controller

This gate does not block creation of the passive no-assist plant if the rider is
initially represented by a fixed/simplified reference mass and that
simplification injects no attitude-control torque.

Before gameplay controller tuning:

- [ ] rider neutral CG and mass configuration;
- [ ] footpeg and handlebar attachment coordinates;
- [ ] fore/aft, vertical and lateral motion limits;
- [ ] effective arm/leg force response or documented reduced model;
- [ ] steering-torque envelope;
- [ ] virtual-rider controller separated from physical plant;
- [ ] all assists switchable off.

**Current status: architecture ready; gameplay calibration deferred.**

---

## 8. Gate N — numerical integration

Candidate families:

- semi-implicit fixed-step stepping with justified local substeps;
- linear-implicit stiff-force treatment, Rosenbrock/LSRT-style family.

Required benchmarks:

- [ ] zero-force airborne momentum conservation;
- [ ] static sag;
- [ ] tire vertical compliance;
- [ ] full-travel landing;
- [ ] combined-slip transient;
- [ ] timestep-halving convergence;
- [ ] deterministic 120 Hz outer-clock repeatability;
- [ ] mobile CPU budget;
- [ ] no NaN/Inf or unbounded numerical energy growth.

**Current status: BENCHMARK-READY.**

---

## 9. Minimum condition to say “it is time to code”

The runtime coding gate becomes **OPEN** only when all of the following are true:

1. Gate A has a defensible initial CG/inertia set with high-sensitivity
   uncertainty controlled.
2. Gate B has a defensible 2025 Pro-Link motion-ratio curve and front/rear
   force-law calibration sufficient for first suspension tests.
3. Gate C has an initial identified engine inertia, loss/braking model, and
   clutch slip law.
4. Gate D has a defensible initial hardpack tire dataset covering vertical,
   longitudinal, lateral/camber, combined-slip, and transient behavior.
5. Every runtime physical constant has provenance and units.
6. Any remaining uncertain constant is explicitly `BOUNDED_LOW_SENSITIVITY`.
7. The implementation plan contains no special wheelie, jump, landing, or
   airborne-attitude physics modes.

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

Only after that plant passes validation may virtual-rider/gameplay assists be
calibrated.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

Gate A is no longer blocked by an unanswered research question. Passes 10-11
show that the public-data CG-height interval is too influential to accept and
define the accuracy required to close it. The remaining Gate-A mass-property
work is a finite same-configuration measurement/identification campaign.

The overall gate still requires:

- same-configuration CRF CG height and defensible inertias;
- 2025 Pro-Link local motion ratio and stock Showa force laws;
- MX33 hardpack force/transient data;
- engine inertia/braking and clutch slip law.
