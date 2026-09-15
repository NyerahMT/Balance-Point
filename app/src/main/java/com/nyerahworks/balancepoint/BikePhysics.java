package com.nyerahworks.balancepoint;

/**
 * Wheelie-first motorcycle dynamics model.
 *
 * The v0.1 prototype intentionally constrains the bike to a straight road and keeps the rear
 * wheel on the pavement. That lets us spend nearly all of the simulation budget on the part the
 * game is actually about: longitudinal acceleration, pitch, throttle balance and rear-brake catch.
 * Units are SI: meters, seconds and Newtons.
 */
final class BikePhysics {
    static final float WHEELBASE = 1.46f;
    static final float WHEEL_RADIUS = 0.31f;
    static final float REAR_LOCAL_X = -WHEELBASE * 0.5f;
    static final float FRONT_LOCAL_X = WHEELBASE * 0.5f;
    static final float WHEEL_LOCAL_Y = -0.42f;

    private static final float MASS = 180f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA_ABOUT_REAR = 74f;
    private static final float MAX_DRIVE_FORCE = 3_550f;
    private static final float MAX_BRAKE_FORCE = 4_800f;
    private static final float ENGINE_POWER_WATTS = 39_000f;
    private static final float PITCH_DAMPING = 28f;
    private static final float LOOP_ANGLE_RAD = (float) Math.toRadians(103f);

    // Position is tracked at the rear axle. The rear axle is the wheelie pivot in v0.1.
    private float rearX;
    private float speed;
    private float angle;
    private float angularVelocity;
    private float rearWheelAngle;
    private float frontWheelAngle;

    private float throttle;
    private float rearBrake;

    private boolean frontContact;
    private boolean crashed;
    private float crashTimer;
    private float wheelieTime;
    private float bestWheelieTime;
    private float frontContactGrace;

    BikePhysics() {
        reset();
    }

    void reset() {
        rearX = 0f;
        speed = 0f;
        angle = 0f;
        angularVelocity = 0f;
        rearWheelAngle = 0f;
        frontWheelAngle = 0f;
        throttle = 0f;
        rearBrake = 0f;
        frontContact = true;
        crashed = false;
        crashTimer = 0f;
        wheelieTime = 0f;
        frontContactGrace = 0f;
    }

    void setInput(float throttle, float rearBrake) {
        this.throttle = clamp01(throttle);
        this.rearBrake = clamp01(rearBrake);
    }

    void step(float dt) {
        if (crashed) {
            crashTimer += dt;
            throttle = 0f;
            rearBrake = 0f;
            if (crashTimer >= 0.9f) reset();
            return;
        }

        float absSpeed = Math.abs(speed);
        float powerLimitedForce = ENGINE_POWER_WATTS / Math.max(absSpeed, 4.0f);
        float driveForce = throttle * Math.min(MAX_DRIVE_FORCE, powerLimitedForce);

        float brakeForce = 0f;
        if (rearBrake > 0f && absSpeed > 0.03f) {
            brakeForce = Math.min(MAX_BRAKE_FORCE * rearBrake, absSpeed * MASS / Math.max(dt, 0.001f));
        }

        float drag = 0f;
        if (absSpeed > 0.01f) {
            drag = 0.42f * speed * absSpeed + 20f * Math.signum(speed);
        }

        float longitudinalForce = driveForce - brakeForce * Math.signum(speed) - drag;
        float acceleration = longitudinalForce / MASS;

        speed += acceleration * dt;
        if (speed < 0f) speed = 0f;
        speed = Math.min(speed, 46f);
        rearX += speed * dt;

        // Effective COM measured from the rear axle. Using the rear axle as the pitch pivot is a
        // deliberate wheelie-game simplification and gives a realistic-feeling balance point.
        float comLocalX = -REAR_LOCAL_X; // 0.73 m ahead of rear axle
        float comLocalY = -WHEEL_LOCAL_Y; // 0.42 m above rear axle
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float comWorldX = comLocalX * cos - comLocalY * sin;
        float comWorldY = comLocalX * sin + comLocalY * cos;

        // In the accelerating rear-axle frame, the longitudinal pseudo-force pitches the bike up;
        // gravity pitches it down. Rear braking therefore becomes the natural wheelie catch.
        float pitchTorque = MASS * acceleration * comWorldY - MASS * GRAVITY * comWorldX;
        pitchTorque -= angularVelocity * PITCH_DAMPING;

        if (frontContact) {
            // The front tire holds the chassis at zero pitch until drive acceleration is strong
            // enough to overcome the gravity moment around the rear axle.
            if (pitchTorque > 0f && speed > 1.0f) {
                frontContact = false;
            } else {
                angle = 0f;
                angularVelocity = 0f;
            }
        }

        if (!frontContact) {
            float angularAcceleration = pitchTorque / PITCH_INERTIA_ABOUT_REAR;
            angularVelocity += angularAcceleration * dt;
            angularVelocity = clamp(angularVelocity, -5.5f, 5.5f);
            angle += angularVelocity * dt;

            if (angle <= 0f) {
                angle = 0f;
                angularVelocity = 0f;
                frontContact = true;
            }
        }

        rearWheelAngle -= (speed / WHEEL_RADIUS) * dt;
        // Front wheel coasts while airborne; on the ground it matches road speed.
        if (frontContact) {
            frontWheelAngle -= (speed / WHEEL_RADIUS) * dt;
        } else {
            frontWheelAngle -= (speed / WHEEL_RADIUS) * dt * 0.985f;
        }

        updateWheelieScore(dt);

        if (angle >= LOOP_ANGLE_RAD || Float.isNaN(angle) || Float.isNaN(speed)) {
            crashed = true;
            crashTimer = 0f;
            if (wheelieTime > bestWheelieTime) bestWheelieTime = wheelieTime;
            wheelieTime = 0f;
        }
    }

    private void updateWheelieScore(float dt) {
        float degrees = (float) Math.toDegrees(angle);
        boolean validWheelie = !frontContact && speed > 2.2f && degrees > 8f;

        if (validWheelie) {
            frontContactGrace = 0f;
            wheelieTime += dt;
            if (wheelieTime > bestWheelieTime) bestWheelieTime = wheelieTime;
        } else if (wheelieTime > 0f) {
            if (frontContact) frontContactGrace += dt;
            if (speed < 1.5f || frontContactGrace > 0.22f) {
                wheelieTime = 0f;
                frontContactGrace = 0f;
            }
        }
    }

    float worldX(float localX, float localY) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        // Solve chassis origin from a rear axle that stays fixed at (rearX, wheelRadius).
        float rotatedRearLocalX = REAR_LOCAL_X * cos - WHEEL_LOCAL_Y * sin;
        float originX = rearX - rotatedRearLocalX;
        return originX + localX * cos - localY * sin;
    }

    float worldY(float localX, float localY) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        float rotatedRearLocalY = REAR_LOCAL_X * sin + WHEEL_LOCAL_Y * cos;
        float originY = WHEEL_RADIUS - rotatedRearLocalY;
        return originY + localX * sin + localY * cos;
    }

    float getRearWheelX() { return rearX; }
    float getRearWheelY() { return WHEEL_RADIUS; }
    float getFrontWheelX() { return worldX(FRONT_LOCAL_X, WHEEL_LOCAL_Y); }
    float getFrontWheelY() { return worldY(FRONT_LOCAL_X, WHEEL_LOCAL_Y); }
    float getX() { return rearX; }
    float getVx() { return speed; }
    float getAngle() { return angle; }
    float getRearWheelAngle() { return rearWheelAngle; }
    float getFrontWheelAngle() { return frontWheelAngle; }
    float getWheelieTime() { return wheelieTime; }
    float getBestWheelieTime() { return bestWheelieTime; }
    boolean isFrontContact() { return frontContact; }
    boolean isRearContact() { return !crashed; }
    boolean isCrashed() { return crashed; }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
