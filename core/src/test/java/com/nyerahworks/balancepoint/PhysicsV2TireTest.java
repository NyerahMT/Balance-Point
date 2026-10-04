package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2TireTest {
    @Test
    public void verticalLoadIsMonotoneAcrossMotocrossRange() {
        float previous = 0f;
        for (int i = 0; i <= 60; i++) {
            float d = i * 0.00025f;
            float load = PhysicsV2Tire.verticalLoad(PhysicsV2Tire.REAR, d, 0f);
            assertTrue(load + 0.01f >= previous);
            previous = load;
        }
    }

    @Test
    public void combinedSlipNeverExceedsEllipse() {
        PhysicsV2Tire.State state = new PhysicsV2Tire.State();
        PhysicsV2Tire.Force out = new PhysicsV2Tire.Force();
        for (int i = 0; i < 500; i++) {
            float kappa = -0.35f + 0.70f * (i % 29) / 28f;
            float alpha = -0.25f + 0.50f * (i % 23) / 22f;
            PhysicsV2Tire.step(PhysicsV2Tire.REAR, state, out,
                    1000f, kappa, alpha, 0.12f, 15f, 1f / 120f, 1f);
            assertTrue(out.utilization <= 1.00001f);
        }
    }

    @Test
    public void contactReleaseRemovesForcesWithoutResidualClamp() {
        PhysicsV2Tire.State state = new PhysicsV2Tire.State();
        PhysicsV2Tire.Force out = new PhysicsV2Tire.Force();
        PhysicsV2Tire.step(PhysicsV2Tire.FRONT, state, out,
                900f, 0.15f, 0.1f, 0f, 12f, 1f / 120f, 1f);
        assertTrue(Math.abs(out.fx) + Math.abs(out.fy) > 1f);
        PhysicsV2Tire.step(PhysicsV2Tire.FRONT, state, out,
                0f, 0.15f, 0.1f, 0f, 12f, 1f / 120f, 1f);
        assertEquals(0f, out.fx, 0f);
        assertEquals(0f, out.fy, 0f);
        assertEquals(0f, out.fz, 0f);
    }
}
