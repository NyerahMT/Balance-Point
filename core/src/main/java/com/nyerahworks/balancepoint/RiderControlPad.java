package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/**
 * Shared normalized geometry and axis mapping for the two-axis rider control pad.
 * Screen Y is top-origin here because it is consumed directly from Gdx.input.
 */
final class RiderControlPad {
    static final float LEFT = 0.035f;
    static final float RIGHT = 0.295f;
    static final float TOP = 0.50f;
    static final float BOTTOM = 0.96f;

    private static final float CENTER_X = (LEFT + RIGHT) * 0.5f;
    private static final float CENTER_Y = (TOP + BOTTOM) * 0.5f;
    private static final float HALF_WIDTH = (RIGHT - LEFT) * 0.5f;
    private static final float HALF_HEIGHT = (BOTTOM - TOP) * 0.5f;
    private static final float DEADZONE = 0.055f;

    private RiderControlPad() {}

    static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }

    static float steer(float x) {
        float raw = MathUtils.clamp((x - CENTER_X) / HALF_WIDTH, -1f, 1f);
        float deadzoned = applyDeadzone(raw);
        float magnitude = Math.abs(deadzoned);
        float shaped = magnitude * (0.62f + 0.38f * magnitude * magnitude);
        return Math.signum(deadzoned) * shaped;
    }

    static float lean(float y) {
        // Finger above center means rider forward/positive lean.
        float raw = MathUtils.clamp((CENTER_Y - y) / HALF_HEIGHT, -1f, 1f);
        return applyDeadzone(raw);
    }

    private static float applyDeadzone(float value) {
        float magnitude = Math.abs(value);
        if (magnitude <= DEADZONE) return 0f;
        float rescaled = (magnitude - DEADZONE) / (1f - DEADZONE);
        return Math.signum(value) * MathUtils.clamp(rescaled, 0f, 1f);
    }
}
