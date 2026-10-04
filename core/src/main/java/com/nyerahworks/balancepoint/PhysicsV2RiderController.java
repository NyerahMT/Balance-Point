package com.nyerahworks.balancepoint;

/**
 * Phone turn intent to handlebar torque.
 *
 * One loop owns lean: the plant roll assist. This controller only servos the bars
 * onto a speed-scaled angle so it cannot wag against that lean loop. Low speed is
 * direct steer. At pace the bars hold the small countersteer a given lean needs,
 * which is what MX vs ATV Legends hides from the player.
 */
final class PhysicsV2RiderController {
    private static final float G = 9.80665f;
    private static final float WHEELBASE = 1.48082f;
    private static final float MAX_LEAN = radians(28f);
    private static final float LOW_SPEED_STEER = radians(24f);
    private static final float STEER_KP = 7.5f;
    private static final float STEER_KD = 1.8f;
    private static final float MAX_HANDLEBAR_TORQUE = 8.0f;

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
        float blend = clamp(dt * 5f, 0f, 1f);
        filteredCommand += (command - filteredCommand) * blend;

        float speed = Math.abs(forwardSpeed);
        float highSpeedBlend = smoothstep(2.5f, 6.0f, speed);
        float leanAuthority = smoothstep(1.5f, 8.0f, speed);
        float shaped = filteredCommand * (0.75f + 0.25f * Math.abs(filteredCommand));

        float directSteer = shaped * LOW_SPEED_STEER;
        float leaned = shaped * MAX_LEAN * leanAuthority;
        float countersteer = 0f;
        if (speed > 2.5f) {
            countersteer = -WHEELBASE * G * (float)Math.tan(leaned)
                    / Math.max(speed * speed, 4f);
            countersteer = clamp(countersteer, -radians(7f), radians(7f));
        }
        float steerTarget = directSteer + (countersteer - directSteer) * highSpeedBlend;

        float torque = STEER_KP * (steerTarget - steerAngle) - STEER_KD * steerRate;
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
