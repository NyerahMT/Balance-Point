package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;

/** Normalized throttle-slider hit box and value mapping. Screen Y is top-origin. */
final class ThrottleControl {
    static final float LEFT = 0.82f;
    static final float RIGHT = 1.00f;
    static final float TOP = 0.46f;
    static final float BOTTOM = 0.90f;

    private static final float ZERO_Y = 0.88f;
    private static final float TRAVEL = 0.40f;

    private ThrottleControl() {}

    static boolean contains(float x, float y) {
        return x > LEFT && x <= RIGHT && y > TOP && y < BOTTOM;
    }

    static float value(float y) {
        return MathUtils.clamp((ZERO_Y - y) / TRAVEL, 0f, 1f);
    }
}
