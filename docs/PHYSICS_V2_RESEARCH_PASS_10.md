# Physics V2 Research Pass 10

Status: **research / Gate A tightening only — no runtime physics changed.**

This pass executes the Honda R&D Figure 9 graphical center-of-gravity extraction proposed in Research Pass 09. It also corrects the image-scale interpretation from Pass 09: the motorcycle visibly rendered in Figure 9 is the **CR ELECTRIC**, so the image must be scaled by the CR ELECTRIC wheelbase published later in the same paper, not by the parenthesized 2021 CRF450R wheelbase.

Reference runtime configuration remains the **late-2025 US Honda CRF450R production specification**. The result below is an adjacent-generation manufacturer graphical prior, not a measured 2025 mass property.

---

## 1. Source and reproducibility

Primary source:

- Honda R&D Technical Review 2026 Vol. 38, pp. 23-30, *Development of Electric Motocrosser CR ELECTRIC*;
- DOI: `10.69239/hondatechnicalreview.2026_38_3`;
- Figure 9: battery-pack outline and vehicle CG positions;
- Figure 17: vehicle-dimension comparison.

Reproducible extraction is implemented by:

- `tools/extract_honda_cg.py`;
- `.github/workflows/physics-v2-cg-extract.yml`.

The workflow downloads the Honda PDF, renders printed page 26 at 6x scale, identifies wheel centers, tire-contact rows and the colored CG markers, writes `results.json`, and uploads the source/render/masks/measurement overlay as a CI artifact.

Source PDF SHA-256 during this pass:

`d7186d19eff69a871bc899f9097090c8033f291ff67bb2ff578bde0350910ef6`

---

## 2. Important correction to Pass 09

Table 1 identifies the comparison motorcycle as the **2021 CRF450R** and lists its wheelbase as **1481 mm**.

However, Figure 9 itself is an annotated side view of the **CR ELECTRIC**. Figure 17 later gives:

- CR ELECTRIC wheelbase: **1491 mm**;
- comparison 2021 CRF450R wheelbase: **1481 mm**.

Therefore the correct scale for pixel distances measured on the visible Figure 9 motorcycle is:

`1491 mm / visible wheel-center separation`

The 1481 mm value remains relevant for identifying the comparison CRF450R configuration, but using it as the image scale would introduce an avoidable approximately 0.67% scale error.

This supersedes the 1.481 m scaling instruction in Research Pass 09.

---

## 3. Pixel extraction

The 6x Figure 9 render is 1206 x 762 px.

Detected geometry:

- rear wheel center: `(244, 509)` px;
- front wheel center: `(941, 506)` px;
- wheel-center separation: `697.0065 px`;
- rear tire contact row: `y = 667 px`;
- front tire contact row: `y = 667 px`;
- CRF450R red-marker centroid: `(538.9784, 359.8193)` px;
- CR ELECTRIC green-marker centroid: `(566.6306, 364.3953)` px.

Using the 1491 mm CR ELECTRIC wheelbase:

`scale = 1491 / 697.0065 = 2.139148 mm/px`

The longitudinal coordinate is projected along the measured wheel-center axis rather than assuming a perfectly horizontal axle line.

---

## 4. Independent scale sanity check

The wheelbase-derived scale predicts the visible tire radii from wheel center to the detected ground-contact row as:

- rear: **337.99 mm**;
- front: **344.40 mm**.

For comparison, simple nominal geometry from the CRF-class tire sizes gives:

- 120/80-19 rear: **337.30 mm** nominal radius;
- 80/100-21 front: **346.70 mm** nominal radius.

Differences are therefore approximately:

- rear: `+0.69 mm`;
- front: `-2.30 mm`.

This agreement is much tighter than required for a publication-graphic extraction and is a strong internal rejection check against using the wrong wheelbase scale, wrong wheel centers or wrong ground line. It does **not** convert the source image into a metrology drawing.

---

## 5. Extracted Honda graphical prior

For the red marker labeled as the CRF450R CG position:

- longitudinal position from rear contact reference: **0.63237 m**;
- vertical position above the visible ground line: **0.65711 m**.

Classification:

`GRAPHICAL_PRIOR_2021_HONDA`

A conservative extraction-only uncertainty of **+/-0.015 m** is assigned to both coordinates. This covers wheel/marker selection, rasterization, marker thickness and small perspective/ground-line ambiguity and is deliberately larger than the raw pixel quantization error.

The green CR ELECTRIC marker extracts to approximately:

- `x = 0.69148 m` from rear contact;
- `z = 0.64732 m` above ground.

That second result is retained only as a source-interpretation cross-check. Physics V2 is not an EV model.

---

## 6. Why the 2021 graphical X value is not promoted to 2025

Honda Europe's same-generation 25YM CRF450R data provide an internally consistent longitudinal prior:

- wheelbase: 1.483 m;
- front/rear static split: 48.7 / 51.3 percent;
- derived `x_CG = 0.722221 m` from the rear contact patch.

The Figure 9 graphical 2021 value of approximately **0.632 m** is roughly **90 mm rearward** of that same-generation 25YM prior. That discrepancy is far larger than the approximately 15 mm graphical extraction uncertainty.

Therefore it would be incorrect to average the two values or silently use the 2021 graphical X coordinate as the late-2025 US runtime value. Plausible causes include generation/configuration change, source-graphic placement conventions, or other vehicle-state differences not recoverable from the publication.

The correct treatment is:

- retain the 25YM static-load result as the stronger same-generation longitudinal prior;
- retain 0.632 m as an adjacent-generation Honda graphical cross-check;
- keep exact late-2025 US `x_CG` unresolved until same-configuration evidence or identification closes it.

---

## 7. CG-height implication

The important new information is the vertical coordinate.

Before this pass, public-data research had no quantitative manufacturer-derived CRF450R CG-height prior. Figure 9 now provides a defensible adjacent-generation value near:

`z_CG ~= 0.657 m`

This is **not** accepted as the literal 2025 US bike-only CG height. Model-generation transfer uncertainty dominates the +/-0.015 m extraction uncertainty.

For pre-implementation sensitivity work, Physics V2 may use the following research envelope:

- central graphical prior: **0.657 m**;
- broad transfer/sensitivity interval: **0.60-0.72 m**.

The interval is intentionally broad enough that the research model must demonstrate whether wheelie threshold, pitch load transfer, jump attitude and landing response are materially sensitive to the unresolved 2025 height.

If the validation outputs vary materially across that interval, CG height remains a required measurement/identification item. If they do not, a narrower manufacturer-informed interval may eventually qualify as `BOUNDED_LOW_SENSITIVITY` under the coding-gate rules.

No runtime constant is authorized by this pass.

---

## 8. Gate A impact

### Improved

- manufacturer-authored CRF-family CG-height prior now exists;
- extraction is reproducible in CI rather than manually eyeballed;
- image scale is independently validated by tire geometry;
- 2021 graphical uncertainty is separated from 2021-to-2025 transfer uncertainty;
- the earlier 1481 mm image-scale interpretation is corrected to 1491 mm.

### Still blocking

- same-configuration late-2025 US bike-only CG height, unless sensitivity work proves the uncertainty low enough to bound;
- exact US longitudinal CG or an accepted same-configuration identification;
- pitch/roll/yaw inertias;
- steering, wheel and swingarm/linkage inertias;
- final force-application hardpoint set.

**Gate A remains NOT READY.**

The coding gate remains closed overall.

---

## 9. Next Gate-A work

The immediate useful next step is now a deterministic CG-height sensitivity sweep around the extracted prior before spending more effort hunting an exact height value.

Recommended test points:

`z_CG = 0.60, 0.63, 0.657, 0.69, 0.72 m`

For each point, hold every other parameter fixed and compare at minimum:

1. quasi-static front/rear normal-load distribution under pitch acceleration;
2. throttle wheelie threshold versus speed/gear;
3. braking load transfer and rear-unload threshold;
4. jump takeoff pitch rate from the same ramp/input trace;
5. landing peak chassis pitch acceleration.

The purpose is not to tune feel. It is to determine whether the unresolved 2025 CG-height uncertainty is large enough to dominate the first validation suite.

After that, the highest-value remaining Gate-A target is pitch inertia / whole-bike inertia identification.

---

## 10. Current verdict

**CODING GATE: CLOSED.**

Pass 10 converts CRF450R CG height from an unbounded public-data unknown into a reproducible manufacturer-graphical prior centered near **0.657 m**, while explicitly refusing to mislabel a 2021 publication graphic as a measured 2025 parameter.

The result is useful enough to run sensitivity analysis and decide whether an exact same-bike CG-height measurement is truly required before Physics V2 implementation.
