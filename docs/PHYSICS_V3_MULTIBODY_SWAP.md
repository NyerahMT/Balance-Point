# Physics V3 Multibody Swap

## Decision

Physics V2 is frozen as a reference implementation. The live motorcycle runtime is being
moved to `PhysicsV3MultibodyPlant` rather than continuing to tune V2.

## Open-source references

### TUMFTM motorcycle_model

Repository: `TUMFTM/motorcycle_model`
License: LGPL-3.0

Physics V3 follows the model topology and control architecture rather than embedding MBSim:

- chassis rigid-body translation and rotation;
- steering generalized coordinate;
- front and rear suspension generalized coordinates;
- front and rear wheel spin coordinates;
- rider generalized coordinates;
- lateral rider controller that requests roll through steering;
- continuously integrated states across contact transitions.

The TUM model remains an external reference. No MBSim runtime is linked into the mobile app.

### moto-sim

Repository: `dikshant1103-beep/moto-sim`
License: MIT

`PhysicsV3PacejkaTire` ports the compact analytical Pacejka/relaxation structure from the
MIT-licensed C++ implementation. Balance Point supplies its own MX hardpack coefficients.

## Motocross-specific extension

The road-only pitch and ground constraints present in several public motorcycle models are
not carried into Physics V3. Terrain interaction is unilateral:

- a tire can push on terrain but cannot pull on it;
- tire longitudinal/lateral forces are exactly zero without normal load;
- both wheel contacts may disappear simultaneously;
- no pitch spring, height spring, or attitude controller is enabled by being airborne;
- wheel angular momentum, steering, suspension, rider motion, gravity and rigid-body state
  continue through takeoff and landing without state reseeding.

The grounded rider-balance and anti-loop controllers are explicitly contact-gated gameplay
controllers. They are not part of the free-flight plant.

## Parameter source

The first CRF450R parameter set reuses the documented Physics V2 research values for mass,
wheelbase, CG, inertia, suspension travel/rates, drivetrain ratios and MX33 effective radii.
Those values are independent from the discarded V2 equations.
