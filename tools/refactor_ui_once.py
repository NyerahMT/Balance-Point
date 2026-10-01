from pathlib import Path

PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java")
source = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    source = source.replace(old, new, 1)


for import_line in (
    "import com.badlogic.gdx.graphics.OrthographicCamera;\n",
    "import com.badlogic.gdx.graphics.Pixmap;\n",
    "import com.badlogic.gdx.graphics.g2d.BitmapFont;\n",
    "import com.badlogic.gdx.graphics.g2d.SpriteBatch;\n",
    "import com.badlogic.gdx.graphics.glutils.ShapeRenderer;\n",
):
    if import_line not in source:
        raise SystemExit(f"missing import scheduled for extraction: {import_line.strip()}")
    source = source.replace(import_line, "", 1)

replace_once(
    "    private enum GameState { MAIN_MENU, PLAYING, PAUSED }\n\n",
    "",
    "nested GameState",
)

replace_once(
    """    private PerspectiveCamera camera;
    private OrthographicCamera uiCamera;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Pixmap instrumentPixmap;
    private Texture instrumentTexture;
""",
    """    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private GameHud hud;
    private InstrumentDisplay instrumentDisplay;
""",
    "presentation fields",
)

replace_once(
    """        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        engineAudio = new EngineAudio();
""",
    """        modelBatch = new ModelBatch();
        hud = new GameHud();
        instrumentDisplay = new InstrumentDisplay();
        engineAudio = new EngineAudio();
""",
    "presentation initialization",
)

replace_once(
    """        uiCamera = new OrthographicCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
""",
    """        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
""",
    "UI camera initialization",
)

replace_once(
    """        instrumentPixmap = new Pixmap(192, 96, Pixmap.Format.RGBA8888);
        instrumentPixmap.setColor(0.008f, 0.014f, 0.013f, 1f);
        instrumentPixmap.fill();
        instrumentTexture = new Texture(instrumentPixmap);
        instrumentTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        TextureAttribute dashTextureAttribute = TextureAttribute.createDiffuse(instrumentTexture);
""",
    """        TextureAttribute dashTextureAttribute =
                TextureAttribute.createDiffuse(instrumentDisplay.texture());
""",
    "instrument texture setup",
)

replace_once(
    """            updateBikeInstances();
            updateInstrumentTexture();
""",
    """            updateBikeInstances();
            instrumentDisplay.update(speed, drivetrain);
""",
    "instrument update",
)

replace_once(
    """    private final WheelContact rearContactState = new WheelContact();
    private final WheelContact frontContactState = new WheelContact();
// Scratch contacts used by the post-step non-penetration check. Keeping the same
// finite-radius terrain query in both the force solve and correction prevents the two
// stages from disagreeing about where the tire actually touches the heightfield.
private final WheelContact rearPostContact = new WheelContact();
private final WheelContact frontPostContact = new WheelContact();
""",
    """    private final WheelContact rearContactState = new WheelContact();
    private final WheelContact frontContactState = new WheelContact();
    // Scratch contacts used by the post-step non-penetration check. Keeping the same
    // finite-radius terrain query in both the force solve and correction prevents the two
    // stages from disagreeing about where the tire actually touches the heightfield.
    private final WheelContact rearPostContact = new WheelContact();
    private final WheelContact frontPostContact = new WheelContact();
""",
    "wheel contact field formatting",
)

replace_once(
    """sampleWheelSurface(rearPostContact, rearPostX, chassisY + rearVerticalPost, rearPostZ,
        sinYaw, cosYaw);
sampleWheelSurface(frontPostContact, frontPostX, chassisY + frontVerticalPost, frontPostZ,
        sinYaw, cosYaw);
float rearPenetration = Math.max(0f, -rearPostContact.gap);
float frontPenetration = Math.max(0f, -frontPostContact.gap);
float penetrationCorrection = Math.max(rearPenetration, frontPenetration) - 0.012f;
""",
    """        sampleWheelSurface(
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
""",
    "post-contact formatting",
)

start = source.find("    private void drawHud() {")
end = source.find("    @Override\n    public void resize", start)
if start < 0 or end < 0 or end <= start:
    raise SystemExit("could not locate HUD/instrument method block")

hud_wrapper = """    private void drawHud() {
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

"""
source = source[:start] + hud_wrapper + source[end:]

replace_once(
    """        if (uiCamera != null) {
            uiCamera.setToOrtho(false, Math.max(1, width), Math.max(1, height));
            uiCamera.update();
        }
""",
    """        if (hud != null) hud.resize(width, height);
""",
    "HUD resize",
)

replace_once(
    """        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (instrumentTexture != null) instrumentTexture.dispose();
        if (instrumentPixmap != null) instrumentPixmap.dispose();
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
""",
    """        if (modelBatch != null) modelBatch.dispose();
        if (hud != null) hud.dispose();
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
        if (instrumentDisplay != null) instrumentDisplay.dispose();
""",
    "presentation disposal",
)

PATH.write_text(source, encoding="utf-8")
print("Extracted HUD and instrument display from BalancePointGame")
