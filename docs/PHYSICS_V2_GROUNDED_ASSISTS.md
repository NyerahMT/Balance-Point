# Physics V2 grounded rideability assists

Runtime baseline: `ec9e9ea675854c3e84045295ed386d1f70b8062f`.

This pass intentionally restores game-facing assists only while the motorcycle retains ground contact.

Active grounded assists:
- additional steering-rate damping while at least one tire is grounded;
- commanded-lean roll stabilization while at least one tire is grounded;
- rear-contact anti-wheelie torque management, including drive-torque trim as pitch/rise rate becomes excessive;
- rear-contact nose-down pitch catch above the wheelie target.

Airborne policy:
- no added roll, pitch, yaw or steering attitude assist when both tires are airborne;
- anti-wheelie drive trimming ends when the rear tire leaves the ground;
- the existing Physics V2 airborne drivetrain/wheel-reaction behavior is otherwise unchanged.

The guarded migration passed the repository quality gate and all 60 core tests before the runtime commit was accepted.
