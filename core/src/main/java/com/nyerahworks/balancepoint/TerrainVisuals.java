package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;

/** Lightweight deterministic rural terrain dressing around the flat road corridor. */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final float SEGMENT_LENGTH = 20f;
    private static final int SEGMENTS = 18;
    private static final int TREES_PER_SEGMENT = 5;

    private final ModelInstance[] flatLeft = new ModelInstance[SEGMENTS];
    private final ModelInstance[] flatRight = new ModelInstance[SEGMENTS];
    private final ModelInstance[] gravelLeft = new ModelInstance[SEGMENTS];
    private final ModelInstance[] gravelRight = new ModelInstance[SEGMENTS];
    private final ModelInstance[] hillLeftNear = new ModelInstance[SEGMENTS];
    private final ModelInstance[] hillRightNear = new ModelInstance[SEGMENTS];
    private final ModelInstance[] hillLeftFar = new ModelInstance[SEGMENTS];
    private final ModelInstance[] hillRightFar = new ModelInstance[SEGMENTS];
    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];

    TerrainVisuals(Array<Model> ownedModels) {
        ModelBuilder b = new ModelBuilder();
        Material grassA = material(0.31f, 0.39f, 0.25f);
        Material grassB = material(0.25f, 0.34f, 0.21f);
        Material gravel = material(0.42f, 0.40f, 0.34f);
        Material hillNear = material(0.22f, 0.31f, 0.20f);
        Material hillFar = material(0.17f, 0.25f, 0.19f);
        Material foliage = material(0.12f, 0.25f, 0.12f);
        Material trunk = material(0.26f, 0.18f, 0.10f);

        Model flatA = b.createBox(22f, 0.08f, SEGMENT_LENGTH, grassA,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model flatB = b.createBox(22f, 0.08f, SEGMENT_LENGTH, grassB,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model gravelStrip = b.createBox(2.2f, 0.05f, SEGMENT_LENGTH, gravel,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model nearHill = b.createSphere(2f, 2f, 2f, 12, 6, hillNear,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model farHill = b.createSphere(2f, 2f, 2f, 12, 6, hillFar,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model canopy = b.createCone(2f, 4.8f, 2f, 8, foliage,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model trunkModel = b.createCylinder(0.38f, 2.7f, 0.38f, 7, trunk,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        ownedModels.add(flatA);
        ownedModels.add(flatB);
        ownedModels.add(gravelStrip);
        ownedModels.add(nearHill);
        ownedModels.add(farHill);
        ownedModels.add(canopy);
        ownedModels.add(trunkModel);

        for (int i = 0; i < SEGMENTS; i++) {
            flatLeft[i] = new ModelInstance((i & 1) == 0 ? flatA : flatB);
            flatRight[i] = new ModelInstance((i & 1) == 0 ? flatB : flatA);
            gravelLeft[i] = new ModelInstance(gravelStrip);
            gravelRight[i] = new ModelInstance(gravelStrip);
            hillLeftNear[i] = new ModelInstance(nearHill);
            hillRightNear[i] = new ModelInstance(nearHill);
            hillLeftFar[i] = new ModelInstance(farHill);
            hillRightFar[i] = new ModelInstance(farHill);
        }
        for (int i = 0; i < trees.length; i++) {
            trees[i] = new ModelInstance(canopy);
            trunks[i] = new ModelInstance(trunkModel);
        }
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    void update(float bikeZ) {
        float firstCenter = (float) Math.floor((bikeZ - 80f) / SEGMENT_LENGTH) * SEGMENT_LENGTH
                + SEGMENT_LENGTH * 0.5f;

        int treeIndex = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            float z = firstCenter + i * SEGMENT_LENGTH;
            int wi = (int) Math.floor(z / SEGMENT_LENGTH);

            flatLeft[i].transform.setToTranslation(-16f, -0.07f, z);
            flatRight[i].transform.setToTranslation(16f, -0.07f, z);
            gravelLeft[i].transform.setToTranslation(-5.3f, -0.015f, z);
            gravelRight[i].transform.setToTranslation(5.3f, -0.015f, z);

            float nearHeightL = 5.2f + hash01(wi, 11) * 4.2f;
            float nearHeightR = 5.2f + hash01(wi, 17) * 4.2f;
            float nearXL = 39f + hash01(wi, 23) * 7f;
            float nearXR = 39f + hash01(wi, 29) * 7f;
            hillLeftNear[i].transform.setToTranslation(-nearXL, nearHeightL * 0.38f - 0.6f, z)
                    .scale(13f + hash01(wi, 31) * 6f, nearHeightL, 11f + hash01(wi, 37) * 8f);
            hillRightNear[i].transform.setToTranslation(nearXR, nearHeightR * 0.38f - 0.6f, z)
                    .scale(13f + hash01(wi, 41) * 6f, nearHeightR, 11f + hash01(wi, 43) * 8f);

            float farHeightL = 10f + hash01(wi, 47) * 7f;
            float farHeightR = 10f + hash01(wi, 53) * 7f;
            hillLeftFar[i].transform.setToTranslation(-67f, farHeightL * 0.40f - 1.0f, z + 5f)
                    .scale(24f, farHeightL, 19f);
            hillRightFar[i].transform.setToTranslation(67f, farHeightR * 0.40f - 1.0f, z - 4f)
                    .scale(24f, farHeightR, 19f);

            for (int t = 0; t < TREES_PER_SEGMENT; t++) {
                float localZ = z - SEGMENT_LENGTH * 0.46f
                        + (t + 0.5f) * (SEGMENT_LENGTH * 0.92f / TREES_PER_SEGMENT);
                placeTree(treeIndex++, -1f, wi, t, localZ);
                placeTree(treeIndex++, 1f, wi, t, localZ);
            }
        }
    }

    private void placeTree(int index, float side, int wi, int t, float z) {
        float random = hash01(wi * 13 + t, side < 0f ? 61 : 67);
        float x = side * (29f + random * 12f);
        float scale = 0.72f + hash01(wi * 7 + t, 71) * 0.62f;
        float zJitter = (hash01(wi * 5 + t, 73) - 0.5f) * 2.8f;
        trunks[index].transform.setToTranslation(x, 1.22f * scale, z + zJitter).scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, 4.15f * scale, z + zJitter).scale(scale, scale, scale);
    }

    private static float hash01(int a, int b) {
        int x = a * 0x1f123bb5 ^ b * 0x5f356495;
        x ^= x >>> 16;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        return (x & 0x7fffffff) / (float) 0x7fffffff;
    }

    void render(ModelBatch batch, Environment environment) {
        for (ModelInstance m : flatLeft) batch.render(m, environment);
        for (ModelInstance m : flatRight) batch.render(m, environment);
        for (ModelInstance m : gravelLeft) batch.render(m, environment);
        for (ModelInstance m : gravelRight) batch.render(m, environment);
        for (ModelInstance m : hillLeftFar) batch.render(m, environment);
        for (ModelInstance m : hillRightFar) batch.render(m, environment);
        for (ModelInstance m : hillLeftNear) batch.render(m, environment);
        for (ModelInstance m : hillRightNear) batch.render(m, environment);
        for (ModelInstance m : trunks) batch.render(m, environment);
        for (ModelInstance m : trees) batch.render(m, environment);
    }
}
