package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Dynamic fore/aft rider mass model.
 *
 * The phone control still asks for a simple forward/rearward body position, but the rider is
 * no longer teleported around the combined COM. Rider mass has its own position, velocity and
 * acceleration relative to the bike. That makes body movement build over time, lag slightly
 * under acceleration/braking, move the combined COM continuously and feed a short reaction
 * torque back into chassis pitch when the rider throws their weight around.
 */
final class MotorcycleRiderDynamics {
    private static final float RIDER_MASS = 86f;
    private static final float FORWARD_TRAVEL = 0.38f;
    private static final float REARWARD_TRAVEL = 0.34f;
    private static final float MAX_INERTIAL_BIAS = 0.070f;
    private static final float INERTIAL_BIAS_PER_G = 0.055f;
    private static final float MAX_BODY_SPEED = 2.25f;
    private static final float MAX_BODY_ACCELERATION = 20f;
    private static final float RIDER_FORCE_HEIGHT = 0.56f;
    private static final float MAX_PITCH_REACTION_TORQUE = 180f;

    private final float totalMass;
    private final float gravity;

    private float position;
    private float velocity;
    private float acceleration;

    MotorcycleRiderDynamics(float totalMass, float gravity) {
        this.totalMass = totalMass;
        this.gravity = gravity;
    }

    void step(float command,
              float longitudinalAcceleration,
              boolean airborne,
              float dt) {
        command = MathUtils.clamp(command, -1f, 1f);

        float commandedPosition = command >= 0f
                ? command * FORWARD_TRAVEL
                : command * REARWARD_TRAVEL;

        // Even a braced rider has inertia. Acceleration lets the body lag rearward a little;
        // braking carries it forward. The effect is deliberately modest so controls remain
        // intuitive rather than feeling like a loose ragdoll on the seat.
        float accelerationInG = longitudinalAcceleration / Math.max(gravity, 0.001f);
        float inertialBias = MathUtils.clamp(
                -accelerationInG * INERTIAL_BIAS_PER_G,
                -MAX_INERTIAL_BIAS,
                MAX_INERTIAL_BIAS);
        float target = MathUtils.clamp(
                commandedPosition + inertialBias,
                -REARWARD_TRAVEL,
                FORWARD_TRAVEL);

        float naturalFrequency = airborne ? 3.8f : 5.4f;
        float dampingRatio = airborne ? 0.76f : 0.92f;
        float rawAcceleration = (target - position) * naturalFrequency * naturalFrequency
                - 2f * dampingRatio * naturalFrequency * velocity;
        acceleration = MathUtils.clamp(
                rawAcceleration,
                -MAX_BODY_ACCELERATION,
                MAX_BODY_ACCELERATION);
        velocity += acceleration * dt;
        velocity = MathUtils.clamp(velocity, -MAX_BODY_SPEED, MAX_BODY_SPEED);
        position += velocity * dt;

        if (position < -REARWARD_TRAVEL) {
            position = -REARWARD_TRAVEL;
            if (velocity < 0f) velocity = 0f;
        } else if (position > FORWARD_TRAVEL) {
            position = FORWARD_TRAVEL;
            if (velocity > 0f) velocity = 0f;
        }
    }

    /** Shift of the combined bike+rider COM caused by the rider's relative body position. */
    float combinedComShift() {
        return position * RIDER_MASS / totalMass;
    }

    /**
     * Normalized presentation pose. +1 is fully forward, -1 fully rearward.
     * Keeping presentation normalized lets camera/visual code remain simple.
     */
    float pose() {
        if (position >= 0f) return MathUtils.clamp(position / FORWARD_TRAVEL, 0f, 1f);
        return MathUtils.clamp(position / REARWARD_TRAVEL, -1f, 0f);
    }

    /**
     * Chassis reaction to accelerating rider mass relative to the motorcycle.
     * A fast forward body throw nudges the nose down; rearward movement nudges it up.
     * Internal body motion has more authority in the air because tire contacts cannot absorb
     * as much of the reaction, but it remains intentionally weaker than brake-tap/throttle air control.
     */
    float pitchReactionTorque(boolean airborne, boolean frontTouching) {
        float coupling = airborne ? 0.62f : (frontTouching ? 0.24f : 0.40f);
        float torque = -RIDER_MASS * acceleration * RIDER_FORCE_HEIGHT * coupling;
        return MathUtils.clamp(
                torque,
                -MAX_PITCH_REACTION_TORQUE,
                MAX_PITCH_REACTION_TORQUE);
    }

    float position() {
        return position;
    }

    float velocity() {
        return velocity;
    }

    float acceleration() {
        return acceleration;
    }

    void reset() {
        position = 0f;
        velocity = 0f;
        acceleration = 0f;
    }
}
