# Physics V3 grounded steering

Horizontal touch input is turn intent. The bars are the lean actuator.

Grounded behavior:
- walking-speed steering stays direct, with a fading upright moment so the bike does not flop at a standstill;
- above walking speed that external roll moment fades out, because a roll torque at the center of gravity has to be cancelled by tire lateral force and saturates the front tire;
- a right-turn request first countersteers left, which is what rolls the chassis right;
- once that lean exists, the bars hold the bicycle-model steer angle for the actual lean, not the requested lean;
- no artificial steering-head yaw kick is injected. Yaw comes from the front contact force.

Airborne steering/attitude behavior is unchanged.
