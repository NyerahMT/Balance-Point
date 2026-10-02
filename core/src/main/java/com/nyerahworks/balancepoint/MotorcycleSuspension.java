package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Front/rear motorcycle suspension model in physical units.
 *
 * Each end owns a persistent wheel/suspension degree of freedom. Terrain contact is mediated by
 * a short-travel radial tire spring, while the wheel state accelerates between tire force and
 * spring/damper force. The terrain therefore no longer teleports the fork/shock directly to a
 * demanded compression every fixed step.
 */
final class MotorcycleSuspension {
    static final float REAR_TRAVEL = 0.300f;
    static final float FRONT_TRAVEL = 0.305f;

    static final float REAR_WHEEL_RATE = 14_000f;
    static final float FRONT_WHEEL_RATE = 11_500f;

    private static final float REAR_COMPRESSION_DAMPING = 1_650f;
    private static final float REAR_REBOUND_DAMPING = 2_750f;
    private static final float FRONT_COMPRESSION_DAMPING = 1_050f;
    private static final float FRONT_REBOUND_DAMPING = 1_850f;
    private static final float REAR_HIGH_SPEED_COMPRESSION = 420f;
    private static final float FRONT_HIGH_SPEED_COMPRESSION = 280f;
    private static final float REAR_BUMP_START = 0.238f;
    private static final float FRONT_BUMP_START = 0.255f;
    private static final float REAR_BUMP_RATE = 82_000f;
    private static final float FRONT_BUMP_RATE = 72_000f;

    // Effective vertical inertia at each axle after fork/swingarm geometry is collapsed into
    // one degree of freedom. These are intentionally modest rather than literal assembly masses.
    private static final float REAR_UNSPRUNG_MASS = 15.5f;
    private static final float FRONT_UNSPRUNG_MASS = 13.5f;

    // Low-pressure knobby tires supply the first layer of compliance before the suspension has
    // to move. Zero terrain gap represents the normal loaded radius, so static tire squash is
    // derived from the ordinary wheel load and recovered as load falls away.
    private static final float REAR_TIRE_RATE = 128_000f;
    private static final float FRONT_TIRE_RATE = 116_000f;
    private static final float REAR_TIRE_DAMPING = 720f;
    private static final float FRONT_TIRE_DAMPING = 640f;
    private static final float MAX_UNSPRUNG_SPEED = 7.5f;
    private static final float EXTRA_TIRE_BOTTOM_OUT_ALLOWANCE = 0.032f;

    private final Unit rear;
    private final Unit front;

    MotorcycleSuspension(float rearStaticLoad, float frontStaticLoad, float maxForce) {
        rear = new Unit(
                REAR_TRAVEL,
                REAR_WHEEL_RATE,
                REAR_COMPRESSION_DAMPING,
                REAR_REBOUND_DAMPING,
                REAR_HIGH_SPEED_COMPRESSION,
                REAR_BUMP_START,
                REAR_BUMP_RATE,
                REAR_UNSPRUNG_MASS,
                REAR_TIRE_RATE,
                REAR_TIRE_DAMPING,
                rearStaticLoad,
                maxForce);
        front = new Unit(
                FRONT_TRAVEL,
                FRONT_WHEEL_RATE,
                FRONT_COMPRESSION_DAMPING,
                FRONT_REBOUND_DAMPING,
                FRONT_HIGH_SPEED_COMPRESSION,
                FRONT_BUMP_START,
                FRONT_BUMP_RATE,
                FRONT_UNSPRUNG_MASS,
                FRONT_TIRE_RATE,
                FRONT_TIRE_DAMPING,
                frontStaticLoad,
                maxForce);
    }

    Unit rear() {
        return rear;
    }

    Unit front() {
        return front;
    }

    void reset() {
        rear.reset();
        front.reset();
    }

    /** One suspension end expressed at the wheel/axle rather than the shock shaft. */
    static final class Unit {
        private final float travel;
        private final float springRate;
        private final float compressionDamping;
        private final float reboundDamping;
        private final float highSpeedCompression;
        private final float bumpStart;
        private final float bumpRate;
        private final float unsprungMass;
        private final float tireRate;
        private final float tireDamping;
        private final float staticCompression;
        private final float staticTireCompression;
        private final float maxForce;

        private float compression;
        private float presentationCompression;
        private float velocity;
        private float force;
        private float suspensionForce;
        private float tireCompression;
        private float actualGap;

        Unit(float travel,
             float springRate,
             float compressionDamping,
             float reboundDamping,
             float highSpeedCompression,
             float bumpStart,
             float bumpRate,
             float unsprungMass,
             float tireRate,
             float tireDamping,
             float staticLoad,
             float maxForce) {
            this.travel = travel;
            this.springRate = springRate;
            this.compressionDamping = compressionDamping;
            this.reboundDamping = reboundDamping;
            this.highSpeedCompression = highSpeedCompression;
            this.bumpStart = MathUtils.clamp(bumpStart, 0f, travel);
            this.bumpRate = bumpRate;
            this.unsprungMass = Math.max(1f, unsprungMass);
            this.tireRate = Math.max(1f, tireRate);
            this.tireDamping = Math.max(0f, tireDamping);
            this.staticCompression = MathUtils.clamp(
                    staticLoad / Math.max(1f, springRate), 0f, travel * 0.65f);
            this.staticTireCompression = Math.max(0f, staticLoad / this.tireRate);
            this.maxForce = maxForce;
            reset();
        }

        /**
         * Live persistent wheel/contact solve.
         *
         * The existing chassis contact solver gives us demandedCompression = staticSag - gap and
         * compressionVelocity = -hardpointNormalVelocity. Recovering those quantities here lets
         * this new unsprung state slot into the current game without a second contact pipeline.
         */
        float solve(float demandedCompression,
                    float compressionVelocity,
                    float effectiveMass,
                    float dt) {
            float nominalGap = staticCompression - demandedCompression;
            float hardpointNormalVelocity = -compressionVelocity;
            return stepContact(
                    nominalGap,
                    1f,
                    hardpointNormalVelocity,
                    effectiveMass,
                    dt);
        }

        /**
         * Persistent wheel/contact solve with an explicit surface-normal vertical component.
         * This overload is ready for the game loop to use directly when we fold slope orientation
         * into unsprung motion in the next refinement.
         */
        float stepContact(float nominalGap,
                          float surfaceNormalUp,
                          float hardpointNormalVelocity,
                          float effectiveMass,
                          float dt) {
            float normalUp = MathUtils.clamp(surfaceNormalUp, 0.15f, 1f);
            float safeDt = Math.max(dt, 0.0001f);

            // Additional suspension compression moves the axle upward relative to the chassis;
            // extension moves it downward and lets the wheel follow a falling surface.
            actualGap = nominalGap + physicalRideOffset() * normalUp;
            float wheelNormalVelocity = hardpointNormalVelocity + velocity * normalUp;

            // At zero geometric gap the loaded tire is already statically squashed. As load
            // disappears it recovers that squash before the wheel truly separates from terrain.
            tireCompression = Math.max(0f, staticTireCompression - actualGap);
            float tireCompressionVelocity = -wheelNormalVelocity;
            float tireForce = 0f;
            if (tireCompression > 0f) {
                tireForce = tireRate * tireCompression
                        + tireDamping * tireCompressionVelocity;
                tireForce = MathUtils.clamp(tireForce, 0f, maxForce);
            }

            suspensionForce = calculateSuspensionForce(
                    compression, velocity, effectiveMass, safeDt, 0f);

            // Compact semi-unsprung model: tire contact pushes the axle toward compression,
            // while spring/damper force pushes it back toward droop. The chassis still receives
            // the net terrain force as one rigid body, avoiding a disruptive multibody rewrite.
            float unsprungAcceleration = (tireForce * normalUp - suspensionForce)
                    / unsprungMass;
            velocity += unsprungAcceleration * safeDt;
            velocity = MathUtils.clamp(velocity, -MAX_UNSPRUNG_SPEED, MAX_UNSPRUNG_SPEED);
            compression += velocity * safeDt;

            if (compression <= 0f) {
                compression = 0f;
                if (velocity < 0f) velocity = 0f;
            } else if (compression >= travel) {
                compression = travel;
                if (velocity > 0f) velocity = 0f;
            }

            // Persistent inertia now supplies the visual smoothing naturally.
            presentationCompression = compression;
            force = tireForce;
            return force;
        }

        /** Direct spring/damper probe for deterministic tuning tests. */
        float probeForce(float demandedCompression,
                         float compressionVelocity,
                         float effectiveMass,
                         float dt) {
            float overTravel = Math.max(0f, demandedCompression - travel);
            float probeCompression = MathUtils.clamp(demandedCompression, 0f, travel);
            if (demandedCompression <= 0f) return 0f;
            return calculateSuspensionForce(
                    probeCompression,
                    compressionVelocity,
                    effectiveMass,
                    dt,
                    overTravel);
        }

        private float calculateSuspensionForce(float compression,
                                               float compressionVelocity,
                                               float effectiveMass,
                                               float dt,
                                               float overTravel) {
            if (compression <= 0f && overTravel <= 0f) return 0f;

            float solvedForce = springRate * MathUtils.clamp(compression, 0f, travel);
            if (compressionVelocity > 0f) {
                float damping = compressionDamping * compressionVelocity
                        + highSpeedCompression * compressionVelocity * compressionVelocity;
                float maxDampingImpulse = Math.max(0f, effectiveMass) * compressionVelocity
                        / Math.max(dt, 0.0001f) * 0.82f;
                solvedForce += Math.min(damping, maxDampingImpulse);
            } else if (compressionVelocity < 0f) {
                solvedForce += reboundDamping * compressionVelocity;
            }

            float bumpTravel = Math.max(0f, compression - bumpStart) + overTravel;
            if (bumpTravel > 0f) {
                solvedForce += bumpRate * bumpTravel * (1f + bumpTravel * 5f);
            }
            return MathUtils.clamp(solvedForce, 0f, maxForce);
        }

        void reset() {
            compression = staticCompression;
            presentationCompression = staticCompression;
            velocity = 0f;
            force = springRate * staticCompression;
            suspensionForce = force;
            tireCompression = staticTireCompression;
            actualGap = 0f;
        }

        float compression() {
            return compression;
        }

        float velocity() {
            return velocity;
        }

        float force() {
            return force;
        }

        float suspensionForce() {
            return suspensionForce;
        }

        float tireCompression() {
            return tireCompression;
        }

        float actualGap() {
            return actualGap;
        }

        float travel() {
            return travel;
        }

        float staticCompression() {
            return staticCompression;
        }

        float staticTireCompression() {
            return staticTireCompression;
        }

        float bottomOutContactAllowance() {
            return staticTireCompression + EXTRA_TIRE_BOTTOM_OUT_ALLOWANCE;
        }

        /** Signed physical travel relative to the normal loaded ride position. */
        float physicalRideOffset() {
            return compression - staticCompression;
        }

        float rideOffset() {
            return presentationCompression - staticCompression;
        }

        float normalizedCompression() {
            return travel > 0f ? compression / travel : 0f;
        }
    }
}
