# Physics V2 Research Pass 06

Status: **research only — no runtime physics changed.**

This pass freezes the production-reference specification that Physics V2 will target, narrows several mass/unsprung-property bounds, adds stronger same-class inertia priors, and removes another set of tempting but unjustified shortcuts. The reference machine remains the **2025 US Honda CRF450R**, but this pass makes the exact specification revision explicit.

---

## 1. Production reference is now frozen

American Honda published two materially different 2025 CRF450R specification sets. Physics V2 will not average them.

The runtime reference will use the **later/repeated Honda Powersports 2025 production specification**:

- curb weight: **249 lb / 112.94 kg**;
- wheelbase: **58.3 in / 1.48082 m**;
- rake: **27.3 deg**;
- trail: **4.5 in / 114.3 mm**;
- front travel: **12.2 in / 309.9 mm**;
- rear travel: **12.2 in / 309.9 mm**;
- front tire: **Dunlop MX33 80/100-21**;
- rear tire: **Dunlop MX33 120/80-19**;
- final drive: **13/49**.

Primary Honda references:

- https://powersports.honda.com/-/media/products/family/crf450r/brochure/2025/2025-crf450r-brochure.pdf
- https://powersports.honda.com/api/custom/downloads/specs?category=motocross&segment=motorcycle&trim=CRF450RS&year=2025

The earlier Honda News launch set — 245 lb, 27.1 deg rake, 114 mm trail, 12.4 in rear travel — remains recorded as a **superseded/alternate published configuration**, not blended into the production-reference constants.

The Dirt Rider test-bike measurement of about **246 lb wet** remains a real same-model measurement and therefore a validation cross-check for production variability, scale/fuel/configuration differences, not the nominal mass used by the reference parameter set.

### Consequence

Any Physics V2 geometry/data file must identify itself as the **late-2025 US production-spec reference**. If later direct measurement is taken from a specific physical CRF450R, that measured bike becomes a separately named configuration rather than silently replacing individual constants in the production set.

---

## 2. Static longitudinal CG remains derivable, vertical CG does not

Honda Europe publishes a 48.7/51.3 percent front/rear wet static load split for the 25YM CRF450R family. The US and European published masses differ, so the percentage is not promoted to a US-specific measured value; however it remains the best same-generation manufacturer prior for longitudinal mass placement.

Honda Europe source:
https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

With wheelbase `L`, the bike-only longitudinal CG measured from the rear contact is

`x_CG = front_load_fraction * L`.

Using 0.487 and the frozen US wheelbase 1.48082 m gives approximately:

`x_CG = 0.721 m from the rear contact`.

Classification: **DERIVED FROM SAME-GENERATION MANUFACTURER PRIOR**, not an exact US-bike measurement.

It is suitable as an initial longitudinal-coordinate prior and static-load validation target, but the final high-sensitivity CG set still requires direct identification.

No valid numeric **CG height** is introduced in this pass. Honda R&D's CR ELECTRIC paper graphically marks a CRF450R target CG, but the figure is not dimensioned tightly enough to extract an accurate absolute coordinate.

---

## 3. Same-class motorcycle inertial data are now separated by intended use

Several literature datasets are useful, but they describe materially different motorcycles. They are retained only as priors/bounds.

### 3.1 Off-road/motocross prior

Vasquez's virtual motocross model uses:

- chassis mass: 81 kg;
- chassis pitch inertia: 21 kg m^2;
- front wheel mass/inertia: 12 kg / 0.5 kg m^2;
- rear wheel mass/inertia: 18 kg / 0.7 kg m^2;
- swingarm inertia: 0.8 kg m^2;
- chassis CG height: 0.59 m.

Primary source:
https://eprints.soton.ac.uk/448147/

These values remain **same-class modeling priors**, not CRF measurements.

### 3.2 Full motorcycle multibody prior

An open motorcycle multibody study publishes component mass/inertia tensors for a complete road-motorcycle model, including front/rear wheel spin inertias around approximately 0.62/0.81 kg m^2 and an 8 kg swingarm. Those values are useful only for order-of-magnitude rejection tests because the motorcycle, tires and component construction are not motocross-equivalent.

Source:
https://pmc.ncbi.nlm.nih.gov/articles/PMC8512544/

### 3.3 Modern measured mass-property methods

Competition-motorcycle research demonstrates whole-bike inertia-tensor rigs and trifilar-pendulum methods for component inertias. This reinforces the identification route already selected for Physics V2 and provides a practical measurement architecture if a physical 2025 CRF450R becomes available.

References:

- https://www.politesi.polimi.it/handle/10589/107928
- https://www.research.unipd.it/handle/11577/2456885

### Rule

The literature priors above may define **test bounds** and initial sensitivity ranges. They may not become production constants unless sensitivity analysis proves the exact value low-impact and the retained interval is explicitly marked `BOUNDED_LOW_SENSITIVITY`.

---

## 4. MX33 tire mass is now constrained enough to affect unsprung-mass bounds

Retail/product data for the exact rear tire size consistently put the Dunlop Geomax MX33 120/80-19 at roughly **5.4-5.5 kg**:

- Maciag Offroad lists **5.4 kg** for the exact 120/80-19 MX33;
- another distributor lists **12 lb 2 oz**, approximately **5.50 kg**, for the exact 120/80-19 MX33.

References:

- https://www.maciag-offroad.com/dunlop-rear-tire-geomax-mx33-120-80-19-sid122525.html
- https://motomentum.com/products/dunlop-geomax-mx33-tire-rear

For the exact MX33 front 80/100-21, SXS Connection reports package/product information of **8.71 lb**, approximately **3.95 kg**. Because this is presented in a package-information field rather than an engineering datasheet, it is retained as a **retailer mass proxy**, not a manufacturer tire mass.

Reference:
https://sxsconnection.com/products/geomax%C2%AE-mx33%E2%84%A2-tire-mx33

### Consequence for the old runtime suspension model

The current prototype suspension uses intentionally modest effective unsprung masses rather than literal assembly masses. Physics V2 cannot carry those values forward as physical masses.

The tire alone is already a substantial fraction of the complete unsprung assembly. A realistic wheel-side mass state must include, as applicable:

- tire;
- tube;
- rim;
- spokes/nipples;
- hub/bearings;
- brake rotor;
- sprocket and relevant chain share at the rear;
- axle/collars according to the chosen generalized-coordinate partition;
- lower/fork-moving mass contribution at the front;
- swingarm/linkage contribution through the generalized mass matrix at the rear.

No exact complete wheel mass is claimed yet.

---

## 5. Tire rotational inertia can be bounded physically, but not yet calibrated

The exact tire masses and profile-radius ranges allow a mathematically necessary polar-inertia bound, but not an accurate final tire inertia.

For a body of mass `m` contained between inner and outer radii `r_i` and `r_o`, its polar inertia about the wheel axis must satisfy a geometry-dependent bound. A crude limiting ring bound `I <= m r_o^2` is useful only as a rejection ceiling; realistic tire mass is distributed through sidewalls, bead and tread and requires either dimensional reconstruction or direct pendulum/spin testing.

Physics V2 will therefore **not** turn exact tire mass into exact wheel inertia with a thin-ring assumption.

Instead:

1. tire and wheel component masses constrain feasible inertia;
2. a complete wheel/tire/tube/rotor/sprocket assembly should be measured with a trifilar/bifilar or known-torque spin test;
3. the resulting complete-wheel spin inertia is the state parameter used by the wheel rotational equations.

The direct measurement is simple enough that a broad thin-ring approximation is not justified as a permanent model constant.

---

## 6. Front and rear assembly geometry is better constrained by current parts data

The 2025 CRF450R uses a new front axle and revised chassis/linkage components. OEM fiche data confirm 2025-specific front-wheel/axle identities and the K95-AG0 linkage family.

Representative sources:

- https://www.partzilla.com/catalog/honda/motorcycle/2025/crf450r/front-wheel
- https://www.revzilla.com/oem/honda/2025-honda-crf450r/cushion-arm

This matters because a previous-generation complete front-end or linkage mass-property dataset cannot be treated as exact for the 2025 machine even when spring/travel dimensions look similar.

The 2025 cushion-arm hardware also confirms three principal 12 mm linkage bolt spans (82, 135, and 103.5 mm nominal bolt lengths), but **bolt length is not hardpoint center distance**. It does not solve the linkage kinematics and is not used as such.

---

## 7. Rear-shock hardware envelope is narrower

Suspension-specialist data for 2025-2026 CRF450R confirms the production architecture remains a Showa shock with approximately:

- **16 mm shock shaft**;
- **50 mm main piston**;
- 52 N/mm stock spring.

Source:
https://www.ridejbi.com/2025-2026-honda-crf450r/

A direct-fit Ohlins replacement remains published at approximately 452 mm eye-to-eye and 133 mm stroke; those values stay classified as a **compatible-part envelope**, not OEM Showa dimensions.

The JBI page lists a 240 mm stock-spring free length while Honda setup data list approximately 232.2 mm as the installed spring length. These are not contradictory quantities: free spring length and installed/preloaded spring length must remain separate fields in the V2 parameter schema.

### Consequence

The rear suspension data structure must distinguish at minimum:

- spring free length;
- installed/preloaded spring length at setup;
- shock eye-to-eye length;
- usable shock stroke;
- wheel travel;
- local motion ratio `dx_wheel / dx_shock` through travel.

Only the final item converts the 52 N/mm shock spring into the actual progressive wheel rate.

---

## 8. Public Pro-Link search has reached diminishing returns

A targeted search still did **not** recover a defensible 2025 axle-travel-versus-shock-travel curve or full bell-crank hardpoint drawing.

What is now firmly established:

- 2025 linkage is new and uses a one-piece pullrod;
- stock pullrod center-to-center is about 147.8 mm from specialist measurement;
- stock/compatible shock envelope is about 452 mm eye-to-eye / 133 mm stroke;
- nominal wheel travel is about 310 mm in the frozen production reference;
- average full-stroke wheel/shock displacement ratio is therefore on the order of 2.3, but the local ratio is progressive and cannot be replaced by that average.

The final Gate B path is therefore now explicitly **measurement/photogrammetry**, not indefinite web searching.

Acceptable identification methods:

1. spring-removed physical sweep: record shock length and axle position every 5-10 mm of shock movement;
2. calibrated side-view photogrammetry/CAD reconstruction with visible pivot centers and at least two known scale dimensions;
3. direct hardpoint measurement from a 2025-family chassis/linkage assembly.

Any measured dataset must preserve raw coordinates and uncertainty before fitting a smooth local motion-ratio curve.

---

## 9. Showa damping search result: force curves remain unavailable publicly

Another targeted pass found setup recommendations, click counts, spring data, and qualitative 2025 valving/friction changes, but no credible stock 2025 Showa **force-versus-shaft-velocity dyno plot**.

Honda confirms the force behavior is materially revised for 2025; suspension specialists confirm the body/settings changed. That makes importing 2024 or generic Showa damping curves especially inappropriate.

Relevant sources:

- https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1
- https://www.ridejbi.com/2025-2026-honda-crf450r/
- https://www.keeferinctesting.com/2025-450-mx-baseline-suspension-settings/

### Gate B decision

Public research alone is no longer expected to close the absolute damping curves. If no stock dyno sheet is obtained from a suspension specialist, the initial curve must be **identified from instrumented displacement/velocity/acceleration data** and marked IDENTIFIED with uncertainty.

No `c * v` coefficient will be invented merely to open the gate.

---

## 10. Motorcycle tire lateral-force research strengthens the identification requirement

Motorcycle tire bench research consistently shows that:

- camber-generated lateral force can be as important as or larger than sideslip force;
- force depends on vertical load;
- side-slip force is not simply proportional to load;
- aligning/twisting moments are material;
- combined longitudinal/lateral operation cannot be represented by independent clamps.

References:

- https://www.research.unipd.it/handle/11577/2486330
- https://www.sciencedirect.com/science/article/abs/pii/S0389430401001138
- https://saemobilus.sae.org/articles/a-two-step-approach-tire-lateral-force-observation-motorcycles-2024-32-0043

These are primarily road-motorcycle methods/data, so their coefficients are not used for MX33 hardpack calibration. Their value is structural: they reinforce that the Physics V2 observation schema needs `Fz`, slip angle, camber, `Fy`, and tire moments rather than a single friction coefficient.

The off-road dataset still has to be identified.

---

## 11. Clutch search result: architecture is known, exact capacity still requires identification

The OEM clutch spring `22401-MKE-AF0` remains used across the current CRF450R family and the pack contains six springs. Public OEM listings provide identity and quantity but do not publish the spring rate or installed clamp load.

Representative sources:

- https://www.partzilla.com/product/honda/22401-MKE-AF0
- https://www.fremonthondakawasaki.com/Parts/Parts-Finder/honda/motorcycles/2025/crf450rwe-ac/clutch/6117a1d9-8745-40b2-a81e-e6259fe20e4c

Therefore the previous `>105 N m` clutch-shaft capacity result remains a **hard lower bound**, not a calibration.

Gate C can be closed by either:

- spring-load + effective friction-radius + coefficient measurement;
- controlled clutch-slip test with known engine/wheel acceleration and identified rotational inertias;
- direct clutch torque test.

The slip law must be bounded and continuous; an arbitrary speed-based pseudo-clutch is not retained.

---

## 12. Gate status after Pass 06

### Gate A — geometry/mass

**NOT READY, but reference configuration and several bounds are now frozen.**

Closed/narrowed:

- production US reference specification selected;
- wheelbase/rake/trail/travel nominal set frozen;
- same-generation static longitudinal-load prior retained;
- exact tire mass ranges now constrain wheel/unsprung estimates;
- measurement route for complete inertia remains established.

Still high sensitivity:

- bike-only CG height;
- pitch/roll/yaw inertia tensor;
- complete wheel spin inertias;
- steering and swingarm/linkage inertias unless later shown low sensitivity.

### Gate B — suspension

**NOT READY; web research has effectively reached the measurement boundary.**

Still high sensitivity:

- 2025 Pro-Link local motion-ratio curve;
- stock Showa force-vs-velocity curves;
- end-stop/friction absolute calibration.

### Gate C — powertrain

**PARTIAL.**

Closed/narrowed:

- ratios;
- dyno peak anchors and coarse curve;
- clutch architecture;
- clutch capacity lower bound;
- engine-inertia differential-test method.

Still high sensitivity:

- equivalent engine inertia;
- engine-braking/loss map;
- clutch torque/slip law;
- final dense dyno trace.

### Gate D — tire/contact

**NOT READY; calibration remains the largest blocker.**

Closed/narrowed:

- exact tire identities/sizes;
- front geometric radius band;
- exact-rear tire mass band;
- motocross-specific load-dependent vertical-stiffness shape prior;
- hardpack observation schema;
- force-model architecture and combined-slip requirement.

Still high sensitivity:

- MX33 loaded/effective radius;
- absolute vertical `Fz(delta)` curve and damping;
- hardpack `Fx(kappa,Fz)`;
- hardpack `Fy(alpha,gamma,Fz)` and moment behavior;
- combined slip;
- relaxation/transient scales.

### Gate N — integrator

**BENCHMARK-READY.**

No further literature survey is required before the numerical benchmark is built. Semi-implicit/substepped and linear-implicit stiff-force methods remain the two serious candidates.

---

## 13. What happens next

Research effort now shifts away from broad web searching and toward **closing the minimum initial calibration set**.

Next actions:

1. define explicit numerical prior intervals for the high-sensitivity mass properties from same-class literature, without selecting a preferred point;
2. define the exact physical/photogrammetric Pro-Link measurement sheet needed to generate the local motion-ratio curve;
3. define the minimum sensor/telemetry set needed to identify Showa damping and MX33 hardpack force surfaces;
4. formalize the engine/flywheel and clutch identification tests into equations and required measurements;
5. produce a final `CODING_GATE_CHECKLIST` in which every runtime-required parameter is either published/measured/identified or has an approved bounded-low-sensitivity interval;
6. only then begin the no-assist Physics V2 runtime rewrite.

**The coding gate remains closed after Pass 06.** The key progress is that the remaining blockers are now specific measurements/identification datasets, and broad public-source research is no longer being mistaken for a path to exact values that manufacturers do not publish.
