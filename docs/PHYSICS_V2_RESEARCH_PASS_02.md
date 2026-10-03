# Physics V2 Research Pass 02

Status: **research only — no runtime physics changed.**

This pass closes as much of the 2025 US CRF450R powertrain and rear-linkage data as public sources support without inventing physical constants. It supplements `PHYSICS_V2_PARAMETER_PACK.md`, `PHYSICS_V2_RESEARCH_PASS_01.md`, and Issue #29.

## 1. Scope and source discipline

Reference machine remains the stock 2025 US Honda CRF450R.

Source classes used here:

- **OEM / manufacturer** — Honda specifications/features/manual data.
- **MEASURED** — instrumented same-model test data, e.g. Dirt Rider Dynojet results.
- **COMPATIBLE-PART PROXY** — dimensions published for an aftermarket component engineered as a direct 2025 CRF450R replacement. Useful for bounds/geometry sanity, not relabeled as an OEM measurement.
- **AFTERMARKET MEASUREMENT** — explicit measured stock-part dimensions/weights from a specialist supplier. Useful, but not manufacturer data.
- **DERIVED BOUND** — calculation that gives a physically necessary bound rather than an exact parameter.

No UNKNOWN value is promoted to a tuned runtime constant.

---

## 2. Powertrain dataset

### 2.1 OEM architecture

Honda's 2025 US specification identifies:

- 449.8 cc liquid-cooled Unicam single-cylinder four-stroke
- 96.0 x 62.1 mm bore/stroke
- 13.5:1 compression ratio
- constant-mesh 5-speed manual transmission
- hydraulically actuated wet multiplate clutch
- **6 clutch springs and 8 friction plates**
- #520 chain
- 13T/49T final drive

Honda source:
https://hondanews.com/en-US/powersports/releases/release-70527d767f112a34ec2e42bfb506153d-2025-honda-crf450r-specifications

The 2025 clutch fiche confirms six `22401-MKE-AF0` clutch springs, six `22201-MEN-670` B friction discs plus two model-specific end friction discs, and seven steel plates.

Clutch/specification references:
- https://www.adeptpowersports.com/oem-parts/2025-honda-crf450r-clutch-assembly.html
- https://www.notice-facile.com/en/manual/1411421/honda%2Bcrf450rwe-2025

Known clutch service dimensions from the 2025 manual data:

- friction-disc thickness: 2.92-3.08 mm standard
- service limit: 2.70 mm
- clutch-spring free length: 56.2 mm standard
- spring service limit: 55.2 mm

These dimensions do **not** identify spring rate, installed preload, friction coefficient, or mean friction radius, so they are insufficient to calculate exact clutch capacity.

### 2.2 Transmission ratios

The official 2025 ratios already recorded in the parameter pack remain accepted:

| Stage | Ratio |
|---|---:|
| Primary | 2.357 |
| 1st | 2.133 |
| 2nd | 1.705 |
| 3rd | 1.421 |
| 4th | 1.210 |
| 5th | 1.043 |
| Final | 49/13 = 3.76923 |

Derived overall reductions:

| Gear | Overall ratio |
|---|---:|
| 1 | 18.9486 |
| 2 | 15.1464 |
| 3 | 12.6235 |
| 4 | 10.7491 |
| 5 | 9.2655 |

The existing runtime representative ratios are not accepted for Physics V2.

### 2.3 Measured 2025 rear-wheel dyno anchors

Dirt Rider tested the stock 2025 CRF450R on its Dynojet 250i and reports:

- peak rear-wheel power: **51.1 hp at 9,600 rpm**
- peak rear-wheel torque: **32.9 lb-ft (44.61 N m equivalent engine-rpm torque) at 6,900 rpm**

Source:
https://www.dirtrider.com/tests/honda-crf450r-dyno-test-2025/

The source chart is directly accessible at:
https://cloudfront-us-east-1.images.arcpublishing.com/octane/5L3YMXUFRJCKNEI3GVF4OP5DMI.jpg

### 2.4 Working digitization of the dyno chart

The table below is a **coarse chart digitization for research/curve-shape work**, not yet the final calibration dataset. Intermediate values were visually digitized from the published chart; the two peak anchors above remain exact reported values. Treat ordinary rows as approximately +/-0.8 hp until a pixel-based digitization is archived.

Torque in the table is derived from `T_lbft = hp * 5252 / rpm`, preserving power/torque consistency rather than independently eyeballing both curves.

| RPM | Power hp | Derived torque lb-ft | Status |
|---:|---:|---:|---|
| 2,000 | 1.0 | 2.6 | chart estimate |
| 2,500 | 8.2 | 17.2 | chart estimate |
| 3,000 | 11.5 | 20.1 | chart estimate |
| 3,500 | 14.2 | 21.3 | chart estimate |
| 4,000 | 17.5 | 23.0 | chart estimate |
| 4,500 | 21.8 | 25.4 | chart estimate |
| 5,000 | 27.2 | 28.6 | chart estimate |
| 5,500 | 31.8 | 30.4 | chart estimate |
| 6,000 | 36.1 | 31.6 | chart estimate |
| 6,500 | 40.5 | 32.7 | chart estimate |
| 6,900 | 43.2 | 32.9 | **reported peak-torque anchor** |
| 7,000 | 43.5 | 32.6 | chart estimate |
| 7,500 | 46.1 | 32.3 | chart estimate |
| 8,000 | 48.3 | 31.7 | chart estimate |
| 8,500 | 49.6 | 30.6 | chart estimate |
| 9,000 | 50.4 | 29.4 | chart estimate |
| 9,500 | 50.9 | 28.1 | chart estimate |
| 9,600 | 51.1 | 28.0 | **reported peak-power anchor** |
| 10,000 | 50.4 | 26.5 | chart estimate |
| 10,500 | 49.5 | 24.8 | chart estimate |
| 11,000 | 47.2 | 22.5 | chart estimate |
| 11,500 | 44.8 | 20.5 | chart estimate / approximate rev ceiling |

This working table is sufficient to design the engine-curve data interface and validation plots. It is **not** sufficient to claim Gate C fully calibrated.

### 2.5 Clutch torque: defensible lower bound, not guessed capacity

The published dyno torque is measured after drivetrain losses but expressed against engine rpm. At the clutch pack, which sits after the 2.357 primary reduction, the friction stack must transmit at least the equivalent of:

`44.61 N m x 2.357 = 105.1 N m`

at the measured peak-torque condition, before allowing for crank-to-wheel losses and design margin.

Therefore:

- **full-engagement clutch static torque capacity must be >105 N m** at the clutch shaft under the dyno condition;
- this is a **DERIVED LOWER BOUND**, not the clutch-capacity parameter to put in the model;
- the real capacity is higher and remains UNKNOWN until spring load, friction geometry/material, or an identified slip test is available.

This bound is useful because any future identified clutch model producing less than 105 N m full-engagement capacity is immediately inconsistent with the measured stock bike.

### 2.6 Engine braking

Honda/Dirt Rider reporting supports a qualitative 2025 change: the bike has **less engine braking** than the previous generation and rolls through corners more freely. Honda's owner guidance also states HSTC does not operate during deceleration and does not prevent rear-wheel skid caused by engine braking.

References:
- https://www.dirtrider.com/tests/2025-honda-crf450r-first-ride-review/
- https://www.manualslib.com/manual/3782186/Honda-Crf450r-2025.html?page=16

No public source found in this pass gives a coast-down torque-vs-rpm curve. Engine-loss/engine-braking torque therefore remains an identification task.

### 2.7 Engine rotational inertia

No numerical stock 2025 crank/engine equivalent inertia was found in this pass. VHM lists a modified **high-inertia** crank option for 2017-2025 CRF450R, which confirms crank inertia is an intentional tuning variable, but supplies no numerical inertia and therefore cannot close the gate.

Reference:
https://lbproduction.s3.amazonaws.com/569cc38891b75c6b2afc3289/extras/vhmkuvasto2025.pdf

Required identification remains:

- unloaded RPM rise/fall transient with known combustion/loss torque, or
- component mass-property measurement/reconstruction.

---

## 3. Rear Pro-Link geometry research

### 3.1 What is OEM-confirmed

Honda states the 2025 CRF450R rear suspension has:

- a revised Pro-Link structure
- **11% greater linkage structural rigidity**
- an altered leverage ratio
- reduced pitching / improved balance under braking
- improved bottoming resistance while retaining small-bump comfort
- 52 N/mm stock shock spring
- 12.4 in rear-wheel travel in the US launch specification

Honda source:
https://hondanews.com/en-US/releases/release-70527d767f112a34ec2e42bfb5060b28-2025-honda-crf450r

Dirt Rider independently notes the 2025 linkage ratio is all-new and the prior two-pullrod arrangement was replaced by a traditional one-piece pullrod.

References:
- https://www.dirtrider.com/tests/2025-honda-crf450r-first-ride-review/
- https://www.dirtrider.com/tests/honda-crf450r-review/

### 3.2 OEM linkage identities

2025 CRF450R cushion-arm fiche:

- cushion arm sub-assembly: `52465-K95-AG0`
- connecting-rod / pullrod sub-assembly: `52475-K95-AG0`
- link bolts: 12 x 82 mm, 12 x 135 mm, 12 x 103.5 mm

Source:
https://www.revzilla.com/oem/honda/2025-honda-crf450r/cushion-arm

The same K95-AG0 linkage components appear across the 2025 CRF250R/RX and 450R/RX family, which gives more measurement opportunities without assuming older-generation geometry.

### 3.3 Stock pullrod center-to-center length

Luxon publishes its 2025-2026 CRF250R/RX and CRF450R/RX adjustable link range as **146.8-151.8 mm**, and explicitly identifies **147.8 mm as stock**. It also reports the stock link weight as **280 g**.

Source:
https://www.mxfactoryparts.co.uk/product-page/luxon-adjustable-link-arm-w-bearings-honda-crf250r-rx-crf450r-rx-2025-2026

Physics V2 classification:

- pullrod c-c length 147.8 mm: **AFTERMARKET MEASUREMENT / high-confidence stock proxy**
- stock pullrod mass 280 g: **AFTERMARKET MEASUREMENT / useful component-mass proxy**

Do not relabel either as Honda-published data.

Ride Engineering independently confirms that millimeter-scale pullrod-length changes materially alter 2025 CRF ride height and leverage; its adjustable 2025 link has a performance position that lowers the rear about 6 mm and a lowering position around 22 mm.

Source:
https://ride-engineering.com/2025-CRF450rCRF250r450rx250rx-Adjustable-Link_p_2104.html

### 3.4 Replacement-shock geometry proxy

Ohlins' direct 2025 CRF450R TTX Flow replacement shock (`DMX1220`) is published as:

- eye-to-eye length: **452.0 mm**
- metal-to-metal stroke: **133.0 mm**

Source:
https://www.teknikmotorsport.com/ohlins-dmx1220-ttx-flow-dv-shock-rear-for-honda-crf-450-r-2025

These are **COMPATIBLE-PART PROXY** dimensions, not claimed OEM Showa measurements. However, a direct-fit replacement strongly constrains the acceptable OEM mounting envelope.

Using Honda's 12.4 in = 314.96 mm rear-wheel travel and the 133 mm compatible shock stroke gives an average full-travel wheel/shock displacement ratio of:

`314.96 / 133 = 2.368`

This **2.37 average ratio is only a sanity bound**. Pro-Link is progressive, so the local motion ratio varies through the stroke and must not be replaced with a constant 2.37 in the final model.

### 3.5 What is still missing for actual Pro-Link kinematics

We still do not have enough information to compute the axle-to-shock motion-ratio curve. The following coordinates/lengths remain required:

- chassis/frame pivot of the cushion arm
- swingarm-side pullrod pivot
- cushion-arm-to-shock pivot
- swingarm pivot position
- upper shock mount position
- rear axle position relative to swingarm pivot through travel
- exact stock Showa shock eye-to-eye and usable shaft stroke, unless the compatible Ohlins dimensions are later confirmed equal by direct measurement

Once those are known, the linkage can be solved purely from rigid-link geometry and the 52 N/mm shock spring can be transformed into the true progressive rear-wheel rate.

### 3.6 Practical fallback if factory hardpoints stay unavailable

A complete mathematical linkage curve can be identified without reverse-engineering proprietary source material by measuring a physical 2025-family chassis:

1. remove spring or use a shock-length fixture;
2. record rear-axle vertical displacement at 5-10 mm increments of shock displacement across full usable travel;
3. fit a monotone cubic spline `x_shock(y_axle)`;
4. derive local displacement ratio `dy_axle/dx_shock` numerically;
5. verify endpoints against published wheel travel and measured shock stroke;
6. preserve raw points and measurement uncertainty.

That empirical motion-ratio curve is sufficient for the game even if every linkage hardpoint is not reconstructed.

---

## 4. Gate status after this pass

### Gate C — powertrain

**PARTIALLY READY, substantially improved.**

Now available:

- OEM engine architecture
- real primary/gear/final ratios
- clutch plate/spring count and service dimensions
- measured 2025 peak power/torque anchors
- coarse full-range dyno trace for interface/testing
- defensible >105 N m full-engagement clutch-capacity lower bound
- qualitative evidence for reduced 2025 engine braking

Still blocked:

- archived pixel-accurate dyno digitization
- engine equivalent rotational inertia
- quantitative engine-braking/loss map
- actual clutch capacity and slip-friction law
- clutch rotating inertia
- drivetrain loss/efficiency characterization

### Gate B — rear suspension kinematics

**PARTIALLY READY, but not enough for final runtime kinematics.**

Now available:

- 2025-specific OEM linkage part identities
- one-piece linkage architecture
- stock-link 147.8 mm c-c third-party measurement
- stock-link 280 g third-party mass measurement
- compatible replacement-shock 452 mm eye-to-eye / 133 mm stroke proxy
- ~2.37 average full-travel displacement-ratio sanity bound
- 52 N/mm stock spring
- published rear-wheel travel

Still blocked:

- bell-crank hardpoints or an empirical axle-vs-shock curve
- exact OEM Showa eye-to-eye/stroke confirmation
- compression/rebound damping force curves
- bump-stop/end-stroke force law

---

## 5. Next research order

1. Search for or construct a pixel-accurate digitization of the 2025 Dirt Rider dyno trace and archive the extracted points.
2. Search 2025 CRF250R/450R suspension specialists, service/CAD sources, and measurement posts for bell-crank/hardpoint dimensions; the shared K95-AG0 parts broaden the search pool.
3. Search Showa/suspension-shop dyno data for the stock 2025 fork and shock. If unavailable, lock an empirical force-identification protocol instead of assigning damping coefficients.
4. Search for direct wheel, clutch, crank, and linkage component masses/inertias; otherwise define bounded measurement procedures.
5. Do not migrate runtime physics until the remaining A-D gate blockers have defensible initial values.
