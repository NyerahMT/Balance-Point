package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2PlantTest {
    private static final float DT = 1f / 120f;

    private static final class FlatTerrain implements PhysicsV2Plant.Terrain {
        boolean voided;
        @Override public float height(float x, float z) { return voided ? -100f : 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void flatGroundSettlesWithoutNumericalBlowup() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 2400; i++) p.step(terrain, DT);

        assertTrue("y=" + p.y(), p.y() > 0.45f && p.y() < 1.25f);
        assertTrue("speed=" + p.speed(), Math.abs(p.speed()) < 1.0f);
        assertTrue("pitch=" + p.pitch(), Math.abs(p.pitch()) < 0.25f);
        assertTrue("roll=" + p.roll(), !Float.isNaN(p.roll()));
        assertTrue("rearFz=" + p.telemetry().rearFz, p.telemetry().rearFz >= 0f);
        assertTrue("frontFz=" + p.telemetry().frontFz, p.telemetry().frontFz >= 0f);
    }

    @Test
    public void throttleProducesPhysicalForwardAcceleration() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 180; i++) p.step(terrain, DT);
        p.input().throttle = 0.85f;
        for (int i = 0; i < 360; i++) p.step(terrain, DT);

        assertTrue("speed=" + p.speed() + " rpm=" + p.powertrain().getRpm()
                        + " rearFz=" + p.telemetry().rearFz + " rearFx=" + p.telemetry().rearFx,
                p.speed() > 0.5f);
        assertTrue("rpm=" + p.powertrain().getRpm(), p.powertrain().getRpm() > 1500f);
        assertTrue(p.telemetry().rearUtilization <= 1.0001f);
    }

    @Test
    public void airborneWheelTorqueChangesPitchWithoutAirMode() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 120; i++) p.step(terrain, DT);
        terrain.voided = true;
        p.input().throttle = 1f;
        float initialPitch = p.pitch();
        for (int i = 0; i < 120; i++) p.step(terrain, DT);

        assertTrue(p.airborne());
        assertTrue(Math.abs(p.pitch() - initialPitch) > 0.0001f);
    }

    @Test
    public void fixedStepRunIsDeterministic() {
        FlatTerrain terrainA = new FlatTerrain();
        FlatTerrain terrainB = new FlatTerrain();
        PhysicsV2Plant a = new PhysicsV2Plant();
        PhysicsV2Plant b = new PhysicsV2Plant();
        a.reset(terrainA);
        b.reset(terrainB);
        a.input().throttle = b.input().throttle = 0.62f;
        a.input().steer = b.input().steer = 0.18f;
        for (int i = 0; i < 600; i++) {
            a.step(terrainA, DT);
            b.step(terrainB, DT);
        }
        assertEquals(a.x(), b.x(), 0f);
        assertEquals(a.y(), b.y(), 0f);
        assertEquals(a.z(), b.z(), 0f);
        assertEquals(a.pitch(), b.pitch(), 0f);
    }
}
