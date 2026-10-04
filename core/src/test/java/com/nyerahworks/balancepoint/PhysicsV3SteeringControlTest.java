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
}
