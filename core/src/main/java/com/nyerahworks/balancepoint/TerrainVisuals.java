package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Deterministic roadside terrain shared by rendering and motorcycle ground contact.
 *
 * Terrain is generated as persistent low-poly heightfield segments instead of long stacked
 * strips. The left and right sides are independent: some world stretches remain broad flat
 * fields for trails/residential areas while others pinch inward into foothills and genuine
 * mountain sides. Segment slots are keyed by world index so terrain and trees do not reshuffle
 * while the rider moves through the world.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final float SEGMENT_LENGTH = 20f;
    private static final int SEGMENTS = 18;
    private static final int Z_CELLS = 5;
    private static final int TREES_PER_SEGMENT = 6;

    private static final float ROAD_EDGE = 4.22f;
    private static final float GRASS_INNER_EDGE = 6.40f;

    // Denser samples around the region where flat fields usually transition into foothills.
    private static final float[] X_SAMPLES = {
            ROAD_EDGE, GRASS_INNER_EDGE, 10f, 14f, 19f, 25f,
            32f, 40f, 50f, 62f, 76f, 94f
    };

    private static final int SURFACE_GRAVEL = 0;
    private static final int SURFACE_GRASS = 1;
    private static final int SURFACE_DIRT = 2;
    private static final int SURFACE_MOUNTAIN_LOW = 3;
    private static final int SURFACE_MOUNTAIN_HIGH = 4;
    private static final int SURFACE_COUNT = 5;

    private final Material[] surfaceMaterials = new Material[SURFACE_COUNT];
    private final Model[] terrainModels = new Model[SEGMENTS];
    private final ModelInstance[] terrainInstances = new ModelInstance[SEGMENTS];
    private final int[] terrainWorldIndex = new int[SEGMENTS];

    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];

    TerrainVisuals(Array<Model> ownedModels) {
        surfaceMaterials[SURFACE_GRAVEL] = material(0.42f, 0.40f, 0.34f);
        surfaceMaterials[SURFACE_GRASS] = material(0.30f, 0.40f, 0.24f);
        surfaceMaterials[SURFACE_DIRT] = material(0.37f, 0.31f, 0.21f);
        surfaceMaterials[SURFACE_MOUNTAIN_LOW] = material(0.22f, 0.29f, 0.20f);
        surfaceMaterials[SURFACE_MOUNTAIN_HIGH] = material(0.17f, 0.21f, 0.17f);

        for (Material m : surfaceMaterials) {
            // Terrain quads are generated from both sides of the road. Double-sided rendering
            // avoids winding sensitivity while keeping the mesh-generation code simple.
            m.set(IntAttribute.createCullFace(GL20.GL_NONE));
        }

        for (int i = 0; i < terrainWorldIndex.length; i++) {
            terrainWorldIndex[i] = Integer.MIN_VALUE;
        }

        ModelBuilder b = new ModelBuilder();
        Material foliage = material(0.12f, 0.25f, 0.12f);
        Material trunk = material(0.26f, 0.18f, 0.10f);
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
        }
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    /** World-space terrain height used by both the renderer and motorcycle ground contact. */
    float groundHeight(float x, float z) {
        float ax = Math.abs(x);
        if (ax <= ROAD_EDGE) return 0f;

        float side = x < 0f ? -1f : 1f;
        float flatEdge = flatEdge(z, side);
        if (ax <= flatEdge) return 0f;

        float presence = mountainPresence(z, side);
        float shallowWidth = shallowWidth(z, side);
        float shallowRise = shallowRise(z, side);
        float shallowEnd = flatEdge + shallowWidth;

        float height;
        if (ax <= shallowEnd) {
            float t = MathUtils.clamp((ax - flatEdge) / shallowWidth, 0f, 1f);
            float curved = 0.12f * t + 0.88f * t * t;
            height = shallowRise * curved;
        } else {
            float dx = ax - shallowEnd;
            float slope = mountainSlope(z, side);
            float blend = MathUtils.lerp(3.2f, 1.8f, presence);
            float mountainRise;
            if (dx < blend) {
                mountainRise = slope * dx * dx / (2f * blend);
            } else {
                float afterBlend = dx - blend;
                mountainRise = slope * (dx - blend * 0.5f)
                        + (0.006f + presence * 0.010f) * afterBlend * afterBlend;
            }

            float maxMountainRise = MathUtils.lerp(
                    13f + smoothNoise(z, 130f, side < 0f ? 107 : 109) * 8f,
                    30f + smoothNoise(z, 155f, side < 0f ? 113 : 127) * 16f,
                    presence);
            height = shallowRise + Math.min(mountainRise, maxMountainRise);
        }

        // Break up perfectly planar mountain faces without disturbing the deliberately flat
        // fields. Roughness increases gradually as terrain gets farther from the field edge.
        float roughT = MathUtils.clamp((ax - flatEdge) / 34f, 0f, 1f);
        float roughA = smoothNoise(z + ax * 1.7f, 37f, side < 0f ? 149 : 151) - 0.5f;
        float roughB = smoothNoise(z - ax * 1.15f, 61f, side < 0f ? 157 : 163) - 0.5f;
        float roughness = (roughA * 0.65f + roughB * 0.35f)
                * roughT * (0.45f + presence * 1.35f);
        return Math.max(0f, height + roughness);
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
        int firstWorldIndex = MathUtils.floor((bikeZ - 80f) / SEGMENT_LENGTH);

        for (int n = 0; n < SEGMENTS; n++) {
            int worldIndex = firstWorldIndex + n;
            int slot = worldIndex % SEGMENTS;
            if (slot < 0) slot += SEGMENTS;

            if (terrainWorldIndex[slot] != worldIndex) {
                rebuildSegment(slot, worldIndex);
            }
        }
    }

    private void rebuildSegment(int slot, int worldIndex) {
        if (terrainModels[slot] != null) {
            terrainModels[slot].dispose();
            terrainModels[slot] = null;
            terrainInstances[slot] = null;
        }

        Model model = buildSegmentModel(worldIndex);
        terrainModels[slot] = model;
        terrainInstances[slot] = new ModelInstance(model);
        terrainWorldIndex[slot] = worldIndex;
        placeTreesForSegment(slot, worldIndex);
    }

    private Model buildSegmentModel(int worldIndex) {
        float zStart = worldIndex * SEGMENT_LENGTH;
        float zStep = SEGMENT_LENGTH / Z_CELLS;
        long attributes = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        ModelBuilder builder = new ModelBuilder();
        builder.begin();

        for (int surface = 0; surface < SURFACE_COUNT; surface++) {
            MeshPartBuilder part = null;

            for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                float side = sideIndex == 0 ? -1f : 1f;

                for (int zi = 0; zi < Z_CELLS; zi++) {
                    float z0 = zStart + zi * zStep;
                    float z1 = z0 + zStep;
                    float zMid = (z0 + z1) * 0.5f;

                    for (int xi = 0; xi < X_SAMPLES.length - 1; xi++) {
                        float ax0 = X_SAMPLES[xi];
                        float ax1 = X_SAMPLES[xi + 1];
                        float axMid = (ax0 + ax1) * 0.5f;
                        int quadSurface = surfaceType(axMid, zMid, side, worldIndex, xi, zi);
                        if (quadSurface != surface) continue;

                        if (part == null) {
                            part = builder.part("terrain_" + surface, GL20.GL_TRIANGLES,
                                    attributes, surfaceMaterials[surface]);
                        }
                        addQuad(part, side, ax0, ax1, z0, z1);
                    }
                }
            }
        }

        return builder.end();
    }

    private int surfaceType(float ax, float z, float side, int worldIndex, int xi, int zi) {
        if (ax <= GRASS_INNER_EDGE + 0.02f) return SURFACE_GRAVEL;

        float x = side * ax;
        float slopeX = groundSlopeX(x, z);
        float slopeZ = groundSlopeZ(x, z);
        float slope = (float) Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        float edge = flatEdge(z, side);

        if (ax <= edge + 0.75f && slope < 0.20f) return SURFACE_GRASS;
        if (slope < 0.52f) {
            float dirtChance = hash01(worldIndex * 31 + xi * 7 + zi,
                    side < 0f ? 181 : 191);
            return dirtChance > 0.70f ? SURFACE_DIRT : SURFACE_GRASS;
        }
        if (slope < 1.05f) {
            float dirtChance = hash01(worldIndex * 19 + xi * 11 + zi,
                    side < 0f ? 193 : 197);
            return dirtChance > 0.58f ? SURFACE_DIRT : SURFACE_MOUNTAIN_LOW;
        }
        return mountainPresence(z, side) > 0.55f
                ? SURFACE_MOUNTAIN_HIGH : SURFACE_MOUNTAIN_LOW;
    }

    private void addQuad(MeshPartBuilder part, float side, float ax0, float ax1,
                         float z0, float z1) {
        float x0 = side * ax0;
        float x1 = side * ax1;
        float h00 = groundHeight(x0, z0);
        float h10 = groundHeight(x1, z0);
        float h11 = groundHeight(x1, z1);
        float h01 = groundHeight(x0, z1);

        Vector3 a = new Vector3(x0, h00 - 0.035f, z0);
        Vector3 b = new Vector3(x1, h10 - 0.035f, z0);
        Vector3 c = new Vector3(x1, h11 - 0.035f, z1);
        Vector3 d = new Vector3(x0, h01 - 0.035f, z1);

        float midX = (x0 + x1) * 0.5f;
        float midZ = (z0 + z1) * 0.5f;
        Vector3 normal = new Vector3(-groundSlopeX(midX, midZ), 1f,
                -groundSlopeZ(midX, midZ)).nor();
        part.rect(a, b, c, d, normal);
    }

    private void placeTreesForSegment(int slot, int worldIndex) {
        float zStart = worldIndex * SEGMENT_LENGTH;
        int base = slot * TREES_PER_SEGMENT * 2;

        for (int t = 0; t < TREES_PER_SEGMENT; t++) {
            placeTree(base + t * 2, -1f, worldIndex, t, zStart);
            placeTree(base + t * 2 + 1, 1f, worldIndex, t, zStart);
        }
    }

    private void placeTree(int index, float side, int worldIndex, int t, float zStart) {
        int sideSalt = side < 0f ? 211 : 223;
        float keep = hash01(worldIndex * 43 + t, sideSalt);
        float presenceAtCenter = mountainPresence(zStart + SEGMENT_LENGTH * 0.5f, side);

        // Open stretches stay genuinely open. Mountain stretches get denser lower tree lines,
        // but even there leave holes so the forest does not become a uniform fence.
        float skipThreshold = MathUtils.lerp(0.38f, 0.20f, presenceAtCenter);
        if (keep < skipThreshold) {
            hideTree(index);
            return;
        }

        float zRand = hash01(worldIndex * 53 + t, sideSalt + 5);
        float treeZ = zStart + 0.8f + zRand * (SEGMENT_LENGTH - 1.6f);
        float edge = flatEdge(treeZ, side);
        float shallow = shallowWidth(treeZ, side);

        float radial = hash01(worldIndex * 61 + t, sideSalt + 9);
        float inner = GRASS_INNER_EDGE + 3.0f;
        float outer = edge + shallow * MathUtils.lerp(0.35f, 0.95f, radial);
        float ax = MathUtils.lerp(inner, Math.max(inner + 1f, outer), radial);
        float x = side * ax;

        float scale = 0.68f + hash01(worldIndex * 67 + t, sideSalt + 13) * 0.72f;
        float ground = groundHeight(x, treeZ);
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);
    }

    private void hideTree(int index) {
        trunks[index].transform.setToTranslation(0f, -1000f, 0f);
        trees[index].transform.setToTranslation(0f, -1000f, 0f);
    }

    private static float flatEdge(float z, float side) {
        float presence = mountainPresence(z, side);
        int salt = side < 0f ? 83 : 89;
        float openEdge = 24f + smoothNoise(z + (side < 0f ? 0f : 43f), 120f, salt) * 13f;
        float mountainEdge = 10.5f + smoothNoise(z + (side < 0f ? 71f : -37f), 72f,
                salt + 4) * 6.0f;
        return MathUtils.lerp(openEdge, mountainEdge, presence);
    }

    private static float shallowWidth(float z, float side) {
        float presence = mountainPresence(z, side);
        int salt = side < 0f ? 97 : 101;
        float variation = smoothNoise(z, 84f, salt);
        return MathUtils.lerp(7.0f + variation * 2.3f, 4.6f + variation * 1.4f, presence);
    }

    private static float shallowRise(float z, float side) {
        float presence = mountainPresence(z, side);
        int salt = side < 0f ? 103 : 107;
        float variation = smoothNoise(z + 19f, 93f, salt);
        return MathUtils.lerp(1.5f + variation * 1.5f, 3.2f + variation * 2.3f, presence);
    }

    private static float mountainSlope(float z, float side) {
        float presence = mountainPresence(z, side);
        int salt = side < 0f ? 131 : 137;
        float variation = smoothNoise(z - 27f, 118f, salt);
        return MathUtils.lerp(0.72f + variation * 0.45f,
                1.25f + variation * 0.80f, presence);
    }

    /**
     * Smooth world-scale mountain events. The two sides use different phases and warped
     * periods, so a mountain can crowd one side of the road while the opposite side remains
     * an open field. Events are long enough to read as geography rather than random bumps.
     */
    private static float mountainPresence(float z, float side) {
        float phase = side < 0f ? 0.65f : 3.15f;
        int salt = side < 0f ? 211 : 223;
        float warp = (smoothNoise(z + (side < 0f ? 37f : -81f), 180f, salt) - 0.5f) * 2.4f;
        float wave = 0.5f + 0.5f * MathUtils.sin(z / 68f + phase + warp);
        float t = MathUtils.clamp((wave - 0.64f) / 0.30f, 0f, 1f);
        return t * t * (3f - 2f * t);
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
        for (ModelInstance instance : terrainInstances) {
            if (instance != null) batch.render(instance, environment);
        }
        for (ModelInstance m : trunks) batch.render(m, environment);
        for (ModelInstance m : trees) batch.render(m, environment);
    }

    void dispose() {
        for (int i = 0; i < terrainModels.length; i++) {
            if (terrainModels[i] != null) {
                terrainModels[i].dispose();
                terrainModels[i] = null;
                terrainInstances[i] = null;
            }
        }
    }
}
