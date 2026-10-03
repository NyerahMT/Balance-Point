# Physics V2 Research Pass 05

Status: **research only — no runtime physics changed.**

This pass concentrates on turning the remaining UNKNOWN values into bounded identification problems rather than continuing an open-ended search for perfect factory data. The reference machine remains the **stock 2025 US Honda CRF450R**.

The main additions are:

- a motocross-specific nonlinear vertical-tire stiffness envelope from Sumitomo Rubber test work using the same 80/100-21 and 120/80-19 size class on a 450 cc motocross motorcycle;
- an explicit separation between Honda's conflicting 2025 US specification documents;
- a manufacturer graphical cross-check for CRF450R center of gravity from Honda R&D;
- confirmation that 2021 swingarm geometry must not be silently transplanted into the 2025 machine;
- a practical engine-inertia / engine-braking identification experiment using a known added flywheel inertia;
- a formal rule for when bounded uncertainty is good enough to open the coding gate.

---

## 1. Coding gate status

**CLOSED after Pass 05.**

However, the gate is no longer defined as "find every exact factory constant." That would be both unrealistic and scientifically unnecessary.

The coding gate may open when every parameter needed by the first no-assist plant is in one of these states:

1. **PUBLISHED / MEASURED** — direct source or direct measurement;
2. **DERIVED / IDENTIFIED** — obtained from published geometry or a documented identification experiment;
3. **BOUNDED-LOW-SENSITIVITY** — exact value unavailable, but a physically defensible interval exists and sensitivity analysis shows that uncertainty across the interval does not materially alter the validation outputs being used at that stage.

A parameter may **not** open the gate as merely "plausible" or because one tuned value feels good.

For any **high-sensitivity** parameter, the gate remains blocked until the value is measured/identified tightly enough that the important outputs are no longer dominated by its uncertainty.

This gives Physics V2 a practical stopping rule without weakening the user's requirement that the physical model not be invented by feel.

---

## 2. Motocross-specific vertical tire nonlinearity

Sumitomo Rubber Industries patent JP7188038B2 is unusually useful because it does not merely discuss generic motorcycle tires. Its comparison tests use:

- motocross tires;
- front size **80/100-21**;
- rear size **120/80-19**;
- front rim **21 x 1.60**;
- rear rim **19 x 2.15**;
- pressure **80 kPa** front and rear;
- a **450 cc motocross motorcycle**.

Those sizes and rims are directly representative of the CRF450R size class, although the tested tires are not claimed to be Dunlop MX33s.

Primary source:
https://patents.google.com/patent/JP7188038B2/en

### 2.1 Definition used by the patent

The patent defines vertical spring constant as:

`K_v = A / B`

where:

- `A` = vertical load;
- `B` = vertical deflection.

It then defines the rate of change of vertical spring constant with load as the difference in spring constant divided by the difference in load.

The preferred load regions are:

- first region: **0.96 to 1.92 kN**;
- second region: **>1.92 to 2.88 kN**.

The stated desirable stiffness-rate slopes are:

- first region: **50 to 100 N/mm per kN**;
- second region: **30 to 80 N/mm per kN**.

The test examples use **75 N/mm per kN** for the first-region stiffness-rate slope.

### 2.2 What this means for Physics V2

This is the strongest public evidence found so far that a motocross tire's vertical stiffness should be treated as **load dependent**, not as one constant radial spring.

It does **not** give the absolute MX33 spring constant. Therefore Physics V2 will not put 75 N/mm/kN directly into a Dunlop parameter table as if it were an MX33 measurement.

Instead, this source supplies a physically defensible prior/constraint for the *shape* of the vertical load-deflection function while direct loaded-radius measurements establish its absolute level.

A suitable identification workflow is:

1. measure static loaded radius / vertical deflection of the actual reference tire at several known wheel loads and the reference pressure;
2. fit a monotone `Fz(delta_z)` curve;
3. require the fitted tangent/effective stiffness evolution to remain compatible with the motocross-specific load-dependent behavior above unless the direct measurements clearly show otherwise;
4. retain raw observations and uncertainty separately from the fitted runtime curve.

This is substantially better than assigning `k_t = 150000 N/m` because it appeared in a simplified off-road simulation.

---

## 3. Real motocross test envelope for tire validation

A broader family of Sumitomo/Dunlop off-road tire patents repeatedly uses essentially the same test configuration:

- 450 cc motocross competition motorcycle;
- 80/100-21 front;
- 120/80-19 rear;
- 21 x 1.60 and 19 x 2.15 rims;
- approximately **80 kPa** inflation;
- hard, soft, muddy, traction, braking, cornering and slide-control evaluations depending on the study.

Representative sources:

- https://patents.google.com/patent/US9950571B2/en
- https://patents.google.com/patent/EP3006232B1/en
- https://patents.google.com/patent/JP2014144752A/en

These tests do not provide a usable quantitative `Fx/Fy` force surface, but they establish that the pressure, load and size ranges being targeted by the Physics V2 identification plan are representative of real motocross development rather than arbitrary game ranges.

For V2 hardpack identification, the default experimental pressure range should therefore include roughly **80-100 kPa (about 11.6-14.5 psi)**, spanning the common patent-test condition and Honda's nominal 15 psi reference.

---

## 4. Riding-data tire identification becomes the preferred fallback

Bartolozzi, Massaro, Mottola and Savino published a motorcycle tire formulation specifically designed to be characterised from ordinary instrumented riding data rather than requiring a dedicated tire bench. Their coefficients are identified from quasi-static uncombined-slip riding manoeuvres, while the model is structured to reproduce transient and combined manoeuvres and tire moments.

Reference:
https://doi.org/10.1177/09544070241247239

This work is road-motorcycle focused, so its numerical coefficients are not imported. Its **identification methodology** is highly relevant to Balance Point because public MX33 force-rig data remain unavailable.

Combined with the off-road inverse-dynamics methodology from Vasquez et al., the preferred fallback for Gate D is now:

1. collect time-synchronised chassis speed/acceleration, wheel speeds, steering, roll/pitch/yaw, suspension displacement and rider/vehicle geometry on representative hardpack;
2. reconstruct wheel normal loads and contact forces through inverse dynamics;
3. identify quasi-static `Fx(kappa,Fz)` and `Fy(alpha,gamma,Fz)` surfaces from separated manoeuvres;
4. validate the same fitted surface on combined throttle/cornering/braking manoeuvres;
5. identify relaxation/transient time or length scales from force-response lag in steering/slip transients;
6. preserve uncertainty and reject parameter sets that require hidden attitude-control torques to match the data.

Michelin's SIMIX catalog independently confirms that off-road tires in these exact size classes are characterised commercially using static vertical/longitudinal/lateral/torsional stiffness and higher-order handling datasets. The proprietary numerical data are not copied into this project.

Example source:
https://chassis-simulation-datasets.michelin.com/Tires/49301-986133-120-80-19-m-c-63r-tracker-r-tt-stiffness.html

---

## 5. CRF450R CG: Honda now supplies a manufacturer cross-check, not a dimension

Honda R&D's 2026 CR ELECTRIC technical paper states that the electric motocrosser was packaged so its target center of gravity was the **same as the CRF450R**. Figure 9 graphically marks the CRF450R CG and CR ELECTRIC CG positions in side view.

Honda R&D source:
https://www.jstage.jst.go.jp/article/hondatechnicalreview/38/0/38_2026_38_3/_supplement/_download/38_2026_38_3_1.pdf

This is valuable manufacturer evidence that the CRF450R CG is a deliberate target quantity and gives a graphical sanity check for an identified location.

It is **not dimensioned sufficiently to extract an exact CRF450R CG coordinate without an additional calibrated geometric reference in the figure**. Therefore no numeric CG height is derived from the artwork.

The paper's comparison CRF450R is also a previous-generation reference, not proof of the exact 2025 US bike CG.

Gate A still requires an ISO-style measurement/identification or another dimensioned same-configuration source for the final numeric CG height.

---

## 6. 2025 US Honda specification discrepancy is now explicit

Two official American Honda 2025 sources disagree materially enough that one cannot be treated as a rounding variant of the other.

### 6.1 May 2024 Honda News launch specification

Honda News reports:

- curb weight: **245 lb**;
- rake: **27.1 deg**;
- trail: **114 mm / 4.48 in**;
- front travel: **12.2 in**;
- rear travel: **12.4 in**;
- wheelbase: **58.3 in**.

Source:
https://hondanews.com/en-US/powersports/releases/release-70527d767f112a34ec2e42bfb506153d-2025-honda-crf450r-specifications

### 6.2 Honda Powersports 2025 brochure/specification

Honda's 2025 CRF450R brochure/specification reports:

- curb weight: **249 lb**;
- rake: **27.3 deg**;
- trail: **4.5 in**;
- front travel: **12.2 in**;
- rear travel: **12.2 in**;
- wheelbase: **58.3 in**.

Source:
https://powersports.honda.com/-/media/products/family/crf450r/brochure/2025/2025-crf450r-brochure.pdf

A current Honda-generated 2025 specification sheet carries the same 249 lb / 27.3 deg / 4.5 in / 12.2 in rear figures and labels the figures preliminary / subject to change.

### 6.3 Physics V2 rule

Do not average these values.

For the first runtime calibration, the reference geometry must be frozen to **one declared Honda revision/configuration**, with any measured test-bike mass treated separately. The alternate Honda document remains an uncertainty/configuration record.

The later 249 lb / 27.3 deg / 12.2 in dataset is the stronger candidate for the final production-reference configuration because it is repeated in Honda's brochure/current generated specification, but the switch from the earlier launch values must be explicit in the parameter pack before coding.

---

## 7. Older Pro-Link geometry is now more clearly disallowed

The 2025 CRF450R rear swingarm subassembly is listed as:

`52200-K95-AG0`

Source:
https://www.revzilla.com/oem/honda/2025-honda-crf450r/swingarm

The 2021 CRF450R rear swingarm subassembly is:

`52200-MKE-AF0`

Source:
https://www.revzilla.com/oem/honda/2021-honda-crf450r/swingarm

The changed assembly identity does not by itself prove every hardpoint moved, but it removes any justification for treating a 2021 linkage/swingarm drawing as exact 2025 geometry.

Combined with Honda's explicit statement that the 2025 Pro-Link structure and leverage behavior changed, Gate B continues to require either:

- actual 2025 hardpoint coordinates; or
- an empirical 2025-family wheel-travel versus shock-travel map.

Older geometry can be used only as a **bound / initial measurement fixture**, never as the final 2025 curve.

---

## 8. Engine inertia and engine-braking identification: added-inertia perturbation

A practical identification route is now available even if Honda never publishes crankshaft/flywheel inertia.

Steahly sells a CRF450R/RX 2017-2026 flywheel weight that adds **7 oz (approximately 0.198 kg)** to the stock rotor. It is an interference-fit ring intended to increase rotational inertia, reduce stalling and smooth low-speed power delivery.

Reference:
https://www.crfsonly.com/crf450r17-24-steahly-flywheel-weight

The 7 oz mass alone is **not** enough to calculate added inertia; the ring's inner/outer radii or actual polar inertia must be measured.

Once its added polar inertia `Delta I` is measured, however, stock equivalent engine inertia can be identified without initially knowing engine-braking torque.

For the same repeatable closed-throttle RPM region, assume the equivalent loss/braking torque is unchanged between stock and added-weight tests:

`alpha_0 = T_loss / I_0`

`alpha_1 = T_loss / (I_0 + Delta I)`

Then:

`alpha_0 / alpha_1 = (I_0 + Delta I) / I_0`

and therefore:

`I_0 = Delta I / (alpha_0 / alpha_1 - 1)`

Once `I_0` is identified, the same stock deceleration trace gives:

`T_loss(omega) = I_0 * |alpha_0(omega)|`

subject to correction for accessory/drivetrain loads and repeatability.

### 8.1 Why this is useful

This converts two Gate C UNKNOWNs — equivalent engine inertia and closed-throttle loss/engine-braking torque — into a practical differential experiment rather than a guessed constant.

Required controls:

- same coolant/oil temperature range;
- same gear/clutch state;
- same throttle command;
- repeated runs to quantify variance;
- added ring polar inertia measured, not inferred from mass alone;
- avoid regions where ECU idle/anti-stall/fueling intervention dominates.

The identified inertia is an **equivalent crank-referenced inertia** for the simulated engine state, which is exactly what the rotational engine equation requires.

---

## 9. Parameter sensitivity now determines whether an UNKNOWN blocks coding

Before opening the coding gate, each remaining uncertain parameter will be assigned a sensitivity class against the validation matrix.

### High sensitivity / must be identified tightly

Expected examples:

- combined-slip tire force shape;
- lateral/camber tire force shape;
- CG height;
- pitch inertia for jump/wheelie response;
- local Pro-Link motion ratio;
- major compression/rebound damping characteristics;
- engine inertia for RPM and wheel-reaction transients.

### Moderate sensitivity / bounded value may be acceptable initially

Candidate examples, subject to actual sensitivity testing:

- wheel spin inertia if its uncertainty changes airborne pitch reaction only modestly;
- steering assembly inertia over a well-supported same-class interval;
- small friction/end-stop details away from end-of-travel events.

### Low sensitivity / bounded initial value can open the first plant

Only parameters shown by deterministic sensitivity sweeps to have negligible influence on the first validation suite may enter as **BOUNDED-LOW-SENSITIVITY** values.

This classification must be generated by the no-assist validation model, not assumed in advance.

---

## 10. Updated gate state

### Gate A — geometry and mass properties

**NOT READY, but the stop rule is defined.**

New:
- Honda R&D CG figure provides a manufacturer graphical cross-check;
- older-generation values are explicitly only priors;
- sensitivity/bounded-value rule defined.

Still material:
- 2025-reference CG height;
- principal inertias / inertia tensor;
- steering, wheel and swingarm/linkage inertias to identified or low-sensitivity-bounded status.

### Gate B — suspension

**NOT READY.**

New:
- older 2021 swingarm geometry is explicitly blocked as exact 2025 data by different assembly identities plus Honda's 2025 redesign statements.

Still material:
- 2025 local Pro-Link motion-ratio curve/hardpoints;
- Showa force-vs-velocity curves;
- friction/end-stop calibration.

### Gate C — powertrain

**PARTIAL, identification route substantially improved.**

New:
- practical differential flywheel-inertia experiment can identify equivalent engine inertia and closed-throttle loss torque.

Still material:
- perform/obtain inertia identification;
- clutch slip/capacity law;
- dense validated dyno trace / loss characterization.

### Gate D — tire/contact

**NOT READY, but vertical behavior is materially better bounded.**

New:
- same-size, 450 cc motocross-specific load regions and vertical-stiffness-rate bounds;
- representative real motocross pressure/test envelope;
- riding-data identification method selected as the preferred fallback when rig data are unavailable.

Still material:
- MX33 absolute vertical load-deflection curve at reference pressure;
- MX33 hardpack `Fx/Fy/Mz` surfaces;
- combined slip and relaxation calibration;
- final rear radius/effective-radius characterization.

### Gate N — numerical integration

**BENCHMARK-READY.**

No new solver research is required before the validation harness exists.

---

## 11. Next research action

The remaining work should now prioritize **closing parameters, not collecting more architecture papers**:

1. freeze the exact 2025 US Honda reference-spec revision in the parameter pack;
2. obtain/derive a 2025-family rear motion-ratio curve;
3. build bounded mass-property intervals and define the first sensitivity matrix;
4. convert available motocross tire evidence into explicit vertical-force and hardpack-identification constraints;
5. complete the dense dyno trace and powertrain identification protocol;
6. decide which residual parameters are high sensitivity versus bounded-low-sensitivity;
7. open the coding gate only when no high-sensitivity physical parameter remains an unconstrained guess.

**The coding gate remains closed after Pass 05, but the research now has a finite exit criterion.**
