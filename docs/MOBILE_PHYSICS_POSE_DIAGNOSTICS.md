# Mobile physics orientation and tire-frame diagnostics

The live Java/libGDX simulator uses a body quaternion for physics. Its graphics
must consume that same quaternion; yaw/roll/pitch reconstructed as separate
Euler rotations do not preserve orientation under combined lean and pitch.

- Physics body +X is right, +Y is up, and +Z is forward. Positive heading yaw
  toward +X is **positive** Y-axis mesh rotation in libGDX.
- The imported bike's measured steering axis is upward. The rendered forks and
  handlebars consume Physics V3's steering-joint angle in radians directly;
  player turn input and a synthetic countersteering visual are not substitutes.
- The rear visual anchor is rotated by the same quaternion as the chassis,
  preserving the physical rear-axle offset under simultaneous yaw, lean, pitch.
- The tire model computes camber from the original tilted wheel axis, but
  projects longitudinal and lateral force directions onto the terrain tangent
  plane before evaluating contact slip/forces. This avoids adding undesired
  vertical shear when leaned on a grade.
- The torque-only steering controller now uses a loaded front contact to
  command a target lean. With the front wheel lifted, steering only damps the
  bars rather than requesting lean through an unloaded contact.

For isolated diagnostics, call `PhysicsV3MultibodyPlant.setStabilizationScale(0f)`
in a test configuration to disable direct upright, roll/yaw damping and
anti-loop moments without altering tire friction or drivetrain forces.
`1f` preserves previous stabilizer gains. This is not a user-facing setting,
and no claim is made that turning stabilizers off is automatically more playable.

Validation must compare the physics forward basis with the graphics forward
axis, confirm the wheel joint angle visually during countersteering, test
static and moving lean, and sample front/rear contact tangency across graded
terrain. CI checks are necessary but not sufficient to validate game feel.

## Follow-up: turn-entry/wobble investigation

The original mobile handling felt reversed on turn-in and wobbled after a
steering event. Physics right-stick maps to positive heading yaw after a full
corner, but the initial high-speed countersteer is physically opposite, and
the prior target-roll controller drove this phase aggressively.

Changes in this pass:
- Reduce the target-lean/countersteer and handlebar torque servo gains, with
  less feedthrough of tire aligning moment. Right/left gamepad input is not
  silently inverted because right-turn physics regression expects right yaw.
- Preserve the physical tilted front/rear axle for gyroscopic torque; use
  separate ground-tangent wheel directions for tire slip/traction forces.
- Remove direct high-speed chassis roll-rate and yaw-rate moments entirely.
- Use 20% of former direct low-speed stabilizing and anti-loop torques on the
  mobile runtime; diagnostic physics unit tests may vary this scale.
- Quantify right turn, release, wheel wobble/steer sign reversals in JUnit.

A real device still must validate whether the countersteer phase is too
prominent or the physical assembly requires additional tuning. Do not reverse
touch controls without verifying the final physical turn direction.
