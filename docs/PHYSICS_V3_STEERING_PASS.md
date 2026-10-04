# Physics V3 grounded steering correction

The live V3 plant now treats horizontal touch input as intuitive turn intent instead of closing a roll-error countersteer loop.

Grounded behavior:
- walking-speed steering remains direct;
- above walking speed, requested lean is established before same-direction front steer is blended in;
- the previous roll-error countersteer reversal is removed;
- the artificial opposite-yaw steering-head reaction is removed;
- grounded roll stabilization is stronger and near critically damped;
- front-tire aligning torque is reduced at the steering servo to avoid exciting weave.

Airborne steering/attitude behavior is unchanged.

The guarded migration passed the full 76-test core suite before commit `9a472bf4924b7d15d3013258bd5dde48d3bec70d` was pushed to `main`.
