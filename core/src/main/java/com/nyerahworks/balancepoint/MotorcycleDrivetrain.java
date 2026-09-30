package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Lightweight five-speed dirt-bike drivetrain.
 *
 * The gearbox is sequential with an automatic launch clutch, but shifting is player controlled.
 * Engine RPM is tied to rear-wheel speed once the clutch is engaged, so each ratio has a real
 * effect on acceleration, wheelie authority and engine sound.
 */
final class MotorcycleDrivetrain {
    private static final float IDLE_RPM = 1_800f;
    private static final float REDLINE_RPM = 13_200f;
    private static final float HARD_LIMIT_RPM = 13_700f;

    private static final float PRIMARY_RATIO = 2.739f;
    private static final float FINAL_DRIVE_RATIO = 4.00f;
    private static final float DRIVETRAIN_EFFICIENCY = 0.92f;
    private static final float PEAK_TORQUE_NM = 46f;

    // Close-ratio five-speed set representative of a modern 450-class MX bike.
    private static final float[] GEAR_RATIOS = {1.800f, 1.470f, 1.235f, 1.050f, 0.909f};

    private static final float SHIFT_DURATION = 0.115f;
    private static final float MIN_SHIFT_INTERVAL = 0.16f;

    private final float wheelRadius;

    private int gear = 1;
    private float rpm = IDLE_RPM;
    private float shiftTimer;
    private float shiftLockout;

    MotorcycleDrivetrain(float wheelRadius) {
        this.wheelRadius = wheelRadius;
    }

    void reset() {
        gear = 1;
        rpm = IDLE_RPM;
        shiftTimer = 0f;
        shiftLockout = 0f;
    }

    float update(float speed, float throttle, float dt) {
        float absSpeed = Math.abs(speed);
        shiftTimer = Math.max(0f, shiftTimer - dt);
        shiftLockout = Math.max(0f, shiftLockout - dt);

        float wheelRpm = absSpeed / (MathUtils.PI2 * wheelRadius) * 60f;
        float coupledRpm = wheelRpm * overallRatio(gear);

        // Below jogging speed an automatic clutch allows the motor to rev above wheel-coupled RPM.
        // As speed rises the clutch locks and engine speed becomes fully dictated by wheel speed.
        float freeRevRpm = IDLE_RPM + throttle * 5_300f;
        float clutchLock = MathUtils.clamp(absSpeed / 5.0f, 0f, 1f);
        float targetRpm = MathUtils.lerp(Math.max(IDLE_RPM, freeRevRpm),
                Math.max(IDLE_RPM, coupledRpm), clutchLock);
        targetRpm = Math.min(targetRpm, HARD_LIMIT_RPM + 250f);

        float rpmResponse = targetRpm < rpm ? 24f : 18f;
        rpm += (targetRpm - rpm) * Math.min(1f, dt * rpmResponse);

        float torque = PEAK_TORQUE_NM * torqueCurve(rpm);
        float clutchEngagement = MathUtils.clamp(0.56f + absSpeed / 5.0f, 0.56f, 1f);

        // A shift briefly unloads the gearbox instead of teleporting from one ratio to the next at
        // full torque. The cut recovers progressively over ~115 ms.
        float shiftAuthority = 1f;
        if (shiftTimer > 0f) {
            float progress = 1f - shiftTimer / SHIFT_DURATION;
            shiftAuthority = MathUtils.lerp(0.08f, 1f, MathUtils.clamp(progress, 0f, 1f));
        }

        // Soft limiter starts just below redline and reaches zero at the hard limit.
        float limiter = 1f - MathUtils.clamp((rpm - (REDLINE_RPM - 250f))
                / (HARD_LIMIT_RPM - (REDLINE_RPM - 250f)), 0f, 1f);

        float wheelTorque = torque * overallRatio(gear) * DRIVETRAIN_EFFICIENCY;
        return throttle * wheelTorque / wheelRadius
                * clutchEngagement * shiftAuthority * limiter;
    }

    boolean shiftUp() {
        if (shiftLockout > 0f || gear >= GEAR_RATIOS.length) return false;
        gear++;
        beginShift();
        return true;
    }

    boolean shiftDown(float speed) {
        if (shiftLockout > 0f || gear <= 1) return false;

        int lowerGear = gear - 1;
        float wheelRpm = Math.abs(speed) / (MathUtils.PI2 * wheelRadius) * 60f;
        float projectedRpm = wheelRpm * overallRatio(lowerGear);
        if (projectedRpm > HARD_LIMIT_RPM) return false;

        gear = lowerGear;
        beginShift();
        return true;
    }

    private void beginShift() {
        shiftTimer = SHIFT_DURATION;
        shiftLockout = MIN_SHIFT_INTERVAL;
    }

    private static float torqueCurve(float rpm) {
        if (rpm < 3_000f) {
            return MathUtils.lerp(0.62f, 0.78f,
                    MathUtils.clamp((rpm - IDLE_RPM) / (3_000f - IDLE_RPM), 0f, 1f));
        }
        if (rpm < 6_500f) {
            return MathUtils.lerp(0.78f, 1.00f, (rpm - 3_000f) / 3_500f);
        }
        if (rpm < 9_500f) {
            return MathUtils.lerp(1.00f, 0.98f, (rpm - 6_500f) / 3_000f);
        }
        if (rpm < REDLINE_RPM) {
            return MathUtils.lerp(0.98f, 0.78f, (rpm - 9_500f) / (REDLINE_RPM - 9_500f));
        }
        return 0.72f;
    }

    private static float overallRatio(int oneBasedGear) {
        return PRIMARY_RATIO * FINAL_DRIVE_RATIO * GEAR_RATIOS[oneBasedGear - 1];
    }

    int getGear() {
        return gear;
    }

    float getRpm() {
        return rpm;
    }

    float getRedlineRpm() {
        return REDLINE_RPM;
    }

    boolean isShifting() {
        return shiftTimer > 0f;
    }
}
