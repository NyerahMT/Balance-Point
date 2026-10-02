package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Classifies real contact events without inventing a separate "jump mode".
 *
 * A landing begins only after a meaningful no-contact interval. The first wheel strike opens a
 * short landing window so the second wheel slap and a delayed suspension bottom-out are part of
 * the same event. Severity is derived from normal impact speed, suspension state and chassis
 * attitude; it never applies a canned pitch correction.
 */
final class MotorcycleLandingDynamics {
    private static final float MIN_AIR_TIME = 0.055f;
    private static final float LANDING_WINDOW = 0.34f;

    private boolean previousRearTouching = true;
    private boolean previousFrontTouching = true;
    private float airborneTime;
    private float landingWindowRemaining;
    private float eventImpactSpeed;
    private float eventSeverity;
    private boolean frontFirst;

    private final Result result = new Result();

    Result step(boolean rearTouching,
                boolean frontTouching,
                float rearNormalVelocity,
                float frontNormalVelocity,
                float rearSurfacePitch,
                float frontSurfacePitch,
                MotorcycleSuspension.Unit rearSuspension,
                MotorcycleSuspension.Unit frontSuspension,
                float pitch,
                float roll,
                float pitchVelocity,
                float dt) {
        boolean airborne = !rearTouching && !frontTouching;
        boolean newRearContact = rearTouching && !previousRearTouching;
        boolean newFrontContact = frontTouching && !previousFrontTouching;
        boolean landedThisStep = false;

        if (airborne) {
            airborneTime += dt;
            landingWindowRemaining = 0f;
            eventImpactSpeed = 0f;
            eventSeverity = 0f;
            frontFirst = false;
        } else {
            if (landingWindowRemaining <= 0f
                    && airborneTime >= MIN_AIR_TIME
                    && (newRearContact || newFrontContact)) {
                landingWindowRemaining = LANDING_WINDOW;
                landedThisStep = true;
                frontFirst = newFrontContact && !newRearContact;
            }

            if (landingWindowRemaining > 0f) {
                landingWindowRemaining = Math.max(0f, landingWindowRemaining - dt);

                float contactImpact = 0f;
                if (newRearContact) contactImpact = Math.max(contactImpact, -rearNormalVelocity);
                if (newFrontContact) contactImpact = Math.max(contactImpact, -frontNormalVelocity);
                eventImpactSpeed = Math.max(eventImpactSpeed, contactImpact);

                float supportPitch;
                if (frontTouching && rearTouching) {
                    supportPitch = angleAverage(rearSurfacePitch, frontSurfacePitch);
                } else if (frontTouching) {
                    supportPitch = frontSurfacePitch;
                } else {
                    supportPitch = rearSurfacePitch;
                }

                float pitchMismatch = Math.abs(wrapAngle(pitch - supportPitch));
                float rollMagnitude = Math.abs(roll);
                float maxCompression = Math.max(
                        rearSuspension.normalizedCompression(),
                        frontSuspension.normalizedCompression());
                float compressionSpeed = Math.max(0f, Math.max(
                        rearSuspension.velocity(), frontSuspension.velocity()));

                float impactScore = smootherStep(2.1f, 7.8f, eventImpactSpeed);
                float compressionScore = smootherStep(0.78f, 0.995f, maxCompression);
                float compressionSpeedScore = smootherStep(0.9f, 5.2f, compressionSpeed);
                float pitchScore = smootherStep(
                        18f * MathUtils.degreesToRadians,
                        56f * MathUtils.degreesToRadians,
                        pitchMismatch);
                float rollScore = smootherStep(
                        17f * MathUtils.degreesToRadians,
                        48f * MathUtils.degreesToRadians,
                        rollMagnitude);
                float pitchRateScore = smootherStep(1.35f, 4.2f, Math.abs(pitchVelocity));

                float severity = impactScore * 0.45f
                        + compressionScore * 0.20f
                        + compressionSpeedScore * 0.12f
                        + pitchScore * 0.12f
                        + rollScore * 0.07f
                        + pitchRateScore * 0.04f;
                if (frontFirst) severity += impactScore * 0.055f;
                eventSeverity = MathUtils.clamp(Math.max(eventSeverity, severity), 0f, 1f);

                boolean extremeImpact = eventImpactSpeed > 8.4f;
                boolean violentFrontStrike = frontFirst
                        && eventImpactSpeed > 5.4f
                        && pitchMismatch > 42f * MathUtils.degreesToRadians;
                boolean sidewaysStrike = eventImpactSpeed > 3.7f
                        && rollMagnitude > 48f * MathUtils.degreesToRadians;
                boolean severeBottomOut = maxCompression > 0.992f
                        && compressionSpeed > 4.7f;
                boolean accumulatedFailure = eventSeverity > 0.79f
                        && (eventImpactSpeed > 4.3f || maxCompression > 0.965f);

                result.crashRecommended = extremeImpact
                        || violentFrontStrike
                        || sidewaysStrike
                        || severeBottomOut
                        || accumulatedFailure;
            } else {
                result.crashRecommended = false;
            }

            airborneTime = 0f;
        }

        result.landedThisStep = landedThisStep;
        result.airborneTime = airborneTime;
        result.landingWindowRemaining = landingWindowRemaining;
        result.impactSpeed = eventImpactSpeed;
        result.severity = eventSeverity;
        result.frontFirst = frontFirst;

        previousRearTouching = rearTouching;
        previousFrontTouching = frontTouching;
        return result;
    }

    void reset() {
        previousRearTouching = true;
        previousFrontTouching = true;
        airborneTime = 0f;
        landingWindowRemaining = 0f;
        eventImpactSpeed = 0f;
        eventSeverity = 0f;
        frontFirst = false;
        result.clear();
    }

    float airborneTime() {
        return airborneTime;
    }

    private static float smootherStep(float edge0, float edge1, float value) {
        float t = MathUtils.clamp((value - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float angleAverage(float a, float b) {
        float x = MathUtils.cos(a) + MathUtils.cos(b);
        float y = MathUtils.sin(a) + MathUtils.sin(b);
        return MathUtils.atan2(y, x);
    }

    private static float wrapAngle(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }

    static final class Result {
        boolean landedThisStep;
        boolean crashRecommended;
        boolean frontFirst;
        float airborneTime;
        float landingWindowRemaining;
        float impactSpeed;
        float severity;

        private void clear() {
            landedThisStep = false;
            crashRecommended = false;
            frontFirst = false;
            airborneTime = 0f;
            landingWindowRemaining = 0f;
            impactSpeed = 0f;
            severity = 0f;
        }
    }
}
