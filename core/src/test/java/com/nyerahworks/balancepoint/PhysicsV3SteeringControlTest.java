package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV3SteeringControlTest {
    private static final float DT = 1f / 120f;

    private static final class FlatTerrain implements PhysicsV3MultibodyPlant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void rightStickCommandsRightFrontSteerAtRest() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV3MultibodyPlant p = new PhysicsV3MultibodyPlant();
        p.reset(terrain);
        for (int i = 0; i < 120; i++) p.step(terrain, DT);

        p.input().steer = 0.65f;
        for (int i = 0; i < 90; i++) p.step(terrain, DT);

        assertTrue("steerAngle=" + p.steerAngle(), p.steerAngle() > 0.02f);
    }

    @Test
    public void rightTurnCountersteersThenLeansAndYawsRight() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV3MultibodyPlant p = new PhysicsV3MultibodyPlant();
        p.reset(terrain);
        p.input().throttle = 0.55f;
        for (int i = 0; i < 420; i++) p.step(terrain, DT);

        float yaw0 = p.yaw();
        p.input().steer = 0.45f;
        float minimumSteer = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 240; i++) {
            p.step(terrain, DT);
            if (i < 100) minimumSteer = Math.min(minimumSteer, p.steerAngle());
        }

        assertTrue("speed=" + p.speed(), p.speed() > 3f);
        assertTrue("minimumSteer=" + minimumSteer, minimumSteer < -0.01f);
        assertTrue("roll=" + p.roll(), p.roll() < -0.05f);
        assertTrue("lateSteer=" + p.steerAngle(), p.steerAngle() > 0f);
        assertTrue("yawDelta=" + (p.yaw() - yaw0), p.yaw() - yaw0 > 0.015f);
    }

    @Test
    public void releasingStickRecentersWithoutPersistentRoll() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV3MultibodyPlant p = new PhysicsV3MultibodyPlant();
        p.reset(terrain);
        p.input().throttle = 0.55f;
        for (int i = 0; i < 420; i++) p.step(terrain, DT);

        p.input().steer = 0.50f;
        for (int i = 0; i < 180; i++) p.step(terrain, DT);
        float heldRoll = Math.abs(p.roll());

        p.input().steer = 0f;
        for (int i = 0; i < 240; i++) p.step(terrain, DT);

        assertTrue("heldRoll=" + heldRoll, heldRoll > 0.05f);
        assertTrue("releasedRoll=" + p.roll(), Math.abs(p.roll()) < 0.12f);
        assertTrue("releasedSteer=" + p.steerAngle(), Math.abs(p.steerAngle()) < 0.08f);
    }
    @Test
    public void capturePostTurnOscillationAndRightInputResponse() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV3MultibodyPlant bike = new PhysicsV3MultibodyPlant();
        bike.reset(terrain);
        bike.input().throttle = 0.75f;
        for (int i = 0; i < 600; i++) bike.step(terrain, DT);

        float startYaw = bike.yaw();
        bike.input().steer = 0.52f;
        float firstSteer = 0f;
        float peakNegative = 0f;
        for (int i = 0; i < 180; i++) {
            bike.step(terrain, DT);
            if (i == 20) firstSteer = bike.steerAngle();
            peakNegative = Math.min(peakNegative, bike.steerAngle());
        }

        float heldYawChange = bike.yaw() - startYaw;
        float heldRoll = bike.roll();
        bike.input().steer = 0f;
        float earlyMax = 0f;
        float lateMax = 0f;
        float lateYawRate = 0f;
        float lateRollRate = 0f;
        int lateSteerReversals = 0;
        int lastSign = 0;
        for (int i = 0; i < 960; i++) {
            bike.step(terrain, DT);
            float steer = bike.steerAngle();
            if (i < 240) earlyMax = Math.max(earlyMax, Math.abs(steer));
            if (i >= 480) {
                lateMax = Math.max(lateMax, Math.abs(steer));
                lateYawRate = Math.max(lateYawRate, Math.abs(bike.yawRate()));
                lateRollRate = Math.max(lateRollRate, Math.abs(bike.rollRate()));
                int sign = steer > 0.025f ? 1 : (steer < -0.025f ? -1 : 0);
                if (sign != 0 && lastSign != 0 && sign != lastSign) lateSteerReversals++;
                if (sign != 0) lastSign = sign;
            }
        }
        System.out.println("STEERING_DIAG speed=" + bike.speed()
                + " yawOnRight=" + heldYawChange + " rollOnRight=" + heldRoll
                + " firstSteer=" + firstSteer + " oppositePeak=" + peakNegative
                + " earlySteer=" + earlyMax + " lateSteer=" + lateMax
                + " lateYawRate=" + lateYawRate + " lateRollRate=" + lateRollRate
                + " lateReversals=" + lateSteerReversals);
        assertTrue("wrong sustained turn direction: " + heldYawChange,
                heldYawChange > 0.01f);
        assertTrue("post-release steer weave " + lateMax, lateMax < 0.12f);
        assertTrue("post-release yaw weave " + lateYawRate, lateYawRate < 0.55f);
        assertTrue("post-release repeated steering reversals " + lateSteerReversals,
                lateSteerReversals <= 3);
    }

}
