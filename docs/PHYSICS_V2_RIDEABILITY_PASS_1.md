# Physics V2 Rideability Pass 1

This pass responds to the first on-device Physics V2 test.

Observed failures:
- modest steering input produced an unrecoverable roll fall;
- approximately 60% throttle could generate a spurious wheelie transient;
- suspension motion felt excessively loose/soft.

Corrections:
- corrected the tire camber-thrust sign so camber force acts toward the lean direction;
- corrected the wheel/chassis force path so tire tangential force enters through the axle while chain/brake torque reacts at the hub;
- separated sprung and unsprung mass for the suspension force path;
- transmitted spring/damper forces into the sprung chassis instead of injecting tire normal force directly into it;
- recomputed first-pass neutral suspension compression from sprung axle loads;
- added wheel gyroscopic bearing reactions and corrected Euler cross-coupling signs;
- added an explicit virtual-rider steering controller that countersteers and regulates requested lean using physical handlebar torque;
- connected the existing fore/aft rider control to the V2 plant;
- added acceleration-dependent rider bracing through the existing rider-mass model instead of an anti-wheelie force or pitch clamp.

No wheelie limiter, roll clamp, jump mode, landing mode, or direct attitude correction was added.

Validated before commit with the full core suite plus dedicated tests for moderate-throttle wheelie behavior, regulated roll response, suspension heave settling, and rider-position effect on hard-launch pitch.
