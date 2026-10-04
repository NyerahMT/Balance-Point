package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2StabilityTest {
    private static final class Flat implements PhysicsV2Plant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    private static final class Wavy implements PhysicsV2Plant.Terrain {
        @Override public float height(float x, float z) {
            return 0.035f * (float)Math.sin(z * 1.7f)
                    + 0.012f * (float)Math.sin(z * 4.2f);
        }

        @Override public float slopeX(float x, float z) { return 0f; }

        @Override public float slopeZ(float x, float z) {
            return 0.0595f * (float)Math.cos(z * 1.7f)
                    + 0.0504f * (float)Math.cos(z * 4.2f);
        }

        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void timestepHalvingConvergesForStraightLaunch() {
        Snapshot a = runFlat(1f / 120f, false);
        Snapshot b = runFlat(1f / 240f, false);
        assertTrue(
                "speed delta=" + Math.abs(a.speed - b.speed),
                Math.abs(a.speed - b.speed) < 0.35f);
        assertTrue(
                "pitch delta=" + Math.abs(a.pitch - b.pitch),
                Math.abs(a.pitch - b.pitch) < radians(1.5f));
        assertTrue(
                "height delta=" + Math.abs(a.y - b.y),
                Math.abs(a.y - b.y) < 0.02f);
    }

    @Test
    public void timestepHalvingConvergesForMildTurn() {
        Snapshot a = runFlat(1f / 120f, true);
        Snapshot b = runFlat(1f / 240f, true);
        assertTrue(
                "roll delta=" + Math.abs(a.roll - b.roll),
                Math.abs(a.roll - b.roll) < radians(3f));
        assertTrue(
                "yaw delta=" + Math.abs(a.yaw - b.yaw),
                angleDelta(a.yaw, b.yaw) < radians(4f));
    }

    @Test
    public void roughTerrainTimestepHalvingRemainsClose() {
        Snapshot a = runWavy(1f / 120f);
        Snapshot b = runWavy(1f / 240f);
        assertTrue(
                "rough speed delta=" + Math.abs(a.speed - b.speed),
                Math.abs(a.speed - b.speed) < 0.65f);
        assertTrue(
                "rough pitch delta=" + Math.abs(a.pitch - b.pitch),
                Math.abs(a.pitch - b.pitch) < radians(4f));
        assertTrue(
                "rough roll delta=" + Math.abs(a.roll - b.roll),
                Math.abs(a.roll - b.roll) < radians(5f));
    }

    @Test
    public void scriptedRideDoesNotDevelopSpontaneousAngularExplosion() {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        float dt = 1f / 120f;
        float maxPitchRate = 0f;
        float maxRollRate = 0f;
        for (int i = 0; i < 1200; i++) {
            float t = i * dt;
            p.input().throttle =
                    t < 2f ? 0.38f : (t < 5f ? 0.55f : 0.28f);
            p.input().steer =
                    t < 2.5f ? 0f : (t < 4f ? 0.22f :
                            (t < 5.5f ? -0.18f : 0f));
            p.input().riderForeAft = t < 3f ? 0.10f : 0f;
            p.step(terrain, dt);
            maxPitchRate = Math.max(maxPitchRate, Math.abs(p.pitchRate()));
            maxRollRate = Math.max(maxRollRate, Math.abs(p.rollRate()));
            assertTrue(
                    "non-finite orientation at t=" + t,
                    Float.isFinite(p.pitch())
                            && Float.isFinite(p.roll())
                            && Float.isFinite(p.yaw()));
        }
        assertTrue("pitch rate=" + maxPitchRate, maxPitchRate < 5f);
        assertTrue("roll rate=" + maxRollRate, maxRollRate < 7f);
    }

    private Snapshot runFlat(float dt, boolean turn) {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        int count = Math.round(5f / dt);
        for (int i = 0; i < count; i++) {
            float t = i * dt;
            p.input().throttle = t < 1f ? 0.25f : 0.48f;
            p.input().steer =
                    turn && t > 2.0f && t < 4.0f ? 0.20f : 0f;
            p.step(terrain, dt);
        }
        return snapshot(p);
    }

    private Snapshot runWavy(float dt) {
        Wavy terrain = new Wavy();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        int count = Math.round(6f / dt);
        for (int i = 0; i < count; i++) {
            float t = i * dt;
            p.input().throttle = 0.38f + 0.05f * (float)Math.sin(t * 0.8f);
            p.input().steer = 0.10f * (float)Math.sin(t * 0.65f);
            p.step(terrain, dt);
        }
        return snapshot(p);
    }

    private Snapshot snapshot(PhysicsV2Plant p) {
        Snapshot s = new Snapshot();
        s.speed = p.speed();
        s.pitch = p.pitch();
        s.roll = p.roll();
        s.yaw = p.yaw();
        s.y = p.y();
        return s;
    }

    private static float angleDelta(float a, float b) {
        float d = Math.abs(a - b);
        while (d > Math.PI * 2f) d -= Math.PI * 2f;
        return d > Math.PI ? (float)(Math.PI * 2f - d) : d;
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

    private static final class Snapshot {
        float speed, pitch, roll, yaw, y;
    }
}
