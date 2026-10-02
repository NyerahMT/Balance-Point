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
    public void frontForkStartsWithMeaningfulRiderSag() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);

        assertTrue("front sag should exceed 70 mm",
                suspension.front().staticCompression() > 0.070f);
        assertTrue("front sag should leave ample compression travel",
                suspension.front().staticCompression()
                        < MotorcycleSuspension.FRONT_TRAVEL * 0.35f);
    }

    @Test
    public void compressionGeneratesMoreForceThanStaticSag() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit rear = suspension.rear();

        float compressed = rear.probeForce(
                rear.staticCompression() + 0.060f,
                0.80f,
                MASS * 0.55f,
                DT);

        assertTrue(compressed > REAR_LOAD);
    }

    @Test
    public void reboundDampingReducesSpringForce() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();
        float compression = front.staticCompression() + 0.045f;

        float compressionForce = front.probeForce(
                compression,
                0.55f,
                MASS * 0.45f,
                DT);
        float reboundForce = front.probeForce(
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

        float midStroke = rear.probeForce(0.20f, 0f, MASS * 0.55f, DT);
        float nearBottom = rear.probeForce(0.285f, 0f, MASS * 0.55f, DT);

        assertTrue(nearBottom > midStroke * 1.35f);
    }

    @Test
    public void springForceRemainsBoundedOnHugeImpact() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();

        float force = front.probeForce(0.70f, 14f, MASS * 0.45f, DT);

        assertTrue(force <= MAX_FORCE);
        assertTrue(force >= 0f);
    }

    @Test
    public void staticGroundRemainsAnEquilibrium() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit rear = suspension.rear();
        float startCompression = rear.compression();

        for (int i = 0; i < 120; i++) {
            float force = rear.solve(
                    rear.staticCompression(),
                    0f,
                    MASS * 0.55f,
                    DT);
            assertTrue(force > 0f);
        }

        assertEquals(startCompression, rear.compression(), 0.0015f);
        assertEquals(REAR_LOAD, rear.force(), REAR_LOAD * 0.06f);
    }

    @Test
    public void airborneWheelExtendsContinuouslyTowardDroop() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();
        float start = front.compression();

        // A 20 cm positive gap means the ground has dropped away. One physics frame should
        // begin extension, not teleport the fork to full droop.
        front.solve(front.staticCompression() - 0.20f, 0f, MASS * 0.45f, DT);
        assertTrue(front.compression() < start);
        assertTrue(front.compression() > 0f);

        for (int i = 0; i < 90; i++) {
            front.solve(front.staticCompression() - 0.20f, 0f, MASS * 0.45f, DT);
        }
        assertEquals(0f, front.compression(), 0.001f);
        assertEquals(0f, front.force(), 0.001f);
    }

    @Test
    public void wheelExtensionCanFollowAChangingCrest() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit rear = suspension.rear();
        boolean regainedContact = false;

        // Ground falls just beyond the statically squashed tire. The wheel initially unloads,
        // then spring extension should chase the surface instead of remaining at static sag.
        for (int i = 0; i < 30; i++) {
            float force = rear.solve(
                    rear.staticCompression() - 0.014f,
                    0f,
                    MASS * 0.55f,
                    DT);
            if (i > 1 && force > 20f) regainedContact = true;
        }

        assertTrue(rear.physicalRideOffset() < 0f);
        assertTrue("extended wheel should be capable of re-contacting a small crest gap",
                regainedContact);
    }

    @Test
    public void landingImpactCompressesPersistentWheelState() {
        MotorcycleSuspension suspension = new MotorcycleSuspension(
                REAR_LOAD, FRONT_LOAD, MAX_FORCE);
        MotorcycleSuspension.Unit front = suspension.front();

        for (int i = 0; i < 45; i++) {
            front.solve(front.staticCompression() - 0.20f, 0f, MASS * 0.45f, DT);
        }
        float airborneCompression = front.compression();
        assertTrue(airborneCompression < front.staticCompression() * 0.20f);

        float peakForce = 0f;
        for (int i = 0; i < 12; i++) {
            float force = front.solve(
                    front.staticCompression() + 0.075f,
                    3.0f,
                    MASS * 0.45f,
                    DT);
            peakForce = Math.max(peakForce, force);
        }

        assertTrue(peakForce > FRONT_LOAD * 1.8f);
        assertTrue(front.compression() > airborneCompression);
        assertTrue(front.compression() <= MotorcycleSuspension.FRONT_TRAVEL);
        assertTrue(Float.isFinite(front.compression()));
        assertTrue(Float.isFinite(front.velocity()));
    }
}
