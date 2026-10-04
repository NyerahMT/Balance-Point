# Physics V2 Research Pass 15 — Hardpack Tire / Contact Closure

Status: **Gate-D engineering closure for the first Physics V2 plant. No legacy runtime physics changed.**

This pass applies the same `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` policy used to close Gates A-C. Exact Dunlop MX33 force-rig data are not public, so the first plant uses a coherent, replaceable hardpack model tied to motocross-specific vertical-stiffness evidence and established motorcycle-tire model structure.

## 1. Reference tires and test envelope

Frozen CRF450R tires:

- front: Dunlop Geomax MX33 80/100-21, tube type;
- rear: Dunlop Geomax MX33 120/80-19, tube type.

Sumitomo/Dunlop motocross tire patents are especially useful because their development tests use the same size class on a 450 cc motocross motorcycle:

- 80/100-21 front;
- 120/80-19 rear;
- 21 x 1.60 / 19 x 2.15 rims;
- 80 kPa test pressure;
- vertical-load regions 0.96-1.92 kN and 1.92-2.88 kN.

The patents define load-dependent vertical spring behavior and give preferred stiffness-growth rates of roughly 50-100 N/mm per kN in the first load region and 30-80 N/mm per kN in the second. Their examples use 75 N/mm per kN in the first region.

Primary sources:

- https://patents.google.com/patent/JP7188038B2/en
- https://www.patentsencyclopedia.com/app/20110214790

These are not MX33 bench curves. They constrain the *shape and operating scale* of a motocross tire model.

## 2. Vertical force law

A single constant radial spring is rejected.

`tools/physics_v2_gate_d_estimate.py` generates monotone front/rear `Fz(delta)` tables by specifying effective stiffness as a function of vertical load, then inverting the load-deflection relationship for runtime interpolation.

First-plant anchors at 960 N:

- front effective vertical stiffness: 105 kN/m;
- rear effective vertical stiffness: 115 kN/m.

Stiffness-growth slopes:

- below 0.96 kN: 35 N/mm per kN engineering continuation;
- 0.96-1.92 kN: 75 N/mm per kN, inside the patent range;
- above 1.92 kN: 55 N/mm per kN, inside the second patent range.

Vertical-force magnitude uncertainty: **±35%**.

Radial damping first-plant anchors:

- front: 550 N s/m;
- rear: 650 N s/m;
- uncertainty: **±60%**.

Radial damping acts only through physical contact compression/rebound state and is not a chassis damper.

## 3. Radius set

Front unloaded geometry retains the public catalog band already established:

- front nominal unloaded radius: 0.3538 m;
- interval: 0.3526-0.3550 m.

Rear nominal unloaded radius begins from the exact nominal size geometry:

- 19 in rim radius + 0.80 * 120 mm sidewall = 0.3373 m;
- deliberately broad first-plant interval: 0.329-0.345 m.

Using the generated vertical curve and the same-generation bike-only static loads gives first-plant loaded/effective rolling radii automatically. Effective rolling radius is kept distinct from geometric loaded radius and is replaceable independently.

Both loaded/effective-radius estimates carry **±6 mm** implementation uncertainty.

## 4. Pure longitudinal force

The first hardpack longitudinal law is a smooth saturating brush-style relationship:

`Fx0 = mu_x(Fz) * Fz * tanh(Ck * kappa / (mu_x(Fz) * Fz))`

where `Ck` is proportional to Fz and `mu_x` has mild load sensitivity.

Nominal hardpack anchors at 1 kN:

- front `mu_x = 0.95`;
- rear `mu_x = 1.05`;
- front normalized slip stiffness `Ck/Fz = 11`;
- rear `Ck/Fz = 10`.

The entire friction level carries **±22%** uncertainty and slip stiffness **±30%**. These are effective hardpack engineering values, not Dunlop measurements.

There is no separate wheelspin force multiplier. Rear drive force and front/rear braking force use the same contact formulation and sign convention.

## 5. Lateral + camber force

Motorcycle tires require camber-sensitive lateral response. The first pure lateral law is:

`Fy0 = mu_y(Fz) * Fz * tanh(Ca * (alpha + K_gamma * gamma) / (mu_y(Fz) * Fz))`

Nominal 1 kN hardpack anchors:

- front `mu_y = 0.90`, `Ca/Fz = 9 rad^-1`, camber-equivalent-slip gain 0.32;
- rear `mu_y = 0.95`, `Ca/Fz = 8 rad^-1`, camber-equivalent-slip gain 0.28.

Friction uncertainty is **±22%**, lateral stiffness **±30%**, and camber gain **±50%**.

The camber term is deliberately explicit so it can later be replaced by a measured camber-thrust surface without altering the contact architecture.

## 6. Combined slip

Longitudinal and lateral pure-slip requests are resolved by one normalized friction ellipse.

If

`q = sqrt((Fx0/(mu_x Fz))^2 + (Fy0/(mu_y Fz))^2)`

then both forces are scaled by `1/max(1,q)`.

Therefore:

- throttle-on cornering gives up lateral capacity as drive force increases;
- braking in a turn gives up lateral capacity automatically;
- no independent longitudinal/lateral clamp can create more resultant force than the contact envelope permits;
- the same law remains continuous through zero slip and sign reversals.

This is the first-plant combined-slip closure required by Gate D.

## 7. Contact moments

The first model includes physical tire moments rather than omitting them silently:

- self-aligning yaw moment: `Mz = -trail * Fy`;
- nominal pneumatic trail: 30 mm front / 20 mm rear at low utilization;
- pneumatic trail fades continuously toward zero at lateral saturation;
- overturning/camber moment: `Mx = -Kmx * Fz * R * sin(gamma)` with first-plant `Kmx = 0.02`.

Moment coefficients remain engineering estimates with broad uncertainty and centralized replacement points.

## 8. Relaxation / transient response

Motorcycle-tire testing literature shows lateral force does not appear instantaneously; relaxation length is the appropriate first-order spatial transient variable. Published motorcycle measurements are commonly on the order of tenths of a meter and vary with pressure/load.

References:

- https://www.research.unipd.it/handle/11577/2462460
- https://www.research.unipd.it/handle/11577/3104147
- https://doi.org/10.1023/B:MECC.0000022842.12077.5c

The first-plant low-pressure/off-road anchors intentionally sit toward the upper half of that order of magnitude:

- front longitudinal relaxation length: 0.14 m;
- rear longitudinal: 0.16 m;
- front lateral: 0.20 m;
- rear lateral: 0.24 m;
- uncertainty: **±50%**.

Runtime implementation uses first-order force states with time constant approximately `sigma / max(|Vx|, Vmin)` and a low-speed regularization that cannot produce infinite or discontinuous force.

## 9. Contact loss and reacquisition

The off-road inverse-dynamics literature explicitly observes wheel detachments and large suspension motion on motocross tracks, so contact loss is normal physics rather than an exceptional mode.

Reference:

- https://doi.org/10.1016/j.ymssp.2020.107228

Rules for V2:

- `Fz <= 0` means `Fz = Fx = Fy = Mx = Mz = 0`;
- relaxation states decay/reset without injecting an impulse;
- reacquisition begins from the normal contact law and builds tangential forces through the same relaxation model;
- there is no landing-specific grip multiplier or airborne tire force.

## 10. Reproducibility and automated checks

`tools/physics_v2_gate_d_estimate.py` generates:

- front/rear vertical force-deflection tables;
- loaded/effective radius estimates;
- a sampled hardpack combined-slip surface;
- friction, stiffness, camber, damping and relaxation uncertainty metadata.

It fails if:

- vertical deflection is not monotone with load;
- sampled combined-slip utilization exceeds the unit envelope.

`.github/workflows/physics-v2-gate-d-estimate.yml` runs the estimator and archives the generated artifacts.

## 11. Gate-D decision

**Gate D: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

The first V2 plant now has one coherent contact architecture covering:

- nonlinear vertical compliance;
- loaded and effective rolling radii;
- radial damping;
- longitudinal drive/braking response;
- lateral slip and camber response;
- self-aligning/overturning moments;
- combined slip;
- transient relaxation;
- contact loss and reacquisition.

None of the numeric values are claimed to be permanent MX33 measurements. Better data replaces the centralized values without changing the equations.

With Gates A-D now ready, the pre-runtime physical-data gate can open. The next task is **Physics V2 implementation and deterministic validation**.
