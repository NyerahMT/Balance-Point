from pathlib import Path
import re

GAME_PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java")
SCENE_PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/GameScene.java")
source = GAME_PATH.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def extract_method(text: str, signature: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"could not locate {signature.strip()}")
    brace = text.find("{", start)
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                while end < len(text) and text[end] in "\r\n":
                    end += 1
                return text[start:end]
    raise SystemExit(f"unclosed method {signature.strip()}")


methods = {
    "material": extract_method(source, "    private Material material("),
    "box": extract_method(source, "    private Model box("),
    "cylinder": extract_method(source, "    private Model cylinder("),
    "sphere": extract_method(source, "    private Model sphere("),
    "create_world": extract_method(source, "    private void createWorldModels()"),
    "create_bike": extract_method(source, "    private void createBikeModels()"),
    "update_world": extract_method(source, "    private void updateWorldInstances("),
    "update_bike": extract_method(source, "    private void updateBikeInstances()"),
    "set_part": extract_method(source, "    private void setPart("),
    "set_wheel": extract_method(source, "    private void setWheel("),
    "render_shadow": extract_method(source, "    private void renderBikeShadow()"),
    "render_bike": extract_method(source, "    private void renderBike()"),
}

create_bike = methods["create_bike"].replace("WHEEL_RADIUS", "wheelRadius")

update_bike = methods["update_bike"].replace(
    "private void updateBikeInstances()", "float updateBike(BikeState state)"
)
for name in (
    "pitch",
    "roll",
    "yaw",
    "terrainRoll",
    "bikeX",
    "bikeZ",
    "chassisY",
    "crashed",
    "crashSettled",
    "wheelSpin",
    "riderLean",
    "speed",
    "frontGrounded",
    "terrainAirborne",
    "steer",
):
    update_bike = re.sub(rf"\b{name}\b", f"state.{name}", update_bike)
for old, new in (
    ("COM_FORWARD", "comForward"),
    ("COM_HEIGHT", "comHeight"),
    ("WHEELBASE", "wheelbase"),
    ("WHEEL_RADIUS", "wheelRadius"),
):
    update_bike = update_bike.replace(old, new)
update_bike = replace_once(
    update_bike,
    "        bikeY = rootHeight;",
    "        float bikeY = rootHeight;",
    "visual bikeY local",
)
close = update_bike.rfind("    }")
if close < 0:
    raise SystemExit("could not close updateBike")
update_bike = update_bike[:close] + "        return bikeY;\n" + update_bike[close:]

scene_header = '''package com.nyerahworks.balancepoint;

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

/** Owns world and motorcycle presentation resources; simulation remains in the game coordinator. */
final class GameScene {
    static final class BikeState {
        float bikeX;
        float bikeZ;
        float chassisY;
        float pitch;
        float roll;
        float yaw;
        float terrainRoll;
        float wheelSpin;
        float speed;
        float steer;
        float riderLean;
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
    private final Matrix4 importedSteeringRoot = new Matrix4();
    private final Vector3 tempA = new Vector3();
    private final Vector3 shadowFocus = new Vector3();

    private final ModelBatch modelBatch = new ModelBatch();
    private final Environment environment = new Environment();
    private final DirectionalShadowLight shadowLight;
    private final ModelBatch shadowBatch;

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

        environment.set(
                new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.65f, 0.63f, 1f));
        shadowLight = new DirectionalShadowLight(1024, 1024, 28f, 28f, 0.5f, 70f);
        shadowLight.set(0.95f, 0.88f, 0.76f, -0.45f, -1f, -0.28f);
        shadowLight.getDepthMap().minFilter = Texture.TextureFilter.Linear;
        shadowLight.getDepthMap().magFilter = Texture.TextureFilter.Linear;
        environment.add(shadowLight);
        environment.shadowMap = null;
        shadowBatch = new ModelBatch(new DepthShaderProvider());

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
        return importedBikeLoaded;
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
    }

    void dispose() {
        terrainVisuals.dispose();
        shadowBatch.dispose();
        shadowLight.dispose();
        modelBatch.dispose();
        for (Model model : ownedModels) model.dispose();
        ownedModels.clear();
    }

'''

scene_source = scene_header
for name in (
    "material",
    "box",
    "cylinder",
    "sphere",
    "create_world",
):
    scene_source += methods[name] + "\n"
scene_source += create_bike + "\n"
scene_source += methods["update_world"] + "\n"
scene_source += update_bike + "\n"
for name in ("set_part", "set_wheel", "render_shadow", "render_bike"):
    scene_source += methods[name] + "\n"
scene_source += "}\n"

game = source
for import_line in (
    "import com.badlogic.gdx.graphics.Color;\n",
    "import com.badlogic.gdx.graphics.GL20;\n",
    "import com.badlogic.gdx.graphics.Texture;\n",
    "import com.badlogic.gdx.graphics.VertexAttributes;\n",
    "import com.badlogic.gdx.graphics.g3d.Environment;\n",
    "import com.badlogic.gdx.graphics.g3d.Material;\n",
    "import com.badlogic.gdx.graphics.g3d.Model;\n",
    "import com.badlogic.gdx.graphics.g3d.ModelBatch;\n",
    "import com.badlogic.gdx.graphics.g3d.ModelInstance;\n",
    "import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;\n",
    "import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;\n",
    "import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;\n",
    "import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;\n",
    "import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;\n",
    "import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;\n",
    "import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;\n",
    "import com.badlogic.gdx.math.Matrix4;\n",
    "import com.badlogic.gdx.math.Vector3;\n",
    "import com.badlogic.gdx.utils.Array;\n",
):
    if import_line in game:
        game = game.replace(import_line, "", 1)

game = replace_once(
    game,
    "    private static final float SEGMENT_LENGTH = 20f;\n"
    "    private static final float ROAD_HALF_WIDTH = 4.2f;\n\n",
    "",
    "world constants",
)
game = replace_once(
    game,
    "    private static final float IMPORTED_RIDER_SCALE = 0.82f;\n",
    "",
    "rider visual constant",
)

field_start = game.find("    private final Array<Model> ownedModels")
field_end = game.find("    private float speed;", field_start)
if field_start < 0 or field_end < 0:
    raise SystemExit("could not locate presentation field block")
game_fields = '''    private final MotorcycleDrivetrain drivetrain = new MotorcycleDrivetrain(WHEEL_RADIUS);

    private GameCamera gameCamera;
    private GameScene scene;
    private GameHud hud;
    private InstrumentDisplay instrumentDisplay;
    private EngineAudio engineAudio;
    // Shared with GameScene so physics and rendered terrain use the same surface instance.
    private TerrainVisuals terrainVisuals;

'''
game = game[:field_start] + game_fields + game[field_end:]

game = replace_once(
    game,
    "        modelBatch = new ModelBatch();\n"
    "        hud = new GameHud();\n"
    "        instrumentDisplay = new InstrumentDisplay();\n"
    "        engineAudio = new EngineAudio();\n",
    "        hud = new GameHud();\n"
    "        instrumentDisplay = new InstrumentDisplay();\n"
    "        scene = new GameScene(\n"
    "                instrumentDisplay, WHEELBASE, WHEEL_RADIUS, COM_FORWARD, COM_HEIGHT);\n"
    "        terrainVisuals = scene.terrain();\n"
    "        engineAudio = new EngineAudio();\n",
    "scene initialization",
)

environment_start = game.find("        environment = new Environment();")
environment_end = game.find("        resetBike();", environment_start)
if environment_start < 0 or environment_end < 0:
    raise SystemExit("could not locate scene initialization block")
game = game[:environment_start] + game[environment_end:]

game = replace_once(
    game,
    "        enterMainMenu();\n\n"
    "        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);\n"
    "        Gdx.gl.glEnable(GL20.GL_CULL_FACE);\n"
    "        Gdx.gl.glCullFace(GL20.GL_BACK);\n",
    "        enterMainMenu();\n",
    "GL initialization",
)

helpers_start = game.find("    private Material material(")
helpers_end = game.find("    @Override\n    public void render()", helpers_start)
if helpers_start < 0 or helpers_end < 0:
    raise SystemExit("could not locate scene construction helpers")
game = game[:helpers_start] + game[helpers_end:]

game = replace_once(
    game,
    "        if (gameState != GameState.MAIN_MENU) {\n"
    "            updateBikeInstances();\n"
    "            instrumentDisplay.update(speed, drivetrain);\n"
    "        }\n",
    "        if (gameState != GameState.MAIN_MENU) {\n"
    "            updateBikeVisuals();\n"
    "            instrumentDisplay.update(speed, drivetrain);\n"
    "        }\n",
    "bike visual update",
)

render_start = game.find("        float worldCenterZ = gameCamera.worldCenterZ(bikeZ);")
render_end = game.find("        drawHud();", render_start)
if render_start < 0 or render_end < 0:
    raise SystemExit("could not locate render scene block")
render_block = '''        float worldCenterZ = gameCamera.worldCenterZ(bikeZ);
        scene.updateWorld(worldCenterZ);
        scene.render(
                gameCamera.camera(),
                worldCenterZ,
                highQuality,
                gameState != GameState.MAIN_MENU,
                bikeX,
                bikeY);
'''
game = game[:render_start] + render_block + game[render_end:]

game = replace_once(
    game,
    "        // Do not leave a stale shadow map attached for even one frame after switching Low.\n"
    "        if (!highQuality && environment != null) environment.shadowMap = null;\n",
    "        if (!highQuality && scene != null) scene.disableShadows();\n",
    "shadow quality toggle",
)

visual_start = game.find("    private void updateWorldInstances(")
visual_end = game.find("    private void updateCamera(float dt)", visual_start)
if visual_start < 0 or visual_end < 0:
    raise SystemExit("could not locate visual update methods")
visual_wrapper = '''    private void updateBikeVisuals() {
        GameScene.BikeState state = scene.bikeState();
        state.bikeX = bikeX;
        state.bikeZ = bikeZ;
        state.chassisY = chassisY;
        state.pitch = pitch;
        state.roll = roll;
        state.yaw = yaw;
        state.terrainRoll = terrainRoll;
        state.wheelSpin = wheelSpin;
        state.speed = speed;
        state.steer = steer;
        state.riderLean = riderLean;
        state.frontGrounded = frontGrounded;
        state.terrainAirborne = terrainAirborne;
        state.crashed = crashed;
        state.crashSettled = crashSettled;
        bikeY = scene.updateBike(state);
    }

'''
game = game[:visual_start] + visual_wrapper + game[visual_end:]

game = replace_once(
    game,
    "        state.bikeRoot = bikeRoot;\n",
    "        state.bikeRoot = scene.bikeRoot();\n",
    "camera bike root",
)
game = replace_once(
    game,
    "        state.importedBikeLoaded = importedBikeLoaded;\n",
    "        state.importedBikeLoaded = scene.importedBikeLoaded();\n",
    "camera imported-bike state",
)

bike_render_start = game.find("    private void renderBikeShadow()")
bike_render_end = game.find("    private void drawHud()", bike_render_start)
if bike_render_start < 0 or bike_render_end < 0:
    raise SystemExit("could not locate bike rendering methods")
game = game[:bike_render_start] + game[bike_render_end:]

game = replace_once(
    game,
    "        if (engineAudio != null) engineAudio.dispose();\n"
    "        if (terrainVisuals != null) terrainVisuals.dispose();\n"
    "        if (shadowBatch != null) shadowBatch.dispose();\n"
    "        if (shadowLight != null) shadowLight.dispose();\n"
    "        if (modelBatch != null) modelBatch.dispose();\n"
    "        if (hud != null) hud.dispose();\n"
    "        for (Model m : ownedModels) m.dispose();\n"
    "        ownedModels.clear();\n"
    "        if (instrumentDisplay != null) instrumentDisplay.dispose();\n",
    "        if (engineAudio != null) engineAudio.dispose();\n"
    "        if (scene != null) scene.dispose();\n"
    "        if (hud != null) hud.dispose();\n"
    "        if (instrumentDisplay != null) instrumentDisplay.dispose();\n",
    "scene disposal",
)

SCENE_PATH.write_text(scene_source, encoding="utf-8")
GAME_PATH.write_text(game, encoding="utf-8")
print(
    "Extracted GameScene: "
    f"BalancePointGame={len(game.splitlines())} lines, "
    f"GameScene={len(scene_source.splitlines())} lines"
)
