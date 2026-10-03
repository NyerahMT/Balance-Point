# Physics V2 Research Pass 04

Status: **research only — no runtime physics changed.**

This pass removes several source ambiguities, tightens the 2025 CRF450R geometry/suspension register, narrows the tyre-data problem, and substantially narrows the numerical-integration decision. It does **not** open the runtime coding gate yet.

Reference machine remains the **stock 2025 US Honda CRF450R**.

---

## 1. Coding gate status

**CLOSED.**

The remaining blockers are now concentrated in measured/calibrated quantities rather than model architecture:

1. CRF450R-specific CG height and inertia set;
2. 2025 Pro-Link local motion-ratio curve or equivalent hardpoint set;
3. stock Showa compression/rebound/end-stop force laws;
4. MX33 hardpack force surfaces and vertical/relaxation properties;
5. engine equivalent inertia, engine-braking/loss map, and clutch slip/capacity calibration.

The numerical-solver gate is no longer conceptually open-ended: a linear-implicit method is now the preferred stiff-system candidate, but it still has to beat the simpler fixed-step alternatives in the Balance Point validation harness before selection.

---

## 2. 2025 CRF450R versus CRF450RX suspension configuration cleanup

A source ambiguity in prior searches is now resolved. The 2025 **CRF450R/RWE** and **CRF450RX** do not use the same standard spring rates.

For the stock CRF450R/RWE, 2025 manual data give:

| Item | Soft | Standard | Stiff |
|---|---:|---:|---:|
| Fork spring, each leg | 4.8 N/mm | **5.0 N/mm** | 5.2 N/mm |
| Shock spring | 50 N/mm | **52 N/mm** | 54 N/mm |

For the CRF450RX, the corresponding standard values are 4.8 N/mm fork and 50 N/mm shock. Those RX values must not be mixed into the R calibration.

The CRF450R standard shock spring installed length is **232.2 mm**. For the standard 52 N/mm spring, one adjuster turn changes installed length by 1.5 mm and preload by approximately 84 N according to the manual data.

Manual transcription/reference:
https://www.notice-facile.com/en/manual/1411421/honda%2Bcrf450rwe-2025

This confirms that the Physics V2 CRF450R register should retain **5.0 N/mm per fork spring** and **52 N/mm rear shock spring**.

### 2.1 Front spring rate derivation remains valid

Two 5.0 N/mm fork springs act in parallel along the fork axis. Ignoring air-spring/end-stroke effects and friction, the bare coil contribution is therefore:

`K_fork,coil = 2 * 5.0 N/mm = 10.0 N/mm = 10,000 N/m`

This is a **DERIVED** fork-axis coil rate, not the complete front suspension force law.

---

## 3. 2025 Showa force-law implications are stronger than before

Honda Europe gives unusually useful qualitative engineering information for the 25YM CRF450R suspension:

- Showa 49 mm USD coil fork;
- 310 mm fork stroke;
- revised oil specification intended to improve low-speed damping;
- **200% more friction force throughout the fork stroke** relative to the prior design target;
- consistent damping force from top to bottom;
- 16 compression and 16 rebound adjustment positions;
- rear shock remade for more uniform control through the stroke;
- rear-shock friction reduced near the bottom of travel;
- revised Pro-Link structure is 11% more rigid, with leverage ratio and axle travel optimized.

Honda source:
https://hondanews.eu/eu/fi/motorcycles/media/pressreleases/475110/25ym-honda-crf450r-1

The 2025 test literature also reports a **310 cc fork oil quantity** for the standard setup and retains the 5.0 N/mm fork spring / 52 N/mm shock spring configuration.

Technical cross-check:
https://www.motocrossactionmag.com/es/prueba-de-carrera-mxa-2025-honda-crf450/

### 3.1 Consequence for the V2 front suspension equation

The final front force law cannot be represented as only `kx + cv`. At minimum it must support:

`F_fork = F_coil(x) + F_air/endstroke(x) + F_comp(v,x) + F_rebound(v,x) + F_friction(v,x)`

The Honda friction statement is not an absolute force measurement, so no friction coefficient is inferred from the quoted 200% change. It does, however, establish that friction is deliberately material in the 2025 design and should not be silently set to zero in the final calibration.

The 310 cc oil quantity likewise is not directly converted into an air-spring coefficient without chamber geometry and oil-height data.

---

## 4. Steering geometry is narrower: 22 mm clamp offset is a strong proxy

Honda's published US/European rake, trail, and wheelbase remain the authoritative whole-bike geometry values. A 2025 CRF450R technical source reports that triple-clamp offset remains **22 mm** for 2025.

Dirt Rider reference:
https://www.dirtrider.com/dirt-bikes/2025-honda-crf450r-technical-information/

Xtrig independently lists its direct-fit 2021-2027 CRF450R ROCS Tech clamp as **22 mm original offset**, providing a compatible-part cross-check rather than OEM dimensional authority:
https://www.xtrig-shop.com/product-selection/Triple-clamp

Physics V2 classification:

- 22 mm steering/triple-clamp offset: **HIGH-CONFIDENCE SECONDARY / COMPATIBLE-PART CROSS-CHECK**;
- not promoted to Honda-published status unless a factory dimensional source is recovered.

This meaningfully narrows the front kinematic skeleton, although full steering-assembly mass/inertia and exact fork/axle hardpoints are still required for final calibration.

---

## 5. MX33 front geometry is now directly catalog-bounded

A Dunlop motorcycle tire product guide provides a direct MX33 entry for the CRF's front size:

**Dunlop Geomax MX33 front 80/100-21 51M**

- standard rim: 1.60 in;
- published section width: **91 mm**;
- published overall diameter: **710 mm**;
- corresponding catalog unloaded radius: **0.3550 m**.

Dunlop guide:
https://dunloptire.co.th/wp-content/uploads/2022/02/MC-Catalog-2022.pdf

A later Dunlop databook mirror gives 27.76 in overall diameter for an MX33 80/100-21, equivalent to about 705.1 mm diameter / 0.3526 m radius. Rather than choosing one value by preference, Physics V2 records a **catalog unloaded-radius band of approximately 0.3526-0.3550 m** until the actual tire is measured at the reference pressure and rim.

The spread can reflect market specification, measurement convention, tire revision, or publication differences. It is too small to justify pretending one source is exact for the game reference bike.

### 5.1 Important rear-data correction

The same 2022 Dunlop PDF contains a 120/80-19 row with 696 mm overall diameter, but that table is labeled **MX3S**, not MX33. Therefore **696 mm must not be entered as an MX33 rear dimension**.

Current Dunlop Europe confirms that `120/80-19 63M TT` is a valid MX33 rear size and product, but the currently accessible page does not publish its overall diameter:
https://www.dunlop.eu/nl_be/motorcycle/tires/geomax-mx33--gx-mx33.html

Until a direct MX33 rear dimensional source is recovered, the same-size motocross data from the prior pass remain geometry priors only. The rear's final unloaded/effective radius remains IDENTIFY.

### 5.2 Runtime architecture consequence remains locked

Physics V2 requires **separate front and rear radii** and distinguishes:

- profile/geometric radius for terrain contact geometry;
- effective rolling radius for `Vx / omega` slip calculations;
- loaded radius for vertical-deflection characterization.

One global `WHEEL_RADIUS` is not acceptable in V2.

---

## 6. Tyre vertical stiffness: a useful prior was explicitly non-measured

Felipe Vasquez's off-road motorcycle thesis contains a simplified tyre vertical model using approximately:

- `k_t = 150,000 N/m`;
- `c_t = 100 Ns/m` in the listed simulation table.

However, the thesis explicitly states that **tyre parameters were unavailable and were estimated from typical vehicle values**. Therefore these numbers are not instrumented motocross-tyre data and must not be promoted into the MX33 parameter register.

Research reference:
https://eprints.soton.ac.uk/448147/

This is an important negative result: a value can appear in credible off-road literature and still be inappropriate as a measured CRF/MX33 constant. For Physics V2 it is retained only as an **order-of-magnitude numerical prior** for sensitivity/stability testing.

### 6.1 Tire Gate D remains genuinely experimental

Exact public MX33 data are still missing for:

- vertical load-deflection at ~15 psi;
- radial damping;
- `Fx(Fz, kappa)` on firm dirt/hardpack;
- `Fy(Fz, alpha, gamma)`;
- aligning/contact moment;
- combined-slip coupling;
- relaxation length/time versus load/slip/speed.

The model interface is settled; the calibration is not.

---

## 7. Hardpack identification dataset schema

To prevent future 'feel tuning' from being mislabeled as tire physics, the first identified hardpack dataset should store measurements as observations rather than directly storing a fitted equation.

Suggested canonical row schema:

| Field | Meaning |
|---|---|
| `surface_id` | defined hardpack test surface |
| `tire_id` | front/rear + exact tire/configuration |
| `pressure_pa` | cold/recorded inflation pressure |
| `speed_mps` | contact-frame forward speed |
| `fz_n` | estimated/measured normal load |
| `kappa` | longitudinal slip ratio |
| `alpha_rad` | slip angle |
| `camber_rad` | camber angle |
| `fx_n` | longitudinal contact force |
| `fy_n` | lateral contact force |
| `mz_nm` | aligning/contact moment if identifiable |
| `radius_effective_m` | free-rolling/effective radius state |
| `source_method` | direct rig / inverse dynamics / derived |
| `sigma_*` | uncertainty fields for the measured/derived outputs |

The fitted runtime representation can later be a constrained spline/table or another identified nonlinear force surface. The raw/identified observation layer must remain separate so a better fitting model can be substituted without losing provenance.

---

## 8. Numerical integrator gate is substantially narrowed

Recent real-time multibody research provides a strong reason not to assume an explicit large-step solver will remain adequate once nonlinear tire compliance, suspension force laws, and contact stiffness are introduced.

A 2024/2025 Multibody System Dynamics study of stiff vehicle suspensions reports that linear-implicit integration can provide the stability of an implicit treatment with deterministic real-time computational effort. It used the two-stage L-stable LSRT2 scheme as an effective real-time/accuracy compromise for stiff nonlinear suspension models.

Primary reference:
https://link.springer.com/article/10.1007/s11044-024-09984-2

A systematic mapping of real-time road-vehicle multibody methods similarly concludes that linear-implicit schemes are well suited to elastokinematic/stiff real-time systems because they combine high numerical stability with approximately fixed computational effort without iterative nonlinear solves.

Reference:
https://link.springer.com/article/10.1007/s11044-025-10081-1

### 8.1 Physics V2 numerical decision

The selection test is now reduced to two serious implementation families:

1. **semi-implicit fixed-step baseline**, with justified local substepping if required;
2. **linear-implicit treatment of stiff force terms**, with LSRT2/Rosenbrock-style methods as the leading literature-backed family.

No specific paper's integrator is automatically copied. The Balance Point plant has different topology and cost constraints.

At 120 Hz outer tick, candidate methods must be compared on identical cases for:

- static sag convergence;
- high-energy landing/full-travel response;
- tire vertical-compliance response;
- combined-slip transient response;
- no-force airborne momentum conservation;
- timestep-halving convergence;
- deterministic mobile CPU cost;
- NaN/Inf and energy-growth behavior.

If semi-implicit stepping passes these tests with reasonable substepping, complexity wins and it should be retained. If it fails stiffness/convergence criteria, linear-implicit becomes the preferred production path.

This turns Gate N from an open research question into a **benchmark decision**.

---

## 9. Updated Gate status

### Gate A — geometry/mass properties

**NOT READY, but geometry is tighter.**

Newly narrowed:
- 22 mm triple-clamp/fork offset has strong independent secondary support;
- front MX33 catalog profile dimensions now exist.

Still blocking:
- CRF-specific CG height;
- roll/pitch/yaw inertia tensor;
- steering assembly inertia;
- complete wheel inertias;
- swingarm/linkage inertia.

### Gate B — suspension

**NOT READY.**

Confirmed strongly:
- CRF450R = 5.0 N/mm fork spring each leg;
- CRF450R = 52 N/mm rear spring;
- shock spring installed length 232.2 mm;
- 2025 fork/shock friction and damping behavior are deliberately nontrivial;
- Pro-Link ratio changed for 2025.

Still blocking:
- local axle-to-shock motion-ratio curve / hardpoints;
- absolute compression/rebound force-vs-velocity curves;
- friction-force magnitude;
- end-stop/air-spring force law;
- moving/unsprung mass calibration.

### Gate C — powertrain

**PARTIALLY READY, unchanged materially from Pass 02.**

Still blocking:
- validated dense dyno trace;
- engine equivalent rotational inertia;
- engine braking/loss torque;
- clutch torque/slip law and clutch inertia;
- drivetrain loss characterization.

### Gate D — tire/contact

**NOT READY / highest calibration uncertainty.**

Improved:
- direct MX33 front catalog geometry recovered;
- misleading MX3S rear dimension explicitly rejected;
- vertical-stiffness literature prior reclassified correctly as estimated rather than measured;
- hardpack observation schema defined.

Still blocking:
- actual MX33 rear dimensions/effective radius;
- vertical stiffness/damping at reference pressure/load;
- hardpack steady-state `Fx/Fy/Mz` surfaces;
- combined slip;
- relaxation/transient calibration.

### Gate N — numerical integration

**BENCHMARK-READY, not production-selected.**

The architecture should compare semi-implicit/substepped stepping against a linear-implicit stiff-force treatment. No further broad solver survey is required before building the validation harness.

---

## 10. Next research actions

1. Recover or bound 2025 CRF450R mass properties using same-class literature, physical component dimensions, and ISO-style identification procedures without promoting priors to measurements.
2. Continue searching for 2025 Pro-Link hardpoints or a measured wheel-travel-versus-shock-travel curve.
3. Search suspension specialists for actual stock 2025 Showa dyno plots; if none are public, formalize the inverse-identification experiment and required sensor channels.
4. Recover an exact MX33 120/80-19 dimensional source and any motocross tire radial-stiffness/force-rig data that can legitimately bound Gate D.
5. Convert the coarse 2025 dyno trace into a pixel-archived, uncertainty-tagged dataset.
6. Close engine-inertia, engine-braking and clutch-slip identification bounds.
7. When Gates A-D have defensible initial parameter sets, open the coding gate and implement the no-assist physical plant before any gameplay/controller tuning.

**Runtime coding remains intentionally blocked after Pass 04.** The key improvement in this pass is that several plausible-looking but incorrect shortcuts are now explicitly excluded, while Gate N is narrow enough to become an engineering benchmark rather than another research branch.
