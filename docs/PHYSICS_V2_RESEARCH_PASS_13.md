# Physics V2 Research Pass 13

Status: **Gate-B engineering closure for first implementation. No runtime physics changed.**

Reference machine remains the late-2025 US Honda CRF450R production-spec configuration.

## 1. Policy

Pass 12 established `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY` as an allowed first-plant parameter state when exact same-bike measurements are unavailable, provided that:

- the estimate is tied to published geometry, measured-compatible hardware, or peer-reviewed model structure;
- uncertainty is explicit;
- the parameter is replaceable without changing solver architecture;
- validation telemetry exposes the parameter's effect;
- later better data replaces the estimate rather than being averaged into it by feel.

Gate B now uses the same rule.

## 2. 2025 rear linkage facts

Honda states that the 2025 CRF450R received a revised one-piece Pro-Link linkage and a new leverage ratio intended to reduce pitching and improve bottoming resistance while retaining small-bump comfort.

Primary source:
https://hondanews.com/en-US/releases/release-70527d767f112a34ec2e42bfb5060b28-2025-honda-crf450r

MXA independently reports that the 2025 pull rods are about 0.5 mm longer and that the bell crank uses a stiffer rising-rate curve. This is qualitative support for a more progressive local ratio, not a source of exact hardpoints.

Source:
https://motocrossactionmag.com/mxa-race-test-2025-honda-crf450/

The already-frozen geometry/hardware constraints remain:

- rear wheel travel: 309.88 mm;
- compatible Showa shock stroke envelope: 133 mm;
- average full-stroke wheel/shock displacement ratio: 309.88 / 133 = 2.3299;
- shock spring: 52 N/mm;
- swingarm length: 585.2 mm.

## 3. First-plant local motion-ratio curve

A piecewise-linear local wheel/shock motion-ratio curve is now defined as an engineering estimate:

- top of stroke: 2.420;
- 70% shock stroke: 2.340;
- full compression: approximately 2.086.

The end value is solved, not hand-picked, so that integration over the complete 133 mm shock stroke reproduces the frozen 309.88 mm wheel travel exactly.

This shape follows two documented constraints:

1. the 2025 linkage is rising-rate;
2. the stronger progression is concentrated later in the stroke, consistent with Honda's bottoming-resistance objective and the published behavior of modern CRF linkage development.

Nominal local motion-ratio uncertainty for first implementation: **+/-0.12**.

With the 52 N/mm shock spring, the corresponding nominal rear wheel spring rate rises from about **8.88 kN/m** at the top of stroke to about **11.95 kN/m** near full compression, roughly a 35% increase.

Classification: `ENGINEERING_ESTIMATE_WITH_UNCERTAINTY`.

## 4. Damping baseline

Vasquez, Lot and Rustighi publish an off-road motorcycle suspension optimization in which the bounce-mode critical damping coefficients are:

- front: 1723 N s/m;
- rear: 1928 N s/m.

They show that severe off-road racing/handling trade-offs occur broadly around damping ratios of approximately 0.35-0.55 depending on excitation and axle.

Source:
https://eprints.soton.ac.uk/416212/

For the first plant, use a nominal damping ratio of **0.45** with the published 0.35-0.55 region retained as the engineering envelope.

This gives nominal equivalent wheel-space damping anchors:

- front: 775 N s/m;
- rear: 868 N s/m.

The first physical branch split is:

- compression: 0.80 times the equivalent anchor;
- rebound: 1.20 times the equivalent anchor.

Therefore the nominal front pair starts near:

- compression: 620 N s/m;
- rebound: 930 N s/m.

At the nominal mid-stroke rear motion ratio, the rear wheel-space anchor maps to a physical shock coefficient of about 4.84 kN s/m, giving approximately:

- compression: 3.88 kN s/m;
- rebound: 5.81 kN s/m.

These are **not claimed to be stock-Showa dyno measurements**. They are physically scaled first-plant force-law anchors with **+/-35% nominal uncertainty**.

The runtime damper law must remain branch-separated and may use a smooth digressive/blow-off representation. It must not use one unlimited linear coefficient at all shaft velocities.

## 5. Springs, friction and end stroke

Published/retained spring data:

- fork: 5.0 N/mm each leg, 10.0 N/mm combined;
- shock: 52 N/mm.

Honda and independent testing describe the 2025 fork as plusher and more progressive than recent prior years, with revised Bending Control Valves, seals and oil. Dirt Rider reports a 310 cc standard fork-oil setting and a practical 303-388 cc range, confirming that trapped-air/end-stroke progression is an intended tuning mechanism.

Sources:
https://hondanews.com/en-US/releases/release-70527d767f112a34ec2e42bfb5060b28-2025-honda-crf450r
https://www.dirtrider.com/tests/honda-crf450r-review/

First-plant end-stroke estimates:

- fork progressive end-stop begins at 75% travel and contributes up to 2.5 kN additional force at full travel;
- rear bump-stop begins at 82% shock stroke and contributes up to 4.5 kN shock-axis force at full compression;
- both use smooth cubic onset;
- end-stop force magnitude carries +/-50% uncertainty.

Fork/shock friction remains a small explicit Coulomb/Stribeck term rather than being hidden inside damping. Its first-plant magnitude may be bounded broadly and must remain telemetry-visible.

## 6. Moving/unsprung mass bounds

The Vasquez off-road motorcycle model uses a 12 kg front wheel/front-end mass and 18 kg rear wheel assembly mass in its virtual experiment. Those are not CRF measurements but are useful same-class bounds.

Source:
https://eprints.soton.ac.uk/448147/

For first implementation, component moving masses may use same-class engineering estimates with +/-25% bounds until exact CRF component masses are available. They are not permitted to alter the static total-mass constraint.

## 7. Reproducibility

`tools/physics_v2_gate_b_estimate.py` generates:

- the complete local rear motion-ratio curve;
- integrated rear wheel travel;
- local wheel spring rate;
- rear bump-stop curve;
- front/rear damping anchors and uncertainty metadata.

`.github/workflows/physics-v2-gate-b-estimate.yml` runs the estimator and archives the outputs.

## 8. Gate-B decision

**Gate B: READY FOR INITIAL IMPLEMENTATION WITH ENGINEERING UNCERTAINTY.**

The first plant now has a complete, continuous and replaceable suspension parameterization without pretending that public data contain an exact 2025 Showa dyno or linkage-coordinate dataset.

Validation requirements remain strict:

- static sag must be plausible with the frozen reference rider configuration;
- a small bump must not inject energy;
- compression/rebound must remain continuous through zero velocity;
- a full-travel landing must remain stable and finite;
- timestep halving must not materially change peak travel/force;
- no attitude-control torque or artificial anti-bottoming force is permitted.

If later measurement or dyno data become available, they replace these estimates directly.
