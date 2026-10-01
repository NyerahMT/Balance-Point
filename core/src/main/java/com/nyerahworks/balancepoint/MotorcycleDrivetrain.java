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
    private static final float IDLE_RPM = 1_900f;
    private static final float REDLINE_RPM = 11_500f;
    private static final float HARD_LIMIT_RPM = 11_900f;

    private static final float PRIMARY_RATIO = 2.739f;
    // 49/13 is a common modern 450 MX final drive (e.g. CRF450R).
    private static final float FINAL_DRIVE_RATIO = 49f / 13f;
    private static final float DRIVETRAIN_EFFICIENCY = 0.92f;
    private static final float PEAK_TORQUE_NM = 46f;

    // Close-ratio five-speed set representative of a modern 450-class MX bike.
    private static final float[] GEAR_RATIOS = {1.800f, 1.470f, 1.235f, 1.050f, 0.909f};

    private static final float SHIFT_DURATION = 0.115f;
    private static final float MIN_SHIFT_INTERVAL = 0.16f;
    private static final float LIMITER_CUT_SECONDS = 0.055f;
    private static final float LIMITER_RESET_RPM = 10_900f;

    private final float wheelRadius;

    private int gear = 1;
    private float rpm = IDLE_RPM;
    private float shiftTimer;
    private float shiftLockout;
    private float limiterCutTimer;

    MotorcycleDrivetrain(float wheelRadius) {
        this.wheelRadius = wheelRadius;
    }

    void reset() {
        gear = 1;
        rpm = IDLE_RPM;
        shiftTimer = 0f;
        shiftLockout = 0f;
        limiterCutTimer = 0f;
    }

    float update(float speed, float throttle, float dt) {
        float absSpeed = Math.abs(speed);
        shiftTimer = Math.max(0f, shiftTimer - dt);
        shiftLockout = Math.max(0f, shiftLockout - dt);
        limiterCutTimer = Math.max(0f, limiterCutTimer - dt);

        float wheelRpm = absSpeed / (MathUtils.PI2 * wheelRadius) * 60f;
        float coupledRpm = wheelRpm * overallRatio(gear);

        // Below jogging speed an automatic clutch allows the motor to rev above wheel-coupled RPM.
        // As speed rises the clutch locks and engine speed becomes fully dictated by wheel speed.
        // Automatic launch clutch: let the 450 work in its broad midrange instead of
        // free-revving near the limiter while barely coupling the rear wheel.
        float freeRevRpm = IDLE_RPM + throttle * 5_100f;
        float clutchLock = MathUtils.clamp(absSpeed / 6.5f, 0f, 1f);
        float targetRpm = MathUtils.lerp(Math.max(IDLE_RPM, freeRevRpm),
                Math.max(IDLE_RPM, coupledRpm), clutchLock);
        targetRpm = Math.min(targetRpm, HARD_LIMIT_RPM + 250f);

        // A real hard limiter repeatedly cuts combustion, lets rpm fall, then re-fires.
        // The previous all-soft limiter could reduce wheel torque to zero before the engine
        // ever reached an audible cut. Trigger near the hard limit and pull the target down
        // for ~55 ms so RPM and sound both visibly/audibly bounce.
        if (limiterCutTimer <= 0f && throttle > 0.45f
                && rpm >= HARD_LIMIT_RPM - 90f && targetRpm >= REDLINE_RPM) {
            limiterCutTimer = LIMITER_CUT_SECONDS;
        }
        if (limiterCutTimer > 0f) {
            targetRpm = Math.min(targetRpm, LIMITER_RESET_RPM);
        }

        float rpmResponse = targetRpm < rpm ? 26f : 18f;
        rpm += (targetRpm - rpm) * Math.min(1f, dt * rpmResponse);
        rpm = Math.min(rpm, HARD_LIMIT_RPM + 40f);

        float torque = PEAK_TORQUE_NM * torqueCurve(rpm);
        float clutchEngagement = MathUtils.clamp(0.70f + absSpeed / 6.0f, 0.70f, 1f);

        // A shift briefly unloads the gearbox instead of teleporting from one ratio to the next at
        // full torque. The cut recovers progressively over ~115 ms.
        float shiftAuthority = 1f;
        if (shiftTimer > 0f) {
            float progress = 1f - shiftTimer / SHIFT_DURATION;
            shiftAuthority = MathUtils.lerp(0.08f, 1f, MathUtils.clamp(progress, 0f, 1f));
        }

        // Keep a mild pre-limit torque rolloff, but do not fade all the way to zero before
        // the hard cut can occur. During the cut, ignition torque is fully removed.
        float limiterApproach = MathUtils.clamp((rpm - (REDLINE_RPM - 150f))
                / (HARD_LIMIT_RPM - (REDLINE_RPM - 150f)), 0f, 1f);
        float limiter = 1f - 0.25f * limiterApproach;
        if (limiterCutTimer > 0f) limiter = 0f;

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
        // Broad modern 450 single: huge midrange, peak torque around 7k,
        // then a real top-end roll-off instead of carrying torque to 13k.
        if (rpm < 3_000f) {
            return MathUtils.lerp(0.60f, 0.73f,
                    MathUtils.clamp((rpm - IDLE_RPM) / (3_000f - IDLE_RPM), 0f, 1f));
        }
        if (rpm < 5_500f) {
            return MathUtils.lerp(0.73f, 0.92f, (rpm - 3_000f) / 2_500f);
        }
        if (rpm < 7_000f) {
            return MathUtils.lerp(0.92f, 1.00f, (rpm - 5_500f) / 1_500f);
        }
        if (rpm < 8_500f) {
            return MathUtils.lerp(1.00f, 0.98f, (rpm - 7_000f) / 1_500f);
        }
        if (rpm < 9_500f) {
            return MathUtils.lerp(0.98f, 0.95f, (rpm - 8_500f) / 1_000f);
        }
        if (rpm < 10_500f) {
            return MathUtils.lerp(0.95f, 0.84f, (rpm - 9_500f) / 1_000f);
        }
        if (rpm < REDLINE_RPM) {
            return MathUtils.lerp(0.84f, 0.68f,
                    (rpm - 10_500f) / (REDLINE_RPM - 10_500f));
        }
        return 0.60f;
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

    boolean isLimiterCut() {
        return limiterCutTimer > 0f;
    }
}
