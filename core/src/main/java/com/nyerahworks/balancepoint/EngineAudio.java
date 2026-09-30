package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Procedural 450-class four-stroke single engine synthesizer.
 *
 * The first pass was mostly a stack of RPM-tracked sine waves, which made the engine sound tonal
 * and synthetic. This version is built around discrete combustion pressure events (one firing every
 * two crank revolutions), fixed exhaust-system resonances, intake bark and a small amount of
 * valvetrain/mechanical content. The result should read much more like a large MX single while
 * remaining tiny and fully RPM-driven on both Android and iOS.
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

    // Simple resonators excited by each combustion event. These are intentionally tied to the
    // exhaust system rather than RPM, so the note keeps a recognizable pipe/body character while
    // the firing cadence rises through the rev range.
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
            created.setVolume(0.62f);
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

        final double lowA = 2.0 * 0.994 * Math.cos(MathUtils.PI2 * 92.0 / SAMPLE_RATE);
        final double lowB = 0.994 * 0.994;
        final double midA = 2.0 * 0.991 * Math.cos(MathUtils.PI2 * 215.0 / SAMPLE_RATE);
        final double midB = 0.991 * 0.991;
        final double barkA = 2.0 * 0.982 * Math.cos(MathUtils.PI2 * 720.0 / SAMPLE_RATE);
        final double barkB = 0.982 * 0.982;

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

            // A four-stroke single fires once every two crank revolutions.
            float firingHz = Math.max(10f, smoothRpm / 120f);
            float crankHz = Math.max(20f, smoothRpm / 60f);
            double firingStep = firingHz / SAMPLE_RATE;
            double crankStep = MathUtils.PI2 * crankHz / SAMPLE_RATE;

            // Closed-throttle high-RPM running still has pumping/exhaust sound, but loaded running
            // gets a much stronger pressure pulse and more high-frequency bark.
            float load = 0.28f + smoothThrottle * 0.72f;
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

                // Near the limiter, deliberately miss an occasional combustion event. That gives
                // the top end a real ignition-cut texture instead of a steady synthesized whistle.
                boolean limiterMiss = smoothRpm > 13_050f && smoothThrottle > 0.72f
                        && (firingCount & 3) == 3;

                noiseState = noiseState * 1664525 + 1013904223;
                double rawNoise = (((noiseState >>> 8) & 0xFFFF) / 32767.5) - 1.0;
                noiseLow += (rawNoise - noiseLow) * 0.085;
                double noiseHigh = rawNoise - noiseLow;

                double excitation = 0.0;
                if (fired && !limiterMiss) {
                    // Small cycle-to-cycle variation stops the exhaust from sounding perfectly
                    // periodic while remaining subtle enough not to read as a misfire.
                    double cycleVariation = 0.94 + 0.08 * Math.abs(rawNoise);
                    excitation = (0.72 + 1.05 * smoothThrottle) * cycleVariation;
                }

                double nextLow = lowA * low1 - lowB * low2 + excitation * 0.72;
                low2 = low1;
                low1 = nextLow;

                double nextMid = midA * mid1 - midB * mid2 + excitation * 0.46;
                mid2 = mid1;
                mid1 = nextMid;

                double nextBark = barkA * bark1 - barkB * bark2
                        + excitation * (0.13 + 0.27 * smoothThrottle);
                bark2 = bark1;
                bark1 = nextBark;

                // Direct combustion-pressure wave: sharp attack, then a negative rarefaction tail.
                // This is the piece that creates the individual low-RPM "thump" and turns into a
                // compressed braap as the firing events overlap at high RPM.
                double phase = combustionPhase;
                double pressurePulse = Math.exp(-phase * 31.0)
                        - 0.27 * Math.exp(-phase * 6.5);

                // Intake valve event occurs later in the 720-degree cycle. The narrow noisy burst
                // becomes much more obvious under throttle, like a 450 airbox opening up.
                double intakeDistance = Math.abs(phase - 0.56);
                double intakeEnvelope = Math.exp(-intakeDistance * intakeDistance * 520.0);
                double intake = noiseHigh * intakeEnvelope * (0.10 + 0.44 * smoothThrottle);

                // Crank/valvetrain order content. Keep it quiet at low RPM and let it emerge toward
                // redline so the motor gains metallic urgency without dominating the exhaust.
                double mechanical = (0.040 + 0.085 * rpmNorm)
                        * (Math.sin(crankPhase * 2.0)
                        + 0.38 * Math.sin(crankPhase * 3.0)
                        + 0.18 * Math.sin(crankPhase * 5.0));

                // Broad exhaust rasp. More throttle and RPM increases the dry high-frequency edge.
                double rasp = noiseHigh * (0.018 + 0.050 * smoothThrottle + 0.035 * rpmNorm);

                // Upshifts get a short dry crack rather than only a volume dip.
                double crack = noiseHigh * shiftCrack * 0.42;
                shiftCrack *= 0.9981;
                if (shiftCrack < 0.0005) shiftCrack = 0.0;

                double exhaustBody = low1 * 0.105 + mid1 * 0.070 + bark1 * 0.040;
                double direct = pressurePulse * (0.20 + 0.34 * smoothThrottle) * load;

                double sample = (exhaustBody + direct + intake + mechanical + rasp + crack)
                        * master;

                // Soft saturation mimics the compressed, clipped pressure character of a loud
                // single-cylinder exhaust and prevents resonator peaks from hard-clipping audio.
                sample = Math.tanh(sample * 1.55) * 0.78;
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
