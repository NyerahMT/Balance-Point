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
    // Presentation constants; live physical parameters are owned by the V3 plant.
    private static final float COM_FORWARD = 0.636107f;
    private static final float COM_HEIGHT = 0.36f;

    private GameCamera gameCamera;
    private GameScene scene;
    private GameHud hud;
    private PhysicsDevMenu physicsDevMenu;
    private InstrumentDisplay instrumentDisplay;
    private EngineAudio engineAudio;
    private TerrainVisuals terrainVisuals;

    // Physics V3 is the single authoritative active motorcycle simulation.
    private final PhysicsV3MultibodyPlant physicsV3 = new PhysicsV3MultibodyPlant();
    private final PhysicsDiagnostics diagnostics = new PhysicsDiagnostics();
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
    private float yaw;
    private float terrainRoll;
    private float wheelSpin;
    // Horizontal translation becomes a world-space ballistic vector at takeoff. This prevents
    // yaw/sideslip changes in the air from bending the actual flight path.

    private float throttle;
    private float throttleTarget;
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
    private int pendingDiagnosticShift;

    private GameState gameState = GameState.MAIN_MENU;
    private boolean hasActiveRide;
    private boolean pauseTouchHeld;
    private boolean highQuality = true;

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(30);
        hud = new GameHud();
        physicsDevMenu = new PhysicsDevMenu(physicsV3, diagnostics);
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
                if (gameState != GameState.PLAYING) break;
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
        if (gameState == GameState.PHYSICS_DEV) physicsDevMenu.draw();
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
        if (gameState == GameState.PHYSICS_DEV) {
            int action = physicsDevMenu.readInput();
            if (action == PhysicsDevMenu.BACK) {
                gameState = GameState.PAUSED;
            } else if (action == PhysicsDevMenu.RECORD) {
                startNewRide();
                pendingDiagnosticShift = 0;
                diagnostics.startCapture(physicsV3.tuning());
            } else if (action == PhysicsDevMenu.SAVE) {
                saveDiagnostics();
            } else if (action == PhysicsDevMenu.REPLAY) {
                if (diagnostics.hasCapture()) {
                    diagnostics.stopCapture();
                    resetBike();
                    if (diagnostics.startReplay()) {
                        hasActiveRide = true;
                        gameState = GameState.PLAYING;
                        if (engineAudio != null) engineAudio.setActive(true);
                    }
                } else {
                    physicsDevMenu.setStatus("NO CAPTURE TO REPLAY");
                }
            }
            return;
        }
        if (gameState == GameState.PAUSED) {
            riderControlPointer = -1;
            throttleControlPointer = -1;
            readPauseMenuInput(w, h);
            return;
        }
        if (diagnostics.replaying()) {
            if (Gdx.input.justTouched()
                    && Gdx.input.getX() < w * 0.14f
                    && Gdx.input.getY() < h * 0.16f) {
                diagnostics.stopReplay();
                enterPause();
            }
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
            if (shiftUpTouch && !shiftUpHeld) {
                physicsV3.shiftUp();
                if (diagnostics.capturing()) pendingDiagnosticShift = 1;
            }
            if (shiftDownTouch && !shiftDownHeld) {
                physicsV3.shiftDown();
                if (diagnostics.capturing()) pendingDiagnosticShift = -1;
            }
        }
        shiftUpHeld = shiftUpTouch;
        shiftDownHeld = shiftDownTouch;

        float newThrottleTarget = crashed ? 0f : (throttleTouch ? requestedThrottle : 0f);
        throttleTarget = newThrottleTarget;
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
        } else if (inside(x, y, bx, h * 0.115f, bw, bh)) {
            gameState = GameState.PHYSICS_DEV;
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
        diagnostics.stopReplay();
        diagnostics.stopCapture();
        resetBike();
        hasActiveRide = true;
        gameState = GameState.PLAYING;
        pauseTouchHeld = false;
        cameraTouchHeld = false;
        if (engineAudio != null) engineAudio.setActive(true);
    }

    private void restartRide() {
        diagnostics.stopReplay();
        diagnostics.stopCapture();
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
        diagnostics.stopReplay();
        diagnostics.stopCapture();
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

    private void saveDiagnostics() {
        diagnostics.stopCapture();
        if (!diagnostics.hasCapture()) {
            physicsDevMenu.setStatus("NO CAPTURE TO EXPORT");
            return;
        }
        try {
            String filename = "BalancePoint-Capture-" + System.currentTimeMillis() + ".csv";
            Gdx.files.local(filename).writeString(diagnostics.toCsv(), false, "UTF-8");
            physicsDevMenu.setStatus("SAVED IN FILES: " + filename);
        } catch (RuntimeException exception) {
            Gdx.app.error("PhysicsDiagnostics", "CSV export failed", exception);
            physicsDevMenu.setStatus("EXPORT FAILED: " + exception.getClass().getSimpleName());
        }
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

        PhysicsV3MultibodyPlant.Input v3Input = physicsV3.input();
        boolean replayStep = diagnostics.replaying();
        if (replayStep) {
            if (!diagnostics.applyReplayInput(v3Input)) {
                enterPause();
                return;
            }
            diagnostics.applyReplayShift(physicsV3);
            throttle = v3Input.throttle;
            rearBrake = v3Input.rearBrake;
            steer = v3Input.steer;
            riderLean = riderLeanTarget = v3Input.riderForeAft;
        } else {
            v3Input.throttle = throttle;
            v3Input.rearBrake = rearBrake;
            v3Input.steer = steer;
            v3Input.riderForeAft = riderLean;
        }
        physicsV3.step(physicsV3Terrain, dt);
        if (diagnostics.capturing()) {
            diagnostics.capture(v3Input, physicsV3, pendingDiagnosticShift);
            pendingDiagnosticShift = 0;
        }
        if (replayStep) diagnostics.compareReplayStep(physicsV3);

        // Dynamic state comes exclusively from the V3 plant. The Y conversion below is only
        // an imported-mesh origin adapter; it does not feed back into Physics V3.
        bikeX = physicsV3.x();
        bikeZ = physicsV3.z();
        chassisY = physicsV3.y() - 0.826f + (WHEEL_RADIUS + COM_HEIGHT);
        speed = physicsV3.speed();
        verticalVelocity = physicsV3.verticalVelocity();
        pitch = physicsV3.pitch();
        pitchVelocity = physicsV3.pitchRate();
        roll = physicsV3.roll();
        yaw = physicsV3.yaw();
        terrainRoll = 0f;
        wheelSpin = physicsV3.rearWheelSpin();
        frontGrounded = physicsV3.frontGrounded();
        terrainAirborne = physicsV3.airborne();

        // Presentation-only legacy values. They do not feed back into PhysicsV3MultibodyPlant.
        effectiveComForward = COM_FORWARD;
        riderLean += (riderLeanTarget - riderLean) * Math.min(1f, dt * 6f);
        bikeY = chassisY - COM_HEIGHT;

        if (physicsV3.rearGrounded() && !frontGrounded && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontGrounded) {
            wheelieTime = 0f;
        }
        if (replayStep && !diagnostics.replaying()) enterPause();
    }

    private void beginCrash() {
        if (crashed) return;
        crashed = true;
        crashSettled = false;
        crashTimer = 0f;
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
        bikeX = 0f;
        chassisY = WHEEL_RADIUS + COM_HEIGHT;
        bikeY = WHEEL_RADIUS;
        effectiveComForward = COM_FORWARD;
        verticalVelocity = 0f;
        terrainAirborne = false;
        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        yaw = 0f;
        terrainRoll = 0f;
        wheelSpin = 0f;
        throttle = throttleTarget = 0f;
        rearBrake = rearBrakeTarget = 0f;
        steer = steerTarget = 0f;
        riderLean = riderLeanTarget = 0f;
        riderControlPointer = -1;
        throttleControlPointer = -1;
        lookYaw = lookPitch = 0f;
        if (gameCamera != null) gameCamera.resetRide();
        shiftDownHeld = shiftUpHeld = false;
        physicsV3.reset(physicsV3Terrain);
        pendingDiagnosticShift = 0;
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
        if (physicsDevMenu != null) physicsDevMenu.resize(width, height);
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
        if (physicsDevMenu != null) physicsDevMenu.dispose();
        if (instrumentDisplay != null) instrumentDisplay.dispose();
    }
}
