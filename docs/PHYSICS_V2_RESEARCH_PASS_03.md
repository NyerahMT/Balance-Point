# Physics V2 Research Pass 03

Status: **research only — no runtime physics changed.**

This pass focuses on the two remaining high-risk areas that determine whether the no-assist Physics V2 plant can be coded without inventing constants: motorcycle mass properties and off-road tyre/contact behaviour.

The reference machine remains the stock 2025 US Honda CRF450R.

---

## 1. Coding gate status after this pass

**Not yet ready for the runtime rewrite.**

The model architecture is sufficiently defined, but three calibration blockers remain material:

1. the 2025 Pro-Link local motion-ratio curve / hardpoints;
2. CRF450R-specific mass properties, especially CG height and principal inertias;
3. an identified hardpack tyre-force baseline, especially lateral/camber/combined-slip behaviour.

This pass narrows those uncertainties and defines the exact identification routes. It does not convert proxy data into CRF450R constants.

---

## 2. Off-road motorcycle mass-property evidence

A useful same-class research prior exists in Felipe Vasquez's off-road motorcycle work. The virtual motocross experiment used the following motorcycle-side values:

| Quantity | Literature virtual-motorcycle value | Physics V2 use |
|---|---:|---|
| Chassis mass | 81 kg | same-class prior only |
| Rear wheel mass | 18 kg | same-class prior only |
| Front wheel mass | 12 kg | same-class prior only |
| Chassis pitch inertia | 21 kg m^2 | same-class prior only |
| Rear wheel spin inertia | 0.7 kg m^2 | same-class prior only |
| Front wheel spin inertia | 0.5 kg m^2 | same-class prior only |
| Swingarm inertia | 0.8 kg m^2 | same-class prior only |
| Wheelbase | 1.35 m | model geometry, not CRF geometry |
| Chassis CoM height | 0.59 m | same-class prior only |
| Chassis CoM from rear wheel | 0.77 m | same-class prior only |
| Swingarm length | 0.59 m | notably close to CRF 0.5852 m |
| Front/rear nominal wheel radius | 0.30 m | model value only |

Primary source:
https://eprints.soton.ac.uk/448147/

The important result is not that these numbers are "the CRF values". They are not. The result is that peer-reviewed off-road motorcycle modelling gives us physically credible **search bounds and sanity checks** for the eventual CRF identification.

### 2.1 What is still prohibited

The following are still UNKNOWN for the stock 2025 CRF450R and must not be copied from the Vasquez virtual bike:

- bike-only CG height;
- chassis/body roll inertia;
- chassis/body pitch inertia;
- chassis/body yaw inertia;
- products of inertia if material;
- front/rear complete wheel rotational inertia;
- steering assembly inertia about the steering axis;
- swingarm/linkage inertia.

### 2.2 Identification route is now locked

If Honda does not publish these values, Physics V2 will use documented physical identification rather than a tuned scalar.

- CG height: ISO 9130-style static/tilt measurement.
- Whole-bike principal inertias: ISO 9129-style pendulum/bifilar/trifilar measurement.
- Wheel inertia: complete wheel/tire/tube/brake assembly pendulum or known-torque angular-acceleration test.
- Steering inertia: assembled steering/fork/front-wheel pendulum measurement or measured component reconstruction.
- Swingarm/linkage inertia: pendulum measurement or CAD/scale reconstruction with uncertainty recorded.

The same-class literature values above are used only to reject nonsensical identified results and to set safe parameter-search ranges.

---

## 3. CRF450R tyre geometry is narrower than it was

Honda specifies:

- front: Dunlop Geomax MX33 80/100-21, tube type;
- rear: Dunlop Geomax MX33 120/80-19, tube type.

Honda source:
https://hondanews.com/en-US/powersports/releases/release-70527d767f112a34ec2e42bfb506153d-2025-honda-crf450r-specifications

The tyre-size code alone gives nominal carcass radii of roughly 0.3467 m front and 0.3373 m rear, but these exclude real tread/profile construction and therefore are not acceptable as actual unloaded radii.

### 3.1 Same-size dimensional cross-checks

Public same-size motocross tyre data provide useful geometric bounds:

- Dunlop MX34 80/100-21: 27.95 in overall diameter -> about **0.3550 m unloaded radius**.
- Dunlop MX34 120/80-19: 27.44 in overall diameter -> about **0.3485 m unloaded radius**.
- Hoosier 80/100-21 MX tyre: approximately 88.0 in circumference -> about **0.3557 m radius**.
- Multiple 120/80-19 motocross tyres publicly cluster around roughly 27.3-27.5 in overall diameter -> about **0.347-0.349 m radius**.

References:
- https://dmt.exults.com/tire-line/geomax-mx34/
- https://shop.hoosiertire.com/racing-tires/07113-80-100-21/

These are **geometry priors**, not MX33 measurements. They are strong enough to reject the old single-radius architecture and to define separate front/rear radius parameters, but the final MX33 effective rolling radius still requires measurement under load at the stock pressure.

### 3.2 Exact rolling-radius identification

For each wheel, record:

1. unloaded circumference/profile at 15 psi;
2. static loaded radius at several vertical loads;
3. free-rolling effective radius from `r_e = V_x / omega` at zero drive/brake torque;
4. sensitivity to load and camber if material.

The simulation should use the effective rolling radius for slip calculation and the geometric carcass/profile radius for contact geometry rather than forcing one scalar to serve both purposes.

---

## 4. Tyre stiffness and transient data: what is public and what is not

Exact public MX33 stiffness/force datasets were not found in this pass.

However, the data type itself is well established. Michelin SIMIX offers same-size off-road motorcycle datasets for 80/100-21 and 120/80-19 tyres containing static vertical, longitudinal and lateral stiffness and, for higher-order models, mass/inertia plus longitudinal/lateral/combined handling behaviour. The numerical datasets are licensed/commercial and therefore are not copied into this project.

Reference:
https://chassis-simulation-datasets.michelin.com/

This is useful because it confirms the parameter set we are asking for is physically standard and measurable, not a game-specific invention.

### 4.1 Relaxation is mandatory

Single-track tyre research shows that lateral tyre response is transient rather than instantaneous and that relaxation properties vary with operating condition and inflation pressure. Physics V2 therefore retains at least one first-order lateral carcass/relaxation state per tyre.

Reference:
https://doi.org/10.1177/0954407015590703

Road-motorcycle relaxation coefficients are not accepted numerically for MX33 dirt operation; only the state structure is inherited.

---

## 5. Off-road force-law selection

Two research branches matter here.

### 5.1 Medium/firm dirt with lugs and camber

Classical off-road multi-spoke tyre work explicitly models lugged tyres under:

- longitudinal slip;
- lateral slip/slip angle;
- camber;
- combined longitudinal/lateral operation;
- soil pressure/shear behaviour;
- lug area/height effects.

It shows that soil hardness, slip angle, camber and lug geometry materially change both tractive and lateral force.

Reference:
https://doi.org/10.1016/S0022-4898(98)00032-9

This establishes that a dirt-bike tyre cannot be represented by `mu * Fz` plus an unrelated lateral clamp.

### 5.2 Modern brush-soil modelling

A 2026 brush-soil fusion model combines tread deformation with soil shear and slip-sinkage and reports <10% traction/sinkage prediction errors across its validation datasets. It is relevant to future deformable-soil work, but the published model is currently longitudinal-focused and explicitly does not solve the full lateral/combined-slip problem.

Reference:
https://doi.org/10.1016/j.ijmecsci.2025.111128

Therefore it does **not** replace the V2.0 lateral tyre model.

---

## 6. V2.0 hardpack tyre strategy is now locked

Physics V2.0 will use a **measured/identified effective hardpack tyre model**, not a full soil deformation solver.

For each tyre, the steady-state force surface is parameterised by:

- vertical load `Fz`;
- longitudinal slip ratio `kappa`;
- lateral slip angle `alpha`;
- camber `gamma`;
- surface class/state.

It returns at least:

- `Fx`;
- `Fy`;
- aligning/contact moment required by the selected motorcycle formulation.

Combined slip is solved coherently. First-order relaxation states lag the shear response. The actual contact point follows the rounded tyre profile and terrain normal.

### 6.1 Initial hardpack identification tests

The minimum data needed to create the first defensible hardpack surface is:

**Longitudinal**
- straight-line acceleration with wheel speed and chassis speed;
- controlled rear-brake lock/recovery;
- wheelspin build/recovery at multiple normal loads;
- derive effective `Fx/Fz` versus slip ratio with uncertainty.

**Lateral/camber**
- steady-radius turns at multiple speeds/lean angles;
- slalom/steering transients;
- combined throttle-on corner exit;
- derive effective lateral force/camber contribution from inverse dynamics rather than assigning a friction percentage.

**Vertical**
- static loaded-radius/deflection measurements;
- controlled bump/drop response with suspension motion separated from tyre deflection.

The Vasquez inverse-dynamics methodology is the preferred reference for turning instrumented motocross kinematics into tyre contact-force estimates.

Reference:
https://doi.org/10.1016/j.ymssp.2020.107228

---

## 7. Same-class off-road model values retained only as priors

Vasquez's virtual motocross model also provides useful suspension/rider priors:

- front suspension stiffness: 9800 N/m;
- front damping: 533 Ns/m;
- rear suspension torsional stiffness: 4836 N/rad;
- rear suspension torsional damping: 236 Ns/rad;
- rider arm stiffness/damping: 8930 N/m / 1456 Ns/m;
- rider leg stiffness/damping: 20180 N/m / 1833 Ns/m.

These values are not copied into Physics V2 calibration. Their purpose is to bound sensitivity studies and validate order of magnitude.

---

## 8. Numerical solver decision remains conditional on measured stiffness

The outer physics clock remains 120 Hz.

The final internal integrator cannot be selected responsibly until the effective tyre vertical stiffness, suspension force laws and contact stiffness are known. The test harness must compare at least:

- current/semi-implicit stepping with local substeps;
- a linearly implicit treatment for stiff force terms;
- timestep-halving convergence.

Selection criteria are stability, momentum/energy behaviour, contact-force convergence and mobile cost — not game feel.

---

## 9. What is now sufficiently defined for future code

The following architecture is no longer research-blocked:

- one authoritative 6-DOF rigid-body chassis state;
- quaternion attitude integration;
- separate front/rear tyre geometry and wheel angular states;
- physical steering DOF receiving torque;
- physical fork-axis and swingarm coordinates;
- force application at physical contact/attachment points;
- engine angular-speed state;
- clutch as bounded transmitted torque from slip;
- chain tension applied along actual geometry;
- reduced active rider with equal/opposite reactions;
- explicit switchable assist layer above the physical plant;
- deterministic validation harness.

What is **not** sufficiently calibrated for the runtime rewrite yet:

- CRF mass/inertia set;
- 2025 Pro-Link local motion ratio;
- Showa damping force curves;
- MX33 hardpack steady-state force surfaces;
- MX33 relaxation and vertical stiffness/damping;
- engine inertia / engine-braking / clutch-capacity curves.

---

## 10. Next research order

1. Search for or derive CRF450R-family component masses/inertias and establish defensible bounded initial mass-property values.
2. Continue 2025 Pro-Link geometry recovery; if public geometry remains unavailable, document a physical measurement protocol as the required identification source.
3. Search exact/adjacent motocross tyre static stiffness data and instrumented force/slip studies.
4. Define a parameter-identification dataset schema for hardpack `Fx/Fy/Mz` surfaces with uncertainty.
5. Close engine inertia, coast/engine-braking and clutch-slip identification.
6. Only when those initial parameter sets are defensible, begin the assists-disabled Physics V2 runtime plant.

The coding gate remains closed after Pass 03, but the remaining blockers are now measurement/calibration problems rather than uncertainty about what mathematical systems the game needs.
