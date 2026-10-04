# Physics V2 Research Pass 09

Status: **research / gate tightening only — no runtime physics changed.**

This pass tightens two of the most important remaining Gate A/B uncertainties without promoting adjacent-generation or replacement-component data into 2025 production constants. It also corrects the rear full-stroke ratio to the frozen late-2025 US reference travel and defines a stronger manufacturer-graphical CG extraction route.

Reference runtime configuration remains the **late-2025 US Honda CRF450R production specification** frozen in Research Pass 06.

---

## 1. Coding gate status

**CLOSED after Pass 09.**

No new model family is missing. Remaining high-sensitivity blockers are still:

- bike CG height and principal inertias;
- 2025 Pro-Link **local** motion-ratio curve;
- stock 2025 Showa force laws;
- MX33 hardpack force/transient calibration;
- engine equivalent inertia, braking/loss map and clutch slip/capacity.

This pass materially improves the bounds around the first two items but does not claim they are measured for the 2025 US reference bike.

---

## 2. Rear full-stroke wheel/shock ratio corrected to the frozen reference

The frozen late-US production reference uses **12.2 in rear-wheel travel**, not the 12.4 in value in Honda's earlier launch specification.

Therefore:

`rear wheel travel = 12.2 in = 0.30988 m`

The direct-fit Ohlins DMX1220 replacement for the 2025 CRF450R is published with **133 mm metal-to-metal stroke** and approximately 452 mm eye-to-eye length.

Using those two quantities only as a compatibility envelope:

`R_avg = wheel travel / shock stroke`

`R_avg = 0.30988 / 0.133 = 2.3299`

So the corrected **average full-stroke displacement ratio is approximately 2.330 wheel/shock**.

Classification:

- 0.30988 m rear travel: `PUBLISHED`, frozen late-US Honda reference;
- 0.133 m shock stroke: `COMPATIBLE_PART_PROXY`, direct-fit Ohlins;
- 2.3299 average ratio: `DERIVED_COMPATIBILITY_BOUND`.

This supersedes the earlier ~2.368 sanity result that combined the early-launch 12.4 in rear-travel figure with the same 133 mm replacement-shock stroke.

It remains **invalid to use 2.330 as a constant local Pro-Link motion ratio**. The linkage is progressive and Gate B still requires the local curve `dx_wheel/dx_shock` through travel.

Sources:

- Honda late-US reference: https://powersports.honda.com/-/media/products/family/crf450r/brochure/2025/2025-crf450r-brochure.pdf
- Ohlins 2025 CRF450R fitment: https://www.ohlins.com/en-us/motorcycle-suspension/mx-enduro/honda-crf-450r_?v=honda-crf-450r-2025

---

## 3. Static-sag equilibrium provides an independent ratio consistency envelope

The Ohlins 2025 CRF450R setup material provides a useful replacement-shock sanity case:

- spring rate: approximately 52 N/mm for the relevant stock-like setup;
- recommended free sag: approximately 40 +/- 5 mm;
- spring preload: approximately 8 mm / four turns.

Honda Europe publishes for its 25YM CRF450R configuration:

- wet mass: 113 kg;
- rear static load fraction: 51.3 percent.

Therefore the manufacturer static rear contact load for that European configuration is:

`F_rear = 113 * 9.80665 * 0.513 = 568.48 N`.

For a deliberately simplified quasi-static consistency check, define:

- `R = dx_wheel/dx_shock`;
- shock spring rate `k = 52,000 N/m`;
- preload `p = 0.008 m`;
- rear free sag `s` measured at the wheel.

Ignoring gas force, seal friction, tire vertical deflection, linkage-angle force-vector effects beyond the local virtual-work ratio, and any configuration mismatch, spring compression associated with wheel sag is approximately `s/R`. The wheel support force is then approximated by:

`F_rear = k * (p + s/R) / R`.

Solving the positive root for `R` gives:

| Free sag | Derived local-ratio consistency value |
|---:|---:|
| 35 mm | 2.192 |
| 40 mm | 2.313 |
| 45 mm | 2.427 |

The nominal **40 mm case produces R ~2.313**, very close to the independently derived full-stroke average compatibility ratio ~2.330.

### Interpretation

This agreement is useful but must not be overclaimed.

It shows that the published replacement-shock setup, Honda static rear load, 52 N/mm spring and the ~2.33 full-stroke compatibility ratio are mutually plausible to first order. It does **not** identify the 2025 Showa linkage curve.

Positive shock-gas extension force or other shock support would increase the shock force available for the same spring compression; under this simplified equilibrium, matching the same wheel load would therefore require a somewhat **larger inferred R**. Tire deflection, friction and exact reference attitude also perturb the result.

Classification: `DERIVED_STATIC_CONSISTENCY_ENVELOPE`, not runtime calibration.

---

## 4. Older Honda data provide a useful manufacturer sanity cross-check

Honda's official 2017 CRF450R technical release states:

- rear Showa shock stroke: **133 mm**;
- rear axle travel: **314 mm**;
- wheelbase: 1482 mm;
- front-axle to swingarm-pivot distance: 913 mm;
- swingarm-pivot to rear-axle distance: 569 mm.

The 2017 shock/axle figures imply an average displacement ratio:

`314 / 133 = 2.361`.

Source:
https://hondanews.eu/be/nl/cars/media/pressreleases/76984/2017-honda-crf450r-absolute-holeshot

This older manufacturer datum is **not** a 2025 linkage curve. Honda explicitly revised the 2025 Pro-Link structure/leverage behavior. Its value is as an adjacent-generation rejection/sanity bound: an average ratio in the low-to-mid 2s is consistent with Honda's own historical CRF450R geometry and with the direct-fit 2025 Ohlins compatibility envelope.

No older bell-crank coordinates are promoted into the 2025 plant.

---

## 5. Honda R&D Figure 9 can now be treated as a calibratable graphical CG prior

Honda R&D's 2026 technical paper on the CR ELECTRIC states that the EV motocrosser was designed to attain the same target CG as the CRF450R. Figure 9 shows a side view with a labeled **"Center of gravity position of CRF450R"** marker.

Crucially, Table 1 in the same paper identifies the comparison motorcycle as the **2021 model CRF450R** and gives its wheelbase as **1481 mm**.

Primary Honda R&D source:
https://doi.org/10.69239/hondatechnicalreview.2026_38_3

This improves the status of Figure 9. Earlier passes treated the artwork as lacking a calibrated dimension; the same paper actually supplies an independent wheelbase scale for the reference bike.

### 5.1 Allowed extraction method

A graphical CG prior may be extracted only from a lossless/high-resolution rendering of Figure 9 using this procedure:

1. identify front and rear wheel centers/contact-axis reference consistently in the figure;
2. measure their pixel separation `L_px`;
3. set scale `S = 1.481 m / L_px`;
4. measure the CRF450R CG marker pixel coordinates relative to the rear wheel/contact reference;
5. convert horizontal and vertical offsets using `S`;
6. repeat the extraction with multiple plausible wheel-center/marker selections;
7. carry crop, perspective and marker-thickness variation into `sigma_x` and `sigma_z`;
8. label the result `GRAPHICAL_PRIOR_2021_HONDA`, never `MEASURED_2025`.

### 5.2 Why this does not close Gate A by itself

The source is manufacturer-authored and dimensionally calibratable, but:

- it is the 2021 CRF450R, not the frozen 2025 US bike;
- the side-view graphic is not a metrology drawing;
- photo perspective and marker placement introduce uncertainty;
- Honda changed chassis/layout details by 2025.

A resulting numeric CG would therefore be valuable for a **same-family prior and sensitivity bound**, not as the final 2025 CG height unless later evidence demonstrates transferability.

This is nevertheless stronger than an unscaled visual guess and should be used to tighten the allowable search interval before ISO-style identification.

---

## 6. Swingarm/axle path and linkage uncertainty are now cleanly separated

Honda Europe states that the 25YM aluminum swingarm is **585.2 mm long and unchanged**, while the Pro-Link structure and leverage ratio are revised.

Therefore the V2 rear geometry can cleanly separate:

### Manufacturer-constrained

- swingarm rotational DOF;
- pivot-to-axle radius ~0.5852 m;
- rear-axle circular arc about the swingarm pivot once reference attitude is established.

### Still 2025-specific / unresolved

- lower shock mount relationship;
- cushion-arm frame pivot;
- cushion-arm shock pivot;
- cushion-arm pullrod pivot;
- pullrod-to-swingarm pivot;
- local shock-travel versus axle-travel curve.

This means Gate B is no longer blocked by the **rear axle path itself**. It is blocked by the mapping from that axle/swingarm state into shock compression and force.

Source:
https://hondanews.eu/eu/en/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

---

## 7. Wheel-inertia bounds: use exact tire masses only as geometric rejection bounds

Current exact-size retail measurements/proxies place the MX33 tire masses around:

- front 80/100-21: approximately **3.7-4.0 kg** depending source/packaging field;
- rear 120/80-19: approximately **5.4-5.5 kg**.

These masses are useful because tire-only polar inertia cannot exceed a thin-ring ceiling `m*r_outer^2` for a mass distribution contained within the stated outer radius. They also establish a nonzero lower contribution before rim, tube, hub, rotor, spokes and rear sprocket are added.

Physics V2 still does **not** use a thin-ring tire estimate as the complete wheel inertia. The complete mounted assembly should be measured by pendulum or known-torque spin test.

The main practical conclusion remains: prototype wheel/unsprung values cannot be relabeled as literal physical masses/inertias merely because they produced stable gameplay.

---

## 8. Suspension-source contamination rule tightened

Several secondary suspension pages mix CRF450R, CRF450RX, prior-year, optional-spring or tuner setup values. Passes 04-08 already established that the 2025 R and RX standard spring sets differ.

For Gate B:

- Honda/manual same-model values outrank tuner/shop setup pages;
- a secondary value that conflicts with the frozen R configuration is retained only as a lead or alternate setup;
- no fork oil quantity, spring rate, clicker count or damping curve is accepted simply because a page title contains "2025 CRF450R";
- RX and R data must remain configuration-tagged.

The frozen stock R spring values remain:

- fork: **5.0 N/mm each leg**;
- rear shock: **52 N/mm**.

No conflicting 4.8 N/mm fork value is allowed to overwrite the R baseline without a same-configuration primary source.

---

## 9. Gate impact after Pass 09

### Gate A — geometry and mass

**NOT READY, but CG prior quality improves.**

Improved:

- Honda R&D 2021 CRF450R graphical CG figure is now dimensionally calibratable using the 1481 mm wheelbase in the same paper;
- extraction method and uncertainty class are defined;
- rear swingarm/axle geometry is manufacturer constrained.

Still blocking:

- 2025-reference CG height tight enough for load-transfer/wheelie prediction;
- pitch/roll/yaw inertias;
- complete wheel, steering and swingarm/linkage inertias or proven low-sensitivity bounds;
- final chassis hardpoint coordinate set.

### Gate B — suspension

**NOT READY, but the plausible ratio envelope is materially tighter.**

Improved:

- corrected frozen-reference full-stroke compatibility ratio ~2.330;
- independent static free-sag consistency result ~2.19-2.43, nominal ~2.31 under stated simplifying assumptions;
- older Honda 2017 average 2.361 provides a manufacturer adjacent-generation sanity check;
- rear axle path is no longer an unresolved arbitrary slider.

Still blocking:

- actual **local 2025 Pro-Link motion-ratio curve**;
- stock Showa compression/rebound curves;
- bottoming/friction calibration.

### Gate C — powertrain

**PARTIAL / unchanged in this pass.**

No invented engine or clutch constants were added.

### Gate D — tire/contact

**NOT READY / unchanged materially in this pass.**

Exact tire masses improve future complete-wheel inertia bounds, but they do not provide the missing hardpack force surface.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

Pass 09 makes two useful advances without cheating the provenance rule:

1. the rear-linkage search space is now centered by three mutually consistent pieces of evidence — corrected 2025 replacement-shock full-stroke ratio, static-sag equilibrium, and older Honda manufacturer geometry — while still refusing to pretend any of them is the 2025 local Pro-Link curve;
2. Honda R&D's Figure 9 is now recognized as a legitimately **calibratable adjacent-generation CG prior** because the same source supplies the 2021 CRF450R wheelbase.

The next gate-closing work should execute the Figure 9 pixel extraction with uncertainty and use it to narrow CG sensitivity bounds, while the Pro-Link, Showa, tire-force and engine/clutch gates continue through the measurement/identification campaign defined in Pass 07 and `PHYSICS_V2_CALIBRATION_DATASET_SPEC.md`.
