package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2PowertrainTest {
    private static final float DT = 1f / 120f;

    @Test
    public void rpmIsIntegratedAndRespondsToLoad() {
        PhysicsV2Powertrain free = new PhysicsV2Powertrain();
        PhysicsV2Powertrain loaded = new PhysicsV2Powertrain();
        for (int i = 0; i < 180; i++) {
            free.step(0f, 0.7f, 0f, DT);
            loaded.step(40f, 0.7f, 1f, DT);
        }
        assertTrue(free.getRpm() > 2000f);
        assertTrue(Math.abs(free.getRpm() - loaded.getRpm()) > 50f);
    }

    @Test
    public void gearboxStaysWithinFiveHondaRatios() {
        PhysicsV2Powertrain p = new PhysicsV2Powertrain();
        assertEquals(1, p.getGear());
        for (int g = 2; g <= 5; g++) {
            assertTrue(p.shiftUp());
            for (int i = 0; i < 20; i++) p.step(0f, 0f, 0f, DT);
            assertEquals(g, p.getGear());
        }
    }

    @Test
    public void shaftTorqueIsFiniteAcrossOperatingRange() {
        PhysicsV2Powertrain p = new PhysicsV2Powertrain();
        for (int i = 0; i < 2000; i++) {
            float rearOmega = (i % 260) * 0.7f;
            float throttle = (i % 101) / 100f;
            float torque = p.step(rearOmega, throttle, 0.65f, DT);
            assertTrue(!Float.isNaN(torque) && !Float.isInfinite(torque));
            assertTrue(!Float.isNaN(p.getRpm()) && !Float.isInfinite(p.getRpm()));
        }
    }
}
