package com.nyerahworks.balancepoint;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
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
    private static final float AIR_WHEEL_DRIVE_ACCEL = 150f;
    private static final float AIR_WHEEL_BRAKE_ACCEL = 260f;
    private static final float AIR_WHEEL_DRAG = 0.22f;
    private static final float AIR_PITCH_DAMPING = 0.08f;
    private static final float AIR_MAX_PITCH_RATE = 5.2f;
    private static final float AIR_WHEEL_REACTION_GAIN = 1.32f;
    private static final float AIR_RIDER_TORQUE_GAIN = 1.30f;
    private static final float AIR_CONTROL_BLEND_TIME = 0.055f;
    private static final float CONTACT_PITCH_SPRING = 38f;
    private static final float CONTACT_PITCH_DAMPING = 11f;
    private static final float LIFT_SEED_RATE = 0.08f;
    private static final float LIFT_MARGIN_FOR_FULL_SEED = 0.25f;
    private static final float THROTTLE_SNAP_RATE = 12f;
    private static final float THROTTLE_SNAP_IMPULSE = 0.52f;
    private static final float TAKEOFF_PITCH_RATE_LIMIT = 2.2f;
    private static final float TAKEOFF_PITCH_RATE_BLEND = 0.55f;

    private final MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);
    private final MotorcycleSuspension suspension = new MotorcycleSuspension(
            REAR_STATIC_LOAD, FRONT_STATIC_LOAD, MAX_CONTACT_FORCE);
    private final MotorcycleLateralDynamics lateralDynamics = new MotorcycleLateralDynamics(
            MASS, WHEELBASE, COM_FORWARD, GRAVITY);
    private final MotorcycleRiderDynamics riderDynamics = new MotorcycleRiderDynamics(
            MASS, GRAVITY);
    private final MotorcycleLandingDynamics landingDynamics = new MotorcycleLandingDynamics();
    private final MotorcycleBodyContact bodyContact = new MotorcycleBodyContact();

    private GameCamera gameCamera;
    private GameScene scene;
    private GameHud hud;
    private InstrumentDisplay instrumentDisplay;
    private EngineAudio engineAudio;
    private TerrainVisuals terrainVisuals;

    // PHYSICS_V3_RUNTIME -- physical dimensions live inside the plant; the old constants above
    // remain only because GameScene/DirtBikeVisualRig use the imported GLB's presentation scale.
    private final PhysicsV3MultibodyPlant physicsV3 = new PhysicsV3MultibodyPlant();
    private final PhysicsV3MultibodyPlant.Terrain physicsV3Terrain = new PhysicsV3MultibodyPlant.Terrain() {
        @Override public float height(float x, float z) {
            return terrainVisuals == null ? 0f : terrainVisuals.groundHeight(x, z);
        }
        @Override public float slopeX(float x, float z) {
            return terrainVisuals == null ? 0f : terrainVisuals.groundSlopeX(x, z);
        }
        @Override public float slopeZ(float x, float z) {
            return terrainVisuals == null ? 0f : terrainVisuals.groundSlopeZ(x, z);
        }
        @Override public float traction(float x, float z) {
            return terrainVisuals == null ? 1f : terrainVisuals.tractionCoefficient(x, z);
        }
    };

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
    // Horizontal translation becomes a world-space ballistic vector at takeoff. This prevents
    // yaw/sideslip changes in the air from bending the actual flight path.
    private float airborneVelocityX;
    private float airborneVelocityZ;
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
        float normalVelocity;

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

        } else {
            physicsAccumulator = 0.0;
        }

        if (gameState != GameState.MAIN_MENU) {
            updateBikeVisuals();
            instrumentDisplay.update(speed, physicsV3.powertrain().getRpm(),
                    physicsV3.powertrain().getRedlineRpm(), physicsV3.powertrain().getGear());
        }
        updateCamera(frameDt);
        if (gameState == GameState.PLAYING && engineAudio != null) {
            updateEngineAudio();
        }

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
            if (shiftUpTouch && !shiftUpHeld) physicsV3.shiftUp();
            if (shiftDownTouch && !shiftDownHeld) physicsV3.shiftDown();
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


    /**
     * Exhaust sits behind the swingarm. Pan, distance and rear radiation drive the outdoor
     * stage; closing speed is the bike velocity toward the listener for doppler.
     */
    private void updateEngineAudio() {
        PerspectiveCamera cam = gameCamera.camera();
        float sin = MathUtils.sin(yaw);
        float cos = MathUtils.cos(yaw);
        float exhaustX = bikeX - sin * 0.92f;
        float exhaustY = bikeY + 0.42f;
        float exhaustZ = bikeZ - cos * 0.92f;
        float dx = exhaustX - cam.position.x;
        float dy = exhaustY - cam.position.y;
        float dz = exhaustZ - cam.position.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dist = Math.max(dist, 0.35f);
        float inv = 1f / dist;
        float lx = dx * inv;
        float ly = dy * inv;
        float lz = dz * inv;

        float fx = cam.direction.x;
        float fy = cam.direction.y;
        float fz = cam.direction.z;
        float ux = cam.up.x;
        float uy = cam.up.y;
        float uz = cam.up.z;
        float rx = fy * uz - fz * uy;
        float ry = fz * ux - fx * uz;
        float rz = fx * uy - fy * ux;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rlen > 0.0001f) {
            rx /= rlen;
            ry /= rlen;
            rz /= rlen;
        }
        float pan = MathUtils.clamp(lx * rx + ly * ry + lz * rz, -1f, 1f);

        // Outlet points rearward. rearRadiation is how directly the listener sits behind it.
        float toListenerX = -lx;
        float toListenerZ = -lz;
        float rear = MathUtils.clamp(toListenerX * -sin + toListenerZ * -cos, 0f, 1f);

        float bikeVx = sin * speed;
        float bikeVy = verticalVelocity;
        float bikeVz = cos * speed;
        float closing = bikeVx * toListenerX + bikeVy * (-ly) + bikeVz * toListenerZ;
        engineAudio.update(physicsV3.powertrain().getRpm(), throttle,
                physicsV3.powertrain().isShifting(), crashed,
                pan, dist, rear, closing);
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

        float previousSpeed = speed;
        PhysicsV3MultibodyPlant.Input v2Input = physicsV3.input();
        v2Input.throttle = throttle;
        v2Input.rearBrake = rearBrake;
        // Player turn intent is physical steering-torque authority in the V2 plant.
        v2Input.steer = steer;
        v2Input.riderForeAft = riderLean;
        physicsV3.step(physicsV3Terrain, dt);

        // Dynamic state comes exclusively from the V2 plant. The Y conversion below is only
        // an imported-mesh origin adapter; it does not feed back into Physics V3.
        bikeX = physicsV3.x();
        bikeZ = physicsV3.z();
        chassisY = physicsV3.y() - 0.826f + (WHEEL_RADIUS + COM_HEIGHT);
        speed = physicsV3.speed();
        verticalVelocity = physicsV3.verticalVelocity();
        pitch = physicsV3.pitch();
        pitchVelocity = physicsV3.pitchRate();
        roll = physicsV3.roll();
        rollVelocity = physicsV3.rollRate();
        yaw = physicsV3.yaw();
        yawVelocity = physicsV3.yawRate();
        terrainRoll = 0f;
        terrainRollVelocity = 0f;
        wheelSpin = physicsV3.rearWheelSpin();
        frontGrounded = physicsV3.frontGrounded();
        terrainAirborne = physicsV3.airborne();
        frontNormalLoad = physicsV3.telemetry().frontFz;
        longitudinalAcceleration = (speed - previousSpeed) / Math.max(dt, 0.0001f);

        // Presentation-only legacy values. They do not feed back into PhysicsV3MultibodyPlant.
        effectiveComForward = COM_FORWARD;
        riderLean += (riderLeanTarget - riderLean) * Math.min(1f, dt * 6f);
        rearWheelAngularSpeed = wheelSpin / Math.max(dt, 0.0001f);
        bikeY = chassisY - COM_HEIGHT;

        if (physicsV3.rearGrounded() && !frontGrounded && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontGrounded) {
            wheelieTime = 0f;
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
        out.normalVelocity = normalVelocity;
        float demandedCompression = suspensionUnit.staticCompression() - out.gap;
        float compressionVelocity = -normalVelocity;
        out.normalForce = suspensionUnit.solve(
                demandedCompression,
                compressionVelocity,
                effectiveMass,
                dt);
    }

    private static float wrapAngle(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
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
        landingDynamics.reset();
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
        airborneVelocityX = 0f;
        airborneVelocityZ = 0f;
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
        landingDynamics.reset();
        physicsV3.reset(physicsV3Terrain);
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
        state.rearSuspensionTravel = physicsV3.rearCompression() - 0.111f;
        state.frontSuspensionTravel = physicsV3.frontCompression() - 0.095f;
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
        state.gear = physicsV3.powertrain().getGear();
        state.rpm = physicsV3.powertrain().getRpm();
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
