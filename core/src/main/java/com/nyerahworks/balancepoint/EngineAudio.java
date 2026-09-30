package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Tiny procedural engine synthesizer.
 *
 * This avoids shipping a placeholder loop and makes the sound follow real drivetrain RPM. It is
 * intentionally lightweight enough for the GMEE-class Android target and also works through the
 * LibGDX audio backend on iOS.
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

    private double exhaustPhase;
    private double mechanicalPhase;
    private int noiseState = 0x13579BDF;

    EngineAudio() {
        AudioDevice created = null;
        try {
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
            created.setVolume(0.55f);
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

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.16f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.20f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.28f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.22f;

            float firingHz = Math.max(12f, smoothRpm / 120f); // four-stroke single: one fire / 2 revs
            float mechanicalHz = Math.max(24f, smoothRpm / 60f);
            float load = 0.22f + smoothThrottle * 0.78f;
            float shiftCut = 1f - smoothShift * 0.55f;
            float amplitude = smoothGain * shiftCut * (0.075f + smoothThrottle * 0.20f);

            double exhaustStep = MathUtils.PI2 * firingHz / SAMPLE_RATE;
            double mechanicalStep = MathUtils.PI2 * mechanicalHz / SAMPLE_RATE;

            for (int i = 0; i < buffer.length; i++) {
                exhaustPhase += exhaustStep;
                mechanicalPhase += mechanicalStep;
                if (exhaustPhase > MathUtils.PI2) exhaustPhase -= MathUtils.PI2;
                if (mechanicalPhase > MathUtils.PI2) mechanicalPhase -= MathUtils.PI2;

                // Exhaust pulse plus upper harmonics gives the low "thump" and bark of a single.
                double exhaust = Math.sin(exhaustPhase)
                        + 0.48 * Math.sin(exhaustPhase * 2.0)
                        + 0.24 * Math.sin(exhaustPhase * 3.0)
                        + 0.11 * Math.sin(exhaustPhase * 5.0);
                exhaust *= 0.52;

                // Mechanical/intake content keeps high RPM from sounding like only a bass tone.
                double mechanical = 0.20 * Math.sin(mechanicalPhase * 2.0)
                        + 0.10 * Math.sin(mechanicalPhase * 3.0)
                        + 0.06 * Math.sin(mechanicalPhase * 5.0);

                noiseState = noiseState * 1664525 + 1013904223;
                double noise = (((noiseState >>> 8) & 0xFFFF) / 32767.5 - 1.0)
                        * (0.025 + 0.055 * smoothThrottle);

                double sample = (exhaust * load + mechanical + noise) * amplitude;
                sample = Math.max(-0.92, Math.min(0.92, sample));
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
