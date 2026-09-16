package com.nyerahworks.balancepoint;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Balance Point - GMEE-focused lightweight 3D motorcycle prototype.
 *
 * Physics v3 keeps the load-transfer and traction model, adds a stronger
 * pitch-rate damper around balance point, rider weight shift, horizon-stabilized
 * helmet cam, free-look, and dedicated analog mobile controls.
 */
public final class BalancePointGame extends ApplicationAdapter {
    private static final float PHYSICS_DT = 1f / 120f;
    private static final float SEGMENT_LENGTH = 20f;
    private static final float ROAD_HALF_WIDTH = 4.2f;

    private static final float WHEELBASE = 1.45f;
    private static final float WHEEL_RADIUS = 0.31f;
    private static final float MASS = 212f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA = 126f;
    private static final float COM_FORWARD = 0.60f;
    private static final float COM_HEIGHT = 0.70f;
    private static final float RIDER_SHIFT = 0.14f;

    private static final float TIRE_MU = 1.05f;
    private static final float MAX_ENGINE_FORCE = 2500f;
    private static final float MAX_REAR_BRAKE_FORCE = 3800f;
    private static final float ENGINE_POWER = 44000f;
    private static final float ROLLING_RESISTANCE = 0.017f;
    private static final float AERO_DRAG = 0.34f;

    private static final float PITCH_DAMPING_BASE = 85f;
    private static final float PITCH_DAMPING_BALANCE = 360f;
    private static final float BALANCE_RATE_DECAY = 3.2f;
    private static final float BALANCE_FREE_RATE = 0.20f;
    private static final float BALANCE_FULL_RATE = 0.95f;
    private static final float BALANCE_DAMPING_BAND = 18f * MathUtils.degreesToRadians;
    private static final float LOOP_ANGLE = 103f * MathUtils.degreesToRadians;

    private final Array<Model> ownedModels = new Array<>();
    private final Matrix4 bikeRoot = new Matrix4();
    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();

    private PerspectiveCamera camera;
    private OrthographicCamera uiCamera;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Environment environment;

    private ModelInstance[] roadSegments;
    private ModelInstance[] groundSegments;
    private ModelInstance[] shoulderLeft;
    private ModelInstance[] shoulderRight;
    private ModelInstance[] laneDashes;
    private ModelInstance[] buildings;

    private ModelInstance rearWheel;
    private ModelInstance frontWheel;
    private ModelInstance rearHub;
    private ModelInstance frontHub;
    private ModelInstance frame;
    private ModelInstance tank;
    private ModelInstance seat;
    private ModelInstance frontFender;
    private ModelInstance forkLeft;
    private ModelInstance forkRight;
    private ModelInstance handlebar;
    private ModelInstance riderTorso;
    private ModelInstance riderHead;
    private ModelInstance riderLegLeft;
    private ModelInstance riderLegRight;
    private ModelInstance riderArmLeft;
    private ModelInstance riderArmRight;
    private ModelInstance frontNumberPlate;

    private ModelInstance importedBody;
    private ModelInstance importedEngine;
    private ModelInstance importedFrontWheel;
    private ModelInstance importedRearWheel;
    private boolean importedBikeLoaded;

    private float speed;
    private float bikeZ;
    private float bikeX;
    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float yaw;
    private float wheelSpin;
    private float longitudinalAcceleration;
    private float frontNormalLoad;

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
    private boolean frontGrounded = true;
    private boolean crashed;
    private float crashTimer;
    private float wheelieTime;
    private float bestWheelieTime;
    private double physicsAccumulator;

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(30);
        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();

        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.08f;
        camera.far = 260f;
        camera.position.set(0f, 2.5f, -5.4f);
        camera.lookAt(0f, 0.8f, 4f);
        camera.update();

        uiCamera = new OrthographicCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.70f, 0.72f, 0.74f, 1f));
        environment.add(new DirectionalLight().set(0.82f, 0.80f, 0.74f, -0.45f, -1f, -0.28f));

        createWorldModels();
        createBikeModels();
        resetBike();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
    }

    private Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private Model box(ModelBuilder builder, float w, float h, float d, Material material) {
        Model model = builder.createBox(w, h, d, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private Model cylinder(ModelBuilder builder, float w, float h, float d,
                           int divisions, Material material) {
        Model model = builder.createCylinder(w, h, d, divisions, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private Model sphere(ModelBuilder builder, float w, float h, float d,
                         int u, int v, Material material) {
        Model model = builder.createSphere(w, h, d, u, v, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private void createWorldModels() {
        ModelBuilder b = new ModelBuilder();
        Model road = box(b, ROAD_HALF_WIDTH * 2f, 0.08f, SEGMENT_LENGTH, material(0.16f, 0.17f, 0.18f));
        Model ground = box(b, 48f, 0.035f, SEGMENT_LENGTH, material(0.42f, 0.46f, 0.40f));
        Model shoulder = box(b, 0.10f, 0.025f, SEGMENT_LENGTH, material(0.86f, 0.86f, 0.80f));
        Model dash = box(b, 0.10f, 0.025f, 2.8f, material(0.92f, 0.80f, 0.35f));
        Model building = box(b, 4f, 3f, 5f, material(0.34f, 0.39f, 0.40f));

        int n = 16;
        roadSegments = new ModelInstance[n];
        groundSegments = new ModelInstance[n];
        shoulderLeft = new ModelInstance[n];
        shoulderRight = new ModelInstance[n];
        buildings = new ModelInstance[n];
        for (int i = 0; i < n; i++) {
            roadSegments[i] = new ModelInstance(road);
            groundSegments[i] = new ModelInstance(ground);
            shoulderLeft[i] = new ModelInstance(shoulder);
            shoulderRight[i] = new ModelInstance(shoulder);
            buildings[i] = new ModelInstance(building);
        }
        laneDashes = new ModelInstance[52];
        for (int i = 0; i < laneDashes.length; i++) laneDashes[i] = new ModelInstance(dash);
    }

    private void createBikeModels() {
        ModelBuilder b = new ModelBuilder();
        Material tire = material(0.055f, 0.058f, 0.060f);
        Material metal = material(0.58f, 0.61f, 0.61f);
        Material dark = material(0.10f, 0.11f, 0.115f);
        Material orange = material(0.92f, 0.29f, 0.085f);
        Material rider = material(0.07f, 0.075f, 0.08f);
        Material visor = material(0.16f, 0.27f, 0.30f);

        Model wheel = cylinder(b, WHEEL_RADIUS * 2f, 0.13f, WHEEL_RADIUS * 2f, 14, tire);
        Model hub = cylinder(b, 0.19f, 0.145f, 0.19f, 12, metal);
        Model frameM = box(b, 0.20f, 0.22f, 1.00f, dark);
        Model tankM = box(b, 0.50f, 0.36f, 0.58f, orange);
        Model seatM = box(b, 0.42f, 0.12f, 0.48f, dark);
        Model fenderM = box(b, 0.43f, 0.055f, 0.55f, orange);
        Model forkM = box(b, 0.055f, 0.72f, 0.055f, metal);
        Model barM = box(b, 0.74f, 0.045f, 0.045f, dark);
        Model torsoM = box(b, 0.40f, 0.58f, 0.22f, rider);
        Model headM = sphere(b, 0.30f, 0.30f, 0.30f, 10, 8, rider);
        Model limbM = box(b, 0.10f, 0.62f, 0.10f, rider);
        Model plateM = box(b, 0.28f, 0.12f, 0.035f, visor);

        rearWheel = new ModelInstance(wheel);
        frontWheel = new ModelInstance(wheel);
        rearHub = new ModelInstance(hub);
        frontHub = new ModelInstance(hub);
        frame = new ModelInstance(frameM);
        tank = new ModelInstance(tankM);
        seat = new ModelInstance(seatM);
        frontFender = new ModelInstance(fenderM);
        forkLeft = new ModelInstance(forkM);
        forkRight = new ModelInstance(forkM);
        handlebar = new ModelInstance(barM);
        riderTorso = new ModelInstance(torsoM);
        riderHead = new ModelInstance(headM);
        riderLegLeft = new ModelInstance(limbM);
        riderLegRight = new ModelInstance(limbM);
        riderArmLeft = new ModelInstance(limbM);
        riderArmRight = new ModelInstance(limbM);
        frontNumberPlate = new ModelInstance(plateM);

        // Load the optimized DirtBike.blend geometry. The procedural bike remains
        // available as a no-crash fallback if an asset is ever missing or corrupt.
        try {
            ModelInstance[] imported = DirtBikeMeshLoader.load(ownedModels,
                    material(0.08f, 0.52f, 0.12f),
                    material(0.16f, 0.17f, 0.18f),
                    material(0.045f, 0.048f, 0.052f));
            importedBody = imported[0];
            importedEngine = imported[1];
            importedFrontWheel = imported[2];
            importedRearWheel = imported[3];
            importedBikeLoaded = true;
        } catch (Exception e) {
            importedBikeLoaded = false;
            Gdx.app.error("BalancePoint", "Could not load imported dirt bike; using fallback", e);
        }
    }

    @Override
    public void render() {
        float frameDt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        readInput(frameDt);

        physicsAccumulator += frameDt;
        int steps = 0;
        while (physicsAccumulator >= PHYSICS_DT && steps < 8) {
            simulate(PHYSICS_DT);
            physicsAccumulator -= PHYSICS_DT;
            steps++;
        }
        if (steps == 8) physicsAccumulator = 0.0;

        updateWorldInstances();
        updateBikeInstances();
        updateCamera(frameDt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.55f, 0.70f, 0.78f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        for (ModelInstance m : groundSegments) modelBatch.render(m, environment);
        for (ModelInstance m : roadSegments) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderLeft) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderRight) modelBatch.render(m, environment);
        for (ModelInstance m : laneDashes) modelBatch.render(m, environment);
        for (ModelInstance m : buildings) modelBatch.render(m, environment);
        renderBike();
        modelBatch.end();
        drawHud();
    }

    private void readInput(float dt) {
        int w = Math.max(1, Gdx.graphics.getWidth());
        int h = Math.max(1, Gdx.graphics.getHeight());

        boolean cameraTouch = false;
        boolean throttleTouch = false;
        boolean brakeTouch = false;
        boolean leftArrowTouch = false;
        boolean rightArrowTouch = false;
        boolean lookTouch = false;
        float requestedThrottle = 0f;
        float requestedBrake = 0f;

        for (int pointer = 0; pointer < 8; pointer++) {
            if (!Gdx.input.isTouched(pointer)) continue;

            float px = Gdx.input.getX(pointer);
            float py = Gdx.input.getY(pointer);
            float x = px / w;
            float y = py / h;

            if (x > 0.86f && y < 0.18f) {
                cameraTouch = true;
                continue;
            }

            // Right-side vertical throttle slider.
            if (x > 0.82f && y > 0.46f && y < 0.90f) {
                throttleTouch = true;
                requestedThrottle = Math.max(requestedThrottle,
                        MathUtils.clamp((0.88f - y) / 0.40f, 0f, 1f));
                continue;
            }

            // Rear brake sits just left of the throttle for the right thumb.
            if (x >= 0.70f && x <= 0.82f && y > 0.70f) {
                brakeTouch = true;
                float brake = MathUtils.clamp((y - 0.70f) / 0.24f, 0.50f, 1f);
                requestedBrake = Math.max(requestedBrake, brake);
                continue;
            }

            // Left/right steering arrows. Digital touch, analog filtered steering.
            if (x >= 0.03f && x < 0.16f && y > 0.66f && y < 0.91f) {
                leftArrowTouch = true;
                continue;
            }
            if (x >= 0.17f && x < 0.30f && y > 0.66f && y < 0.91f) {
                rightArrowTouch = true;
                continue;
            }

            // Middle-screen drag remains free-look and never feeds steering.
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

        if (cameraTouch && !cameraTouchHeld) cockpitCamera = !cockpitCamera;
        cameraTouchHeld = cameraTouch;
        looking = lookTouch;

        throttleTarget = throttleTouch ? requestedThrottle : 0f;
        rearBrakeTarget = brakeTouch ? requestedBrake : 0f;

        if (leftArrowTouch == rightArrowTouch) {
            steerTarget = 0f;
        } else {
            // Positive steer is screen-left in the current motorcycle model.
            steerTarget = leftArrowTouch ? 0.65f : -0.65f;
        }

        // Fore/aft rider lean is intentionally disabled.
        riderLean = 0f;
        riderLeanTarget = 0f;

        throttle = approach(throttle, throttleTarget,
                (throttleTarget > throttle ? 7.0f : 11f) * dt);
        rearBrake = approach(rearBrake, rearBrakeTarget,
                (rearBrakeTarget > rearBrake ? 24f : 22f) * dt);

        // Digital arrows feed a damped analog steering state.
        float steerResponse = 1.65f + Math.min(speed * 0.018f, 0.55f);
        steer += (steerTarget - steer) * Math.min(1f, dt * steerResponse);

        if (!looking) {
            lookYaw *= Math.max(0f, 1f - dt * 1.65f);
            lookPitch *= Math.max(0f, 1f - dt * 1.65f);
        }
    }

    private static float approach(float value, float target, float amount) {
        if (value < target) return Math.min(value + amount, target);
        return Math.max(value - amount, target);
    }

    private void simulate(float dt) {
        if (crashed) {
            crashTimer += dt;
            throttle = 0f;
            rearBrake = 0f;
            speed = Math.max(0f, speed - 7f * dt);
            pitchVelocity += 2.2f * dt;
            pitch += pitchVelocity * dt;
            roll += 1.35f * dt;
            bikeX += MathUtils.sin(yaw) * speed * dt;
            bikeZ += MathUtils.cos(yaw) * speed * dt;
            if (crashTimer > 1.35f) resetBike();
            return;
        }

        float absSpeed = Math.abs(speed);
        boolean offRoad = Math.abs(bikeX) > ROAD_HALF_WIDTH;
        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT, 0.48f, 0.82f);

        float rearNormalEstimate;
        if (frontGrounded) {
            rearNormalEstimate = MASS * GRAVITY * (WHEELBASE - effectiveComForward) / WHEELBASE
                    + MASS * longitudinalAcceleration * COM_HEIGHT / WHEELBASE;
        } else {
            rearNormalEstimate = MASS * GRAVITY;
        }
        rearNormalEstimate = Math.max(0f, rearNormalEstimate);
        float tractionLimit = TIRE_MU * rearNormalEstimate;

        float powerLimitedForce = ENGINE_POWER / Math.max(absSpeed, 6f);
        float driveForce = Math.min(throttle * Math.min(MAX_ENGINE_FORCE, powerLimitedForce), tractionLimit);

        float brakeForce = 0f;
        if (rearBrake > 0f && absSpeed > 0.03f) {
            brakeForce = Math.min(MAX_REAR_BRAKE_FORCE * rearBrake, tractionLimit * 0.98f);
            brakeForce = Math.min(brakeForce, absSpeed * MASS / Math.max(dt, 0.001f));
        }

        float rolling = absSpeed > 0.02f ? MASS * GRAVITY * ROLLING_RESISTANCE : 0f;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = offRoad ? 95f + absSpeed * 5f : 0f;
        longitudinalAcceleration = (driveForce - brakeForce - rolling - aero - surfaceDrag) / MASS;

        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);

        frontNormalLoad = (MASS * GRAVITY * effectiveComForward
                - MASS * longitudinalAcceleration * COM_HEIGHT) / WHEELBASE;

        if (frontGrounded) {
            pitch = 0f;
            pitchVelocity = 0f;
            if (frontNormalLoad <= 0f && speed > 2.5f) {
                frontGrounded = false;
                frontNormalLoad = 0f;
                pitchVelocity = 0.035f;
            }
        } else {
            float sinPitch = MathUtils.sin(pitch);
            float cosPitch = MathUtils.cos(pitch);
            float comWorldForward = effectiveComForward * cosPitch - COM_HEIGHT * sinPitch;
            float comWorldHeight = effectiveComForward * sinPitch + COM_HEIGHT * cosPitch;
            float balanceAngle = (float) Math.atan2(effectiveComForward, COM_HEIGHT);
            float balanceDistance = Math.abs(pitch - balanceAngle);
            float proximityLinear = 1f - MathUtils.clamp(balanceDistance / BALANCE_DAMPING_BAND, 0f, 1f);
            float proximity = proximityLinear * proximityLinear;

            // Slow, deliberate corrections receive almost no assist. Damping ramps
            // in only when pitch rate gets large near the balance region.
            float rateMagnitude = Math.abs(pitchVelocity);
            float rateAssist = MathUtils.clamp(
                    (rateMagnitude - BALANCE_FREE_RATE) / (BALANCE_FULL_RATE - BALANCE_FREE_RATE),
                    0f, 1f);
            rateAssist *= rateAssist;
            float assist = proximity * rateAssist;
            float angularDamping = PITCH_DAMPING_BASE + assist * PITCH_DAMPING_BALANCE;

            float pitchTorque = MASS * longitudinalAcceleration * comWorldHeight
                    - MASS * GRAVITY * comWorldForward
                    - pitchVelocity * angularDamping;

            pitchVelocity += (pitchTorque / PITCH_INERTIA) * dt;

            // Progressive safety net rather than an auto-balance system: normal
            // balance-point movement stays in the rider's hands, while fast pitch
            // excursions are softened enough to remain recoverable.
            float rateDecay = 0.45f + assist * BALANCE_RATE_DECAY;
            pitchVelocity *= (float) Math.exp(-rateDecay * dt);
            pitchVelocity = MathUtils.clamp(pitchVelocity, -1.90f, 1.90f);
            pitch += pitchVelocity * dt;

            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                frontGrounded = true;
            }
        }

        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);
        float maxSteerDeg = MathUtils.lerp(16f, 4.5f, speedBlend);
        float steerAngle = steer * maxSteerDeg * MathUtils.degreesToRadians;
        float steeringAuthority = frontGrounded ? 1f : 0.14f;
        float yawRate = speed > 0.35f
                ? (speed / WHEELBASE) * (float) Math.tan(steerAngle) * steeringAuthority
                : 0f;
        yaw += yawRate * dt;
        if (yaw > MathUtils.PI) yaw -= MathUtils.PI2;
        if (yaw < -MathUtils.PI) yaw += MathUtils.PI2;

        float lateralAcceleration = speed * yawRate;
        float rollTarget = -(float) Math.atan2(lateralAcceleration, GRAVITY);
        rollTarget = MathUtils.clamp(rollTarget,
                -46f * MathUtils.degreesToRadians, 46f * MathUtils.degreesToRadians);
        float rollResponse = frontGrounded ? 3.0f : 1.65f;
        roll += (rollTarget - roll) * Math.min(1f, dt * rollResponse);

        bikeX += MathUtils.sin(yaw) * speed * dt;
        bikeZ += MathUtils.cos(yaw) * speed * dt;

        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        if (!frontGrounded && pitch > 8f * MathUtils.degreesToRadians && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontGrounded) {
            wheelieTime = 0f;
        }

        if (pitch > LOOP_ANGLE || Math.abs(bikeX) > ROAD_HALF_WIDTH + 10f
                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)) {
            crashed = true;
            crashTimer = 0f;
        }
    }

    private void resetBike() {
        speed = 0f;
        bikeX = 0f;
        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        yaw = 0f;
        wheelSpin = 0f;
        longitudinalAcceleration = 0f;
        frontNormalLoad = MASS * GRAVITY * COM_FORWARD / WHEELBASE;
        throttle = throttleTarget = 0f;
        rearBrake = rearBrakeTarget = 0f;
        steer = steerTarget = 0f;
        riderLean = riderLeanTarget = 0f;
        lookYaw = lookPitch = 0f;
        frontGrounded = true;
        crashed = false;
        crashTimer = 0f;
        wheelieTime = 0f;
        physicsAccumulator = 0.0;
    }

    private void updateWorldInstances() {
        float firstCenter = (float) Math.floor((bikeZ - 70f) / SEGMENT_LENGTH) * SEGMENT_LENGTH
                + SEGMENT_LENGTH * 0.5f;
        for (int i = 0; i < roadSegments.length; i++) {
            float z = firstCenter + i * SEGMENT_LENGTH;
            int wi = (int) Math.floor(z / SEGMENT_LENGTH);
            groundSegments[i].transform.setToTranslation(0f, -0.075f, z);
            roadSegments[i].transform.setToTranslation(0f, -0.025f, z);
            shoulderLeft[i].transform.setToTranslation(-ROAD_HALF_WIDTH + 0.18f, 0.026f, z);
            shoulderRight[i].transform.setToTranslation(ROAD_HALF_WIDTH - 0.18f, 0.026f, z);

            float side = (wi & 1) == 0 ? 1f : -1f;
            float sx = 0.78f + positiveMod(wi * 17, 5) * 0.08f;
            float sy = 0.70f + positiveMod(wi * 13, 7) * 0.10f;
            float sz = 0.82f + positiveMod(wi * 7, 4) * 0.12f;
            float bx = side * (8.5f + positiveMod(wi * 11, 5));
            float bz = z + (positiveMod(wi * 5, 7) - 3) * 0.85f;
            buildings[i].transform.setToTranslation(bx, 1.5f * sy, bz).scale(sx, sy, sz);
        }

        float dashStart = (float) Math.floor((bikeZ - 35f) / 6f) * 6f;
        for (int i = 0; i < laneDashes.length; i++)
            laneDashes[i].transform.setToTranslation(0f, 0.028f, dashStart + i * 6f);
    }

    private static int positiveMod(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    private void updateBikeInstances() {
        float pitchDeg = pitch * MathUtils.radiansToDegrees;
        float rollDeg = roll * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;
        bikeRoot.idt().translate(bikeX, WHEEL_RADIUS, bikeZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg)
                .rotate(Vector3.X, -pitchDeg);

        float spinDeg = -wheelSpin * MathUtils.radiansToDegrees;
        setWheel(rearWheel, 0f, 0f, 0f, spinDeg);
        setWheel(frontWheel, 0f, 0f, WHEELBASE, spinDeg);
        setWheel(rearHub, 0f, 0f, 0f, spinDeg);
        setWheel(frontHub, 0f, 0f, WHEELBASE, spinDeg);

        setPart(frame, 0f, 0.34f, 0.68f);
        setPart(tank, 0f, 0.61f, 0.81f); tank.transform.rotate(Vector3.X, -7f);
        setPart(seat, 0f, 0.68f, 0.33f);
        setPart(frontFender, 0f, 0.32f, 1.34f);
        setPart(forkLeft, -0.17f, 0.37f, 1.18f);
        setPart(forkRight, 0.17f, 0.37f, 1.18f);
        forkLeft.transform.rotate(Vector3.X, 10f);
        forkRight.transform.rotate(Vector3.X, 10f);
        setPart(handlebar, 0f, 0.91f, 1.04f);
        setPart(frontNumberPlate, 0f, 0.72f, 1.19f);
        frontNumberPlate.transform.rotate(Vector3.X, 10f);

        // Rider visibly follows the joystick's fore-aft weight input.
        float riderZ = 0.43f + riderLean * 0.10f;
        setPart(riderTorso, 0f, 1.07f, riderZ);
        riderTorso.transform.rotate(Vector3.X, -11f - riderLean * 9f);
        setPart(riderHead, 0f, 1.48f, 0.55f + riderLean * 0.11f);
        setPart(riderLegLeft, -0.15f, 0.57f, 0.40f);
        setPart(riderLegRight, 0.15f, 0.57f, 0.40f);
        riderLegLeft.transform.rotate(Vector3.X, 33f);
        riderLegRight.transform.rotate(Vector3.X, 33f);
        setPart(riderArmLeft, -0.20f, 0.97f, 0.82f);
        setPart(riderArmRight, 0.20f, 0.97f, 0.82f);
        riderArmLeft.transform.rotate(Vector3.X, 69f);
        riderArmRight.transform.rotate(Vector3.X, 69f);

        if (importedBikeLoaded) {
            importedBody.transform.set(bikeRoot);
            importedEngine.transform.set(bikeRoot);

            float importedSpinDeg = -wheelSpin * MathUtils.radiansToDegrees;
            importedRearWheel.transform.set(bikeRoot)
                    .rotate(Vector3.X, importedSpinDeg);

            float visualSteerDeg = -steer * MathUtils.lerp(13f, 5f,
                    MathUtils.clamp(speed / 30f, 0f, 1f));
            importedFrontWheel.transform.set(bikeRoot)
                    .translate(0f, 0f, WHEELBASE)
                    .rotate(Vector3.Y, visualSteerDeg)
                    .rotate(Vector3.X, importedSpinDeg);
        }
    }

    private void setPart(ModelInstance m, float x, float y, float z) {
        m.transform.set(bikeRoot).translate(x, y, z);
    }

    private void setWheel(ModelInstance m, float x, float y, float z, float spinDeg) {
        m.transform.set(bikeRoot).translate(x, y, z)
                .rotate(Vector3.Z, 90f).rotate(Vector3.Y, spinDeg);
    }

    private void updateCamera(float dt) {
        float viewYaw = yaw + lookYaw;
        float sinView = MathUtils.sin(viewYaw);
        float cosView = MathUtils.cos(viewYaw);

        if (cockpitCamera) {
            // Camera position rides with the helmet, but orientation is referenced to
            // world-up. Wheelie pitch and chassis roll no longer tilt the horizon.
            tempA.set(0f, 1.42f, 0.58f + riderLean * 0.10f).mul(bikeRoot);
            camera.position.set(tempA);
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(sinView * cp, MathUtils.sin(lookPitch), cosView * cp).nor();
            camera.up.set(Vector3.Y);
        } else {
            float orbitPitch = MathUtils.clamp(lookPitch, -25f * MathUtils.degreesToRadians,
                    30f * MathUtils.degreesToRadians);
            float horizontalDistance = 5.7f * MathUtils.cos(orbitPitch);
            float desiredHeight = 2.65f + 5.7f * MathUtils.sin(orbitPitch);
            tempA.set(bikeX - sinView * horizontalDistance,
                    Math.max(0.9f, desiredHeight),
                    bikeZ - cosView * horizontalDistance);
            float response = 1f - (float) Math.exp(-3.8f * dt);
            camera.position.lerp(tempA, response);
            tempB.set(bikeX + MathUtils.sin(yaw) * 2.2f,
                    0.92f + MathUtils.sin(pitch) * 0.52f,
                    bikeZ + MathUtils.cos(yaw) * 2.2f);
            camera.up.set(Vector3.Y);
            camera.lookAt(tempB);
        }
        camera.update();
    }

    private void renderBike() {
        if (importedBikeLoaded) {
            modelBatch.render(importedBody, environment);
            modelBatch.render(importedEngine, environment);
            modelBatch.render(importedRearWheel, environment);
            modelBatch.render(importedFrontWheel, environment);

            // Keep the lightweight existing rider and steering hardware for this
            // first import pass; the source model has these as separate pieces too.
            modelBatch.render(forkLeft, environment); modelBatch.render(forkRight, environment);
            modelBatch.render(handlebar, environment);
            modelBatch.render(riderTorso, environment); modelBatch.render(riderHead, environment);
            modelBatch.render(riderLegLeft, environment); modelBatch.render(riderLegRight, environment);
            modelBatch.render(riderArmLeft, environment); modelBatch.render(riderArmRight, environment);
            return;
        }

        modelBatch.render(rearWheel, environment); modelBatch.render(frontWheel, environment);
        modelBatch.render(rearHub, environment); modelBatch.render(frontHub, environment);
        modelBatch.render(frame, environment); modelBatch.render(tank, environment);
        modelBatch.render(seat, environment); modelBatch.render(frontFender, environment);
        modelBatch.render(forkLeft, environment); modelBatch.render(forkRight, environment);
        modelBatch.render(handlebar, environment); modelBatch.render(frontNumberPlate, environment);
        modelBatch.render(riderTorso, environment); modelBatch.render(riderHead, environment);
        modelBatch.render(riderLegLeft, environment); modelBatch.render(riderLegRight, environment);
        modelBatch.render(riderArmLeft, environment); modelBatch.render(riderArmRight, environment);
    }

    private void drawHud() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        float min = Math.min(w, h);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(18f, h - 142f, 300f, 120f);

        // Right-side throttle slider.
        float sliderX = w * 0.91f;
        float sliderBottom = h * 0.12f;
        float sliderTop = h * 0.52f;
        shapes.setColor(1f, 1f, 1f, 0.14f);
        shapes.rect(sliderX - 16f, sliderBottom, 32f, sliderTop - sliderBottom);
        float knobY = sliderBottom + throttleTarget * (sliderTop - sliderBottom);
        shapes.setColor(1f, 1f, 1f, 0.48f);
        shapes.circle(sliderX, knobY, min * 0.045f, 20);

        // Rear brake beside throttle.
        float brakeX = w * 0.755f;
        float brakeY = h * 0.17f;
        shapes.setColor(1f, 1f, 1f, rearBrakeTarget > 0f ? 0.50f : 0.16f);
        shapes.circle(brakeX, brakeY, min * 0.065f, 24);

        // Left-side steering arrows.
        float steerLeftX = w * 0.095f;
        float steerRightX = w * 0.235f;
        float steerY = h * 0.18f;
        float steerR = min * 0.072f;

        shapes.setColor(1f, 1f, 1f, steerTarget > 0.05f ? 0.46f : 0.14f);
        shapes.circle(steerLeftX, steerY, steerR, 24);
        shapes.setColor(1f, 1f, 1f, 0.78f);
        shapes.triangle(steerLeftX - steerR * 0.38f, steerY,
                steerLeftX + steerR * 0.26f, steerY + steerR * 0.42f,
                steerLeftX + steerR * 0.26f, steerY - steerR * 0.42f);

        shapes.setColor(1f, 1f, 1f, steerTarget < -0.05f ? 0.46f : 0.14f);
        shapes.circle(steerRightX, steerY, steerR, 24);
        shapes.setColor(1f, 1f, 1f, 0.78f);
        shapes.triangle(steerRightX + steerR * 0.38f, steerY,
                steerRightX - steerR * 0.26f, steerY + steerR * 0.42f,
                steerRightX - steerR * 0.26f, steerY - steerR * 0.42f);

        shapes.setColor(0f, 0f, 0f, 0.42f);
        shapes.rect(w - 145f, h - 66f, 125f, 45f);
        if (looking) {
            shapes.setColor(1f, 1f, 1f, 0.09f);
            shapes.rect(w * 0.30f, h * 0.34f, w * 0.40f, h * 0.32f);
        }
        if (crashed) {
            shapes.setColor(0f, 0f, 0f, 0.55f);
            shapes.rect(0f, 0f, w, h);
        }
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(Math.max(0.85f, h / 720f * 1.15f));

        float mph = speed * 2.23694f;
        float angle = pitch * MathUtils.radiansToDegrees;
        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT, 0.48f, 0.82f);
        float staticFrontLoad = MASS * GRAVITY * effectiveComForward / WHEELBASE;
        float frontPercent = staticFrontLoad > 0f
                ? MathUtils.clamp(frontNormalLoad / staticFrontLoad * 100f, 0f, 130f) : 0f;

        font.draw(spriteBatch, String.format(java.util.Locale.US, "%03.0f MPH", mph), 34f, h - 44f);
        font.draw(spriteBatch, String.format(java.util.Locale.US, "ANGLE %3.0f deg", angle), 34f, h - 69f);
        font.draw(spriteBatch, String.format(java.util.Locale.US, "WHEELIE %.1fs  BEST %.1fs", wheelieTime, bestWheelieTime), 34f, h - 94f);
        font.draw(spriteBatch, frontGrounded
                ? String.format(java.util.Locale.US, "FRONT LOAD %3.0f%%", frontPercent)
                : "FRONT AIR", 34f, h - 119f);
        font.draw(spriteBatch, "FPS " + Gdx.graphics.getFramesPerSecond(), 225f, h - 44f);

        font.draw(spriteBatch, "THROTTLE", sliderX - 31f, sliderTop + 22f);
        font.draw(spriteBatch, "BRAKE", brakeX - 23f, brakeY + 5f);
        font.draw(spriteBatch, "STEER", w * 0.145f, steerY + steerR + 20f);
        font.draw(spriteBatch, "DRAG CENTER TO LOOK", w * 0.405f, h * 0.62f);
        font.draw(spriteBatch, cockpitCamera ? "CAM: HELMET" : "CAM: CHASE", w - 133f, h - 39f);

        if (crashed) {
            font.getData().setScale(Math.max(1.6f, h / 720f * 2.2f));
            font.draw(spriteBatch, "LOOPED IT", w * 0.40f, h * 0.54f);
        }
        spriteBatch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.viewportWidth = Math.max(1, width);
            camera.viewportHeight = Math.max(1, height);
            camera.update();
        }
        if (uiCamera != null) {
            uiCamera.setToOrtho(false, Math.max(1, width), Math.max(1, height));
            uiCamera.update();
        }
    }

    @Override
    public void resume() {
        physicsAccumulator = 0.0;
    }

    @Override
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
    }
}
