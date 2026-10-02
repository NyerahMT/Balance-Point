package com.nyerahworks.balancepoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MotorcycleLateralDynamicsTest {
    private static final float DT = 1f / 120f;

    @Test
    public void centeredBikeStaysStraight() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 240; i++) {
            dynamics.step(15f, 0f, 0f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        assertEquals(0f, dynamics.yawRate(), 0.0001f);
        assertEquals(0f, dynamics.roll(), 0.0001f);
        assertEquals(0f, dynamics.lateralSpeed(), 0.0001f);
    }

    @Test
    public void steeringBuildsTurnAndLeanInSameDirection() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 90; i++) {
            dynamics.step(14f, 0.65f, 0f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        assertTrue(dynamics.yawRate() < -0.05f);
        assertTrue(dynamics.roll() > 0.05f);
        assertTrue(dynamics.steeringAngle() < 0f);
    }

    @Test
    public void rearDriveForceChangesLateralResponseWithoutDeletingRearGrip() {
        MotorcycleLateralDynamics freeRear = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);
        MotorcycleLateralDynamics drivenRear = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 120; i++) {
            freeRear.step(16f, 0.75f, 0f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
            drivenRear.step(16f, 0.75f, 1000f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        assertTrue(Math.abs(drivenRear.yawRate() - freeRear.yawRate()) > 0.01f);
        assertTrue(Math.abs(drivenRear.lateralSpeed()) < 2.5f);
        assertTrue(Math.abs(drivenRear.yawRate()) < 1.5f);
    }

    @Test
    public void airborneScrubKeepsWhipYawButLevelsRoll() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 55; i++) {
            dynamics.step(15f, 0.80f, 0f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }
        for (int i = 0; i < 18; i++) {
            dynamics.step(15f, -0.95f, 0f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        float takeoffRoll = Math.abs(dynamics.roll());
        float takeoffRollRate = Math.abs(dynamics.rollRate());
        float takeoffYawRate = Math.abs(dynamics.yawRate());
        dynamics.syncAirborneVelocity(1.25f);

        for (int i = 0; i < 60; i++) {
            dynamics.step(15f, 0f, 0f,
                    0f, 0f, 1f, 1f,
                    false, false, DT);
            dynamics.syncAirborneVelocity(1.25f);
        }

        assertTrue(takeoffRoll > 0.20f);
        assertTrue(takeoffRollRate > 0.70f);
        assertTrue(takeoffYawRate > 0.35f);
        assertEquals(1.25f, dynamics.lateralSpeed(), 0.0001f);
        assertTrue(Math.abs(dynamics.roll()) < takeoffRoll * 0.35f);
        assertTrue(Math.abs(dynamics.rollRate()) < takeoffRollRate * 0.35f);
        assertTrue(Math.abs(dynamics.yawRate()) > takeoffYawRate * 0.90f);
    }

    @Test
    public void airborneSteerActivelyBuildsWhipYawWithoutRollingBike() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 60; i++) {
            dynamics.step(15f, 1f, 0f,
                    0f, 0f, 1f, 1f,
                    false, false, DT);
        }

        assertTrue(dynamics.yawRate() < -0.90f);
        assertTrue(Math.abs(dynamics.roll()) < 0.02f);
        assertTrue(Math.abs(dynamics.rollRate()) < 0.02f);
    }

    @Test
    public void oppositeAirborneSteerCanUnwindWhip() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 70; i++) {
            dynamics.step(15f, 1f, 0f,
                    0f, 0f, 1f, 1f,
                    false, false, DT);
        }
        float yawBeforeCorrection = Math.abs(dynamics.yawRate());

        for (int i = 0; i < 70; i++) {
            dynamics.step(15f, -1f, 0f,
                    0f, 0f, 1f, 1f,
                    false, false, DT);
        }

        assertTrue(yawBeforeCorrection > 1.0f);
        assertTrue(Math.abs(dynamics.yawRate()) < yawBeforeCorrection * 0.35f);
    }

    @Test
    public void releasedSteeringRecapturesSidewaysMotionQuickly() {
        MotorcycleLateralDynamics dynamics = new MotorcycleLateralDynamics(
                198f, 1.403232f, 0.636107f, 9.81f);

        for (int i = 0; i < 120; i++) {
            dynamics.step(16f, 0.85f, 950f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        float lateralAtRelease = Math.abs(dynamics.lateralSpeed());
        for (int i = 0; i < 60; i++) {
            dynamics.step(16f, 0f, 950f,
                    1050f, 890f, 1f, 1f,
                    true, true, DT);
        }

        assertTrue(lateralAtRelease > 0.20f);
        assertTrue(Math.abs(dynamics.lateralSpeed()) < lateralAtRelease * 0.35f);
        assertTrue(Math.abs(dynamics.yawRate()) < 0.25f);
    }
}
