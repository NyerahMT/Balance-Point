# Physics V2 Research Pass 08

Status: **research / gate tightening only — no runtime physics changed.**

This pass corrects two earlier source-handling ambiguities, closes more of the static geometry/mass register, and quantifies a useful powertrain uncertainty bound. The reference runtime configuration remains the **late-2025 US Honda CRF450R production specification** frozen in Pass 06.

---

## 1. Coding gate status

**CLOSED after Pass 08.**

No new mathematical subsystem is missing. The remaining blockers are still high-sensitivity calibration data:

- bike CG height and principal inertias;
- 2025 Pro-Link local motion-ratio curve;
- stock 2025 Showa force laws;
- MX33 hardpack force/relaxation calibration;
- engine equivalent inertia, braking/loss map and clutch slip/capacity.

This pass does, however, remove avoidable uncertainty from the longitudinal CG prior and swingarm geometry.

---

## 2. Longitudinal CG prior is now derived within one consistent Honda configuration

Honda Europe publishes the 25YM CRF450R as:

- wet mass: **113 kg**;
- wheelbase: **1.483 m**;
- static front/rear weight split: **48.7 / 51.3 percent**.

Primary source:
https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

For a level static motorcycle with rear contact patch at `x = 0` and front contact patch at `x = L`, force equilibrium gives:

`x_CG = (F_front / W) * L`.

Using the manufacturer values from the **same European configuration**:

- total weight `W = 113 * 9.80665 = 1108.15 N`;
- front static load `F_front = 0.487 * W = 539.67 N`;
- rear static load `F_rear = 0.513 * W = 568.48 N`;
- longitudinal CG `x_CG = 0.487 * 1.483 = 0.722221 m` from the rear contact patch;
- distance from the front contact patch = `1.483 - 0.722221 = 0.760779 m`.

Classification: **DERIVED from same-configuration manufacturer data**.

### Correction to Pass 06

Pass 06 combined the European 48.7/51.3 split with the frozen US wheelbase to produce a transported US prior. That remains usable only as a cross-configuration prior. The cleaner datum is the internally consistent European derivation above.

For the frozen US runtime configuration, the exact longitudinal CG remains `IDENTIFY` unless the European load split is explicitly accepted as a same-generation transfer with uncertainty. Do not silently mix the two configurations.

Vertical CG remains unresolved and high sensitivity.

---

## 3. Swingarm geometry correction: the 2025 swingarm itself is manufacturer-stated unchanged

Earlier passes correctly rejected older **linkage** geometry because Honda changed the 2025 Pro-Link structure and leverage ratio. However, a part-number change on the swingarm assembly was too weak a reason to reject the swingarm geometry itself.

Honda Europe explicitly states for the 25YM CRF450R:

- the aluminum swingarm is **585.2 mm long**;
- the swingarm is **unchanged**;
- the Pro-Link structure and leverage ratio are revised.

Primary source:
https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

### Physics V2 consequence

The distinction is now:

- **Swingarm pivot-to-axle geometry / 585.2 mm length:** manufacturer-backed and may reuse prior-generation swingarm geometry only where the unchanged statement and coordinates are actually consistent.
- **Bell-crank / cushion-arm / pullrod / shock-linkage geometry:** 2025-specific and may **not** reuse the 2021-2024 motion-ratio curve as final data.

This narrows Gate B because the rear axle path is constrained by a known swingarm length; the unresolved kinematics are concentrated in the 2025 linkage and shock attachment geometry.

The changed 2025 swingarm assembly part number is retained as a parts/configuration fact, but it is no longer treated as proof that the swingarm kinematic length changed.

---

## 4. Rear axle path can now be parameterized without inventing a wheel-rate curve

With a rigid swingarm length `L_s = 0.5852 m`, rear axle position relative to the swingarm pivot is a circular arc:

`r_axle = [L_s cos(theta_s), 0, L_s sin(theta_s)]`

in the chosen swingarm plane/reference convention.

This does **not** determine the Pro-Link wheel/shock motion ratio because the shock, cushion arm and pullrod geometry remain unresolved.

It does mean the V2 geometry model no longer needs an arbitrary vertical rear-wheel slider. The rear wheel can be attached kinematically to the physical swingarm rotational DOF from the beginning once the pivot/reference attitude is fixed.

---

## 5. Showa hardware register is tighter, force law still unresolved

JBI's 2025-2026 CRF450R data provide useful same-model hardware/setup dimensions:

- stock fork spring: **5.0 N/mm per leg**;
- fork spring free length: **500 mm**;
- stock rear spring: **52 N/mm**;
- rear spring free length: **240 mm**;
- Showa rear shock shaft: **16 mm**;
- Showa main piston: **50 mm**.

Source:
https://www.ridejbi.com/2025-2026-honda-crf450r/

Honda setup data separately give an installed/preloaded rear spring length around 232.2 mm, so the free and installed lengths remain distinct fields.

No credible public stock-2025 force-versus-velocity dyno curves were recovered. Therefore absolute compression/rebound force laws remain `IDENTIFY` and are not replaced with click-count heuristics or a single `c*v` coefficient.

---

## 6. Powertrain uncertainty gets a useful same-generation replicate bound

Dirt Rider measured the 2025 CRF450R at:

- **51.1 hp** peak rear-wheel power;
- **32.9 lb-ft** peak rear-wheel torque.

Source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

Dirt Rider later describes the 2026 CRF450R as left essentially unchanged after the 2025 redesign and measured:

- **52.6 hp** peak rear-wheel power;
- **33.7 lb-ft** peak rear-wheel torque.

Source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2026/

Relative to the 2025 result, those peak differences are approximately:

- power: `(52.6 - 51.1) / 51.1 = 2.94%`;
- torque: `(33.7 - 32.9) / 32.9 = 2.43%`.

These are **not** proof of a formal dyno uncertainty distribution; they include bike-to-bike, environment, tire/test and run-condition variation. They are useful evidence that the V2 engine curve should not be overfit to sub-percent precision from one published chart.

### Gate-C consequence

The dense 2025 dyno trace should carry realistic uncertainty. Exact engine inertia, engine-braking and clutch dynamics remain separate identification problems and are not solved by steady-state dyno replication.

---

## 7. Tire gate: public literature still supports identification, not direct MX33 coefficients

The strongest open off-road work remains Vasquez et al.'s inverse-dynamics approach, which estimates motocross contact forces from kinematic measurements and explicitly handles large suspension motion and wheel detachment.

Primary source:
https://doi.org/10.1016/j.ymssp.2020.107228

The experimental work demonstrates that force estimation from an instrumented motocross bike is feasible. It does **not** publish an MX33 hardpack `Fx/Fy` coefficient set that can simply be copied into Balance Point.

Dunlop confirms the exact MX33 product identities and rim sizes, including the 120/80-19 rear on a 2.15-inch rim, but current public product pages still do not provide the required force/slip or radial-stiffness data.

Dunlop reference:
https://www.dunlop.eu/da_dk/motorcycle/tires/geomax-mx33--gx-mx33.html

Gate D therefore remains a measurement/system-identification problem, not a literature-coefficient problem.

---

## 8. Public-data ceiling is now explicit

After eight passes, broad web research is no longer the limiting factor for the critical remaining values.

The following quantities have repeatedly failed to appear as credible same-configuration public numerical datasets:

- 2025 CRF450R bike CG height and full principal inertia set;
- 2025 Pro-Link local motion-ratio curve;
- stock 2025 Showa fork/shock force-versus-velocity curves;
- Dunlop MX33 hardpack longitudinal/lateral/combined-slip force surfaces;
- MX33 relaxation parameters;
- CRF450R equivalent engine inertia and quantitative engine-braking map;
- stock clutch torque-slip law.

Further generic searching should only continue when a new source lead exists. Otherwise the project should execute the measurement/identification procedures already specified in Pass 07 and `PHYSICS_V2_CALIBRATION_DATASET_SPEC.md`.

---

## 9. Gate status after Pass 08

### Gate A — geometry/mass

**NOT READY.**

Improved:
- internally consistent OEM longitudinal-CG prior: 0.722221 m from rear contact for the European 25YM configuration;
- swingarm length/arc geometry is manufacturer-backed.

Still blocking:
- US-reference CG height;
- pitch/roll/yaw inertias;
- component inertias / hardpoint coordinates required by the full 3D plant.

### Gate B — suspension

**NOT READY, but rear-wheel kinematics are less ambiguous.**

Improved:
- 0.5852 m swingarm length manufacturer-backed;
- old swingarm geometry is no longer blanket-rejected;
- linkage-specific old geometry remains prohibited;
- spring/hardware dimensions tightened.

Still blocking:
- 2025 local Pro-Link motion ratio;
- absolute front/rear damping force laws;
- bottoming/friction calibration.

### Gate C — powertrain

**PARTIAL.**

Improved:
- same-generation 2025/2026 dyno comparison provides a useful few-percent empirical repeatability/configuration bound for peak output.

Still blocking:
- dense 2025 curve with uncertainty;
- equivalent engine inertia;
- loss/braking map;
- clutch torque/slip law.

### Gate D — tire/contact

**NOT READY / largest blocker.**

No defensible public same-tire hardpack force surface was found. The instrumented-riding identification campaign remains the required path.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

The important result of Pass 08 is not another pile of adjacent-bike numbers. It is a cleaner physical geometry register and a hard conclusion about where public information ends. The next gate-closing work is now experimental/identification work, not broad literature collection.
