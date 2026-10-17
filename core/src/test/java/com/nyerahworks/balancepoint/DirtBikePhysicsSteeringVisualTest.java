package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Fork visuals must represent the live steering generalized coordinate.
 * A positive rider turn demand can initially countersteer in the opposite
 * direction, so the fork must NOT be synthesized from controller input.
 */
public final class DirtBikePhysicsSteeringVisualTest {
    @Test
    public void frontForkMatchesPhysicalSteerAtAllSpeeds() {
        GameScene.BikeState bike = new GameScene.BikeState();
        bike.physicsSteerAngle = -0.06f;
        bike.speed = 0f;
        float atRest = DirtBikeVisualRig.physicalSteerDegrees(bike);
        bike.speed = 30f;
        bike.roll = -0.35f;
        bike.frontGrounded = false;
        assertEquals(atRest, DirtBikeVisualRig.physicalSteerDegrees(bike), 0.00001f);
        assertTrue(atRest < 0f);
    }

    @Test
    public void positiveSteerStaysPositiveEvenWithOpposingBodyRoll() {
        GameScene.BikeState bike = new GameScene.BikeState();
        bike.physicsSteerAngle = 0.1f;
        bike.roll = -0.45f;
        bike.speed = 25f;
        assertEquals(0.1f * 57.29578f,
                DirtBikeVisualRig.physicalSteerDegrees(bike), 0.0001f);
    }

    @Test
    public void neutralPhysicsSteeringDoesNotInventTurnFromLean() {
        GameScene.BikeState bike = new GameScene.BikeState();
        bike.physicsSteerAngle = 0f;
        bike.roll = 0.4f;
        bike.speed = 15f;
        assertEquals(0f, DirtBikeVisualRig.physicalSteerDegrees(bike), 0f);
    }
}
