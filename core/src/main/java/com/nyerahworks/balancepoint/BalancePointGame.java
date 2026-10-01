package com.nyerahworks.balancepoint;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
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

    private static final float WHEELBASE = 1.403232f;
    private static final float WHEEL_RADIUS = 0.334978f;
    // Approx. 247 lb wet 450 + 190 lb rider = 198 kg combined system mass.
    private static final float MASS = 198f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA = 168f;
    // Unified wheel/terrain contact. Wheels generate forces continuously from local terrain
    // penetration and point velocity; wheelies, jumps and landings are results, not modes.
    // Off-road suspension tune: softer spring, light compression damping so square edges do
    // not kick the chassis upward, and stronger rebound damping so stored spring energy is
    // dissipated instead of producing repeated pogo oscillations.
    private static final float CONTACT_STIFFNESS = 43_000f;
    private static final float CONTACT_COMPRESSION_DAMPING = 3_600f;
    // High-speed damping is quadratic in compression velocity: ordinary bumps stay compliant,
    // but drop landings shed far more kinetic energy instead of storing it in the spring.
    private static final float CONTACT_HIGH_SPEED_COMPRESSION = 300f;
    private static final float CONTACT_REBOUND_DAMPING = 9_200f;
    private static final float CONTACT_BUMP_START = 0.075f;
    private static final float CONTACT_BUMP_STIFFNESS = 60_000f;
    private static final float MAX_CONTACT_FORCE = MASS * GRAVITY * 5f;
    // Angular damping is contact-dependent. With both tires loaded the chassis should
    // absorb short terrain impulses instead of carrying the rotation; a rear-only wheelie
    // stays freer, and true flight remains freer still so brake-tap/throttle control works.
    private static final float PITCH_DAMPING_GROUNDED = 2.85f;
    private static final float PITCH_DAMPING_WHEELIE = 1.20f;
    private static final float PITCH_DAMPING_AIR = 0.38f;
    private static final float YAW_RATE_RESPONSE_GROUND = 9.0f;
    private static final float YAW_RATE_RESPONSE_AIR = 3.0f;
    private static final float ROLL_NATURAL_FREQUENCY = 7.0f;
    private static final float ROLL_DAMPING_RATIO = 1.10f;
    private static final float TERRAIN_ROLL_NATURAL_FREQUENCY = 5.5f;
    private static final float TERRAIN_ROLL_DAMPING_RATIO = 1.15f;
    // Neutral combined bike+rider COM. 0.36 m above the axles plus the 0.337 m tire
    // radius puts the system COM about 0.70 m above level ground. 0.67 m forward of the
    // rear axle yields a believable ~55/45 rear/front static load split.
    private static final float COM_FORWARD = 0.636107f;
    private static final float AIRBORNE_COM_FORWARD = 1.00f;
    private static final float AIRBORNE_COM_SHIFT_START = 25f * MathUtils.degreesToRadians;
    private static final float AIRBORNE_COM_SHIFT_END = 55f * MathUtils.degreesToRadians;
    private static final float COM_HEIGHT = 0.36f;
    private static final float REAR_CONTACT_PRELOAD = MASS * GRAVITY
            * (WHEELBASE - COM_FORWARD) / WHEELBASE / CONTACT_STIFFNESS;
    private static final float FRONT_CONTACT_PRELOAD = MASS * GRAVITY
            * COM_FORWARD / WHEELBASE / CONTACT_STIFFNESS;
    // Maximum fore/aft shift of the combined bike+rider COM from rider body movement.
    // About 180 mm gives meaningful weight transfer while staying plausible for a rider
    // moving between the tank and rear of the seat.
    private static final float RIDER_SHIFT = 0.18f;

    // Rear tire longitudinal slip model. Grip is a force ceiling, not a power reducer:
    // surplus engine torque accelerates the wheel and produces visible/audible wheelspin.
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
    // Free-flight pitch comes from launch angular momentum plus rear-wheel reaction torque.
    // Throttle accelerates the rear wheel and raises the nose; a rear-brake tap lowers it.
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
    private static final float IMPORTED_RIDER_SCALE = 0.82f;

    private final Array<Model> ownedModels = new Array<>();
    private final Matrix4 bikeRoot = new Matrix4();
    private final Matrix4 importedSteeringRoot = new Matrix4();
    private final Vector3 tempA = new Vector3();
    private final Vector3 tempC = new Vector3();
    private final MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);

    private GameCamera gameCamera;
    private ModelBatch modelBatch;
    private GameHud hud;
    private InstrumentDisplay instrumentDisplay;
    private Environment environment;
    private DirectionalShadowLight shadowLight;
    private ModelBatch shadowBatch;
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
    // Rear axle world height. Unlike the old root-height snap, this carries vertical
    // momentum across crests when both wheels leave the terrain.
    private float bikeY = WHEEL_RADIUS;
    private float chassisY = WHEEL_RADIUS + COM_HEIGHT;
    private float verticalVelocity;
    private boolean terrainAirborne;
    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float rollVelocity;
    private float yaw;
    private float yawVelocity;
    // Sidehill bank is visual/support attitude, but it is rate-damped just like the chassis
    // instead of snapping to each newly sampled terrain normal.
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
    // Scratch contacts used by the post-step non-penetration check. Keeping the same
    // finite-radius terrain query in both the force solve and correction prevents the two
    // stages from disagreeing about where the tire actually touches the heightfield.
    private final WheelContact rearPostContact = new WheelContact();
    private final WheelContact frontPostContact = new WheelContact();

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(30);
        modelBatch = new ModelBatch();
        hud = new GameHud();
        instrumentDisplay = new InstrumentDisplay();
        engineAudio = new EngineAudio();
        highQuality = Gdx.app.getPreferences("balance-point")
                .getBoolean("qualityHigh", true);

        gameCamera = new GameCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.65f, 0.63f, 1f));
        // Official libGDX shadow-map path: DirectionalShadowLight is both the key light
        // and the Environment ShadowMap. Keep this first pass deliberately local to the bike.
        shadowLight = new DirectionalShadowLight(1024, 1024, 28f, 28f, 0.5f, 70f);
        shadowLight.set(0.95f, 0.88f, 0.76f, -0.45f, -1f, -0.28f);
        // Keep the existing 1024 map and libGDX PCF, but soften texel stair-stepping.
        shadowLight.getDepthMap().minFilter = Texture.TextureFilter.Linear;
        shadowLight.getDepthMap().magFilter = Texture.TextureFilter.Linear;
        environment.add(shadowLight);
        // Shadow sampling is attached only while High quality is actively rendering gameplay.
        // The directional light itself remains in the Environment in both modes, preserving
        // the same scene lighting while Low avoids both the depth pass and shadow shader cost.
        environment.shadowMap = null;
        shadowBatch = new ModelBatch(new DepthShaderProvider());

        createWorldModels();
        createBikeModels();
        resetBike();
        enterMainMenu();

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

        int n = 30;
        roadSegments = new ModelInstance[n];
        shoulderLeft = new ModelInstance[n];
        shoulderRight = new ModelInstance[n];
        for (int i = 0; i < n; i++) {
            roadSegments[i] = new ModelInstance(road);
            shoulderLeft[i] = new ModelInstance(edgeLine);
            shoulderRight[i] = new ModelInstance(edgeLine);
        }
        laneDashes = new ModelInstance[92];
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

        TextureAttribute dashTextureAttribute =
                TextureAttribute.createDiffuse(instrumentDisplay.texture());
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
            updateBikeInstances();
            instrumentDisplay.update(speed, drivetrain);
        }
        updateCamera(frameDt);

        float worldCenterZ = gameCamera.worldCenterZ(bikeZ);
        updateWorldInstances(worldCenterZ);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.58f, 0.72f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        boolean shadowsEnabled = highQuality && gameState != GameState.MAIN_MENU;
        if (shadowsEnabled) {
            shadowLight.begin(tempC.set(bikeX, bikeY + 0.65f, worldCenterZ), gameCamera.camera().direction);
            shadowBatch.begin(shadowLight.getCamera());
            renderBikeShadow();
            shadowBatch.end();
            shadowLight.end();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        }
        environment.shadowMap = shadowsEnabled ? shadowLight : null;

        modelBatch.begin(gameCamera.camera());
        terrainVisuals.render(modelBatch, environment, worldCenterZ, highQuality);
        for (ModelInstance m : roadSegments) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderLeft) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderRight) modelBatch.render(m, environment);
        for (ModelInstance m : laneDashes) modelBatch.render(m, environment);
        if (gameState != GameState.MAIN_MENU) renderBike();
        modelBatch.end();
        drawHud();
    }

    private void readInput(float dt) {
        int w = Math.max(1, Gdx.graphics.getWidth());
        int h = Math.max(1, Gdx.graphics.getHeight());

        if (gameState == GameState.MAIN_MENU) {
            readMainMenuInput(w, h);
            return;
        }
        if (gameState == GameState.PAUSED) {
            readPauseMenuInput(w, h);
            return;
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

            if (x < 0.14f && y < 0.16f) {
                pauseTouch = true;
                continue;
            }
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
            // Rectangular two-axis rider control. Axes are independent rather than radial, so
            // full steering and full fore/aft body movement can be commanded simultaneously.
            // Input Y is top-origin: touching above center therefore maps to positive (forward) lean.
            if (x >= 0.035f && x <= 0.295f && y >= 0.64f && y <= 0.94f) {
                controlBoxTouch = true;
                requestedSteer = applyAxisDeadzone(MathUtils.clamp((x - 0.165f) / 0.130f, -1f, 1f), 0.055f);
                requestedLean = applyAxisDeadzone(MathUtils.clamp((0.790f - y) / 0.150f, -1f, 1f), 0.055f);
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

        float steerResponse = 2.8f + Math.min(Math.abs(speed) * 0.020f, 0.65f);
        steer += (steerTarget - steer) * Math.min(1f, dt * steerResponse);
        // Body movement is deliberately slower than handlebar input, but still responsive
        // enough to preload the bike before a crest or move forward under acceleration.
        riderLean += (riderLeanTarget - riderLean) * Math.min(1f, dt * 5.2f);

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
        // Do not leave a stale shadow map attached for even one frame after switching Low.
        if (!highQuality && environment != null) environment.shadowMap = null;
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
        throttleTarget = 0f;
        rearBrakeTarget = 0f;
        steerTarget = 0f;
        riderLeanTarget = 0f;
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(false);
    }

    private void enterMainMenu() {
        gameState = GameState.MAIN_MENU;
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

    private static float applyAxisDeadzone(float value, float deadzone) {
        float magnitude = Math.abs(value);
        if (magnitude <= deadzone) return 0f;
        float rescaled = (magnitude - deadzone) / (1f - deadzone);
        return Math.signum(value) * MathUtils.clamp(rescaled, 0f, 1f);
    }

    private void simulate(float dt) {
        if (crashed) {
            simulateCrash(dt);
            return;
        }

        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);

        // Axle locations relative to the CURRENT combined COM. Positive rider lean moves
        // the mass forward; negative lean moves it rearward. On an uphill, a real rider does
        // not remain rigidly rotated with the chassis: they naturally keep their torso forward
        // over the bike. Approximate that posture with a grade-dependent COM shift instead of
        // letting steep terrain rotate the whole combined mass rearward and create a fake loop.
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
        float effectiveComForward = MathUtils.clamp(
                COM_FORWARD + riderLean * RIDER_SHIFT + climbPostureShift, 0.48f, 0.96f);
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

        // Point velocity = COM translation + angular velocity x radius. This is what lets one
        // wheel leave/strike the terrain independently without any explicit landing mode.
        float rearForwardVelocity = speed - pitchVelocity * rearVertical;
        float rearVerticalVelocity = verticalVelocity + pitchVelocity * rearForward;
        float frontForwardVelocity = speed - pitchVelocity * frontVertical;
        float frontVerticalVelocity = verticalVelocity + pitchVelocity * frontForward;

        float rearEffectiveMass = MASS * (WHEELBASE - effectiveComForward) / WHEELBASE;
        float frontEffectiveMass = MASS - rearEffectiveMass;
        solveWheelContact(rearContactState, rearX, rearY, rearZ,
                rearForwardVelocity, rearVerticalVelocity, sinYaw, cosYaw,
                REAR_CONTACT_PRELOAD, rearEffectiveMass, dt);
        solveWheelContact(frontContactState, frontX, frontY, frontZ,
                frontForwardVelocity, frontVerticalVelocity, sinYaw, cosYaw,
                FRONT_CONTACT_PRELOAD, frontEffectiveMass, dt);

        boolean rearTouching = rearContactState.touching();
        boolean frontTouching = frontContactState.touching();
        terrainAirborne = !rearTouching && !frontTouching;
        frontGrounded = frontTouching;
        frontNormalLoad = frontContactState.normalForce;

        float absSpeed = Math.abs(speed);

        // Surface tangent is perpendicular to the solved terrain normal in the forward/up
        // plane. The rear wheel is an independent rotating body now: ground speed and tire
        // surface speed are allowed to diverge, and THAT slip creates longitudinal tire force.
        float rearTangentForward = rearContactState.normalUp;
        float rearTangentUp = -rearContactState.normalForward;
        float tangentGroundSpeed = speed * rearTangentForward
                + verticalVelocity * rearTangentUp;
        float wheelLinearSpeed = rearWheelAngularSpeed * WHEEL_RADIUS;

        // Engine output stays available even after the tire exceeds available grip. The
        // drivetrain follows driven-wheel speed, so breaking traction makes RPM flare instead
        // of silently deleting horsepower at a friction clamp.
        float drivetrainForce = MathUtils.clamp(
                drivetrain.update(wheelLinearSpeed, throttle, dt), 0f, MAX_ENGINE_FORCE);
        float rearSurfaceMu = terrainVisuals != null
                ? terrainVisuals.tractionCoefficient(rearX, rearZ) : 1.0f;
        float rearTractionLimit = rearSurfaceMu * rearContactState.normalForce;
        float slipSpeed = wheelLinearSpeed - tangentGroundSpeed;
        float rearTireForce = 0f;
        if (rearTouching && rearTractionLimit > 0f) {
            float absSlip = Math.abs(slipSpeed);
            float forceBuild = MathUtils.clamp(absSlip / TIRE_SLIP_SPEED_AT_PEAK, 0f, 1f);
            // Once the tire is properly spinning, available longitudinal force falls a little
            // below peak static grip instead of unrealistically staying glued at mu*N.
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

        // Do not fake loose terrain with a generic speed-dependent power drain. Surface
        // differences now come from actual tire slip/grip instead.
        float totalForwardForce = rearNormalForward + frontNormalForward + rearTireForward
                - rollingForce - aero;
        float totalVerticalForce = rearNormalUp + frontNormalUp + rearTireUp - MASS * GRAVITY;

        longitudinalAcceleration = totalForwardForce / MASS;
        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, -10f, 48f);
        verticalVelocity += totalVerticalForce / MASS * dt;

        // Integrate the driven rear wheel from engine torque, tire reaction torque and rear
        // brake torque. This is the core distinction between traction and a power restriction:
        // torque that cannot reach the ground remains in the wheel as angular acceleration.
        float previousWheelAngularSpeed = rearWheelAngularSpeed;
        float engineWheelTorque = drivetrainForce * WHEEL_RADIUS;
        float tireReactionTorque = rearTireForce * WHEEL_RADIUS;
        float wheelTorqueBeforeBrake = engineWheelTorque - tireReactionTorque;
        float maxBrakeTorque = MAX_REAR_BRAKE_FORCE * rearBrake * WHEEL_RADIUS;

        if (rearBrake > 0f && Math.abs(rearWheelAngularSpeed) < 1.0f
                && maxBrakeTorque >= Math.abs(wheelTorqueBeforeBrake)) {
            // Brake can statically hold the wheel at zero instead of numerically reversing it.
            rearWheelAngularSpeed = 0f;
        } else {
            float brakeDirection = Math.abs(rearWheelAngularSpeed) > 0.25f
                    ? Math.signum(rearWheelAngularSpeed)
                    : Math.signum(wheelTorqueBeforeBrake);
            float netWheelTorque = wheelTorqueBeforeBrake - brakeDirection * maxBrakeTorque;
            float wheelAngularAcceleration = netWheelTorque / REAR_WHEEL_INERTIA
                    - rearWheelAngularSpeed * AIR_WHEEL_DRAG;
            rearWheelAngularSpeed += wheelAngularAcceleration * dt;
            // A braking step may stop the wheel, but should not drive it backwards.
            if (rearBrake > 0f && previousWheelAngularSpeed * rearWheelAngularSpeed < 0f) {
                rearWheelAngularSpeed = 0f;
            }
            rearWheelAngularSpeed = MathUtils.clamp(rearWheelAngularSpeed, -50f, 260f);
        }

        float actualAngularAcceleration = (rearWheelAngularSpeed - previousWheelAngularSpeed)
                / Math.max(dt, 0.0001f);
        float wheelReactionTorque = rearTouching ? 0f
                : actualAngularAcceleration * REAR_WHEEL_INERTIA;

        // Apply contact forces at the actual tire contact patches and integrate the resulting
        // moment about the already-shifted COM. No second COM offset is applied here: axle
        // geometry above already contains the rider weight transfer.
        float rearContactForwardArm = rearForward
                - WHEEL_RADIUS * rearContactState.normalForward;
        float rearContactVerticalArm = rearVertical
                - WHEEL_RADIUS * rearContactState.normalUp;
        float frontContactForwardArm = frontForward
                - WHEEL_RADIUS * frontContactState.normalForward;
        float frontContactVerticalArm = frontVertical
                - WHEEL_RADIUS * frontContactState.normalUp;

        float rearForceForward = rearNormalForward + rearTireForward;
        float rearForceUp = rearNormalUp + rearTireUp;
        float pitchTorque = rearContactForwardArm * rearForceUp
                - rearContactVerticalArm * rearForceForward
                + frontContactForwardArm * frontNormalUp
                - frontContactVerticalArm * frontNormalForward
                + wheelReactionTorque;

        float pitchDamping = frontTouching ? PITCH_DAMPING_GROUNDED
                : (rearTouching ? PITCH_DAMPING_WHEELIE : PITCH_DAMPING_AIR);
        float pitchAcceleration = pitchTorque / PITCH_INERTIA
                - pitchVelocity * pitchDamping;
        pitchVelocity += pitchAcceleration * dt;
        pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
        pitch += pitchVelocity * dt;

        // Semi-implicit translation after force/torque integration.
        bikeX += sinYaw * speed * dt;
        bikeZ += cosYaw * speed * dt;
        chassisY += verticalVelocity * dt;

        // Position-level non-penetration constraint. This fixes tunnelling without adding
        // upward momentum; the force solver still owns suspension response and pitch.
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
        sampleWheelSurface(
                rearPostContact,
                rearPostX,
                chassisY + rearVerticalPost,
                rearPostZ,
                sinYaw,
                cosYaw);
        sampleWheelSurface(
                frontPostContact,
                frontPostX,
                chassisY + frontVerticalPost,
                frontPostZ,
                sinYaw,
                cosYaw);
        float rearPenetration = Math.max(0f, -rearPostContact.gap);
        float frontPenetration = Math.max(0f, -frontPostContact.gap);
        float penetrationCorrection = Math.max(rearPenetration, frontPenetration) - 0.012f;
        if (penetrationCorrection > 0f) {
            chassisY += Math.min(penetrationCorrection, 0.16f);
            if (verticalVelocity < 0f) {
                float correctionBlend = MathUtils.clamp(penetrationCorrection / 0.16f, 0f, 1f);
                float velocityRetention = MathUtils.lerp(0.92f, 0.58f, correctionBlend);
                verticalVelocity *= velocityRetention;
            }
        }

        // Derived rear-axle height retained for camera/UI code. Physics itself lives at COM.
        rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;
        bikeY = chassisY + rearVertical;

        wheelSpin += rearWheelAngularSpeed * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        // Front-tire steering still comes from front load, but a wheelie no longer loses all
        // directional authority. Rear-only steering represents rider/handlebar/gyro steering
        // while balancing on the rear tire, blended continuously as front load disappears.
        float speedBlend = MathUtils.clamp(Math.abs(speed) / 30f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);
        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);
        // UI/control convention is X<0 left, X>0 right. The world yaw convention used by
        // this prototype is opposite, so convert once here rather than reversing the pad axis.
        float steerAngle = -steer * maxSteerDeg * MathUtils.degreesToRadians;
        float frontAuthority = MathUtils.clamp(frontContactState.normalForce
                / (MASS * GRAVITY * 0.32f), 0f, 1f);
        float frontYawRateTarget = Math.abs(speed) > 0.35f
                ? (speed / WHEELBASE) * (float) Math.tan(steerAngle) * frontAuthority
                : 0f;

        float wheelieBlend = rearTouching && !frontTouching ? 1f - frontAuthority : 0f;
        float wheelieSpeedBlend = MathUtils.clamp(Math.abs(speed) / 35f, 0f, 1f);
        // Full input gives about 54 deg/s at low wheelie speed and ~24 deg/s at high speed.
        // This is deliberately stronger than the old zero-authority wheelie behavior, but is
        // rate limited so a bump cannot instantly yaw the whole bike sideways.
        float wheelieYawRateTarget = -steer
                * MathUtils.lerp(0.95f, 0.42f, wheelieSpeedBlend) * wheelieBlend;
        float yawRateTarget = frontYawRateTarget + wheelieYawRateTarget;
        float yawResponse = terrainAirborne ? YAW_RATE_RESPONSE_AIR : YAW_RATE_RESPONSE_GROUND;
        yawVelocity += (yawRateTarget - yawVelocity) * Math.min(1f, dt * yawResponse);
        yawVelocity = MathUtils.clamp(yawVelocity, -2.6f, 2.6f);
        yaw += yawVelocity * dt;
        if (yaw > MathUtils.PI) yaw -= MathUtils.PI2;
        if (yaw < -MathUtils.PI) yaw += MathUtils.PI2;

        // Roll is a critically/over-damped angular state rather than an immediate lerp. Short
        // lateral impulses from bumps change roll velocity, then the damper kills the motion.
        float lateralAcceleration = speed * yawVelocity;
        float rollTarget = -(float) Math.atan2(lateralAcceleration, GRAVITY);
        rollTarget = MathUtils.clamp(rollTarget,
                -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);
        float rollFrequency = terrainAirborne ? 3.2f : ROLL_NATURAL_FREQUENCY;
        float rollDampingRatio = terrainAirborne ? 0.72f : ROLL_DAMPING_RATIO;
        float rollAcceleration = (rollTarget - roll) * rollFrequency * rollFrequency
                - 2f * rollDampingRatio * rollFrequency * rollVelocity;
        rollVelocity += rollAcceleration * dt;
        rollVelocity = MathUtils.clamp(rollVelocity, -4.0f, 4.0f);
        roll += rollVelocity * dt;

        // The old renderer snapped directly to the local side-slope normal, which made the bike
        // visibly twitch sideways on every irregular triangle. Filter that support bank with a
        // second-order damper at the fixed physics rate.
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

        // A wheelie is now just rear contact with an unloaded front tire. No wheelie-specific
        // force model is entered.
        if (rearTouching && !frontTouching && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontTouching) {
            wheelieTime = 0f;
        }

        if (pitch > LOOP_ANGLE
                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)
                || Float.isNaN(roll) || Float.isNaN(bikeX) || Float.isNaN(bikeZ)
                || Float.isNaN(chassisY)) {
            beginCrash();
        }
    }

/**
 * Finds the closest terrain plane under the lower arc of a finite-radius motorcycle tire.
 * The old solver sampled only the height directly below the axle and then added R, which is
 * geometrically valid only on level ground. A circle tangent to a slope needs its center
 * farther above the vertical height sample, and on a crest the contact point can sit ahead
 * or behind the axle. Sampling seven tangent planes across the lower tire footprint fixes
 * both cases while still colliding against the exact baked heightfield.
 */
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

        // Probe the exact height surface in the wheel's rolling direction rather than
        // using the deliberately smoothed 6 m render/contact normal.
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
                               float sinYaw, float cosYaw, float preloadGap,
                               float effectiveMass, float dt) {
    sampleWheelSurface(out, worldX, wheelY, worldZ, sinYaw, cosYaw);

    float virtualCompression = preloadGap - out.gap;
        if (virtualCompression <= 0f) {
            out.normalForce = 0f;
            return;
        }

        float normalVelocity = pointForwardVelocity * out.normalForward
                + pointVerticalVelocity * out.normalUp;
        float force = CONTACT_STIFFNESS * virtualCompression;
        if (normalVelocity < 0f) {
            float compressionSpeed = -normalVelocity;
            float dampingForce = CONTACT_COMPRESSION_DAMPING * compressionSpeed
                    + CONTACT_HIGH_SPEED_COMPRESSION * compressionSpeed * compressionSpeed;
            // A fixed-step damping impulse may remove most of the incoming normal velocity,
            // but cannot reverse it hard enough to turn a grade change into a launch ramp.
            float maxDampingForce = effectiveMass * compressionSpeed
                    / Math.max(dt, 0.0001f) * 0.82f;
            force += Math.min(dampingForce, maxDampingForce);
        } else {
            force -= CONTACT_REBOUND_DAMPING * normalVelocity;
        }

        float actualPenetration = Math.max(0f, -out.gap);
        if (actualPenetration > CONTACT_BUMP_START) {
            // Softer progressive bump stop: prevents tunneling/bottoming without acting like
            // a second stiff launch spring on the way back out.
            float bumpTravel = actualPenetration - CONTACT_BUMP_START;
            force += CONTACT_BUMP_STIFFNESS * bumpTravel * (1f + bumpTravel * 3.5f);
        }
        out.normalForce = MathUtils.clamp(force, 0f, MAX_CONTACT_FORCE);
    }

    private void beginCrash() {
        if (crashed) return;
        crashed = true;
        crashSettled = false;
        crashTimer = 0f;
        rollVelocity = 0f;
        yawVelocity = 0f;
        terrainRollVelocity = 0f;
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
        chassisY = WHEEL_RADIUS + COM_HEIGHT;
        bikeY = WHEEL_RADIUS;
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
        frontNormalLoad = MASS * GRAVITY * COM_FORWARD / WHEELBASE;
        throttle = throttleTarget = previousThrottleTarget = 0f;
        throttleSnap = 0f;
        rearBrake = rearBrakeTarget = 0f;
        steer = steerTarget = 0f;
        riderLean = riderLeanTarget = 0f;
        lookYaw = lookPitch = 0f;
        if (gameCamera != null) gameCamera.resetRide();
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

    private void updateWorldInstances(float centerZ) {
        float firstCenter = (float) Math.floor((centerZ - 80f) / SEGMENT_LENGTH) * SEGMENT_LENGTH
                + SEGMENT_LENGTH * 0.5f;
        for (int i = 0; i < roadSegments.length; i++) {
            float z = firstCenter + i * SEGMENT_LENGTH;
            roadSegments[i].transform.setToTranslation(0f, -0.025f, z);
            shoulderLeft[i].transform.setToTranslation(-ROAD_HALF_WIDTH + 0.18f, 0.026f, z);
            shoulderRight[i].transform.setToTranslation(ROAD_HALF_WIDTH - 0.18f, 0.026f, z);
        }
        terrainVisuals.update(centerZ);
        float dashStart = (float) Math.floor((centerZ - 42f) / 6f) * 6f;
        for (int i = 0; i < laneDashes.length; i++)
            laneDashes[i].transform.setToTranslation(0f, 0.028f, dashStart + i * 6f);
    }

    private void updateBikeInstances() {
        float pitchDeg = pitch * MathUtils.radiansToDegrees;
        float rollDeg = roll * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;

        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);
        float rearForward = -COM_FORWARD * cosPitch + COM_HEIGHT * sinPitch;
        float rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;
        float rootX = bikeX + sinYaw * rearForward;
        float rootZ = bikeZ + cosYaw * rearForward;
        float rootHeight = chassisY + rearVertical;
        bikeY = rootHeight;

        // Sidehill support attitude is already filtered at the fixed physics rate.
        float terrainRollDeg = terrainRoll * MathUtils.radiansToDegrees;

        if (crashed && crashSettled) {
            float rearTerrainHeight = terrainVisuals != null
                    ? terrainVisuals.groundHeight(rootX, rootZ) : 0f;
            rootHeight = rearTerrainHeight + 0.22f;
        }

        bikeRoot.idt().translate(rootX, rootHeight, rootZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
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
            // Match the imported fork/wheel to the same left-negative/right-positive control
            // convention used by the rectangular pad.
            float visualMaxSteerDeg = MathUtils.lerp(28f, 5f, visualSteerBlend);
            // Let the bars remain visibly useful while balancing on the rear wheel.
            if (!frontGrounded && !terrainAirborne) visualMaxSteerDeg = Math.max(visualMaxSteerDeg, 11f);
            float visualSteerDeg = steer * visualMaxSteerDeg;
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
        GameCamera.State state = gameCamera.state();
        state.gameState = gameState;
        state.bikeRoot = bikeRoot;
        state.importedBikeLoaded = importedBikeLoaded;
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

    private void renderBikeShadow() {
        if (importedBikeLoaded) {
            shadowBatch.render(importedBike.body);
            shadowBatch.render(importedBike.engine);
            shadowBatch.render(importedBike.steering);
            shadowBatch.render(importedBike.rearWheel);
            shadowBatch.render(importedBike.frontWheel);
            return;
        }

        shadowBatch.render(rearWheel); shadowBatch.render(frontWheel);
        shadowBatch.render(rearHub); shadowBatch.render(frontHub);
        shadowBatch.render(frame); shadowBatch.render(tank);
        shadowBatch.render(seat); shadowBatch.render(frontFender);
        shadowBatch.render(forkLeft); shadowBatch.render(forkRight);
        shadowBatch.render(handlebar); shadowBatch.render(frontNumberPlate);
        shadowBatch.render(riderTorso); shadowBatch.render(riderHead);
        shadowBatch.render(riderLegLeft); shadowBatch.render(riderLegRight);
        shadowBatch.render(riderArmLeft); shadowBatch.render(riderArmRight);
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
        if (terrainVisuals != null) terrainVisuals.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        if (shadowLight != null) shadowLight.dispose();
        if (modelBatch != null) modelBatch.dispose();
        if (hud != null) hud.dispose();
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
        if (instrumentDisplay != null) instrumentDisplay.dispose();
    }
}
