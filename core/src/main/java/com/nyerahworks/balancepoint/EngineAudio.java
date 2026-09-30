package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Pressure/flow audio model for a 450-class four-stroke single.
 *
 * The engine source is generated from combustion/valve pressure and propagated through a
 * multi-section exhaust model. The final stage presents that source in stereo as an actual
 * rear-exhaust emitter with distance, directivity, interaural delay, ground bounce and sparse
 * outdoor early reflections. All atmosphere is derived from the engine pressure signal itself;
 * no broadband ambience or white-noise bed is mixed into the output.
 */
final class EngineAudio {
    private static final int SAMPLE_RATE = 44_100;
    private static final int BUFFER_FRAMES = 512;
    private static final int DELAY_BUFFER_SIZE = 2048;
    private static final int SPATIAL_BUFFER_SIZE = 8192;

    private static final double COMPRESSION_RATIO = 13.0;
    private static final double ROD_RATIO = 3.45;
    private static final double IGNITION_DEG = 356.0;
    private static final double EXHAUST_OPEN_DEG = 500.0;
    private static final double EXHAUST_CLOSE_DEG = 22.0;
    private static final double INTAKE_OPEN_DEG = 700.0;
    private static final double INTAKE_CLOSE_DEG = 220.0;

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

    private final double[] headerLine = new double[DELAY_BUFFER_SIZE];
    private final double[] midLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerShortLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerLongLine = new double[DELAY_BUFFER_SIZE];
    private final double[] outletLine = new double[DELAY_BUFFER_SIZE];
    private final double[] spatialLine = new double[SPATIAL_BUFFER_SIZE];

    private int headerWrite;
    private int midWrite;
    private int mufflerShortWrite;
    private int mufflerLongWrite;
    private int outletWrite;
    private int spatialWrite;

    private volatile boolean running;
    private volatile boolean active = true;
    private volatile float targetRpm = 1_800f;
    private volatile float targetThrottle;
    private volatile boolean targetShifting;
    private volatile float targetPan;
    private volatile float targetDistance = 3.0f;
    private volatile float targetRearRadiation = 1.0f;

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

    private double intakeNoiseLow;
    private double intakeNoiseMid;

    // Final tonal shaping. These cascaded low-pass states create broad low/body/presence/air bands
    // without introducing narrow EQ resonances that could bring the insect-like tone back.
    private double eqLowLp;
    private double eqBodyLp;
    private double eqPresenceLp;

    // Outdoor atmosphere states. Each reflection is deliberately low-passed and sparse so the
    // result reads as air/ground/environment around the bike rather than indoor reverb.
    private double groundLp;
    private double earlyLeftLp;
    private double earlyRightLp;
    private double lateLeftLp;
    private double lateRightLp;

    private double afterfire;
    private int noiseState = 0x13579BDF;

    EngineAudio() {
        AudioDevice created = null;
        try {
            // Stereo is required for source placement and the diffuse atmosphere field.
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, false);
            created.setVolume(0.76f);
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
        update(rpm, throttle, shifting, crashed, 0f, 3f, 1f);
    }

    /**
     * @param pan camera-relative source pan, -1 left to +1 right
     * @param distanceMeters listener-to-exhaust distance
     * @param rearRadiation 0 when listener is in front of the outlet, 1 directly behind it
     */
    void update(float rpm, float throttle, boolean shifting, boolean crashed,
                float pan, float distanceMeters, float rearRadiation) {
        targetRpm = MathUtils.clamp(rpm, 1_200f, 14_000f);
        targetThrottle = crashed ? 0f : MathUtils.clamp(throttle, 0f, 1f);
        targetShifting = shifting;
        targetPan = MathUtils.clamp(pan, -1f, 1f);
        targetDistance = MathUtils.clamp(distanceMeters, 0.35f, 20f);
        targetRearRadiation = MathUtils.clamp(rearRadiation, 0f, 1f);
    }

    void setActive(boolean active) {
        this.active = active;
    }

    private void audioLoop() {
        // libGDX stereo AudioDevice expects interleaved L/R samples.
        short[] buffer = new short[BUFFER_FRAMES * 2];
        float smoothRpm = targetRpm;
        float smoothThrottle = 0f;
        float smoothGain = 0f;
        float smoothShift = 0f;
        float smoothPan = 0f;
        float smoothDistance = targetDistance;
        float smoothRearRadiation = targetRearRadiation;
        boolean previousShiftTarget = false;

        final double intakeLowAlpha = onePoleAlpha(420.0);
        final double intakeMidAlpha = onePoleAlpha(1_650.0);
        final double eqLowAlpha = onePoleAlpha(190.0);
        final double eqBodyAlpha = onePoleAlpha(820.0);
        final double eqPresenceAlpha = onePoleAlpha(2_350.0);
        final double groundAlpha = onePoleAlpha(1_450.0);
        final double atmosphereAlpha = onePoleAlpha(2_200.0);

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.20f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.24f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.34f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.24f;
            smoothPan += (targetPan - smoothPan) * 0.16f;
            smoothDistance += (targetDistance - smoothDistance) * 0.12f;
            smoothRearRadiation += (targetRearRadiation - smoothRearRadiation) * 0.12f;

            boolean shiftTarget = targetShifting;
            if (shiftTarget && !previousShiftTarget && smoothThrottle > 0.42f) {
                afterfire = Math.max(afterfire, 0.58 + smoothThrottle * 0.42);
            }
            previousShiftTarget = shiftTarget;

            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 11_400f, 0f, 1f);
            float load = 0.23f + smoothThrottle * 0.77f;
            double degreesPerSample = smoothRpm * 6.0 / SAMPLE_RATE;

            double soundSpeed = 438.0 + smoothThrottle * 60.0 + rpmNorm * 24.0;
            double headerSamples = HEADER_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double midSamples = MIDPIPE_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double mufflerShortSamples = MUFFLER_SHORT_PATH_M / soundSpeed * SAMPLE_RATE;
            double mufflerLongSamples = MUFFLER_LONG_PATH_M / soundSpeed * SAMPLE_RATE;
            double outletSamples = OUTLET_LENGTH_M / soundSpeed * SAMPLE_RATE;

            double headerToMidR = areaReflection(HEADER_AREA, MIDPIPE_AREA);
            double midToBodyR = areaReflection(MIDPIPE_AREA, MUFFLER_BODY_AREA);
            double bodyToOutletR = areaReflection(MUFFLER_BODY_AREA, OUTLET_AREA);
            double headerToMidT = 1.0 + headerToMidR;
            double midToBodyT = 1.0 + midToBodyR;
            double bodyToOutletT = 1.0 + bodyToOutletR;

            double bodyAlpha = onePoleAlpha(650.0 + 180.0 * smoothThrottle);
            double edgeAlpha = onePoleAlpha(1_850.0 + 520.0 * smoothThrottle);
            double outletBodyAlpha = onePoleAlpha(900.0 + 200.0 * smoothThrottle);
            double outletEdgeAlpha = onePoleAlpha(2_450.0 + 420.0 * smoothThrottle);

            double absPan = Math.abs(smoothPan);
            double interauralSamples = 2.0 + absPan * SAMPLE_RATE * 0.00043;
            double distanceGain = 1.0 / (1.0 + 0.024 * smoothDistance * smoothDistance);
            double atmosphereAmount = 0.055 + 0.055 * MathUtils.clamp(smoothDistance / 7f, 0f, 1f);
            double airLoss = 1.0 / (1.0 + smoothDistance * 0.055);

            // Sparse outdoor reflection timings. Distance changes them slightly so the atmosphere
            // does not sit as a fixed chorus glued to the engine signal.
            double groundDelay = SAMPLE_RATE * (0.0025 + 0.00018 * smoothDistance);
            double earlyLeftDelay = SAMPLE_RATE * (0.0115 + 0.00022 * smoothDistance);
            double earlyRightDelay = SAMPLE_RATE * (0.0148 + 0.00017 * smoothDistance);
            double lateLeftDelay = SAMPLE_RATE * (0.0270 + 0.00030 * smoothDistance);
            double lateRightDelay = SAMPLE_RATE * (0.0335 + 0.00024 * smoothDistance);

            for (int frame = 0; frame < BUFFER_FRAMES; frame++) {
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
                double exhaustSource = exhaustLift * exhaustPressure * 0.50 + pumping;

                if (afterfire > 0.0001) {
                    exhaustSource += afterfire * 0.18;
                    afterfire *= 0.9950;
                } else {
                    afterfire = 0.0;
                }

                double intakeFlow = intakeLift * intakeTexture
                        * (0.003 + 0.030 * smoothThrottle)
                        * (0.35 + 0.65 * rpmNorm);

                double headerFeedback = headerReflectionLp * headerToMidR * 0.34;
                headerLine[headerWrite] = exhaustSource + headerFeedback;
                double headerAtMid = readDelay(headerLine, headerWrite, headerSamples);
                headerWrite = (headerWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                headerReflectionLp += (headerAtMid - headerReflectionLp) * 0.14;
                double intoMid = headerAtMid * headerToMidT;

                double midFeedback = midReflectionLp * midToBodyR * 0.23;
                midLine[midWrite] = intoMid + midFeedback;
                double midAtBody = readDelay(midLine, midWrite, midSamples);
                midWrite = (midWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                midReflectionLp += (midAtBody - midReflectionLp) * 0.10;
                double intoBody = midAtBody * midToBodyT;

                mufflerPressure += (intoBody - mufflerPressure) * 0.030;
                mufflerPressure *= 0.9991;
                mufflerBodyLp += (intoBody - mufflerBodyLp) * bodyAlpha;
                mufflerEdgeLp += (intoBody - mufflerEdgeLp) * edgeAlpha;
                double bodyBand = mufflerBodyLp;
                double edgeBand = mufflerEdgeLp - mufflerBodyLp;

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

                double canOut = (shortOut * 0.74 - longOut * 0.31 + mufflerPressure * 0.22)
                        * bodyToOutletT;

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

                double monoRaw = outletBody * 1.16
                        + outletEdge * (0.13 + 0.045 * smoothThrottle)
                        + pressureVelocity * 0.010
                        + mufflerPressure * 0.055
                        + intakeFlow;

                double shiftGain = 1.0 - smoothShift * 0.28;
                monoRaw *= shiftGain * smoothGain;

                // Broad 4-band EQ: emphasize 200-800 Hz body, keep useful 800-2350 Hz definition,
                // and strongly reduce the upper edge that makes compact procedural engines sound
                // like a fly. Rear-facing directivity mainly affects definition/air, not bass.
                eqLowLp += (monoRaw - eqLowLp) * eqLowAlpha;
                eqBodyLp += (monoRaw - eqBodyLp) * eqBodyAlpha;
                eqPresenceLp += (monoRaw - eqPresenceLp) * eqPresenceAlpha;
                double low = eqLowLp;
                double body = eqBodyLp - eqLowLp;
                double presence = eqPresenceLp - eqBodyLp;
                double air = monoRaw - eqPresenceLp;

                double directivity = 0.30 + 0.70 * smoothRearRadiation;
                double direct = low * 1.08
                        + body * 1.20
                        + presence * (0.52 + 0.24 * directivity) * airLoss
                        + air * (0.10 + 0.12 * directivity) * airLoss;

                double atmosphereSource = low * 0.92 + body * 0.96
                        + presence * 0.46 + air * 0.04;
                spatialLine[spatialWrite] = atmosphereSource;

                // ITD/ILD source placement. The farther ear gets a sub-millisecond delay and level
                // loss. This follows camera-relative pan supplied by the game each frame.
                double farEar = readDelay(spatialLine, spatialWrite, interauralSamples);
                double leftDirect = direct;
                double rightDirect = direct;
                if (smoothPan > 0f) {
                    leftDirect = direct * (1.0 - absPan) + farEar * absPan;
                } else if (smoothPan < 0f) {
                    rightDirect = direct * (1.0 - absPan) + farEar * absPan;
                }

                double panAmount = smoothPan * 0.58;
                double leftPanGain = Math.sqrt(0.5 * (1.0 - panAmount));
                double rightPanGain = Math.sqrt(0.5 * (1.0 + panAmount));
                leftDirect *= leftPanGain * distanceGain;
                rightDirect *= rightPanGain * distanceGain;

                // Hard-ground bounce gives the exhaust a physical relationship to the road. Sparse,
                // differently timed lateral reflections provide open-air atmosphere without a room
                // reverb tail. Every path is low-passed because distant reflections lose edge first.
                double ground = readDelay(spatialLine, spatialWrite, groundDelay);
                groundLp += (ground - groundLp) * groundAlpha;

                double earlyL = readDelay(spatialLine, spatialWrite, earlyLeftDelay);
                double earlyR = readDelay(spatialLine, spatialWrite, earlyRightDelay);
                double lateL = readDelay(spatialLine, spatialWrite, lateLeftDelay);
                double lateR = readDelay(spatialLine, spatialWrite, lateRightDelay);
                earlyLeftLp += (earlyL - earlyLeftLp) * atmosphereAlpha;
                earlyRightLp += (earlyR - earlyRightLp) * atmosphereAlpha;
                lateLeftLp += (lateL - lateLeftLp) * atmosphereAlpha;
                lateRightLp += (lateR - lateRightLp) * atmosphereAlpha;

                double leftAtmosphere = groundLp * 0.115
                        + earlyLeftLp * 0.075 + earlyRightLp * 0.026
                        + lateLeftLp * 0.037 - lateRightLp * 0.014;
                double rightAtmosphere = groundLp * 0.115
                        + earlyRightLp * 0.075 + earlyLeftLp * 0.026
                        + lateRightLp * 0.037 - lateLeftLp * 0.014;

                spatialWrite = (spatialWrite + 1) & (SPATIAL_BUFFER_SIZE - 1);

                double left = leftDirect + leftAtmosphere * atmosphereAmount;
                double right = rightDirect + rightAtmosphere * atmosphereAmount;

                // Gentle output compression; preserve interaural differences by processing channels
                // independently but with identical transfer functions.
                left = Math.tanh(left * (left >= 0.0 ? 1.02 : 0.92)) * 0.90;
                right = Math.tanh(right * (right >= 0.0 ? 1.02 : 0.92)) * 0.90;

                int sampleIndex = frame * 2;
                buffer[sampleIndex] = toPcm16(left);
                buffer[sampleIndex + 1] = toPcm16(right);
            }

            try {
                device.writeSamples(buffer, 0, buffer.length);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    private static short toPcm16(double sample) {
        sample = Math.max(-1.0, Math.min(1.0, sample));
        return (short) Math.round(sample * 32767.0);
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
