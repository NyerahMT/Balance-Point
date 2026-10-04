# Physics V2 grounded rideability assists

Runtime baseline: `ec9e9ea675854c3e84045295ed386d1f70b8062f`, balance-point revision `legends-balance-point-v1`.

Active grounded assists:
- commanded-lean roll stabilization while at least one tire is grounded, critically damped so a steer input leans in and holds instead of weaving;
- speed-scaled yaw-rate tracking so the stick arcs the bike the way MX vs ATV Legends does, without a second roll loop on the bars;
- handlebar torque only servos steer angle. It no longer regulates roll, which was fighting the lean assist;
- a rear-contact balance point, not an anti-wheelie clamp.

Balance point:
- The equilibrium angle is `atan(comAheadOfRear / comHeight)`. Gravity drops the front below it and loops the bike past it. That is the same inverted-pendulum point used by a real dirt bike and by MX vs ATV Legends with wheelie assist off.
- Rearward rider input shifts the combined CoM aft (lower point) and adds a nose-up pop, matching Legends right-stick-back. Forward input drops the nose.
- Throttle is not cut for pitch. Drive reaction lifts the front; tapping it holds the point. Rear brake adds a nose-down moment once the front is up, so a dab saves a high wheelie.
- A soft catch starts only about 7 degrees past the geometric point and is capped well below the old 1200 Nm anti-wheelie. That is the Legends raised wreck threshold: recoverable, not glued at 14 degrees and not an instant flip.
- Both-wheels-down pitch spring/damping settles porpoise without running during a wheelie.

Airborne policy:
- no added roll, pitch, yaw or steering attitude assist when both tires are airborne;
- the balance-point catch and brake pop end when the rear tire leaves the ground;
- the existing Physics V2 airborne drivetrain/wheel-reaction behavior is otherwise unchanged.

