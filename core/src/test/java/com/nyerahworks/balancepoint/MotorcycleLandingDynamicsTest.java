package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MotorcycleLandingDynamicsTest {
    private static final float DT = 1f / 120f;
    private static final float MASS = 198f;
    private static final float GRAVITY = 9.81f;
    private static final float WHEELBASE = 1.403232f;
    private static final float COM_FORWARD = 0.636107f;
    private static final float REAR_LOAD = MASS * GRAVITY
            * (WHEELBASE - COM_FORWARD) / WHEELBASE;
    private static final float FRONT_LOAD = MASS * GRAVITY
            * COM_FORWARD / WHEELBASE;
    private static final float MAX_FORCE = MASS * GRAVITY * 5f;

    private static MotorcycleSuspension suspension() {
        return new MotorcycleSuspension(REAR_LOAD, FRONT_LOAD, MAX_FORCE);
    }

    private static void spendTimeAirborne(MotorcycleLandingDynamics landing,
                                          MotorcycleSuspension suspension,
                                          float seconds) {
        int steps = Math.max(1, Math.round(seconds / DT));
        for (int i = 0; i < steps; i++) {
            landing.step(false, false,
                    0f, 0f,
                    0f, 0f,
                    suspension.rear(), suspension.front(),
                    0f, 0f, 0f, DT);
        }
    }

    @Test
    public void tinyContactDropoutIsNotAJumpLanding() {
        MotorcycleLandingDynamics landing = new MotorcycleLandingDynamics();
        MotorcycleSuspension suspension = suspension();

        spendTimeAirborne(landing, suspension, 0.025f);
        MotorcycleLandingDynamics.Result result = landing.step(
                true, true,
                -3.5f, -3.5f,
                0f, 0f,
                suspension.rear(), suspension.front(),
                0f, 0f, 0f, DT);

        assertFalse(result.landedThisStep);
        assertFalse(result.crashRecommended);
    }

    @Test
    public void cleanTwoWheelLandingStaysRideable() {
        MotorcycleLandingDynamics landing = new MotorcycleLandingDynamics();
        MotorcycleSuspension suspension = suspension();

        spendTimeAirborne(landing, suspension, 0.22f);
        MotorcycleLandingDynamics.Result result = landing.step(
                true, true,
                -2.25f, -2.10f,
                0f, 0f,
                suspension.rear(), suspension.front(),
                3f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                2f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                0.35f,
                DT);

        assertTrue(result.landedThisStep);
        assertFalse(result.crashRecommended);
        assertTrue(result.severity < 0.40f);
    }

    @Test
    public void violentFrontFirstStrikeCanCrash() {
        MotorcycleLandingDynamics landing = new MotorcycleLandingDynamics();
        MotorcycleSuspension suspension = suspension();

        spendTimeAirborne(landing, suspension, 0.30f);
        MotorcycleLandingDynamics.Result result = landing.step(
                false, true,
                0f, -6.2f,
                0f, 0f,
                suspension.rear(), suspension.front(),
                -50f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                5f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                -2.0f,
                DT);

        assertTrue(result.landedThisStep);
        assertTrue(result.frontFirst);
        assertTrue(result.crashRecommended);
    }

    @Test
    public void secondWheelSlapRemainsPartOfSameLanding() {
        MotorcycleLandingDynamics landing = new MotorcycleLandingDynamics();
        MotorcycleSuspension suspension = suspension();

        spendTimeAirborne(landing, suspension, 0.24f);
        MotorcycleLandingDynamics.Result rearFirst = landing.step(
                true, false,
                -2.8f, 0f,
                0f, 0f,
                suspension.rear(), suspension.front(),
                8f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                0f, 0.5f, DT);
        assertTrue(rearFirst.landedThisStep);
        assertFalse(rearFirst.crashRecommended);

        MotorcycleLandingDynamics.Result frontSlap = landing.step(
                true, true,
                -0.2f, -8.7f,
                0f, 0f,
                suspension.rear(), suspension.front(),
                -8f * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                0f, -1.5f, DT);

        assertFalse(frontSlap.landedThisStep);
        assertTrue(frontSlap.impactSpeed > 8.4f);
        assertTrue(frontSlap.crashRecommended);
    }
}
