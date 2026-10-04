package com.nyerahworks.balancepoint;

/**
 * Compact motorcycle Pacejka/relaxation tire used by Physics V3.
 *
 * Structure ported from the MIT-licensed moto-sim PacejkaTire implementation:
 * https://github.com/dikshant1103-beep/moto-sim
 * The coefficients below are Balance Point MX hardpack parameters rather than
 * the road-bike defaults from that project.
 */
final class PhysicsV3PacejkaTire {
    static final class Spec {
        final float radius;
        final float effectiveRadius;
        final float mu;
        final float kAlpha;
        final float kGamma;
        final float sigma;

        Spec(float radius, float effectiveRadius, float mu,
             float kAlpha, float kGamma, float sigma) {
            this.radius = radius;
            this.effectiveRadius = effectiveRadius;
            this.mu = mu;
            this.kAlpha = kAlpha;
            this.kGamma = kGamma;
            this.sigma = sigma;
        }
    }

    static final class State {
        float relaxedAlpha;

        void reset() {
            relaxedAlpha = 0f;
        }
    }

    static final class Force {
        float fx;
        float fy;
        float mz;
        float utilization;

        void clear() {
            fx = fy = mz = utilization = 0f;
        }
    }

    static final Spec FRONT = new Spec(
            0.3478f,
            0.3499f,
            0.95f,
            11.0f,
            0.72f,
            0.20f);

    static final Spec REAR = new Spec(
            0.3317f,
            0.3337f,
            1.05f,
            12.5f,
            0.66f,
            0.24f);

    private static final float C_PAC = 1.80f;
    private static final float E_PAC = 0f;
    private static final float KX = 12f;
    private static final float TP0 = 0.030f;
    private static final float BT = 10f;
    private static final float CT = 1.50f;
    private static final float K_GAMMA_MZ = 0.020f;
    private static final float BXA = 10f;
    private static final float CXA = 1f;
    private static final float BYK = 8f;
    private static final float CYK = 1f;

    private PhysicsV3PacejkaTire() {
    }

    static void step(Spec spec, State state, Force out,
                     float fz, float alphaKinematic, float kappa,
                     float gamma, float longitudinalSpeed,
                     float dt, float tractionScale) {
        if (fz <= 0f) {
            out.clear();
            state.relaxedAlpha *= Math.max(0f, 1f - dt * 8f);
            return;
        }

        float vx = Math.max(Math.abs(longitudinalSpeed), 0.001f);
        float decay = (float)Math.exp(-vx * dt / Math.max(spec.sigma, 0.01f));
        state.relaxedAlpha = alphaKinematic
                + (state.relaxedAlpha - alphaKinematic) * decay;

        float traction = clamp(tractionScale, 0.20f, 1.35f);
        float capacity = Math.max(1f, spec.mu * traction * fz);
        float d = capacity;
        float bcd = spec.kAlpha * fz;
        float b = bcd / Math.max(C_PAC * d, 1e-6f);
        float ba = b * state.relaxedAlpha;
        float phi = ba - E_PAC * (ba - (float)Math.atan(ba));
        float fyPacejka = d * (float)Math.sin(C_PAC * Math.atan(phi));
        float fyCamber = spec.kGamma * fz * gamma;
        float fyPure = clamp(fyPacejka + fyCamber, -d, d);

        float fxPure = d * (float)Math.tanh(KX * kappa);
        float gxa = (float)Math.cos(CXA * Math.atan(BXA * state.relaxedAlpha));
        float gyk = (float)Math.cos(CYK * Math.atan(BYK * kappa));
        float fx = gxa * fxPure;
        float fy = gyk * fyPure;

        float nx = fx / capacity;
        float ny = fy / capacity;
        float utilization = (float)Math.sqrt(nx * nx + ny * ny);
        if (utilization > 1f) {
            float inv = 1f / utilization;
            fx *= inv;
            fy *= inv;
            utilization = 1f;
        }

        float tp = TP0 * (float)Math.cos(CT * Math.atan(BT * state.relaxedAlpha));
        float mz = -fy * tp + K_GAMMA_MZ * gamma * fz;

        out.fx = fx;
        out.fy = fy;
        out.mz = mz;
        out.utilization = utilization;
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
