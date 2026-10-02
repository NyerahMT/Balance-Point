package com.nyerahworks.balancepoint;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

/**
 * Balance Point - lightweight 3D motorcycle prototype.
 */
public final class BalancePointGame extends ApplicationAdapter {
    private static final float PHYSICS_DT = 1f / 120f;
    private static final float WHEELBASE = 1.403232f;
    private static final float WHEEL_RADIUS = 0.334978f;
    // Approx. 247 lb wet 450 + 190 lb rider = 198 kg combined system mass.
    private static final float MASS = 198f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA = 168f;
    private static final float MAX_CONTACT_FORCE = MASS * GRAVITY * 5f;
    private static final float PITCH_DAMPING_GROUNDED = 2.85f;
    private static final float PITCH_DAMPING_WHEELIE = 1.20f;
    private static final float PITCH_DAMPING_AIR = 0.38f;
    private static final float TERRAIN_ROLL_NATURAL_FREQUENCY = 5.5f;
    private static final float TERRAIN_ROLL_DAMPING_RATIO = 1.15f;
    private static final float COM_FORWARD = 0.636107f;
    private static final float AIRBORNE_COM_FORWARD = 1.00f;
    private static final float AIRBORNE_COM_SHIFT_START = 25f * MathUtils.degreesToRadians;
    private static final float AIRBORNE_COM_SHIFT_END = 55f * MathUtils.degreesToRadians;
    private static final float COM_HEIGHT = 0.36f;
    private static final float REAR_STATIC_LOAD = MASS * GRAVITY
            * (WHEELBASE - COM_FORWARD) / WHEELBASE;
    private static final float FRONT_STATIC_LOAD = MASS * GRAVITY
            * COM_FORWARD / WHEELBASE;
    private static final float TIRE_SLIP_SPEED_AT_PEAK = 0.65f;
    private static final float TIRE_HIGH_SLIP_START = 3.0f;
    private static final float TIRE_HIGH_SLIP_FULL = 10.0f;
    private static final float TIRE_HIGH_SLIP_GRIP = 0.86f;
    private static final float MAX_ENGINE_FORCE = 2500f;
    private static final float MAX_REAR_BRAKE_FORCE = 3800f;
    private static final float ROLLING_RESISTANCE = 0.017f;
    private static final float AERO_DRAG = 0.34f;
    private static final float PITCH_DAMPING = 30f;
    private static final float MAX_PITCH_RATE = 5.4f;
    private static final float REAR_WHEEL_INERTIA = 1.05f;
    private static final float AIR_WHEEL_DRIVE_ACCEL = 115f;
    private static final float AIR_WHEEL_BRAKE_ACCEL = 220f;
    private static final float AIR_WHEEL_DRAG = 0.22f;
    private static final float AIR_PITCH_DAMPING = 0.22f;
    private static final float AIR_MAX_PITCH_RATE = 3.8f;
    private static final float CONTACT_PITCH_SPRING = 38f;
    private static final float CONTACT_PITCH_DAMPING = 11f;
    private static final float LIFT_SEED_RATE = 0.08f;
    private static final float LIFT_MARGIN_FOR_FULL_SEED = 0.25f;
    private static final float THROTTLE_SNAP_RATE = 12f;
    private static final float THROTTLE_SNAP_IMPULSE = 0.52f;
    private static final float LOOP_ANGLE = 155f * MathUtils.degreesToRadians;

    private final MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);
    private final MotorcycleSuspension suspension = new MotorcycleSuspension(
            REAR_STATIC_LOAD, FRONT_STATIC_LOAD, MAX_CONTACT_FORCE);
    private final MotorcycleLateralDynamics lateralDynamics = new MotorcycleLateralDynamics(
            MASS, WHEELBASE, COM_FORWARD, GRAVITY);
    private final MotorcycleRiderDynamics riderDynamics = new MotorcycleRiderDynamics(
            MASS, GRAVITY);

    private GameCamera gameCamera;
    private GameScene scene;
    private GameHud hud;
    private InstrumentDisplay instrumentDisplay;
    private EngineAudio engineAudio;
    private TerrainVisuals terrainVisuals;

    private float speed;
    private float lateralSpeed;
    private float bikeZ;
    private float bikeX;
    private float bikeY = WHEEL_RADIUS;
    private float chassisY = WHEEL_RADIUS + COM_HEIGHT;
    private float effectiveComForward = COM_FORWARD;
    private float verticalVelocity;
    private boolean terrainAirborne;
    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float rollVelocity;
    private float yaw;
    private float yawVelocity;
    private float terrainRoll;
    private float terrainRollVelocity;
    private float wheelSpin;
    private float rearWheelAngularSpeed;
    private float lastGroundSupportPitch;
    private float groundSupportPitchRate;
    private float longitudinalAcceleration;
    private float frontNormalLoad;

    private float throttle;
    private float throttleTarget;
    private float previousThrottleTarget;
    private float throttleSnap;
    private float rearBrake;
    private float rearBrakeTarget;
    private float steer;
    private float steerTarget;
    private float riderLean;
    private float riderLeanTarget;

    private float lookYaw;
    private float lookPitch;
    private boolean looking;
    private boolean cockpitCamera;
    private boolean cameraTouchHeld;
    private boolean shiftDownHeld;
    private boolean shiftUpHeld;
    private boolean frontGrounded = true;
    private int riderControlPointer = -1;
    private int throttleControlPointer = -1;

    private boolean crashed;
    private boolean crashSettled;
    private float crashTimer;
    private float crashPitchRate;
    private float crashRollRate;
    private float crashYawRate;
    private float crashSide = 1f;

    private float wheelieTime;
    private float bestWheelieTime;
    private double physicsAccumulator;

    private GameState gameState = GameState.MAIN_MENU;
    private boolean hasActiveRide;
    private boolean pauseTouchHeld;
    private boolean highQuality = true;

    private static final class WheelContact {
        float normalForce;
        float normalForward;
        float normalUp;
        float groundY;
        float gap;

        boolean touching() {
            return normalForce > 12f;
        }
    }

    private final WheelContact rearContactState = new WheelContact();
    private final WheelContact frontContactState = new WheelContact();
    private final WheelContact rearPostContact = new WheelContact();
    private final WheelContact frontPostContact = new WheelContact();

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(30);
        hud = new GameHud();
        instrumentDisplay = new InstrumentDisplay();
        scene = new GameScene(
                instrumentDisplay, WHEELBASE, WHEEL_RADIUS, COM_FORWARD, COM_HEIGHT);
        terrainVisuals = scene.terrain();
        engineAudio = new EngineAudio();
        highQuality = Gdx.app.getPreferences("balance-point")
                .getBoolean("qualityHigh", true);

        gameCamera = new GameCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        resetBike();
        enterMainMenu();
    }

    @Override
    public void render() {
        float frameDt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        readInput(frameDt);

        if (gameState == GameState.PLAYING) {
            physicsAccumulator += frameDt;
            int steps = 0;
            while (physicsAccumulator >= PHYSICS_DT && steps < 8) {
                simulate(PHYSICS_DT);
                physicsAccumulator -= PHYSICS_DT;
                steps++;
            }
            if (steps == 8) physicsAccumulator = 0.0;

            if (engineAudio != null) {
                engineAudio.update(drivetrain.getRpm(), throttle,
                        drivetrain.isShifting(), crashed);
            }
        } else {
            physicsAccumulator = 0.0;
        }

        if (gameState != GameState.MAIN_MENU) {
            updateBikeVisuals();
            instrumentDisplay.update(speed, drivetrain);
        }
        updateCamera(frameDt);

        float worldCenterZ = gameCamera.worldCenterZ(bikeZ);
        scene.updateWorld(worldCenterZ);
        scene.render(
                gameCamera.camera(),
                worldCenterZ,
                highQuality,
                gameState != GameState.MAIN_MENU,
                bikeX,
                bikeY);
        drawHud();
    }

    private void readInput(float dt) {
        int w = Math.max(1, Gdx.graphics.getWidth());
        int h = Math.max(1, Gdx.graphics.getHeight());

        if (gameState == GameState.MAIN_MENU) {
            riderControlPointer = -1;
            throttleControlPointer = -1;
            readMainMenuInput(w, h);
            return;
        }
        if (gameState == GameState.PAUSED) {
            riderControlPointer = -1;
            throttleControlPointer = -1;
            readPauseMenuInput(w, h);
            return;
        }

        if (riderControlPointer >= 0 && !Gdx.input.isTouched(riderControlPointer)) {
            riderControlPointer = -1;
        }
        if (throttleControlPointer >= 0 && !Gdx.input.isTouched(throttleControlPointer)) {
            throttleControlPointer = -1;
        }

        boolean pauseTouch = false;
        boolean cameraTouch = false;
        boolean throttleTouch = false;
        boolean brakeTouch = false;
        boolean controlBoxTouch = false;
        boolean shiftDownTouch = false;
        boolean shiftUpTouch = false;
        boolean lookTouch = false;
        float requestedThrottle = 0f;
        float requestedBrake = 0f;
        float requestedSteer = 0f;
        float requestedLean = 0f;

        for (int pointer = 0; pointer < 8; pointer++) {
            if (!Gdx.input.isTouched(pointer)) continue;
            float px = Gdx.input.getX(pointer);
            float py = Gdx.input.getY(pointer);
            float x = px / w;
            float y = py / h;

            if (pointer == riderControlPointer) {
                controlBoxTouch = true;
                requestedSteer = RiderControlPad.steer(x);
                requestedLean = RiderControlPad.lean(y);
                continue;
            }
            if (pointer == throttleControlPointer) {
                throttleTouch = true;
                requestedThrottle = ThrottleControl.value(y);
                continue;
            }

            if (x < 0.14f && y < 0.16f) {
                pauseTouch = true;
                continue;
            }
            if (x > 0.86f && y < 0.18f) {
                cameraTouch = true;
                continue;
            }
            if (throttleControlPointer < 0 && ThrottleControl.contains(x, y)) {
                throttleControlPointer = pointer;
                throttleTouch = true;
                requestedThrottle = ThrottleControl.value(y);
                continue;
            }
            if (x >= 0.70f && x <= 0.82f && y > 0.70f) {
                brakeTouch = true;
                float brake = MathUtils.clamp((y - 0.70f) / 0.24f, 0.50f, 1f);
                requestedBrake = Math.max(requestedBrake, brake);
                continue;
            }
            if (riderControlPointer < 0 && RiderControlPad.contains(x, y)) {
                riderControlPointer = pointer;
                controlBoxTouch = true;
                requestedSteer = RiderControlPad.steer(x);
                requestedLean = RiderControlPad.lean(y);
                continue;
            }
            if (x >= 0.34f && x < 0.44f && y > 0.70f && y < 0.92f) {
                shiftDownTouch = true;
                continue;
            }
            if (x >= 0.46f && x < 0.56f && y > 0.70f && y < 0.92f) {
                shiftUpTouch = true;
                continue;
            }
            if (x > 0.30f && x < 0.70f && y > 0.10f && y < 0.66f) {
                lookTouch = true;
                lookYaw -= Gdx.input.getDeltaX(pointer) * 0.0048f;
                lookPitch -= Gdx.input.getDeltaY(pointer) * 0.0043f;
                lookYaw = MathUtils.clamp(lookYaw, -110f * MathUtils.degreesToRadians,
                        110f * MathUtils.degreesToRadians);
                lookPitch = MathUtils.clamp(lookPitch, -38f * MathUtils.degreesToRadians,
                        38f * MathUtils.degreesToRadians);
            }
        }

        if (pauseTouch && !pauseTouchHeld) {
            pauseTouchHeld = true;
            enterPause();
            return;
        }
        pauseTouchHeld = pauseTouch;

        if (cameraTouch && !cameraTouchHeld) cockpitCamera = !cockpitCamera;
        cameraTouchHeld = cameraTouch;
        looking = lookTouch;

        if (!crashed) {
            if (shiftUpTouch && !shiftUpHeld) drivetrain.shiftUp();
            if (shiftDownTouch && !shiftDownHeld) drivetrain.shiftDown(speed);
        }
        shiftUpHeld = shiftUpTouch;
        shiftDownHeld = shiftDownTouch;

        float newThrottleTarget = crashed ? 0f : (throttleTouch ? requestedThrottle : 0f);
        float throttleRiseRate = Math.max(0f, newThrottleTarget - previousThrottleTarget)
                / Math.max(dt, 0.001f);
        float snap = MathUtils.clamp(throttleRiseRate / THROTTLE_SNAP_RATE, 0f, 1f);
        throttleSnap = Math.max(throttleSnap * Math.max(0f, 1f - dt * 3.5f), snap);
        throttleTarget = newThrottleTarget;
        previousThrottleTarget = newThrottleTarget;
        rearBrakeTarget = crashed ? 0f : (brakeTouch ? requestedBrake : 0f);

        steerTarget = crashed || !controlBoxTouch ? 0f : requestedSteer;
        riderLeanTarget = crashed || !controlBoxTouch ? 0f : requestedLean;
        throttle = approach(throttle, throttleTarget,
                (throttleTarget > throttle ? 10.5f : 12f) * dt);
        rearBrake = approach(rearBrake, rearBrakeTarget,
                (rearBrakeTarget > rearBrake ? 24f : 22f) * dt);

        float steerResponse = 5.8f + Math.min(Math.abs(speed) * 0.035f, 1.4f);
        steer += (steerTarget - steer) * Math.min(1f, dt * steerResponse);

        if (!looking) {
            lookYaw *= Math.max(0f, 1f - dt * 1.65f);
            lookPitch *= Math.max(0f, 1f - dt * 1.65f);
        }
    }

    private void readMainMenuInput(int w, int h) {
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = h - Gdx.input.getY();
        float bx = w * 0.075f;
        float bw = w * 0.30f;
        float bh = h * 0.085f;
        float qualityY = h * 0.155f;

        if (inside(x, y, bx, qualityY, bw, bh)) {
            toggleQuality();
            return;
        }

        if (hasActiveRide) {
            if (inside(x, y, bx, h * 0.36f, bw, bh)) {
                resumeRide();
            } else if (inside(x, y, bx, h * 0.245f, bw, bh)) {
                startNewRide();
            }
        } else if (inside(x, y, bx, h * 0.285f, bw, bh)) {
            startNewRide();
        }
    }

    private void readPauseMenuInput(int w, int h) {
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = h - Gdx.input.getY();
        float bx = w * 0.34f;
        float bw = w * 0.32f;
        float bh = h * 0.075f;

        if (inside(x, y, bx, h * 0.56f, bw, bh)) {
            resumeRide();
        } else if (inside(x, y, bx, h * 0.45f, bw, bh)) {
            restartRide();
        } else if (inside(x, y, bx, h * 0.34f, bw, bh)) {
            toggleQuality();
        } else if (inside(x, y, bx, h * 0.23f, bw, bh)) {
            enterMainMenu();
        }
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void toggleQuality() {
        highQuality = !highQuality;
        Gdx.app.getPreferences("balance-point")
                .putBoolean("qualityHigh", highQuality)
                .flush();
        if (!highQuality && scene != null) scene.disableShadows();
    }

    private void startNewRide() {
        resetBike();
        hasActiveRide = true;
        gameState = GameState.PLAYING;
        pauseTouchHeld = false;
        cameraTouchHeld = false;
        if (engineAudio != null) engineAudio.setActive(true);
    }

    private void restartRide() {
        resetBike();
        hasActiveRide = true;
        gameState = GameState.PLAYING;
        pauseTouchHeld = false;
        if (engineAudio != null) engineAudio.setActive(true);
    }

    private void resumeRide() {
        if (!hasActiveRide) {
            startNewRide();
            return;
        }
        gameState = GameState.PLAYING;
        physicsAccumulator = 0.0;
        pauseTouchHeld = false;
        if (engineAudio != null) engineAudio.setActive(true);
    }

    private void enterPause() {
        if (gameState != GameState.PLAYING) return;
        gameState = GameState.PAUSED;
        riderControlPointer = -1;
        throttleControlPointer = -1;
        throttleTarget = 0f;
        rearBrakeTarget = 0f;
        steerTarget = 0f;
        riderLeanTarget = 0f;
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(false);
    }

    private void enterMainMenu() {
        gameState = GameState.MAIN_MENU;
        riderControlPointer = -1;
        throttleControlPointer = -1;
        physicsAccumulator = 0.0;
        pauseTouchHeld = false;
        cameraTouchHeld = false;
        if (gameCamera != null) gameCamera.enterMainMenu();
        if (engineAudio != null) engineAudio.setActive(false);
    }

    private static float approach(float value, float target, float amount) {
        if (value < target) return Math.min(value + amount, target);
        return Math.max(value - amount, target);
    }

    private void simulate(float dt) {
        if (crashed) {
            simulateCrash(dt);
            return;
        }

        riderDynamics.step(riderLeanTarget, longitudinalAcceleration, terrainAirborne, dt);
        riderLean = riderDynamics.pose();

        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);

        float climbPostureShift = 0f;
        if (terrainVisuals != null && !terrainAirborne) {
            float centerSlopeX = terrainVisuals.groundSlopeX(bikeX, bikeZ);
            float centerSlopeZ = terrainVisuals.groundSlopeZ(bikeX, bikeZ);
            float forwardGrade = centerSlopeX * sinYaw + centerSlopeZ * cosYaw;
            float uphillAngle = (float) Math.atan(Math.max(0f, forwardGrade));
            float climbBlend = MathUtils.clamp(uphillAngle
                    / (40f * MathUtils.degreesToRadians), 0f, 1f);
            climbPostureShift = climbBlend * 0.14f;
        }
        effectiveComForward = MathUtils.clamp(
                COM_FORWARD + riderDynamics.combinedComShift() + climbPostureShift,
                0.48f, 0.96f);
        float rearForward = -effectiveComForward * cosPitch + COM_HEIGHT * sinPitch;
        float rearVertical = -effectiveComForward * sinPitch - COM_HEIGHT * cosPitch;
        float frontForward = (WHEELBASE - effectiveComForward) * cosPitch + COM_HEIGHT * sinPitch;
        float frontVertical = (WHEELBASE - effectiveComForward) * sinPitch - COM_HEIGHT * cosPitch;

        float rearX = bikeX + sinYaw * rearForward;
        float rearZ = bikeZ + cosYaw * rearForward;
        float frontX = bikeX + sinYaw * frontForward;
        float frontZ = bikeZ + cosYaw * frontForward;
        float rearY = chassisY + rearVertical;
        float frontY = chassisY + frontVertical;

        float rearForwardVelocity = speed - pitchVelocity * rearVertical;
        float rearVerticalVelocity = verticalVelocity + pitchVelocity * rearForward;
        float frontForwardVelocity = speed - pitchVelocity * frontVertical;
        float frontVerticalVelocity = verticalVelocity + pitchVelocity * frontForward;

        float rearEffectiveMass = MASS * (WHEELBASE - effectiveComForward) / WHEELBASE;
        float frontEffectiveMass = MASS - rearEffectiveMass;
        solveWheelContact(rearContactState, rearX, rearY, rearZ,
                rearForwardVelocity, rearVerticalVelocity, sinYaw, cosYaw,
                suspension.rear(), rearEffectiveMass, dt);
        solveWheelContact(frontContactState, frontX, frontY, frontZ,
                frontForwardVelocity, frontVerticalVelocity, sinYaw, cosYaw,
                suspension.front(), frontEffectiveMass, dt);

        boolean rearTouching = rearContactState.touching();
        boolean frontTouching = frontContactState.touching();
        terrainAirborne = !rearTouching && !frontTouching;
        frontGrounded = frontTouching;
        frontNormalLoad = frontContactState.normalForce;

        float absSpeed = Math.abs(speed);
        float rearTangentForward = rearContactState.normalUp;
        float rearTangentUp = -rearContactState.normalForward;
        float tangentGroundSpeed = speed * rearTangentForward
                + verticalVelocity * rearTangentUp;
        float wheelLinearSpeed = rearWheelAngularSpeed * WHEEL_RADIUS;

        float drivetrainForce = MathUtils.clamp(
                drivetrain.update(wheelLinearSpeed, throttle, dt), 0f, MAX_ENGINE_FORCE);
        float rearSurfaceMu = terrainVisuals != null
                ? terrainVisuals.tractionCoefficient(rearX, rearZ) : 1.0f;
        float frontSurfaceMu = terrainVisuals != null
                ? terrainVisuals.tractionCoefficient(frontX, frontZ) : 1.0f;
        float rearTractionLimit = rearSurfaceMu * rearContactState.normalForce;
        float slipSpeed = wheelLinearSpeed - tangentGroundSpeed;
        float rearTireForce = 0f;
        if (rearTouching && rearTractionLimit > 0f) {
            float absSlip = Math.abs(slipSpeed);
            float forceBuild = MathUtils.clamp(absSlip / TIRE_SLIP_SPEED_AT_PEAK, 0f, 1f);
            float highSlipBlend = MathUtils.clamp((absSlip - TIRE_HIGH_SLIP_START)
                    / (TIRE_HIGH_SLIP_FULL - TIRE_HIGH_SLIP_START), 0f, 1f);
            float highSlipFactor = MathUtils.lerp(1f, TIRE_HIGH_SLIP_GRIP, highSlipBlend);
            rearTireForce = Math.signum(slipSpeed) * rearTractionLimit
                    * forceBuild * highSlipFactor;
        }

        float rearTireForward = rearTireForce * rearTangentForward;
        float rearTireUp = rearTireForce * rearTangentUp;
        float rearNormalForward = rearContactState.normalForce * rearContactState.normalForward;
        float rearNormalUp = rearContactState.normalForce * rearContactState.normalUp;
        float frontNormalForward = frontContactState.normalForce * frontContactState.normalForward;
        float frontNormalUp = frontContactState.normalForce * frontContactState.normalUp;

        float rollingMagnitude = (rearContactState.normalForce + frontContactState.normalForce)
                * ROLLING_RESISTANCE;
        float rollingForce = absSpeed > 0.05f ? Math.signum(speed) * rollingMagnitude : 0f;
        float aero = AERO_DRAG * speed * absSpeed;

        float totalForwardForce = rearNormalForward + frontNormalForward + rearTireForward
                - rollingForce - aero;
        float totalVerticalForce = rearNormalUp + frontNormalUp + rearTireUp - MASS * GRAVITY;

        longitudinalAcceleration = totalForwardForce / MASS;
        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, -10f, 48f);
        verticalVelocity += totalVerticalForce / MASS * dt;

        float previousWheelAngularSpeed = rearWheelAngularSpeed;
        float engineWheelTorque = drivetrainForce * WHEEL_RADIUS;
        float tireReactionTorque = rearTireForce * WHEEL_RADIUS;
        float wheelTorqueBeforeBrake = engineWheelTorque - tireReactionTorque;
        float maxBrakeTorque = MAX_REAR_BRAKE_FORCE * rearBrake * WHEEL_RADIUS;

        if (rearBrake > 0f && Math.abs(rearWheelAngularSpeed) < 1.0f
                && maxBrakeTorque >= Math.abs(wheelTorqueBeforeBrake)) {
            rearWheelAngularSpeed = 0f;
        } else {
            float brakeDirection = Math.abs(rearWheelAngularSpeed) > 0.25f
                    ? Math.signum(rearWheelAngularSpeed)
                    : Math.signum(wheelTorqueBeforeBrake);
            float netWheelTorque = wheelTorqueBeforeBrake - brakeDirection * maxBrakeTorque;
            float wheelAngularAcceleration = netWheelTorque / REAR_WHEEL_INERTIA
                    - rearWheelAngularSpeed * AIR_WHEEL_DRAG;
            rearWheelAngularSpeed += wheelAngularAcceleration * dt;
            if (rearBrake > 0f && previousWheelAngularSpeed * rearWheelAngularSpeed < 0f) {
                rearWheelAngularSpeed = 0f;
            }
            rearWheelAngularSpeed = MathUtils.clamp(rearWheelAngularSpeed, -50f, 260f);
        }

        float actualAngularAcceleration = (rearWheelAngularSpeed - previousWheelAngularSpeed)
                / Math.max(dt, 0.0001f);
        float wheelReactionTorque = rearTouching ? 0f
                : actualAngularAcceleration * REAR_WHEEL_INERTIA;

        float rearContactForwardArm = rearForward
                - WHEEL_RADIUS * rearContactState.normalForward;
        float rearContactVerticalArm = rearVertical
                + suspension.rear().physicalRideOffset()
                - WHEEL_RADIUS * rearContactState.normalUp;
        float frontContactForwardArm = frontForward
                - WHEEL_RADIUS * frontContactState.normalForward;
        float frontContactVerticalArm = frontVertical
                + suspension.front().physicalRideOffset()
                - WHEEL_RADIUS * frontContactState.normalUp;

        float rearForceForward = rearNormalForward + rearTireForward;
        float rearForceUp = rearNormalUp + rearTireUp;
        float riderPitchTorque = riderDynamics.pitchReactionTorque(terrainAirborne, frontTouching);
        float pitchTorque = rearContactForwardArm * rearForceUp
                - rearContactVerticalArm * rearForceForward
                + frontContactForwardArm * frontNormalUp
                - frontContactVerticalArm * frontNormalForward
                + wheelReactionTorque
                + riderPitchTorque;

        float pitchDamping = frontTouching ? PITCH_DAMPING_GROUNDED
                : (rearTouching ? PITCH_DAMPING_WHEELIE : PITCH_DAMPING_AIR);
        float pitchAcceleration = pitchTorque / PITCH_INERTIA
                - pitchVelocity * pitchDamping;
        pitchVelocity += pitchAcceleration * dt;
        pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
        pitch += pitchVelocity * dt;

        bikeX += (sinYaw * speed + cosYaw * lateralSpeed) * dt;
        bikeZ += (cosYaw * speed - sinYaw * lateralSpeed) * dt;
        chassisY += verticalVelocity * dt;

        sinPitch = MathUtils.sin(pitch);
        cosPitch = MathUtils.cos(pitch);
        float rearForwardPost = -effectiveComForward * cosPitch + COM_HEIGHT * sinPitch;
        float rearVerticalPost = -effectiveComForward * sinPitch - COM_HEIGHT * cosPitch;
        float frontForwardPost = (WHEELBASE - effectiveComForward) * cosPitch + COM_HEIGHT * sinPitch;
        float frontVerticalPost = (WHEELBASE - effectiveComForward) * sinPitch - COM_HEIGHT * cosPitch;
        float rearPostX = bikeX + sinYaw * rearForwardPost;
        float rearPostZ = bikeZ + cosYaw * rearForwardPost;
        float frontPostX = bikeX + sinYaw * frontForwardPost;
        float frontPostZ = bikeZ + cosYaw * frontForwardPost;
        sampleWheelSurface(rearPostContact, rearPostX, chassisY + rearVerticalPost,
                rearPostZ, sinYaw, cosYaw);
        sampleWheelSurface(frontPostContact, frontPostX, chassisY + frontVerticalPost,
                frontPostZ, sinYaw, cosYaw);
        float rearOverTravel = Math.max(0f,
                suspension.rear().staticCompression() - rearPostContact.gap
                        - suspension.rear().travel());
        float frontOverTravel = Math.max(0f,
                suspension.front().staticCompression() - frontPostContact.gap
                        - suspension.front().travel());
        float penetrationCorrection = Math.max(rearOverTravel, frontOverTravel) - 0.012f;
        if (penetrationCorrection > 0f) {
            chassisY += Math.min(penetrationCorrection, 0.16f);
            if (verticalVelocity < 0f) {
                float correctionBlend = MathUtils.clamp(penetrationCorrection / 0.16f, 0f, 1f);
                float velocityRetention = MathUtils.lerp(0.92f, 0.58f, correctionBlend);
                verticalVelocity *= velocityRetention;
            }
        }

        rearVertical = -effectiveComForward * sinPitch - COM_HEIGHT * cosPitch;
        bikeY = chassisY + rearVertical;

        wheelSpin += rearWheelAngularSpeed * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        lateralDynamics.step(
                speed,
                steer,
                rearTireForce,
                rearContactState.normalForce,
                frontContactState.normalForce,
                rearSurfaceMu,
                frontSurfaceMu,
                rearTouching,
                frontTouching,
                dt);
        lateralSpeed = lateralDynamics.lateralSpeed();
        yawVelocity = lateralDynamics.yawRate();
        roll = lateralDynamics.roll();
        rollVelocity = lateralDynamics.rollRate();
        yaw += yawVelocity * dt;
        if (yaw > MathUtils.PI) yaw -= MathUtils.PI2;
        if (yaw < -MathUtils.PI) yaw += MathUtils.PI2;

        float terrainRollTarget = 0f;
        if (!terrainAirborne && terrainVisuals != null) {
            float slopeX = terrainVisuals.groundSlopeX(bikeX, bikeZ);
            float slopeZ = terrainVisuals.groundSlopeZ(bikeX, bikeZ);
            float lateralGrade = slopeX * MathUtils.cos(yaw) - slopeZ * MathUtils.sin(yaw);
            terrainRollTarget = (float) Math.atan(lateralGrade);
        }
        float terrainRollAcceleration = (terrainRollTarget - terrainRoll)
                * TERRAIN_ROLL_NATURAL_FREQUENCY * TERRAIN_ROLL_NATURAL_FREQUENCY
                - 2f * TERRAIN_ROLL_DAMPING_RATIO * TERRAIN_ROLL_NATURAL_FREQUENCY
                * terrainRollVelocity;
        terrainRollVelocity += terrainRollAcceleration * dt;
        terrainRollVelocity = MathUtils.clamp(terrainRollVelocity, -2.5f, 2.5f);
        terrainRoll += terrainRollVelocity * dt;

        if (rearTouching && !frontTouching && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontTouching) {
            wheelieTime = 0f;
        }

        if (pitch > LOOP_ANGLE
                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(lateralSpeed)
                || Float.isNaN(yaw) || Float.isNaN(roll) || Float.isNaN(bikeX)
                || Float.isNaN(bikeZ) || Float.isNaN(chassisY)) {
            beginCrash();
        }
    }

    private void sampleWheelSurface(WheelContact out, float worldX, float wheelY, float worldZ,
                                    float sinYaw, float cosYaw) {
        final float[] offsets = {-0.90f, -0.60f, -0.30f, 0f, 0.30f, 0.60f, 0.90f};
        final float slopeProbe = 0.075f;
        float bestGap = Float.POSITIVE_INFINITY;
        float bestNormalForward = 0f;
        float bestNormalUp = 1f;
        float bestGroundY = WHEEL_RADIUS;

        for (float factor : offsets) {
            float offset = factor * WHEEL_RADIUS;
            float sampleX = worldX + sinYaw * offset;
            float sampleZ = worldZ + cosYaw * offset;
            float ground = terrainVisuals != null
                    ? terrainVisuals.groundHeight(sampleX, sampleZ) : 0f;
            float ahead = terrainVisuals != null
                    ? terrainVisuals.groundHeight(sampleX + sinYaw * slopeProbe,
                    sampleZ + cosYaw * slopeProbe) : 0f;
            float behind = terrainVisuals != null
                    ? terrainVisuals.groundHeight(sampleX - sinYaw * slopeProbe,
                    sampleZ - cosYaw * slopeProbe) : 0f;
            float forwardSlope = (ahead - behind) / (2f * slopeProbe);
            float invLength = 1f / (float) Math.sqrt(1f + forwardSlope * forwardSlope);
            float normalForward = -forwardSlope * invLength;
            float normalUp = invLength;

            float centerFromProbe = -offset;
            float signedDistance = normalForward * centerFromProbe
                    + normalUp * (wheelY - ground);
            float gap = signedDistance - WHEEL_RADIUS;
            if (gap < bestGap) {
                bestGap = gap;
                bestNormalForward = normalForward;
                bestNormalUp = normalUp;
                bestGroundY = ground
                        + (WHEEL_RADIUS - normalForward * centerFromProbe) / normalUp;
            }
        }

        out.normalForward = bestNormalForward;
        out.normalUp = bestNormalUp;
        out.groundY = bestGroundY;
        out.gap = bestGap;
    }

    private void solveWheelContact(WheelContact out, float worldX, float wheelY, float worldZ,
                                   float pointForwardVelocity, float pointVerticalVelocity,
                                   float sinYaw, float cosYaw,
                                   MotorcycleSuspension.Unit suspensionUnit,
                                   float effectiveMass, float dt) {
        sampleWheelSurface(out, worldX, wheelY, worldZ, sinYaw, cosYaw);

        float normalVelocity = pointForwardVelocity * out.normalForward
                + pointVerticalVelocity * out.normalUp;
        float demandedCompression = suspensionUnit.staticCompression() - out.gap;
        float compressionVelocity = -normalVelocity;
        out.normalForce = suspensionUnit.solve(
                demandedCompression,
                compressionVelocity,
                effectiveMass,
                dt);
    }

    private void beginCrash() {
        if (crashed) return;
        crashed = true;
        crashSettled = false;
        crashTimer = 0f;
        lateralSpeed = 0f;
        rollVelocity = 0f;
        yawVelocity = 0f;
        terrainRollVelocity = 0f;
        suspension.reset();
        crashSide = roll < -0.05f ? -1f : (roll > 0.05f ? 1f : (steer < 0f ? -1f : 1f));
        crashPitchRate = MathUtils.clamp(pitchVelocity + 0.75f, -1.2f, 3.2f);
        crashRollRate = crashSide * (1.4f + MathUtils.clamp(speed * 0.045f, 0f, 1.5f));
        crashYawRate = -steer * (0.4f + MathUtils.clamp(speed * 0.025f, 0f, 0.8f));
        throttleControlPointer = -1;
        throttleTarget = 0f;
        rearBrakeTarget = 0f;
        steerTarget = 0f;
    }

    private void simulateCrash(float dt) {
        crashTimer += dt;
        throttle = approach(throttle, 0f, 14f * dt);
        rearBrake = 0f;
        drivetrain.update(speed, 0f, dt);

        float slideDecel = 6.5f + speed * 0.95f;
        speed = Math.max(0f, speed - slideDecel * dt);
        bikeX += MathUtils.sin(yaw) * speed * dt;
        bikeZ += MathUtils.cos(yaw) * speed * dt;
        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        if (!crashSettled) {
            if (crashTimer < 0.38f) {
                pitch += crashPitchRate * dt;
                roll += crashRollRate * dt;
                yaw += crashYawRate * dt;
                crashPitchRate *= Math.max(0f, 1f - 2.4f * dt);
                crashRollRate *= Math.max(0f, 1f - 2.0f * dt);
                crashYawRate *= Math.max(0f, 1f - 2.5f * dt);
            } else {
                float targetRoll = crashSide * 78f * MathUtils.degreesToRadians;
                float targetPitch = 10f * MathUtils.degreesToRadians;
                float settleResponse = Math.min(1f, dt * 5.5f);
                roll += (targetRoll - roll) * settleResponse;
                pitch += (targetPitch - pitch) * Math.min(1f, dt * 4.2f);
                yaw += crashYawRate * dt;
                crashYawRate *= Math.max(0f, 1f - 4f * dt);

                if (crashTimer > 0.95f || speed < 0.45f) {
                    roll = targetRoll;
                    pitch = targetPitch;
                    crashSettled = true;
                }
            }
        }

        if (crashSettled) {
            speed = Math.max(0f, speed - 10f * dt);
        }

        if (crashTimer > 2.25f) resetBike();
    }

    private void resetBike() {
        speed = 0f;
        lateralSpeed = 0f;
        bikeX = 0f;
        chassisY = WHEEL_RADIUS + COM_HEIGHT;
        bikeY = WHEEL_RADIUS;
        effectiveComForward = COM_FORWARD;
        verticalVelocity = 0f;
        terrainAirborne = false;
        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        rollVelocity = 0f;
        yaw = 0f;
        yawVelocity = 0f;
        terrainRoll = 0f;
        terrainRollVelocity = 0f;
        wheelSpin = 0f;
        rearWheelAngularSpeed = 0f;
        lastGroundSupportPitch = 0f;
        groundSupportPitchRate = 0f;
        longitudinalAcceleration = 0f;
        frontNormalLoad = FRONT_STATIC_LOAD;
        throttle = throttleTarget = previousThrottleTarget = 0f;
        throttleSnap = 0f;
        rearBrake = rearBrakeTarget = 0f;
        steer = steerTarget = 0f;
        riderLean = riderLeanTarget = 0f;
        riderControlPointer = -1;
        throttleControlPointer = -1;
        lookYaw = lookPitch = 0f;
        if (gameCamera != null) gameCamera.resetRide();
        shiftDownHeld = shiftUpHeld = false;
        drivetrain.reset();
        suspension.reset();
        lateralDynamics.reset();
        riderDynamics.reset();
        frontGrounded = true;
        crashed = false;
        crashSettled = false;
        crashTimer = 0f;
        crashPitchRate = crashRollRate = crashYawRate = 0f;
        crashSide = 1f;
        wheelieTime = 0f;
        physicsAccumulator = 0.0;
    }

    private void updateBikeVisuals() {
        GameScene.BikeState state = scene.bikeState();
        state.bikeX = bikeX;
        state.bikeZ = bikeZ;
        state.chassisY = chassisY;
        state.effectiveComForward = effectiveComForward;
        state.pitch = pitch;
        state.roll = roll;
        state.yaw = yaw;
        state.terrainRoll = terrainRoll;
        state.wheelSpin = wheelSpin;
        state.speed = speed;
        state.steer = steer;
        state.riderLean = riderLean;
        state.rearSuspensionTravel = suspension.rear().rideOffset();
        state.frontSuspensionTravel = suspension.front().rideOffset();
        state.frontGrounded = frontGrounded;
        state.terrainAirborne = terrainAirborne;
        state.crashed = crashed;
        state.crashSettled = crashSettled;
        bikeY = scene.updateBike(state);
    }

    private void updateCamera(float dt) {
        GameCamera.State state = gameCamera.state();
        state.gameState = gameState;
        state.bikeRoot = scene.bikeRoot();
        state.importedBikeLoaded = scene.importedBikeLoaded();
        state.terrainAirborne = terrainAirborne;
        state.cockpitCamera = cockpitCamera;
        state.bikeX = bikeX;
        state.bikeY = bikeY;
        state.bikeZ = bikeZ;
        state.speed = speed;
        state.verticalVelocity = verticalVelocity;
        state.pitch = pitch;
        state.roll = roll;
        state.yaw = yaw;
        state.terrainRoll = terrainRoll;
        state.riderLean = riderLean;
        state.lookYaw = lookYaw;
        state.lookPitch = lookPitch;
        gameCamera.update(dt, terrainVisuals);
    }

    private void drawHud() {
        GameHud.State state = hud.state();
        state.gameState = gameState;
        state.hasActiveRide = hasActiveRide;
        state.highQuality = highQuality;
        state.crashed = crashed;
        state.crashSettled = crashSettled;
        state.frontGrounded = frontGrounded;
        state.shiftDownHeld = shiftDownHeld;
        state.shiftUpHeld = shiftUpHeld;
        state.pitch = pitch;
        state.wheelieTime = wheelieTime;
        state.bestWheelieTime = bestWheelieTime;
        state.crashTimer = crashTimer;
        state.speed = speed;
        state.steerTarget = steerTarget;
        state.riderLeanTarget = riderLeanTarget;
        state.throttleTarget = throttleTarget;
        state.rearBrakeTarget = rearBrakeTarget;
        state.gear = drivetrain.getGear();
        state.rpm = drivetrain.getRpm();
        hud.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (gameCamera != null) gameCamera.resize(width, height);
        if (hud != null) hud.resize(width, height);
    }

    @Override
    public void pause() {
        if (engineAudio != null) engineAudio.setActive(false);
    }

    @Override
    public void resume() {
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(gameState == GameState.PLAYING);
    }

    @Override
    public void dispose() {
        if (engineAudio != null) engineAudio.dispose();
        if (scene != null) scene.dispose();
        if (hud != null) hud.dispose();
        if (instrumentDisplay != null) instrumentDisplay.dispose();
    }
}
