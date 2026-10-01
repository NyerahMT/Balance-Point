from pathlib import Path

GAME = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
TERRAIN = Path('core/src/main/java/com/nyerahworks/balancepoint/TerrainVisuals.java')

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Missing patch anchor: {label}')
    return text.replace(old, new, 1)

# --- BalancePointGame.java ---
g = GAME.read_text()

g = replace_once(g,
'''    private GameState gameState = GameState.MAIN_MENU;
    private boolean hasActiveRide;
    private boolean pauseTouchHeld;
    private float menuCameraZ;
    private float menuCameraBobPhase;
''',
'''    private GameState gameState = GameState.MAIN_MENU;
    private boolean hasActiveRide;
    private boolean pauseTouchHeld;
    private boolean highQuality = true;
    private float menuCameraZ;
    private float menuCameraBobPhase;
''', 'quality field')

g = replace_once(g,
'''        font = new BitmapFont();
        engineAudio = new EngineAudio();
''',
'''        font = new BitmapFont();
        engineAudio = new EngineAudio();
        highQuality = Gdx.app.getPreferences("balance-point")
                .getBoolean("qualityHigh", true);
''', 'load quality preference')

g = replace_once(g,
'''        environment.add(shadowLight);
        environment.shadowMap = shadowLight;
        shadowBatch = new ModelBatch(new DepthShaderProvider());
''',
'''        environment.add(shadowLight);
        // Shadow sampling is attached only while High quality is actively rendering gameplay.
        // The directional light itself remains in the Environment in both modes, preserving
        // the same scene lighting while Low avoids both the depth pass and shadow shader cost.
        environment.shadowMap = null;
        shadowBatch = new ModelBatch(new DepthShaderProvider());
''', 'defer shadow map')

g = replace_once(g,
'''        float shadowX = gameState == GameState.MAIN_MENU ? 0f : bikeX;
        float shadowY = gameState == GameState.MAIN_MENU ? 0.65f : bikeY + 0.65f;
        shadowLight.begin(tempC.set(shadowX, shadowY, worldCenterZ), camera.direction);
        shadowBatch.begin(shadowLight.getCamera());
        if (gameState != GameState.MAIN_MENU) renderBikeShadow();
        shadowBatch.end();
        shadowLight.end();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        modelBatch.begin(camera);
        terrainVisuals.render(modelBatch, environment);
''',
'''        boolean shadowsEnabled = highQuality && gameState != GameState.MAIN_MENU;
        if (shadowsEnabled) {
            shadowLight.begin(tempC.set(bikeX, bikeY + 0.65f, worldCenterZ), camera.direction);
            shadowBatch.begin(shadowLight.getCamera());
            renderBikeShadow();
            shadowBatch.end();
            shadowLight.end();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        }
        environment.shadowMap = shadowsEnabled ? shadowLight : null;

        modelBatch.begin(camera);
        terrainVisuals.render(modelBatch, environment, worldCenterZ, highQuality);
''', 'quality render path')

g = replace_once(g,
'''        float bx = w * 0.075f;
        float bw = w * 0.30f;
        float bh = h * 0.085f;

        if (hasActiveRide) {
''',
'''        float bx = w * 0.075f;
        float bw = w * 0.30f;
        float bh = h * 0.085f;
        float qualityY = h * 0.155f;

        if (inside(x, y, bx, qualityY, bw, bh)) {
            toggleQuality();
            return;
        }

        if (hasActiveRide) {
''', 'quality menu input')

g = replace_once(g,
'''    private void startNewRide() {
''',
'''    private void toggleQuality() {
        highQuality = !highQuality;
        Gdx.app.getPreferences("balance-point")
                .putBoolean("qualityHigh", highQuality)
                .flush();
        // Do not leave a stale shadow map attached for even one frame after switching Low.
        if (!highQuality && environment != null) environment.shadowMap = null;
    }

    private void startNewRide() {
''', 'quality toggle method')

g = replace_once(g,
'''        if (hasActiveRide) {
            drawUiButtonShape(bx, h * 0.36f, bw, bh, true);
            drawUiButtonShape(bx, h * 0.245f, bw, bh, false);
        } else {
            drawUiButtonShape(bx, h * 0.285f, bw, bh, true);
        }
        shapes.end();
''',
'''        if (hasActiveRide) {
            drawUiButtonShape(bx, h * 0.36f, bw, bh, true);
            drawUiButtonShape(bx, h * 0.245f, bw, bh, false);
        } else {
            drawUiButtonShape(bx, h * 0.285f, bw, bh, true);
        }
        drawUiButtonShape(bx, h * 0.155f, bw, bh, false);
        shapes.end();
''', 'quality menu button shape')

g = replace_once(g,
'''        if (hasActiveRide) {
            font.draw(spriteBatch, "RESUME", bx + bw * 0.10f, h * 0.36f + bh * 0.61f);
            font.draw(spriteBatch, "NEW RIDE", bx + bw * 0.10f, h * 0.245f + bh * 0.61f);
        } else {
            font.draw(spriteBatch, "RIDE", bx + bw * 0.10f, h * 0.285f + bh * 0.61f);
        }
        spriteBatch.end();
''',
'''        if (hasActiveRide) {
            font.draw(spriteBatch, "RESUME", bx + bw * 0.10f, h * 0.36f + bh * 0.61f);
            font.draw(spriteBatch, "NEW RIDE", bx + bw * 0.10f, h * 0.245f + bh * 0.61f);
        } else {
            font.draw(spriteBatch, "RIDE", bx + bw * 0.10f, h * 0.285f + bh * 0.61f);
        }
        font.setColor(1f, 1f, 1f, 0.82f);
        font.draw(spriteBatch, highQuality ? "QUALITY   HIGH" : "QUALITY   LOW",
                bx + bw * 0.10f, h * 0.155f + bh * 0.61f);
        spriteBatch.end();
''', 'quality menu text')

GAME.write_text(g)

# --- TerrainVisuals.java ---
t = TERRAIN.read_text()

t = replace_once(t,
'''    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
''',
'''    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    // Hidden vegetation used to remain in the render submission list at Y=-1000. Track
    // visibility explicitly so it costs zero draw submissions, and retain world Z for the
    // Low-quality distance/density cull without touching gameplay or terrain geometry.
    private final boolean[] treeActive = new boolean[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeWorldZ = new float[SEGMENTS * TREES_PER_SEGMENT * 2];
''', 'tree visibility fields')

t = replace_once(t,
'''        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);
    }

    private void hideTree(int index) {
        trunks[index].transform.setToTranslation(0f, -1000f, 0f);
        trees[index].transform.setToTranslation(0f, -1000f, 0f);
    }
''',
'''        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);
        treeActive[index] = true;
        treeWorldZ[index] = treeZ;
    }

    private void hideTree(int index) {
        treeActive[index] = false;
        treeWorldZ[index] = 0f;
        trunks[index].transform.setToTranslation(0f, -1000f, 0f);
        trees[index].transform.setToTranslation(0f, -1000f, 0f);
    }
''', 'tree active bookkeeping')

t = replace_once(t,
'''    void render(ModelBatch batch, Environment environment) {
        for (ModelInstance instance : terrainInstances) {
            if (instance != null) batch.render(instance, environment);
        }
        for (ModelInstance m : trunks) batch.render(m, environment);
        for (ModelInstance m : trees) batch.render(m, environment);
    }
''',
'''    void render(ModelBatch batch, Environment environment, float centerZ, boolean highQuality) {
        for (ModelInstance instance : terrainInstances) {
            if (instance != null) batch.render(instance, environment);
        }

        final float lowTreeDistance = 145f;
        final float lowFullDensityDistance = 78f;
        for (int i = 0; i < trees.length; i++) {
            if (!treeActive[i]) continue;
            if (!highQuality) {
                float distance = Math.abs(treeWorldZ[i] - centerZ);
                if (distance > lowTreeDistance) continue;
                // Keep the nearby riding envelope fully populated, then halve density in the
                // middle distance where the weak mobile GPU benefits far more than the eye notices.
                if (distance > lowFullDensityDistance && (i & 1) != 0) continue;
            }
            batch.render(trunks[i], environment);
            batch.render(trees[i], environment);
        }
    }
''', 'quality vegetation render')

TERRAIN.write_text(t)
print('Quality tier patch applied.')
