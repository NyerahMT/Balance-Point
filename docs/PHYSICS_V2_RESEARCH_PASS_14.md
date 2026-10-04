# Physics V2 Research Pass 14 — Drivetrain Closure

Status: **Gate-C engineering closure for the first Physics V2 plant. No current runtime physics changed.**

This pass applies the same policy already accepted for Gates A and B: unavailable factory values may enter the first plant as `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` when they are physically derived, uncertainty-bounded, centralized, replaceable, and exercised by validation.

## 1. Published / measured anchors

Honda documentation establishes the CRF450R drivetrain architecture and ratios:

- idle speed: **2,000 ±100 rpm**;
- primary reduction: **2.357**;
- gears: **2.133 / 1.706 / 1.421 / 1.211 / 1.043**;
- final reduction: **3.769 = 49/13**;
- wet multiplate hydraulic clutch;
- current-generation clutch: **8 plates / 6 springs**.

Honda sources:

- https://cdn.powersports.honda.com/documentum/MWOM/ml.remawmom.2022_crf450r-rx-rwe_owner_s_manual.pdf
- https://hondanews.com/en-US/releases/release-70527d767f112a34ec2e42bfb5060b28-2025-honda-crf450r

Dirt Rider measured the 2025 CRF450R at the rear wheel on a Dynojet 250i:

- **32.9 lb-ft / 44.61 N m at 6,900 rpm**;
- **51.1 hp at 9,600 rpm**.

Source:

- https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

The 2025 comparison methodology used a Dunlop D404 rear tire for dyno compatibility. Therefore the curve is a valid steady-state rear-wheel power target but is not used for MX33 transient inertia or tire calibration.

## 2. The existing game drivetrain is not carried into V2

The current `MotorcycleDrivetrain` uses a generic primary ratio and generic gearset, plus target-RPM interpolation. Those choices remain valid only for the legacy plant.

Physics V2 uses the Honda ratios above and integrates engine angular speed from torque balance:

`I_e * omega_dot = T_combustion - T_loss - T_clutch`

RPM is therefore an actual state. It is not blended toward a wheel-speed-derived target.

## 3. First-plant torque curve

`tools/physics_v2_drivetrain_estimate.py` contains a bounded reconstruction of the published Dirt Rider chart.

Hard anchors are the measured values at 6,900 rpm and 9,600 rpm. Interior chart points are `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` with a ±6% shape envelope and remain replaceable.

A nominal crank torque curve is obtained from the rear-wheel-equivalent dyno curve using a first-plant total drivetrain efficiency of **0.95**, interval **0.93–0.97**.

This is consistent with published motorcycle chain-efficiency work showing a properly configured chain drive in the high-90% range while leaving room for primary, gearbox, bearing, oil-churning and dirty-track losses.

The measured 44.61 N m peak therefore corresponds to a nominal pre-loss engine torque near **46.96 N m** at the 0.95 efficiency point.

## 4. Equivalent engine inertia

Exact current-CRF crank/flywheel equivalent inertia is not public.

Honda has historically published a CRF450R ACG flywheel inertia near **8.1 kg·cm² = 0.00081 kg·m²** after a 4.5% increase, demonstrating the correct physical order for one rotor component. Motorcycle performance-model literature commonly uses crank inertias on the order of several `10^-3 kg m²`.

Useful cross-checks:

- Honda 2015 CRF450R technical release: https://www.honda.co.uk/motorcycles/ride-with-us/news/new-crf450r-for-2015.html
- Tony Foale motorcycle performance manual example: https://motochassis.com/DocumentFiles/PerformanceManual.pdf

First-plant crank-referenced equivalent inertia:

- nominal: **0.0065 kg m²**;
- interval: **0.00325–0.00975 kg m²**.

The ±50% interval is intentionally conservative. This value is never tuned to make throttle pickup feel better; sensitivity is validated directly against RPM flare, clutch launch, shift recovery, engine braking, and airborne wheel-reaction response.

## 5. Closed-throttle loss / engine braking

A continuous replaceable crank-loss map is used instead of direct chassis drag:

| rpm | nominal loss torque |
|---:|---:|
| 2,000 | 2.5 N m |
| 4,000 | 4.1 N m |
| 6,000 | 6.2 N m |
| 8,000 | 8.6 N m |
| 10,000 | 11.0 N m |
| 11,500 | 12.7 N m |

Intermediate points are in the estimator. The entire map carries **±40% uncertainty** for the first plant.

This map represents pumping, friction, oil and accessory losses at closed throttle. It acts on the engine rotational state. It is not allowed to become an arbitrary negative rear-wheel force.

## 6. Clutch torque/slip law

At nominal peak engine torque, the Honda primary ratio requires approximately:

`46.96 * 2.357 = 110.7 N m`

at the clutch shaft before additional transient reserve.

The first-plant fully engaged clutch capacity is therefore:

- nominal: **150 N m**;
- interval: **115–190 N m**.

This is consistent with Honda's eight-plate, six-spring design and Honda's historical statement that the redesigned eight-plate clutch dramatically reduced peak-power slip.

The first continuous clutch law is:

`T_clutch = engagement * T_capacity * tanh(delta_omega / omega_scale)`

with:

- nominal slip scale: **20 rad/s**;
- interval: **10–35 rad/s**.

The clutch applies equal-and-opposite torque to engine and gearbox sides. Launch and shift behavior therefore emerge from slip, inertia and available engine torque rather than a wheel-force multiplier.

## 7. Shift and limiter rules

For the first V2 plant:

- gear selection changes the physical ratio;
- torque interruption is represented through combustion/clutch state, not direct rear-wheel force scaling;
- limiter nominal: **11,500 rpm**, uncertainty interval **11,000–12,000 rpm** until a stronger same-generation source is available;
- the limiter may cut combustion torque, but it may not directly overwrite engine RPM.

## 8. Gate-C verdict

**Gate C: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

The first plant now has:

- correct Honda ratios;
- measured dyno anchors and a bounded torque reconstruction;
- explicit engine rotational inertia;
- explicit closed-throttle loss map;
- continuous clutch torque/slip law and capacity bound;
- explicit drivetrain efficiency/loss interval;
- a rotational-state architecture that forbids target-RPM control.

The entire provisional set is reproduced by:

`tools/physics_v2_drivetrain_estimate.py`

No value is authorized as a permanent Honda measurement. Better data replaces the centralized parameter without changing the physical equations.

**Next blocking subsystem: Gate D — MX33 tire/contact physics.**
