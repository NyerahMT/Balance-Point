from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    if text.count(old) != 1:
        raise SystemExit(f"patch anchor not unique ({text.count(old)}): {label}")
    return text.replace(old, new, 1)

root = Path('.')

game_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java'
game = game_path.read_text()

game = replace_once(
    game,
    '''        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.65f, 0.63f, 1f));
        environment.add(new DirectionalLight().set(0.95f, 0.88f, 0.76f, -0.45f, -1f, -0.28f));''',
    '''        environment = new Environment();
        // Daylight with real separation instead of CAD-viewport flat ambient. The cool fill
        // keeps shadow-facing geometry readable while the warm key gives terrain/bike shape.
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,
                0.36f, 0.395f, 0.42f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog,
                0.67f, 0.755f, 0.79f, 1f));
        environment.add(new DirectionalLight().set(
                1.08f, 1.00f, 0.88f, -0.48f, -1f, -0.31f));
        environment.add(new DirectionalLight().set(
                0.18f, 0.225f, 0.30f, 0.58f, -0.34f, 0.70f));''',
    'environment lighting')

game = replace_once(
    game,
    '''        Model road = box(b, ROAD_HALF_WIDTH * 2f, 0.08f, SEGMENT_LENGTH,
                material(0.13f, 0.14f, 0.145f));
        Model edgeLine = box(b, 0.10f, 0.025f, SEGMENT_LENGTH,
                material(0.82f, 0.80f, 0.72f));
        Model dash = box(b, 0.10f, 0.025f, 2.8f,
                material(0.90f, 0.74f, 0.26f));''',
    '''        Model road = box(b, ROAD_HALF_WIDTH * 2f, 0.08f, SEGMENT_LENGTH,
                material(0.095f, 0.102f, 0.108f));
        Model edgeLine = box(b, 0.10f, 0.025f, SEGMENT_LENGTH,
                material(0.91f, 0.90f, 0.84f));
        Model dash = box(b, 0.10f, 0.025f, 2.8f,
                material(0.96f, 0.72f, 0.15f));''',
    'road palette')

game = replace_once(
    game,
    '''        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.58f, 0.72f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);''',
    '''        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.66f, 0.76f, 0.80f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        drawSkyBackground();

        modelBatch.begin(camera);''',
    'sky render hook')

insert_anchor = '''    private void drawHud() {\n'''
insert_method = '''    /**
     * Lightweight atmospheric sky. Screen-space bands are intentionally cheap on mobile, but
     * the horizon tracks camera pitch so looking up/down does not leave the gradient glued to
     * one arbitrary screen height. Terrain fog uses the same horizon family of colors.
     */
    private void drawSkyBackground() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        float horizon = MathUtils.clamp(0.43f - camera.direction.y * 0.34f, 0.20f, 0.70f);
        int bands = 18;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < bands; i++) {
            float y0 = i / (float) bands;
            float y1 = (i + 1f) / bands;
            float sample = (y0 + y1) * 0.5f;
            float above = MathUtils.clamp((sample - horizon) / Math.max(0.001f, 1f - horizon), 0f, 1f);
            float below = MathUtils.clamp((horizon - sample) / Math.max(0.001f, horizon), 0f, 1f);

            // Pale blue-gray at the horizon, richer blue overhead, muted earth haze below.
            float r = sample >= horizon
                    ? MathUtils.lerp(0.69f, 0.22f, above)
                    : MathUtils.lerp(0.69f, 0.49f, below);
            float g = sample >= horizon
                    ? MathUtils.lerp(0.78f, 0.50f, above)
                    : MathUtils.lerp(0.78f, 0.61f, below);
            float b = sample >= horizon
                    ? MathUtils.lerp(0.82f, 0.78f, above)
                    : MathUtils.lerp(0.82f, 0.63f, below);
            shapes.setColor(r, g, b, 1f);
            shapes.rect(0f, y0 * h, w, (y1 - y0) * h + 1f);
        }
        shapes.end();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

'''
game = replace_once(game, insert_anchor, insert_method + insert_anchor, 'sky method insertion')
game_path.write_text(game)

terrain_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/TerrainVisuals.java'
terrain = terrain_path.read_text()

terrain = replace_once(
    terrain,
    '''        terrainMaterial = new Material(
                TextureAttribute.createDiffuse(terrainTexture),
                ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE));''',
    '''        terrainMaterial = new Material(
                TextureAttribute.createDiffuse(terrainTexture),
                // Small daylight tint keeps the baked albedo from reading oversaturated under
                // the stronger sun while retaining its grass/dirt/rock variation.
                ColorAttribute.createDiffuse(new Color(0.95f, 0.985f, 0.92f, 1f)),
                IntAttribute.createCullFace(GL20.GL_NONE));''',
    'terrain tint')

terrain = replace_once(
    terrain,
    '''        ModelBuilder b = new ModelBuilder();
        Material foliage = material(0.105f, 0.235f, 0.095f);
        Material trunk = material(0.25f, 0.17f, 0.09f);
        Model canopy = b.createCone(2f, 4.8f, 2f, 8, foliage,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model trunkModel = b.createCylinder(0.38f, 2.7f, 0.38f, 7, trunk,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(canopy);
        ownedModels.add(trunkModel);

        for (int i = 0; i < trees.length; i++) {
            trees[i] = new ModelInstance(canopy);
            trunks[i] = new ModelInstance(trunkModel);
            hideTree(i);
        }''',
    '''        ModelBuilder b = new ModelBuilder();
        Material foliageA = material(0.090f, 0.205f, 0.078f);
        Material foliageB = material(0.125f, 0.255f, 0.095f);
        Material foliageC = material(0.075f, 0.175f, 0.072f);
        Material trunk = material(0.235f, 0.155f, 0.085f);
        Model canopyA = b.createCone(2.05f, 4.8f, 2.05f, 8, foliageA,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model canopyB = b.createCone(2.30f, 5.25f, 2.30f, 9, foliageB,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model canopyC = b.createCone(1.90f, 4.35f, 1.90f, 7, foliageC,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model trunkModel = b.createCylinder(0.38f, 2.7f, 0.38f, 7, trunk,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(canopyA);
        ownedModels.add(canopyB);
        ownedModels.add(canopyC);
        ownedModels.add(trunkModel);

        for (int i = 0; i < trees.length; i++) {
            Model canopy = (i % 3 == 0) ? canopyA : (i % 3 == 1 ? canopyB : canopyC);
            trees[i] = new ModelInstance(canopy);
            trunks[i] = new ModelInstance(trunkModel);
            hideTree(i);
        }''',
    'tree model variation')

terrain = replace_once(
    terrain,
    '''        float keep = hash01(worldIndex * 43 + t, salt);
        if (keep < 0.22f) {
            hideTree(index);
            return;
        }''',
    '''        float keep = hash01(worldIndex * 43 + t, salt);
        // Density varies in broad ~100 m groves rather than being statistically identical
        // every chunk. This creates natural open meadows followed by denser tree lines.
        int groveCell = Math.floorDiv(worldIndex, 4);
        float grove = hash01(groveCell, salt + 71);
        float keepThreshold = MathUtils.lerp(0.08f, 0.40f, grove);
        if (keep < keepThreshold) {
            hideTree(index);
            return;
        }''',
    'tree density variation')

terrain = replace_once(
    terrain,
    '''        float scale = 0.70f + hash01(worldIndex * 67 + t, salt + 13) * 0.72f;
        float ground = groundHeight(x, treeZ);
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);''',
    '''        float scale = 0.70f + hash01(worldIndex * 67 + t, salt + 13) * 0.72f;
        float widthVariation = 0.82f + hash01(worldIndex * 71 + t, salt + 17) * 0.38f;
        float heightVariation = 0.90f + hash01(worldIndex * 73 + t, salt + 19) * 0.26f;
        float yawDeg = hash01(worldIndex * 79 + t, salt + 23) * 360f;
        float ground = groundHeight(x, treeZ);
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .rotate(com.badlogic.gdx.math.Vector3.Y, yawDeg)
                .scale(scale * 0.92f, scale * heightVariation, scale * 0.92f);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale * heightVariation, treeZ)
                .rotate(com.badlogic.gdx.math.Vector3.Y, yawDeg)
                .scale(scale * widthVariation, scale * heightVariation,
                        scale * (1.02f - (widthVariation - 0.82f) * 0.20f));''',
    'tree transform variation')

terrain_path.write_text(terrain)
