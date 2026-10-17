package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Live V3 physics tuning parameters. Defaults exactly match the original V3
 * controller and suspension equations; the editor does not introduce forces.
 */
final class PhysicsTuning {
    static final String[] CATEGORIES = {
            "Steering & Balance", "Rider Movement", "Suspension", "Wheelies"
    };
    static final String[][] SUBCATEGORIES = {
            {"Steering", "Countersteering", "Stabilization"},
            {"Weight transfer"},
            {"Front fork", "Rear shock"},
            {"Wheelie catch"}
    };

    static final class Parameter {
        final int category;
        final int subcategory;
        final String label;
        final String units;
        final float minimum;
        final float maximum;
        final float defaultValue;

        Parameter(int category, int subcategory, String label, String units,
                  float minimum, float maximum, float defaultValue) {
            this.category = category;
            this.subcategory = subcategory;
            this.label = label;
            this.units = units;
            this.minimum = minimum;
            this.maximum = maximum;
            this.defaultValue = defaultValue;
        }
    }

    static final int STEER_KP = 0;
    static final int STEER_KD = 1;
    static final int COUNTER_KP = 2;
    static final int COUNTER_KD = 3;
    static final int BALANCE_KP = 4;
    static final int BALANCE_KD = 5;
    static final int RIDER_KP = 6;
    static final int RIDER_KD = 7;
    static final int FRONT_COMP = 8;
    static final int FRONT_REBOUND = 9;
    static final int REAR_COMP = 10;
    static final int REAR_REBOUND = 11;
    static final int CATCH_KP = 12;
    static final int CATCH_KD = 13;
    static final int CATCH_MAX = 14;

    static final Parameter[] PARAMETERS = {
            new Parameter(0, 0, "Steering response", "gain", 0f, 100f, 46f),
            new Parameter(0, 0, "Steering damping", "gain", 0f, 40f, 25f),
            new Parameter(0, 1, "Countersteer response", "gain", 0f, 2f, 0.20f),
            new Parameter(0, 1, "Countersteer damping", "gain", 0f, 2f, 0.05f),
            new Parameter(0, 2, "Upright assist", "gain", 0f, 400f, 160f),
            new Parameter(0, 2, "Upright damping", "gain", 0f, 220f, 85f),
            new Parameter(1, 0, "Weight-shift response", "gain", 0f, 100f, 46f),
            new Parameter(1, 0, "Weight-shift damping", "gain", 0f, 50f, 13f),
            new Parameter(2, 0, "Compression damping", "N s/m", 100f, 5000f, 1450f),
            new Parameter(2, 0, "Rebound damping", "N s/m", 100f, 7000f, 2150f),
            new Parameter(2, 1, "Compression damping", "N s/m", 100f, 6000f, 1650f),
            new Parameter(2, 1, "Rebound damping", "N s/m", 100f, 8000f, 2450f),
            new Parameter(3, 0, "Wheelie catch strength", "gain", 0f, 1200f, 480f),
            new Parameter(3, 0, "Wheelie catch damping", "gain", 0f, 350f, 120f),
            new Parameter(3, 0, "Maximum catch torque", "N m", 0f, 1200f, 520f)
    };

    private final float[] values = new float[PARAMETERS.length];
    private final float[][] presets = new float[2][PARAMETERS.length];
    private final boolean[] presetSaved = new boolean[2];

    PhysicsTuning() {
        resetDefaults();
    }

    float get(int id) {
        return values[id];
    }

    void set(int id, float value) {
        Parameter p = PARAMETERS[id];
        if (!Float.isNaN(value) && !Float.isInfinite(value)) {
            values[id] = MathUtils.clamp(value, p.minimum, p.maximum);
        }
    }

    void resetDefaults() {
        for (int i = 0; i < PARAMETERS.length; i++) {
            values[i] = PARAMETERS[i].defaultValue;
        }
    }

    void savePreset(int slot) {
        System.arraycopy(values, 0, presets[slot], 0, values.length);
        presetSaved[slot] = true;
    }

    boolean loadPreset(int slot) {
        if (!presetSaved[slot]) return false;
        System.arraycopy(presets[slot], 0, values, 0, values.length);
        return true;
    }

    boolean hasPreset(int slot) {
        return presetSaved[slot];
    }

    static int parameterAt(int category, int subcategory, int row) {
        for (int i = 0; i < PARAMETERS.length; i++) {
            Parameter p = PARAMETERS[i];
            if (p.category == category && p.subcategory == subcategory) {
                if (row-- == 0) return i;
            }
        }
        return -1;
    }

    static int count(int category, int subcategory) {
        int count = 0;
        for (Parameter p : PARAMETERS) {
            if (p.category == category && p.subcategory == subcategory) count++;
        }
        return count;
    }
}
