from pathlib import Path
import re

path = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = path.read_text()


def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'{label}: source block not found')
    s = s.replace(old, new, 1)


def regex_once(pattern, replacement, label):
    global s
    s2, count = re.subn(pattern, lambda m: replacement, s, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, got {count}')
    s = s2


replace_once(
    'public final class BalancePointGame extends ApplicationAdapter {\n',
    '''public final class BalancePointGame extends ApplicationAdapter {
    private enum GameState { MAIN_MENU, PLAYING, PAUSED }

''',
    'game state enum')

replace_once(
    '''    private float wheelieTime;
    private float bestWheelieTime;
    private double physicsAccumulator;
''',
    '''    private float wheelieTime;
    private float bestWheelieTime;
    private double physicsAccumulator;

    private GameState gameState = GameState.MAIN_MENU;
    private boolean hasActiveRide;
    private boolean pauseTouchHeld;
    private float menuCameraZ;
    private float menuCameraBobPhase;
''',
    'menu state fields')

replace_once(
    '''        createWorldModels();
        createBikeModels();
        resetBike();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
''',
    '''        createWorldModels();
        createBikeModels();
        resetBike();
        enterMainMenu();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
''',
    'initial menu entry')

render_method = '''    @Override
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
            updateInstrumentTexture();
        }
        updateCamera(frameDt);

        float worldCenterZ = gameState == GameState.MAIN_MENU ? menuCameraZ : bikeZ;
        updateWorldInstances(worldCenterZ);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.58f, 0.72f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        float shadowX = gameState == GameState.MAIN_MENU ? 0f : bikeX;
        float shadowY = gameState == GameState.MAIN_MENU ? 0.65f : bikeY + 0.65f;
        shadowLight.begin(tempC.set(shadowX, shadowY, worldCenterZ), camera.direction);
        shadowBatch.begin(shadowLight.getCamera());
        if (gameState != GameState.MAIN_MENU) renderBikeShadow();
        shadowBatch.end();
        shadowLight.end();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        modelBatch.begin(camera);
        terrainVisuals.render(modelBatch, environment);
        for (ModelInstance m : roadSegments) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderLeft) modelBatch.render(m, environment);
        for (ModelInstance m : shoulderRight) modelBatch.render(m, environment);
        for (ModelInstance m : laneDashes) modelBatch.render(m, environment);
        if (gameState != GameState.MAIN_MENU) renderBike();
        modelBatch.end();
        drawHud();
    }

    private void readInput'''
regex_once(
    r'    @Override\n    public void render\(\) \{.*?\n    \}\n\n    private void readInput',
    render_method,
    'render method')

replace_once(
    '''        int w = Math.max(1, Gdx.graphics.getWidth());
        int h = Math.max(1, Gdx.graphics.getHeight());

        boolean cameraTouch = false;
''',
    '''        int w = Math.max(1, Gdx.graphics.getWidth());
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
''',
    'state input gate')

replace_once(
    '''            float x = px / w;
            float y = py / h;

            if (x > 0.86f && y < 0.18f) {
''',
    '''            float x = px / w;
            float y = py / h;

            if (x < 0.14f && y < 0.16f) {
                pauseTouch = true;
                continue;
            }
            if (x > 0.86f && y < 0.18f) {
''',
    'pause touch region')

replace_once(
    '''        if (cameraTouch && !cameraTouchHeld) cockpitCamera = !cockpitCamera;
''',
    '''        if (pauseTouch && !pauseTouchHeld) {
            pauseTouchHeld = true;
            enterPause();
            return;
        }
        pauseTouchHeld = pauseTouch;

        if (cameraTouch && !cameraTouchHeld) cockpitCamera = !cockpitCamera;
''',
    'pause transition')

state_helpers = '''    private void readMainMenuInput(int w, int h) {
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = h - Gdx.input.getY();
        float bx = w * 0.075f;
        float bw = w * 0.30f;
        float bh = h * 0.085f;

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

        if (inside(x, y, bx, h * 0.55f, bw, bh)) {
            resumeRide();
        } else if (inside(x, y, bx, h * 0.44f, bw, bh)) {
            restartRide();
        } else if (inside(x, y, bx, h * 0.33f, bw, bh)) {
            enterMainMenu();
        }
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
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
        menuCameraZ = MathUtils.random(-100f, 5000f);
        menuCameraBobPhase = MathUtils.random(0f, MathUtils.PI2);
        if (engineAudio != null) engineAudio.setActive(false);
    }

    private void updateMenuCamera(float dt) {
        menuCameraZ += dt * 5.5f;
        if (menuCameraZ > 5200f) menuCameraZ = -120f;
        menuCameraBobPhase += dt * 0.42f;
        float cameraY = 1.22f + MathUtils.sin(menuCameraBobPhase) * 0.028f;
        camera.position.set(0f, cameraY, menuCameraZ);
        camera.up.set(Vector3.Y);
        camera.lookAt(0f, 0.52f, menuCameraZ + 15f);
        camera.fieldOfView = 64f;
        camera.update();
    }

'''
replace_once(
    '    private static float approach(float value, float target, float amount) {\n',
    state_helpers + '    private static float approach(float value, float target, float amount) {\n',
    'state helper methods')

old_world = '''    private void updateWorldInstances() {
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
'''
new_world = '''    private void updateWorldInstances(float centerZ) {
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
'''
replace_once(old_world, new_world, 'world center update')

replace_once(
    '''    private void updateCamera(float dt) {
        float viewYaw = yaw + lookYaw;
''',
    '''    private void updateCamera(float dt) {
        if (gameState == GameState.MAIN_MENU) {
            updateMenuCamera(dt);
            return;
        }

        float viewYaw = yaw + lookYaw;
''',
    'menu camera branch')

hud_methods = '''    private void drawHud() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        float min = Math.min(w, h);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (gameState == GameState.MAIN_MENU) {
            drawMainMenu(w, h, min);
        } else if (gameState == GameState.PAUSED) {
            drawPauseMenu(w, h, min);
        } else {
            drawTouchControls(w, h, min);

            spriteBatch.setProjectionMatrix(uiCamera.combined);
            spriteBatch.begin();
            font.setColor(1f, 1f, 1f, 0.92f);

            if (!crashed && !frontGrounded && pitch > 8f * MathUtils.degreesToRadians) {
                font.getData().setScale(Math.max(0.9f, h / 720f * 1.08f));
                String wheelie = String.format(java.util.Locale.US, "%.1fs   %02.0f deg",
                        wheelieTime, pitch * MathUtils.radiansToDegrees);
                font.draw(spriteBatch, wheelie, w * 0.43f, h - 30f);
            }

            if (crashed) {
                font.getData().setScale(Math.max(1.1f, h / 720f * 1.45f));
                font.setColor(1f, 1f, 1f,
                        MathUtils.clamp(1f - Math.max(0f, crashTimer - 1.35f), 0f, 1f));
                font.draw(spriteBatch, crashSettled ? "DOWN" : "CRASH", w * 0.47f, h * 0.78f);
            }
            spriteBatch.end();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void drawMainMenu(int w, int h, float min) {
        float panelX = w * 0.045f;
        float panelY = h * 0.14f;
        float panelW = w * 0.39f;
        float panelH = h * 0.72f;
        float bx = w * 0.075f;
        float bw = w * 0.30f;
        float bh = h * 0.085f;

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f, 0.025f, 0.028f, 0.62f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.92f);
        shapes.rect(panelX, panelY, Math.max(4f, min * 0.007f), panelH);

        if (hasActiveRide) {
            drawUiButtonShape(bx, h * 0.36f, bw, bh, true);
            drawUiButtonShape(bx, h * 0.245f, bw, bh, false);
        } else {
            drawUiButtonShape(bx, h * 0.285f, bw, bh, true);
        }
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, 0.98f);
        font.getData().setScale(Math.max(1.55f, h / 720f * 2.15f));
        font.draw(spriteBatch, "BALANCE POINT", w * 0.075f, h * 0.76f);
        font.getData().setScale(Math.max(0.72f, h / 720f * 0.92f));
        font.setColor(1f, 1f, 1f, 0.62f);
        font.draw(spriteBatch, "RIDE. BALANCE. SEND IT.", w * 0.077f, h * 0.70f);

        font.getData().setScale(Math.max(0.90f, h / 720f * 1.08f));
        font.setColor(1f, 1f, 1f, 0.94f);
        if (hasActiveRide) {
            font.draw(spriteBatch, "RESUME", bx + bw * 0.10f, h * 0.36f + bh * 0.61f);
            font.draw(spriteBatch, "NEW RIDE", bx + bw * 0.10f, h * 0.245f + bh * 0.61f);
        } else {
            font.draw(spriteBatch, "RIDE", bx + bw * 0.10f, h * 0.285f + bh * 0.61f);
        }
        spriteBatch.end();
    }

    private void drawPauseMenu(int w, int h, float min) {
        float panelX = w * 0.29f;
        float panelY = h * 0.24f;
        float panelW = w * 0.42f;
        float panelH = h * 0.52f;
        float bx = w * 0.34f;
        float bw = w * 0.32f;
        float bh = h * 0.075f;

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.50f);
        shapes.rect(0f, 0f, w, h);
        shapes.setColor(0.025f, 0.030f, 0.034f, 0.94f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.95f);
        shapes.rect(panelX, panelY + panelH - Math.max(4f, min * 0.007f),
                panelW, Math.max(4f, min * 0.007f));
        drawUiButtonShape(bx, h * 0.55f, bw, bh, true);
        drawUiButtonShape(bx, h * 0.44f, bw, bh, false);
        drawUiButtonShape(bx, h * 0.33f, bw, bh, false);
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, 0.96f);
        font.getData().setScale(Math.max(1.25f, h / 720f * 1.65f));
        font.draw(spriteBatch, "PAUSED", w * 0.405f, h * 0.69f);
        font.getData().setScale(Math.max(0.82f, h / 720f * 1.02f));
        font.draw(spriteBatch, "RESUME", bx + bw * 0.10f, h * 0.55f + bh * 0.61f);
        font.draw(spriteBatch, "RESTART", bx + bw * 0.10f, h * 0.44f + bh * 0.61f);
        font.draw(spriteBatch, "MAIN MENU", bx + bw * 0.10f, h * 0.33f + bh * 0.61f);
        spriteBatch.end();
    }

    private void drawUiButtonShape(float x, float y, float w, float h, boolean primary) {
        shapes.setColor(primary ? 0.95f : 1f,
                primary ? 0.32f : 1f,
                primary ? 0.08f : 1f,
                primary ? 0.88f : 0.12f);
        shapes.rect(x, y, w, h);
        if (!primary) {
            float border = Math.max(2f, h * 0.035f);
            shapes.setColor(1f, 1f, 1f, 0.20f);
            shapes.rect(x, y, w, border);
            shapes.rect(x, y + h - border, w, border);
            shapes.rect(x, y, border, h);
            shapes.rect(x + w - border, y, border, h);
        }
    }

    private void drawTouchControls(int w, int h, float min) {
        float sliderX = w * 0.91f;
        float sliderBottom = h * 0.12f;
        float sliderTop = h * 0.52f;
        float brakeX = w * 0.755f;
        float brakeY = h * 0.17f;
        float controlLeft = w * 0.035f;
        float controlBottom = h * 0.06f;
        float controlWidth = w * 0.260f;
        float controlHeight = h * 0.300f;
        float controlCenterX = controlLeft + controlWidth * 0.5f;
        float controlCenterY = controlBottom + controlHeight * 0.5f;
        float shiftDownX = w * 0.39f;
        float shiftUpX = w * 0.51f;
        float shiftY = h * 0.17f;
        float shiftR = min * 0.047f;

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Compact telemetry strip: readable at a glance without covering the riding view.
        shapes.setColor(0.015f, 0.018f, 0.020f, 0.48f);
        shapes.rect(w * 0.37f, h * 0.91f, w * 0.29f, h * 0.065f);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.88f);
        shapes.rect(w * 0.37f, h * 0.91f, Math.max(3f, min * 0.005f), h * 0.065f);

        // Pause and camera buttons live symmetrically in the top corners.
        shapes.setColor(0.01f, 0.012f, 0.014f, 0.52f);
        shapes.circle(w * 0.07f, h * 0.93f, min * 0.044f, 24);
        shapes.circle(w * 0.93f, h * 0.93f, min * 0.044f, 24);

        // Steer/lean pad gets an actual panel and border while retaining its touch geometry.
        shapes.setColor(0.01f, 0.012f, 0.014f, 0.34f);
        shapes.rect(controlLeft, controlBottom, controlWidth, controlHeight);
        float border = Math.max(2f, min * 0.003f);
        shapes.setColor(1f, 1f, 1f, 0.12f);
        shapes.rect(controlLeft, controlBottom, controlWidth, border);
        shapes.rect(controlLeft, controlBottom + controlHeight - border, controlWidth, border);
        shapes.rect(controlLeft, controlBottom, border, controlHeight);
        shapes.rect(controlLeft + controlWidth - border, controlBottom, border, controlHeight);
        shapes.setColor(1f, 1f, 1f, 0.10f);
        shapes.rect(controlCenterX - 1f, controlBottom, 2f, controlHeight);
        shapes.rect(controlLeft, controlCenterY - 1f, controlWidth, 2f);

        float controlKnobX = controlCenterX + steerTarget * controlWidth * 0.5f;
        float controlKnobY = controlCenterY + riderLeanTarget * controlHeight * 0.5f;
        float knobHalf = min * 0.020f;
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.76f);
        shapes.rect(controlKnobX - knobHalf, controlKnobY - knobHalf,
                knobHalf * 2f, knobHalf * 2f);

        // Throttle gets a larger visual housing without changing the touch strip.
        shapes.setColor(0.01f, 0.012f, 0.014f, 0.34f);
        shapes.rect(sliderX - min * 0.035f, sliderBottom - min * 0.025f,
                min * 0.070f, sliderTop - sliderBottom + min * 0.050f);
        shapes.setColor(1f, 1f, 1f, 0.10f);
        shapes.rect(sliderX - 4f, sliderBottom, 8f, sliderTop - sliderBottom);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.70f);
        shapes.rect(sliderX - 4f, sliderBottom, 8f,
                (sliderTop - sliderBottom) * throttleTarget);
        float knobY = sliderBottom + throttleTarget * (sliderTop - sliderBottom);
        shapes.setColor(1f, 1f, 1f, 0.52f);
        shapes.circle(sliderX, knobY, min * 0.029f, 22);

        // Brake and shift controls share the same visual language.
        shapes.setColor(0.01f, 0.012f, 0.014f, 0.46f);
        shapes.circle(brakeX, brakeY, min * 0.061f, 26);
        shapes.setColor(0.95f, 0.28f, 0.10f, rearBrakeTarget > 0f ? 0.78f : 0.20f);
        shapes.circle(brakeX, brakeY, min * 0.046f, 24);

        shapes.setColor(0.01f, 0.012f, 0.014f, 0.46f);
        shapes.circle(shiftDownX, shiftY, shiftR, 24);
        shapes.circle(shiftUpX, shiftY, shiftR, 24);
        shapes.setColor(1f, 1f, 1f, shiftDownHeld ? 0.44f : 0.13f);
        shapes.circle(shiftDownX, shiftY, shiftR * 0.76f, 22);
        shapes.setColor(1f, 1f, 1f, shiftUpHeld ? 0.44f : 0.13f);
        shapes.circle(shiftUpX, shiftY, shiftR * 0.76f, 22);
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.getData().setScale(Math.max(0.64f, h / 720f * 0.78f));
        font.setColor(1f, 1f, 1f, 0.68f);
        int mph = MathUtils.clamp(Math.round(speed * 2.23694f), 0, 199);
        String telemetry = String.format(java.util.Locale.US, "%03d MPH    G %d    %4.0f RPM",
                mph, drivetrain.getGear(), drivetrain.getRpm());
        font.draw(spriteBatch, telemetry, w * 0.39f, h * 0.953f);

        font.getData().setScale(Math.max(0.60f, h / 720f * 0.72f));
        font.setColor(1f, 1f, 1f, 0.62f);
        font.draw(spriteBatch, "II", w * 0.07f - 5f, h * 0.93f + 5f);
        font.draw(spriteBatch, "CAM", w * 0.93f - 12f, h * 0.93f + 4f);
        font.draw(spriteBatch, "STEER / LEAN", controlLeft + min * 0.010f,
                controlBottom + controlHeight - min * 0.012f);
        font.draw(spriteBatch, "-", shiftDownX - 3f, shiftY + 4f);
        font.draw(spriteBatch, "+", shiftUpX - 4f, shiftY + 4f);
        font.draw(spriteBatch, "BRK", brakeX - 11f, brakeY + 4f);
        font.draw(spriteBatch, "THR", sliderX - 11f, sliderTop + min * 0.038f);
        spriteBatch.end();
    }



    private void updateInstrumentTexture()'''
regex_once(
    r'    private void drawHud\(\) \{.*?\n    private void updateInstrumentTexture\(\)',
    hud_methods,
    'hud and controls')

replace_once(
    '''    @Override
    public void resume() {
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(true);
    }
''',
    '''    @Override
    public void resume() {
        physicsAccumulator = 0.0;
        if (engineAudio != null) engineAudio.setActive(gameState == GameState.PLAYING);
    }
''',
    'lifecycle resume')

path.write_text(s)
print('Patched BalancePointGame menu, pause, and UI shell')
