package com.nyerahworks.balancepoint;

/** Converts phone turn intent into physical handlebar torque. */
final class PhysicsV2RiderController {
    private static final float G = 9.80665f;
    private static final float WHEELBASE = 1.48082f;
    private static final float MAX_LEAN = radians(28f);
    private static final float MAX_HANDLEBAR_TORQUE = 10.0f;
    private static final float LOW_SPEED_TORQUE = 3.0f;
    private static final float ROLL_KP = 16.0f;
    private static final float ROLL_KD = 4.5f;
    private static final float STEER_KP = 6.0f;
    private static final float MAX_TARGET_ROLL_RATE = 1.8f;

    private float filteredCommand;

    void reset() {
        filteredCommand = 0f;
    }

    float normalizedTorque(
            float command,
            float forwardSpeed,
            float roll,
            float rollRate,
            float steerAngle,
            float steerRate,
            float plantMaxTorque,
            float dt) {
        command = clamp(command, -1f, 1f);
        float blend = clamp(dt * 9f, 0f, 1f);
        filteredCommand += (command - filteredCommand) * blend;

        float speed = Math.abs(forwardSpeed);
        float highSpeedBlend = smoothstep(2.2f, 5.5f, speed);
        float leanAuthority = smoothstep(1.5f, 8.0f, speed);
        float shaped = filteredCommand * (0.70f + 0.30f * Math.abs(filteredCommand));

        // Positive player command means right turn. Right lean is negative roll here.
        float targetRoll = -shaped * MAX_LEAN * leanAuthority;
        float rollError = targetRoll - roll;
        float targetRollRate = clamp(
                rollError * 4.2f,
                -MAX_TARGET_ROLL_RATE,
                MAX_TARGET_ROLL_RATE);

        float targetSteer = 0f;
        if (speed > 1.0f) {
            targetSteer = -WHEELBASE * G * (float)Math.tan(targetRoll)
                    / Math.max(speed * speed, 1.0f);
            targetSteer = clamp(targetSteer, -radians(12f), radians(12f));
        }

        float highSpeedTorque = ROLL_KP * rollError
                + ROLL_KD * (targetRollRate - rollRate)
                + STEER_KP * (targetSteer - steerAngle);
        float lowSpeedTorque = filteredCommand * LOW_SPEED_TORQUE
                - 2.2f * steerAngle - 0.35f * steerRate;
        float torque = lowSpeedTorque
                + (highSpeedTorque - lowSpeedTorque) * highSpeedBlend;
        torque = clamp(torque, -MAX_HANDLEBAR_TORQUE, MAX_HANDLEBAR_TORQUE);
        return torque / Math.max(plantMaxTorque, 0.001f);
    }

    private static float smoothstep(float lo, float hi, float x) {
        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
