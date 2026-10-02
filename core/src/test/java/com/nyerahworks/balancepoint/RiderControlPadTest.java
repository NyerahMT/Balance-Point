package com.nyerahworks.balancepoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RiderControlPadTest {
    @Test
    public void centerIsNeutral() {
        assertEquals(0f, RiderControlPad.steer(0.165f), 0.0001f);
        assertEquals(0f, RiderControlPad.lean(0.73f), 0.0001f);
    }

    @Test
    public void tallPadProvidesFullForeAftTravel() {
        assertEquals(1f, RiderControlPad.lean(RiderControlPad.TOP), 0.0001f);
        assertEquals(-1f, RiderControlPad.lean(RiderControlPad.BOTTOM), 0.0001f);
        assertEquals(1f, RiderControlPad.lean(RiderControlPad.TOP - 0.25f), 0.0001f);
        assertEquals(-1f, RiderControlPad.lean(RiderControlPad.BOTTOM + 0.25f), 0.0001f);
    }

    @Test
    public void steeringClampsWhenDraggedPastPad() {
        assertEquals(-1f, RiderControlPad.steer(RiderControlPad.LEFT - 0.25f), 0.0001f);
        assertEquals(1f, RiderControlPad.steer(RiderControlPad.RIGHT + 0.25f), 0.0001f);
    }

    @Test
    public void containsOnlyAcquisitionArea() {
        assertTrue(RiderControlPad.contains(0.165f, 0.73f));
        assertTrue(RiderControlPad.contains(RiderControlPad.LEFT, RiderControlPad.TOP));
        assertFalse(RiderControlPad.contains(RiderControlPad.LEFT - 0.001f, 0.73f));
        assertFalse(RiderControlPad.contains(0.165f, RiderControlPad.BOTTOM + 0.001f));
    }
}
