# Physics V2 Research Pass 01

Status: **research only — no runtime physics implementation authorized by this document.**

This pass narrows the model choices, locks a single reference configuration, records parameter derivations that are defensible now, and converts the remaining unknowns into explicit identification tasks. It supplements `PHYSICS_V2_PARAMETER_PACK.md` and Issue #29.

## 1. Reference configuration lock

The first Physics V2 plant will represent a **stock 2025 US Honda CRF450R**, not a generic 450 and not an average of Honda US, Honda Europe, CRF450RWE, or CRF450RX specifications.

The following source differences are retained rather than averaged:

- Honda US launch specification: 245 lb curb weight, 58.3 in wheelbase, 27.1 deg rake, 114 mm trail, 12.2 in front travel and 12.4 in rear travel.
- Dirt Rider's stock 2025 CRF450R test bike: measured 246 lb wet, while retaining the US geometry above.
- Other Honda regional/current materials report slightly different mass, trail/rake and travel figures. Those are useful cross-checks but are not silently mixed into the US reference configuration.

**Reference operating bike mass for validation:** 246 lb = **111.584 kg**, because it is a measured wet stock test-bike value. Honda's 245 lb claimed curb mass remains recorded separately as the manufacturer specification.

Sources:

- Honda US 2025 specification: https://hondanews.com/en-US/powersports/releases/release-70527d767f112a34ec2e42bfb506153d-2025-honda-crf450r-specifications
- Dirt Rider 2025 measured test: https://www.dirtrider.com/tests/honda-crf450r-review/

## 2. Mechanical model selection

The plant remains a nonlinear 11-DOF motorcycle with a reduced active 3-DOF rider.

### 2.1 Motorcycle coordinates

1. chassis world X translation
2. chassis world Y translation
3. chassis world Z translation
4. chassis orientation roll component
5. chassis orientation pitch component
6. chassis orientation yaw component
7. steering rotation about the physical steering axis
8. front fork displacement along the fork axis
9. rear swingarm rotation
10. front-wheel rotation
11. rear-wheel rotation

Internally the chassis orientation must use a singularity-safe representation such as a unit quaternion; roll/pitch/yaw are outputs for UI/debugging, not the integrated orientation state.

### 2.2 Rider coordinates

12. rider fore/aft displacement relative to motorcycle
13. rider vertical displacement relative to motorcycle
14. rider lateral displacement relative to motorcycle

The rider is not a fixed CG offset. Forces are transmitted through explicit footpeg and handlebar locations and must create equal-and-opposite reactions on the motorcycle.

### 2.3 Why this order is justified

Cossalter and Lot demonstrated an 11-DOF nonlinear multibody motorcycle containing chassis, steering, front/rear suspension, wheels and a dynamic tire treatment; their FastBike implementation was validated against real slalom and lane-change measurements and was real-time capable. Their natural-coordinate implementation is not being copied literally, but it establishes that this model order retains the important motorcycle mechanisms without introducing behavior-specific wheelie/jump modes.

Reference:
https://www.research.unipd.it/handle/11577/2473624

Vasquez, Lot, Rustighi and Pegoraro show why the off-road extension matters: motocross has large suspension motion, frequent wheel detachment and a predominantly standing rider, and they validated inverse-dynamics force estimates against instrumented motocross riding including whoops and braking.

Reference:
https://doi.org/10.1016/j.ymssp.2020.107228

## 3. Constraint and numerical-solver scope

This needs its own gate; the integrator is not a tuning detail.

FastBike formulated its natural-coordinate system as an index-3 DAE, used Baumgarte stabilization to reduce constraint problems, and used the implicit DASSL family for numerical integration. The paper explicitly notes conversion toward ODE form to increase integration speed. Modern real-time multibody work likewise uses linear-implicit methods when stiff suspension/compliance terms make a purely explicit large-step solve fragile.

References:

- Cossalter/Lot model: https://doi.org/10.1076/vesd.37.6.423.3523
- Real-time stiff suspension integration example: https://doi.org/10.1007/s11044-024-09984-2

### Numerical implementation rule

Physics V2 will use **minimal physical coordinates wherever geometry permits** so that fork, swingarm, steering and wheel joints are satisfied kinematically rather than recreated as a large generic constraint system. This avoids importing the 51-coordinate FastBike formulation merely because the paper used it.

The game keeps its deterministic **120 Hz outer physics clock**, but the final internal integration method is **NOT READY**. Before runtime migration, a numerical-method validation must compare candidate schemes on the stiffest expected tire/suspension/contact state and verify:

- bounded total-energy drift in unforced airborne motion;
- exact/near-exact linear and angular momentum conservation in the absence of external forces;
- stable static sag without numerical chatter;
- stable full-travel landing/contact response;
- convergence when the timestep is halved;
- no material change in tire slip-force response under timestep refinement;
- mobile execution cost compatible with the existing fixed-step budget.

Candidate methods are not physical parameters and must not be chosen by feel. A linearly implicit or implicit treatment is preferred if the measured stiffnesses make 120 Hz explicit integration non-convergent. Local substepping is acceptable only if convergence tests justify it.

## 4. Tire model selection

### 4.1 What is locked

The tire layer must support, for each wheel:

Inputs:

- normal deflection/load state
- longitudinal slip ratio `kappa`
- lateral slip angle `alpha`
- camber `gamma`
- normal spin/contact kinematics where material
- wheel angular speed
- surface state
- contact-frame velocity

Outputs at the actual contact point:

- normal force `Fz`
- longitudinal force `Fx`
- lateral force `Fy`
- aligning/yaw moment `Mz`

The model must include:

- rounded motorcycle-tire/contact geometry rather than a car-like flat disk contact assumption;
- load sensitivity;
- camber contribution;
- combined longitudinal/lateral saturation;
- state-dependent transient/relaxation behavior;
- continuity through zero slip and through drive/coast/brake transitions.

### 4.2 Baseline mathematical family

For the first off-road implementation, use a **physical brush/enhanced-string-inspired point-contact backbone with identified steady-state force curves and first-order relaxation states** rather than importing an unvalidated road-racing Magic Formula coefficient set.

Meijaard and Popov's enhanced-string model is useful because its physical inputs are normal deflection, camber, longitudinal/lateral slip and normal spin; outputs are normal/lateral/longitudinal forces and aligning moment, and its relaxation lengths depend on operating state. This matches the state/interface we need while keeping parameters interpretable enough for identification.

Reference:
https://doi.org/10.1080/00423110500140682

Cossalter/Lot's tire work additionally establishes the importance of the actual moving contact point, carcass deformation and transient tire behavior in a motorcycle multibody model.

Reference:
https://doi.org/10.1023/B%3AMECC.0000022842.12077.5C

### 4.3 What is NOT locked

The numerical coefficients and even the exact steady-state curve representation remain blocked until off-road data are identified. It may ultimately be a constrained spline/table rather than a closed-form Magic Formula. A mathematically sophisticated equation with road-bike coefficients is not more defensible than a measured table.

Full deformable-soil terramechanics is explicitly deferred from V2.0. Initial surface classes modify identified tire/shear-capacity parameters while the existing heightfield remains the geometric terrain. Bekker/Janosi-Hanamoto-style sinkage/shear and persistent rut deformation belong to a later terramechanics pass once the motorcycle itself validates.

## 5. Tire geometry: knowns and bounds

Reference tires from Honda US:

- front: Dunlop Geomax MX33 80/100-21, tube
- rear: Dunlop Geomax MX33 120/80-19, tube

Nominal size-code radii, useful only as geometry sanity checks:

- front: `(21 in rim + 2 x 80 mm sidewall) / 2` = **0.3467 m nominal radius**
- rear: `(19 in rim + 2 x 96 mm sidewall) / 2` = **0.3373 m nominal radius**

These are not loaded rolling radii and are not MX33 carcass measurements. Actual unloaded profile and loaded effective rolling radius at the stock 15 psi must be identified.

A structural decision follows immediately: **Physics V2 must have separate front and rear wheel/tire radii.** The existing single-radius architecture is not retained.

Identification required:

1. unloaded circumference/profile at 15 psi;
2. static loaded radius vs vertical load;
3. vertical force-deflection curve at 15 psi;
4. radial damping or equivalent transient response;
5. force/slip data by surface.

## 6. Suspension model selection

### 6.1 Front

Use a fork-axis translational DOF with:

- exact steering/fork geometry;
- two physical fork springs;
- preload/initial extension;
- separate nonlinear compression and rebound damping curves;
- seal/Coulomb friction if identified as material;
- progressive hydraulic/bump end-stop represented by an identified end-of-travel force law.

For the stock CRF450R, the 2025 optional-parts information identifies **5.0 N/mm as the standard spring rate per fork spring**, with 4.8 and 5.2 N/mm alternatives. With two equal fork springs acting in parallel, the bare coil contribution along the fork axis is therefore **10.0 N/mm = 10,000 N/m** before considering friction, oil-level/end-stroke effects or projection into the chassis/ground frame.

Honda manual transcription/reference:
https://www.notice-facile.com/en/manual/1411421/honda%2Bcrf450rwe-2025

### 6.2 Rear

Use a swingarm rotational DOF and **actual Pro-Link kinematics**. The shock spring/damper force acts through the linkage; wheel rate is an output of the linkage motion ratio, not a separately invented nonlinear spring.

The stock 2025 CRF450R rear spring is **52 N/mm**, and the standard installed spring length is **232.2 mm** according to the 2025 manual data.

Manual reference:
https://www.manuals.co.uk/honda/crf450r-2025/manual?p=115

The 2025 linkage is revised. Older linkage hardpoints/motion ratios are prohibited unless used only as uncertainty bounds. Exact 2025 pivot coordinates or an axle-travel-to-shock-travel curve are still blockers.

### 6.3 Damper representation

Do not simulate shim stacks, oil compressibility and gas dynamics unless data justify that complexity. Do not collapse the damper to one arbitrary `c*v` coefficient either.

The V2 baseline is a **piecewise or monotone-spline force-vs-shaft-velocity law**, separately identified for compression and rebound, with optional position/end-stop and Coulomb-friction contributions. Motorcycle-suspension literature supports nonlinear spring/damper identification from test-ride data when bench data are unavailable.

Reference:
https://doi.org/10.1002/pamm.201800005

Preferred source order:

1. damper dyno curves for the stock 2025 components;
2. identified curves from instrumented suspension displacement/velocity plus chassis/wheel kinematics;
3. adjacent-bike data as bounds only, never mislabeled as CRF values.

## 7. Chain drive and anti-squat

Honda specifies a #520 chain and 13/49 sprockets. For a standard 520 chain pitch of 15.875 mm, sprocket pitch radius is

`r = p / (2 sin(pi / N))`.

Derived pitch radii:

- 13T countershaft sprocket: **0.03317 m**
- 49T rear sprocket: **0.12389 m**

These radii are usable once the countershaft, swingarm-pivot and rear-axle coordinates are known.

The baseline V2 chain model is **quasi-static geometric chain tension**: delivered rear-wheel torque determines chain tension, which is applied along the actual chain line to the rear assembly/swingarm. This captures anti-squat/pro-squat geometry without inventing a multiplier.

Detailed chain elasticity/backlash is deferred. Sharp, Evangelou and Limebeer's multibody work shows chain geometry materially changes anti-squat response; their work also demonstrates that adding chain compliance introduces an additional oscillatory mode and requires compliance/damping parameters that we do not currently possess.

Reference:
https://pure.uj.ac.za/en/publications/multibody-aspects-of-motorcycle-modelling-with-special-reference-/

## 8. Powertrain: closed and open data

Official 2025 CRF450R drivetrain ratios:

- primary reduction: **2.357**
- gear 1: **2.133**
- gear 2: **1.705**
- gear 3: **1.421**
- gear 4: **1.210**
- gear 5: **1.043**
- final drive: **3.769 = 49/13**

Derived overall reductions:

- 1st: **18.9486**
- 2nd: **15.1464**
- 3rd: **12.6235**
- 4th: **10.7491**
- 5th: **9.2655**

The existing representative drivetrain ratios are therefore not retained in V2.

Honda manual/specification references:

- https://cdn.powersports.honda.com/documentum/MWOM/ml.remawmom.amker2525omen.pdf
- https://hondanews.com/en-US/powersports/releases/release-70527d767f112a34ec2e42bfb506153d-2025-honda-crf450r-specifications

Measured rear-wheel dyno anchors from Dirt Rider:

- **51.1 hp at 9,600 rpm**
- **32.9 lb-ft = 44.61 N m at 6,900 rpm**

Source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

Still blocked:

- digitized full throttle torque curve;
- engine equivalent rotational inertia;
- clutch torque capacity versus slip/command/temperature state sufficient for launch behavior;
- engine-braking/loss torque map;
- drivetrain loss characterization.

The engine state must obey `I_engine * omegaDot = combustion torque - load torque - loss torque`. RPM is not allowed to follow a hand-smoothed target.

## 9. Mass properties: measurement instead of guessing

We do not have defensible CRF450R CG height or inertia tensor data.

Therefore the model defines an identification route rather than filling the fields with adjacent-bike numbers:

- **ISO 9130:2005** defines measurement of motorcycle and motorcycle+rider center-of-gravity location.
- **ISO 9129:2008** defines measurement of motorcycle and motorcycle/rider moments of inertia.

References:

- https://www.iso.org/standard/33494.html
- https://www.iso.org/standard/42939.html

Published motorcycle/motocross model inertias may be used to choose safe parameter-search bounds or sanity-check an identified result, but they remain priors, not CRF450R measurements.

### Component inertia identification

If exact component data cannot be sourced:

- wheel rotational inertia: identify with a physical/bifilar pendulum or known-torque angular-acceleration test of the complete wheel/tire assembly;
- steering assembly inertia: pendulum/bifilar measurement of the assembled steering/fork/wheel grouping or a mass-property reconstruction from measured components;
- swingarm/linkage inertia: pendulum measurement or CAD/scale reconstruction with uncertainty recorded.

Every identified value must retain the test configuration, uncertainty and raw measurement references.

## 10. Rider and control separation

The standing rider remains a reduced three-coordinate active mass because off-road research shows the standing rider's arms/legs isolate and actively couple the body to the motorcycle.

Physical rider layer:

- mass and mass distribution;
- body coordinate states and limits;
- compliant/active forces through pegs and bars;
- steering torque applied to the free steering DOF;
- equal-and-opposite reaction forces/moments on the motorcycle.

Virtual-rider/controller layer:

- converts player intent into physically applied steering torque and body targets;
- may use feedback from roll, roll rate, yaw rate and steering state;
- has bandwidth/rate limits;
- is calibrated only after the no-assist physical plant validates.

Any direct torque/attitude authority that does not arise from the physical rider is labeled **ASSIST**, switchable off, and excluded from physical validation.

## 11. Parameter-identification experiments

These are part of the implementation scope, not optional polish.

### A. Geometry / mass

- wheelbase, rake, trail, travel: manufacturer/reference configuration;
- CG location: ISO 9130-style static measurement;
- inertia tensor: ISO 9129-style pendulum measurement or equivalent validated method;
- linkage/countershaft/peg/bar hardpoints: direct measurement, service/CAD data, or calibrated photogrammetry with an uncertainty budget.

### B. Suspension

- static/free/race sag for consistency checks;
- axle travel versus shock travel sampled across full rear travel to recover `d(shock)/d(axle)` motion ratio;
- fork and shock shaft-velocity force curves from dyno if possible;
- otherwise infer constrained spline curves from suspension-potentiometer + IMU + wheel/contact kinematics, then validate on a second dataset;
- controlled drop/bump test for end-stop and rebound validation.

### C. Tire/contact

At stock 15 psi:

- static vertical load-deflection bench test;
- wheel circumference/profile measurement;
- straight-line acceleration/braking test with front/rear wheel speed and IMU to identify longitudinal slip-force behavior;
- constant-radius/slalom/inverse-dynamics data to identify lateral/camber terms;
- combined throttle/brake + cornering tests for combined-slip reduction;
- transient steering/slip tests to identify relaxation state/length.

Each surface class requires its own identified scaling/curve set; hardpack is the first baseline.

### D. Powertrain

- digitize and cross-check the full stock 2025 dyno trace;
- engine free-rev acceleration/deceleration with known/no-load conditions to bound equivalent rotational inertia and loss torque;
- clutch launch data using engine speed, rear-wheel speed and vehicle acceleration to identify torque-capacity/slip relationship;
- coast-down/closed-throttle runs by gear to identify engine braking plus drivetrain loss.

## 12. Numerical validation matrix

The no-assist plant must pass all of these before game-feel tuning:

1. static equilibrium and front/rear axle load
2. static sag / ride height
3. suspension step response
4. controlled bump/drop response
5. unloaded and loaded wheel-spin conservation/decay tests
6. engine/gear kinematic ratio tests
7. clutch launch
8. straight-line acceleration
9. coast-down / engine braking
10. hard rear brake and wheel lock as an emergent result
11. front-brake load transfer / stoppie onset if front brake is exposed
12. longitudinal tire slip-force sweep
13. lateral/camber force sweep
14. combined-slip sweep
15. constant-radius equilibrium
16. free-steering stability test
17. steering-torque step / slalom transient
18. wheelie onset without wheelie mode
19. jump release with no takeoff-rate override
20. airborne wheel-acceleration reaction
21. airborne rider-motion reaction
22. exact/near-exact airborne momentum conservation with controls neutral
23. rear-first/front-first/two-wheel landing response
24. timestep-convergence test at 120/240 Hz reference
25. deterministic replay from identical initial state and input history

## 13. Gate status after Research Pass 01

### Gate A — geometry and mass properties: **NOT READY**

Closed/strong:

- reference configuration selected;
- wheelbase/rake/trail/manufacturer geometry available;
- separate tire sizes established;
- stock mass anchor established;
- ISO identification routes established.

Blockers:

- CG height;
- inertia tensor;
- steering/wheel/swingarm inertias;
- exact hardpoint geometry including countershaft/swingarm/peg/bar coordinates.

### Gate B — suspension: **NOT READY**

Closed/strong:

- front/rear architecture selected;
- stock front spring 5.0 N/mm per leg;
- stock rear spring 52 N/mm;
- rear installed spring length 232.2 mm;
- nonlinear damper representation selected.

Blockers:

- exact 2025 Pro-Link motion ratio/hardpoints;
- stock damping force-velocity curves;
- end-stop curves;
- friction characterization.

### Gate C — powertrain: **PARTIAL, NOT READY**

Closed:

- official primary, gearbox and final ratios;
- idle/specification anchors;
- measured peak rear-wheel power/torque anchors.

Blockers:

- full torque trace dataset;
- engine inertia;
- clutch law;
- engine braking/losses.

### Gate D — tire/contact: **NOT READY — highest uncertainty**

Closed:

- model interface and physical mechanisms;
- separate front/rear geometry requirement;
- identification protocol;
- hardpack chosen as first surface baseline.

Blockers:

- MX33 profile/loaded-radius data;
- vertical compliance;
- steady longitudinal/lateral/camber curves;
- combined-slip data;
- relaxation/transient data.

### Gate E — rider/controller: **ARCHITECTURE READY, CALIBRATION NOT READY**

The physical/control separation is defined, but actual standing-rider geometry, movement limits, stiffness/damping and steering-torque envelope remain to be identified.

### Gate N — numerical integration: **NOT READY**

120 Hz deterministic outer scheduling is retained. Internal numerical integration must pass stiffness, convergence and conservation tests before selection.

## 14. Coding authorization rule

Physics V2 runtime migration begins only when:

- Gates A-D contain sufficient parameterized values to run the relevant subsystem without UNKNOWN values being silently replaced by tuned constants;
- Gate N has a tested numerical method;
- every approximation has a source, derivation, identification procedure or explicit `ASSIST` classification;
- the validation harness has reference tests defined before the physical solver replaces the current game solver.

Until then, research code may digitize data, process measurements or validate equations, but the production motorcycle plant remains unchanged.
