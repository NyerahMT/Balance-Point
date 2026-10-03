package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

/**
 * Pressure/flow audio model for a 450-class four-stroke single.
 *
 * The radiated note is the blowdown crack at exhaust opening, not a sine held across the
 * valve window. That pulse travels a header quarter-wave into a packed absorption can, then
 * leaves the outlet as a differentiated pressure wave. An airbox Helmholtz path sits beside
 * it and is louder up close. Overrun pops and a crank-locked mechanical tick come from the
 * same cycle. The outdoor stage is still derived only from those engine signals: ground
 * bounce, sparse berm reflections, distance air loss, doppler and a short crossfeed so a
 * chase camera is not a mono point.
 */
final class EngineAudio {
    private static final int SAMPLE_RATE = 44_100;
    private static final int BUFFER_FRAMES = 512;
    private static final int DELAY_BUFFER_SIZE = 2048;
    private static final int SPATIAL_BUFFER_SIZE = 8192;

    private static final double COMPRESSION_RATIO = 13.0;
    private static final double ROD_RATIO = 3.45;
    // 75° BBDC. A 450 motocross cam dumps the cylinder well before bottom dead center.
    private static final double EXHAUST_OPEN_DEG = 465.0;
    private static final double EXHAUST_CLOSE_DEG = 32.0;
    private static final double INTAKE_OPEN_DEG = 700.0;
    private static final double INTAKE_CLOSE_DEG = 240.0;
    private static final double BLOWDOWN_RISE_DEG = 5.2;
    private static final double BLOWDOWN_DECAY_DEG = 34.0;

    private static final double HEADER_LENGTH_M = 0.58;
    private static final double MIDPIPE_LENGTH_M = 0.28;
    private static final double MUFFLER_CORE_M = 0.31;
    private static final double MUFFLER_PACK_M = 0.47;
    private static final double OUTLET_LENGTH_M = 0.16;

    private static final double HEADER_DIAMETER_M = 0.044;
    private static final double MIDPIPE_DIAMETER_M = 0.052;
    private static final double MUFFLER_BODY_DIAMETER_M = 0.112;
    private static final double OUTLET_DIAMETER_M = 0.034;

    private static final double HEADER_AREA = area(HEADER_DIAMETER_M);
    private static final double MIDPIPE_AREA = area(MIDPIPE_DIAMETER_M);
    private static final double MUFFLER_BODY_AREA = area(MUFFLER_BODY_DIAMETER_M);
    private static final double OUTLET_AREA = area(OUTLET_DIAMETER_M);

    // ~10 L airbox, short bell. Resonates near 150 Hz, which is the close-throttle honk.
    private static final double INTAKE_HZ = 152.0;

    private final AudioDevice device;
    private final Thread audioThread;

    private final double[] headerLine = new double[DELAY_BUFFER_SIZE];
    private final double[] midLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerCoreLine = new double[DELAY_BUFFER_SIZE];
    private final double[] mufflerPackLine = new double[DELAY_BUFFER_SIZE];
    private final double[] outletLine = new double[DELAY_BUFFER_SIZE];
    private final double[] spatialLine = new double[SPATIAL_BUFFER_SIZE];

    private int headerWrite;
    private int midWrite;
    private int mufflerCoreWrite;
    private int mufflerPackWrite;
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
    private volatile float targetClosingSpeed;

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
    private double radiationLp;

    private double blowdownPressure;
    private double mechanical;
    private double overrunEnergy;
    private double afterfire;

    private double intakePos;
    private double intakeVel;

    private double eqLowLp;
    private double eqBodyLp;
    private double eqPresenceLp;

    private double groundLp;
    private double earlyLeftLp;
    private double earlyRightLp;
    private double midLeftLp;
    private double midRightLp;
    private double lateLeftLp;
    private double lateRightLp;

    private int noiseState = 0x13579BDF;

    EngineAudio() {
        AudioDevice created = null;
        try {
            created = Gdx.audio.newAudioDevice(SAMPLE_RATE, false);
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
        update(rpm, throttle, shifting, crashed, 0f, 3f, 1f, 0f);
    }

    /**
     * @param pan camera-relative source pan, -1 left to +1 right
     * @param distanceMeters listener-to-exhaust distance
     * @param rearRadiation 0 when listener is in front of the outlet, 1 directly behind it
     * @param closingSpeedMeters source speed toward the listener, for doppler
     */
    void update(float rpm, float throttle, boolean shifting, boolean crashed,
                float pan, float distanceMeters, float rearRadiation,
                float closingSpeedMeters) {
        targetRpm = MathUtils.clamp(rpm, 1_200f, 14_000f);
        targetThrottle = crashed ? 0f : MathUtils.clamp(throttle, 0f, 1f);
        targetShifting = shifting;
        targetPan = MathUtils.clamp(pan, -1f, 1f);
        targetDistance = MathUtils.clamp(distanceMeters, 0.35f, 24f);
        targetRearRadiation = MathUtils.clamp(rearRadiation, 0f, 1f);
        targetClosingSpeed = MathUtils.clamp(closingSpeedMeters, -48f, 48f);
    }

    void setActive(boolean active) {
        this.active = active;
    }

    private void audioLoop() {
        short[] buffer = new short[BUFFER_FRAMES * 2];
        float smoothRpm = targetRpm;
        float smoothThrottle = 0f;
        float fastThrottle = 0f;
        float smoothGain = 0f;
        float smoothShift = 0f;
        float smoothPan = 0f;
        float smoothDistance = targetDistance;
        float smoothRearRadiation = targetRearRadiation;
        float smoothClosing = 0f;
        boolean previousShiftTarget = false;
        float previousFastThrottle = 0f;

        final double eqLowAlpha = onePoleAlpha(180.0);
        final double eqBodyAlpha = onePoleAlpha(780.0);
        final double eqPresenceAlpha = onePoleAlpha(3_400.0);
        final double groundAlpha = onePoleAlpha(880.0);
        final double earlyAlpha = onePoleAlpha(1_700.0);
        final double midAlpha = onePoleAlpha(1_050.0);
        final double lateAlpha = onePoleAlpha(620.0);
        final double radiationAlpha = onePoleAlpha(4_200.0);
        final double intakeOmega = 2.0 * Math.PI * INTAKE_HZ;
        final double intakeDamping = 2.0 * 0.075 * intakeOmega;

        while (running) {
            smoothRpm += (targetRpm - smoothRpm) * 0.20f;
            smoothThrottle += (targetThrottle - smoothThrottle) * 0.24f;
            fastThrottle += (targetThrottle - fastThrottle) * 0.62f;
            smoothShift += ((targetShifting ? 1f : 0f) - smoothShift) * 0.34f;
            smoothGain += ((active ? 1f : 0f) - smoothGain) * 0.24f;
            smoothPan += (targetPan - smoothPan) * 0.18f;
            smoothDistance += (targetDistance - smoothDistance) * 0.14f;
            smoothRearRadiation += (targetRearRadiation - smoothRearRadiation) * 0.14f;
            smoothClosing += (targetClosingSpeed - smoothClosing) * 0.18f;

            boolean shiftTarget = targetShifting;
            if (shiftTarget && !previousShiftTarget && smoothThrottle > 0.42f) {
                afterfire = Math.max(afterfire, 0.62 + smoothThrottle * 0.38);
            }
            float throttleDrop = previousFastThrottle - fastThrottle;
            if (throttleDrop > 0.045f) {
                overrunEnergy = Math.max(overrunEnergy, 0.35 + throttleDrop * 3.4);
            }
            previousFastThrottle = fastThrottle;
            previousShiftTarget = shiftTarget;

            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 10_100f, 0f, 1f);
            float load = 0.18f + fastThrottle * 0.82f;
            // Approaching source raises firing rate. Chase motion is near zero; orbit and
            // jumps are where this is audible.
            double doppler = MathUtils.clamp(1.0 + smoothClosing / 343.0, 0.88, 1.14);
            double degreesPerSample = smoothRpm * 6.0 / SAMPLE_RATE * doppler;
            // More advance down low, less at the limiter. Pressure at EVO follows it.
            double ignitionDeg = 360.0 - (27.0 - 15.0 * rpmNorm);

            double soundSpeed = 470.0 + smoothThrottle * 70.0 + rpmNorm * 28.0;
            double headerSamples = HEADER_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double midSamples = MIDPIPE_LENGTH_M / soundSpeed * SAMPLE_RATE;
            double mufflerCoreSamples = MUFFLER_CORE_M / soundSpeed * SAMPLE_RATE;
            double mufflerPackSamples = MUFFLER_PACK_M / soundSpeed * SAMPLE_RATE;
            double outletSamples = OUTLET_LENGTH_M / soundSpeed * SAMPLE_RATE;

            double headerToMidR = areaReflection(HEADER_AREA, MIDPIPE_AREA);
            double midToBodyR = areaReflection(MIDPIPE_AREA, MUFFLER_BODY_AREA);
            double bodyToOutletR = areaReflection(MUFFLER_BODY_AREA, OUTLET_AREA);
            double headerToMidT = 1.0 + headerToMidR;
            double midToBodyT = 1.0 + midToBodyR;
            double bodyToOutletT = 1.0 + bodyToOutletR;

            // Packing, not a reflective chamber. Edge dies as load and gas temperature rise.
            double bodyAlpha = onePoleAlpha(520.0 + 140.0 * smoothThrottle);
            double edgeAlpha = onePoleAlpha(1_450.0 + 380.0 * smoothThrottle);
            double outletBodyAlpha = onePoleAlpha(860.0 + 180.0 * smoothThrottle);
            double outletEdgeAlpha = onePoleAlpha(2_800.0 + 500.0 * smoothThrottle);

            double absPan = Math.abs(smoothPan);
            double interauralSamples = 2.0 + absPan * SAMPLE_RATE * 0.00046;
            double distanceBlend = MathUtils.clamp(smoothDistance / 8f, 0f, 1f);
            double distanceGain = 1.0 / (1.0 + 0.055 * smoothDistance
                    + 0.010 * smoothDistance * smoothDistance);
            double proximity = 1.0 / (1.0 + smoothDistance * 0.42);
            double atmosphereAmount = 0.10 + 0.30 * distanceBlend;
            double airLoss = 1.0 / (1.0 + smoothDistance * 0.085);

            double groundDelay = SAMPLE_RATE * (0.0034 + 0.00022 * smoothDistance);
            double earlyLeftDelay = SAMPLE_RATE * (0.0135 + 0.00028 * smoothDistance);
            double earlyRightDelay = SAMPLE_RATE * (0.0195 + 0.00024 * smoothDistance);
            double midLeftDelay = SAMPLE_RATE * (0.046 + 0.00055 * smoothDistance);
            double midRightDelay = SAMPLE_RATE * (0.067 + 0.00048 * smoothDistance);
            double lateLeftDelay = SAMPLE_RATE * (0.108 + 0.00070 * smoothDistance);
            double lateRightDelay = SAMPLE_RATE * (0.151 + 0.00062 * smoothDistance);
            double widthDelay = 9.0 + smoothDistance * 0.55;

            overrunEnergy *= 0.965;

            for (int frame = 0; frame < BUFFER_FRAMES; frame++) {
                double previousDeg = cycleDeg;
                cycleDeg += degreesPerSample;
                if (cycleDeg >= 720.0) cycleDeg -= 720.0;

                noiseState = noiseState * 1664525 + 1013904223;
                double white = (((noiseState >>> 8) & 0xFFFF) / 32767.5) - 1.0;

                if (crossedAngle(previousDeg, cycleDeg, ignitionDeg)) {
                    combustionCount++;
                    boolean limiterCut = smoothRpm > 11_450f && smoothThrottle > 0.65f
                            && (combustionCount & 1) == 1;
                    if (limiterCut) {
                        cycleStrength = 0.04;
                    } else {
                        double variation = 1.0 + 0.055 * white;
                        double overrunCut = 1.0 - Math.min(0.55, overrunEnergy * 0.45);
                        cycleStrength = (0.22 + 1.55 * load) * variation * overrunCut;
                    }
                }

                double exhaustLift = wrappedValveLift(cycleDeg,
                        EXHAUST_OPEN_DEG, EXHAUST_CLOSE_DEG);
                double intakeLift = wrappedValveLift(cycleDeg,
                        INTAKE_OPEN_DEG, INTAKE_CLOSE_DEG);

                double volume = normalizedCylinderVolume(cycleDeg);
                double clearance = 1.0 / COMPRESSION_RATIO;
                double sinceIgnition = wrap720(cycleDeg - ignitionDeg);

                double combustionPressure = 0.0;
                if (sinceIgnition < 280.0) {
                    double burnRise = 1.0 - Math.exp(-sinceIgnition / 8.0);
                    double heatDecay = Math.exp(-sinceIgnition / 210.0);
                    double expansion = Math.pow(clearance / Math.max(clearance, volume), 0.72);
                    combustionPressure = cycleStrength * burnRise * heatDecay * expansion * 8.4;
                }

                double compression = 0.0;
                if (cycleDeg >= 180.0 && cycleDeg < ignitionDeg) {
                    double trappedCharge = 0.14 + fastThrottle * 0.86;
                    compression = trappedCharge
                            * (Math.pow(1.0 / Math.max(volume, clearance), 1.17) - 1.0)
                            * 0.045;
                }

                double cylinderPressure = combustionPressure + compression;
                if (crossedAngle(previousDeg, cycleDeg, EXHAUST_OPEN_DEG)) {
                    blowdownPressure = Math.max(0.0, cylinderPressure);
                    boolean overrunPop = overrunEnergy > 0.18
                            && ((noiseState >>> 16) & 3) != 0;
                    if (overrunPop) {
                        blowdownPressure += overrunEnergy * (0.55 + 0.35 * Math.abs(white));
                        overrunEnergy *= 0.62;
                    }
                    if (afterfire > 0.05) {
                        blowdownPressure += afterfire * 0.85;
                        afterfire *= 0.45;
                    }
                }
                if (crossedAngle(previousDeg, cycleDeg, 0.0)
                        || crossedAngle(previousDeg, cycleDeg, 360.0)) {
                    mechanical = Math.max(mechanical,
                            0.035 + 0.055 * (1.0 - fastThrottle));
                }

                double sinceExhaust = wrap720(cycleDeg - EXHAUST_OPEN_DEG);
                double blowdownEnv = 0.0;
                if (sinceExhaust < 150.0 && blowdownPressure > 0.0) {
                    if (sinceExhaust < BLOWDOWN_RISE_DEG) {
                        double x = sinceExhaust / BLOWDOWN_RISE_DEG;
                        blowdownEnv = x * x;
                    } else {
                        blowdownEnv = Math.exp(-(sinceExhaust - BLOWDOWN_RISE_DEG)
                                / BLOWDOWN_DECAY_DEG);
                    }
                }
                mechanical *= 0.982;
                if (afterfire > 0.0001) afterfire *= 0.996;
                else afterfire = 0.0;

                double exhaustSource = blowdownEnv * blowdownPressure * 1.15
                        + exhaustLift * (0.010 + 0.022 * load)
                        + mechanical * (0.65 + 0.35 * white);

                double intakeDrive = intakeLift * fastThrottle * (0.55 + 0.45 * rpmNorm) * 1.6;
                double intakeAccel = intakeDrive - intakeOmega * intakeOmega * intakePos
                        - intakeDamping * intakeVel;
                intakeVel += intakeAccel / SAMPLE_RATE;
                intakePos += intakeVel / SAMPLE_RATE;
                intakePos *= 0.9996;
                double intake = intakePos * 0.22;

                double headerFeedback = headerReflectionLp * headerToMidR * 0.28;
                headerLine[headerWrite] = exhaustSource + headerFeedback;
                double headerAtMid = readDelay(headerLine, headerWrite, headerSamples);
                headerWrite = (headerWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                headerReflectionLp += (headerAtMid - headerReflectionLp) * 0.12;
                double intoMid = headerAtMid * headerToMidT;

                double midFeedback = midReflectionLp * midToBodyR * 0.16;
                midLine[midWrite] = intoMid + midFeedback;
                double midAtBody = readDelay(midLine, midWrite, midSamples);
                midWrite = (midWrite + 1) & (DELAY_BUFFER_SIZE - 1);
                midReflectionLp += (midAtBody - midReflectionLp) * 0.08;
                double intoBody = midAtBody * midToBodyT;

                mufflerPressure += (intoBody - mufflerPressure) * 0.022;
                mufflerPressure *= 0.9992;
                mufflerBodyLp += (intoBody - mufflerBodyLp) * bodyAlpha;
                mufflerEdgeLp += (intoBody - mufflerEdgeLp) * edgeAlpha;
                double bodyBand = mufflerBodyLp;
                double edgeBand = mufflerEdgeLp - mufflerBodyLp;

                // Perforated core carries the pulse. Packing returns a dull copy, not a comb.
                double coreFeed = bodyBand * 0.78 + edgeBand * 0.10 + mufflerPressure * 0.20;
                mufflerCoreLine[mufflerCoreWrite] = coreFeed;
                double coreOut = readDelay(mufflerCoreLine, mufflerCoreWrite, mufflerCoreSamples);
                mufflerCoreWrite = (mufflerCoreWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                mufflerPackLine[mufflerPackWrite] = coreFeed * 0.16;
                double packOut = readDelay(mufflerPackLine, mufflerPackWrite, mufflerPackSamples);
                mufflerPackWrite = (mufflerPackWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                double canOut = (coreOut * 0.90 + packOut * 0.12 + mufflerPressure * 0.14)
                        * bodyToOutletT;

                outletLine[outletWrite] = canOut + previousOutlet * bodyToOutletR * 0.04;
                double tailPressure = readDelay(outletLine, outletWrite, outletSamples);
                outletWrite = (outletWrite + 1) & (DELAY_BUFFER_SIZE - 1);

                outletDc += (tailPressure - outletDc) * 0.0022;
                double acPressure = tailPressure - outletDc;
                double pressureVelocity = acPressure - previousOutlet;
                previousOutlet = acPressure;
                radiationLp += (pressureVelocity - radiationLp) * radiationAlpha;
                double radiation = radiationLp;

                outletBodyLp += (acPressure - outletBodyLp) * outletBodyAlpha;
                outletEdgeLp += (acPressure - outletEdgeLp) * outletEdgeAlpha;
                double outletBody = outletBodyLp;
                double outletEdge = outletEdgeLp - outletBodyLp;

                double monoRaw = outletBody * 0.92
                        + outletEdge * (0.28 + 0.08 * smoothThrottle)
                        + radiation * 0.20
                        + mufflerPressure * 0.04;

                double shiftGain = 1.0 - smoothShift * 0.28;
                monoRaw *= shiftGain * smoothGain;

                eqLowLp += (monoRaw - eqLowLp) * eqLowAlpha;
                eqBodyLp += (monoRaw - eqBodyLp) * eqBodyAlpha;
                eqPresenceLp += (monoRaw - eqPresenceLp) * eqPresenceAlpha;
                double low = eqLowLp;
                double body = eqBodyLp - eqLowLp;
                double presence = eqPresenceLp - eqBodyLp;
                double air = monoRaw - eqPresenceLp;

                double directivity = 0.34 + 0.66 * smoothRearRadiation;
                double chest = 1.0 + 0.28 * proximity;
                double direct = low * 1.02 * chest
                        + body * 1.12
                        + presence * (0.62 + 0.22 * directivity) * airLoss
                        + air * (0.16 + 0.14 * directivity) * airLoss * airLoss;

                double atmosphereSource = low * 0.95 + body * 1.05
                        + presence * 0.34 + air * 0.03;
                spatialLine[spatialWrite] = atmosphereSource;

                double farEar = readDelay(spatialLine, spatialWrite, interauralSamples);
                double leftDirect = direct;
                double rightDirect = direct;
                if (smoothPan > 0f) {
                    leftDirect = direct * (1.0 - absPan) + farEar * absPan;
                } else if (smoothPan < 0f) {
                    rightDirect = direct * (1.0 - absPan) + farEar * absPan;
                }

                // Even a centered chase image gets a short lateral tap, so the pipe is not a point.
                double width = readDelay(spatialLine, spatialWrite, widthDelay);
                leftDirect = leftDirect * 0.84 + width * 0.16;
                rightDirect = rightDirect * 0.84 + width * 0.16;

                double panAmount = smoothPan * 0.62;
                double leftPanGain = Math.sqrt(0.5 * (1.0 - panAmount));
                double rightPanGain = Math.sqrt(0.5 * (1.0 + panAmount));
                leftDirect *= leftPanGain * distanceGain;
                rightDirect *= rightPanGain * distanceGain;

                // Airbox is at the other end of the bike and mostly omnidirectional up close.
                double intakeGain = (0.18 + 0.55 * proximity) * smoothGain * (0.35 + fastThrottle);
                leftDirect += intake * intakeGain;
                rightDirect += intake * intakeGain;

                double ground = readDelay(spatialLine, spatialWrite, groundDelay);
                double earlyL = readDelay(spatialLine, spatialWrite, earlyLeftDelay);
                double earlyR = readDelay(spatialLine, spatialWrite, earlyRightDelay);
                double midL = readDelay(spatialLine, spatialWrite, midLeftDelay);
                double midR = readDelay(spatialLine, spatialWrite, midRightDelay);
                double lateL = readDelay(spatialLine, spatialWrite, lateLeftDelay);
                double lateR = readDelay(spatialLine, spatialWrite, lateRightDelay);
                groundLp += (ground - groundLp) * groundAlpha;
                earlyLeftLp += (earlyL - earlyLeftLp) * earlyAlpha;
                earlyRightLp += (earlyR - earlyRightLp) * earlyAlpha;
                midLeftLp += (midL - midLeftLp) * midAlpha;
                midRightLp += (midR - midRightLp) * midAlpha;
                lateLeftLp += (lateL - lateLeftLp) * lateAlpha;
                lateRightLp += (lateR - lateRightLp) * lateAlpha;

                double groundGain = 0.16 + 0.10 * smoothRearRadiation;
                double leftAtmosphere = groundLp * groundGain
                        + earlyLeftLp * 0.10 + earlyRightLp * 0.035
                        + midLeftLp * 0.070 + midRightLp * 0.022
                        + lateLeftLp * 0.046 - lateRightLp * 0.012;
                double rightAtmosphere = groundLp * groundGain
                        + earlyRightLp * 0.10 + earlyLeftLp * 0.035
                        + midRightLp * 0.070 + midLeftLp * 0.022
                        + lateRightLp * 0.046 - lateLeftLp * 0.012;

                spatialWrite = (spatialWrite + 1) & (SPATIAL_BUFFER_SIZE - 1);

                double left = leftDirect + leftAtmosphere * atmosphereAmount;
                double right = rightDirect + rightAtmosphere * atmosphereAmount;

                left = Math.tanh(left * (left >= 0.0 ? 1.08 : 0.94)) * 0.86;
                right = Math.tanh(right * (right >= 0.0 ? 1.08 : 0.94)) * 0.86;

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
