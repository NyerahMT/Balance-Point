package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Lightweight physical-ish audio model for a 450-class four-stroke single.
 *
 * The audible source is exhaust pressure/flow, not an RPM-tracked oscillator. A 720-degree crank
 * cycle drives combustion, exhaust-valve blowdown and intake flow. The pressure pulse then travels
 * through fractional-delay feedback lines representing the header and silencer before being
 * converted to audio. This signal-flow approach is inspired by the MIT-licensed Engine Simulator
 * by Ange Yaghi, but is a small Java model written specifically for Balance Point.
 */
final class EngineAudio {
    private static final int SAMPLE_RATE = 44_100;
    private static final int BUFFER_SAMPLES = 512;

    // Representative modern 449 cc MX single geometry / timing. These are audio-model parameters,
    // not tuning data for a particular production motorcycle.
    private static final double COMPRESSION_RATIO = 13.0;
    private static final double ROD_RATIO = 3.45; // connecting-rod length / crank radius
    private static final double IGNITION_DEG = 356.0;
    private static final double EXHAUST_OPEN_DEG = 500.0;
    private static final double EXHAUST_CLOSE_DEG = 22.0;
    private static final double INTAKE_OPEN_DEG = 700.0;
    private static final double INTAKE_CLOSE_DEG = 220.0;

    // Acoustic lengths are round-trip waveguide lengths, converted to samples using the current
    // exhaust-gas speed of sound. A hot, loaded engine therefore changes pipe character subtly.
    private static final double HEADER_LENGTH_M = 0.82;
    private static final double SILENCER_LENGTH_M = 0.46;
    private static final int DELAY_BUFFER_SIZE = 512;

    private final AudioDevice device;
    private final Thread audioThread;

    private final double[] headerDelay = new double[DELAY_BUFFER_SIZE];
    private final double[] silencerDelay = new double[DELAY_BUFFER_SIZE];
    private int headerWrite;
    private int silencerWrite;

    private volatile boolean running;
    private volatile boolean active = true;
    private volatile float targetRpm = 1_800f;
    private volatile float targetThrottle;
    private volatile boolean targetShifting;

    private double cycleDeg;
    private double cycleStrength = 0.30;
    private int combustionCount;

    private double intakeNoiseLow;
    private double tailDc;
    private double tailLowPass;
    private double previousTail;
    private double afterfire;
    private int noiseState = 0x13579BDF;

    EngineAudio() {
        AudioDevice created = null;
        try {
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
            created.setVolume(0.72f);
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

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.20f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.24f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.34f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.24f;

            boolean shiftTarget = targetShifting;
            if (shiftTarget && !previousShiftTarget && smoothThrottle > 0.42f) {
                // Real four-stroke shifts often produce a pressure event in the pipe when ignition
                // and torque are cut. Feed it through the exhaust model instead of playing a click.
                afterfire = Math.max(afterfire, 0.72 + smoothThrottle * 0.45);
            }
            previousShiftTarget = shiftTarget;

            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 11_400f, 0f, 1f);
            float load = 0.24f + smoothThrottle * 0.76f;
            double degreesPerSample = smoothRpm * 6.0 / SAMPLE_RATE;

            // Hot exhaust gas transmits pressure waves faster than cold air. This small dynamic
            // change prevents the pipe from behaving like one static comb filter at every load.
            double soundSpeed = 430.0 + smoothThrottle * 72.0 + rpmNorm * 28.0;
            double headerDelaySamples = 2.0 * HEADER_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double silencerDelaySamples = 2.0 * SILENCER_LENGTH_M / soundSpeed * SAMPLE_RATE;

            // Pressure reflection: expansion at the open end inverts much of the returning wave;
            // the silencer is more lossy than the header.
            double headerReflection = -0.58 - smoothThrottle * 0.05;
            double silencerReflection = -0.34 - smoothThrottle * 0.04;

            // Enough top end to preserve valve/exhaust texture without recreating the old buzz.
            double cutoffHz = 3_200.0 + 1_350.0 * smoothThrottle + 450.0 * rpmNorm;
            double lpAlpha = 1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / SAMPLE_RATE);

            for (int i = 0; i < buffer.length; i++) {
                double previousDeg = cycleDeg;
                cycleDeg += degreesPerSample;
                if (cycleDeg >= 720.0) cycleDeg -= 720.0;

                noiseState = noiseState * 1664525 + 1013904223;
                double white = (((noiseState >>> 8) & 0xFFFF) / 32767.5) - 1.0;
                intakeNoiseLow += (white - intakeNoiseLow) * 0.055;
                double intakeNoise = white - intakeNoiseLow;

                if (crossedAngle(previousDeg, cycleDeg, IGNITION_DEG)) {
                    combustionCount++;
                    boolean limiterCut = smoothRpm > 13_050f && smoothThrottle > 0.70f
                            && (combustionCount & 3) == 3;
                    if (limiterCut) {
                        cycleStrength = 0.035;
                    } else {
                        // Idle still needs genuine combustion pressure. Throttle/load then raises
                        // the pressure dramatically, with mild cycle-to-cycle irregularity.
                        double variation = 0.965 + 0.07 * Math.abs(white);
                        cycleStrength = (0.31 + 1.58 * load) * variation;
                    }
                }

                double exhaustLift = wrappedValveLift(cycleDeg,
                        EXHAUST_OPEN_DEG, EXHAUST_CLOSE_DEG);
                double intakeLift = wrappedValveLift(cycleDeg,
                        INTAKE_OPEN_DEG, INTAKE_CLOSE_DEG);

                // Approximate cylinder volume from crank-slider geometry. The compression ratio
                // matters because blowdown pressure should change with piston position, not simply
                // decay as an arbitrary audio envelope.
                double volume = normalizedCylinderVolume(cycleDeg);
                double clearance = 1.0 / COMPRESSION_RATIO;

                double sinceIgnition = wrap720(cycleDeg - IGNITION_DEG);
                double combustionPressure = 0.0;
                if (sinceIgnition < 310.0) {
                    // Heat release rises quickly just after ignition, then expansion lowers pressure
                    // as piston volume increases. The exponent is intentionally softer than a full
                    // thermodynamic solver so the compact model remains stable and musical.
                    double burnRise = 1.0 - Math.exp(-sinceIgnition / 11.0);
                    double heatDecay = Math.exp(-sinceIgnition / 235.0);
                    double expansion = Math.pow(clearance / Math.max(clearance, volume), 0.72);
                    combustionPressure = cycleStrength * burnRise * heatDecay * expansion * 8.7;
                }

                // Compression/pumping component adds the characteristic pressure motion between
                // firing events. Closed throttle reduces trapped charge, while open throttle lets
                // the compression pulse become more pronounced.
                double compression = 0.0;
                if (cycleDeg >= 180.0 && cycleDeg < IGNITION_DEG) {
                    double trappedCharge = 0.18 + smoothThrottle * 0.82;
                    compression = trappedCharge
                            * (Math.pow(1.0 / Math.max(volume, clearance), 1.18) - 1.0)
                            * 0.055;
                }

                // Exhaust-valve flow is the actual acoustic source. Blowdown near EVO is strong;
                // later in the exhaust stroke piston pumping keeps a broader pressure tail alive.
                double exhaustPressure = combustionPressure + compression;
                double pumping = exhaustLift * (0.035 + 0.12 * load)
                        * Math.sin(Math.PI * MathUtils.clamp(
                        (float) wrapRange(cycleDeg, EXHAUST_OPEN_DEG, EXHAUST_CLOSE_DEG),
                        0f, 1f));
                double exhaustSource = exhaustLift * exhaustPressure * 0.48 + pumping;

                if (afterfire > 0.0001) {
                    // A short combustion event injected ahead of the header naturally gets colored
                    // by every pipe reflection instead of sounding like a generic shift pop sample.
                    exhaustSource += afterfire * 0.22;
                    afterfire *= 0.9945;
                } else {
                    afterfire = 0.0;
                }

                // Intake roar is turbulent flow rather than a pitched oscillator. Keep it behind
                // the tailpipe but let it become obvious under a wide-open throttle.
                double intakeFlow = intakeLift * intakeNoise
                        * (0.012 + 0.105 * smoothThrottle)
                        * (0.45 + 0.55 * rpmNorm);

                // Fractional-delay feedback waveguides. These produce propagation/reflection timing
                // from physical pipe lengths rather than fixed musical resonant frequencies.
                double headerReturn = readDelay(headerDelay, headerWrite, headerDelaySamples);
                double headerInput = exhaustSource + headerReturn * headerReflection;
                headerDelay[headerWrite] = headerInput;
                headerWrite = (headerWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                double silencerReturn = readDelay(silencerDelay, silencerWrite,
                        silencerDelaySamples);
                double silencerInput = headerReturn * 0.88
                        + silencerReturn * silencerReflection;
                silencerDelay[silencerWrite] = silencerInput;
                silencerWrite = (silencerWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                double tailPressure = silencerReturn * 0.92 + headerReturn * 0.16;

                // Engine Simulator's useful insight here is that pressure itself is not yet the
                // finished audio. Remove DC, add a restrained pressure-derivative component, then
                // let turbulence modulate the result before band-limiting it.
                tailDc += (tailPressure - tailDc) * 0.0028;
                double acPressure = tailPressure - tailDc;
                double derivative = acPressure - previousTail;
                previousTail = acPressure;

                double turbulentAir = 1.0 + intakeNoise * (0.012 + 0.025 * load);
                double raw = (acPressure * 0.88 + derivative * 0.13) * turbulentAir
                        + intakeFlow;

                tailLowPass += (raw - tailLowPass) * lpAlpha;

                // Shift torque cut should lower exhaust energy instead of muting the whole engine.
                double shiftGain = 1.0 - smoothShift * 0.30;
                double output = tailLowPass * shiftGain * smoothGain;

                // Gentle compression approximates microphone/exhaust saturation while leaving the
                // pressure-wave timing intact. No hard clipping and no harmonic oscillator stack.
                output = Math.tanh(output * 1.15) * 0.84;
                buffer[i] = (short) Math.round(output * 32767.0);
            }

            try {
                device.writeSamples(buffer, 0, buffer.length);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    private static boolean crossedAngle(double previous, double current, double angle) {
        if (current >= previous) return previous < angle && current >= angle;
        return angle > previous || angle <= current;
    }

    private static double wrap720(double degrees) {
        degrees %= 720.0;
        return degrees < 0.0 ? degrees + 720.0 : degrees;
    }

    /** Returns 0..1 progress through a possibly wrap-around crank-angle window. */
    private static double wrapRange(double angle, double open, double close) {
        double total = wrap720(close - open);
        double position = wrap720(angle - open);
        if (position > total) return position < 360.0 ? 0.0 : 1.0;
        return total > 0.0 ? position / total : 0.0;
    }

    private static double wrappedValveLift(double angle, double open, double close) {
        double total = wrap720(close - open);
        double position = wrap720(angle - open);
        if (position > total || total <= 0.0) return 0.0;
        double x = position / total;
        // Smooth, rounded cam-lobe approximation with zero velocity at open and close.
        double s = Math.sin(Math.PI * x);
        return s * s;
    }

    private static double normalizedCylinderVolume(double phaseDeg) {
        // TDCs occur at 0, 360 and 720 degrees; BDCs at 180 and 540 degrees.
        double theta = Math.toRadians(phaseDeg % 360.0);
        double halfStroke = 1.0;
        double rodLength = ROD_RATIO * halfStroke;
        double sin = Math.sin(theta);
        double pistonFromTdc = halfStroke * (1.0 - Math.cos(theta))
                + rodLength - Math.sqrt(Math.max(0.0,
                rodLength * rodLength - halfStroke * halfStroke * sin * sin));
        double displacementFraction = pistonFromTdc / (2.0 * halfStroke);
        double clearance = 1.0 / COMPRESSION_RATIO;
        return clearance + displacementFraction * (1.0 - clearance);
    }

    private static double readDelay(double[] line, int writeIndex, double delaySamples) {
        double read = writeIndex - MathUtils.clamp((float) delaySamples, 2f,
                line.length - 3f);
        while (read < 0.0) read += line.length;
        int i0 = ((int) Math.floor(read)) & (line.length - 1);
        int i1 = (i0 + 1) & (line.length - 1);
        double fraction = read - Math.floor(read);
        return line[i0] * (1.0 - fraction) + line[i1] * fraction;
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
