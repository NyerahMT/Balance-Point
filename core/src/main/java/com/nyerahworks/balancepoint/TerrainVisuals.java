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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Deterministic roadside terrain shared by rendering and the bike ground model.
 *
 * The road sits inside a broad flat rural corridor. Farther out the ground rolls through
 * a short, rideable grass/dirt foothill before blending into steep mountain terrain. The
 * flat edge and terrain profile vary slowly along Z so the transition never reads as a
 * perfectly straight wall and broad clearings remain available for trails/residential use.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final float SEGMENT_LENGTH = 20f;
    private static final int SEGMENTS = 18;
    private static final int BANDS = 7;
    private static final int SHALLOW_BANDS = 3;
    private static final int TREES_PER_SEGMENT = 5;

    private static final float GRASS_INNER_EDGE = 6.40f;
    private static final float MOUNTAIN_VISUAL_RUN = 31f;

    private final ModelInstance[] flatLeft = new ModelInstance[SEGMENTS];
    private final ModelInstance[] flatRight = new ModelInstance[SEGMENTS];
    private final ModelInstance[] gravelLeft = new ModelInstance[SEGMENTS];
    private final ModelInstance[] gravelRight = new ModelInstance[SEGMENTS];
    private final ModelInstance[][] slopeLeft = new ModelInstance[SEGMENTS][BANDS];
    private final ModelInstance[][] slopeRight = new ModelInstance[SEGMENTS][BANDS];
    private final ModelInstance[] ridgeLeft = new ModelInstance[SEGMENTS];
    private final ModelInstance[] ridgeRight = new ModelInstance[SEGMENTS];
    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];

    TerrainVisuals(Array<Model> ownedModels) {
        ModelBuilder b = new ModelBuilder();

        Material grassA = material(0.31f, 0.40f, 0.25f);
        Material grassB = material(0.27f, 0.36f, 0.22f);
        Material gravel = material(0.42f, 0.40f, 0.34f);
        Material slopeGrass = material(0.26f, 0.36f, 0.21f);
        Material slopeDirt = material(0.37f, 0.32f, 0.22f);
        Material mountainLow = material(0.22f, 0.29f, 0.20f);
        Material mountainHigh = material(0.20f, 0.23f, 0.19f);
        Material ridge = material(0.16f, 0.21f, 0.17f);
        Material foliage = material(0.12f, 0.25f, 0.12f);
        Material trunk = material(0.26f, 0.18f, 0.10f);

        // Unit-width strips are scaled and rotated into the deterministic terrain profile.
        Model flatA = b.createBox(1f, 0.08f, SEGMENT_LENGTH * 1.06f, grassA,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model flatB = b.createBox(1f, 0.08f, SEGMENT_LENGTH * 1.06f, grassB,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model gravelStrip = b.createBox(2.2f, 0.05f, SEGMENT_LENGTH * 1.04f, gravel,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model shallowGrass = b.createBox(1f, 0.10f, SEGMENT_LENGTH * 1.08f, slopeGrass,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model shallowDirt = b.createBox(1f, 0.10f, SEGMENT_LENGTH * 1.08f, slopeDirt,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model mountainA = b.createBox(1f, 0.14f, SEGMENT_LENGTH * 1.10f, mountainLow,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model mountainB = b.createBox(1f, 0.16f, SEGMENT_LENGTH * 1.12f, mountainHigh,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model farRidge = b.createSphere(2f, 2f, 2f, 12, 6, ridge,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model canopy = b.createCone(2f, 4.8f, 2f, 8, foliage,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model trunkModel = b.createCylinder(0.38f, 2.7f, 0.38f, 7, trunk,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        ownedModels.add(flatA);
        ownedModels.add(flatB);
        ownedModels.add(gravelStrip);
        ownedModels.add(shallowGrass);
        ownedModels.add(shallowDirt);
        ownedModels.add(mountainA);
        ownedModels.add(mountainB);
        ownedModels.add(farRidge);
        ownedModels.add(canopy);
        ownedModels.add(trunkModel);

        for (int i = 0; i < SEGMENTS; i++) {
            flatLeft[i] = new ModelInstance((i & 1) == 0 ? flatA : flatB);
            flatRight[i] = new ModelInstance((i & 1) == 0 ? flatB : flatA);
            gravelLeft[i] = new ModelInstance(gravelStrip);
            gravelRight[i] = new ModelInstance(gravelStrip);
            ridgeLeft[i] = new ModelInstance(farRidge);
            ridgeRight[i] = new ModelInstance(farRidge);

            for (int band = 0; band < BANDS; band++) {
                Model leftModel;
                Model rightModel;
                if (band < SHALLOW_BANDS) {
                    // Dirt appears in irregular foothill patches instead of as a hard biome line.
                    leftModel = ((i + band) % 4 == 0) ? shallowDirt : shallowGrass;
                    rightModel = ((i * 2 + band) % 5 == 0) ? shallowDirt : shallowGrass;
                } else {
                    leftModel = band < 5 ? mountainA : mountainB;
                    rightModel = band < 5 ? mountainA : mountainB;
                }
                slopeLeft[i][band] = new ModelInstance(leftModel);
                slopeRight[i][band] = new ModelInstance(rightModel);
            }
        }

        for (int i = 0; i < trees.length; i++) {
            trees[i] = new ModelInstance(canopy);
            trunks[i] = new ModelInstance(trunkModel);
        }
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    /** World-space terrain height used by both the renderer and motorcycle ground contact. */
    float groundHeight(float x, float z) {
        float ax = Math.abs(x);
        float flatEdge = flatEdge(z);
        if (ax <= flatEdge) return 0f;

        float shallowWidth = shallowWidth(z);
        float shallowRise = shallowRise(z);
        float shallowEnd = flatEdge + shallowWidth;
        if (ax <= shallowEnd) {
            float t = MathUtils.clamp((ax - flatEdge) / shallowWidth, 0f, 1f);
            // Gentle roll-in, then progressively steeper. No abrupt ramp at the field edge.
            float curved = 0.18f * t + 0.82f * t * t;
            return shallowRise * curved;
        }

        float dx = ax - shallowEnd;
        float mountainSlope = mountainSlope(z);
        float blend = 2.4f;
        float mountainRise;
        if (dx < blend) {
            // Ease from the shallow hill into the mountain rather than forming a crease.
            mountainRise = mountainSlope * dx * dx / (2f * blend);
        } else {
            float afterBlend = dx - blend;
            mountainRise = mountainSlope * (dx - blend * 0.5f)
                    + 0.010f * afterBlend * afterBlend;
        }

        float maxMountainRise = 22f + smoothNoise(z, 125f, 107) * 10f;
        return shallowRise + Math.min(mountainRise, maxMountainRise);
    }

    float groundSlopeX(float x, float z) {
        final float e = 0.30f;
        return (groundHeight(x + e, z) - groundHeight(x - e, z)) / (2f * e);
    }

    float groundSlopeZ(float x, float z) {
        final float e = 0.55f;
        return (groundHeight(x, z + e) - groundHeight(x, z - e)) / (2f * e);
    }

    void update(float bikeZ) {
        float firstCenter = (float) Math.floor((bikeZ - 80f) / SEGMENT_LENGTH) * SEGMENT_LENGTH
                + SEGMENT_LENGTH * 0.5f;

        int treeIndex = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            float z = firstCenter + i * SEGMENT_LENGTH;
            float edge = flatEdge(z);
            float shallow = shallowWidth(z);

            float flatWidth = Math.max(1f, edge - GRASS_INNER_EDGE);
            flatLeft[i].transform.idt()
                    .translate(-(GRASS_INNER_EDGE + flatWidth * 0.5f), -0.04f, z)
                    .scale(flatWidth, 1f, 1f);
            flatRight[i].transform.idt()
                    .translate(GRASS_INNER_EDGE + flatWidth * 0.5f, -0.04f, z)
                    .scale(flatWidth, 1f, 1f);
            gravelLeft[i].transform.setToTranslation(-5.3f, -0.015f, z);
            gravelRight[i].transform.setToTranslation(5.3f, -0.015f, z);

            float inner = edge;
            for (int band = 0; band < BANDS; band++) {
                float width;
                if (band < SHALLOW_BANDS) {
                    width = shallow / SHALLOW_BANDS;
                } else {
                    // Widen the bands as the terrain gets steeper so the mountain reads as
                    // a continuous mass rather than a stack of equal terraces.
                    width = band == 3 ? 4.5f : band == 4 ? 6.0f : band == 5 ? 8.0f : 12.5f;
                }
                float outer = inner + width;
                float h1 = groundHeight(inner, z);
                float h2 = groundHeight(outer, z);
                placeSlopeStrip(slopeLeft[i][band], -1f, inner, width, h1, h2, z);
                placeSlopeStrip(slopeRight[i][band], 1f, inner, width, h1, h2, z);
                inner = outer;
            }

            float ridgeX = edge + shallow + MOUNTAIN_VISUAL_RUN + 18f;
            float ridgeBase = groundHeight(edge + shallow + MOUNTAIN_VISUAL_RUN, z);
            float ridgeHeightL = 13f + smoothNoise(z, 82f, 131) * 13f;
            float ridgeHeightR = 13f + smoothNoise(z + 31f, 91f, 137) * 13f;
            ridgeLeft[i].transform.setToTranslation(-ridgeX, ridgeBase + ridgeHeightL * 0.25f, z + 3f)
                    .scale(18f, ridgeHeightL, 16f);
            ridgeRight[i].transform.setToTranslation(ridgeX, ridgeBase + ridgeHeightR * 0.25f, z - 4f)
                    .scale(18f, ridgeHeightR, 16f);

            for (int t = 0; t < TREES_PER_SEGMENT; t++) {
                float localZ = z - SEGMENT_LENGTH * 0.46f
                        + (t + 0.5f) * (SEGMENT_LENGTH * 0.92f / TREES_PER_SEGMENT);
                placeTree(treeIndex++, -1f, i, t, localZ);
                placeTree(treeIndex++, 1f, i, t, localZ);
            }
        }
    }

    private void placeSlopeStrip(ModelInstance instance, float side, float inner,
                                 float width, float h1, float h2, float z) {
        float rise = h2 - h1;
        float angle = (float) Math.atan2(rise, width);
        float projectedCos = Math.max(0.22f, MathUtils.cos(angle));
        float localWidth = width / projectedCos;
        float signedAngle = side * angle * MathUtils.radiansToDegrees;
        instance.transform.idt()
                .translate(side * (inner + width * 0.5f), (h1 + h2) * 0.5f - 0.05f, z)
                .rotate(Vector3.Z, signedAngle)
                .scale(localWidth, 1f, 1f);
    }

    private void placeTree(int index, float side, int segmentIndex, int t, float z) {
        int seed = segmentIndex * 17 + t;
        float edge = flatEdge(z);
        float random = hash01(seed, side < 0f ? 61 : 67);
        // Most trees live around the flat/foothill transition; some climb the low slope.
        float outward = -4f + random * (shallowWidth(z) + 9f);
        float x = side * Math.max(GRASS_INNER_EDGE + 4f, edge + outward);
        float scale = 0.72f + hash01(seed, 71) * 0.62f;
        float zJitter = (hash01(seed, 73) - 0.5f) * 2.8f;
        float treeZ = z + zJitter;
        float ground = groundHeight(x, treeZ);
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);
    }

    private static float flatEdge(float z) {
        // 24-34 m: long broad clearings remain possible while other stretches tighten up.
        return 24f + smoothNoise(z, 95f, 83) * 10f;
    }

    private static float shallowWidth(float z) {
        // Roughly 20-27 ft of genuinely rideable hillside before mountain terrain takes over.
        return 6.2f + smoothNoise(z, 72f, 89) * 2.0f;
    }

    private static float shallowRise(float z) {
        return 1.7f + smoothNoise(z, 88f, 97) * 1.5f;
    }

    private static float mountainSlope(float z) {
        // dy/dx = 0.95-1.65 -> about 44-59 degrees before the extra curvature term.
        return 0.95f + smoothNoise(z, 110f, 101) * 0.70f;
    }

    private static float smoothNoise(float z, float wavelength, int salt) {
        float cell = z / wavelength;
        int i0 = MathUtils.floor(cell);
        float t = cell - i0;
        t = t * t * (3f - 2f * t);
        return MathUtils.lerp(hash01(i0, salt), hash01(i0 + 1, salt), t);
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
        for (int i = 0; i < SEGMENTS; i++) {
            for (int band = BANDS - 1; band >= 0; band--) {
                batch.render(slopeLeft[i][band], environment);
                batch.render(slopeRight[i][band], environment);
            }
        }
        for (ModelInstance m : ridgeLeft) batch.render(m, environment);
        for (ModelInstance m : ridgeRight) batch.render(m, environment);
        for (ModelInstance m : trunks) batch.render(m, environment);
        for (ModelInstance m : trees) batch.render(m, environment);
    }
}
