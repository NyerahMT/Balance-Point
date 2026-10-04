package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Balance-point wheelie contract, tuned to the MX vs ATV Legends assist-off feel:
 * rearward weight plus throttle lifts the front, the bike can hang near the point
 * without an anti-wheelie glue torque, and rear brake drops the nose.
 */
public final class PhysicsV2BalancePointTest {
    private static final float DT = 1f / 120f;

    private static final class Flat implements PhysicsV2Plant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void rearwardWeightAndThrottleLiftHigherThanNeutral() {
        Flat terrain = new Flat();
        float neutral = pitchAt(terrain, 0f, 0.88f, 150);
        float back = pitchAt(terrain, -0.85f, 0.88f, 150);
        assertTrue(
                "neutral=" + neutral + " back=" + back,
                back > neutral + radians(2f));
        assertTrue("back pitch=" + back, back > radians(6f));
    }

    @Test
    public void rearBrakeDropsAHighWheelie() {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        p.input().riderForeAft = -0.75f;
        p.input().throttle = 0.88f;
        float lifted = 0f;
        for (int i = 0; i < 360; i++) {
            p.step(terrain, DT);
            lifted = Math.max(lifted, p.pitch());
        }
        assertTrue("did not lift, pitch=" + lifted, lifted > radians(6f));

        p.input().throttle = 0.10f;
        p.input().riderForeAft = 0.40f;
        p.input().rearBrake = 1f;
        float minPitch = p.pitch();
        for (int i = 0; i < 200; i++) {
            p.step(terrain, DT);
            minPitch = Math.min(minPitch, p.pitch());
        }
        assertTrue(
                "brake did not drop nose, lifted=" + lifted + " min=" + minPitch,
                minPitch < lifted - radians(4f));
    }

    @Test
    public void balancePointCanBeHeldWithoutLoopingOrGluing() {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        p.input().riderForeAft = -0.70f;
        float peak = 0f;
        int frontUp = 0;
        for (int i = 0; i < 900; i++) {
            float pitch = p.pitch();
            // Tap throttle the way Legends holds a wheelie: more gas if it falls,
            // less gas as it climbs toward the point. No rear brake.
            p.input().throttle = pitch < radians(18f) ? 0.85f : 0.22f;
            p.input().rearBrake = 0f;
            p.step(terrain, DT);
            peak = Math.max(peak, p.pitch());
            if (!p.frontGrounded() && p.rearGrounded()) frontUp++;
            assertTrue("non-finite at " + i, Float.isFinite(p.pitch()));
        }
        assertTrue("never lifted, peak=" + peak, frontUp > 40);
        assertTrue("looped, peak=" + peak, peak < radians(70f));
        assertTrue("glued low, peak=" + peak, peak > radians(12f));
    }

    private static float pitchAt(Flat terrain, float rider, float throttle, int steps) {
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        p.input().riderForeAft = rider;
        p.input().throttle = throttle;
        for (int i = 0; i < steps; i++) p.step(terrain, DT);
        return p.pitch();
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }
}
