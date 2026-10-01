package com.nyerahworks.balancepoint;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Balance Point - lightweight 3D motorcycle prototype.
 */
public final class BalancePointGame extends ApplicationAdapter {
    private static final float PHYSICS_DT = 1f / 120f;
    private static final float SEGMENT_LENGTH = 20f;
    private static final float ROAD_HALF_WIDTH = 4.2f;

    private static final float WHEELBASE = 1.45f;
    private static final float WHEEL_RADIUS = 0.31f;
    private static final float MASS = 212f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA = 168f;
    private static final float COM_FORWARD = 0.60f;
    private static final float AIRBORNE_COM_FORWARD = 1.00f;
    private static final float AIRBORNE_COM_SHIFT_START = 25f * MathUtils.degreesToRadians;
    private static final float AIRBORNE_COM_SHIFT_END = 55f * MathUtils.degreesToRadians;
    private static final float COM_HEIGHT = 0.70f;
    private static final float RIDER_SHIFT = 0.14f;

    private static final float TIRE_MU = 1.05f;
    private static final float MAX_ENGINE_FORCE = 2500f;
    private static final float MAX_REAR_BRAKE_FORCE = 3800f;
    private static final float ROLLING_RESISTANCE = 0.017f;
    private static final float AERO_DRAG = 0.34f;

    private static final float PITCH_DAMPING = 30f;
    private static final float MAX_PITCH_RATE = 5.4f;
    private static final float LIFT_SEED_RATE = 0.08f;
    private static final float LIFT_MARGIN_FOR_FULL_SEED = 0.25f;
    private static final float THROTTLE_SNAP_RATE = 12f;
    private static final float THROTTLE_SNAP_IMPULSE = 0.52f;
    private static final float LOOP_ANGLE = 155f * MathUtils.degreesToRadians;
    private static final float IMPORTED_RIDER_SCALE = 0.82f;

    private final Array<Model> ownedModels = new Array<>();
    private final Matrix4 bikeRoot = new Matrix4();
    private final Matrix4 importedSteeringRoot = new Matrix4();
    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();
    private final MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);

    private PerspectiveCamera camera;
    private OrthographicCamera uiCamera;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Pixmap instrumentPixmap;
    private Texture instrumentTexture;
    private Environment environment;
    private EngineAudio engineAudio;

    private TerrainVisuals terrainVisuals;
    private ModelInstance[] roadSegments;
    private ModelInstance[] shoulderLeft;
    private ModelInstance[] shoulderRight;
    private ModelInstance[] laneDashes;

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
    private ModelInstance instrumentPanel;
    private ModelInstance instrumentScreen;
    private ModelInstance instrumentButtonLeft;
    private ModelInstance instrumentButtonRight;

    private DirtBikeMeshLoader.LoadedBike importedBike;
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

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(30);
        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        engineAudio = new EngineAudio();

        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.08f;
        camera.far = 520f;
        camera.position.set(0f, 2.5f, -5.4f);
        camera.lookAt(0f, 0.8f, 4f);
        camera.update();

        uiCamera = new OrthographicCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.65f, 0.63f, 1f));
        environment.add(new DirectionalLight().set(0.95f, 0.88f, 0.76f, -0.45f, -1f, -0.28f));

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
        Model road = box(b, ROAD_HALF_WIDTH * 2f, 0.08f, SEGMENT_LENGTH,
                material(0.13f, 0.14f, 0.145f));
        Model edgeLine = box(b, 0.10f, 0.025f, SEGMENT_LENGTH,
                material(0.82f, 0.80f, 0.72f));
        Model dash = box(b, 0.10f, 0.025f, 2.8f,
                material(0.90f, 0.74f, 0.26f));

        int n = 18;
        roadSegments = new ModelInstance[n];
        shoulderLeft = new ModelInstance[n];
        shoulderRight = new ModelInstance[n];
        for (int i = 0; i < n; i++) {
            roadSegments[i] = new ModelInstance(road);
            shoulderLeft[i] = new ModelInstance(edgeLine);
            shoulderRight[i] = new ModelInstance(edgeLine);
        }
        laneDashes = new ModelInstance[58];
        for (int i = 0; i < laneDashes.length; i++) laneDashes[i] = new ModelInstance(dash);
        terrainVisuals = new TerrainVisuals(ownedModels);
    }

    private void createBikeModels() {
        ModelBuilder b = new ModelBuilder();
        Material tire = material(0.055f, 0.058f, 0.060f);
        Material metal = material(0.58f, 0.61f, 0.61f);
        Material dark = material(0.10f, 0.11f, 0.115f);
        Material orange = material(0.92f, 0.29f, 0.085f);
        Material rider = material(0.07f, 0.075f, 0.08f);
        Material visor = material(0.16f, 0.27f, 0.30f);
        Material dashMat = material(0.030f, 0.034f, 0.035f);
        Material dashButtonMat = material(0.070f, 0.075f, 0.074f);

        instrumentPixmap = new Pixmap(192, 96, Pixmap.Format.RGBA8888);
        instrumentPixmap.setColor(0.008f, 0.014f, 0.013f, 1f);
        instrumentPixmap.fill();
        instrumentTexture = new Texture(instrumentPixmap);
        instrumentTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        TextureAttribute dashTextureAttribute = TextureAttribute.createDiffuse(instrumentTexture);
        // The world-space quad is upright now; only mirror U so the LCD reads left-to-right.
        dashTextureAttribute.offsetU = 1f;
        dashTextureAttribute.scaleU = -1f;
        Material dashScreenMat = new Material(dashTextureAttribute,
                ColorAttribute.createDiffuse(Color.WHITE));
        // The UV-corrected quad faces the opposite winding from the generated bike geometry.
        // Keep the LCD double-sided so global back-face culling cannot make it disappear.
        dashScreenMat.set(IntAttribute.createCullFace(GL20.GL_NONE));

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
        // Compact trail-computer-sized housing instead of the oversized prototype box.
        Model dashM = box(b, 0.145f, 0.022f, 0.082f, dashMat);
        Model dashButtonM = box(b, 0.016f, 0.006f, 0.011f, dashButtonMat);
        // UV orientation matters here: createRect maps its first edge to texture U.
        // Run that edge across the handlebars (X), not fore/aft (Z), so the LCD
        // texture is physically landscape on the dashboard instead of rotated 90 deg.
        Model dashScreenM = b.createRect(
                -0.060f, 0f, -0.024f,
                 0.060f, 0f, -0.024f,
                 0.060f, 0f,  0.029f,
                -0.060f, 0f,  0.029f,
                 0f, 1f, 0f, dashScreenMat,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                        | VertexAttributes.Usage.TextureCoordinates);
        ownedModels.add(dashScreenM);

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
        instrumentPanel = new ModelInstance(dashM);
        instrumentScreen = new ModelInstance(dashScreenM);
        instrumentButtonLeft = new ModelInstance(dashButtonM);
        instrumentButtonRight = new ModelInstance(dashButtonM);

        try {
            importedBike = DirtBikeMeshLoader.load(ownedModels,
                    material(0.08f, 0.52f, 0.12f),
                    material(0.16f, 0.17f, 0.18f),
                    material(0.045f, 0.048f, 0.052f));
            importedBikeLoaded = true;
        } catch (Exception e) {
            importedBike = null;
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

        if (engineAudio != null) {
            engineAudio.update(drivetrain.getRpm(), throttle, drivetrain.isShifting(), crashed);
        }

        updateWorldInstances();
        updateBikeInstances();
        updateInstrumentTexture();
        updateCamera(frameDt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.58f, 0.72f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        terrainVisuals.render(modelBatch, environment);
        for (ModelInstance m : roadSegments) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderLeft) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderRight) modelBatch.render(m, environment);
        for (ModelInstance m : laneDashes) modelBatch.render(m, environment);
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
        boolean shiftDownTouch = false;
        boolean shiftUpTouch = false;
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
            if (x > 0.82f && y > 0.46f && y < 0.90f) {
                throttleTouch = true;
                requestedThrottle = Math.max(requestedThrottle,
                        MathUtils.clamp((0.88f - y) / 0.40f, 0f, 1f));
                continue;
            }
            if (x >= 0.70f && x <= 0.82f && y > 0.70f) {
                brakeTouch = true;
                float brake = MathUtils.clamp((y - 0.70f) / 0.24f, 0.50f, 1f);
                requestedBrake = Math.max(requestedBrake, brake);
                continue;
            }
            if (x >= 0.03f && x < 0.16f && y > 0.66f && y < 0.91f) {
                leftArrowTouch = true;
                continue;
            }
            if (x >= 0.17f && x < 0.30f && y > 0.66f && y < 0.91f) {
                rightArrowTouch = true;
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

        if (crashed || leftArrowTouch == rightArrowTouch) {
            steerTarget = 0f;
        } else {
            steerTarget = leftArrowTouch ? 1.0f : -1.0f;
        }

        riderLean = 0f;
        riderLeanTarget = 0f;
        throttle = approach(throttle, throttleTarget,
                (throttleTarget > throttle ? 10.5f : 12f) * dt);
        rearBrake = approach(rearBrake, rearBrakeTarget,
                (rearBrakeTarget > rearBrake ? 24f : 22f) * dt);

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
            simulateCrash(dt);
            return;
        }

        float absSpeed = Math.abs(speed);
        boolean offRoad = Math.abs(bikeX) > ROAD_HALF_WIDTH;
        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;
        float terrainSlopeMagnitude = (float) Math.sqrt(terrainSlopeX * terrainSlopeX
                + terrainSlopeZ * terrainSlopeZ);
        float terrainNormalScale = 1f / (float) Math.sqrt(1f
                + terrainSlopeMagnitude * terrainSlopeMagnitude);
        float forwardGrade = terrainSlopeX * MathUtils.sin(yaw)
                + terrainSlopeZ * MathUtils.cos(yaw);
        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT, 0.48f, 0.82f);

        float rearNormalEstimate;
        if (frontGrounded) {
            rearNormalEstimate = MASS * GRAVITY * (WHEELBASE - effectiveComForward) / WHEELBASE
                    + MASS * longitudinalAcceleration * COM_HEIGHT / WHEELBASE;
        } else {
            rearNormalEstimate = MASS * GRAVITY;
        }
        rearNormalEstimate = Math.max(0f, rearNormalEstimate) * terrainNormalScale;
        float tractionLimit = TIRE_MU * rearNormalEstimate;

        float drivetrainForce = drivetrain.update(absSpeed, throttle, dt);
        float driveForce = Math.min(Math.min(MAX_ENGINE_FORCE, drivetrainForce), tractionLimit);

        float brakeForce = 0f;
        if (rearBrake > 0f && absSpeed > 0.03f) {
            brakeForce = Math.min(MAX_REAR_BRAKE_FORCE * rearBrake, tractionLimit * 0.98f);
            brakeForce = Math.min(brakeForce, absSpeed * MASS / Math.max(dt, 0.001f));
        }

        float rolling = absSpeed > 0.02f ? MASS * GRAVITY * ROLLING_RESISTANCE : 0f;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = offRoad ? 95f + absSpeed * 5f : 0f;
        float gradeAngle = (float) Math.atan(forwardGrade);
        float gradeAcceleration = GRAVITY * MathUtils.sin(gradeAngle);
        longitudinalAcceleration = (driveForce - brakeForce - rolling - aero - surfaceDrag) / MASS
                - gradeAcceleration;

        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);

        float staticFrontLoad = MASS * GRAVITY * effectiveComForward / WHEELBASE;
        frontNormalLoad = (MASS * GRAVITY * effectiveComForward
                - MASS * longitudinalAcceleration * COM_HEIGHT) / WHEELBASE;

        if (frontGrounded) {
            pitch = 0f;
            pitchVelocity = 0f;
            if (frontNormalLoad <= 0f && speed > 2.5f) {
                float liftMargin = MathUtils.clamp(-frontNormalLoad
                        / Math.max(staticFrontLoad * LIFT_MARGIN_FOR_FULL_SEED, 1f), 0f, 1f);
                float liftAuthority = liftMargin * liftMargin * (3f - 2f * liftMargin);
                frontGrounded = false;
                frontNormalLoad = 0f;
                pitchVelocity = (LIFT_SEED_RATE + throttleSnap * THROTTLE_SNAP_IMPULSE)
                        * liftAuthority;
                throttleSnap = 0f;
            }
        } else {
            float sinPitch = MathUtils.sin(pitch);
            float cosPitch = MathUtils.cos(pitch);
            float airborneComBlend = MathUtils.clamp((pitch - AIRBORNE_COM_SHIFT_START)
                    / (AIRBORNE_COM_SHIFT_END - AIRBORNE_COM_SHIFT_START), 0f, 1f);
            airborneComBlend = airborneComBlend * airborneComBlend * (3f - 2f * airborneComBlend);
            float airborneComForward = MathUtils.lerp(COM_FORWARD, AIRBORNE_COM_FORWARD,
                    airborneComBlend) + riderLean * RIDER_SHIFT;
            float comWorldForward = airborneComForward * cosPitch - COM_HEIGHT * sinPitch;
            float comWorldHeight = airborneComForward * sinPitch + COM_HEIGHT * cosPitch;
            float pitchTorque = MASS * longitudinalAcceleration * comWorldHeight
                    - MASS * GRAVITY * comWorldForward
                    - pitchVelocity * PITCH_DAMPING;

            pitchVelocity += (pitchTorque / PITCH_INERTIA) * dt;
            pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
            pitch += pitchVelocity * dt;

            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                frontGrounded = true;
            }
        }

        // Dirt-bike steering needs real low-speed lock for switchbacks/U-turns, while
        // high-speed steering must remain small enough to stay controllable.
        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);
        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);
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
                -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);
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

        // Leaving the road is not a crash. The old lateral boundary check made any
        // committed turn or U-turn eventually trigger an artificial game-over.
        if (pitch > LOOP_ANGLE
                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)
                || Float.isNaN(roll) || Float.isNaN(bikeX) || Float.isNaN(bikeZ)) {
            beginCrash();
        }
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
        bikeX = 0f;
        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        yaw = 0f;
        wheelSpin = 0f;
        longitudinalAcceleration = 0f;
        frontNormalLoad = MASS * GRAVITY * COM_FORWARD / WHEELBASE;
        throttle = throttleTarget = previousThrottleTarget = 0f;
        throttleSnap = 0f;
        rearBrake = rearBrakeTarget = 0f;
        steer = steerTarget = 0f;
        riderLean = riderLeanTarget = 0f;
        lookYaw = lookPitch = 0f;
        shiftDownHeld = shiftUpHeld = false;
        drivetrain.reset();
        frontGrounded = true;
        crashed = false;
        crashSettled = false;
        crashTimer = 0f;
        crashPitchRate = crashRollRate = crashYawRate = 0f;
        crashSide = 1f;
        wheelieTime = 0f;
        physicsAccumulator = 0.0;
    }

    private void updateWorldInstances() {
        float firstCenter = (float) Math.floor((bikeZ - 80f) / SEGMENT_LENGTH) * SEGMENT_LENGTH
                + SEGMENT_LENGTH * 0.5f;
        for (int i = 0; i < roadSegments.length; i++) {
            float z = firstCenter + i * SEGMENT_LENGTH;
            roadSegments[i].transform.setToTranslation(0f, -0.025f, z);
            shoulderLeft[i].transform.setToTranslation(-ROAD_HALF_WIDTH + 0.18f, 0.026f, z);
            shoulderRight[i].transform.setToTranslation(ROAD_HALF_WIDTH - 0.18f, 0.026f, z);
        }
        terrainVisuals.update(bikeZ);
        float dashStart = (float) Math.floor((bikeZ - 42f) / 6f) * 6f;
        for (int i = 0; i < laneDashes.length; i++)
            laneDashes[i].transform.setToTranslation(0f, 0.028f, dashStart + i * 6f);
    }

    private void updateBikeInstances() {
        float pitchDeg = pitch * MathUtils.radiansToDegrees;
        float rollDeg = roll * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;

        float terrainHeight = terrainVisuals != null ? terrainVisuals.groundHeight(bikeX, bikeZ) : 0f;
        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;
        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float terrainForwardGrade = terrainSlopeX * sinYaw + terrainSlopeZ * cosYaw;
        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;
        float terrainPitchDeg = (float) Math.atan(terrainForwardGrade) * MathUtils.radiansToDegrees;
        float terrainRollDeg = (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;

        float rootHeight = terrainHeight + (crashed && crashSettled ? 0.22f : WHEEL_RADIUS);
        bikeRoot.idt().translate(bikeX, rootHeight, bikeZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
                .rotate(Vector3.X, -(pitchDeg + terrainPitchDeg));

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
        // The display is actual bike geometry. Everything below inherits the same local
        // transform, so camera movement changes perspective instead of sliding a 2D overlay.
        setPart(instrumentPanel, 0f, 0.915f, 0.985f);
        instrumentPanel.transform.rotate(Vector3.X, -22f);
        instrumentScreen.transform.set(instrumentPanel.transform)
                .translate(0f, 0.0117f, 0.004f);
        instrumentButtonLeft.transform.set(instrumentPanel.transform)
                .translate(-0.041f, 0.0143f, -0.032f);
        instrumentButtonRight.transform.set(instrumentPanel.transform)
                .translate(0.041f, 0.0143f, -0.032f);

        if (importedBikeLoaded) {
            setPart(riderTorso, 0f, 0.94f, 0.49f);
            riderTorso.transform.rotate(Vector3.X, -16f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            setPart(riderHead, 0f, 1.30f, 0.65f);
            riderHead.transform.scale(IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE,
                    IMPORTED_RIDER_SCALE);
            setPart(riderLegLeft, -0.13f, 0.56f, 0.42f);
            setPart(riderLegRight, 0.13f, 0.56f, 0.42f);
            riderLegLeft.transform.rotate(Vector3.X, 36f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            riderLegRight.transform.rotate(Vector3.X, 36f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            setPart(riderArmLeft, -0.17f, 0.91f, 0.82f);
            setPart(riderArmRight, 0.17f, 0.91f, 0.82f);
            riderArmLeft.transform.rotate(Vector3.X, 67f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            riderArmRight.transform.rotate(Vector3.X, 67f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
        } else {
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
        }

        if (importedBikeLoaded) {
            importedBike.body.transform.set(bikeRoot);
            importedBike.engine.transform.set(bikeRoot);
            float importedSpinDeg = -wheelSpin * (WHEEL_RADIUS / importedBike.wheelRadius)
                    * MathUtils.radiansToDegrees;
            importedBike.rearWheel.transform.set(bikeRoot)
                    .translate(importedBike.rearAxleOffset)
                    .rotate(Vector3.X, importedSpinDeg);
            float visualSteerBlend = MathUtils.clamp(speed / 30f, 0f, 1f);
            visualSteerBlend = visualSteerBlend * visualSteerBlend * (3f - 2f * visualSteerBlend);
            float visualSteerDeg = -steer * MathUtils.lerp(28f, 5f, visualSteerBlend);
            importedSteeringRoot.set(bikeRoot)
                    .translate(importedBike.steeringHead)
                    .rotate(importedBike.steeringAxis, visualSteerDeg);
            importedBike.steering.transform.set(importedSteeringRoot);
            tempA.set(importedBike.frontAxleOffset).sub(importedBike.steeringHead);
            importedBike.frontWheel.transform.set(importedSteeringRoot)
                    .translate(tempA)
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
            float helmetY = importedBikeLoaded ? 1.22f : 1.36f;
            float helmetZ = importedBikeLoaded ? 0.66f : (0.58f + riderLean * 0.10f);
            tempA.set(0f, helmetY, helmetZ).mul(bikeRoot);
            camera.position.set(tempA);
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(sinView * cp, MathUtils.sin(lookPitch), cosView * cp).nor();
            camera.up.set(Vector3.Y);
            camera.fieldOfView = 80f;
        } else {
            // Compact chase camera: keep a fixed physical distance from the bike and use
            // FOV, not camera pull-back, to communicate speed. The old slow lerp created
            // a velocity-dependent trailing error that made the camera drift farther away
            // the faster the bike travelled.
            float orbitPitch = MathUtils.clamp(lookPitch, -22f * MathUtils.degreesToRadians,
                    24f * MathUtils.degreesToRadians);
            float chaseDistance = 3.90f;
            float horizontalDistance = chaseDistance * MathUtils.cos(orbitPitch);
            float desiredHeight = 1.88f + chaseDistance * MathUtils.sin(orbitPitch);
            tempA.set(bikeX - sinView * horizontalDistance,
                    Math.max(0.82f, desiredHeight),
                    bikeZ - cosView * horizontalDistance);

            // At speed the camera nearly locks to the desired rig position so relative
            // distance stays constant. At low speed a little smoothing keeps U-turns and
            // orbit input from looking robotic.
            float followResponse = Math.min(1f, dt * (11.5f + speed * 1.35f));
            camera.position.lerp(tempA, followResponse);

            float targetLead = 1.15f;
            tempB.set(bikeX + MathUtils.sin(yaw) * targetLead,
                    0.88f + MathUtils.sin(pitch) * 0.42f,
                    bikeZ + MathUtils.cos(yaw) * targetLead);
            camera.up.set(Vector3.Y);
            camera.lookAt(tempB);

            float speedFov = MathUtils.clamp(speed / 38f, 0f, 1f);
            camera.fieldOfView = MathUtils.lerp(68f, 76f, speedFov);
        }
        camera.update();
    }

    private void renderBike() {
        if (importedBikeLoaded) {
            modelBatch.render(importedBike.body, environment);
            modelBatch.render(importedBike.engine, environment);
            modelBatch.render(importedBike.steering, environment);
            modelBatch.render(importedBike.rearWheel, environment);
            modelBatch.render(importedBike.frontWheel, environment);
            modelBatch.render(instrumentPanel, environment);
            modelBatch.render(instrumentScreen, environment);
            modelBatch.render(instrumentButtonLeft, environment);
            modelBatch.render(instrumentButtonRight, environment);
            return;
        }

        modelBatch.render(rearWheel, environment); modelBatch.render(frontWheel, environment);
        modelBatch.render(rearHub, environment); modelBatch.render(frontHub, environment);
        modelBatch.render(frame, environment); modelBatch.render(tank, environment);
        modelBatch.render(seat, environment); modelBatch.render(frontFender, environment);
        modelBatch.render(forkLeft, environment); modelBatch.render(forkRight, environment);
        modelBatch.render(handlebar, environment); modelBatch.render(frontNumberPlate, environment);
        modelBatch.render(instrumentPanel, environment);
        modelBatch.render(instrumentScreen, environment);
        modelBatch.render(instrumentButtonLeft, environment);
        modelBatch.render(instrumentButtonRight, environment);
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

        drawTouchControls(w, h, min);

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, 0.92f);

        if (!crashed && !frontGrounded && pitch > 8f * MathUtils.degreesToRadians) {
            font.getData().setScale(Math.max(0.9f, h / 720f * 1.08f));
            String wheelie = String.format(java.util.Locale.US, "%.1fs   %02.0f°",
                    wheelieTime, pitch * MathUtils.radiansToDegrees);
            font.draw(spriteBatch, wheelie, w * 0.47f, h - 32f);
        }

        if (crashed) {
            font.getData().setScale(Math.max(1.1f, h / 720f * 1.45f));
            font.setColor(1f, 1f, 1f, MathUtils.clamp(1f - Math.max(0f, crashTimer - 1.35f), 0f, 1f));
            font.draw(spriteBatch, crashSettled ? "DOWN" : "CRASH", w * 0.47f, h * 0.78f);
        }
        spriteBatch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void drawTouchControls(int w, int h, float min) {
        float sliderX = w * 0.91f;
        float sliderBottom = h * 0.12f;
        float sliderTop = h * 0.52f;
        float brakeX = w * 0.755f;
        float brakeY = h * 0.17f;
        float steerLeftX = w * 0.095f;
        float steerRightX = w * 0.235f;
        float steerY = h * 0.18f;
        float steerR = min * 0.064f;
        float shiftDownX = w * 0.39f;
        float shiftUpX = w * 0.51f;
        float shiftY = h * 0.17f;
        float shiftR = min * 0.047f;

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Throttle: thin unobtrusive rail with fill rather than a giant labeled slider.
        shapes.setColor(1f, 1f, 1f, 0.08f);
        shapes.rect(sliderX - 4f, sliderBottom, 8f, sliderTop - sliderBottom);
        shapes.setColor(1f, 1f, 1f, 0.34f);
        shapes.rect(sliderX - 4f, sliderBottom, 8f,
                (sliderTop - sliderBottom) * throttleTarget);
        float knobY = sliderBottom + throttleTarget * (sliderTop - sliderBottom);
        shapes.setColor(1f, 1f, 1f, 0.26f);
        shapes.circle(sliderX, knobY, min * 0.027f, 20);

        // Rear brake.
        shapes.setColor(1f, 1f, 1f, rearBrakeTarget > 0f ? 0.36f : 0.09f);
        shapes.circle(brakeX, brakeY, min * 0.055f, 24);
        shapes.setColor(1f, 1f, 1f, 0.28f);
        shapes.circle(brakeX, brakeY, min * 0.025f, 20);

        // Steering pads.
        shapes.setColor(1f, 1f, 1f, steerTarget > 0.05f ? 0.30f : 0.075f);
        shapes.circle(steerLeftX, steerY, steerR, 24);
        shapes.setColor(1f, 1f, 1f, 0.35f);
        shapes.triangle(steerLeftX - steerR * 0.34f, steerY,
                steerLeftX + steerR * 0.20f, steerY + steerR * 0.34f,
                steerLeftX + steerR * 0.20f, steerY - steerR * 0.34f);

        shapes.setColor(1f, 1f, 1f, steerTarget < -0.05f ? 0.30f : 0.075f);
        shapes.circle(steerRightX, steerY, steerR, 24);
        shapes.setColor(1f, 1f, 1f, 0.35f);
        shapes.triangle(steerRightX + steerR * 0.34f, steerY,
                steerRightX - steerR * 0.20f, steerY + steerR * 0.34f,
                steerRightX - steerR * 0.20f, steerY - steerR * 0.34f);

        // Shift buttons.
        shapes.setColor(1f, 1f, 1f, shiftDownHeld ? 0.32f : 0.075f);
        shapes.circle(shiftDownX, shiftY, shiftR, 22);
        shapes.setColor(1f, 1f, 1f, shiftUpHeld ? 0.32f : 0.075f);
        shapes.circle(shiftUpX, shiftY, shiftR, 22);

        // Small camera button; no permanent label panel.
        shapes.setColor(0f, 0f, 0f, 0.22f);
        shapes.circle(w * 0.93f, h * 0.93f, min * 0.040f, 22);
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, 0.58f);
        font.getData().setScale(Math.max(0.72f, h / 720f * 0.88f));
        font.draw(spriteBatch, "−", shiftDownX - 4f, shiftY + 5f);
        font.draw(spriteBatch, "+", shiftUpX - 5f, shiftY + 5f);
        font.draw(spriteBatch, "B", brakeX - 4f, brakeY + 5f);
        font.draw(spriteBatch, "C", w * 0.93f - 4f, h * 0.93f + 5f);
        spriteBatch.end();
    }



    private void updateInstrumentTexture() {
        if (instrumentPixmap == null || instrumentTexture == null) return;

        final int width = instrumentPixmap.getWidth();
        final int height = instrumentPixmap.getHeight();
        final float rpmNorm = MathUtils.clamp(drivetrain.getRpm()
                / drivetrain.getRedlineRpm(), 0f, 1f);

        // Deep green-black LCD glass with a subtle inner frame and top sheen.
        instrumentPixmap.setColor(0.007f, 0.014f, 0.012f, 1f);
        instrumentPixmap.fill();
        instrumentPixmap.setColor(0.07f, 0.12f, 0.10f, 1f);
        instrumentPixmap.drawRectangle(2, 2, width - 5, height - 5);
        instrumentPixmap.setColor(0.035f, 0.070f, 0.060f, 1f);
        instrumentPixmap.fillRectangle(4, 4, width - 8, 3);

        // Segmented tach across the top. The last two segments become a warm shift zone.
        final int rpmSegments = 14;
        final int activeSegments = MathUtils.clamp(Math.round(rpmNorm * rpmSegments), 0,
                rpmSegments);
        final int barX = 9;
        final int barY = 11;
        final int barGap = 2;
        final int barWidth = 10;
        for (int i = 0; i < rpmSegments; i++) {
            boolean active = i < activeSegments;
            if (!active) {
                instrumentPixmap.setColor(0.045f, 0.095f, 0.078f, 1f);
            } else if (i >= rpmSegments - 2) {
                instrumentPixmap.setColor(0.98f, 0.28f, 0.08f, 1f);
            } else if (i >= rpmSegments - 4) {
                instrumentPixmap.setColor(0.98f, 0.70f, 0.10f, 1f);
            } else {
                instrumentPixmap.setColor(0.30f, 0.98f, 0.68f, 1f);
            }
            instrumentPixmap.fillRectangle(barX + i * (barWidth + barGap), barY,
                    barWidth, 6);
        }

        // A small divider makes the right-side gear readout feel like a dedicated instrument cell.
        instrumentPixmap.setColor(0.055f, 0.115f, 0.095f, 1f);
        instrumentPixmap.drawLine(142, 25, 142, 86);

        int mph = MathUtils.clamp(Math.round(speed * 2.23694f), 0, 199);
        drawSevenSegmentNumber(mph, 10, 29, 31, 45, 5, 3);
        drawSevenSegmentDigit(MathUtils.clamp(drivetrain.getGear(), 0, 9),
                153, 29, 28, 45, 5);

        drawMiniText("MPH", 11, 80, 2);
        drawMiniText("G", 162, 80, 2);

        // Shift indicator dot. Unlike the tach bar, this only lights very near redline.
        if (rpmNorm > 0.92f) {
            instrumentPixmap.setColor(1f, 0.24f, 0.08f, 1f);
            instrumentPixmap.fillCircle(181, 17, 4);
        } else {
            instrumentPixmap.setColor(0.09f, 0.035f, 0.025f, 1f);
            instrumentPixmap.fillCircle(181, 17, 3);
        }

        instrumentTexture.draw(instrumentPixmap, 0, 0);
    }

    private void drawSevenSegmentNumber(int value, int x, int y, int digitWidth,
                                        int digitHeight, int thickness, int gap) {
        String text = Integer.toString(Math.max(0, value));
        int slots = 3;
        int startX = x + (slots - text.length()) * (digitWidth + gap);
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            drawSevenSegmentDigit(digit, startX + i * (digitWidth + gap), y,
                    digitWidth, digitHeight, thickness);
        }
    }

    private void drawSevenSegmentDigit(int digit, int x, int y, int width, int height,
                                       int thickness) {
        int mask;
        switch (digit) {
            case 0: mask = 0x3F; break;
            case 1: mask = 0x06; break;
            case 2: mask = 0x5B; break;
            case 3: mask = 0x4F; break;
            case 4: mask = 0x66; break;
            case 5: mask = 0x6D; break;
            case 6: mask = 0x7D; break;
            case 7: mask = 0x07; break;
            case 8: mask = 0x7F; break;
            case 9: mask = 0x6F; break;
            default: mask = 0; break;
        }

        instrumentPixmap.setColor(0.032f, 0.078f, 0.064f, 1f);
        drawSevenSegmentMask(0x7F, x, y, width, height, thickness);
        instrumentPixmap.setColor(0.55f, 1.00f, 0.78f, 1f);
        drawSevenSegmentMask(mask, x, y, width, height, thickness);
    }

    private void drawSevenSegmentMask(int mask, int x, int y, int width, int height,
                                      int thickness) {
        int half = height / 2;
        if ((mask & 0x01) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y,
                    width - thickness * 2, thickness); // A
        if ((mask & 0x02) != 0)
            instrumentPixmap.fillRectangle(x + width - thickness, y + thickness,
                    thickness, half - thickness); // B
        if ((mask & 0x04) != 0)
            instrumentPixmap.fillRectangle(x + width - thickness, y + half,
                    thickness, half - thickness); // C
        if ((mask & 0x08) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y + height - thickness,
                    width - thickness * 2, thickness); // D
        if ((mask & 0x10) != 0)
            instrumentPixmap.fillRectangle(x, y + half,
                    thickness, half - thickness); // E
        if ((mask & 0x20) != 0)
            instrumentPixmap.fillRectangle(x, y + thickness,
                    thickness, half - thickness); // F
        if ((mask & 0x40) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y + half - thickness / 2,
                    width - thickness * 2, thickness); // G
    }

    private void drawMiniText(String text, int x, int y, int scale) {
        int cursor = x;
        instrumentPixmap.setColor(0.32f, 0.58f, 0.48f, 1f);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int[] rows = miniGlyph(c);
            if (rows == null) {
                cursor += 4 * scale;
                continue;
            }
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < 5; col++) {
                    if ((rows[row] & (1 << (4 - col))) != 0) {
                        instrumentPixmap.fillRectangle(cursor + col * scale,
                                y + row * scale, scale, scale);
                    }
                }
            }
            cursor += 6 * scale;
        }
    }

    private static int[] miniGlyph(char c) {
        switch (c) {
            case 'M': return new int[] {0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'P': return new int[] {0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'H': return new int[] {0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'G': return new int[] {0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0E};
            default: return null;
        }
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
    public void pause() {
        if (engineAudio != null) engineAudio.setActive(false);
    }

    @Override
    public void resume() {
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(true);
    }

    @Override
    public void dispose() {
        if (engineAudio != null) engineAudio.dispose();
        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (instrumentTexture != null) instrumentTexture.dispose();
        if (instrumentPixmap != null) instrumentPixmap.dispose();
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
    }
}
