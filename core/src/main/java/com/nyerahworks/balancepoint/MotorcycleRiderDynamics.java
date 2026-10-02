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
    private static final float GROUNDED_MAX_BODY_SPEED = 2.25f;
    private static final float AIRBORNE_MAX_BODY_SPEED = 4.80f;
    private static final float GROUNDED_MAX_BODY_ACCELERATION = 20f;
    private static final float AIRBORNE_MAX_BODY_ACCELERATION = 56f;
    private static final float RIDER_FORCE_HEIGHT = 0.56f;
    private static final float MAX_GROUNDED_PITCH_REACTION_TORQUE = 360f;
    private static final float MAX_AIRBORNE_PITCH_REACTION_TORQUE = 920f;
    // Once the rider is airborne, holding a fore/aft body command still represents active force
    // through the bars and pegs. Without this term the old model went dead as soon as the rider
    // mass reached its target because acceleration fell back to zero.
    private static final float AIRBORNE_BAR_PEG_CONTROL_TORQUE = 520f;

    private final float totalMass;
    private final float gravity;

    private float position;
    private float velocity;
    private float acceleration;
    private float command;

    MotorcycleRiderDynamics(float totalMass, float gravity) {
        this.totalMass = totalMass;
        this.gravity = gravity;
    }

    void step(float command,
              float longitudinalAcceleration,
              boolean airborne,
              float dt) {
        command = MathUtils.clamp(command, -1f, 1f);
        this.command = command;

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

        // Grounded movement stays planted. With both wheels clear, the rider needs to feel much
        // more immediate: body position changes quickly enough to be useful during a normal jump
        // instead of taking most of the airtime just to reach the requested posture.
        float naturalFrequency = airborne ? 9.2f : 5.4f;
        float dampingRatio = airborne ? 0.56f : 0.92f;
        float maxBodyAcceleration = airborne
                ? AIRBORNE_MAX_BODY_ACCELERATION : GROUNDED_MAX_BODY_ACCELERATION;
        float maxBodySpeed = airborne ? AIRBORNE_MAX_BODY_SPEED : GROUNDED_MAX_BODY_SPEED;
        float rawAcceleration = (target - position) * naturalFrequency * naturalFrequency
                - 2f * dampingRatio * naturalFrequency * velocity;
        acceleration = MathUtils.clamp(
                rawAcceleration,
                -maxBodyAcceleration,
                maxBodyAcceleration);
        velocity += acceleration * dt;
        velocity = MathUtils.clamp(velocity, -maxBodySpeed, maxBodySpeed);
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

    /** Normalized presentation pose. +1 is fully forward, -1 fully rearward. */
    float pose() {
        if (position >= 0f) return MathUtils.clamp(position / FORWARD_TRAVEL, 0f, 1f);
        return MathUtils.clamp(position / REARWARD_TRAVEL, -1f, 0f);
    }

    /**
     * Chassis reaction to rider movement and, in the air, active bar/peg leverage.
     * A forward command pitches the nose down; a rearward command pitches it up.
     */
    float pitchReactionTorque(boolean airborne, boolean frontTouching) {
        float coupling = airborne ? 1.00f : (frontTouching ? 0.24f : 0.40f);
        float torque = -RIDER_MASS * acceleration * RIDER_FORCE_HEIGHT * coupling;

        if (airborne) {
            // Preserve the physically useful impulse from moving rider mass, but do not let the
            // control disappear after the body reaches its target. A rider can keep loading the
            // bars/pegs and rotating the bike while holding a posture in the air.
            float shapedCommand = command * (0.72f + 0.28f * Math.abs(command));
            torque += -shapedCommand * AIRBORNE_BAR_PEG_CONTROL_TORQUE;
            return MathUtils.clamp(
                    torque,
                    -MAX_AIRBORNE_PITCH_REACTION_TORQUE,
                    MAX_AIRBORNE_PITCH_REACTION_TORQUE);
        }

        return MathUtils.clamp(
                torque,
                -MAX_GROUNDED_PITCH_REACTION_TORQUE,
                MAX_GROUNDED_PITCH_REACTION_TORQUE);
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
        command = 0f;
    }
}
