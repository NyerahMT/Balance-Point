package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
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
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Owns world presentation; motorcycle presentation is delegated to Motorcycle. */
final class GameScene {
    static final class BikeState {
        float bikeX;
        float bikeZ;
        float chassisY;
        float effectiveComForward;
        float pitch;
        float roll;
        float yaw;
        float terrainRoll;
        float wheelSpin;
        float speed;
        float steer;
        float riderLean;
        // Signed axle travel from the normal loaded ride position. Positive is compression;
        // negative is extension. Physics owns these values and the visual rig only consumes them.
        float rearSuspensionTravel;
        float frontSuspensionTravel;
        boolean frontGrounded = true;
        boolean terrainAirborne;
        boolean crashed;
        boolean crashSettled;
    }

    private static final float SEGMENT_LENGTH = 20f;
    private static final float ROAD_HALF_WIDTH = 4.2f;
    private static final float IMPORTED_RIDER_SCALE = 0.82f;

    private final float wheelbase;
    private final float wheelRadius;
    private final float comForward;
    private final float comHeight;
    private final InstrumentDisplay instrumentDisplay;
    private final BikeState bikeState = new BikeState();

    private final Array<Model> ownedModels = new Array<>();
    private final Matrix4 bikeRoot = new Matrix4();
    private final Vector3 tempA = new Vector3();
    private final Vector3 shadowFocus = new Vector3();

    private final ModelBatch modelBatch = new ModelBatch();
    private final Environment environment = new Environment();
    private final DirectionalShadowLight shadowLight;
    private final ModelBatch shadowBatch;
    private final ScreenSpaceAmbientOcclusion ssao;

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

    private Motorcycle motorcycle;
    private SkeletonRider skeletonRider;

    GameScene(
            InstrumentDisplay instrumentDisplay,
            float wheelbase,
            float wheelRadius,
            float comForward,
            float comHeight) {
        this.instrumentDisplay = instrumentDisplay;
        this.wheelbase = wheelbase;
        this.wheelRadius = wheelRadius;
        this.comForward = comForward;
        this.comHeight = comHeight;

        // A much lower ambient floor leaves room for the key light and SSAO to describe form.
        environment.set(
                new ColorAttribute(ColorAttribute.AmbientLight, 0.30f, 0.34f, 0.38f, 1f));
        shadowLight = new DirectionalShadowLight(1024, 1024, 28f, 28f, 0.5f, 70f);
        shadowLight.set(1.05f, 0.94f, 0.82f, -0.45f, -1f, -0.28f);
        shadowLight.getDepthMap().minFilter = Texture.TextureFilter.Linear;
        shadowLight.getDepthMap().magFilter = Texture.TextureFilter.Linear;
        environment.add(shadowLight);
        environment.shadowMap = null;
        shadowBatch = new ModelBatch(new DepthShaderProvider());
        ssao = new ScreenSpaceAmbientOcclusion();

        createWorldModels();
        createBikeModels();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
    }

    TerrainVisuals terrain() {
        return terrainVisuals;
    }

    BikeState bikeState() {
        return bikeState;
    }

    Matrix4 bikeRoot() {
        return bikeRoot;
    }

    boolean importedBikeLoaded() {
        return motorcycle != null;
    }

    void disableShadows() {
        environment.shadowMap = null;
    }

    void updateWorld(float centerZ) {
        updateWorldInstances(centerZ);
    }

    void render(
            PerspectiveCamera camera,
            float centerZ,
            boolean highQuality,
            boolean showBike,
            float bikeX,
            float bikeY) {
        Gdx.gl.glViewport(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.58f, 0.72f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        boolean shadowsEnabled = highQuality && showBike;
        if (shadowsEnabled) {
            shadowLight.begin(shadowFocus.set(bikeX, bikeY + 0.65f, centerZ), camera.direction);
            shadowBatch.begin(shadowLight.getCamera());
            renderBikeShadow();
            shadowBatch.end();
            shadowLight.end();
            Gdx.gl.glViewport(
                    0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        }
        environment.shadowMap = shadowsEnabled ? shadowLight : null;

        modelBatch.begin(camera);
        terrainVisuals.render(modelBatch, environment, centerZ, highQuality);
        for (ModelInstance model : roadSegments) modelBatch.render(model, environment);
        for (ModelInstance model : shoulderLeft) modelBatch.render(model, environment);
        for (ModelInstance model : shoulderRight) modelBatch.render(model, environment);
        for (ModelInstance model : laneDashes) modelBatch.render(model, environment);
        if (showBike) renderBike();
        modelBatch.end();

        // Screen-space AO runs before the HUD so only world geometry is shaded.
        ssao.apply(camera, highQuality);
    }

    void dispose() {
        terrainVisuals.dispose();
        ssao.dispose();
        shadowBatch.dispose();
        shadowLight.dispose();
        modelBatch.dispose();
        for (Model model : ownedModels) model.dispose();
        ownedModels.clear();
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
        dashTextureAttribute.offsetU = 1f;
        dashTextureAttribute.scaleU = -1f;
        Material dashScreenMat = new Material(dashTextureAttribute,
                ColorAttribute.createDiffuse(Color.WHITE));
        dashScreenMat.set(IntAttribute.createCullFace(GL20.GL_NONE));

        Model wheel = cylinder(b, wheelRadius * 2f, 0.13f, wheelRadius * 2f, 14, tire);
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
        Model dashM = box(b, 0.145f, 0.022f, 0.082f, dashMat);
        Model dashButtonM = box(b, 0.016f, 0.006f, 0.011f, dashButtonMat);
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
            DirtBikeMeshLoader.LoadedBike loadedBike = DirtBikeMeshLoader.load(
                    ownedModels,
                    material(0.08f, 0.52f, 0.12f),
                    material(0.16f, 0.17f, 0.18f),
                    material(0.045f, 0.048f, 0.052f));
            motorcycle = new Motorcycle(loadedBike, wheelbase, wheelRadius);
        } catch (Exception e) {
            motorcycle = null;
            Gdx.app.error("BalancePoint", "Could not load imported dirt bike; using fallback", e);
        }

        skeletonRider = SkeletonRider.load(ownedModels);
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
        for (int i = 0; i < laneDashes.length; i++) {
            laneDashes[i].transform.setToTranslation(0f, 0.028f, dashStart + i * 6f);
        }
    }

    float updateBike(BikeState state) {
        float pitchDeg = state.pitch * MathUtils.radiansToDegrees;
        float rollDeg = state.roll * MathUtils.radiansToDegrees;
        float yawDeg = state.yaw * MathUtils.radiansToDegrees;

        float sinYaw = MathUtils.sin(state.yaw);
        float cosYaw = MathUtils.cos(state.yaw);
        float sinPitch = MathUtils.sin(state.pitch);
        float cosPitch = MathUtils.cos(state.pitch);
        float visualComForward = state.effectiveComForward > 0f
                ? state.effectiveComForward : comForward;
        float rearForward = -visualComForward * cosPitch + comHeight * sinPitch;
        float rearVertical = -visualComForward * sinPitch - comHeight * cosPitch;
        float rootX = state.bikeX + sinYaw * rearForward;
        float rootZ = state.bikeZ + cosYaw * rearForward;
        float rootHeight = state.chassisY + rearVertical;
        float bikeY = rootHeight;

        float terrainRollDeg = state.terrainRoll * MathUtils.radiansToDegrees;

        if (state.crashed && state.crashSettled) {
            float rearTerrainHeight = terrainVisuals != null
                    ? terrainVisuals.groundHeight(rootX, rootZ) : 0f;
            rootHeight = rearTerrainHeight + 0.22f;
        }

        bikeRoot.idt().translate(rootX, rootHeight, rootZ)
                // Physics yaw is atan2(forward.x, forward.z): positive heads +X.
                // libGDX +Y rotation heads -X, so the model has to take the opposite angle
                // or the bike faces and leans backwards relative to the stick.
                .rotate(Vector3.Y, -yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
                .rotate(Vector3.X, -pitchDeg);

        float spinDeg = -state.wheelSpin * MathUtils.radiansToDegrees;
        setWheel(rearWheel, 0f, 0f, 0f, spinDeg);
        setWheel(frontWheel, 0f, 0f, wheelbase, spinDeg);
        setWheel(rearHub, 0f, 0f, 0f, spinDeg);
        setWheel(frontHub, 0f, 0f, wheelbase, spinDeg);

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

        setPart(instrumentPanel, 0f, 0.915f, 0.985f);
        instrumentPanel.transform.rotate(Vector3.X, -22f);
        instrumentScreen.transform.set(instrumentPanel.transform)
                .translate(0f, 0.0117f, 0.004f);
        instrumentButtonLeft.transform.set(instrumentPanel.transform)
                .translate(-0.041f, 0.0143f, -0.032f);
        instrumentButtonRight.transform.set(instrumentPanel.transform)
                .translate(0.041f, 0.0143f, -0.032f);

        if (skeletonRider != null) {
            skeletonRider.update(bikeRoot, state.riderLean);
        } else if (motorcycle != null) {
            float riderShift = state.riderLean;
            float riderAbsShift = Math.abs(riderShift);
            setPart(riderTorso, 0f, 0.94f - riderAbsShift * 0.025f,
                    0.49f + riderShift * 0.15f);
            riderTorso.transform.rotate(Vector3.X, -16f - riderShift * 10f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            setPart(riderHead, 0f, 1.30f - riderAbsShift * 0.025f,
                    0.65f + riderShift * 0.17f);
            riderHead.transform.scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            setPart(riderLegLeft, -0.13f, 0.56f, 0.42f + riderShift * 0.035f);
            setPart(riderLegRight, 0.13f, 0.56f, 0.42f + riderShift * 0.035f);
            riderLegLeft.transform.rotate(Vector3.X, 36f - riderShift * 3f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            riderLegRight.transform.rotate(Vector3.X, 36f - riderShift * 3f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            setPart(riderArmLeft, -0.17f, 0.91f - riderAbsShift * 0.01f,
                    0.82f + riderShift * 0.055f);
            setPart(riderArmRight, 0.17f, 0.91f - riderAbsShift * 0.01f,
                    0.82f + riderShift * 0.055f);
            riderArmLeft.transform.rotate(Vector3.X, 67f + riderShift * 5f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
            riderArmRight.transform.rotate(Vector3.X, 67f + riderShift * 5f).scale(
                    IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE, IMPORTED_RIDER_SCALE);
        } else {
            float riderZ = 0.43f + state.riderLean * 0.10f;
            setPart(riderTorso, 0f, 1.07f, riderZ);
            riderTorso.transform.rotate(Vector3.X, -11f - state.riderLean * 9f);
            setPart(riderHead, 0f, 1.48f, 0.55f + state.riderLean * 0.11f);
            setPart(riderLegLeft, -0.15f, 0.57f, 0.40f);
            setPart(riderLegRight, 0.15f, 0.57f, 0.40f);
            riderLegLeft.transform.rotate(Vector3.X, 33f);
            riderLegRight.transform.rotate(Vector3.X, 33f);
            setPart(riderArmLeft, -0.20f, 0.97f, 0.82f);
            setPart(riderArmRight, 0.20f, 0.97f, 0.82f);
            riderArmLeft.transform.rotate(Vector3.X, 69f);
            riderArmRight.transform.rotate(Vector3.X, 69f);
        }

        if (motorcycle != null) {
            motorcycle.update(bikeRoot, state, terrainVisuals);

            tempA.set(0f, 0.915f, 0.985f).sub(motorcycle.steeringHead());
            instrumentPanel.transform.set(motorcycle.steeringRoot())
                    .translate(tempA)
                    .rotate(Vector3.X, -22f);
            instrumentScreen.transform.set(instrumentPanel.transform)
                    .translate(0f, 0.0117f, 0.004f);
            instrumentButtonLeft.transform.set(instrumentPanel.transform)
                    .translate(-0.041f, 0.0143f, -0.032f);
            instrumentButtonRight.transform.set(instrumentPanel.transform)
                    .translate(0.041f, 0.0143f, -0.032f);
        }
        return bikeY;
    }

    private void setPart(ModelInstance model, float x, float y, float z) {
        model.transform.set(bikeRoot).translate(x, y, z);
    }

    private void setWheel(ModelInstance model, float x, float y, float z, float spinDeg) {
        model.transform.set(bikeRoot).translate(x, y, z)
                .rotate(Vector3.Z, 90f).rotate(Vector3.Y, spinDeg);
    }

    private void renderRiderShadow() {
        if (skeletonRider != null) {
            skeletonRider.renderShadow(shadowBatch);
            return;
        }
        shadowBatch.render(riderTorso);
        shadowBatch.render(riderHead);
        shadowBatch.render(riderLegLeft);
        shadowBatch.render(riderLegRight);
        shadowBatch.render(riderArmLeft);
        shadowBatch.render(riderArmRight);
    }

    private void renderRider() {
        if (skeletonRider != null) {
            skeletonRider.render(modelBatch, environment);
            return;
        }
        modelBatch.render(riderTorso, environment);
        modelBatch.render(riderHead, environment);
        modelBatch.render(riderLegLeft, environment);
        modelBatch.render(riderLegRight, environment);
        modelBatch.render(riderArmLeft, environment);
        modelBatch.render(riderArmRight, environment);
    }

    private void renderBikeShadow() {
        if (motorcycle != null) {
            motorcycle.renderShadow(shadowBatch);
            renderRiderShadow();
            return;
        }

        shadowBatch.render(rearWheel);
        shadowBatch.render(frontWheel);
        shadowBatch.render(rearHub);
        shadowBatch.render(frontHub);
        shadowBatch.render(frame);
        shadowBatch.render(tank);
        shadowBatch.render(seat);
        shadowBatch.render(frontFender);
        shadowBatch.render(forkLeft);
        shadowBatch.render(forkRight);
        shadowBatch.render(handlebar);
        shadowBatch.render(frontNumberPlate);
        renderRiderShadow();
    }

    private void renderBike() {
        if (motorcycle != null) {
            motorcycle.render(modelBatch, environment);
            modelBatch.render(instrumentPanel, environment);
            modelBatch.render(instrumentScreen, environment);
            modelBatch.render(instrumentButtonLeft, environment);
            modelBatch.render(instrumentButtonRight, environment);
            renderRider();
            return;
        }

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
        modelBatch.render(instrumentPanel, environment);
        modelBatch.render(instrumentScreen, environment);
        modelBatch.render(instrumentButtonLeft, environment);
        modelBatch.render(instrumentButtonRight, environment);
        renderRider();
    }
}
