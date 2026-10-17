package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsDiagnosticsTest {
    private static final class Flat implements PhysicsV3MultibodyPlant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void recordAndReplayFromResetMatchExactly() {
        Flat ground = new Flat();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        PhysicsDiagnostics diag = new PhysicsDiagnostics();
        plant.reset(ground);
        diag.startCapture(plant.tuning());
        for (int i = 0; i < 150; i++) {
            PhysicsV3MultibodyPlant.Input in = plant.input();
            in.throttle = 0.34f;
            in.steer = i < 40 ? 0.05f : 0.21f;
            in.riderForeAft = -0.2f;
            plant.step(ground, PhysicsDiagnostics.FIXED_DT);
            diag.capture(in, plant);
        }
        diag.stopCapture();
        assertEquals(150, diag.frameCount());
        assertTrue(diag.startReplay());
        plant.reset(ground);
        int replayed = 0;
        while (diag.applyReplayInput(plant.input())) {
            plant.step(ground, PhysicsDiagnostics.FIXED_DT);
            diag.compareReplayStep(plant);
            replayed++;
        }
        assertEquals(150, replayed);
        assertFalse(diag.replaying());
        assertTrue("position diverged: " + diag.maxPositionError(),
                diag.maxPositionError() < 0.00001f);
        assertTrue("roll diverged: " + diag.maxRollError(),
                diag.maxRollError() < 0.00001f);
        assertTrue(diag.toCsv().contains("wheelie_catch_nm"));
        assertTrue(diag.toCsv().contains("# frames,150"));
    }

    @Test
    public void emptyCaptureCannotReplay() {
        PhysicsDiagnostics d = new PhysicsDiagnostics();
        assertFalse(d.startReplay());
        assertFalse(d.applyReplayInput(new PhysicsV3MultibodyPlant.Input()));
        assertEquals("", d.toCsv());
    }

    @Test
    public void playbackUsesRecordedInputsNotLiveTouch() {
        Flat ground = new Flat();
        PhysicsV3MultibodyPlant p = new PhysicsV3MultibodyPlant();
        PhysicsDiagnostics d = new PhysicsDiagnostics();
        d.startCapture(p.tuning());
        p.input().throttle = 0.68f;
        p.input().rearBrake = 0.12f;
        p.input().steer = -0.3f;
        p.input().riderForeAft = 0.25f;
        p.step(ground, PhysicsDiagnostics.FIXED_DT);
        d.capture(p.input(), p);
        d.stopCapture();
        assertTrue(d.startReplay());
        PhysicsV3MultibodyPlant.Input restore = new PhysicsV3MultibodyPlant.Input();
        assertTrue(d.applyReplayInput(restore));
        assertEquals(0.68f, restore.throttle, 0f);
        assertEquals(0.12f, restore.rearBrake, 0f);
        assertEquals(-0.3f, restore.steer, 0f);
        assertEquals(0.25f, restore.riderForeAft, 0f);
    }
}
