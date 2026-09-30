package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Procedural 450-class four-stroke single engine synthesizer.
 *
 * Built around discrete combustion pressure events, low exhaust-system resonances, intake bark
 * and restrained mechanical content. The exhaust body is intentionally voiced low and heavy so
 * the motor reads as a large 450 single rather than a small buzzy engine.
 */
final class EngineAudio {
    private static final int SAMPLE_RATE = 22_050;
    private static final int BUFFER_SAMPLES = 512;

    private final AudioDevice device;
    private final Thread audioThread;

    private volatile boolean running;
    private volatile boolean active = true;
    private volatile float targetRpm = 1_800f;
    private volatile float targetThrottle;
    private volatile boolean targetShifting;

    // Combustion phase is 0..1 over the full four-stroke firing interval (720 crank degrees).
    private double combustionPhase;
    private double crankPhase;

    // Resonators excited by each combustion event. These frequencies define most of the perceived
    // exhaust body. They are deliberately about an octave below the previous pass.
    private double low1;
    private double low2;
    private double mid1;
    private double mid2;
    private double bark1;
    private double bark2;

    private double noiseLow;
    private int noiseState = 0x13579BDF;
    private int firingCount;

    EngineAudio() {
        AudioDevice created = null;
        try {
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
            created.setVolume(0.66f);
        } catch (Throwable t) {
            Gdx.app.error("BalancePoint", "Engine audio unavailable", t);
        }
        device = created;

        if (device != null) {
            running = true;
            audioThread = new Thread(this::audioLoop, "balance-point-engine-audio");
            audioThread.setDaemon(true);
            audioThread.start();
        } else {
            audioThread = null;
        }
    }

    void update(float rpm, float throttle, boolean shifting, boolean crashed) {
        targetRpm = MathUtils.clamp(rpm, 1_200f, 14_000f);
        targetThrottle = crashed ? 0f : MathUtils.clamp(throttle, 0f, 1f);
        targetShifting = shifting;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    private void audioLoop() {
        short[] buffer = new short[BUFFER_SAMPLES];
        float smoothRpm = targetRpm;
        float smoothThrottle = 0f;
        float smoothGain = 0f;
        float smoothShift = 0f;
        boolean previousShiftTarget = false;
        double shiftCrack = 0.0;

        // Lower the exhaust body's resonant character about one octave without lying about RPM or
        // changing the actual four-stroke firing cadence.
        final double lowA = 2.0 * 0.996 * Math.cos(MathUtils.PI2 * 46.0 / SAMPLE_RATE);
        final double lowB = 0.996 * 0.996;
        final double midA = 2.0 * 0.994 * Math.cos(MathUtils.PI2 * 108.0 / SAMPLE_RATE);
        final double midB = 0.994 * 0.994;
        final double barkA = 2.0 * 0.988 * Math.cos(MathUtils.PI2 * 360.0 / SAMPLE_RATE);
        final double barkB = 0.988 * 0.988;

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.18f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.24f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.32f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.24f;

            boolean currentShiftTarget = targetShifting;
            if (currentShiftTarget && !previousShiftTarget && smoothThrottle > 0.38f) {
                shiftCrack = 1.0;
            }
            previousShiftTarget = currentShiftTarget;

            // A four-stroke single fires once every two crank revolutions. Keep this physically
            // correct; perceived pitch is lowered with exhaust-body voicing instead.
            float firingHz = Math.max(10f, smoothRpm / 120f);
            float crankHz = Math.max(20f, smoothRpm / 60f);
            double firingStep = firingHz / SAMPLE_RATE;
            double crankStep = MathUtils.PI2 * crankHz / SAMPLE_RATE;

            float load = 0.30f + smoothThrottle * 0.70f;
            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 11_400f, 0f, 1f);
            float shiftCut = 1f - smoothShift * 0.50f;
            float master = smoothGain * (0.30f + 0.70f * shiftCut);

            for (int i = 0; i < buffer.length; i++) {
                boolean fired = false;
                combustionPhase += firingStep;
                if (combustionPhase >= 1.0) {
                    combustionPhase -= 1.0;
                    fired = true;
                    firingCount++;
                }

                crankPhase += crankStep;
                if (crankPhase >= MathUtils.PI2) crankPhase -= MathUtils.PI2;

                boolean limiterMiss = smoothRpm > 13_050f && smoothThrottle > 0.72f
                        && (firingCount & 3) == 3;

                noiseState = noiseState * 1664525 + 1013904223;
                double rawNoise = (((noiseState >>> 8) & 0xFFFF) / 32767.5) - 1.0;

                // Heavier low-pass than the previous pass. Keep only a small amount of dry edge.
                noiseLow += (rawNoise - noiseLow) * 0.045;
                double noiseHigh = rawNoise - noiseLow;

                double excitation = 0.0;
                if (fired && !limiterMiss) {
                    double cycleVariation = 0.95 + 0.06 * Math.abs(rawNoise);
                    excitation = (0.78 + 1.08 * smoothThrottle) * cycleVariation;
                }

                double nextLow = lowA * low1 - lowB * low2 + excitation * 0.82;
                low2 = low1;
                low1 = nextLow;

                double nextMid = midA * mid1 - midB * mid2 + excitation * 0.42;
                mid2 = mid1;
                mid1 = nextMid;

                double nextBark = barkA * bark1 - barkB * bark2
                        + excitation * (0.075 + 0.15 * smoothThrottle);
                bark2 = bark1;
                bark1 = nextBark;

                // Wider pressure pulse means less click/buzz and more of the heavy thump that a
                // large-bore single produces through the exhaust.
                double phase = combustionPhase;
                double pressurePulse = Math.exp(-phase * 21.0)
                        - 0.24 * Math.exp(-phase * 5.0);

                double intakeDistance = Math.abs(phase - 0.56);
                double intakeEnvelope = Math.exp(-intakeDistance * intakeDistance * 360.0);
                double intake = noiseHigh * intakeEnvelope * (0.045 + 0.20 * smoothThrottle);

                // Keep the top-end mechanical order audible but well behind the exhaust. The old
                // high-order stack was a major source of the electric/buzzy character.
                double mechanical = (0.018 + 0.036 * rpmNorm)
                        * (Math.sin(crankPhase)
                        + 0.22 * Math.sin(crankPhase * 2.0)
                        + 0.08 * Math.sin(crankPhase * 3.0));

                double rasp = noiseHigh * (0.005 + 0.015 * smoothThrottle + 0.010 * rpmNorm);

                double crack = noiseHigh * shiftCrack * 0.24;
                shiftCrack *= 0.9975;
                if (shiftCrack < 0.0005) shiftCrack = 0.0;

                // Weight the lowest resonator most heavily. Mid/bark provide definition without
                // dragging the apparent engine size upward.
                double exhaustBody = low1 * 0.145 + mid1 * 0.060 + bark1 * 0.022;
                double direct = pressurePulse * (0.24 + 0.38 * smoothThrottle) * load;

                double sample = (exhaustBody + direct + intake + mechanical + rasp + crack)
                        * master;

                // Softer saturation retains the pressure/compression feel without generating as
                // many extra upper harmonics as the previous harder drive.
                sample = Math.tanh(sample * 1.28) * 0.82;
                buffer[i] = (short) (sample * 32767.0);
            }

            try {
                device.writeSamples(buffer, 0, buffer.length);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    void dispose() {
        running = false;
        if (audioThread != null) {
            try {
                audioThread.join(120L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (device != null) device.dispose();
    }
}
