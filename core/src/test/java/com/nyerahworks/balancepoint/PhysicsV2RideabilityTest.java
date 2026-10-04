package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2RideabilityTest {
    private static final float DT = 1f / 120f;

    private static final class FlatTerrain implements PhysicsV2Plant.Terrain {
        float height;

        @Override public float height(float x, float z) { return height; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void moderateThrottleDoesNotCreateSpuriousWheelie() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 240; i++) p.step(terrain, DT);

        p.input().throttle = 0.62f;
        int frontOffSamples = 0;
        float maxPitch = 0f;
        for (int i = 0; i < 600; i++) {
            p.step(terrain, DT);
            if (!p.frontGrounded() && p.rearGrounded()) frontOffSamples++;
            maxPitch = Math.max(maxPitch, Math.abs(p.pitch()));
        }
        assertTrue("front-off samples=" + frontOffSamples, frontOffSamples < 36);
        assertTrue("max pitch=" + maxPitch, maxPitch < radians(12f));
    }

    @Test
    public void turnCommandIsRollRegulatedInsteadOfFallingOver() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        p.input().throttle = 0.42f;
        for (int i = 0; i < 600; i++) p.step(terrain, DT);

        p.input().throttle = 0.18f;
        p.input().steer = 0.42f;
        float maxRoll = 0f;
        for (int i = 0; i < 300; i++) {
            p.step(terrain, DT);
            maxRoll = Math.max(maxRoll, Math.abs(p.roll()));
        }
        assertTrue("max roll=" + maxRoll, maxRoll < radians(42f));

        p.input().steer = 0f;
        for (int i = 0; i < 300; i++) p.step(terrain, DT);
        assertTrue("release roll=" + p.roll(), Math.abs(p.roll()) < radians(20f));
    }

    @Test
    public void suspensionHeaveSettlesAfterRoadStep() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 480; i++) p.step(terrain, DT);

        terrain.height = 0.06f;
        for (int i = 0; i < 720; i++) p.step(terrain, DT);
        assertTrue("front compression=" + p.frontCompression(),
                Math.abs(p.frontCompression() - 0.08035f) < 0.025f);
        assertTrue("rear compression=" + p.rearCompression(),
                Math.abs(p.rearCompression() - 0.09240f) < 0.025f);
        assertTrue("vertical velocity=" + p.verticalVelocity(),
                Math.abs(p.verticalVelocity()) < 0.20f);
    }

    @Test
    public void forwardRiderPositionReducesHardLaunchPitch() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant neutral = new PhysicsV2Plant();
        PhysicsV2Plant forward = new PhysicsV2Plant();
        neutral.reset(terrain);
        forward.reset(terrain);
        neutral.input().throttle = 0.82f;
        forward.input().throttle = 0.82f;
        forward.input().riderForeAft = 0.65f;
        float neutralMax = 0f;
        float forwardMax = 0f;
        for (int i = 0; i < 360; i++) {
            neutral.step(terrain, DT);
            forward.step(terrain, DT);
            neutralMax = Math.max(neutralMax, neutral.pitch());
            forwardMax = Math.max(forwardMax, forward.pitch());
        }
        assertTrue("neutral=" + neutralMax + " forward=" + forwardMax,
                forwardMax < neutralMax);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }
}
