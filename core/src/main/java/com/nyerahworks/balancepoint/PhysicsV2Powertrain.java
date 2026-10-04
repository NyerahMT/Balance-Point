package com.nyerahworks.balancepoint;

/** Physical rotational drivetrain for Physics V2. */
final class PhysicsV2Powertrain {
    private static final float PRIMARY = 2.357f;
    private static final float FINAL = 49f / 13f;
    private static final float[] GEARS = {2.133f, 1.706f, 1.421f, 1.211f, 1.043f};
    private static final float ENGINE_INERTIA = 0.0065f;
    private static final float EFFICIENCY = 0.95f;
    private static final float CLUTCH_CAPACITY = 150f;
    private static final float CLUTCH_SLIP_SCALE = 20f;
    private static final float IDLE_RPM = 2000f;
    private static final float LIMIT_RPM = 11_500f;
    private static final float LIMIT_RESET_RPM = 10_950f;
    private static final float SHIFT_TIME = 0.105f;

    private static final float[] CURVE_RPM = {
            2500, 3000, 4000, 5000, 6000, 6900, 7500, 8500, 9000, 9600, 10200, 10800, 11200
    };
    private static final float[] REAR_EQ_TORQUE = {
            30.0f, 32.5f, 37.0f, 40.5f, 43.5f, 44.61f, 44.2f, 42.5f, 40.8f, 37.70f, 33.0f, 27.0f, 20.0f
    };

    private int gear = 1;
    private float engineOmega = rpmToOmega(IDLE_RPM);
    private float engineAlpha;
    private float shiftTimer;
    private boolean limiterCut;
    private float lastClutchTorque;
    private float lastRearDriveTorque;

    void reset() {
        gear = 1;
        engineOmega = rpmToOmega(IDLE_RPM);
        engineAlpha = 0f;
        shiftTimer = 0f;
        limiterCut = false;
        lastClutchTorque = 0f;
        lastRearDriveTorque = 0f;
    }

    /**
     * Integrates crank speed and returns torque delivered to the rear wheel shaft.
     * clutchCommand is a physical plate-normal-force command in the range 0..1.
     */
    float step(float rearWheelOmega, float throttle, float clutchCommand, float dt) {
        throttle = clamp(throttle, 0f, 1f);
        clutchCommand = clamp(clutchCommand, 0f, 1f);
        shiftTimer = Math.max(0f, shiftTimer - dt);

        float rpm = getRpm();
        if (!limiterCut && rpm >= LIMIT_RPM) limiterCut = true;
        if (limiterCut && rpm <= LIMIT_RESET_RPM) limiterCut = false;

        float combustion = limiterCut ? 0f : throttle * torqueCurve(rpm);
        // Explicit idle-air/fueling authority: this acts as combustion torque, not an RPM setter.
        if (rpm < IDLE_RPM + 250f && throttle < 0.12f) {
            combustion += clamp((IDLE_RPM - rpm) * 0.012f, 0f, 12f);
        }
        float loss = lossTorque(rpm);

        float gearboxInputOmega = rearWheelOmega * FINAL * GEARS[gear - 1];
        float clutchOuterOmega = engineOmega / PRIMARY;
        float slip = clutchOuterOmega - gearboxInputOmega;
        float shiftAuthority = shiftTimer <= 0f
                ? 1f : clamp(0.12f + 0.88f * (1f - shiftTimer / SHIFT_TIME), 0.12f, 1f);
        float engagement = clutchCommand * shiftAuthority;
        float clutchTorque = engagement * CLUTCH_CAPACITY
                * (float)Math.tanh(slip / CLUTCH_SLIP_SCALE);

        float crankReaction = clutchTorque / PRIMARY;
        engineAlpha = (combustion - loss - crankReaction) / ENGINE_INERTIA;
        engineOmega += engineAlpha * dt;
        if (engineOmega < 0f) engineOmega = 0f;

        lastClutchTorque = clutchTorque;
        lastRearDriveTorque = clutchTorque * GEARS[gear - 1] * FINAL * EFFICIENCY;
        return lastRearDriveTorque;
    }

    boolean shiftUp() {
        if (shiftTimer > 0f || gear >= GEARS.length) return false;
        gear++;
        shiftTimer = SHIFT_TIME;
        return true;
    }

    boolean shiftDown(float rearWheelOmega) {
        if (shiftTimer > 0f || gear <= 1) return false;
        int lower = gear - 1;
        float projected = Math.abs(rearWheelOmega) * FINAL * GEARS[lower - 1] * PRIMARY;
        if (omegaToRpm(projected) > LIMIT_RPM * 1.02f) return false;
        gear = lower;
        shiftTimer = SHIFT_TIME;
        return true;
    }

    int getGear() {
        return gear;
    }

    float getRpm() {
        return omegaToRpm(engineOmega);
    }

    float getRedlineRpm() {
        return LIMIT_RPM;
    }

    float getEngineOmega() {
        return engineOmega;
    }

    float getEngineAlpha() {
        return engineAlpha;
    }

    float getLastClutchTorque() {
        return lastClutchTorque;
    }

    float getLastRearDriveTorque() {
        return lastRearDriveTorque;
    }

    boolean isShifting() {
        return shiftTimer > 0f;
    }

    boolean isLimiterCut() {
        return limiterCut;
    }

    private static float torqueCurve(float rpm) {
        if (rpm <= CURVE_RPM[0]) return REAR_EQ_TORQUE[0] / EFFICIENCY;
        for (int i = 1; i < CURVE_RPM.length; i++) {
            if (rpm <= CURVE_RPM[i]) {
                float t = (rpm - CURVE_RPM[i - 1]) / (CURVE_RPM[i] - CURVE_RPM[i - 1]);
                float rear = REAR_EQ_TORQUE[i - 1]
                        + (REAR_EQ_TORQUE[i] - REAR_EQ_TORQUE[i - 1]) * t;
                return rear / EFFICIENCY;
            }
        }
        return 0f;
    }

    private static float lossTorque(float rpm) {
        if (rpm <= 2000f) return 2.5f;
        if (rpm >= 11500f) return 12.7f;
        if (rpm <= 4000f) return lerp(2.5f, 4.1f, (rpm - 2000f) / 2000f);
        if (rpm <= 6000f) return lerp(4.1f, 6.2f, (rpm - 4000f) / 2000f);
        if (rpm <= 8000f) return lerp(6.2f, 8.6f, (rpm - 6000f) / 2000f);
        if (rpm <= 10000f) return lerp(8.6f, 11.0f, (rpm - 8000f) / 2000f);
        return lerp(11.0f, 12.7f, (rpm - 10000f) / 1500f);
    }

    private static float rpmToOmega(float rpm) {
        return rpm * (float)(Math.PI * 2.0 / 60.0);
    }

    private static float omegaToRpm(float omega) {
        return omega * (float)(60.0 / (Math.PI * 2.0));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
