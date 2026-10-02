package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Front/rear motorcycle suspension model in physical units.
 *
 * Each end stores compression (m) and shaft velocity (m/s). Spring force is linear through
 * the working stroke, compression/rebound damping are asymmetric, high-speed compression is
 * progressive, and a rising bump stop takes over near the end of travel. The current contact
 * solver can feed geometric compression demand into this class without changing the rest of
 * the chassis model; later the same state can be coupled to explicit unsprung wheel masses.
 */
final class MotorcycleSuspension {
    static final float REAR_TRAVEL = 0.300f;
    static final float FRONT_TRAVEL = 0.305f;

    // Wheel rates, not raw shock/fork spring rates. The rear tune is already reading correctly
    // in motion. The front is intentionally softer than the first pass: 18 N/mm only produced
    // about 49 mm of loaded sag and visually behaved almost like a rigid fork. 11.5 N/mm puts
    // the current bike+rider load near 77 mm of front sag, leaving useful extension and plenty
    // of compression travel for bumps, jump faces and landings.
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
        private final float staticCompression;
        private final float maxForce;

        private float compression;
        private float presentationCompression;
        private float velocity;
        private float force;

        Unit(float travel,
             float springRate,
             float compressionDamping,
             float reboundDamping,
             float highSpeedCompression,
             float bumpStart,
             float bumpRate,
             float staticLoad,
             float maxForce) {
            this.travel = travel;
            this.springRate = springRate;
            this.compressionDamping = compressionDamping;
            this.reboundDamping = reboundDamping;
            this.highSpeedCompression = highSpeedCompression;
            this.bumpStart = MathUtils.clamp(bumpStart, 0f, travel);
            this.bumpRate = bumpRate;
            this.staticCompression = MathUtils.clamp(
                    staticLoad / Math.max(1f, springRate), 0f, travel * 0.65f);
            this.maxForce = maxForce;
            reset();
        }

        /**
         * Solve force from geometric suspension demand.
         *
         * demandedCompression is the compression required for the tire to remain on the sampled
         * ground plane. compressionVelocity is positive while the suspension is being driven
         * into its stroke. effectiveMass is used only to cap damping impulses so one fixed step
         * cannot numerically reverse a landing velocity.
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
                return force;
            }

            float solvedForce = springRate * compression;
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

            force = MathUtils.clamp(solvedForce, 0f, maxForce);
            return force;
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

        float travel() {
            return travel;
        }

        float staticCompression() {
            return staticCompression;
        }

        /** Signed physical travel relative to the normal loaded ride position. */
        float physicalRideOffset() {
            return compression - staticCompression;
        }

        /** Signed display travel; extension is rate-limited after the tire leaves the ground. */
        float rideOffset() {
            return presentationCompression - staticCompression;
        }

        float normalizedCompression() {
            return travel > 0f ? compression / travel : 0f;
        }
    }
}
