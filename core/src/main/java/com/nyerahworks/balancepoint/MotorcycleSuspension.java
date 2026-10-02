package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Front/rear motorcycle suspension model in physical units.
 *
 * Each end now owns a persistent wheel/suspension degree of freedom. The terrain no longer
 * teleports compression directly to the geometric demand every fixed step: contact is mediated
 * by a short-travel radial tire spring, while the wheel state accelerates between tire force and
 * spring/damper force. That lets the wheel extend across crests, reach droop in the air and
 * re-compress progressively on landing.
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

    // Effective unsprung masses. These are not literal scale weights for every rotating part;
    // they are the vertical inertia seen at the axle after the swingarm/fork geometry is folded
    // into one suspension-axis degree of freedom.
    private static final float REAR_UNSPRUNG_MASS = 15.5f;
    private static final float FRONT_UNSPRUNG_MASS = 13.5f;

    // Low-pressure knobby tires provide the first few centimetres of landing/chatter compliance
    // before the fork/shock has to move. Geometry treats zero gap as the normal loaded radius,
    // so static tire compression is baked into each Unit from its static wheel load.
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
         * Persistent wheel/contact solve used by the live bike.
         *
         * nominalGap is the terrain gap at the axle's normal loaded position. Positive physical
         * ride offset moves the wheel upward with additional suspension compression; negative
         * offset moves it down as the fork/shock extends. Tire force then accelerates the wheel
         * state against the suspension force instead of directly assigning a new compression.
         *
         * This intentionally remains a compact semi-unsprung model: the chassis still receives
         * the net terrain force as one rigid body, while this DOF supplies realistic contact
         * continuity without requiring a full articulated multibody motorcycle.
         */
        float stepContact(float nominalGap,
                          float surfaceNormalUp,
                          float hardpointNormalVelocity,
                          float effectiveMass,
                          float dt) {
            float normalUp = MathUtils.clamp(surfaceNormalUp, 0.15f, 1f);
            float safeDt = Math.max(dt, 0.0001f);

            actualGap = nominalGap + physicalRideOffset() * normalUp;
            float wheelNormalVelocity = hardpointNormalVelocity + velocity * normalUp;

            // Zero geometric gap is the ordinary loaded tire radius. As load comes off, the
            // tire first recovers its static squash before a true air gap opens.
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

            // Positive compression velocity moves the axle upward relative to the chassis.
            // The ground pushes it up; spring/damper force pushes it back toward droop.
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

            // The state itself now has inertia, so the old artificial visual extension limiter
            // is no longer needed for live contact. Render the actual physical wheel travel.
            presentationCompression = compression;
            force = tireForce;
            return force;
        }

        /**
         * Direct force solve retained for deterministic unit tests and tuning probes. The live
         * motorcycle uses stepContact so compression has persistent inertia.
         */
        float solve(float demandedCompression,
                    float compressionVelocity,
                    float effectiveMass,
                    float dt) {
            float overTravel = Math.max(0f, demandedCompression - travel);
            compression = MathUtils.clamp(demandedCompression, 0f, travel);
            velocity = compressionVelocity;
            updatePresentationCompression(dt);

            if (demandedCompression <= 0f) {
                force = 0f;
                suspensionForce = 0f;
                return force;
            }

            suspensionForce = calculateSuspensionForce(
                    compression, compressionVelocity, effectiveMass, dt, overTravel);
            force = suspensionForce;
            return force;
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

        private void updatePresentationCompression(float dt) {
            float maxRate = compression >= presentationCompression ? 4.0f : 1.25f;
            float maxStep = maxRate * Math.max(0f, dt);
            float delta = compression - presentationCompression;
            presentationCompression += MathUtils.clamp(delta, -maxStep, maxStep);
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
