package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV3MultibodyPlantTest {
    private static final float DT = 1f / 120f;

    private static final class TestTerrain implements PhysicsV3MultibodyPlant.Terrain {
        boolean voided;
        boolean waves;

        @Override
        public float height(float x, float z) {
            if (voided) {
                return -100f;
            }
            if (!waves) {
                return 0f;
            }
            return 0.055f * (float)Math.sin(z * 1.7f);
        }

        @Override
        public float slopeX(float x, float z) {
            return 0f;
        }

        @Override
        public float slopeZ(float x, float z) {
            if (voided || !waves) {
                return 0f;
            }
            return 0.0935f * (float)Math.cos(z * 1.7f);
        }

        @Override
        public float traction(float x, float z) {
            return 1f;
        }
    }

    @Test
    public void flatGroundSettlesAndCarriesStaticLoad() {
        TestTerrain terrain = new TestTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        run(plant, terrain, 3f);

        float totalLoad = plant.telemetry().frontFz + plant.telemetry().rearFz;
        assertTrue("y=" + plant.y(), plant.y() > 0.55f && plant.y() < 1.05f);
        assertTrue("pitch=" + plant.pitch(), Math.abs(plant.pitch()) < 0.22f);
        assertTrue("roll=" + plant.roll(), Math.abs(plant.roll()) < 0.22f);
        assertTrue("load=" + totalLoad, totalLoad > 1200f && totalLoad < 2700f);
    }

    @Test
    public void throttleAcceleratesWithoutImmediateLoop() {
        TestTerrain terrain = new TestTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        run(plant, terrain, 1.5f);
        plant.input().throttle = 0.62f;
        run(plant, terrain, 4f);

        assertTrue("speed=" + plant.speed(), plant.speed() > 1.5f);
        assertTrue("pitch=" + plant.pitch(), plant.pitch() < 1.10f);
        assertTrue(plant.telemetry().rearUtilization <= 1.0001f);
        assertTrue(plant.telemetry().frontUtilization <= 1.0001f);
    }

    @Test
    public void rightTurnCommandProducesBoundedRightLean() {
        TestTerrain terrain = new TestTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        plant.input().throttle = 0.55f;
        run(plant, terrain, 3f);
        plant.input().steer = 0.45f;
        run(plant, terrain, 2.5f);

        assertTrue("roll=" + plant.roll(), plant.roll() < -0.015f);
        assertTrue("roll=" + plant.roll(), plant.roll() > -0.95f);
        assertTrue("yawRate=" + plant.yawRate(), Math.abs(plant.yawRate()) < 3.0f);
    }

    @Test
    public void terrainVoidProducesTrueUnilateralContactLoss() {
        TestTerrain terrain = new TestTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        plant.input().throttle = 0.5f;
        run(plant, terrain, 2f);
        terrain.voided = true;
        run(plant, terrain, 0.5f);

        assertTrue(plant.airborne());
        assertEquals(0f, plant.telemetry().frontFz, 0.001f);
        assertEquals(0f, plant.telemetry().rearFz, 0.001f);
        assertEquals(0f, plant.telemetry().frontFx, 0.001f);
        assertEquals(0f, plant.telemetry().rearFx, 0.001f);
    }

    @Test
    public void airborneThrottleChangesPitchThroughWheelMomentum() {
        TestTerrain terrain = new TestTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        run(plant, terrain, 1f);
        terrain.voided = true;
        run(plant, terrain, 0.25f);
        float before = plant.pitch();
        plant.input().throttle = 1f;
        run(plant, terrain, 0.8f);

        assertTrue(plant.airborne());
        assertTrue("deltaPitch=" + (plant.pitch() - before),
                Math.abs(plant.pitch() - before) > 0.002f);
    }

    @Test
    public void suspensionStaysInsidePhysicalTravelOnWaves() {
        TestTerrain terrain = new TestTerrain();
        terrain.waves = true;
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        plant.input().throttle = 0.55f;
        run(plant, terrain, 7f);

        assertTrue(plant.frontCompression() >= -0.001f);
        assertTrue(plant.frontCompression() <= 0.3105f);
        assertTrue(plant.rearCompression() >= -0.001f);
        assertTrue(plant.rearCompression() <= 0.3105f);
        assertFalse(Float.isNaN(plant.pitch()));
        assertFalse(Float.isNaN(plant.roll()));
    }

    @Test
    public void fixedStepRunIsDeterministic() {
        TestTerrain terrainA = new TestTerrain();
        TestTerrain terrainB = new TestTerrain();
        PhysicsV3MultibodyPlant a = new PhysicsV3MultibodyPlant();
        PhysicsV3MultibodyPlant b = new PhysicsV3MultibodyPlant();
        a.reset(terrainA);
        b.reset(terrainB);
        a.input().throttle = b.input().throttle = 0.58f;
        a.input().steer = b.input().steer = 0.22f;
        a.input().riderForeAft = b.input().riderForeAft = -0.25f;

        for (int i = 0; i < 720; i++) {
            a.step(terrainA, DT);
            b.step(terrainB, DT);
        }

        assertEquals(a.x(), b.x(), 0f);
        assertEquals(a.y(), b.y(), 0f);
        assertEquals(a.z(), b.z(), 0f);
        assertEquals(a.pitch(), b.pitch(), 0f);
        assertEquals(a.roll(), b.roll(), 0f);
    }

    @Test
    public void timestepHalvingConvergesOnWavyTerrain() {
        TestTerrain terrainA = new TestTerrain();
        TestTerrain terrainB = new TestTerrain();
        terrainA.waves = true;
        terrainB.waves = true;
        PhysicsV3MultibodyPlant a = new PhysicsV3MultibodyPlant();
        PhysicsV3MultibodyPlant b = new PhysicsV3MultibodyPlant();
        a.reset(terrainA);
        b.reset(terrainB);
        a.input().throttle = b.input().throttle = 0.52f;
        a.input().steer = b.input().steer = 0.18f;

        int coarseSteps = 600;
        for (int i = 0; i < coarseSteps; i++) {
            a.step(terrainA, 1f / 120f);
            b.step(terrainB, 1f / 240f);
            b.step(terrainB, 1f / 240f);
        }

        assertTrue("z delta=" + Math.abs(a.z() - b.z()),
                Math.abs(a.z() - b.z()) < 0.9f);
        assertTrue("pitch delta=" + Math.abs(a.pitch() - b.pitch()),
                Math.abs(a.pitch() - b.pitch()) < 0.18f);
        assertTrue("roll delta=" + Math.abs(a.roll() - b.roll()),
                Math.abs(a.roll() - b.roll()) < 0.20f);
    }

    private static void run(PhysicsV3MultibodyPlant plant,
                            TestTerrain terrain, float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            plant.step(terrain, DT);
        }
    }
}
