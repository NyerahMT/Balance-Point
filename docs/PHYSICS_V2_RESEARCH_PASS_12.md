# Physics V2 Research Pass 12 — Engineering Mass-Property Closure

Status: **Gate-A engineering closure for implementation; replaceable estimates, not factory measurements.**

This pass changes the project policy deliberately: exact same-bike measurements are preferred but are no longer mandatory when a physical parameter can be estimated from published motorcycle data, scaled by the frozen CRF450R geometry/mass, assigned an explicit uncertainty envelope, and validated across that envelope.

New allowed state:

`ENGINEERING_ESTIMATE_WITH_UNCERTAINTY`

This state is allowed into the first Physics V2 plant only when the derivation is reproducible, uncertainty is explicit, the parameter remains externally replaceable, and validation is run across its stated interval. It is not permission to tune a number until the bike feels right.

## 1. CG set for the first plant

Longitudinal CG:

- nominal `x_CG = 0.722221 m` from rear contact;
- source: same-generation Honda 25YM 48.7/51.3 static load split and 1.483 m wheelbase;
- implementation uncertainty: `+/-0.015 m` to cover configuration transfer rather than pretending the EU value is exact for the US bike.

Vertical CG:

- nominal `z_CG = 0.657 m` above ground;
- source: reproducible Honda graphical extraction from Research Pass 10;
- the image-extraction uncertainty itself is much smaller, but 2021-to-2025 model transfer dominates;
- implementation uncertainty: `+/-0.040 m`.

The broader 0.60-0.72 m Pass-11 sweep remains the audit record showing that CG height is high sensitivity. The tighter +/-40 mm implementation interval is an engineering-transfer assumption, not a new measurement.

## 2. Pitch inertia reconstructed from an off-road model

The off-road motorcycle model reported by Vasquez/Lot/Rustighi/Pegoraro provides a longitudinal set of motorcycle body masses, positions, and local pitch inertia. Excluding the rider body, the published motorcycle bodies are reconstructed with the parallel-axis theorem.

Published model values used by `tools/physics_v2_mass_property_estimate.py`:

- rear body/wheel-equivalent: 18 kg at x = 0.000 m, z = -0.300 m;
- massless 0.28 kg m^2 rotational body at x = 0.279 m;
- main motorcycle body: 81 kg, local pitch inertia 23 kg m^2, x = 0.768 m, z = -0.507 m;
- front body/wheel-equivalent: 12 kg at x = 1.350 m, z = -0.300 m.

Reconstruction gives approximately:

- total motorcycle mass: `111 kg`;
- system CG x: `0.7064 m`;
- system CG z: `-0.4511 m` in the source coordinate system;
- whole-bike pitch inertia: `38.48 kg m^2`;
- source wheelbase: `1.35 m`;
- dimensionless pitch coefficient `k_y = Iyy / (m L^2) ~= 0.1902`.

Transporting that coefficient to the frozen CRF values:

- mass `112.94 kg`;
- wheelbase `1.48082 m`;

produces the first-plant estimate:

`Iyy_pitch ~= 47.1 kg m^2`.

Classification: **ENGINEERING_ESTIMATE_WITH_UNCERTAINTY**.

Uncertainty for first implementation: **+/-25%**.

That interval is deliberately wide. It is carried into jump/wheelie/landing validation rather than hidden by controller tuning.

Source method/data:

- Vasquez et al., off-road motorcycle tyre-force / multibody work, IOP Conference Series 1264 (2019) 012020 and related off-road model work.

## 3. Roll and yaw inertia priors

A published Enduro motorcycle model reports vehicle+rider principal inertias of 33.4 / 64.9 / 39.4 kg m^2 for roll/pitch/yaw. Those absolute numbers are not transported because the model includes a rider. Only the dimensionless shape ratios are used as low-confidence priors:

- `Ixx/Iyy ~= 33.4/64.9`;
- `Izz/Iyy ~= 39.4/64.9`.

Applying those ratios to the off-road-derived CRF pitch estimate gives nominal first-plant values:

- `Ixx_roll ~= 24.3 kg m^2`;
- `Iyy_pitch ~= 47.1 kg m^2`;
- `Izz_yaw ~= 28.7 kg m^2`.

Roll/yaw uncertainty is **+/-35%**. Products of inertia are initialized to zero in the nominal symmetric plant and must remain separately replaceable if later geometry reconstruction supports nonzero values.

These roll/yaw values are lower-confidence than pitch and must not be presented as CRF measurements.

Cross-check source:

- published Supersport/Enduro motorcycle parameter table with Enduro mass, geometry and 33.4/64.9/39.4 kg m^2 roll/pitch/yaw inertias.

## 4. Gate-A policy after Pass 12

Gate A no longer waits indefinitely for unavailable manufacturer or laboratory data.

For the first Physics V2 implementation:

- CG X and Z may use the nominal engineering set above;
- pitch/roll/yaw inertias may use the nominal engineering set above;
- all values remain centralized and replaceable;
- automated validation must be rerun at the uncertainty endpoints;
- no gameplay assist may compensate for a mass-property choice;
- if a result changes qualitatively across the uncertainty interval, that validation result is flagged uncertain rather than tuned away.

Component wheel/steering/swingarm inertias may likewise enter as geometry-based engineering estimates with conservative bounds when their subsystem is implemented. Exact local linkage hardpoints remain part of the suspension/geometry reconstruction rather than a reason to hold the entire project at Gate A.

## 5. Verdict

**Gate A: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

This does not mean the mass properties are known exactly. It means their provenance, nominal values, uncertainty and replacement policy are now explicit enough to build the plant without pretending to possess unavailable factory data.

Next priority: **Gate B — 2025 Pro-Link kinematics and Showa force-law engineering model.**
