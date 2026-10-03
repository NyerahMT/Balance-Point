# Physics V2 Research Pass 07

Status: **research / identification planning only — no runtime physics changed.**

This pass stops treating the remaining blockers as generic “missing data” and converts them into a concrete calibration campaign. It also tightens the useful operating envelope for suspension identification and clarifies which published dyno data can and cannot be used for drivetrain identification.

Reference machine remains the **late-2025 US Honda CRF450R production-spec configuration** frozen in Research Pass 06.

---

## 1. Coding gate status

**CLOSED after Pass 07.**

The remaining blockers are still the same high-sensitivity physical quantities, but the work required to close them is now much more operational:

- Gate A: bike CG height and principal inertias;
- Gate B: 2025 Pro-Link local motion ratio and stock Showa force laws;
- Gate C: equivalent engine inertia, engine-braking/loss torque and clutch slip/capacity;
- Gate D: MX33 hardpack vertical/longitudinal/lateral/combined-slip/transient calibration.

No new model family is required. The next gains come from measurement, identification and uncertainty reduction.

---

## 2. Real motocross suspension velocity envelope is much wider than a low-speed bench assumption

Motoklik publishes measured motocross suspension-speed examples from instrumented riding. Its public material reports:

- average front-fork compression speed around **0.476 m/s** on one hardpack example;
- real motocross maximum fork speeds up to approximately **7.1 m/s** in its discussed dataset;
- real motocross maximum shock speeds up to approximately **2.3 m/s**;
- rear shock motion is substantially slower than rear-axle motion because of the linkage, described approximately as a 3:1 reduction in the example article.

Sources:

- https://www.motoklik.com/average-speed-of-suspension-in-motocross/
- https://www.motoklik.com/category/news/page/2/
- https://www.motoklik.com/making-accurate-predictions-on-suspension-setup-in-motocross/

These values are **not CRF450R damping constants**. They define the velocity range that the force-law identification must cover or responsibly extrapolate.

### Consequence for Gate B

A stock damping curve measured only at a few very low shaft velocities is insufficient for landing/whoop validation.

The identified front/rear force laws must therefore:

1. preserve separate compression and rebound branches;
2. cover the velocity region actually populated by motocross riding;
3. retain high-speed compression behavior rather than extending one linear low-speed coefficient indefinitely;
4. treat end-stroke/bottoming forces separately from ordinary velocity damping;
5. retain the raw measured force/velocity points and test temperature/setup.

If a shock dyno cannot reproduce the highest track velocities, the unmeasured region must be flagged as extrapolated and constrained by instrumented on-bike response rather than silently extended.

---

## 3. A practical stock-Showa identification campaign is now defined

Public research still has not produced a credible stock-2025 CRF450R Showa force-versus-velocity plot. The fallback is therefore a two-layer experiment.

### 3.1 Bench layer

Record for the exact reference fork/shock setup:

- compression and rebound force versus shaft velocity;
- multiple velocities spanning the available dyno range;
- clicker positions and spring/preload setup;
- oil temperature or stabilized operating temperature;
- displacement within stroke so position-dependent behavior can be detected;
- force hysteresis if visible;
- near-bottom behavior as a separate end-stroke dataset.

### 3.2 On-bike layer

Record synchronized:

- front suspension position and velocity;
- rear shock position and velocity;
- chassis IMU acceleration/angular rate;
- wheel speeds;
- GPS/ground speed;
- throttle/brake states;
- known rider/reference mass and setup.

Motoklik is public evidence that front/rear suspension position and velocity are practical motocross measurements rather than laboratory-only channels:

https://www.motoklik.com/motocross-data-logger/

The on-bike traces are used to verify that the bench curve predicts sag recovery, braking dive, jump-face compression, whoops, landing and rebound without hidden chassis attitude torques.

---

## 4. 2025 Pro-Link gate is now explicitly an empirical kinematics task

Another targeted public search did not recover a trustworthy 2025 bell-crank drawing or local leverage-ratio curve. Honda explicitly states the 2025 linkage has a new leverage ratio and one-piece structure, so older CRF geometry remains prohibited as the final curve.

Primary Honda source:

https://hondanews.com/en-US/releases/release-70527d767f112a34ec2e42bfb5060b28-2025-honda-crf450r

Known 2025-family constraints already retained:

- stock pullrod center-to-center: approximately 147.8 mm from specialist measurement;
- compatible shock envelope: approximately 452 mm eye-to-eye / 133 mm stroke;
- frozen reference rear-wheel travel: approximately 310 mm;
- stock shock spring: 52 N/mm.

The required identification dataset is now fixed as paired samples:

`shock_length_or_stroke_m, rear_axle_position_m`

across the usable stroke.

Preferred physical procedure:

1. remove the shock spring or use a low-force fixture;
2. place the chassis in a repeatable reference attitude;
3. sweep rear-wheel travel slowly;
4. record shock length/shaft position and rear-axle coordinates at small increments;
5. differentiate a fitted monotone curve to obtain local `dx_wheel/dx_shock`;
6. preserve the raw samples so the motion-ratio fit can later be replaced without losing provenance.

A calibrated side-view photogrammetry reconstruction is acceptable only if visible pivot centers and at least two independent known dimensions constrain scale and perspective. Its uncertainty must be carried into the motion-ratio curve.

---

## 5. Dyno data are valid for the engine curve but not for tire/inertia identification

Dirt Rider reports the 2025 CRF450R at **51.1 hp** and **32.9 lb-ft** on its in-house Dynojet 250i.

Source:

https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

However, Dirt Rider's 2025 450 comparison methodology states that the bikes were dyno-tested with a **Dunlop D404 street tire installed on the rear wheel** for drum-roller compatibility rather than the stock motocross rear tire.

Source:

https://www.dirtrider.com/tests/2025-450-motocross-bike-comparison/

### Consequence

The dyno chart remains a valid measured **rear-wheel steady-state power/torque source** for the engine calibration target.

It must **not** be used to infer:

- stock MX33 rolling radius;
- stock MX33 rotational inertia;
- stock rear-wheel transient acceleration behavior;
- stock hardpack tire slip behavior.

This prevents an easy but invalid cross-contamination between Gate C and Gate D.

---

## 6. Tire identification can be closed from instrumented riding data if a bench dataset remains unavailable

Public MX33 force-rig coefficients remain unavailable. That no longer means Gate D has no path forward.

Bartolozzi, Massaro, Mottola and Savino describe a motorcycle tire formulation whose coefficients are identified from ordinary instrumented riding maneuvers rather than requiring every coefficient from a dedicated tire bench.

Reference:

https://doi.org/10.1177/09544070241247239

Vasquez et al. provide the off-road inverse-dynamics side of the problem, including motocross riding with large suspension motion and wheel detachments:

https://doi.org/10.1016/j.ymssp.2020.107228

The numerical road-tire coefficients from Bartolozzi et al. are **not reused**. The useful result is the identification method.

### 6.1 Hardpack campaign channels

Minimum synchronized channels for the first defensible effective hardpack dataset:

- chassis linear acceleration and angular rate;
- attitude estimate;
- GPS/ground speed;
- front and rear wheel angular speed;
- steering angle;
- front fork position;
- rear shock position;
- throttle command;
- front/rear brake command or pressure if available;
- tire pressure and exact tire configuration;
- rider/reference mass and position definition.

### 6.2 Maneuver set

Use separated maneuvers first:

- free rolling / rolling-radius identification;
- straight acceleration at several load-transfer states;
- controlled rear-wheel spin build/recovery;
- straight front/rear braking and lock/recovery;
- steady-radius cornering over multiple speeds/leans;
- low-amplitude slalom/steering transient;

then validate on combined maneuvers:

- throttle-on corner exit;
- trail braking / braking while leaned;
- rough-corner transient;
- contact loss and reacquisition.

The identified runtime surface remains `Fz, kappa, alpha, gamma -> Fx, Fy, Mz`, with transient/relaxation states fit from response lag.

---

## 7. Tire-model structure receives one important refinement

Cossalter/Doria experimental motorcycle tire work shows that camber force, slip-angle force and tire moments have different load dependence and can saturate at high load/camber. It also emphasizes use of the actual moving contact point.

Useful structural references:

- https://doi.org/10.1023/B%3AMECC.0000022842.12077.5C
- https://www.sciencedirect.com/science/article/abs/pii/S0389430401001138

Physics V2 therefore must not reduce lateral force to one `cornering_stiffness * slip_angle` relation plus a friction clamp.

The implementation interface should permit:

- separate camber and sideslip contributions;
- nonlinear load sensitivity;
- saturation/coupling;
- aligning/twisting moment;
- transient state(s);
- actual contact-point kinematics.

The exact hardpack coefficients remain IDENTIFY.

---

## 8. Engine inertia / braking experiment remains the cleanest Gate-C closure route

The added-inertia perturbation method from Pass 05 remains the preferred approach:

- measure the added polar inertia of a known flywheel ring;
- record repeatable stock and added-inertia closed-throttle RPM decay;
- solve equivalent stock engine inertia from the ratio of angular decelerations;
- then derive closed-throttle loss/braking torque versus RPM.

This is superior to assigning a generic single-cylinder engine inertia because both engine inertia and engine braking materially affect:

- clutch launch;
- RPM flare/recovery;
- shift transients;
- rear-wheel torque transient;
- airborne wheel-reaction pitch authority.

The full-throttle torque curve and the closed-throttle loss curve remain separate datasets.

---

## 9. Exact measurement deliverables required to open the gate

The remaining high-sensitivity work is now reduced to the following artifacts rather than open-ended research:

### Mass / geometry

- bike CG measurement sheet;
- whole-bike principal inertia measurement sheet;
- complete front/rear wheel spin inertia results;
- steering/swingarm inertia bounds or measurements;
- force-application hardpoint coordinate sheet.

### Suspension

- Pro-Link paired wheel/shock travel CSV;
- front fork force/velocity dataset;
- rear shock force/velocity dataset;
- end-stop/bottoming observations;
- static/rider sag reference setup.

### Powertrain

- dense digitized full-throttle dyno table with archived source image/reference;
- engine-inertia identification traces;
- closed-throttle loss map;
- clutch slip/capacity identification traces.

### Tires

- unloaded/loaded/effective front/rear radius observations;
- vertical load-deflection observations;
- hardpack longitudinal dataset;
- hardpack lateral/camber dataset;
- combined-slip validation dataset;
- relaxation/transient observations.

Once those data exist at sufficient quality, the coding gate can be evaluated mechanically instead of by judgment.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

The useful change in Pass 07 is that the remaining blockers are now specified as concrete datasets, sensor channels and experiments. Public web research has largely reached diminishing returns for the exact 2025 Honda linkage/damper and MX33 hardpack coefficients.

The project should now prioritize obtaining/deriving those datasets rather than continuing to collect adjacent-bike constants.
