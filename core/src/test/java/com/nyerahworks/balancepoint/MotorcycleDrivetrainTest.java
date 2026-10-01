package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MotorcycleDrivetrainTest {
    private static final float WHEEL_RADIUS = 0.334978f;
    private static final float DT = 1f / 120f;

    @Test
    public void resetReturnsToFirstGearAndIdle() {
        MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);
        drivetrain.shiftUp();
        step(drivetrain, 18f, 1f, 40);

        drivetrain.reset();

        assertEquals(1, drivetrain.getGear());
        assertEquals(1_900f, drivetrain.getRpm(), 0.01f);
        assertFalse(drivetrain.isShifting());
        assertFalse(drivetrain.isLimiterCut());
    }

    @Test
    public void gearboxCannotShiftOutsideValidRange() {
        MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);

        assertFalse(drivetrain.shiftDown(0f));
        for (int expectedGear = 2; expectedGear <= 5; expectedGear++) {
            assertTrue(drivetrain.shiftUp());
            step(drivetrain, 0f, 0f, 24);
            assertEquals(expectedGear, drivetrain.getGear());
        }
        assertFalse(drivetrain.shiftUp());
    }

    @Test
    public void wheelSpeedCouplesEngineAboveLaunchRegion() {
        MotorcycleDrivetrain slow = new MotorcycleDrivetrain(WHEEL_RADIUS);
        MotorcycleDrivetrain fast = new MotorcycleDrivetrain(WHEEL_RADIUS);

        step(slow, 8f, 0.55f, 120);
        step(fast, 22f, 0.55f, 120);

        assertTrue(fast.getRpm() > slow.getRpm());
        assertTrue(fast.getRpm() >= 1_900f);
        assertTrue(fast.getRpm() <= 11_940f);
    }

    @Test
    public void shiftTemporarilyCutsDriveForceThenRecovers() {
        MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);
        step(drivetrain, 13f, 0.8f, 80);
        step(drivetrain, 13f, 0f, 30);

        assertTrue(drivetrain.shiftUp());
        float duringShift = drivetrain.update(13f, 0.8f, DT);
        step(drivetrain, 13f, 0.8f, 24);
        float afterShift = drivetrain.update(13f, 0.8f, DT);

        assertTrue(duringShift >= 0f);
        assertTrue(afterShift > duringShift);
    }

    @Test
    public void outputForceIsFiniteAndNonNegativeAcrossOperatingRange() {
        MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);

        for (int i = 0; i < 1_500; i++) {
            float speed = (i % 240) * 0.12f;
            float throttle = (i % 101) / 100f;
            float force = drivetrain.update(speed, throttle, DT);

            assertFalse(Float.isNaN(force));
            assertFalse(Float.isInfinite(force));
            assertTrue(force >= 0f);
        }
    }

    private static void step(
            MotorcycleDrivetrain drivetrain,
            float wheelLinearSpeed,
            float throttle,
            int steps) {
        for (int i = 0; i < steps; i++) {
            drivetrain.update(wheelLinearSpeed, throttle, DT);
        }
    }
}
