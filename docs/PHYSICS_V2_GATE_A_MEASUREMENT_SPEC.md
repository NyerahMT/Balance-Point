# Physics V2 Gate A Measurement Specification

Status: **execution specification for the remaining Gate-A physical data.**

This document exists because Research Pass 11 proved that the public-data
CG-height envelope is too influential to accept as low sensitivity. It defines
the minimum physical measurements needed to convert Gate A from externally
blocked to READY without inserting guessed motocross constants.

Reference configuration: **late-2025 US Honda CRF450R production spec**.

All measurements must record the exact machine configuration. A result from a
bike with materially different fuel level, wheels/tires, skid plate, exhaust,
battery, or other mass-changing hardware is a different configuration unless
the difference is explicitly reconstructed.

---

## 1. Required outputs and acceptance targets

| Quantity | Preferred uncertainty | Gate-A maximum before sensitivity review |
|---|---:|---:|
| longitudinal bike-only CG | +/- 10 mm | +/- 15 mm |
| vertical bike-only CG | +/- 10 mm | +/- 15 mm |
| whole-bike pitch inertia about CG | +/- 5% | +/- 7.5% |
| whole-bike roll inertia about CG | +/- 7.5% | +/- 10% or sensitivity proof |
| whole-bike yaw inertia about CG | +/- 7.5% | +/- 10% or sensitivity proof |
| complete wheel spin inertia | +/- 5% | +/- 7.5% |
| steering/fork inertia about steering axis | +/- 7.5% | +/- 10% or sensitivity proof |
| swingarm/linkage generalized inertia | +/- 10% | sensitivity-dependent |
| plant hardpoint coordinates | +/- 3 mm preferred | +/- 5 mm unless sensitivity proof |

The uncertainty is the uncertainty of the accepted value, not instrument
resolution alone.

---

## 2. Machine preparation record

Before any test, record:

- VIN/model-year confirmation with identifying information omitted from the
  public dataset if required;
- stock/modified status;
- fuel mass or repeatable fuel level;
- coolant and oil at normal service level;
- battery installed;
- exact front/rear wheel and tire assemblies;
- tire model, size, tube/mousse state, and pressure;
- chain/sprocket configuration;
- skid plate, hand guards, launch device, hour meter, and other accessories;
- suspension spring and preload settings;
- whether mud, tools, stands, or tie-downs contribute load;
- ambient temperature;
- calibrated scale/inclinometer/length-instrument identifiers.

The bike should be clean and complete in the same physical configuration used
for the accepted mass measurement.

---

## 3. Level static axle-load test

### 3.1 Setup

Use two scales or load cells whose combined capacity comfortably exceeds the
bike weight. Their top surfaces must be at equal elevation.

Keep the bike upright without adding vertical support load. Acceptable methods
include very light lateral stabilizing lines or a fixture whose vertical load
can be measured and corrected. A conventional stand under the frame is not
acceptable during axle-load acquisition.

Record at least five repeat placements.

### 3.2 Measurements

For each run record:

- front normal load `N_f0`;
- rear normal load `N_r0`;
- total `W = N_f0 + N_r0`;
- axle-center wheelbase `L` under the test setup.

Longitudinal CG from the rear contact reference is:

`x_G = L * N_f0 / (N_f0 + N_r0)`

The repeated-run mean and standard deviation are retained. If total measured
mass changes appreciably between runs, the setup is not repeatable enough.

---

## 4. CG-height tilt test

### 4.1 Principle

Raise one axle by a precisely known amount while keeping both tire contact
points on vertical-reading scales/load cells. The motorcycle is then pitched by
a known angle while gravity remains vertical.

Let:

- `L` = distance between tire contact references along the bike baseline;
- `x_G` = longitudinal CG from the level test;
- `z_G` = CG height above the tire-contact baseline;
- `theta` = bike pitch angle, positive nose-up;
- `N_f(theta)` = front vertical reaction at that angle;
- `W` = total bike weight.

Moment equilibrium about the rear contact gives:

`N_f L cos(theta) = W [x_G cos(theta) - z_G sin(theta)]`

therefore:

`z_G = [x_G - L N_f/W] / tan(theta)`

This is the same equilibrium used in Research Pass 11, written in a directly
executable form.

### 4.2 Test angles

Do not rely on one small angle. Target at least three nonzero pitch angles that
produce a clearly measurable load transfer without creating an unsafe setup.
A practical campaign is approximately 5, 10, and 15 degrees if the fixture can
support them safely.

Where practical, repeat with the opposite axle raised. Sign reversal is an
excellent systematic-error check.

### 4.3 Acceptance

Fit one `z_G` to all tilt observations rather than selecting the nicest run.
Report:

- fitted `z_G`;
- residual for every observation;
- standard uncertainty from scale, angle, wheelbase, and repeatability;
- expanded/conservative uncertainty used by Physics V2.

Gate-A target: **+/-15 mm or better**, with +/-10 mm preferred.

A result outside the Pass-10 0.60-0.72 m search interval is not automatically
rejected; it triggers a setup/error audit before any prior is allowed to pull
the measurement toward an expected value.

---

## 5. Whole-bike pitch inertia

Pitch inertia is high sensitivity because, for a known external pitch moment,
`alpha_y = M_y / I_y`. It must not be assigned from an adjacent motorcycle.

### 5.1 Preferred method: compound pendulum

Suspend the complete bike from a rigid, low-friction horizontal axis parallel
to the motorcycle lateral axis. The pivot must be fixed relative to the bike.

Measure:

- complete bike mass `m`;
- distance `d` from the identified bike CG to the pivot axis;
- small-amplitude oscillation period `T`;
- oscillation amplitude;
- at least 20 cycles per timing sample;
- at least five independent timing samples.

For a physical pendulum:

`T = 2 pi sqrt(I_p / (m g d))`

where `I_p = I_y + m d^2`.

Thus:

`I_y = m g d T^2 / (4 pi^2) - m d^2`

Use small angles so the small-angle period approximation remains valid, or
apply a documented finite-amplitude correction.

### 5.2 Fixture audit

The fixture must not silently contribute inertia. Either:

- its moving inertia is negligible relative to the bike and demonstrated as
  such; or
- measure the fixture-alone period/inertia and subtract it using the same
  rigid-body formulation.

Bearing friction must be low enough that period does not materially drift over
the timed interval.

### 5.3 Acceptance

Preferred accepted uncertainty: **+/-5%**.

Maximum without a new maneuver-sensitivity argument: **+/-7.5%**.

The raw periods, pivot-to-CG geometry, fixture correction, and uncertainty
calculation must be archived.

---

## 6. Roll and yaw inertia

Roll/yaw can use the same compound-pendulum principle with the suspension axis
reoriented appropriately, or a documented bifilar/trifilar fixture.

If a bifilar rig is used, archive the actual suspension geometry and derive the
restoring stiffness from that geometry rather than copying a generic formula
whose definition of string spacing may differ.

Roll and yaw are allowed a wider initial uncertainty than pitch only if the
first validation suite demonstrates that the interval is low sensitivity.

---

## 7. Complete wheel spin inertia

Measure the wheel as the complete runtime assembly:

- rim;
- spokes/hub/bearings as applicable;
- brake rotor;
- sprocket on the rear;
- exact tire;
- tube/mousse;
- representative pressure.

Preferred identification choices:

1. known applied torque and measured angular acceleration;
2. calibrated torsional/pendulum fixture.

For known torque after subtracting bearing/parasitic torque:

`I_wheel = tau_net / alpha`

Use multiple torque/acceleration levels to verify linearity. A single coast
spin-down time without a torque model is not sufficient.

Target uncertainty: **+/-5% preferred**.

---

## 8. Steering/fork assembly inertia

Measure the moving steering assembly about the physical steering axis with the
front wheel installed in the accepted configuration.

The test must distinguish:

- steering-axis inertia;
- wheel spin inertia;
- trail/contact forces.

The cleanest bench test unloads the front tire from the ground and uses a
small-angle torsional/pendulum identification about the steering axis. Record
cables/hoses in their normal routing because they add restoring torque and
friction; either characterize them or demonstrate that their effect is below
the uncertainty budget.

---

## 9. Swingarm/linkage generalized inertia

The rear assembly may be identified by one of two routes:

- measured masses/CGs/inertias of the swingarm, linkage, axle/wheel assembly and
  a kinematic reconstruction; or
- an equivalent generalized inertia identified from a known applied torque and
  measured swingarm angular acceleration with spring/shock forces removed or
  independently measured.

The accepted representation must remain consistent with the final 2025
Pro-Link kinematics from Gate B.

---

## 10. Plant hardpoint coordinate sheet

Choose one bike-fixed coordinate convention and never silently change it.
Recommended:

- origin: rear tire contact reference in the static reference attitude;
- +X: forward;
- +Z: upward;
- +Y: rider left, forming the documented right-handed convention used by the
  runtime equations.

At minimum record:

- rear axle center;
- swingarm pivot center;
- front axle center at reference fork extension;
- steering-axis definition using two measured points;
- upper/lower shock attachment centers;
- all Pro-Link pivot centers;
- countershaft center;
- rear sprocket center;
- fork travel axis;
- brake-force application references required by the model.

Footpeg and handlebar attachment coordinates are recorded in the rider dataset
and do not block the initial fixed-rider passive plant.

Preferred coordinate uncertainty: **+/-3 mm**; +/-5 mm may be accepted only if
its applicable force/moment sensitivity remains below the gate threshold.

A calibrated side photograph can be a cross-check, but a ruler/CMM-style
physical measurement is preferred for final hardpoints because Honda confirms
the 2025 frame/linkage geometry was substantially revised.

---

## 11. Dataset layout

Store accepted raw data under a configuration-specific directory, for example:

`calibration/gate_a/2025_crf450r_stock/`

Recommended files:

- `configuration.json`
- `static_axle_loads.csv`
- `cg_tilt_runs.csv`
- `cg_fit.json`
- `pitch_pendulum_runs.csv`
- `pitch_inertia_fit.json`
- `roll_inertia_fit.json`
- `yaw_inertia_fit.json`
- `front_wheel_inertia.json`
- `rear_wheel_inertia.json`
- `steering_inertia.json`
- `swingarm_inertia.json`
- `hardpoints.csv`

Every fit file must reference the raw observations used to create it.

---

## 12. Gate-A completion rule

Gate A may change to READY only when:

1. same-configuration CG height meets the accepted uncertainty target;
2. pitch inertia meets its accepted uncertainty target;
3. roll/yaw/component inertias are either identified or demonstrably low
   sensitivity over their retained intervals;
4. plant-essential hardpoints are complete;
5. all values carry units, configuration identity, provenance, and uncertainty;
6. deterministic checks reproduce the measured static axle loads and the
   accepted sensitivity tests without hidden scaling.

Until those observations exist, **Gate A is externally measurement-blocked,
not research-incomplete.**
