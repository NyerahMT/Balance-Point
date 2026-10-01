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
 * Deterministic streamed roadside terrain built from FastNoiseLite.
 * The road corridor is carved into a continuous noise field so open residential/trail
 * sections and mountain sections are parts of one world rather than separate scenery.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final int WORLD_SEED = 4502026;
    private static final float SEGMENT_LENGTH = 20f;
    private static final int SEGMENTS = 18;
    private static final int Z_CELLS = 10;
    private static final int TREES_PER_SEGMENT = 8;

    private static final float ROAD_EDGE = 4.22f;
    private static final float GRASS_INNER_EDGE = 6.40f;

    private static final float[] X_SAMPLES = {
            ROAD_EDGE, GRASS_INNER_EDGE, 8.5f, 11f, 14f, 18f, 23f, 29f,
            36f, 44f, 54f, 66f, 80f, 96f, 114f, 132f
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

    private final FastNoiseLite macroNoise = makeNoise(WORLD_SEED + 11,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0032f, 4, 0.52f, 2.0f);
    private final FastNoiseLite ridgeNoise = makeNoise(WORLD_SEED + 29,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.Ridged,
            0.0105f, 4, 0.50f, 2.08f);
    private final FastNoiseLite detailNoise = makeNoise(WORLD_SEED + 47,
            FastNoiseLite.NoiseType.Perlin, FastNoiseLite.FractalType.FBm,
            0.029f, 3, 0.46f, 2.0f);
    private final FastNoiseLite eventNoise = makeNoise(WORLD_SEED + 73,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0054f, 3, 0.52f, 2.0f);
    private final FastNoiseLite corridorNoise = makeNoise(WORLD_SEED + 101,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0070f, 2, 0.50f, 2.0f);
    private final FastNoiseLite surfaceNoise = makeNoise(WORLD_SEED + 131,
            FastNoiseLite.NoiseType.Perlin, FastNoiseLite.FractalType.FBm,
            0.042f, 3, 0.48f, 2.0f);

    TerrainVisuals(Array<Model> ownedModels) {
        surfaceMaterials[SURFACE_GRAVEL] = material(0.40f, 0.39f, 0.34f);
        surfaceMaterials[SURFACE_GRASS] = material(0.25f, 0.39f, 0.18f);
        surfaceMaterials[SURFACE_DIRT] = material(0.34f, 0.26f, 0.16f);
        surfaceMaterials[SURFACE_MOUNTAIN_LOW] = material(0.24f, 0.30f, 0.19f);
        surfaceMaterials[SURFACE_MOUNTAIN_HIGH] = material(0.29f, 0.30f, 0.28f);

        for (Material m : surfaceMaterials) {
            m.set(IntAttribute.createCullFace(GL20.GL_NONE));
        }
        for (int i = 0; i < terrainWorldIndex.length; i++) {
            terrainWorldIndex[i] = Integer.MIN_VALUE;
        }

        ModelBuilder b = new ModelBuilder();
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
        }
    }

    private static FastNoiseLite makeNoise(int seed, FastNoiseLite.NoiseType type,
                                           FastNoiseLite.FractalType fractal,
                                           float frequency, int octaves,
                                           float gain, float lacunarity) {
        FastNoiseLite n = new FastNoiseLite(seed);
        n.SetNoiseType(type);
        n.SetFrequency(frequency);
        n.SetFractalType(fractal);
        n.SetFractalOctaves(octaves);
        n.SetFractalGain(gain);
        n.SetFractalLacunarity(lacunarity);
        return n;
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    /** Same field is sampled by rendering and wheel contact. */
    float groundHeight(float x, float z) {
        float ax = Math.abs(x);
        if (ax <= ROAD_EDGE) return 0f;

        float side = x < 0f ? -1f : 1f;
        float edge = flatEdge(z, side);
        if (ax <= edge) return 0f;

        float d = ax - edge;
        float presence = mountainPresence(z, side);
        float entry = smootherStep(0f, 5.5f, d);
        float foothillT = smootherStep(0f, 19f, d);

        // Low-frequency coordinate warp keeps ridges from lining up with the chunk grid.
        float warpX = detailNoise.GetNoise(x * 0.42f + 911f, z * 0.42f - 433f) * 18f;
        float warpZ = detailNoise.GetNoise(x * 0.42f - 607f, z * 0.42f + 277f) * 18f;
        float wx = x + warpX;
        float wz = z + warpZ;

        float broad = unit(macroNoise.GetNoise(wx, wz));
        float detail = detailNoise.GetNoise(wx, wz);
        float ridge = unit(ridgeNoise.GetNoise(wx * 1.08f + wz * 0.12f,
                wz * 0.96f - wx * 0.09f));
        float ridgeSharp = (float) Math.pow(MathUtils.clamp(ridge, 0f, 1f), 2.15f);

        // About 0-18 ft of generally rideable foothill before mountain terrain dominates.
        float foothillRise = foothillT * (0.75f + broad * 4.75f);
        float foothillShape = detail * 0.55f * foothillT;

        float mountainStart = MathUtils.lerp(30f, 11.5f, presence);
        float mountainRamp = smootherStep(mountainStart, mountainStart + 22f, d);
        float mountainMass = presence * mountainRamp * (7f + 32f * ridgeSharp);
        float regionalLift = presence * mountainRamp * (broad - 0.38f) * 7.5f;
        float micro = entry * detail * MathUtils.lerp(0.18f, 1.30f, mountainRamp);

        float height = entry * (foothillRise + foothillShape)
                + mountainMass + regionalLift + micro;
        return MathUtils.clamp(height, -0.20f, 52f);
    }

    float groundSlopeX(float x, float z) {
        final float e = 0.24f;
        return (groundHeight(x + e, z) - groundHeight(x - e, z)) / (2f * e);
    }

    float groundSlopeZ(float x, float z) {
        final float e = 0.40f;
        return (groundHeight(x, z + e) - groundHeight(x, z - e)) / (2f * e);
    }

    void update(float bikeZ) {
        int firstWorldIndex = MathUtils.floor((bikeZ - 80f) / SEGMENT_LENGTH);
        for (int n = 0; n < SEGMENTS; n++) {
            int worldIndex = firstWorldIndex + n;
            int slot = worldIndex % SEGMENTS;
            if (slot < 0) slot += SEGMENTS;
            if (terrainWorldIndex[slot] != worldIndex) rebuildSegment(slot, worldIndex);
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
                        int quadSurface = surfaceType(axMid, zMid, side,
                                worldIndex, xi, zi);
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

    private int surfaceType(float ax, float z, float side,
                            int worldIndex, int xi, int zi) {
        if (ax <= GRASS_INNER_EDGE + 0.02f) return SURFACE_GRAVEL;
        float x = side * ax;
        float sx = groundSlopeX(x, z);
        float sz = groundSlopeZ(x, z);
        float slope = (float) Math.sqrt(sx * sx + sz * sz);
        float h = groundHeight(x, z);
        float patch = unit(surfaceNoise.GetNoise(x, z));

        if (slope < 0.28f) return patch > 0.77f ? SURFACE_DIRT : SURFACE_GRASS;
        if (slope < 0.62f) return patch > 0.58f ? SURFACE_DIRT : SURFACE_GRASS;
        if (slope < 1.15f) {
            if (h > 12f && patch < 0.56f) return SURFACE_MOUNTAIN_LOW;
            return SURFACE_DIRT;
        }
        return h > 15f || mountainPresence(z, side) > 0.55f
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
        int salt = side < 0f ? 211 : 223;
        float zRand = hash01(worldIndex * 53 + t, salt + 5);
        float treeZ = zStart + 0.8f + zRand * (SEGMENT_LENGTH - 1.6f);
        float event = mountainPresence(treeZ, side);
        float edge = flatEdge(treeZ, side);
        float radial = hash01(worldIndex * 61 + t, salt + 9);
        float inner = GRASS_INNER_EDGE + 2.5f;
        float outer = edge + MathUtils.lerp(30f, 20f, event);
        float ax = MathUtils.lerp(inner, Math.max(inner + 1f, outer), radial);
        float x = side * ax;

        float sx = groundSlopeX(x, treeZ);
        float sz = groundSlopeZ(x, treeZ);
        float slope = (float) Math.sqrt(sx * sx + sz * sz);
        float ground = groundHeight(x, treeZ);
        float vegetation = unit(surfaceNoise.GetNoise(x + 241f, treeZ - 367f));
        float keep = hash01(worldIndex * 43 + t, salt);
        float threshold = MathUtils.lerp(0.60f, 0.40f, event);
        if (slope > 0.82f || ground > 27f || vegetation < threshold || keep < 0.24f) {
            hideTree(index);
            return;
        }

        float scale = 0.65f + hash01(worldIndex * 67 + t, salt + 13) * 0.80f;
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
                .scale(scale, scale, scale);
    }

    private float flatEdge(float z, float side) {
        float presence = mountainPresence(z, side);
        float n = unit(corridorNoise.GetNoise(side * 173f, z));
        float openEdge = 27f + n * 11f;
        float mountainEdge = 9.5f + n * 6.0f;
        return MathUtils.lerp(openEdge, mountainEdge, presence);
    }

    private float mountainPresence(float z, float side) {
        float a = unit(eventNoise.GetNoise(side * 487f, z));
        float b = unit(macroNoise.GetNoise(side * 251f, z * 0.72f + 903f));
        float combined = a * 0.72f + b * 0.28f;
        return smootherStep(0.50f, 0.69f, combined);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float unit(float n) {
        return MathUtils.clamp(n * 0.5f + 0.5f, 0f, 1f);
    }

    private static float hash01(int a, int b) {
        int x = a * 0x1f123bb5 ^ b * 0x5f356495;
        x ^= x >>> 16;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        return (x & 0x7fffffff) / (float) 0x7fffffff;
    }

    private void hideTree(int index) {
        trunks[index].transform.setToTranslation(0f, -1000f, 0f);
        trees[index].transform.setToTranslation(0f, -1000f, 0f);
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
