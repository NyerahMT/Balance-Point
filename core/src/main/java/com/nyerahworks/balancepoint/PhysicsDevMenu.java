package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import java.util.Locale;

/**
 * Paused developer-only physics editor. Edits the live V3 tuning registry,
 * not visual transforms or an unused legacy simulation.
 */
final class PhysicsDevMenu {
    static final int NONE = 0;
    static final int BACK = 1;
    static final int RECORD = 2;
    static final int SAVE = 3;
    static final int REPLAY = 4;
    private final PhysicsV3MultibodyPlant plant;
    private final PhysicsDiagnostics diagnostics;
    private final PhysicsTuning tuning;
    private final Preferences preferences;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();

    private int category;
    private int subcategory;
    private int dragging = -1;
    private String status = "LIVE V3 TUNING";

    PhysicsDevMenu(PhysicsV3MultibodyPlant plant, PhysicsDiagnostics diagnostics) {
        this.plant = plant;
        this.diagnostics = diagnostics;
        this.tuning = plant.tuning();
        this.preferences = Gdx.app.getPreferences("balance-point-physics-dev");
        restoreSavedSettings();
    }

    private void restoreSavedSettings() {
        for (int slot = 0; slot < 2; slot++) {
            if (!preferences.getBoolean("preset-saved-" + slot, false)) continue;
            for (int id = 0; id < PhysicsTuning.PARAMETERS.length; id++) {
                tuning.set(id, preferences.getFloat("preset-" + slot + "-" + id,
                        tuning.get(id)));
            }
            tuning.savePreset(slot);
        }
        tuning.resetDefaults();
        for (int id = 0; id < PhysicsTuning.PARAMETERS.length; id++) {
            String key = "active-" + id;
            if (preferences.contains(key)) tuning.set(id, preferences.getFloat(key));
        }
    }

    private void persistActive() {
        for (int id = 0; id < PhysicsTuning.PARAMETERS.length; id++) {
            preferences.putFloat("active-" + id, tuning.get(id));
        }
        preferences.flush();
    }

    private void persistPreset(int slot) {
        tuning.savePreset(slot);
        for (int id = 0; id < PhysicsTuning.PARAMETERS.length; id++) {
            preferences.putFloat("preset-" + slot + "-" + id, tuning.get(id));
        }
        preferences.putBoolean("preset-saved-" + slot, true);
        preferences.flush();
    }

    void resize(int width, int height) {
        camera.setToOrtho(false, Math.max(1, width), Math.max(1, height));
        camera.update();
    }

    void setStatus(String message) {
        status = message;
    }

    int readInput() {
        float width = Math.max(1, Gdx.graphics.getWidth());
        float height = Math.max(1, Gdx.graphics.getHeight());
        if (!Gdx.input.isTouched()) dragging = -1;
        if (Gdx.input.justTouched()) {
            float x = Gdx.input.getX() / width;
            float y = 1f - Gdx.input.getY() / height;
            dragging = -1;
            if (inside(x, y, 0.03f, 0.86f, 0.15f, 0.11f)) {
                persistActive();
                return BACK;
            }
            if (inside(x, y, 0.59f, 0.86f, 0.12f, 0.11f)) return RECORD;
            if (inside(x, y, 0.72f, 0.86f, 0.12f, 0.11f)) return SAVE;
            if (inside(x, y, 0.85f, 0.86f, 0.12f, 0.11f)) return REPLAY;

            for (int i = 0; i < PhysicsTuning.CATEGORIES.length; i++) {
                if (inside(x, y, 0.03f, 0.74f - 0.13f * i,
                        0.26f, 0.085f)) {
                    category = i;
                    subcategory = 0;
                    return NONE;
                }
            }
            for (int i = 0; i < PhysicsTuning.SUBCATEGORIES[category].length; i++) {
                if (inside(x, y, 0.32f, 0.74f - 0.13f * i,
                        0.23f, 0.085f)) {
                    subcategory = i;
                    return NONE;
                }
            }

            int visible = PhysicsTuning.count(category, subcategory);
            for (int i = 0; i < visible; i++) {
                float sliderY = 0.70f - i * 0.17f;
                if (inside(x, y, 0.60f, sliderY - 0.06f, 0.35f, 0.13f)) {
                    dragging = PhysicsTuning.parameterAt(category, subcategory, i);
                    setFromTouch(x);
                    return NONE;
                }
            }

            if (inside(x, y, 0.23f, 0.07f, 0.13f, 0.10f)) {
                tuning.resetDefaults();
                status = "DEFAULTS RESTORED";
            } else if (inside(x, y, 0.40f, 0.07f, 0.13f, 0.10f)) {
                persistPreset(0);
                status = "PRESET A SAVED";
            } else if (inside(x, y, 0.54f, 0.07f, 0.13f, 0.10f)) {
                status = tuning.loadPreset(0) ? "PRESET A LOADED" : "SAVE A FIRST";
            } else if (inside(x, y, 0.69f, 0.07f, 0.13f, 0.10f)) {
                persistPreset(1);
                status = "PRESET B SAVED";
            } else if (inside(x, y, 0.83f, 0.07f, 0.13f, 0.10f)) {
                status = tuning.loadPreset(1) ? "PRESET B LOADED" : "SAVE B FIRST";
            }
        }
        if (dragging >= 0 && Gdx.input.isTouched()) {
            setFromTouch(Gdx.input.getX() / width);
        }
        return NONE;
    }

    private void setFromTouch(float x) {
        PhysicsTuning.Parameter p = PhysicsTuning.PARAMETERS[dragging];
        float fraction = MathUtils.clamp((x - 0.615f) / 0.32f, 0f, 1f);
        tuning.set(dragging, p.minimum + fraction * (p.maximum - p.minimum));
        status = "LIVE: " + p.label;
    }

    private static boolean inside(float x, float y, float left, float bottom,
                                  float width, float height) {
        return x >= left && x <= left + width
                && y >= bottom && y <= bottom + height;
    }

    void draw() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.015f, 0.022f, 0.029f, 0.96f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0.92f, 0.40f, 0.12f, 1f);
        shapes.rect(0f, height * 0.98f, width, height * 0.02f);
        button(width, height, 0.03f, 0.86f, 0.15f, 0.11f, true);
        button(width, height, 0.59f, 0.86f, 0.12f, 0.11f, diagnostics.capturing());
        button(width, height, 0.72f, 0.86f, 0.12f, 0.11f, false);
        button(width, height, 0.85f, 0.86f, 0.12f, 0.11f, diagnostics.replaying());

        for (int i = 0; i < PhysicsTuning.CATEGORIES.length; i++) {
            button(width, height, 0.03f, 0.74f - 0.13f * i,
                    0.26f, 0.085f, i == category);
        }
        for (int i = 0; i < PhysicsTuning.SUBCATEGORIES[category].length; i++) {
            button(width, height, 0.32f, 0.74f - 0.13f * i,
                    0.23f, 0.085f, i == subcategory);
        }
        int visible = PhysicsTuning.count(category, subcategory);
        for (int i = 0; i < visible; i++) {
            int id = PhysicsTuning.parameterAt(category, subcategory, i);
            PhysicsTuning.Parameter p = PhysicsTuning.PARAMETERS[id];
            float y = height * (0.70f - i * 0.17f);
            float left = width * 0.615f;
            float track = width * 0.32f;
            float valueFraction = (tuning.get(id) - p.minimum)
                    / Math.max(0.001f, p.maximum - p.minimum);
            shapes.setColor(1f, 1f, 1f, 0.22f);
            shapes.rect(left, y - 3f, track, 6f);
            shapes.setColor(0.96f, 0.43f, 0.12f, 0.96f);
            shapes.rect(left, y - 3f, track * valueFraction, 6f);
            shapes.rect(left + track * valueFraction - 4f, y - 10f, 8f, 20f);
        }
        button(width, height, 0.23f, 0.07f, 0.13f, 0.10f, false);
        button(width, height, 0.40f, 0.07f, 0.13f, 0.10f, false);
        button(width, height, 0.54f, 0.07f, 0.13f, 0.10f, false);
        button(width, height, 0.69f, 0.07f, 0.13f, 0.10f, false);
        button(width, height, 0.83f, 0.07f, 0.13f, 0.10f, false);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(1f, 1f, 1f, 0.94f);
        font.getData().setScale(Math.max(0.9f, height / 720f * 1.0f));
        label("BACK", width * 0.066f, height * 0.929f);
        font.getData().setScale(Math.max(1.0f, height / 720f * 1.3f));
        label("PHYSICS LAB", width * 0.28f, height * 0.93f);
        font.getData().setScale(Math.max(0.75f, height / 720f * 0.90f));
        label("RECORD", width * 0.607f, height * 0.929f);
        label("EXPORT", width * 0.737f, height * 0.929f);
        label("REPLAY", width * 0.867f, height * 0.929f);
        font.getData().setScale(Math.max(0.7f, height / 720f * 0.87f));
        for (int i = 0; i < PhysicsTuning.CATEGORIES.length; i++) {
            label(PhysicsTuning.CATEGORIES[i], width * 0.041f,
                    height * (0.795f - 0.13f * i));
        }
        for (int i = 0; i < PhysicsTuning.SUBCATEGORIES[category].length; i++) {
            label(PhysicsTuning.SUBCATEGORIES[category][i], width * 0.332f,
                    height * (0.795f - 0.13f * i));
        }
        for (int i = 0; i < visible; i++) {
            int id = PhysicsTuning.parameterAt(category, subcategory, i);
            PhysicsTuning.Parameter p = PhysicsTuning.PARAMETERS[id];
            float y = 0.70f - i * 0.17f;
            String value = Math.abs(tuning.get(id)) >= 10f
                    ? String.format(Locale.US, "%.0f", tuning.get(id))
                    : String.format(Locale.US, "%.2f", tuning.get(id));
            label(p.label, width * 0.615f, height * (y + 0.063f));
            label(value + " " + p.units, width * 0.84f, height * (y + 0.063f));
        }
        label("RESET", width * 0.253f, height * 0.13f);
        label("SAVE A", width * 0.420f, height * 0.13f);
        label("LOAD A", width * 0.560f, height * 0.13f);
        label("SAVE B", width * 0.710f, height * 0.13f);
        label("LOAD B", width * 0.850f, height * 0.13f);
        font.setColor(1f, 1f, 1f, 0.65f);
        label(status, width * 0.04f, height * 0.29f);
        String captureState = diagnostics.capturing() ? "RECORDING" :
                diagnostics.replaying() ? "REPLAYING" : "READY";
        String captured = String.format(Locale.US,
                "%s   %.1f s / 90.0 s   REPLAY ERROR %.3f m",
                captureState, diagnostics.frameCount() * PhysicsDiagnostics.FIXED_DT,
                diagnostics.maxPositionError());
        label(captured, width * 0.04f, height * 0.335f);
        PhysicsV3MultibodyPlant.Telemetry telemetry = plant.telemetry();
        font.setColor(1f, 1f, 1f, 0.60f);
        font.getData().setScale(Math.max(0.66f, height / 720f * 0.76f));
        String loads = String.format(Locale.US,
                "SPEED %.1f mph   FRONT LOAD %.0f N   REAR LOAD %.0f N",
                plant.speed() * 2.23694f, telemetry.frontFz, telemetry.rearFz);
        String stability = String.format(Locale.US,
                "ROLL %.1f deg   STEER %.1f deg   TIRE USE F %.0f%% / R %.0f%%",
                plant.roll() * MathUtils.radiansToDegrees,
                plant.steerAngle() * MathUtils.radiansToDegrees,
                telemetry.frontUtilization * 100f,
                telemetry.rearUtilization * 100f);
        label(loads, width * 0.04f, height * 0.248f);
        label(stability, width * 0.04f, height * 0.205f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void button(float w, float h, float x, float y,
                        float width, float height, boolean selected) {
        shapes.setColor(selected ? 0.94f : 1f,
                selected ? 0.37f : 1f,
                selected ? 0.08f : 1f,
                selected ? 0.78f : 0.15f);
        shapes.rect(w * x, h * y, w * width, h * height);
    }

    private void label(String text, float x, float y) {
        font.draw(batch, text, x, y);
    }

    void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
