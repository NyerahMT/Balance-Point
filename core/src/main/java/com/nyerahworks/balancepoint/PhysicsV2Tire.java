package com.nyerahworks.balancepoint;

/**
 * Coherent hardpack tire model for the first Physics V2 plant.
 *
 * One model owns vertical compliance, pure slip, combined slip, relaxation and contact moments.
 * The numbers mirror the bounded Gate-D parameter pack and remain centralized/replaceable.
 */
final class PhysicsV2Tire {
    static final class Spec {
        final float radius;
        final float effectiveRadius;
        final float verticalKAt960;
        final float radialDamping;
        final float muX;
        final float muY;
        final float cxPerFz;
        final float caPerFz;
        final float camberGain;
        final float relaxX;
        final float relaxY;
        final float pneumaticTrail;
        final float overturningCoeff;

        Spec(float radius, float effectiveRadius, float verticalKAt960,
             float radialDamping, float muX, float muY, float cxPerFz,
             float caPerFz, float camberGain, float relaxX, float relaxY,
             float pneumaticTrail, float overturningCoeff) {
            this.radius = radius;
            this.effectiveRadius = effectiveRadius;
            this.verticalKAt960 = verticalKAt960;
            this.radialDamping = radialDamping;
            this.muX = muX;
            this.muY = muY;
            this.cxPerFz = cxPerFz;
            this.caPerFz = caPerFz;
            this.camberGain = camberGain;
            this.relaxX = relaxX;
            this.relaxY = relaxY;
            this.pneumaticTrail = pneumaticTrail;
            this.overturningCoeff = overturningCoeff;
        }
    }

    static final class State {
        float fx;
        float fy;

        void reset() {
            fx = 0f;
            fy = 0f;
        }
    }

    static final class Force {
        float fx;
        float fy;
        float fz;
        float mz;
        float mx;
        float utilization;
    }

    static final Spec FRONT = new Spec(
            0.3538f, 0.34991484f, 105_000f, 550f,
            0.95f, 0.90f, 11f, 9f, 0.32f,
            0.14f, 0.20f, 0.030f, 0.020f);

    static final Spec REAR = new Spec(
            0.3373f, 0.3336522f, 115_000f, 650f,
            1.05f, 0.95f, 10f, 8f, 0.28f,
            0.16f, 0.24f, 0.020f, 0.020f);

    private static final float MU_REFERENCE_LOAD = 1000f;
    private static final float MU_LOAD_EXPONENT = 0.08f;

    private PhysicsV2Tire() {}

    static float effectiveVerticalStiffness(Spec spec, float load) {
        load = Math.max(0f, load);
        if (load <= 960f) {
            return Math.max(25_000f, spec.verticalKAt960 + 35f * (load - 960f));
        }
        if (load <= 1920f) {
            return spec.verticalKAt960 + 75f * (load - 960f);
        }
        float at1920 = spec.verticalKAt960 + 75f * 960f;
        return at1920 + 55f * (load - 1920f);
    }

    /** Invert the Gate-D load-dependent effective-stiffness construction by fixed point. */
    static float verticalLoad(Spec spec, float deflection, float deflectionRate) {
        if (deflection <= 0f) return 0f;
        float load = deflection * spec.verticalKAt960;
        for (int i = 0; i < 7; i++) {
            float elastic = deflection * effectiveVerticalStiffness(spec, load);
            load = 0.45f * load + 0.55f * elastic;
        }
        load += spec.radialDamping * deflectionRate;
        return clamp(load, 0f, 6_000f);
    }

    static Force step(Spec spec, State state, Force out, float fz,
                      float kappa, float alpha, float gamma,
                      float longitudinalSpeed, float dt, float surfaceScale) {
        if (fz <= 0.5f) {
            state.reset();
            out.fx = out.fy = out.fz = out.mz = out.mx = out.utilization = 0f;
            return out;
        }

        surfaceScale = clamp(surfaceScale, 0.55f, 1.15f);
        float muX = loadSensitiveMu(spec.muX * surfaceScale, fz);
        float muY = loadSensitiveMu(spec.muY * surfaceScale, fz);
        float maxX = Math.max(1f, muX * fz);
        float maxY = Math.max(1f, muY * fz);
        // Camber thrust acts toward the lean direction. Positive gamma in this coordinate
        // system is left lean, so its equivalent slip-angle contribution is negative.
        float alphaEffective = alpha - spec.camberGain * gamma;

        float pureFx = maxX * (float)Math.tanh(spec.cxPerFz * fz * kappa / maxX);
        float pureFy = maxY * (float)Math.tanh(spec.caPerFz * fz * alphaEffective / maxY);
        float nx = pureFx / maxX;
        float ny = pureFy / maxY;
        float pureUtil = (float)Math.sqrt(nx * nx + ny * ny);
        float combinedScale = 1f / Math.max(1f, pureUtil);
        float targetFx = pureFx * combinedScale;
        float targetFy = pureFy * combinedScale;

        float speed = Math.max(Math.abs(longitudinalSpeed), 1.5f);
        float tauX = spec.relaxX / speed;
        float tauY = spec.relaxY / speed;
        float bx = 1f - (float)Math.exp(-dt / Math.max(tauX, 0.001f));
        float by = 1f - (float)Math.exp(-dt / Math.max(tauY, 0.001f));
        state.fx += (targetFx - state.fx) * bx;
        state.fy += (targetFy - state.fy) * by;

        nx = state.fx / maxX;
        ny = state.fy / maxY;
        float relaxedUtil = (float)Math.sqrt(nx * nx + ny * ny);
        if (relaxedUtil > 1f) {
            state.fx /= relaxedUtil;
            state.fy /= relaxedUtil;
            relaxedUtil = 1f;
        }

        float lateralUtil = Math.min(1f, Math.abs(state.fy) / maxY);
        float trail = spec.pneumaticTrail * (1f - lateralUtil);
        out.fx = state.fx;
        out.fy = state.fy;
        out.fz = fz;
        out.mz = -trail * state.fy;
        out.mx = -spec.overturningCoeff * fz * spec.radius * (float)Math.sin(gamma);
        out.utilization = relaxedUtil;
        return out;
    }

    private static float loadSensitiveMu(float muRef, float fz) {
        float ratio = Math.max(0.20f, fz / MU_REFERENCE_LOAD);
        return muRef * (float)Math.pow(ratio, -MU_LOAD_EXPONENT);
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
