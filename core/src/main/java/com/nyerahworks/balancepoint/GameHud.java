package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import java.util.Locale;

/** Owns all screen-space presentation resources and drawing for menus and ride controls. */
final class GameHud {
    static final class State {
        GameState gameState = GameState.MAIN_MENU;
        boolean hasActiveRide;
        boolean highQuality = true;
        boolean crashed;
        boolean crashSettled;
        boolean frontGrounded = true;
        boolean shiftDownHeld;
        boolean shiftUpHeld;
        float pitch;
        float wheelieTime;
        float bestWheelieTime;
        float crashTimer;
        float speed;
        float steerTarget;
        float riderLeanTarget;
        float throttleTarget;
        float rearBrakeTarget;
        int gear;
        float rpm;
    }

    private final OrthographicCamera camera = new OrthographicCamera();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final State state = new State();

    State state() {
        return state;
    }

    void resize(int width, int height) {
        camera.setToOrtho(false, Math.max(1, width), Math.max(1, height));
        camera.update();
    }

    void draw() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        float min = Math.min(width, height);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (state.gameState == GameState.MAIN_MENU) {
            drawMainMenu(width, height, min);
        } else if (state.gameState == GameState.PAUSED) {
            drawPauseMenu(width, height, min);
        } else {
            drawTouchControls(width, height, min);
            drawRideStatus(width, height);
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void drawRideStatus(int width, int height) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(1f, 1f, 1f, 0.92f);

        if (!state.crashed
                && !state.frontGrounded
                && state.pitch > 8f * MathUtils.degreesToRadians) {
            font.getData().setScale(Math.max(0.9f, height / 720f * 1.08f));
            String wheelie = String.format(
                    Locale.US,
                    "%.1fs   %02.0f deg",
                    state.wheelieTime,
                    state.pitch * MathUtils.radiansToDegrees);
            font.draw(batch, wheelie, width * 0.43f, height - 30f);
        }

        if (state.crashed) {
            font.getData().setScale(Math.max(1.1f, height / 720f * 1.45f));
            font.setColor(
                    1f,
                    1f,
                    1f,
                    MathUtils.clamp(1f - Math.max(0f, state.crashTimer - 1.35f), 0f, 1f));
            font.draw(
                    batch,
                    state.crashSettled ? "DOWN" : "CRASH",
                    width * 0.47f,
                    height * 0.78f);
        }
        batch.end();
    }

    private void drawMainMenu(int width, int height, float min) {
        float panelX = width * 0.045f;
        float panelY = height * 0.14f;
        float panelW = width * 0.39f;
        float panelH = height * 0.72f;
        float buttonX = width * 0.075f;
        float buttonW = width * 0.30f;
        float buttonH = height * 0.085f;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f, 0.025f, 0.028f, 0.62f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.92f);
        shapes.rect(panelX, panelY, Math.max(4f, min * 0.007f), panelH);

        if (state.hasActiveRide) {
            drawButton(buttonX, height * 0.36f, buttonW, buttonH, true);
            drawButton(buttonX, height * 0.245f, buttonW, buttonH, false);
        } else {
            drawButton(buttonX, height * 0.285f, buttonW, buttonH, true);
        }
        drawButton(buttonX, height * 0.155f, buttonW, buttonH, false);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(1f, 1f, 1f, 0.98f);
        font.getData().setScale(Math.max(1.55f, height / 720f * 2.15f));
        font.draw(batch, "BALANCE POINT", width * 0.075f, height * 0.76f);
        font.getData().setScale(Math.max(0.72f, height / 720f * 0.92f));
        font.setColor(1f, 1f, 1f, 0.62f);
        font.draw(batch, "RIDE. BALANCE. SEND IT.", width * 0.077f, height * 0.70f);

        font.getData().setScale(Math.max(0.90f, height / 720f * 1.08f));
        font.setColor(1f, 1f, 1f, 0.94f);
        if (state.hasActiveRide) {
            font.draw(
                    batch,
                    "RESUME",
                    buttonX + buttonW * 0.10f,
                    height * 0.36f + buttonH * 0.61f);
            font.draw(
                    batch,
                    "NEW RIDE",
                    buttonX + buttonW * 0.10f,
                    height * 0.245f + buttonH * 0.61f);
        } else {
            font.draw(
                    batch,
                    "RIDE",
                    buttonX + buttonW * 0.10f,
                    height * 0.285f + buttonH * 0.61f);
        }
        font.setColor(1f, 1f, 1f, 0.82f);
        font.draw(
                batch,
                state.highQuality ? "GRAPHICS   HIGH" : "GRAPHICS   LOW",
                buttonX + buttonW * 0.10f,
                height * 0.155f + buttonH * 0.61f);
        batch.end();
    }

    private void drawPauseMenu(int width, int height, float min) {
        float panelX = width * 0.29f;
        float panelY = height * 0.17f;
        float panelW = width * 0.42f;
        float panelH = height * 0.64f;
        float buttonX = width * 0.34f;
        float buttonW = width * 0.32f;
        float buttonH = height * 0.075f;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.50f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0.025f, 0.030f, 0.034f, 0.94f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.95f);
        shapes.rect(
                panelX,
                panelY + panelH - Math.max(4f, min * 0.007f),
                panelW,
                Math.max(4f, min * 0.007f));
        drawButton(buttonX, height * 0.56f, buttonW, buttonH, true);
        drawButton(buttonX, height * 0.45f, buttonW, buttonH, false);
        drawButton(buttonX, height * 0.34f, buttonW, buttonH, false);
        drawButton(buttonX, height * 0.23f, buttonW, buttonH, false);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(1f, 1f, 1f, 0.96f);
        font.getData().setScale(Math.max(1.25f, height / 720f * 1.65f));
        font.draw(batch, "PAUSED", width * 0.405f, height * 0.735f);
        font.getData().setScale(Math.max(0.82f, height / 720f * 1.02f));
        font.draw(
                batch,
                "RESUME",
                buttonX + buttonW * 0.10f,
                height * 0.56f + buttonH * 0.61f);
        font.draw(
                batch,
                "RESTART",
                buttonX + buttonW * 0.10f,
                height * 0.45f + buttonH * 0.61f);
        font.draw(
                batch,
                state.highQuality ? "GRAPHICS   HIGH" : "GRAPHICS   LOW",
                buttonX + buttonW * 0.10f,
                height * 0.34f + buttonH * 0.61f);
        font.draw(
                batch,
                "MAIN MENU",
                buttonX + buttonW * 0.10f,
                height * 0.23f + buttonH * 0.61f);

        font.getData().setScale(Math.max(0.60f, height / 720f * 0.72f));
        font.setColor(1f, 1f, 1f, 0.52f);
        String rideStats = String.format(
                Locale.US, "BEST WHEELIE  %.1fs", state.bestWheelieTime);
        font.draw(batch, rideStats, buttonX + buttonW * 0.10f, height * 0.195f);
        batch.end();
    }

    private void drawButton(float x, float y, float width, float height, boolean primary) {
        shapes.setColor(
                primary ? 0.95f : 1f,
                primary ? 0.32f : 1f,
                primary ? 0.08f : 1f,
                primary ? 0.88f : 0.12f);
        shapes.rect(x, y, width, height);
        if (!primary) {
            float border = Math.max(2f, height * 0.035f);
            shapes.setColor(1f, 1f, 1f, 0.20f);
            shapes.rect(x, y, width, border);
            shapes.rect(x, y + height - border, width, border);
            shapes.rect(x, y, border, height);
            shapes.rect(x + width - border, y, border, height);
        }
    }

    private void drawTouchControls(int width, int height, float min) {
        float sliderX = width * 0.91f;
        float sliderBottom = height * 0.12f;
        float sliderTop = height * 0.52f;
        float brakeX = width * 0.755f;
        float brakeY = height * 0.17f;
        // RiderControlPad uses top-origin input Y; HUD coordinates are bottom-origin.
        float controlLeft = width * RiderControlPad.LEFT;
        float controlBottom = height * (1f - RiderControlPad.BOTTOM);
        float controlWidth = width * (RiderControlPad.RIGHT - RiderControlPad.LEFT);
        float controlHeight = height * (RiderControlPad.BOTTOM - RiderControlPad.TOP);
        float controlCenterX = controlLeft + controlWidth * 0.5f;
        float controlCenterY = controlBottom + controlHeight * 0.5f;
        float shiftDownX = width * 0.39f;
        float shiftUpX = width * 0.51f;
        float shiftY = height * 0.17f;
        float shiftRadius = min * 0.047f;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.015f, 0.018f, 0.020f, 0.48f);
        shapes.rect(width * 0.37f, height * 0.91f, width * 0.29f, height * 0.065f);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.88f);
        shapes.rect(
                width * 0.37f,
                height * 0.91f,
                Math.max(3f, min * 0.005f),
                height * 0.065f);

        shapes.setColor(0.01f, 0.012f, 0.014f, 0.52f);
        shapes.circle(width * 0.07f, height * 0.93f, min * 0.044f, 24);
        shapes.circle(width * 0.93f, height * 0.93f, min * 0.044f, 24);

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

        float controlKnobX = controlCenterX + state.steerTarget * controlWidth * 0.5f;
        float controlKnobY = controlCenterY + state.riderLeanTarget * controlHeight * 0.5f;
        float knobHalf = min * 0.020f;
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.76f);
        shapes.rect(
                controlKnobX - knobHalf,
                controlKnobY - knobHalf,
                knobHalf * 2f,
                knobHalf * 2f);

        shapes.setColor(0.01f, 0.012f, 0.014f, 0.34f);
        shapes.rect(
                sliderX - min * 0.035f,
                sliderBottom - min * 0.025f,
                min * 0.070f,
                sliderTop - sliderBottom + min * 0.050f);
        shapes.setColor(1f, 1f, 1f, 0.10f);
        shapes.rect(sliderX - 4f, sliderBottom, 8f, sliderTop - sliderBottom);
        shapes.setColor(0.95f, 0.42f, 0.10f, 0.70f);
        shapes.rect(
                sliderX - 4f,
                sliderBottom,
                8f,
                (sliderTop - sliderBottom) * state.throttleTarget);
        float knobY = sliderBottom + state.throttleTarget * (sliderTop - sliderBottom);
        shapes.setColor(1f, 1f, 1f, 0.52f);
        shapes.circle(sliderX, knobY, min * 0.029f, 22);

        shapes.setColor(0.01f, 0.012f, 0.014f, 0.46f);
        shapes.circle(brakeX, brakeY, min * 0.061f, 26);
        shapes.setColor(
                0.95f,
                0.28f,
                0.10f,
                state.rearBrakeTarget > 0f ? 0.78f : 0.20f);
        shapes.circle(brakeX, brakeY, min * 0.046f, 24);

        shapes.setColor(0.01f, 0.012f, 0.014f, 0.46f);
        shapes.circle(shiftDownX, shiftY, shiftRadius, 24);
        shapes.circle(shiftUpX, shiftY, shiftRadius, 24);
        shapes.setColor(1f, 1f, 1f, state.shiftDownHeld ? 0.44f : 0.13f);
        shapes.circle(shiftDownX, shiftY, shiftRadius * 0.76f, 22);
        shapes.setColor(1f, 1f, 1f, state.shiftUpHeld ? 0.44f : 0.13f);
        shapes.circle(shiftUpX, shiftY, shiftRadius * 0.76f, 22);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(Math.max(0.64f, height / 720f * 0.78f));
        font.setColor(1f, 1f, 1f, 0.68f);
        int mph = MathUtils.clamp(Math.round(state.speed * 2.23694f), 0, 199);
        String telemetry = String.format(
                Locale.US, "%03d MPH    G %d    %4.0f RPM", mph, state.gear, state.rpm);
        font.draw(batch, telemetry, width * 0.39f, height * 0.953f);

        font.getData().setScale(Math.max(0.60f, height / 720f * 0.72f));
        font.setColor(1f, 1f, 1f, 0.62f);
        font.draw(batch, "II", width * 0.07f - 5f, height * 0.93f + 5f);
        font.draw(batch, "CAM", width * 0.93f - 12f, height * 0.93f + 4f);
        font.draw(
                batch,
                "STEER / LEAN",
                controlLeft + min * 0.010f,
                controlBottom + controlHeight - min * 0.012f);
        font.draw(batch, "-", shiftDownX - 3f, shiftY + 4f);
        font.draw(batch, "+", shiftUpX - 4f, shiftY + 4f);
        font.draw(batch, "BRK", brakeX - 11f, brakeY + 4f);
        font.draw(batch, "THR", sliderX - 11f, sliderTop + min * 0.038f);
        batch.end();
    }

    void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
