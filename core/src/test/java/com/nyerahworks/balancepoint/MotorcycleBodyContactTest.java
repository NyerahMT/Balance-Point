package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import org.junit.Test;

public final class MotorcycleBodyContactTest {
    private final MotorcycleBodyContact contact = new MotorcycleBodyContact();
    private final MotorcycleBodyContact.GroundHeightSampler flat = (x, z) -> 0f;

    @Test
    public void normalRidingDoesNotTouchHardParts() {
        assertFalse(contact.hitsGround(
                0f, 0.335f, 0f, 0f, 0f, 0f, flat));
    }

    @Test
    public void rearFenderCanScrapeBeforeSeatBecomesCrashPoint() {
        assertFalse(contact.hitsGround(
                0f, 0.335f, 0f, 0f, 105f * MathUtils.degreesToRadians, 0f, flat));
        assertTrue(contact.hitsGround(
                0f, 0.335f, 0f, 0f, 145f * MathUtils.degreesToRadians, 0f, flat));
    }

    @Test
    public void noseDownHandlebarStrikeCrashes() {
        assertTrue(contact.hitsGround(
                0f, 0.335f, 0f, 0f, -80f * MathUtils.degreesToRadians, 0f, flat));
    }
}
