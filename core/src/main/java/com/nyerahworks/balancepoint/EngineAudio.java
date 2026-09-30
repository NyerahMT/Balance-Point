package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Lightweight pressure/flow audio model for a 450-class four-stroke single.
 *
 * The source is cylinder/exhaust pressure, not an RPM-tracked oscillator. A 720-degree crank cycle
 * drives combustion and valve flow, then the pressure wave travels through a multi-section exhaust
 * network: header, expanding midpipe, packed muffler body and outlet. Cross-sectional area changes
 * create scattering/reflections and the muffler splits energy across multiple lossy paths, avoiding
 * the narrow "straight tube" character of a single feedback delay line.
 */
final class EngineAudio {
    private static final int SAMPLE_RATE = 44_100;
    private static final int BUFFER_SAMPLES = 512;
    private static final int DELAY_BUFFER_SIZE = 2048;

    private static final double COMPRESSION_RATIO = 13.0;
    private static final double ROD_RATIO = 3.45;
    private static final double IGNITION_DEG = 356.0;
    private static final double EXHAUST_OPEN_DEG = 500.0;
    private static final double EXHAUST_CLOSE_DEG = 22.0;
    private static final double INTAKE_OPEN_DEG = 700.0;
    private static final double INTAKE_CLOSE_DEG = 220.0;

    // Representative dimensions for a modern 450 MX exhaust. Diameter matters here because area
    // changes control acoustic impedance and therefore reflection/transmission strength.
    private static final double HEADER_LENGTH_M = 0.58;
    private static final double MIDPIPE_LENGTH_M = 0.28;
    private static final double MUFFLER_SHORT_PATH_M = 0.24;
    private static final double MUFFLER_LONG_PATH_M = 0.46;
    private static final double OUTLET_LENGTH_M = 0.17;

    private static final double HEADER_DIAMETER_M = 0.044;
    private static final double MIDPIPE_DIAMETER_M = 0.052;
    private static final double MUFFLER_BODY_DIAMETER_M = 0.112;
    private static final double OUTLET_DIAMETER_M = 0.040;

    private static final double HEADER_AREA = area(HEADER_DIAMETER_M);
    private static final double MIDPIPE_AREA = area(MIDPIPE_DIAMETER_M);
    private static final double MUFFLER_BODY_AREA = area(MUFFLER_BODY_DIAMETER_M);
    private static final double OUTLET_AREA = area(OUTLET_DIAMETER_M);

    private final AudioDevice device;
    private final Thread audioThread;

    // Directional-ish delay paths. Using several paths with different lengths/losses spreads the
    // exhaust energy across a wide acoustic body instead of one high-Q comb resonance.
    private final double[] headerLine = new double[DELAY_BUFFER_SIZE];
    private final double[] midLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerShortLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerLongLine = new double[DELAY_BUFFER_SIZE];
    private final double[] outletLine = new double[DELAY_BUFFER_SIZE];

    private int headerWrite;
    private int midWrite;
    private int mufflerShortWrite;
    private int mufflerLongWrite;
    private int outletWrite;

    private volatile boolean running;
    private volatile boolean active = true;
    private volatile float targetRpm = 1_800f;
    private volatile float targetThrottle;
    private volatile boolean targetShifting;

    private double cycleDeg;
    private double cycleStrength = 0.30;
    private int combustionCount;

    private double headerReflectionLp;
    private double midReflectionLp;
    private double mufflerPressure;
    private double mufflerBodyLp;
    private double mufflerEdgeLp;
    private double outletDc;
    private double outletBodyLp;
    private double outletEdgeLp;
    private double previousOutlet;

    // Two low-pass states form a band-limited intake texture. The previous pass subtracted a single
    // low-pass from raw white noise and also mixed raw turbulence directly into the output, which
    // produced an audible broadband hiss. Nothing here feeds unfiltered white noise to the speaker.
    private double intakeNoiseLow;
    private double intakeNoiseMid;

    private double afterfire;
    private int noiseState = 0x13579BDF;

    EngineAudio() {
        AudioDevice created = null;
        try {
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
            created.setVolume(0.74f);
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

        // Fixed noise filters. Their difference creates a broad intake band concentrated in the
        // hundreds-to-low-thousands of Hz instead of the previous full-band white hiss.
        final double intakeLowAlpha = onePoleAlpha(420.0);
        final double intakeMidAlpha = onePoleAlpha(1_650.0);

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.20f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.24f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.34f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.24f;

            boolean shiftTarget = targetShifting;
            if (shiftTarget && !previousShiftTarget && smoothThrottle > 0.42f) {
                afterfire = Math.max(afterfire, 0.58 + smoothThrottle * 0.42);
            }
            previousShiftTarget = shiftTarget;

            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 11_400f, 0f, 1f);
            float load = 0.23f + smoothThrottle * 0.77f;
            double degreesPerSample = smoothRpm * 6.0 / SAMPLE_RATE;

            // Hot exhaust raises wave speed. Keep the range modest so the pipe character moves
            // naturally with load without turning into a pitch effect.
            double soundSpeed = 438.0 + smoothThrottle * 60.0 + rpmNorm * 24.0;
            double headerSamples = HEADER_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double midSamples = MIDPIPE_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double mufflerShortSamples = MUFFLER_SHORT_PATH_M / soundSpeed * SAMPLE_RATE;
            double mufflerLongSamples = MUFFLER_LONG_PATH_M / soundSpeed * SAMPLE_RATE;
            double outletSamples = OUTLET_LENGTH_M / soundSpeed * SAMPLE_RATE;

            // Area-junction reflection coefficients. Expansions reflect pressure with opposite sign;
            // contractions reflect with the same sign. Values come from acoustic impedance Z~1/A.
            double headerToMidR = areaReflection(HEADER_AREA, MIDPIPE_AREA);
            double midToBodyR = areaReflection(MIDPIPE_AREA, MUFFLER_BODY_AREA);
            double bodyToOutletR = areaReflection(MUFFLER_BODY_AREA, OUTLET_AREA);

            double headerToMidT = 1.0 + headerToMidR;
            double midToBodyT = 1.0 + midToBodyR;
            double bodyToOutletT = 1.0 + bodyToOutletR;

            // Deliberately keep the packed-can edge bandwidth below the old pass. The body remains
            // strong, but the high-order pulse train no longer dominates as an insect-like buzz.
            double bodyAlpha = onePoleAlpha(650.0 + 180.0 * smoothThrottle);
            double edgeAlpha = onePoleAlpha(1_850.0 + 520.0 * smoothThrottle);
            double outletBodyAlpha = onePoleAlpha(900.0 + 200.0 * smoothThrottle);
            double outletEdgeAlpha = onePoleAlpha(2_450.0 + 420.0 * smoothThrottle);

            for (int i = 0; i < buffer.length; i++) {
                double previousDeg = cycleDeg;
                cycleDeg += degreesPerSample;
                if (cycleDeg >= 720.0) cycleDeg -= 720.0;

                noiseState = noiseState * 1664525 + 1013904223;
                double white = (((noiseState >>> 8) & 0xFFFF) / 32767.5) - 1.0;
                intakeNoiseLow += (white - intakeNoiseLow) * intakeLowAlpha;
                intakeNoiseMid += (white - intakeNoiseMid) * intakeMidAlpha;
                double intakeTexture = intakeNoiseMid - intakeNoiseLow;

                if (crossedAngle(previousDeg, cycleDeg, IGNITION_DEG)) {
                    combustionCount++;
                    boolean limiterCut = smoothRpm > 13_050f && smoothThrottle > 0.70f
                            && (combustionCount & 3) == 3;
                    if (limiterCut) {
                        cycleStrength = 0.035;
                    } else {
                        // Use slow colored noise only for tiny cycle-to-cycle combustion variation.
                        // Raw white noise is intentionally not used here or in the final mix.
                        double variation = 1.0 + 0.028 * intakeNoiseLow;
                        cycleStrength = (0.30 + 1.62 * load) * variation;
                    }
                }

                double exhaustLift = wrappedValveLift(cycleDeg,
                        EXHAUST_OPEN_DEG, EXHAUST_CLOSE_DEG);
                double intakeLift = wrappedValveLift(cycleDeg,
                        INTAKE_OPEN_DEG, INTAKE_CLOSE_DEG);

                double volume = normalizedCylinderVolume(cycleDeg);
                double clearance = 1.0 / COMPRESSION_RATIO;

                double sinceIgnition = wrap720(cycleDeg - IGNITION_DEG);
                double combustionPressure = 0.0;
                if (sinceIgnition < 315.0) {
                    double burnRise = 1.0 - Math.exp(-sinceIgnition / 10.5);
                    double heatDecay = Math.exp(-sinceIgnition / 240.0);
                    double expansion = Math.pow(clearance / Math.max(clearance, volume), 0.70);
                    combustionPressure = cycleStrength * burnRise * heatDecay * expansion * 9.0;
                }

                double compression = 0.0;
                if (cycleDeg >= 180.0 && cycleDeg < IGNITION_DEG) {
                    double trappedCharge = 0.16 + smoothThrottle * 0.84;
                    compression = trappedCharge
                            * (Math.pow(1.0 / Math.max(volume, clearance), 1.17) - 1.0)
                            * 0.050;
                }

                double exhaustPressure = combustionPressure + compression;
                double exhaustWindow = wrapRange(cycleDeg,
                        EXHAUST_OPEN_DEG, EXHAUST_CLOSE_DEG);
                double pumping = exhaustLift * (0.030 + 0.105 * load)
                        * Math.sin(Math.PI * exhaustWindow);

                // Pressure/flow at the valve is the acoustic source. A slightly wider blowdown
                // term gives the large-bore single a dense thump rather than a sharp click.
                double exhaustSource = exhaustLift * exhaustPressure * 0.50 + pumping;

                if (afterfire > 0.0001) {
                    exhaustSource += afterfire * 0.18;
                    afterfire *= 0.9950;
                } else {
                    afterfire = 0.0;
                }

                // Intake sound now exists only while the intake valve is flowing and uses a colored
                // mid-band texture. This removes the constant broadband air hiss from the last pass.
                double intakeFlow = intakeLift * intakeTexture
                        * (0.003 + 0.030 * smoothThrottle)
                        * (0.35 + 0.65 * rpmNorm);

                // HEADER: mostly clean pressure propagation with a lightly damped return from the
                // area increase into the midpipe. Reflection is filtered to mimic wall/thermal loss.
                double headerFeedback = headerReflectionLp * headerToMidR * 0.34;
                headerLine[headerWrite] = exhaustSource + headerFeedback;
                double headerAtMid = readDelay(headerLine, headerWrite, headerSamples);
                headerWrite = (headerWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                headerReflectionLp += (headerAtMid - headerReflectionLp) * 0.14;
                double intoMid = headerAtMid * headerToMidT;

                // MIDPIPE: second area expansion before the can. Lower-Q feedback deliberately
                // avoids the single resonant tube effect while preserving authentic pulse timing.
                double midFeedback = midReflectionLp * midToBodyR * 0.23;
                midLine[midWrite] = intoMid + midFeedback;
                double midAtBody = readDelay(midLine, midWrite, midSamples);
                midWrite = (midWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                midReflectionLp += (midAtBody - midReflectionLp) * 0.10;
                double intoBody = midAtBody * midToBodyT;

                // MUFFLER VOLUME: pressure enters a much larger cross-sectional area. The reservoir
                // stores low-frequency energy while packing damps the fast pressure edge.
                mufflerPressure += (intoBody - mufflerPressure) * 0.030;
                mufflerPressure *= 0.9991;

                mufflerBodyLp += (intoBody - mufflerBodyLp) * bodyAlpha;
                mufflerEdgeLp += (intoBody - mufflerEdgeLp) * edgeAlpha;
                double bodyBand = mufflerBodyLp;
                double edgeBand = mufflerEdgeLp - mufflerBodyLp;

                // Split the can into two lossy acoustic paths. Reduce edge energy heavily; most of
                // the audible character should come from pressure body, not repeated sharp pulses.
                double shortFeed = bodyBand * 0.74 + edgeBand * 0.11 + mufflerPressure * 0.28;
                double longFeed = bodyBand * 0.52 + edgeBand * 0.035 + mufflerPressure * 0.46;

                mufflerShortLine[mufflerShortWrite] = shortFeed;
                double shortOut = readDelay(mufflerShortLine, mufflerShortWrite,
                        mufflerShortSamples);
                mufflerShortWrite = (mufflerShortWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                mufflerLongLine[mufflerLongWrite] = longFeed;
                double longOut = readDelay(mufflerLongLine, mufflerLongWrite,
                        mufflerLongSamples);
                mufflerLongWrite = (mufflerLongWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                // Recombine the paths with slight opposite polarity. That represents expansion-
                // chamber scattering and prevents both paths from summing into one rigid comb tone.
                double canOut = shortOut * 0.74 - longOut * 0.31 + mufflerPressure * 0.22;
                canOut *= bodyToOutletT;

                // OUTLET: short tailpipe with very low feedback. Keep only enough reflection for a
                // physical outlet boundary; excessive feedback recreates the narrow fly-like tone.
                outletLine[outletWrite] = canOut + previousOutlet * bodyToOutletR * 0.035;
                double tailPressure = readDelay(outletLine, outletWrite, outletSamples);
                outletWrite = (outletWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                outletDc += (tailPressure - outletDc) * 0.0025;
                double acPressure = tailPressure - outletDc;
                double pressureVelocity = acPressure - previousOutlet;
                previousOutlet = acPressure;

                outletBodyLp += (acPressure - outletBodyLp) * outletBodyAlpha;
                outletEdgeLp += (acPressure - outletEdgeLp) * outletEdgeAlpha;
                double outletBody = outletBodyLp;
                double outletEdge = outletEdgeLp - outletBodyLp;

                // No additive white-noise/turbulence path here. The only stochastic audio component
                // is the intake-flow texture above, which is band-limited and valve-gated.
                double raw = outletBody * 1.16
                        + outletEdge * (0.13 + 0.045 * smoothThrottle)
                        + pressureVelocity * 0.010
                        + mufflerPressure * 0.055
                        + intakeFlow;

                double shiftGain = 1.0 - smoothShift * 0.28;
                double output = raw * shiftGain * smoothGain;

                // Gentle asymmetrical compression gives the exhaust pulse density without turning
                // the outlet back into a buzzy harmonic generator.
                double positiveDrive = output >= 0.0 ? 1.02 : 0.92;
                output = Math.tanh(output * positiveDrive) * 0.88;
                buffer[i] = (short) Math.round(output * 32767.0);
            }

            try {
                device.writeSamples(buffer, 0, buffer.length);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    private static double area(double diameter) {
        double radius = diameter * 0.5;
        return Math.PI * radius * radius;
    }

    private static double areaReflection(double fromArea, double toArea) {
        return (fromArea - toArea) / (fromArea + toArea);
    }

    private static double onePoleAlpha(double cutoffHz) {
        return 1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / SAMPLE_RATE);
    }

    private static boolean crossedAngle(double previous, double current, double angle) {
        if (current >= previous) return previous < angle && current >= angle;
        return angle > previous || angle <= current;
    }

    private static double wrap720(double degrees) {
        degrees %= 720.0;
        return degrees < 0.0 ? degrees + 720.0 : degrees;
    }

    private static double wrapRange(double angle, double open, double close) {
        double total = wrap720(close - open);
        double position = wrap720(angle - open);
        if (position > total) return 0.0;
        return total > 0.0 ? position / total : 0.0;
    }

    private static double wrappedValveLift(double angle, double open, double close) {
        double total = wrap720(close - open);
        double position = wrap720(angle - open);
        if (position > total || total <= 0.0) return 0.0;
        double x = position / total;
        double s = Math.sin(Math.PI * x);
        return s * s;
    }

    private static double normalizedCylinderVolume(double phaseDeg) {
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
        double clamped = Math.max(2.0, Math.min(line.length - 3.0, delaySamples));
        double read = writeIndex - clamped;
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
