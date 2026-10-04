from pathlib import Path
import re

PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/PhysicsV3MultibodyPlant.java")
text = PATH.read_text()
original = text

text = text.replace(
    "    private static final float STEER_SERVO_KP = 72f;\n"
    "    private static final float STEER_SERVO_KD = 8.5f;\n"
    "    private static final float STEER_ROLL_ERROR_GAIN = 0.62f;\n",
    "    // Grounded steering is intentionally direct and overdamped. Player input is turn\n"
    "    // intent, not a request for an autonomous countersteer/lean feedback loop.\n"
    "    private static final float STEER_SERVO_KP = 46f;\n"
    "    private static final float STEER_SERVO_KD = 15f;\n"
    "    private static final float STEER_ALIGNING_GAIN = 0.25f;\n",
)

text = text.replace(
    "    private static final float BALANCE_ROLL_KP = 118f;\n"
    "    private static final float BALANCE_ROLL_KD = 58f;\n"
    "    private static final float MAX_BALANCE_ROLL_TORQUE = 135f;\n",
    "    // Strong, near-critically-damped grounded rider balance. This is a game-facing\n"
    "    // rider assist and never runs when both tires are unloaded.\n"
    "    private static final float BALANCE_ROLL_KP = 520f;\n"
    "    private static final float BALANCE_ROLL_KD = 300f;\n"
    "    private static final float MAX_BALANCE_ROLL_TORQUE = 500f;\n",
)

pattern = re.compile(
    r"    private void updateSteering\(float speedForward, float dt\) \{.*?\n"
    r"    \}\n\n    private void integrateSuspension",
    re.S,
)
replacement = '''    private void updateSteering(float speedForward, float dt) {
        float speed = Math.abs(speedForward);
        boolean anyGround = rearGrounded() || frontGrounded();
        float controllerTorque;

        if (anyGround) {
            float command = clamp(input.steer, -1f, 1f);
            float authority = smoothstep(1.0f, 6.0f, speed);
            float targetRoll = -command * MAX_TARGET_LEAN * authority;

            // At walking speed the bars behave directly. Once the bike is moving, player
            // input first establishes lean in the requested direction; same-direction front
            // steer is fed in only as that lean develops. This avoids both the autonomous
            // countersteer reversal and the opposite-roll kick caused by instantly steering
            // a still-upright motorcycle into the turn.
            float lowSpeedBlend = 1f - smoothstep(0.6f, 2.6f, speed);
            float directionalLean = -Math.signum(command) * roll();
            float targetLeanMagnitude = Math.abs(targetRoll);
            float leanProgress = targetLeanMagnitude > radians(1f)
                    ? clamp(directionalLean / Math.max(targetLeanMagnitude * 0.70f, radians(3f)), 0f, 1f)
                    : 1f;

            float speedSq = Math.max(speed * speed, 9f);
            float cornerMagnitude = (float)Math.atan(
                    WHEELBASE * G * Math.tan(targetLeanMagnitude) / speedSq);
            float cornerSteer = Math.signum(command) * cornerMagnitude * leanProgress;
            float lowSpeedSteer = command * MAX_STEER_ANGLE * lowSpeedBlend;
            float desiredSteer = clamp(
                    lowSpeedSteer + cornerSteer * (1f - lowSpeedBlend),
                    -MAX_STEER_ANGLE,
                    MAX_STEER_ANGLE);

            controllerTorque = STEER_SERVO_KP * (desiredSteer - steerAngle)
                    - STEER_SERVO_KD * steerRate;
        } else {
            // Airborne behavior intentionally unchanged: no steering recenter servo.
            controllerTorque = -2.2f * steerRate;
        }

        float tireAligning = frontGrounded()
                ? STEER_ALIGNING_GAIN * frontTireForce.mz : 0f;
        float steerTorque = clamp(
                controllerTorque + tireAligning,
                -MAX_STEER_TORQUE,
                MAX_STEER_TORQUE);
        steerRate += steerTorque / STEER_INERTIA * dt;
        steerAngle += steerRate * dt;

        if (steerAngle > MAX_STEER_ANGLE) {
            steerAngle = MAX_STEER_ANGLE;
            steerRate = Math.min(0f, steerRate);
        } else if (steerAngle < -MAX_STEER_ANGLE) {
            steerAngle = -MAX_STEER_ANGLE;
            steerRate = Math.max(0f, steerRate);
        }

        // Do not inject an artificial equal/opposite yaw kick into the chassis. The front
        // contact force already produces the yaw moment about the CG. The old -0.18 reaction
        // made the bike initially yaw opposite the player's command and contributed to weave.
    }

    private void integrateSuspension'''
text, count = pattern.subn(replacement, text)
if count != 1:
    raise SystemExit(f"expected one updateSteering block, replaced {count}")

if text == original:
    raise SystemExit("migration made no changes")
if "STEER_ROLL_ERROR_GAIN" in text:
    raise SystemExit("legacy roll-error countersteer survived")
if "-0.18f * steerTorque" in text:
    raise SystemExit("legacy artificial yaw reaction survived")

PATH.write_text(text)
print("Applied V3 grounded steering sanity pass")
