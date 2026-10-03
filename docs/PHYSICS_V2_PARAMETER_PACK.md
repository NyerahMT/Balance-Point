# Physics V2 Parameter Pack

Status: research gate; **runtime physics must not be rewritten from this document until the applicable subsystem gate is marked READY**.

The purpose of this document is to keep Physics V2 auditable. A number is not allowed into the physical plant merely because it makes the game feel plausible. Every physical parameter must be one of:

- **PUBLISHED** — directly stated by the manufacturer, a measurement source, a standard, or a technical paper.
- **DERIVED** — calculated from published quantities with the derivation recorded.
- **IDENTIFIED** — obtained from a documented experiment, measurement, or system-identification procedure.
- **ASSIST** — deliberately nonphysical game-control authority, kept outside the physical plant and named as an assist.
- **UNKNOWN** — not yet justified. UNKNOWN values are blockers, not invitations to guess.

Reference motorcycle for the first Physics V2 implementation: **late-2025 US Honda CRF450R production-spec configuration**.

Important source-handling note from Research Pass 08: Honda Europe publishes a same-generation 25YM 48.7/51.3 front/rear static load split and 1.483 m wheelbase. Those values form one internally consistent European configuration. They must not be silently mixed with the frozen US geometry when deriving exact runtime CG coordinates.

---

## 1. Model architecture locked by research

The motorcycle plant will use the lowest-order model that preserves the mechanisms Balance Point needs rather than a collection of behavior-specific rules.

### 1.1 Mechanical coordinates

The baseline motorcycle model is an **11-DOF nonlinear multibody system**:

1. chassis translation X
2. chassis translation Y
3. chassis translation Z
4. chassis roll
5. chassis pitch
6. chassis yaw
7. steering rotation about the physical steering axis
8. front suspension displacement along the fork axis
9. rear swingarm rotation
10. front-wheel rotation
11. rear-wheel rotation

The rider is added as a reduced active model with three relative coordinates:

12. rider fore/aft displacement
13. rider vertical displacement
14. rider lateral displacement

Additional dynamic states that are not counted as mechanical DOFs include engine angular speed, clutch state, and tire relaxation/carcass states.

### 1.2 Why this model

Cossalter and Lot's real-time FastBike work demonstrates an eleven-DOF nonlinear motorcycle multibody model containing chassis, steering, suspensions, wheels and tire dynamics and validates it against real slalom/lane-change experiments. The exact FastBike equations are not being copied; the work establishes that this order of model is appropriate for real-time motorcycle simulation without behavior-specific wheelie/jump modes.

Primary reference:
https://www.research.unipd.it/handle/11577/2556482

Off-road work by Vasquez, Lot, Rustighi and Pegoraro demonstrates that motocross requires large suspension motion, wheel detachments and a standing-rider treatment that differs materially from road-motorcycle assumptions.

Primary reference:
https://www.sciencedirect.com/science/article/pii/S0888327020306142

### 1.3 Hard architecture rules

- There is one authoritative 3-D Newton-Euler chassis solve.
- Orientation is integrated with a quaternion or an equivalently singularity-safe representation. Euler angles may be exposed only for presentation/debugging.
- Forces are applied at physical points and generate moments by `r x F`.
- Steering angle is a state, not a commanded angle.
- Player turn input is converted by a virtual rider/controller into steering torque and rider target motion.
- Front and rear suspension are physical coordinates, not visual filters.
- Wheel angular speeds are physical states.
- Tire forces use the instantaneous tire/contact kinematics; lateral and longitudinal forces are not solved by unrelated systems.
- Combined slip is mandatory.
- Jumps, wheelies, stoppies and landings are outcomes of contact state and forces. They do not receive separate physics modes.
- Fully airborne chassis linear and angular momentum may change only from external forces/moments or equal-and-opposite internal exchanges (wheel/rider motion). Any extra control authority is an explicit ASSIST.
- All assists are applied above or alongside the physical plant and are switchable for validation.

---

## 2. Reference-machine parameter register

### 2.1 Chassis and geometry

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Reference model | 2025 Honda CRF450R | Honda | PUBLISHED |
| Runtime reference revision | late-2025 US production spec | Honda Powersports 2025 brochure/current spec | PUBLISHED |
| Nominal curb mass | 249 lb / 112.94 kg | Honda Powersports 2025 production spec | PUBLISHED |
| Wheelbase | 58.3 in / 1.48082 m | Honda Powersports | PUBLISHED |
| Rake | 27.3 deg | Honda Powersports | PUBLISHED |
| Trail | 4.5 in / 114.3 mm | Honda Powersports | PUBLISHED |
| Front travel | 12.2 in / 309.9 mm | Honda Powersports | PUBLISHED |
| Rear travel | 12.2 in / 309.9 mm | Honda Powersports | PUBLISHED |
| Swingarm length | 585.2 mm | Honda Europe 25YM technical release | PUBLISHED |
| Triple-clamp offset | 22 mm | secondary/direct-fit cross-checks | BOUNDED / high-confidence secondary |
| Bike-only CG X | US exact: unresolved | must not silently transport EU load split | UNKNOWN |
| Bike-only CG Z | — | ISO-style identification required | UNKNOWN |
| Inertia tensor | — | ISO-style identification required | UNKNOWN |

Reference source:
https://powersports.honda.com/-/media/products/family/crf450r/brochure/2025/2025-crf450r-brochure.pdf

### 2.1.1 Same-generation OEM longitudinal-CG prior

Honda Europe publishes one internally consistent 25YM configuration:

- wet mass: 113 kg;
- wheelbase: 1.483 m;
- static front/rear load split: 48.7 / 51.3 percent.

From static equilibrium:

`x_CG = front_load_fraction * wheelbase`

therefore:

- `x_CG = 0.487 * 1.483 = 0.722221 m` from the rear contact patch;
- front static load ≈ 539.67 N;
- rear static load ≈ 568.48 N.

Classification: **DERIVED from same-configuration manufacturer data**.

This is a same-generation prior/cross-check, not an exact US-runtime measurement.

Honda Europe source:
https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

### 2.2 Tire geometry

Honda specifies:

- front: Dunlop Geomax MX33 80/100-21, tube type;
- rear: Dunlop Geomax MX33 120/80-19, tube type.

Direct Dunlop front data and later cross-checks put the unloaded front radius in approximately the 0.3526-0.3550 m catalog band. Rear unloaded/effective radius remains IDENTIFY.

Physics V2 uses separate values for:

- geometric/profile radius;
- loaded radius;
- effective rolling radius.

One global wheel radius is prohibited.

### 2.3 Mass-property identification

Exact 2025 CRF450R CG height and principal inertias have not been recovered publicly.

Identification route:

- CG: ISO 9130-style static/tilt measurement;
- whole-bike inertia: ISO 9129-style measurement;
- complete wheel inertia: pendulum/trifilar or known-torque angular acceleration;
- steering assembly inertia: pendulum or component reconstruction;
- swingarm/linkage inertia: pendulum or CAD/scale reconstruction.

Same-class literature values may define safe search bounds only and must not be mislabeled as CRF measurements.

---

## 3. Suspension register

### 3.1 Front fork

Stock 2025 CRF450R:

- Showa 49 mm USD coil fork;
- 5.0 N/mm spring per leg;
- two legs in parallel -> bare fork-axis coil contribution of 10.0 N/mm = 10,000 N/m;
- fork spring free length approximately 500 mm from specialist same-model data;
- revised 2025 oil/valving/seals and deliberately material friction behavior.

The final force law must support:

`F_fork = F_coil(x) + F_air/endstroke(x) + F_comp(v,x) + F_rebound(v,x) + F_friction(v,x)`

Unknown:

- absolute compression force-vs-velocity curve;
- absolute rebound force-vs-velocity curve;
- friction-force magnitude;
- end-stop/air-spring curve.

### 3.2 Rear suspension

Known:

- Pro-Link system;
- stock shock spring 52 N/mm;
- stock rear spring free length approximately 240 mm from same-model specialist data;
- installed/preloaded spring length approximately 232.2 mm from Honda setup data;
- 16 mm shock shaft / 50 mm main piston secondary same-model hardware data;
- stock pullrod center-to-center approximately 147.8 mm from specialist measurement;
- compatible replacement shock envelope approximately 452 mm eye-to-eye / 133 mm stroke;
- manufacturer swingarm length 585.2 mm.

### 3.2.1 2025 swingarm/linkage distinction

Honda Europe explicitly states the 585.2 mm aluminum swingarm is unchanged for 25YM while the Pro-Link structure and leverage ratio are revised.

Therefore:

- the rear axle path may be parameterized as a rigid swingarm arc using the manufacturer-backed 0.5852 m swingarm length once pivot/reference attitude is fixed;
- prior-generation **linkage** hardpoints/motion-ratio curves may not be used as final 2025 data;
- a changed parts-assembly number alone is not evidence that the swingarm kinematic length changed.

Still required:

- local axle-travel/shock-travel motion-ratio curve or complete 2025 linkage hardpoints;
- rear compression/rebound force-vs-velocity curves;
- end-stop law;
- moving/unsprung mass and generalized inertia partition.

---

## 4. Tire/contact model gate

### 4.1 Locked architecture

For each tire the model accepts at least:

- normal/load state;
- longitudinal slip ratio `kappa`;
- lateral slip angle `alpha`;
- camber `gamma`;
- contact-frame velocity;
- wheel angular speed;
- surface state.

It returns at least:

- normal force `Fz`;
- longitudinal force `Fx`;
- lateral force `Fy`;
- aligning/contact moment `Mz` required by the selected formulation.

Required behavior:

- rounded motorcycle contact geometry;
- load sensitivity;
- camber force;
- coherent combined slip;
- transient/relaxation states;
- continuity across zero slip and drive/coast/brake transitions.

### 4.2 Hardpack baseline

V2.0 uses an identified effective hardpack tire model rather than full deformable-soil terramechanics.

The runtime fit may be a constrained spline/table or another nonlinear identified representation. Raw measurements stay separate from the fitted representation.

Exact public MX33 hardpack force/slip data remain unavailable. The required fallback is instrumented-riding system identification using the observation schema in `docs/PHYSICS_V2_CALIBRATION_DATASET_SPEC.md`.

### 4.3 Vertical behavior

Same-size motocross tire research supports load-dependent vertical stiffness. It does not provide exact MX33 absolute stiffness.

Direct loaded-radius/load-deflection measurements at the reference pressure are therefore still required to calibrate `Fz(delta)`.

---

## 5. Drivetrain register

Official 2025 ratios:

- primary: 2.357;
- 1st: 2.133;
- 2nd: 1.705;
- 3rd: 1.421;
- 4th: 1.210;
- 5th: 1.043;
- final: 49/13 = 3.76923.

Derived overall reductions:

- 1st: 18.9486;
- 2nd: 15.1464;
- 3rd: 12.6235;
- 4th: 10.7491;
- 5th: 9.2655.

Measured 2025 rear-wheel dyno anchors:

- 51.1 hp at 9,600 rpm;
- 32.9 lb-ft / 44.61 N m at 6,900 rpm.

Dirt Rider source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

Dirt Rider's 2026 test of the essentially unchanged CRF450R reports 52.6 hp and 33.7 lb-ft. The approximately 2-3 percent peak difference is retained as evidence that the engine calibration should carry realistic measurement/bike/environment uncertainty rather than being overfit to one chart.

Still required:

- dense validated full-throttle curve;
- equivalent engine rotational inertia;
- closed-throttle loss/engine-braking map;
- clutch torque/slip law and clutch inertia;
- drivetrain loss characterization.

The engine state obeys:

`I_engine * omegaDot = combustion_torque - load_torque - loss_torque`.

RPM is not allowed to follow a hand-smoothed target.

---

## 6. Chain drive

Honda specifies a #520 chain and 13/49 sprockets.

For chain pitch 15.875 mm, pitch radius:

`r = p / (2 sin(pi/N))`.

Derived:

- 13T countershaft radius ≈ 0.03317 m;
- 49T rear sprocket radius ≈ 0.12389 m.

Baseline chain model: quasi-static geometric chain tension applied along the real chain line. Anti-squat/pro-squat emerges from countershaft/swingarm/axle/sprocket geometry; it is not an arbitrary multiplier.

Chain elasticity/backlash is deferred until measurable parameters exist.

---

## 7. Rider and control separation

Physical rider layer:

- rider mass/distribution;
- relative body coordinates/limits;
- forces through pegs/bars;
- steering torque into the free steering DOF;
- equal-and-opposite motorcycle reactions.

Virtual-rider/controller layer:

- converts player intent into physical steering torque/body targets;
- may use roll/yaw/steer feedback;
- has bandwidth/rate limits;
- is calibrated only after the no-assist plant validates.

Any direct attitude torque not generated by the physical rider is an **ASSIST**, switchable off and excluded from physical validation.

---

## 8. Numerical integration gate

Outer physics clock remains 120 Hz.

Candidate internal integration families:

1. semi-implicit fixed-step with justified local substeps;
2. linear-implicit treatment of stiff force terms (Rosenbrock/LSRT-style family).

Selection criteria:

- static sag stability;
- full-travel landing stability;
- tire compliance convergence;
- combined-slip transient convergence;
- no-force airborne momentum conservation;
- timestep-halving convergence;
- deterministic mobile CPU cost;
- no NaN/Inf or unbounded numerical-energy growth.

No production integrator is selected by feel.

---

## 9. Validation matrix

The no-assist plant must support deterministic validation of:

- static axle loads/sag;
- front/rear suspension bump response;
- wheel hop/natural response where measurable;
- coast-down;
- clutch launch;
- engine RPM versus wheel speed by gear;
- acceleration speed-time;
- rear-wheel slip build/recovery;
- hard front/rear braking and load transfer;
- rear lock/recovery;
- steady turn equilibrium;
- steering/slalom transient;
- wheelie onset and sustained response;
- jump takeoff pitch rate from lip/contact release;
- ballistic trajectory independence from attitude rotation;
- airborne wheel-acceleration/braking reaction torque;
- rider mass-shift reaction in air;
- landing compression/rebound/bottoming;
- linear/angular momentum conservation while fully airborne with no external torque.

Gameplay/controller tests remain separate.

---

## 10. Gate state

### Gate A — geometry/mass

**NOT READY.**

Closed/narrowed:

- reference model/spec revision;
- mass/wheelbase/rake/trail/travel;
- manufacturer-backed 585.2 mm swingarm length;
- internally consistent European 25YM longitudinal-CG prior (0.722221 m from rear contact).

Still required:

- US-reference CG height;
- principal inertia tensor;
- component inertias and remaining force-application hardpoints.

### Gate B — suspension

**NOT READY.**

Closed/narrowed:

- front/rear spring data;
- free/installed spring distinction;
- shock hardware envelope;
- rigid swingarm arc geometry.

Still required:

- 2025 Pro-Link local motion-ratio curve;
- front/rear damping force laws;
- friction/end-stop calibration.

### Gate C — powertrain

**PARTIAL.**

Closed/narrowed:

- all ratios;
- dyno anchors and coarse curve;
- few-percent same-generation peak-output variation bound;
- clutch architecture/lower-bound capacity;
- engine-inertia identification method.

Still required:

- dense 2025 curve with uncertainty;
- engine equivalent inertia;
- engine-braking/loss map;
- clutch torque/slip law;
- drivetrain loss characterization.

### Gate D — tire/contact

**NOT READY / highest uncertainty.**

Closed/narrowed:

- model architecture;
- exact tire identities/sizes;
- front profile-radius band;
- motocross-specific vertical-stiffness shape prior;
- measurement/identification data contract.

Still required:

- loaded/effective radii;
- vertical stiffness/damping;
- hardpack `Fx/Fy/Mz` surfaces;
- combined slip;
- relaxation/transient calibration.

### Gate E — rider/controller

**Architecture ready, calibration deferred.**

### Gate N — numerical integration

**Benchmark-ready, production method not selected.**

---

## 11. Research work order before runtime coding

1. Execute/obtain the high-sensitivity measurement/identification datasets defined in `PHYSICS_V2_RESEARCH_PASS_07.md` and `PHYSICS_V2_CALIBRATION_DATASET_SPEC.md`.
2. Measure or identify bike CG height and principal inertias.
3. Measure/reconstruct the 2025 Pro-Link wheel-travel/shock-travel curve.
4. Obtain or identify stock Showa compression/rebound/end-stop force laws.
5. Identify MX33 hardpack vertical, longitudinal, lateral/camber, combined-slip and relaxation behavior.
6. Identify equivalent engine inertia, closed-throttle loss/braking and clutch slip/capacity.
7. Validate the dense dyno trace with explicit uncertainty.
8. Re-evaluate `PHYSICS_V2_CODING_GATE.md` mechanically.
9. Only when Gates A-D are defensible, implement the assists-disabled physical plant.
10. Validate against Section 9 before adding gameplay assists.

---

## 12. Source hierarchy

When sources disagree, use this priority unless there is a documented reason not to:

1. Honda factory technical/owner/service/competition data for machine geometry and specifications.
2. Direct instrumented measurements/dyno data for the same model year and configuration.
3. Peer-reviewed motorcycle/off-road dynamics literature for model structure and experimentally measured mechanisms.
4. High-quality measurements from adjacent model years/configurations as bounded references only.
5. MX vs ATV Legends / MX Bikes black-box measurements for game-feel and controller behavior, never as proof of real physical constants.
6. Community/forum values only as leads to find a better source, not as final physical constants.

This hierarchy is intentionally strict. Physics V2 should be slower to start and much faster to trust.
