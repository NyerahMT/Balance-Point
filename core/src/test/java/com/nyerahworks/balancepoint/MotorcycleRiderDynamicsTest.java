package com.nyerahworks.balancepoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MotorcycleRiderDynamicsTest {
    private static final float DT = 1f / 120f;

    @Test
    public void forwardInputMovesMassProgressively() {
        MotorcycleRiderDynamics rider = new MotorcycleRiderDynamics(198f, 9.81f);

        rider.step(1f, 0f, false, DT);
        float firstPosition = rider.position();
        assertTrue(firstPosition > 0f);
        assertTrue(firstPosition < 0.05f);

        for (int i = 0; i < 120; i++) {
            rider.step(1f, 0f, false, DT);
        }

        assertTrue(rider.position() > 0.30f);
        assertTrue(rider.combinedComShift() > 0.12f);
        assertTrue(rider.pose() > 0.80f);
    }

    @Test
    public void airborneBodyThrowRespondsFasterThanGrounded() {
        MotorcycleRiderDynamics grounded = new MotorcycleRiderDynamics(198f, 9.81f);
        MotorcycleRiderDynamics airborne = new MotorcycleRiderDynamics(198f, 9.81f);

        for (int i = 0; i < 15; i++) {
            grounded.step(1f, 0f, false, DT);
            airborne.step(1f, 0f, true, DT);
        }

        assertTrue(airborne.position() > grounded.position() * 1.20f);
        assertTrue(airborne.velocity() > grounded.velocity());
    }

    @Test
    public void accelerationMakesNeutralRiderLagRearward() {
        MotorcycleRiderDynamics rider = new MotorcycleRiderDynamics(198f, 9.81f);

        for (int i = 0; i < 120; i++) {
            rider.step(0f, 8.0f, false, DT);
        }

        assertTrue(rider.position() < -0.025f);
        assertTrue(rider.combinedComShift() < 0f);
    }

    @Test
    public void bodyThrowCreatesBoundedPitchReaction() {
        MotorcycleRiderDynamics rider = new MotorcycleRiderDynamics(198f, 9.81f);

        rider.step(1f, 0f, true, DT);
        float torque = rider.pitchReactionTorque(true, false);

        assertTrue(torque < 0f);
        assertTrue(Math.abs(torque) <= 180f);

        for (int i = 0; i < 360; i++) {
            rider.step(1f, 0f, true, DT);
        }
        assertTrue(Math.abs(rider.pitchReactionTorque(true, false)) < 20f);
    }

    @Test
    public void resetClearsRiderState() {
        MotorcycleRiderDynamics rider = new MotorcycleRiderDynamics(198f, 9.81f);
        for (int i = 0; i < 60; i++) rider.step(-1f, 0f, false, DT);

        rider.reset();

        assertEquals(0f, rider.position(), 0.0001f);
        assertEquals(0f, rider.velocity(), 0.0001f);
        assertEquals(0f, rider.acceleration(), 0.0001f);
        assertEquals(0f, rider.combinedComShift(), 0.0001f);
    }
}
