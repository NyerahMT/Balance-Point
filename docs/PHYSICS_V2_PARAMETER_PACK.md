# Physics V2 Parameter Pack

Status: research gate; **runtime physics must not be rewritten from this document until the applicable subsystem gate is marked READY**.

The purpose of this document is to keep Physics V2 auditable. A number is not allowed into the physical plant merely because it makes the game feel plausible. Every physical parameter must be one of:

- **PUBLISHED** — directly stated by the manufacturer, a measurement source, a standard, or a technical paper.
- **DERIVED** — calculated from published quantities with the derivation recorded.
- **IDENTIFIED** — obtained from a documented experiment, measurement, or system-identification procedure.
- **ASSIST** — deliberately nonphysical game-control authority, kept outside the physical plant and named as an assist.
- **UNKNOWN** — not yet justified. UNKNOWN values are blockers, not invitations to guess.

Reference motorcycle for the first Physics V2 implementation: **2025 Honda CRF450R**.

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
| Wet motorcycle mass | 113 kg | Honda Europe 25YM release | PUBLISHED |
| US curb-weight cross-check | 249 lb | Honda US specifications | PUBLISHED |
| Wheelbase | 1.482 m US manual / 1.483 m EU release | Honda | PUBLISHED |
| Caster/rake | 27 deg 19 min | Honda owner manual | PUBLISHED |
| Trail | 0.115 m | Honda owner manual | PUBLISHED |
| Ground clearance | 0.333 m | Honda | PUBLISHED |
| Swingarm length | 0.5852 m | Honda Europe 25YM release | PUBLISHED |
| Wet static weight distribution | 48.7% front / 51.3% rear | Honda Europe 25YM release | PUBLISHED |
| Bike-only longitudinal CG from rear axle | about 0.722 m | 0.487 x 1.483 m | DERIVED |
| Bike-only front static load | about 540 N | 113 kg x g x 0.487 | DERIVED |
| Bike-only rear static load | about 569 N | 113 kg x g x 0.513 | DERIVED |
| Bike-only CG height | unknown | Requires measurement/source | UNKNOWN |
| Chassis/whole-bike inertia tensor | unknown | Requires measurement/source | UNKNOWN |
| Steering assembly mass/inertia | unknown | Requires measurement/source | UNKNOWN |
| Front unsprung mass | unknown | Requires measurement/source | UNKNOWN |
| Rear unsprung mass | unknown | Requires measurement/source | UNKNOWN |
| Front wheel rotational inertia | unknown | Requires measurement/source | UNKNOWN |
| Rear wheel rotational inertia | unknown | Requires measurement/source | UNKNOWN |

Honda sources:

- 2025 owner manual, official Honda PDF: https://cdn.powersports.honda.com/documentum/MWOM/ml.remawmom.amker2525omen.pdf
- 25YM Honda Europe technical release: https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1
- Honda US specifications: https://powersports.honda.com/api/custom/downloads/specs?category=motocross&segment=motorcycle&trim=CRF450RS&year=2025

### 2.2 Tires and wheels

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Front tire | Dunlop MX33F 80/100-21 51M | Honda owner manual | PUBLISHED |
| Rear tire | Dunlop MX33 120/80-19 63M | Honda owner manual | PUBLISHED |
| Construction | bias-ply, tube | Honda owner manual | PUBLISHED |
| Cold pressure front | 100 kPa / 15 psi | Honda owner manual | PUBLISHED |
| Cold pressure rear | 100 kPa / 15 psi | Honda owner manual | PUBLISHED |
| Unloaded rolling radius | unknown as an exact physical parameter | Tire geometry/measurement required | UNKNOWN |
| Vertical stiffness vs load | unknown | Test/data required | UNKNOWN |
| Vertical damping | unknown | Test/data required | UNKNOWN |
| Longitudinal force vs slip/load/surface | unknown | Off-road identification required | UNKNOWN |
| Lateral force vs slip angle/load/camber/surface | unknown | Off-road identification required | UNKNOWN |
| Camber thrust/moment behavior | unknown | Tire data/identification required | UNKNOWN |
| Combined-slip coupling | model form required; coefficients unknown | Literature + identification | UNKNOWN |
| Relaxation length/time constants | unknown | Literature + identification | UNKNOWN |

**Important:** road-motorcycle Pacejka coefficients are not acceptable substitutes for MX33-on-dirt data. The off-road literature explicitly notes that road-tire models with known road/tire interaction do not transfer cleanly to motocross because the surface changes, pitch/suspension motion is large and wheels detach frequently.

Off-road force-estimation reference:
https://www.sciencedirect.com/science/article/pii/S0888327020306142

Motorcycle tire-model reference for carcass/contact geometry and transient behavior:
https://doi.org/10.1023/B%3AMECC.0000022842.12077.5C

### 2.3 Front suspension

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Fork type | Showa 49 mm USD coil spring | Honda | PUBLISHED |
| Wheel/fork travel | 310 mm nominal EU / 12.2 in US | Honda | PUBLISHED |
| Standard fork spring | 5.0 N/mm | 2025 competition handbook data; OEM parts cross-check | PUBLISHED |
| Optional softer spring | 4.8 N/mm | 2025 competition handbook data | PUBLISHED |
| Optional stiffer spring | 5.2 N/mm | 2025 competition handbook data | PUBLISHED |
| Base fork oil quantity | 310 cc reported for standard 2025 setup | Dirt Rider technical test | PUBLISHED/SECONDARY |
| Fork-axis geometry | rake known; triple/offset details still needed for exact kinematics | Honda + measurement | PARTIAL |
| Compression force-vs-velocity curve | unknown | Damper dyno/source required | UNKNOWN |
| Rebound force-vs-velocity curve | unknown | Damper dyno/source required | UNKNOWN |
| Coulomb/seal friction | 2025 changed materially, exact force unknown | Honda says friction changed; value absent | UNKNOWN |
| End-stop/bottoming curve | unknown | Test/measurement required | UNKNOWN |
| Exact unsprung mass and fork moving mass | unknown | Measurement required | UNKNOWN |

2025 suspension references:

- Honda technical release: https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1
- 2025 CRF450R test: https://www.dirtrider.com/tests/honda-crf450r-review/
- OEM fork parts cross-check: https://us.fowlersparts.co.uk/parts/6837517/crf450r/front-forks

### 2.4 Rear suspension

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Architecture | Pro-Link / Showa shock | Honda | PUBLISHED |
| Rear wheel travel | about 310 mm / 12.2 in | Honda | PUBLISHED |
| Swingarm length | 585.2 mm | Honda | PUBLISHED |
| Standard shock spring | 52 N/mm | 2025 competition-handbook data | PUBLISHED |
| Standard spring installed length | 232.2 mm | 2025 competition-handbook data | PUBLISHED |
| Spring preload change per adjuster turn | 1.5 mm; 84 N for standard CRF450R spring | competition-handbook data | PUBLISHED |
| Recommended/nominal race sag target | roughly 100-110 mm depending source/setup; do not use as kinematic geometry | setup references | REFERENCE ONLY |
| Pro-Link axle-to-shock motion-ratio curve | unknown | Critical geometry required | UNKNOWN |
| Link/pullrod/pivot hard-point coordinates | unknown | Critical geometry required | UNKNOWN |
| Compression force-vs-shock-velocity curve | unknown | Damper dyno/source required | UNKNOWN |
| Rebound force-vs-shock-velocity curve | unknown | Damper dyno/source required | UNKNOWN |
| Bump-stop/end-stop curve | unknown | Test/measurement required | UNKNOWN |
| Swingarm/linkage mass and inertia | unknown | Measurement/source required | UNKNOWN |

Honda states that the 2025 Pro-Link structure was revised and that its ratio and axle travel were optimized. Therefore an older-year linkage curve must not be silently reused as a 2025 curve.

Competition-handbook transcription/search reference:
https://www.manuals.co.uk/honda/crf450r-2025/manual?p=115

### 2.5 Drivetrain and engine

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Engine | 449.77 cc single-cylinder four-stroke | Honda | PUBLISHED |
| Bore x stroke | 96.000 x 62.138 mm | Honda owner manual | PUBLISHED |
| Compression ratio | 13.5:1 | Honda | PUBLISHED |
| Idle | 2,000 +/- 100 rpm | Honda owner manual | PUBLISHED |
| Primary reduction | 2.357 | Honda owner manual | PUBLISHED |
| Gear 1 | 2.133 | Honda owner manual | PUBLISHED |
| Gear 2 | 1.705 | Honda owner manual | PUBLISHED |
| Gear 3 | 1.421 | Honda owner manual | PUBLISHED |
| Gear 4 | 1.210 | Honda owner manual | PUBLISHED |
| Gear 5 | 1.043 | Honda owner manual | PUBLISHED |
| Final reduction | 3.769 / 13T:49T | Honda | PUBLISHED |
| Overall ratios 1-5 | 18.949, 15.146, 12.624, 10.749, 9.266 | primary x gear x final | DERIVED |
| Peak rear-wheel dyno power | 51.1 hp at 9,600 rpm | Dirt Rider dyno | PUBLISHED/MEASURED |
| Peak rear-wheel dyno torque | 32.9 lb-ft / about 44.6 N m at 6,900 rpm | Dirt Rider dyno | PUBLISHED/MEASURED |
| Full torque curve | chart exists but not yet digitized/validated into data points | Dirt Rider | UNKNOWN DATASET |
| Engine equivalent rotational inertia | unknown | Identification/measurement required | UNKNOWN |
| Clutch torque-capacity vs engagement | unknown | Identification/measurement required | UNKNOWN |
| Clutch rotating inertia | unknown | Identification/measurement required | UNKNOWN |
| Engine-braking map | unknown | Identification required | UNKNOWN |
| Drivetrain efficiency/loss map | unknown | Identification required | UNKNOWN |

Official 2025 Honda drivetrain ratios are in the owner manual linked above. Dyno source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

### 2.6 Brakes

| Parameter | Physics V2 value | Provenance | Status |
|---|---:|---|---|
| Front rotor | 260 mm | Honda | PUBLISHED |
| Rear rotor | 240 mm | Honda | PUBLISHED |
| Master-cylinder/caliper hydraulic gain | unknown | Component/service data or identification | UNKNOWN |
| Pad friction/effective radius | unknown | Component data or identification | UNKNOWN |
| Maximum wheel brake torque | unknown | Derived only after hydraulic/friction data or measured decel | UNKNOWN |

Brakes will act as wheel torques. A rear-wheel lock is an outcome of brake torque exceeding the tire's available longitudinal force, not a special lockup mode.

### 2.7 Chain/suspension interaction

The drive chain will be modeled geometrically from countershaft, swingarm pivot, rear-axle and sprocket pitch radii. Chain tension creates forces/moments on the swingarm and therefore contributes to squat/anti-squat.

| Parameter | Status |
|---|---|
| 13T front / 49T rear tooth counts | PUBLISHED |
| Rear sprocket pitch radius | DERIVABLE once chain pitch geometry is fixed |
| Countershaft center relative to swingarm pivot | UNKNOWN |
| Swingarm pivot relative to chassis CG | UNKNOWN |
| Rear axle path | PARTIAL: swingarm length known; hardpoint geometry still required |

Until those hardpoints are known, acceleration-induced suspension behavior is not allowed to be represented by an arbitrary anti-squat multiplier.

---

## 3. Rider model parameter register

Motocross riding is dominated by a standing rider whose arms and legs isolate and actively couple the rider to the motorcycle. The rider will therefore not be collapsed into a fixed CG offset.

### 3.1 Reduced rider model

The reduced rider has three relative translational coordinates:

- fore/aft
- vertical
- lateral

Forces are transmitted through explicit footpeg and handlebar attachment points. Internal rider motion produces equal-and-opposite reaction on the motorcycle. Rider steering input is expressed as handlebar/steering torque.

The reduced model is intentionally less detailed than a full biomechanical human because the game cannot identify dozens of human joint parameters reliably. It retains the mechanisms that matter to motocross vehicle dynamics.

Primary off-road rider reference:
https://www.sciencedirect.com/science/article/pii/S0888327020306142

### 3.2 Rider parameters

| Parameter | Status |
|---|---|
| Reference rider mass | DESIGN VARIABLE; not a motorcycle constant |
| Rider CG in neutral standing posture | UNKNOWN/needs anthropometric or measured reference |
| Footpeg coordinates | PARTIAL: Honda publishes footpeg height; fore/aft coordinates still needed |
| Handlebar coordinates | UNKNOWN |
| Maximum fore/aft body travel | IDENTIFICATION REQUIRED |
| Maximum vertical body travel | IDENTIFICATION REQUIRED |
| Maximum lateral body travel | IDENTIFICATION REQUIRED |
| Effective leg stiffness/damping | IDENTIFICATION REQUIRED |
| Effective arm stiffness/damping | IDENTIFICATION REQUIRED |
| Active control bandwidth/latency | IDENTIFICATION REQUIRED |
| Maximum steering torque envelope | IDENTIFICATION REQUIRED |

Any control augmentation beyond forces achievable by this model is labeled ASSIST.

---

## 4. Tire model scope and gate

### 4.1 Required structure

For each wheel, the tire model must receive at least:

- contact position and normal
- wheel-center velocity
- wheel angular velocity
- wheel orientation / camber
- vertical load
- longitudinal slip ratio
- lateral slip angle
- surface class/state
- tire transient state

It must return at least:

- normal force `Fz`
- longitudinal force `Fx`
- lateral force `Fy`
- self-aligning/contact moments required by the chosen motorcycle tire formulation

Combined slip is mandatory: acceleration/braking must reduce available lateral capacity and lateral demand must reduce available longitudinal capacity through one coherent force law.

### 4.2 Model family

The implementation should be capable of representing a motorcycle-specific nonlinear force surface similar in structure to the Cossalter/Lot motorcycle tire work or an appropriately identified combined-slip formula. The contact point must follow the rounded tire/camber geometry rather than treating the tire as an upright car wheel.

A transient relaxation/carcass state is required so lateral force does not appear instantaneously after steering input.

### 4.3 What is not permitted

- no constant `mu * Fz` as the complete tire model
- no separate unrelated longitudinal and lateral clamps
- no arbitrary fixed percentage of lateral grip retained under power
- no road-racing tire coefficients presented as MX33 dirt coefficients
- no surface-name multipliers with no identified physical meaning

### 4.4 V2.0 dirt scope

Physics V2.0 will **not** solve full deformable soil. Full terramechanics would add sinkage, compaction, shear deformation, bulldozing and evolving rut geometry before the core motorcycle has been validated.

V2.0 uses the existing terrain geometry with **identified effective tire-force parameter sets** for a small number of surface classes. Their uncertainty must be recorded. Deformable-soil physics is a later research stage.

---

## 5. Mass-property identification gate

The physical plant cannot use arbitrary scalar pitch/yaw inertias.

Two established standards define the relevant measurement problem:

- ISO 9130:2005 — motorcycle and motorcycle/rider center-of-gravity location: https://www.iso.org/standard/33494.html
- ISO 9129:2008 — motorcycle and motorcycle/rider moments of inertia: https://www.iso.org/standard/42939.html

### Required before chassis solver calibration is called validated

- bike-only CG: x, y, z
- whole-bike principal moments or full inertia tensor about CG
- front steering assembly inertia about steer axis
- front and rear wheel rotational inertia
- rear swingarm/linkage inertia or justified reduced equivalent
- rider neutral CG and mass

The published 48.7/51.3 static split gives us the bike-only longitudinal CG location but **not CG height or the inertia tensor**.

---

## 6. Suspension identification gate

### Front

READY for geometry skeleton only when fork axis and travel are set from Honda dimensions. **Not ready for final force calibration** until a force law is sourced/identified.

Required force law:

`F_fork = F_spring(x) + F_compression(v,x) + F_rebound(v,x) + F_friction(v,x) + F_endstop(x,v)`

A single linear damping coefficient is not considered sufficient final calibration.

### Rear

The rear solve must transform swingarm angle into shock displacement/velocity through a measured/derived **Pro-Link motion-ratio curve**. Shock force is then transformed back to wheel/swingarm generalized force.

Required before rear suspension is called validated:

- pivot coordinates
- pullrod/link coordinates
- shock mount coordinates
- shock length at reference position
- axle-to-shock displacement map over full travel
- compression force-vs-velocity data
- rebound force-vs-velocity data
- end-stop behavior

The published 52 N/mm shock spring by itself is not the rear wheel rate.

---

## 7. Powertrain model gate

Physics V2 replaces RPM-target interpolation with rotational dynamics:

`I_e * d(omega_e)/dt = T_engine - T_clutch - T_engine_loss`

The clutch transmits a bounded torque based on relative angular speed and player/automatic clutch command. Gearbox ratios map clutch/transmission torque to the rear wheel. Rear wheel speed feeds back through the same kinematic chain.

### Ready now

- primary ratio
- all five gear ratios
- final ratio
- idle RPM
- peak dyno power and torque anchors

### Blockers for calibrated model

- digitized full torque/power curve
- engine equivalent rotational inertia
- engine-braking/loss torque curve
- clutch torque-capacity/engagement behavior

The existing hand-shaped torque curve is not accepted as Physics V2 source data.

---

## 8. Free-steering and virtual-rider controller scope

The physical steering system receives torque. It does not receive a target steering angle.

The virtual rider is a controller above the plant and may use:

- desired curvature/turn command from the touch stick
- motorcycle roll/roll rate
- yaw rate
- steering angle/rate
- speed
- tire contact state

Its outputs are physically applied steering torque and rider-body targets.

This lets the phone controls remain intuitive without replacing countersteering/self-steering physics with a direct steering-angle command.

Controller gains are not motorcycle constants. They are tuning/assist parameters and must remain separately visible.

---

## 9. Validation suite required before game-feel tuning

Physics V2 must have deterministic validation cases before intentional assists are added.

### Static

- total mass and gravity balance
- front/rear static axle load
- static fork/shock sag
- steering geometry/trail sanity

### Straight-line

- clutch launch
- engine RPM/wheel-speed coupling by gear
- acceleration curve
- coast-down
- rear-wheel spin buildup/recovery
- rear-brake lock and recovery
- front/rear braking load transfer

### Suspension

- single bump response
- drop/landing response
- bottoming/end-stop response
- whoop sequence response
- powered vs coasted rear-suspension response to expose chain-force effects

### Lateral

- free-steer release behavior
- small steering-torque step
- steady-radius corner
- slalom
- combined braking/turning
- throttle-on corner exit and rear slip

### Airborne

- ballistic path independent of chassis yaw command
- angular momentum conservation with controls neutral
- rear-wheel acceleration reaction
- rear-wheel braking reaction
- rider fore/aft movement reaction
- rider lateral movement reaction
- no spontaneous roll/yaw leveling with assists disabled

### Transitions

- front-wheel lift emerges continuously from load transfer/contact
- front wheel reacquisition after wheelie
- ramp takeoff emerges when normal force reaches zero
- landing force comes from tire/suspension/chassis dynamics
- one-wheel-to-two-wheel transitions are continuous

### Numerical

- repeatability at fixed 120 Hz
- bounded energy error in no-damping/no-contact tests
- no NaN/Inf propagation
- force/moment balance instrumentation per step

---

## 10. Explicit list of current concepts to remove from the physical plant

These may have been useful prototype controls, but Physics V2 must not preserve them as hidden physical laws:

- speed-commanded steering angle
- direct roll target spring as motorcycle lean physics
- direct airborne yaw acceleration
- airborne roll-leveling spring
- wheelie-specific yaw authority
- separate grounded/wheelie/air pitch-damping regimes used to force attitude behavior
- throttle-snap pitch impulse
- lift-seed pitch-rate helper
- takeoff pitch-rate overwrite/blend
- arbitrary airborne rear-wheel reaction gain above physical angular-momentum exchange
- persistent airborne bar/peg pitch torque after rider relative motion ceases, unless reintroduced explicitly as ASSIST
- arbitrary longitudinal-grip-cost percentage in the lateral solver
- target-RPM engine model and speed-based pseudo-clutch as final drivetrain physics

Current source files containing these prototype mechanisms include `BalancePointGame.java`, `MotorcycleLateralDynamics.java`, `MotorcycleRiderDynamics.java`, and `MotorcycleDrivetrain.java`.

---

## 11. Implementation gates

### Gate A — reference geometry and mass properties

**Current status: NOT READY**

Available:
- mass
- wheelbase
- rake
- trail
- wet front/rear weight distribution
- swingarm length
- basic tire sizes

Still required:
- CG height
- inertia tensor
- steering/wheel/swingarm inertias
- exact steering/fork offsets/hardpoints

### Gate B — suspension kinematics and force laws

**Current status: NOT READY**

Available:
- travel
- spring rates
- swingarm length
- high-level Pro-Link architecture

Still required:
- rear linkage hardpoints/motion-ratio curve
- front and rear damping curves
- end-stop/friction characterization
- unsprung/moving masses

### Gate C — powertrain

**Current status: PARTIALLY READY**

Available:
- all ratios
- idle RPM
- dyno peak anchors

Still required:
- digitized full dyno curve
- equivalent engine inertia
- engine braking/loss torque
- clutch torque behavior

### Gate D — tire/contact model

**Current status: RESEARCH BLOCKER / highest uncertainty**

Available:
- tire model/size/pressure/construction
- validated motorcycle tire-model architecture from literature
- off-road inverse-dynamics literature for identification/validation

Still required:
- effective off-road longitudinal/lateral/camber force surfaces
- load sensitivity
- combined-slip calibration
- relaxation/transient calibration
- vertical tire stiffness/damping

### Gate E — rider plant and virtual controller

**Current status: ARCHITECTURE READY, PARAMETERS NOT READY**

Available:
- off-road literature strongly supports a standing-rider model and explicit handlebar/footpeg coupling

Still required:
- neutral rider geometry/CG
- effective arm/leg response parameters
- body travel limits
- steering-torque envelope and control-response data

---

## 12. Research work order before runtime coding

1. **Acquire/digitize the full 2025 CRF450R dyno curve.** Preserve source image and extracted points with uncertainty.
2. **Recover 2025 Pro-Link kinematics.** Prefer service/CAD/part-drawing hardpoints; otherwise derive a measured axle-vs-shock curve. Do not substitute an older linkage without documenting the mismatch.
3. **Find or identify Showa 2025 damping curves.** Search suspension dyno data/tuning literature; if unavailable, define an explicit measurement/identification protocol instead of inventing coefficients.
4. **Build the mass-property plan.** Use published static distribution immediately; define ISO-consistent CG-height/inertia identification for missing values and identify any defensible published comparison data as bounds only.
5. **Build the MX33/dirt tire identification plan.** Search instrumented motocross literature for usable force/slip ranges and define which parameters can be inferred from real-bike or high-quality reference-game tests without claiming those games are ground truth.
6. **Identify wheel and rotating inertias.** Prefer measured component data; otherwise derive bounded estimates from actual masses/geometries and label them DERIVED with sensitivity ranges.
7. **Research clutch/engine rotational dynamics.** Find measured or component-based inertia/torque-capacity information; otherwise define a black-box identification test from RPM vs wheel-speed transients.
8. **Only after Gates A-D have defensible initial parameter sets, implement the physical plant with assists disabled.**
9. Validate against the Section 9 test matrix.
10. Add game-feel/controller assists only after discrepancies in the physical plant are understood.

---

## 13. Source hierarchy

When sources disagree, use this priority unless there is a documented reason not to:

1. Honda factory technical/owner/service/competition data for machine geometry and specifications.
2. Direct instrumented measurements/dyno data for the same model year and configuration.
3. Peer-reviewed motorcycle/off-road dynamics literature for model structure and experimentally measured mechanisms.
4. High-quality measurements from adjacent model years/configurations as bounded references only.
5. MX vs ATV Legends / MX Bikes black-box measurements for game-feel and controller behavior, never as proof of real physical constants.
6. Community/forum values only as leads to find a better source, not as final physical constants.

This hierarchy is intentionally strict. Physics V2 should be slower to start and much faster to trust.