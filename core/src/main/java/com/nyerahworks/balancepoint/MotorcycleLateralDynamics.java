package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Lightweight lateral motorcycle dynamics for steering, tire side force, yaw and lean.
 *
 * The controls describe where the rider wants the bike to go. The tires still have finite
 * lateral grip and can develop slip, but the model intentionally avoids requiring direct
 * countersteer micromanagement from a phone control pad.
 */
final class MotorcycleLateralDynamics {
    private static final float FRONT_CORNERING_GAIN = 7.0f;
    private static final float REAR_CORNERING_GAIN = 7.6f;
    private static final float YAW_INERTIA = 82f;
    private static final float YAW_DAMPING = 1.02f;
    private static final float MAX_YAW_RATE = 1.75f;
    private static final float MAX_LATERAL_SPEED = 3.0f;
    private static final float MAX_ROLL = 58f * MathUtils.degreesToRadians;

    private static final float REAR_LONGITUDINAL_GRIP_COST = 0.32f;
    private static final float REAR_MIN_LATERAL_GRIP_FRACTION = 0.72f;

    private final float mass;
    private final float wheelbase;
    private final float rearArm;
    private final float frontArm;
    private final float gravity;

    private float lateralSpeed;
    private float yawRate;
    private float roll;
    private float rollRate;
    private float steeringAngle;
    private float lateralAcceleration;

    MotorcycleLateralDynamics(float mass,
                              float wheelbase,
                              float comForward,
                              float gravity) {
        this.mass = mass;
        this.wheelbase = wheelbase;
        this.rearArm = comForward;
        this.frontArm = wheelbase - comForward;
        this.gravity = gravity;
    }

    void step(float speed,
              float steerCommand,
              float rearLongitudinalForce,
              float rearNormalLoad,
              float frontNormalLoad,
              float rearMu,
              float frontMu,
              boolean rearTouching,
              boolean frontTouching,
              float dt) {
        float absSpeed = Math.abs(speed);
        float motionBlend = smooth(MathUtils.clamp(absSpeed / 2.5f, 0f, 1f));
        float speedBlend = smooth(MathUtils.clamp(absSpeed / 22f, 0f, 1f));

        float maxSteer = MathUtils.lerp(29f, 5.5f, speedBlend)
                * MathUtils.degreesToRadians;
        steeringAngle = -steerCommand * maxSteer;

        boolean touching = rearTouching || frontTouching;
        if (touching) {
            float referenceSpeed = Math.max(absSpeed, 2.0f);
            float frontPointLateral = lateralSpeed + yawRate * frontArm;
            float rearPointLateral = lateralSpeed - yawRate * rearArm;
            float frontSlipAngle = frontTouching
                    ? (float) Math.atan2(frontPointLateral, referenceSpeed) - steeringAngle
                    : 0f;
            float rearSlipAngle = rearTouching
                    ? (float) Math.atan2(rearPointLateral, referenceSpeed)
                    : 0f;

            float frontLimit = Math.max(0f, frontMu * frontNormalLoad);
            float rearCircle = Math.max(0f, rearMu * rearNormalLoad);
            float rearDriveUse = rearCircle > 0.001f
                    ? MathUtils.clamp(Math.abs(rearLongitudinalForce) / rearCircle, 0f, 1f)
                    : 0f;
            float rearGripRetention = (float) Math.sqrt(Math.max(
                    0f, 1f - REAR_LONGITUDINAL_GRIP_COST * rearDriveUse * rearDriveUse));
            rearGripRetention = Math.max(REAR_MIN_LATERAL_GRIP_FRACTION, rearGripRetention);
            float rearLimit = rearCircle * rearGripRetention;

            float frontLateralForce = frontTouching
                    ? softLimit(-frontSlipAngle * frontNormalLoad * FRONT_CORNERING_GAIN,
                    frontLimit) * motionBlend
                    : 0f;
            float rearLateralForce = rearTouching
                    ? softLimit(-rearSlipAngle * rearNormalLoad * REAR_CORNERING_GAIN,
                    rearLimit) * motionBlend
                    : 0f;

            lateralAcceleration = (frontLateralForce + rearLateralForce) / mass;
            float bodyLateralAcceleration = lateralAcceleration - speed * yawRate;
            lateralSpeed += bodyLateralAcceleration * dt;

            float steerMagnitude = MathUtils.clamp(Math.abs(steerCommand), 0f, 1f);
            float lateralCapture;
            if (frontTouching && rearTouching) {
                lateralCapture = MathUtils.lerp(5.0f, 3.1f, steerMagnitude);
            } else if (rearTouching) {
                lateralCapture = 1.25f;
            } else {
                lateralCapture = 2.4f;
            }
            lateralSpeed *= Math.max(0f, 1f - dt * lateralCapture);
            lateralSpeed = MathUtils.clamp(
                    lateralSpeed, -MAX_LATERAL_SPEED, MAX_LATERAL_SPEED);

            float yawTorque = frontArm * frontLateralForce - rearArm * rearLateralForce;
            float yawAcceleration = yawTorque / YAW_INERTIA - yawRate * YAW_DAMPING;
            if (rearTouching && !frontTouching) {
                float wheelieAuthority = MathUtils.lerp(2.0f, 0.72f, speedBlend);
                yawAcceleration += -steerCommand * wheelieAuthority * motionBlend;
            }
            yawRate += yawAcceleration * dt;
        } else {
            // In flight there is no tire force available to bend the trajectory or recapture
            // sideslip. World-space velocity is owned by BalancePointGame while this class keeps
            // the motorcycle's attitude dynamics. Preserve yaw angular momentum with only light
            // aerodynamic damping rather than forcing the bike to point down its flight path.
            lateralAcceleration = 0f;
            yawRate *= Math.max(0f, 1f - dt * 0.08f);
        }

        yawRate = MathUtils.clamp(yawRate, -MAX_YAW_RATE, MAX_YAW_RATE);

        if (touching) {
            float forceRoll = -(float) Math.atan2(lateralAcceleration, gravity);
            float leanSpeedBlend = smooth(MathUtils.clamp((absSpeed - 1.0f) / 12f, 0f, 1f));
            float intentLimit = MathUtils.lerp(10f, 50f, leanSpeedBlend)
                    * MathUtils.degreesToRadians;
            float intentRoll = steerCommand * intentLimit;
            float intentWeight = frontTouching ? 0.30f : 0.20f;
            float rollTarget = MathUtils.clamp(
                    forceRoll * (1f - intentWeight) + intentRoll * intentWeight,
                    -MAX_ROLL,
                    MAX_ROLL);

            float rollFrequency = frontTouching ? 5.3f : 4.2f;
            float rollDamping = frontTouching ? 0.86f : 0.80f;
            float rollAcceleration = (rollTarget - roll) * rollFrequency * rollFrequency
                    - 2f * rollDamping * rollFrequency * rollRate;
            rollRate += rollAcceleration * dt;
        } else {
            // Do not spring the bike upright in mid-air. Carry takeoff roll angular momentum and
            // allow a modest rider/steer input to roll the chassis without inventing tire forces.
            float airborneRollAcceleration = steerCommand * 1.85f - rollRate * 0.12f;
            rollRate += airborneRollAcceleration * dt;
        }

        rollRate = MathUtils.clamp(rollRate, -4.3f, 4.3f);
        roll += rollRate * dt;
        roll = MathUtils.clamp(roll, -MAX_ROLL, MAX_ROLL);
    }

    /** Synchronizes body-frame sideslip with the ballistic world-space velocity while airborne. */
    void syncAirborneVelocity(float bodyLateralSpeed) {
        lateralSpeed = MathUtils.clamp(bodyLateralSpeed, -MAX_LATERAL_SPEED, MAX_LATERAL_SPEED);
    }

    void reset() {
        lateralSpeed = 0f;
        yawRate = 0f;
        roll = 0f;
        rollRate = 0f;
        steeringAngle = 0f;
        lateralAcceleration = 0f;
    }

    float lateralSpeed() {
        return lateralSpeed;
    }

    float yawRate() {
        return yawRate;
    }

    float roll() {
        return roll;
    }

    float rollRate() {
        return rollRate;
    }

    float steeringAngle() {
        return steeringAngle;
    }

    float lateralAcceleration() {
        return lateralAcceleration;
    }

    private static float softLimit(float force, float limit) {
        if (limit <= 0.001f) return 0f;
        return limit * (float) Math.tanh(force / limit);
    }

    private static float smooth(float value) {
        return value * value * (3f - 2f * value);
    }
}
