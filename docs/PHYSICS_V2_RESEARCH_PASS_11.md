# Physics V2 Research Pass 11

Status: **Gate A sensitivity decision / no runtime physics changed.**

This pass executes the deterministic CG-height sensitivity sweep requested by
Research Pass 10 and converts the result into concrete mass-property accuracy
targets. The purpose is to decide whether the unresolved late-2025 CRF450R
CG-height uncertainty can be accepted as `BOUNDED_LOW_SENSITIVITY`.

It cannot.

Reference runtime motorcycle remains the **late-2025 US Honda CRF450R
production-spec configuration**. The analytical sweep intentionally uses the
internally consistent Honda Europe 25YM geometry/load-split prior as one
research configuration rather than mixing EU load split with US wheelbase.

---

## 1. Reproducible sensitivity harness

The sweep is implemented in:

- `tools/physics_v2_gate_a_sensitivity.py`
- `.github/workflows/physics-v2-gate-a-sensitivity.yml`

The workflow emits JSON and CSV artifacts and fails if the expected monotonic
relationships or source-derived longitudinal CG are violated.

Research configuration:

- wheelbase: `1.483 m`
- static front load fraction: `0.487`
- static rear load fraction: `0.513`
- derived longitudinal CG: `0.722221 m` forward of the rear contact patch
- CG-height sweep: `0.600, 0.630, 0.657, 0.690, 0.720 m`

The 0.657 m center is the Honda Figure-9 graphical prior extracted in Pass 10.
The full 0.60-0.72 m interval is a model-generation transfer envelope, not a
claimed 2025 measurement.

---

## 2. Analytical model used for the first screening test

This pass deliberately uses the simplest exact rigid-body load-transfer
relations before involving unknown tire or suspension parameters.

For a motorcycle with wheelbase `L`, longitudinal CG coordinate `x`, CG height
`h`, and forward acceleration `a`:

`N_front / (m g) = x/L - (a/g) h/L`

The idealized acceleration at zero front normal load is therefore:

`a_wheelie / g = x/h`

For straight-line braking with deceleration magnitude `d`:

`N_rear / (m g) = (L-x)/L - (d/g) h/L`

and the idealized rear-lift threshold is:

`d_rear_lift / g = (L-x)/h`

These are screening equations, not a replacement for the final suspension,
tire, rider, or rotational dynamics. They are useful specifically because the
CG-height sensitivity appears without needing any guessed tire coefficient or
damping curve.

---

## 3. Sweep result

| CG height m | Ideal wheelie threshold g | Ideal rear-lift threshold g | Front load fraction at 0.8 g accel | Rear load fraction at 0.8 g braking |
|---:|---:|---:|---:|---:|
| 0.600 | 1.2037 | 1.2680 | 0.1633 | 0.1893 |
| 0.630 | 1.1464 | 1.2076 | 0.1471 | 0.1731 |
| 0.657 | 1.0993 | 1.1580 | 0.1326 | 0.1586 |
| 0.690 | 1.0467 | 1.1026 | 0.1148 | 0.1408 |
| 0.720 | 1.0031 | 1.0566 | 0.0986 | 0.1246 |

Across the 0.60-0.72 m envelope, relative to the 0.657 m center:

- wheelie-threshold full span: **18.25%**
- rear-lift-threshold full span: **18.25%**
- longitudinal-contact-force pitch-arm full span: **18.26%**

The Gate-A low-sensitivity screening limit is 5%.

Result:

`CG_HEIGHT = HIGH_SENSITIVITY_UNRESOLVED`

The broad 0.60-0.72 m transfer envelope therefore cannot open Gate A.

---

## 4. Accuracy target for CG height

For wheelie threshold `a/g = x/h`, first-order relative sensitivity is:

`delta(a)/a ~= -delta(h)/h`

Around the 0.657 m prior, keeping the CG-height contribution to threshold
uncertainty below approximately 5% requires roughly:

`|delta h| < 0.05 * 0.657 = 0.0329 m`

That is only a one-sided first-order limit. To keep the complete accepted
height interval itself near a 5% full-span influence, the working target is
more stringent:

**same-configuration CG-height identification target: +/-0.015 m or better.**

This is intentionally comparable to the graphical extraction uncertainty from
Pass 10, but it must come from the 2025 configuration or an identification that
actually constrains the 2025 configuration. The 2021 graphical prior alone does
not satisfy this requirement because generation-transfer uncertainty dominates.

Preferred closure method remains a static tilt/load measurement using a known
wheelbase, axle loads and measured pitch angle.

---

## 5. Pitch inertia is also necessarily high sensitivity

For any known external pitch moment `M_y`:

`alpha_y = M_y / I_y`

Therefore the first-order relative sensitivity is direct:

`delta(alpha_y)/alpha_y ~= -delta(I_y)/I_y`

An inertia uncertainty of 20% creates approximately 20% uncertainty in pitch
angular acceleration before suspension/tire coupling is even considered.

For first Physics-V2 validation, the working target is therefore:

**whole-bike pitch inertia uncertainty: +/-5% preferred, +/-7.5% maximum unless
a later deterministic validation proves the applicable maneuver insensitive.**

Roll/yaw inertia may eventually be admitted with wider intervals if their own
validation cases prove low sensitivity, but pitch inertia does not get that
assumption.

---

## 6. Adjacent off-road inertia literature is a search prior only

Felipe Vasquez's off-road motorcycle work provides useful model-scale checks.
One published virtual motocross model uses approximately:

- chassis mass: 81 kg
- chassis pitch inertia: 21 kg m^2
- wheelbase: 1.35 m
- chassis CG height: 0.59 m

A related off-road tire-force model reports an 81 kg chassis with approximately
23 kg m^2 pitch inertia.

Sources:

- Vasquez, *Optimisation of off-road motorcycle suspensions*, University of
  Southampton doctoral thesis.
- Vasquez, Lot, Rustighi and Pegoraro, off-road motorcycle tire-force work.

These values are useful for checking orders of magnitude and designing the
identification fixture. They are **not CRF450R measurements**, do not include a
fully equivalent component partition, and are prohibited as late-2025 runtime
constants.

This distinction matters because simply scaling an adjacent-bike inertia by
mass or wheelbase can preserve the wrong mass distribution while producing a
plausible-looking number.

---

## 7. Gate-A measurement deliverable is now finite

The mass-property part of Gate A is no longer an open-ended research task.
It is reduced to the following acceptance artifacts:

1. same-configuration bike-only CG height, target uncertainty <= +/-15 mm;
2. whole-bike pitch inertia, target uncertainty <= +/-5% preferred;
3. roll/yaw inertia or defensible intervals with maneuver sensitivity tests;
4. complete front/rear wheel spin inertias;
5. steering/fork assembly inertia about the steering axis;
6. swingarm/linkage generalized inertia or a defensible reconstruction;
7. force-application hardpoint coordinate sheet with source and uncertainty.

The first two items are high-sensitivity and cannot be replaced by
"typical motocross" values.

---

## 8. Practical CG measurement equation

For a static tilt test with wheelbase `L`, measured front/rear normal loads
`N_f`, `N_r`, and bike pitch angle `mu`, a convenient equilibrium form is:

`N_f (L - x_G) - N_r x_G + (N_f + N_r) tan(mu) z_G = 0`

With the zero-pitch axle loads establishing `x_G`, one or more known tilt
angles solve `z_G`. Repeating the test at several angles provides residuals and
an empirical uncertainty rather than one fragile point estimate.

The test should record:

- tire pressures;
- fuel/coolant/oil state;
- exact bike configuration;
- wheelbase at test setup;
- front and rear scale readings;
- pitch angle from a calibrated inclinometer;
- repeat runs in both tilt directions where practical.

This is inexpensive compared with a full inertia rig and is now the highest
value external measurement for Gate A.

---

## 9. Roadmap consequence

The Pass-10 question is answered:

**No, exact 2025 CG height cannot be skipped on the basis of the 0.60-0.72 m
public-data envelope.**

That envelope changes first-order wheelie/braking thresholds by more than three
times the accepted low-sensitivity limit.

The project should not spend more time searching generic CG-height values.
Either obtain a same-configuration measurement/identification or locate a
manufacturer-quality 2025 value with equivalent uncertainty.

In parallel, pitch-inertia identification should proceed because it is the next
mass-property quantity capable of dominating jump/takeoff/landing pitch
response.

---

## 10. Current verdict

**Gate A remains NOT READY, but its mass-property uncertainty is now quantified
rather than merely unknown.**

Pass 11 closes the CG-sensitivity question and establishes the accuracy needed
to close it physically. The next useful Gate-A result is not another adjacent
bike constant; it is a same-configuration CG-height measurement and a pitch
inertia identification with recorded uncertainty.
