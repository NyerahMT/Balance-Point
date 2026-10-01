from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
terrain_path = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/TerrainVisuals.java"
game_path = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java"

terrain = terrain_path.read_text()
game = game_path.read_text()

old = '''    /**
     * Height on the same triangle surface used by the render mesh. This is deliberately not
     * bilinear interpolation: each source cell is split along the same diagonal as the mesh so
     * collision cannot disagree with the visible ground between vertices.
     */
    float groundHeight(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        CellSample c = cell(x, z);
        if (c.fx + c.fz <= 1f) {
            return c.h00 + c.fx * (c.h10 - c.h00) + c.fz * (c.h01 - c.h00);
        }
        return c.h11 + (1f - c.fx) * (c.h01 - c.h11)
                + (1f - c.fz) * (c.h10 - c.h11);
    }

    float groundSlopeX(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        // Average grade over a 6 m window instead of inheriting the slope of one 1.5 m
        // triangle. Geometry still uses the same heightfield, but normals/contact response
        // no longer jump at every cell diagonal.
        float r = sampleSpacing * 2f;
        return (groundHeight(x + r, z) - groundHeight(x - r, z)) / (2f * r);
    }

    float groundSlopeZ(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        float r = sampleSpacing * 2f;
        return (groundHeight(x, z + r) - groundHeight(x, z - r)) / (2f * r);
    }

    private CellSample cell(float x, float z) {
        float gx = MathUtils.clamp((x - originX) / sampleSpacing, 0f, mapWidth - 1.001f);
        float gz = MathUtils.clamp((z - originZ) / sampleSpacing, 0f, mapHeight - 1.001f);
        int ix = MathUtils.floor(gx);
        int iz = MathUtils.floor(gz);
        CellSample c = cellScratch;
        c.fx = gx - ix;
        c.fz = gz - iz;
        c.h00 = decoded(ix, iz);
        c.h10 = decoded(ix + 1, iz);
        c.h01 = decoded(ix, iz + 1);
        c.h11 = decoded(ix + 1, iz + 1);
        return c;
    }

    private final CellSample cellScratch = new CellSample();
'''

new = '''    // The terrain underlay is intentionally sunk very slightly beneath the separate road mesh
    // so the asphalt never z-fights with the flat corridor. Keep that offset in the shared
    // vertex sampler instead of applying it only at render time; off-road collision then lands
    // on the exact triangles the player sees.
    private static final float TERRAIN_Y_OFFSET = -0.028f;

    /**
     * Height of the actual rendered terrain triangle at a world point. The render mesh uses a
     * dense 1.5 m grid near the road and progressively wider X columns farther out. Collision
     * resolves against those exact X columns and the same global 1.5 m Z rows, then uses the
     * same a-b-c / b-d-c diagonal split as buildSegmentModel().
     */
    float groundHeight(float x, float z) {
        // The dedicated road mesh/physics owns the corridor. The terrain underlay stays sunk
        // beneath it and begins contributing immediately outside the road edge.
        if (Math.abs(x) <= ROAD_EDGE) return 0f;

        int xi = renderXCell(x);
        float x0 = X_SAMPLES[xi];
        float x1 = X_SAMPLES[xi + 1];
        float zStep = SEGMENT_LENGTH / Z_CELLS;
        float z0 = MathUtils.floor(z / zStep) * zStep;
        float z1 = z0 + zStep;

        float fx = MathUtils.clamp((x - x0) / (x1 - x0), 0f, 1f);
        float fz = MathUtils.clamp((z - z0) / zStep, 0f, 1f);
        float h00 = meshVertexHeight(x0, z0);
        float h10 = meshVertexHeight(x1, z0);
        float h01 = meshVertexHeight(x0, z1);
        float h11 = meshVertexHeight(x1, z1);

        if (fx + fz <= 1f) {
            return h00 + fx * (h10 - h00) + fz * (h01 - h00);
        }
        return h11 + (1f - fx) * (h01 - h11)
                + (1f - fz) * (h10 - h11);
    }

    float groundSlopeX(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        // Average grade over a 6 m window instead of inheriting the slope of one triangle.
        // The sampled heights still come from the exact rendered surface.
        float r = sampleSpacing * 2f;
        return (groundHeight(x + r, z) - groundHeight(x - r, z)) / (2f * r);
    }

    float groundSlopeZ(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        float r = sampleSpacing * 2f;
        return (groundHeight(x, z + r) - groundHeight(x, z - r)) / (2f * r);
    }

    /** Height of one render-mesh vertex before triangle interpolation. */
    private float meshVertexHeight(float x, float z) {
        float base = Math.abs(x) <= ROAD_EDGE ? 0f : sourceHeight(x, z);
        return base + TERRAIN_Y_OFFSET;
    }

    /**
     * Sample the baked 1.5 m source heightfield. This is used only to create render vertices;
     * wheel collision must use groundHeight() so coarsened render columns cannot diverge from
     * physics between vertices.
     */
    private float sourceHeight(float x, float z) {
        CellSample c = sourceCell(x, z);
        if (c.fx + c.fz <= 1f) {
            return c.h00 + c.fx * (c.h10 - c.h00) + c.fz * (c.h01 - c.h00);
        }
        return c.h11 + (1f - c.fx) * (c.h01 - c.h11)
                + (1f - c.fz) * (c.h10 - c.h11);
    }

    private int renderXCell(float x) {
        if (x <= X_SAMPLES[0]) return 0;
        int last = X_SAMPLES.length - 1;
        if (x >= X_SAMPLES[last]) return last - 1;

        int lo = 0;
        int hi = last;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (X_SAMPLES[mid] <= x) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private CellSample sourceCell(float x, float z) {
        float gx = MathUtils.clamp((x - originX) / sampleSpacing, 0f, mapWidth - 1.001f);
        float gz = MathUtils.clamp((z - originZ) / sampleSpacing, 0f, mapHeight - 1.001f);
        int ix = MathUtils.floor(gx);
        int iz = MathUtils.floor(gz);
        CellSample c = cellScratch;
        c.fx = gx - ix;
        c.fz = gz - iz;
        c.h00 = decoded(ix, iz);
        c.h10 = decoded(ix + 1, iz);
        c.h01 = decoded(ix, iz + 1);
        c.h11 = decoded(ix + 1, iz + 1);
        return c;
    }

    private final CellSample cellScratch = new CellSample();
'''

if old not in terrain:
    raise SystemExit("Terrain sampling block did not match current source")
terrain = terrain.replace(old, new, 1)

old = '''                float y = groundHeight(x, z) - 0.028f;
'''
new = '''                float y = meshVertexHeight(x, z);
'''
if old not in terrain:
    raise SystemExit("Terrain vertex height line did not match current source")
terrain = terrain.replace(old, new, 1)

old = '''                // Same diagonal convention as groundHeight(): a-b-c and b-d-c.
'''
new = '''                // Collision uses this exact diagonal convention: a-b-c and b-d-c.
'''
if old not in terrain:
    raise SystemExit("Terrain diagonal comment did not match current source")
terrain = terrain.replace(old, new, 1)

old = '''        if (inside(x, y, bx, h * 0.55f, bw, bh)) {
            resumeRide();
        } else if (inside(x, y, bx, h * 0.44f, bw, bh)) {
            restartRide();
        } else if (inside(x, y, bx, h * 0.33f, bw, bh)) {
            enterMainMenu();
        }
'''
new = '''        if (inside(x, y, bx, h * 0.56f, bw, bh)) {
            resumeRide();
        } else if (inside(x, y, bx, h * 0.45f, bw, bh)) {
            restartRide();
        } else if (inside(x, y, bx, h * 0.34f, bw, bh)) {
            toggleQuality();
        } else if (inside(x, y, bx, h * 0.23f, bw, bh)) {
            enterMainMenu();
        }
'''
if old not in game:
    raise SystemExit("Pause input block did not match current source")
game = game.replace(old, new, 1)

old = '''    private void drawPauseMenu(int w, int h, float min) {
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
'''
new = '''    private void drawPauseMenu(int w, int h, float min) {
        float panelX = w * 0.29f;
        float panelY = h * 0.17f;
        float panelW = w * 0.42f;
        float panelH = h * 0.64f;
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
        drawUiButtonShape(bx, h * 0.56f, bw, bh, true);
        drawUiButtonShape(bx, h * 0.45f, bw, bh, false);
        drawUiButtonShape(bx, h * 0.34f, bw, bh, false);
        drawUiButtonShape(bx, h * 0.23f, bw, bh, false);
        shapes.end();

        spriteBatch.setProjectionMatrix(uiCamera.combined);
        spriteBatch.begin();
        font.setColor(1f, 1f, 1f, 0.96f);
        font.getData().setScale(Math.max(1.25f, h / 720f * 1.65f));
        font.draw(spriteBatch, "PAUSED", w * 0.405f, h * 0.735f);
        font.getData().setScale(Math.max(0.82f, h / 720f * 1.02f));
        font.draw(spriteBatch, "RESUME", bx + bw * 0.10f, h * 0.56f + bh * 0.61f);
        font.draw(spriteBatch, "RESTART", bx + bw * 0.10f, h * 0.45f + bh * 0.61f);
        font.draw(spriteBatch, highQuality ? "GRAPHICS   HIGH" : "GRAPHICS   LOW",
                bx + bw * 0.10f, h * 0.34f + bh * 0.61f);
        font.draw(spriteBatch, "MAIN MENU", bx + bw * 0.10f, h * 0.23f + bh * 0.61f);

        font.getData().setScale(Math.max(0.60f, h / 720f * 0.72f));
        font.setColor(1f, 1f, 1f, 0.52f);
        String rideStats = String.format(java.util.Locale.US, "BEST WHEELIE  %.1fs", bestWheelieTime);
        font.draw(spriteBatch, rideStats, bx + bw * 0.10f, h * 0.195f);
        spriteBatch.end();
    }
'''
if old not in game:
    raise SystemExit("Pause menu draw block did not match current source")
game = game.replace(old, new, 1)

old = '''        font.draw(spriteBatch, highQuality ? "QUALITY   HIGH" : "QUALITY   LOW",
'''
new = '''        font.draw(spriteBatch, highQuality ? "GRAPHICS   HIGH" : "GRAPHICS   LOW",
'''
if old not in game:
    raise SystemExit("Main menu quality label did not match current source")
game = game.replace(old, new, 1)

terrain_path.write_text(terrain)
game_path.write_text(game)
print("Patched exact render/collision terrain surface and pause UI")
