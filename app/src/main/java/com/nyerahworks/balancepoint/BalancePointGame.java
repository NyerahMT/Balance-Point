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
 * Balance Point 0.2 - deliberately tiny 3D motorcycle prototype.
 *
 * The target is the feel and camera language of Cafe Racer, not its content count:
 * one lightweight road, one stunt bike, steering, throttle, rear brake and a real
 * acceleration/brake-driven wheelie balance model.
 */
public final class BalancePointGame extends ApplicationAdapter {
    private static final float PHYSICS_DT = 1f / 120f;
    private static final float SEGMENT_LENGTH = 20f;
    private static final float ROAD_HALF_WIDTH = 4.2f;
    private static final float WHEELBASE = 1.45f;
    private static final float WHEEL_RADIUS = 0.31f;
    private static final float MASS = 188f;
    private static final float GRAVITY = 9.81f;
    private static final float PITCH_INERTIA = 76f;
    private static final float COM_FORWARD = 0.70f;
    private static final float COM_HEIGHT = 0.55f;
    private static final float MAX_DRIVE_FORCE = 4200f;
    private static final float MAX_BRAKE_FORCE = 5200f;
    private static final float ENGINE_POWER = 56000f;
    private static final float PITCH_DAMPING = 31f;
    private static final float LOOP_ANGLE = 106f * MathUtils.degreesToRadians;

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

    private float speed;
    private float bikeZ;
    private float bikeX;
    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float yaw;
    private float wheelSpin;

    private float throttle;
    private float rearBrake;
    private float steer;
    private float steerTarget;

    private boolean cockpitCamera;
    private boolean cameraTouchHeld;
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

    private Model box(ModelBuilder builder, float width, float height, float depth, Material material) {
        Model model = builder.createBox(width, height, depth, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private Model cylinder(ModelBuilder builder, float width, float height, float depth,
                           int divisions, Material material) {
        Model model = builder.createCylinder(width, height, depth, divisions, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private Model sphere(ModelBuilder builder, float width, float height, float depth,
                         int divisionsU, int divisionsV, Material material) {
        Model model = builder.createSphere(width, height, depth, divisionsU, divisionsV, material,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(model);
        return model;
    }

    private void createWorldModels() {
        ModelBuilder builder = new ModelBuilder();
        Model roadModel = box(builder, ROAD_HALF_WIDTH * 2f, 0.08f, SEGMENT_LENGTH,
                material(0.16f, 0.17f, 0.18f));
        Model groundModel = box(builder, 48f, 0.035f, SEGMENT_LENGTH,
                material(0.42f, 0.46f, 0.40f));
        Model shoulderModel = box(builder, 0.10f, 0.025f, SEGMENT_LENGTH,
                material(0.86f, 0.86f, 0.80f));
        Model dashModel = box(builder, 0.10f, 0.025f, 2.8f,
                material(0.92f, 0.80f, 0.35f));
        Model buildingModel = box(builder, 4f, 3f, 5f,
                material(0.34f, 0.39f, 0.40f));

        int segmentCount = 16;
        roadSegments = new ModelInstance[segmentCount];
        groundSegments = new ModelInstance[segmentCount];
        shoulderLeft = new ModelInstance[segmentCount];
        shoulderRight = new ModelInstance[segmentCount];
        buildings = new ModelInstance[segmentCount];

        for (int i = 0; i < segmentCount; i++) {
            roadSegments[i] = new ModelInstance(roadModel);
            groundSegments[i] = new ModelInstance(groundModel);
            shoulderLeft[i] = new ModelInstance(shoulderModel);
            shoulderRight[i] = new ModelInstance(shoulderModel);
            buildings[i] = new ModelInstance(buildingModel);
        }

        laneDashes = new ModelInstance[52];
        for (int i = 0; i < laneDashes.length; i++) {
            laneDashes[i] = new ModelInstance(dashModel);
        }
    }

    private void createBikeModels() {
        ModelBuilder builder = new ModelBuilder();
        Material tire = material(0.055f, 0.058f, 0.060f);
        Material metal = material(0.58f, 0.61f, 0.61f);
        Material dark = material(0.10f, 0.11f, 0.115f);
        Material orange = material(0.92f, 0.29f, 0.085f);
        Material rider = material(0.07f, 0.075f, 0.08f);
        Material visor = material(0.16f, 0.27f, 0.30f);

        Model wheelModel = cylinder(builder, WHEEL_RADIUS * 2f, 0.13f, WHEEL_RADIUS * 2f, 14, tire);
        Model hubModel = cylinder(builder, 0.19f, 0.145f, 0.19f, 12, metal);
        Model frameModel = box(builder, 0.20f, 0.22f, 1.00f, dark);
        Model tankModel = box(builder, 0.50f, 0.36f, 0.58f, orange);
        Model seatModel = box(builder, 0.42f, 0.12f, 0.48f, dark);
        Model fenderModel = box(builder, 0.43f, 0.055f, 0.55f, orange);
        Model forkModel = box(builder, 0.055f, 0.72f, 0.055f, metal);
        Model handlebarModel = box(builder, 0.74f, 0.045f, 0.045f, dark);
        Model torsoModel = box(builder, 0.40f, 0.58f, 0.22f, rider);
        Model headModel = sphere(builder, 0.30f, 0.30f, 0.30f, 10, 8, rider);
        Model limbModel = box(builder, 0.10f, 0.62f, 0.10f, rider);
        Model visorModel = box(builder, 0.28f, 0.12f, 0.035f, visor);

        rearWheel = new ModelInstance(wheelModel);
        frontWheel = new ModelInstance(wheelModel);
        rearHub = new ModelInstance(hubModel);
        frontHub = new ModelInstance(hubModel);
        frame = new ModelInstance(frameModel);
        tank = new ModelInstance(tankModel);
        seat = new ModelInstance(seatModel);
        frontFender = new ModelInstance(fenderModel);
        forkLeft = new ModelInstance(forkModel);
        forkRight = new ModelInstance(forkModel);
        handlebar = new ModelInstance(handlebarModel);
        riderTorso = new ModelInstance(torsoModel);
        riderHead = new ModelInstance(headModel);
        riderLegLeft = new ModelInstance(limbModel);
        riderLegRight = new ModelInstance(limbModel);
        riderArmLeft = new ModelInstance(limbModel);
        riderArmRight = new ModelInstance(limbModel);

        // Reuse the visor geometry as a small front number plate to make the silhouette read as a supermoto.
        // It is intentionally not a licensed real-world bike.
        frontNumberPlate = new ModelInstance(visorModel);
    }

    private ModelInstance frontNumberPlate;

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
        for (ModelInstance instance : groundSegments) modelBatch.render(instance, environment);
        for (ModelInstance instance : roadSegments) modelBatch.render(instance, environment);
        for (ModelInstance instance : shoulderLeft) modelBatch.render(instance, environment);
        for (ModelInstance instance : shoulderRight) modelBatch.render(instance, environment);
        for (ModelInstance instance : laneDashes) modelBatch.render(instance, environment);
        for (ModelInstance instance : buildings) modelBatch.render(instance, environment);
        renderBike();
        modelBatch.end();

        drawHud();
    }

    private void readInput(float dt) {
        boolean throttleDown = false;
        boolean brakeDown = false;
        boolean steeringTouch = false;
        boolean cameraTouch = false;
        float target = 0f;

        int width = Math.max(1, Gdx.graphics.getWidth());
        int height = Math.max(1, Gdx.graphics.getHeight());

        for (int pointer = 0; pointer < 8; pointer++) {
            if (!Gdx.input.isTouched(pointer)) continue;
            float x = Gdx.input.getX(pointer) / (float) width;
            float y = Gdx.input.getY(pointer) / (float) height;

            if (x > 0.83f && y < 0.24f) {
                cameraTouch = true;
            } else if (y > 0.62f && x > 0.68f) {
                throttleDown = true;
            } else if (y > 0.62f && x < 0.32f) {
                brakeDown = true;
            } else {
                steeringTouch = true;
                target = MathUtils.clamp((x - 0.5f) * 2.25f, -1f, 1f);
            }
        }

        if (cameraTouch && !cameraTouchHeld) cockpitCamera = !cockpitCamera;
        cameraTouchHeld = cameraTouch;

        throttle = throttleDown ? 1f : 0f;
        rearBrake = brakeDown ? 1f : 0f;
        steerTarget = steeringTouch ? target : 0f;
        steer += (steerTarget - steer) * Math.min(1f, dt * 7.5f);
    }

    private void simulate(float dt) {
        if (crashed) {
            crashTimer += dt;
            throttle = 0f;
            rearBrake = 0f;
            speed = Math.max(0f, speed - 9f * dt);
            pitchVelocity += 2.8f * dt;
            pitch += pitchVelocity * dt;
            roll += 1.8f * dt;
            bikeZ += speed * dt;
            if (crashTimer > 1.15f) resetBike();
            return;
        }

        float absSpeed = Math.abs(speed);
        float powerLimited = ENGINE_POWER / Math.max(absSpeed, 4.2f);
        float driveForce = throttle * Math.min(MAX_DRIVE_FORCE, powerLimited);

        float brakeForce = 0f;
        if (rearBrake > 0f && absSpeed > 0.04f) {
            brakeForce = Math.min(MAX_BRAKE_FORCE * rearBrake,
                    absSpeed * MASS / Math.max(dt, 0.001f));
        }

        float drag = absSpeed > 0.02f ? 0.38f * speed * absSpeed + 18f * Math.signum(speed) : 0f;
        float acceleration = (driveForce - brakeForce * Math.signum(speed) - drag) / MASS;

        speed += acceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 50f);
        bikeZ += speed * dt;

        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);
        float comWorldForward = COM_FORWARD * cosPitch - COM_HEIGHT * sinPitch;
        float comWorldHeight = COM_FORWARD * sinPitch + COM_HEIGHT * cosPitch;
        float pitchTorque = MASS * acceleration * comWorldHeight
                - MASS * GRAVITY * comWorldForward
                - pitchVelocity * PITCH_DAMPING;

        if (pitch <= 0f && pitchTorque <= 0f) {
            pitch = 0f;
            pitchVelocity = 0f;
        } else {
            pitchVelocity += (pitchTorque / PITCH_INERTIA) * dt;
            pitchVelocity = MathUtils.clamp(pitchVelocity, -5.5f, 5.5f);
            pitch += pitchVelocity * dt;
            if (pitch < 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
            }
        }

        float maxLean = MathUtils.clamp(speed * 0.85f, 8f, 31f) * MathUtils.degreesToRadians;
        float rollTarget = -steer * maxLean;
        roll += (rollTarget - roll) * Math.min(1f, dt * 5.8f);

        float yawTarget = steer * MathUtils.clamp(speed * 0.16f, 0f, 6.5f) * MathUtils.degreesToRadians;
        yaw += (yawTarget - yaw) * Math.min(1f, dt * 6f);

        float lateralRate = steer * (0.75f + speed * 0.050f);
        bikeX += lateralRate * dt;
        bikeX = MathUtils.clamp(bikeX, -ROAD_HALF_WIDTH + 0.62f, ROAD_HALF_WIDTH - 0.62f);

        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        if (pitch > 10f * MathUtils.degreesToRadians && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (pitch < 5f * MathUtils.degreesToRadians) {
            wheelieTime = 0f;
        }

        if (pitch > LOOP_ANGLE || Float.isNaN(pitch) || Float.isNaN(speed)) {
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
            int worldIndex = (int) Math.floor(z / SEGMENT_LENGTH);

            groundSegments[i].transform.setToTranslation(0f, -0.075f, z);
            roadSegments[i].transform.setToTranslation(0f, -0.025f, z);
            shoulderLeft[i].transform.setToTranslation(-ROAD_HALF_WIDTH + 0.18f, 0.026f, z);
            shoulderRight[i].transform.setToTranslation(ROAD_HALF_WIDTH - 0.18f, 0.026f, z);

            float side = (worldIndex & 1) == 0 ? 1f : -1f;
            float sx = 0.78f + positiveMod(worldIndex * 17, 5) * 0.08f;
            float sy = 0.70f + positiveMod(worldIndex * 13, 7) * 0.10f;
            float sz = 0.82f + positiveMod(worldIndex * 7, 4) * 0.12f;
            float bx = side * (8.5f + positiveMod(worldIndex * 11, 5));
            float bz = z + (positiveMod(worldIndex * 5, 7) - 3) * 0.85f;
            buildings[i].transform.setToTranslation(bx, 1.5f * sy, bz).scale(sx, sy, sz);
        }

        float dashStart = (float) Math.floor((bikeZ - 35f) / 6f) * 6f;
        for (int i = 0; i < laneDashes.length; i++) {
            laneDashes[i].transform.setToTranslation(0f, 0.028f, dashStart + i * 6f);
        }
    }

    private static int positiveMod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private void updateBikeInstances() {
        float pitchDeg = pitch * MathUtils.radiansToDegrees;
        float rollDeg = roll * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;

        bikeRoot.idt()
                .translate(bikeX, WHEEL_RADIUS, bikeZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg)
                .rotate(Vector3.X, -pitchDeg);

        float spinDeg = -wheelSpin * MathUtils.radiansToDegrees;
        setWheel(rearWheel, 0f, 0f, 0f, spinDeg);
        setWheel(frontWheel, 0f, 0f, WHEELBASE, spinDeg);
        setWheel(rearHub, 0f, 0f, 0f, spinDeg);
        setWheel(frontHub, 0f, 0f, WHEELBASE, spinDeg);

        setPart(frame, 0f, 0.34f, 0.68f);
        setPart(tank, 0f, 0.61f, 0.81f);
        tank.transform.rotate(Vector3.X, -7f);
        setPart(seat, 0f, 0.68f, 0.33f);
        setPart(frontFender, 0f, 0.32f, 1.34f);

        setPart(forkLeft, -0.17f, 0.37f, 1.18f);
        setPart(forkRight, 0.17f, 0.37f, 1.18f);
        forkLeft.transform.rotate(Vector3.X, 10f);
        forkRight.transform.rotate(Vector3.X, 10f);

        setPart(handlebar, 0f, 0.91f, 1.04f);
        setPart(frontNumberPlate, 0f, 0.72f, 1.19f);
        frontNumberPlate.transform.rotate(Vector3.X, 10f);

        setPart(riderTorso, 0f, 1.07f, 0.43f);
        riderTorso.transform.rotate(Vector3.X, -11f);
        setPart(riderHead, 0f, 1.48f, 0.55f);

        setPart(riderLegLeft, -0.15f, 0.57f, 0.40f);
        setPart(riderLegRight, 0.15f, 0.57f, 0.40f);
        riderLegLeft.transform.rotate(Vector3.X, 33f);
        riderLegRight.transform.rotate(Vector3.X, 33f);

        setPart(riderArmLeft, -0.20f, 0.97f, 0.82f);
        setPart(riderArmRight, 0.20f, 0.97f, 0.82f);
        riderArmLeft.transform.rotate(Vector3.X, 69f);
        riderArmRight.transform.rotate(Vector3.X, 69f);
    }

    private void setPart(ModelInstance instance, float x, float y, float z) {
        instance.transform.set(bikeRoot).translate(x, y, z);
    }

    private void setWheel(ModelInstance instance, float x, float y, float z, float spinDeg) {
        instance.transform.set(bikeRoot)
                .translate(x, y, z)
                .rotate(Vector3.Z, 90f)
                .rotate(Vector3.Y, spinDeg);
    }

    private void updateCamera(float dt) {
        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);

        if (cockpitCamera) {
            tempA.set(0f, 1.22f, 0.62f).mul(bikeRoot);
            camera.position.set(tempA);

            tempB.set(sinYaw * MathUtils.cos(pitch), MathUtils.sin(pitch),
                    cosYaw * MathUtils.cos(pitch)).nor();
            camera.direction.set(tempB);
            camera.up.set(MathUtils.sin(roll), MathUtils.cos(roll), 0f).nor();
        } else {
            tempA.set(bikeX - sinYaw * 5.3f, 2.55f, bikeZ - cosYaw * 5.3f);
            float response = 1f - (float) Math.exp(-5.5f * dt);
            camera.position.lerp(tempA, response);

            tempB.set(bikeX + sinYaw * 2.4f,
                    0.86f + MathUtils.sin(pitch) * 0.60f,
                    bikeZ + cosYaw * 2.4f);
            camera.up.set(Vector3.Y);
            camera.lookAt(tempB);
        }

        camera.update();
    }

    private void renderBike() {
        modelBatch.render(rearWheel, environment);
        modelBatch.render(frontWheel, environment);
        modelBatch.render(rearHub, environment);
        modelBatch.render(frontHub, environment);
        modelBatch.render(frame, environment);
        modelBatch.render(tank, environment);
        modelBatch.render(seat, environment);
        modelBatch.render(frontFender, environment);
        modelBatch.render(forkLeft, environment);
        modelBatch.render(forkRight, environment);
        modelBatch.render(handlebar, environment);
        modelBatch.render(frontNumberPlate, environment);
        modelBatch.render(riderTorso, environment);
        modelBatch.render(riderHead, environment);
        modelBatch.render(riderLegLeft, environment);
        modelBatch.render(riderLegRight, environment);
        modelBatch.render(riderArmLeft, environment);
        modelBatch.render(riderArmRight, environment);
    }

    private void drawHud() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(18f, height - 118f, 270f, 96f);

        float controlRadius = Math.min(width, height) * 0.105f;
        float controlY = controlRadius * 1.22f;
        shapes.setColor(1f, 1f, 1f, throttle > 0f ? 0.42f : 0.15f);
        shapes.circle(width - controlRadius * 1.30f, controlY, controlRadius, 24);
        shapes.setColor(1f, 1f, 1f, rearBrake > 0f ? 0.42f : 0.15f);
        shapes.circle(controlRadius * 1.30f, controlY, controlRadius, 24);

        shapes.setColor(0f, 0f, 0f, 0.42f);
        shapes.rect(width - 145f, height - 66f, 125f, 45f);
        shapes.setColor(1f, 1f, 1f, 0.14f);
        shapes.rect(width * 0.39f, 22f, width * 0.22f, 35f);

        if (crashed) {
            shapes.setColor(0f, 0f, 0f, 0.55f);
            shapes.rect(0f, 0f, width, height);
        }
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(Math.max(0.85f, height / 720f * 1.15f));

        float mph = speed * 2.23694f;
        float angle = pitch * MathUtils.radiansToDegrees;
        font.draw(spriteBatch, String.format(java.util.Locale.US, "%03.0f MPH", mph), 34f, height - 44f);
        font.draw(spriteBatch, String.format(java.util.Locale.US, "ANGLE %3.0f deg", angle), 34f, height - 69f);
        font.draw(spriteBatch, String.format(java.util.Locale.US, "WHEELIE %.1fs  BEST %.1fs", wheelieTime, bestWheelieTime),
                34f, height - 94f);
        font.draw(spriteBatch, "FPS " + Gdx.graphics.getFramesPerSecond(), 205f, height - 44f);

        font.draw(spriteBatch, "BRAKE", controlRadius * 0.78f, controlY + 5f);
        font.draw(spriteBatch, "THROTTLE", width - controlRadius * 1.72f, controlY + 5f);
        font.draw(spriteBatch, "STEER", width * 0.47f, 47f);
        font.draw(spriteBatch, cockpitCamera ? "CAM: HELMET" : "CAM: CHASE", width - 133f, height - 39f);

        if (crashed) {
            font.getData().setScale(Math.max(1.6f, height / 720f * 2.2f));
            font.draw(spriteBatch, "LOOPED IT", width * 0.40f, height * 0.54f);
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
        for (Model model : ownedModels) model.dispose();
        ownedModels.clear();
    }
}
