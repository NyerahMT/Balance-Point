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

    // Balance Point is aiming for believable, controllable bike behavior rather than a tire
    // engineering simulator. Longitudinal drive can loosen the rear, but it must not erase
    // essentially all lateral authority the instant the tire reaches its drive-force limit.
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

        // At parking-lot speed the bars can visibly steer. At riding speed the same pad travel
        // commands a much smaller front-wheel angle, which avoids the old twitchy snap-turn feel.
        float maxSteer = MathUtils.lerp(29f, 5.5f, speedBlend)
                * MathUtils.degreesToRadians;
        steeringAngle = -steerCommand * maxSteer;

        if (rearTouching || frontTouching) {
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

            // Drive force reduces rear cornering authority, but only partially. The previous
            // full friction-circle subtraction could drive rear lateral grip nearly to zero at
            // full throttle, which made power-on turns feel like the rear tire was on ice.
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

            // Motorcycle sideslip should be something the player can catch, not momentum that
            // persists for half the track. When both tires are planted they rapidly recapture
            // the chassis velocity, especially as the rider releases steering input. Rear-only
            // contact keeps much less of this assistance so wheelies can still move around.
            float steerMagnitude = MathUtils.clamp(Math.abs(steerCommand), 0f, 1f);
            float lateralCapture = 0f;
            if (frontTouching && rearTouching) {
                lateralCapture = MathUtils.lerp(5.0f, 3.1f, steerMagnitude);
            } else if (rearTouching) {
                lateralCapture = 1.25f;
            } else if (frontTouching) {
                lateralCapture = 2.4f;
            }
            lateralSpeed *= Math.max(0f, 1f - dt * lateralCapture);
            lateralSpeed = MathUtils.clamp(
                    lateralSpeed, -MAX_LATERAL_SPEED, MAX_LATERAL_SPEED);

            float yawTorque = frontArm * frontLateralForce - rearArm * rearLateralForce;
            float yawAcceleration = yawTorque / YAW_INERTIA - yawRate * YAW_DAMPING;

            // With the front wheel in the air there is no front contact patch to steer with.
            // A modest rear-only authority keeps wheelies controllable without reverting to a
            // canned yaw-rate target.
            if (rearTouching && !frontTouching) {
                float wheelieAuthority = MathUtils.lerp(2.0f, 0.72f, speedBlend);
                yawAcceleration += -steerCommand * wheelieAuthority * motionBlend;
            }

            yawRate += yawAcceleration * dt;
        } else {
            lateralAcceleration = 0f;
            lateralSpeed *= Math.max(0f, 1f - dt * 0.08f);
            yawRate *= Math.max(0f, 1f - dt * 0.34f);
        }

        yawRate = MathUtils.clamp(yawRate, -MAX_YAW_RATE, MAX_YAW_RATE);

        // Tire force supplies the steady-state lean. A smaller rider-intent term leads it during
        // turn-in so the bike feels willing to change direction instead of waiting for yaw first.
        float forceRoll = -(float) Math.atan2(lateralAcceleration, gravity);
        float leanSpeedBlend = smooth(MathUtils.clamp((absSpeed - 1.0f) / 12f, 0f, 1f));
        float intentLimit = MathUtils.lerp(10f, 50f, leanSpeedBlend)
                * MathUtils.degreesToRadians;
        float intentRoll = steerCommand * intentLimit;
        float intentWeight = frontTouching ? 0.30f : (rearTouching ? 0.20f : 0.08f);
        float rollTarget = MathUtils.clamp(
                forceRoll * (1f - intentWeight) + intentRoll * intentWeight,
                -MAX_ROLL,
                MAX_ROLL);

        float rollFrequency = frontTouching ? 5.3f : (rearTouching ? 4.2f : 2.7f);
        float rollDamping = frontTouching ? 0.86f : (rearTouching ? 0.80f : 0.68f);
        float rollAcceleration = (rollTarget - roll) * rollFrequency * rollFrequency
                - 2f * rollDamping * rollFrequency * rollRate;
        rollRate += rollAcceleration * dt;
        rollRate = MathUtils.clamp(rollRate, -4.3f, 4.3f);
        roll += rollRate * dt;
        roll = MathUtils.clamp(roll, -MAX_ROLL, MAX_ROLL);
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
