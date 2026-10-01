from pathlib import Path

PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java")
source = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    source = source.replace(old, new, 1)


replace_once(
    "import com.badlogic.gdx.graphics.PerspectiveCamera;\n",
    "",
    "PerspectiveCamera import",
)

replace_once(
    """    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();
    private final Vector3 tempC = new Vector3();
""",
    """    private final Vector3 tempA = new Vector3();
    private final Vector3 tempC = new Vector3();
""",
    "camera scratch vectors",
)

replace_once(
    """    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
""",
    """    private GameCamera gameCamera;
    private ModelBatch modelBatch;
""",
    "camera field",
)

replace_once(
    """    // Helmet pitch follows the bike's forward path, not chassis attitude or a fixed horizon.
    // A damped angular state prevents suspension/terrain chatter from shaking the rider view.
    private float helmetTrajectoryPitch;
    private float helmetTrajectoryPitchVelocity;
""",
    "",
    "helmet camera state",
)

replace_once(
    """    private boolean highQuality = true;
    private float menuCameraZ;
    private float menuCameraBobPhase;
""",
    """    private boolean highQuality = true;
""",
    "menu camera state",
)

replace_once(
    """        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.08f;
        camera.far = 900f;
        camera.position.set(0f, 2.5f, -5.4f);
        camera.lookAt(0f, 0.8f, 4f);
        camera.update();

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
""",
    """        gameCamera = new GameCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
""",
    "camera initialization",
)

replace_once(
    "shadowLight.begin(tempC.set(bikeX, bikeY + 0.65f, worldCenterZ), camera.direction);",
    "shadowLight.begin(tempC.set(bikeX, bikeY + 0.65f, worldCenterZ), gameCamera.camera().direction);",
    "shadow camera direction",
)
replace_once(
    "modelBatch.begin(camera);",
    "modelBatch.begin(gameCamera.camera());",
    "world camera",
)
replace_once(
    "float worldCenterZ = gameState == GameState.MAIN_MENU ? menuCameraZ : bikeZ;",
    "float worldCenterZ = gameCamera.worldCenterZ(bikeZ);",
    "world center",
)

replace_once(
    """        menuCameraZ = MathUtils.random(-100f, 5000f);
        menuCameraBobPhase = MathUtils.random(0f, MathUtils.PI2);
        if (engineAudio != null) engineAudio.setActive(false);
""",
    """        if (gameCamera != null) gameCamera.enterMainMenu();
        if (engineAudio != null) engineAudio.setActive(false);
""",
    "menu camera entry",
)

menu_start = source.find("    private void updateMenuCamera(float dt) {")
menu_end = source.find("    private static float approach", menu_start)
if menu_start < 0 or menu_end < 0 or menu_end <= menu_start:
    raise SystemExit("could not locate updateMenuCamera")
source = source[:menu_start] + source[menu_end:]

replace_once(
    """        lookYaw = lookPitch = 0f;
        helmetTrajectoryPitch = helmetTrajectoryPitchVelocity = 0f;
        shiftDownHeld = shiftUpHeld = false;
""",
    """        lookYaw = lookPitch = 0f;
        if (gameCamera != null) gameCamera.resetRide();
        shiftDownHeld = shiftUpHeld = false;
""",
    "ride camera reset",
)

camera_start = source.find("    private void updateCamera(float dt) {")
camera_end = source.find("    private void renderBikeShadow()", camera_start)
if camera_start < 0 or camera_end < 0 or camera_end <= camera_start:
    raise SystemExit("could not locate updateCamera")

camera_wrapper = """    private void updateCamera(float dt) {
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

"""
source = source[:camera_start] + camera_wrapper + source[camera_end:]

replace_once(
    """        if (camera != null) {
            camera.viewportWidth = Math.max(1, width);
            camera.viewportHeight = Math.max(1, height);
            camera.update();
        }
        if (hud != null) hud.resize(width, height);
""",
    """        if (gameCamera != null) gameCamera.resize(width, height);
        if (hud != null) hud.resize(width, height);
""",
    "camera resize",
)

if "menuCameraZ" in source or "helmetTrajectoryPitch" in source:
    raise SystemExit("camera state remained in BalancePointGame")

PATH.write_text(source, encoding="utf-8")
print("Extracted GameCamera from BalancePointGame")
