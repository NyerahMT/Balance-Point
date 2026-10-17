# Physics V3 grounded steering

Horizontal touch input is turn intent. The bars are the lean actuator.

Grounded behavior:
- walking-speed steering stays direct, with a fading upright moment so the bike does not flop at a standstill;
- above walking speed that external roll moment fades out, because a roll torque at the center of gravity has to be cancelled by tire lateral force and saturates the front tire;
- a right-turn request first countersteers left, which is what rolls the chassis right;
- once that lean exists, the bars hold the bicycle-model steer angle for the actual lean, not the requested lean;
- no artificial steering-head yaw kick is injected. Yaw comes from the front contact force.

Airborne steering/attitude behavior is unchanged.

## 2026-10-17 turn-release stability regression

Turn-to-neutral regression tests exposed a sustained ~12-cycle roll weave over
five seconds on both right and left releases. The previous short endpoint test
could not see the intermediate oscillation. For the same deterministic turn,
a parameter sweep found materially better decay with:
- countersteer proportional gain: `0.85 → 0.20`
- countersteer roll-rate gain: `0.95 → 0.15`
- steer servo rate damping: `15 → 25`

The selected calibration preserved ~14° of lean with a positive into-turn
steer angle after two seconds and eliminated large post-release reversals
in the deterministic trial. Unit tests now protect
**the full turn-to-neutral transient**, not just a final roll measurement.
This is a controller calibration change, not a modified tire or suspension
model. Real-device handling and slower/faster-speed tests are still required.
