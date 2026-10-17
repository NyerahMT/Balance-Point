package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Regression for return-to-center oscillation: a steering controller must not
 * repeatedly overshoot upright after turn intent returns to zero.
 */
public final class PhysicsV3TurnReleaseTest {
    private static final float DT = 1f / 120f;

    private static final class FlatTerrain implements PhysicsV3MultibodyPlant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    private static final class Response {
        float heldRoll;
        float releasePeakRoll;
        float latePeakRoll;
        float finalRoll;
        float finalRollRate;
        float finalYawRate;
        float earlyRollRange;
        float lateRollRange;
        int substantialReversals;

        String report() {
            return "heldRoll=" + heldRoll + ", peak=" + releasePeakRoll
                    + ", latePeak=" + latePeakRoll
                    + ", finalRoll=" + finalRoll
                    + ", finalRollRate=" + finalRollRate
                    + ", finalYawRate=" + finalYawRate
                    + ", earlyRange=" + earlyRollRange
                    + ", lateRange=" + lateRollRange
                    + ", substantialReversals=" + substantialReversals;
        }
    }

    private static Response driveAndRelease(float steerCommand) {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        plant.reset(terrain);
        plant.input().throttle = 0.55f;
        for (int i = 0; i < 420; i++) plant.step(terrain, DT);

        plant.input().steer = steerCommand;
        for (int i = 0; i < 180; i++) plant.step(terrain, DT);

        Response result = new Response();
        result.heldRoll = plant.roll();
        plant.input().steer = 0f;
        float earlyMin = Float.POSITIVE_INFINITY;
        float earlyMax = Float.NEGATIVE_INFINITY;
        float lateMin = Float.POSITIVE_INFINITY;
        float lateMax = Float.NEGATIVE_INFINITY;
        int lastSide = 0;
        // More than the previous two-second endpoint test: inspect the full
        // response, including any sustained oscillation after releasing input.
        for (int i = 0; i < 600; i++) {
            plant.step(terrain, DT);
            float roll = plant.roll();
            float absRoll = Math.abs(roll);
            result.releasePeakRoll = Math.max(result.releasePeakRoll, absRoll);
            if (i < 120) {
                earlyMin = Math.min(earlyMin, roll);
                earlyMax = Math.max(earlyMax, roll);
            }
            if (i >= 240) {
                lateMin = Math.min(lateMin, roll);
                lateMax = Math.max(lateMax, roll);
                result.latePeakRoll = Math.max(result.latePeakRoll, absRoll);
            }
            int side = roll > 0.035f ? 1 : roll < -0.035f ? -1 : 0;
            if (side != 0) {
                if (lastSide != 0 && side != lastSide) result.substantialReversals++;
                lastSide = side;
            }
        }
        result.earlyRollRange = earlyMax - earlyMin;
        result.lateRollRange = lateMax - lateMin;
        result.finalRoll = plant.roll();
        result.finalRollRate = plant.rollRate();
        result.finalYawRate = plant.yawRate();
        return result;
    }

    @Test
    public void rightTurnReleaseSettlesRatherThanWeaving() {
        Response result = driveAndRelease(0.5f);
        assertTrue("Right-turn response: " + result.report(),
                Math.abs(result.heldRoll) > 0.05f
                && result.latePeakRoll < 0.14f
                && Math.abs(result.finalRollRate) < 0.25f
                && result.substantialReversals <= 2);
    }

    @Test
    public void leftTurnReleaseSettlesRatherThanWeaving() {
        Response result = driveAndRelease(-0.5f);
        assertTrue("Left-turn response: " + result.report(),
                Math.abs(result.heldRoll) > 0.05f
                && result.latePeakRoll < 0.14f
                && Math.abs(result.finalRollRate) < 0.25f
                && result.substantialReversals <= 2);
    }
}
