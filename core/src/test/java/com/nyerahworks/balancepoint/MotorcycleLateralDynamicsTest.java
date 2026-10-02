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
    public void rearDriveForceChangesLateralResponse() {
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
        assertTrue(Math.abs(drivenRear.lateralSpeed() - freeRear.lateralSpeed()) > 0.01f);
    }
}
