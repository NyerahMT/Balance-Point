package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsTuningTest {
    @Test
    public void defaultsUseValidatedSteeringAndOriginalWheelieCoefficients() {
        PhysicsV3MultibodyPlant plant = new PhysicsV3MultibodyPlant();
        PhysicsTuning tuning = plant.tuning();
        assertEquals(46f, tuning.get(PhysicsTuning.STEER_KP), 0.0001f);
        assertEquals(25f, tuning.get(PhysicsTuning.STEER_KD), 0.0001f);
        assertEquals(0.20f, tuning.get(PhysicsTuning.COUNTER_KP), 0.0001f);
        assertEquals(0.15f, tuning.get(PhysicsTuning.COUNTER_KD), 0.0001f);
        assertEquals(160f, tuning.get(PhysicsTuning.BALANCE_KP), 0.0001f);
        assertEquals(520f, tuning.get(PhysicsTuning.CATCH_MAX), 0.0001f);
    }

    @Test
    public void clampsOutOfRangeAndRejectsNonfiniteValues() {
        PhysicsTuning tuning = new PhysicsTuning();
        tuning.set(PhysicsTuning.CATCH_MAX, -100f);
        assertEquals(0f, tuning.get(PhysicsTuning.CATCH_MAX), 0f);
        tuning.set(PhysicsTuning.STEER_KP, 10000f);
        assertEquals(100f, tuning.get(PhysicsTuning.STEER_KP), 0f);
        tuning.set(PhysicsTuning.STEER_KP, Float.NaN);
        assertEquals(100f, tuning.get(PhysicsTuning.STEER_KP), 0f);
    }

    @Test
    public void presetsAndDefaultsRemainIndependent() {
        PhysicsTuning tuning = new PhysicsTuning();
        assertFalse(tuning.hasPreset(0));
        tuning.set(PhysicsTuning.STEER_KP, 33f);
        tuning.savePreset(0);
        assertTrue(tuning.hasPreset(0));
        tuning.set(PhysicsTuning.STEER_KP, 9f);
        assertTrue(tuning.loadPreset(0));
        assertEquals(33f, tuning.get(PhysicsTuning.STEER_KP), 0f);
        tuning.resetDefaults();
        assertEquals(46f, tuning.get(PhysicsTuning.STEER_KP), 0f);
        assertFalse(tuning.loadPreset(1));
    }

    @Test
    public void categoriesResolveTheirOwnParameters() {
        assertEquals(2, PhysicsTuning.count(2, 0));
        assertEquals(PhysicsTuning.FRONT_COMP, PhysicsTuning.parameterAt(2, 0, 0));
        assertEquals(PhysicsTuning.REAR_COMP, PhysicsTuning.parameterAt(2, 1, 0));
        assertEquals(-1, PhysicsTuning.parameterAt(2, 1, 8));
    }
}
