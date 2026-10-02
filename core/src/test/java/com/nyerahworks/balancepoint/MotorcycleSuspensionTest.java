package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MotorcycleSuspensionTest {
    private static final float DT = 1f / 120f;
    private static final float MASS = 198f;
    private static final float GRAVITY = 9.81f;
    private static final float WHEELBASE = 1.403232f;
    private static final float COM_FORWARD = 0.636107f;
    private static final float REAR_LOAD = MASS * GRAVITY
            * (WHEELBASE - COM_FORWARD) / WHEELBASE;
    private static final float FRONT_LOAD = MASS * GRAVITY
            * COM_FORWARD / WHEELBASE;
    private static final float MAX_FORCE = MASS * GRAVITY * 5f;

    @Test
    public void resetStartsAtStaticSagAndStaticLoad() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);

        assertEquals(REAR_LOAD, suspension.rear().force(), 0.5f);
        assertEquals(FRONT_LOAD, suspension.front().force(), 0.5f);
        assertEquals(0f, suspension.rear().rideOffset(), 0.0001f);
        assertEquals(0f, suspension.front().rideOffset(), 0.0001f);
    }

    @Test
    public void compressionGeneratesMoreForceThanStaticSag() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit rear = suspension.rear();

        float compressed = rear.solve(
                rear.staticCompression() + 0.060f,
                0.80f,
                MASS * 0.55f,
                DT);

        assertTrue(compressed > REAR_LOAD);
        assertTrue(rear.rideOffset() > 0f);
        assertTrue(rear.velocity() > 0f);
    }

    @Test
    public void reboundDampingReducesSpringForce() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();
        float compression = front.staticCompression() + 0.045f;

        float compressionForce = front.solve(
                compression,
                0.55f,
                MASS * 0.45f,
                DT);
        float reboundForce = front.solve(
                compression,
                -0.55f,
                MASS * 0.45f,
                DT);

        assertTrue(compressionForce > reboundForce);
    }

    @Test
    public void bumpStopIsProgressiveNearFullTravel() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit rear = suspension.rear();

        float midStroke = rear.solve(0.20f, 0f, MASS * 0.55f, DT);
        float nearBottom = rear.solve(0.285f, 0f, MASS * 0.55f, DT);

        assertTrue(nearBottom > midStroke * 1.35f);
    }

    @Test
    public void travelAndForceRemainBoundedOnHugeImpact() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();

        float force = front.solve(0.70f, 14f, MASS * 0.45f, DT);

        assertEquals(MotorcycleSuspension.FRONT_TRAVEL,
                front.compression(), 0.0001f);
        assertTrue(force <= MAX_FORCE);
        assertTrue(force >= 0f);
    }

    @Test
    public void fullExtensionCarriesNoNormalForce() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);

        assertEquals(0f, suspension.front().solve(
                -0.03f, -1.2f, MASS * 0.45f, DT), 0.0001f);
        assertEquals(0f, suspension.front().compression(), 0.0001f);
    }
}
