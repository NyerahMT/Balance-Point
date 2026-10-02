package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThrottleControlTest {
    @Test
    public void hitBoxMatchesVisibleSliderArea() {
        assertTrue(ThrottleControl.contains(0.91f, 0.65f));
        assertFalse(ThrottleControl.contains(0.70f, 0.65f));
    }

    @Test
    public void valueClampsOutsideSliderTravel() {
        assertEquals(1f, ThrottleControl.value(0.10f), 0.0001f);
        assertEquals(0f, ThrottleControl.value(0.95f), 0.0001f);
        assertEquals(0.5f, ThrottleControl.value(0.68f), 0.0001f);
    }
}
