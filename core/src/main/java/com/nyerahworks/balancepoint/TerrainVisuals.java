package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Streamed terrain renderer backed by an offline-eroded 16-bit heightfield.
 *
 * Rendering and motorcycle collision both sample the exact same triangulated surface. Near
 * terrain follows the source heightmap's 1.5 m grid; farther terrain progressively coarsens in
 * X while retaining continuous heights/normals.
 *
 * Presentation deliberately separates two spatial scales. Vertex color carries broad material
 * identity (healthy/dry grass, packed dirt, rock, worn riding lines and macro variation), while
 * a small repeatable neutral ground texture supplies close gravel/soil breakup. This prevents a
 * single world-sized texture from turning into blurry green paint at motorcycle distance.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final String HEIGHT_PATH = "terrain/world.bpheight";
    private static final int HEIGHT_MAGIC = 0x42504854; // BPHT

    private static final float ROAD_EDGE = 4.22f;
    private static final float SEGMENT_LENGTH = 24f;
    private static final int Z_CELLS = 16; // 1.5 m longitudinal mesh spacing
    private static final int SEGMENTS = 24;
    private static final int TREES_PER_SEGMENT = 8;
    private static final float DETAIL_REPEAT_METERS = 5.4f;
    private static final float SUPERCROSS_CENTER_X = 7.5f;
    private static final float SUPERCROSS_Z_START = 18f;
    private static final float SUPERCROSS_Z_END = 270f;
    private static final float[] X_SAMPLES = buildXSamples();

    private final Model[] terrainModels = new Model[SEGMENTS];
    private final ModelInstance[] terrainInstances = new ModelInstance[SEGMENTS];
    private final Model[] groundDetailModels = new Model[SEGMENTS];
    private final ModelInstance[] groundDetailInstances = new ModelInstance[SEGMENTS];
    private final int[] terrainWorldIndex = new int[SEGMENTS];
    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final boolean[] treeActive = new boolean[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeWorldZ = new float[SEGMENTS * TREES_PER_SEGMENT * 2];

    private int mapWidth;
    private int mapHeight;
    private float sampleSpacing;
    private float originX;
    private float originZ;
    private float encodedMin;
    private float encodedMax;
    private short[] heightSamples;

    private final Texture terrainTexture;
    private final Material terrainMaterial;
    private final Material groundDetailMaterial;
    private final Color colorScratch = new Color();
    private final SurfaceSample surfaceScratch = new SurfaceSample();

    // Multiple independent scales stop the terrain from advertising one procedural frequency.
    private final FastNoiseLite surfaceNoise = createNoise(4502157, 0.035f, 4, 0.48f);
    private final FastNoiseLite macroNoise = createNoise(4502281, 0.0065f, 4, 0.52f);
    private final FastNoiseLite dryNoise = createNoise(4502399, 0.014f, 3, 0.55f);
    private final FastNoiseLite fineNoise = createNoise(4502477, 0.135f, 3, 0.46f);

    TerrainVisuals(Array<Model> ownedModels) {
        loadHeightfield();

        terrainTexture = createGroundDetailTexture();
        terrainTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        terrainTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        terrainMaterial = new Material(
                TextureAttribute.createDiffuse(terrainTexture),
                ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE));
        groundDetailMaterial = new Material(
                ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE));

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

    private void loadHeightfield() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Gdx.files.internal(HEIGHT_PATH).read(), 1 << 20))) {
            if (in.readInt() != HEIGHT_MAGIC) {
                throw new GdxRuntimeException("Invalid Balance Point terrain heightfield");
            }
            int version = in.readInt();
            if (version != 1) throw new GdxRuntimeException("Unsupported terrain version " + version);

            mapWidth = in.readInt();
            mapHeight = in.readInt();
            sampleSpacing = in.readFloat();
            originX = in.readFloat();
            originZ = in.readFloat();
            encodedMin = in.readFloat();
            encodedMax = in.readFloat();

            if (mapWidth < 2 || mapHeight < 2 || sampleSpacing <= 0f) {
                throw new GdxRuntimeException("Invalid terrain dimensions");
            }
            heightSamples = new short[mapWidth * mapHeight];
            for (int i = 0; i < heightSamples.length; i++) heightSamples[i] = in.readShort();
        } catch (IOException e) {
            throw new GdxRuntimeException("Could not load " + HEIGHT_PATH, e);
        }
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private static FastNoiseLite createNoise(int seed, float frequency, int octaves, float gain) {
        FastNoiseLite n = new FastNoiseLite(seed);
        n.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        n.SetFractalType(FastNoiseLite.FractalType.FBm);
        n.SetFrequency(frequency);
        n.SetFractalOctaves(octaves);
        n.SetFractalGain(gain);
        n.SetFractalLacunarity(2.0f);
        return n;
    }

    private Texture createGroundDetailTexture() {
        final int size = 256;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGB888);
        for (int y = 0; y < size; y++) {
            float v = MathUtils.PI2 * y / size;
            for (int x = 0; x < size; x++) {
                float u = MathUtils.PI2 * x / size;
                float wave = MathUtils.sin(u * 3f + v * 2f + 0.7f) * 0.13f
                        + MathUtils.sin(u * 7f - v * 5f + 2.1f) * 0.09f
                        + MathUtils.cos(u * 13f + v * 11f + 1.4f) * 0.055f
                        + MathUtils.sin(u * 29f - v * 23f + 0.2f) * 0.030f;
                float grit = hash01(x * 313 + y * 911, 733) - 0.5f;
                float value = MathUtils.clamp(0.86f + wave + grit * 0.11f, 0.58f, 1f);
                float warm = MathUtils.sin(u * 17f + 0.4f) * 0.010f;
                pixmap.setColor(
                        MathUtils.clamp(value + warm, 0f, 1f),
                        MathUtils.clamp(value - 0.010f, 0f, 1f),
                        MathUtils.clamp(value - 0.025f - warm * 0.3f, 0f, 1f),
                        1f);
                pixmap.drawPixel(x, y);
            }
        }

        // Tiny aggregate marks are wrapped manually at texture edges. They are intentionally
        // neutral: macro terrain color decides whether the same physical grain reads as dirt,
        // stone or vegetation shadow instead of stamping one photo tile everywhere.
        for (int i = 0; i < 900; i++) {
            int cx = (int) (hash01(i, 811) * size) % size;
            int cy = (int) (hash01(i, 823) * size) % size;
            int radius = hash01(i, 829) > 0.72f ? 2 : 1;
            float light = hash01(i, 839) > 0.76f ? 0.96f : 0.64f;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy > radius * radius) continue;
                    int px = (cx + dx + size) % size;
                    int py = (cy + dy + size) % size;
                    pixmap.setColor(light, light * 0.98f, light * 0.93f, 1f);
                    pixmap.drawPixel(px, py);
                }
            }
        }

        Texture texture = new Texture(pixmap, true);
        pixmap.dispose();
        return texture;
    }

    /** Longitudinal tire friction coefficient at a world point. */
    float tractionCoefficient(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 1.00f;

        float h = groundHeight(x, z);
        float sx = groundSlopeX(x, z);
        float sz = groundSlopeZ(x, z);
        float slope = (float) Math.sqrt(sx * sx + sz * sz);
        SurfaceSample s = classifySurface(x, z, h, slope, surfaceScratch);

        // Dry MX-knobby peak grip. Loose surfaces still fall off through the slip curve.
        return MathUtils.clamp(s.grass * 0.94f + s.dirt * 0.98f + s.rock * 0.90f,
                0.88f, 0.985f);
    }

    private SurfaceSample classifySurface(float x, float z, float height, float slope,
                                          SurfaceSample out) {
        float patch = noise01(surfaceNoise, x, z);
        float macro = noise01(macroNoise, x, z);
        float wear = rideWear(x, z);

        float rock = smootherStep(0.53f, 1.08f, slope);
        rock = Math.max(rock, smootherStep(62f, 104f, height) * 0.72f);
        rock *= 1f - wear * 0.76f;

        float dirt = smootherStep(0.18f, 0.70f, slope) * (1f - rock * 0.64f);
        dirt = Math.max(dirt, smootherStep(0.67f, 0.90f, patch) * 0.62f);
        dirt = Math.max(dirt, smootherStep(0.70f, 0.88f, macro) * 0.34f);
        dirt = Math.max(dirt, wear);
        dirt = MathUtils.clamp(dirt * (1f - rock * 0.42f), 0f, 1f);
        rock = MathUtils.clamp(rock, 0f, 1f - dirt * 0.45f);
        float grass = MathUtils.clamp(1f - dirt - rock, 0f, 1f);

        float total = grass + dirt + rock;
        if (total < 0.001f) {
            grass = 1f;
            total = 1f;
        }
        out.grass = grass / total;
        out.dirt = dirt / total;
        out.rock = rock / total;
        out.wear = wear;
        return out;
    }

    private float rideWear(float x, float z) {
        float ax = Math.abs(x);
        float shoulder = 0f;
        if (ax > ROAD_EDGE) {
            shoulder = 1f - smootherStep(ROAD_EDGE + 0.35f, ROAD_EDGE + 3.4f, ax);
            shoulder *= 0.62f;
        }

        float laneX = 1f - smootherStep(1.45f, 3.45f, Math.abs(x - SUPERCROSS_CENTER_X));
        float enter = smootherStep(SUPERCROSS_Z_START, SUPERCROSS_Z_START + 10f, z);
        float leave = 1f - smootherStep(SUPERCROSS_Z_END - 12f, SUPERCROSS_Z_END, z);
        float track = laneX * Math.min(enter, leave);
        return MathUtils.clamp(Math.max(shoulder, track), 0f, 1f);
    }

    private float terrainColorBits(float x, float z, float height, float slope) {
        SurfaceSample s = classifySurface(x, z, height, slope, surfaceScratch);
        float macro = macroNoise.GetNoise(x, z);
        float dryField = noise01(dryNoise, x, z);
        float fine = fineNoise.GetNoise(x, z);

        float dryness = MathUtils.clamp(
                dryField * 0.78f + smootherStep(35f, 92f, height) * 0.24f,
                0f, 1f);

        float grassR = MathUtils.lerp(0.155f, 0.385f, dryness);
        float grassG = MathUtils.lerp(0.305f, 0.340f, dryness);
        float grassB = MathUtils.lerp(0.075f, 0.155f, dryness);

        float dirtDark = s.wear * (0.30f + 0.20f * noise01(fineNoise, x * 1.8f, z * 1.8f));
        float dirtR = MathUtils.lerp(0.38f, 0.285f, dirtDark);
        float dirtG = MathUtils.lerp(0.235f, 0.165f, dirtDark);
        float dirtB = MathUtils.lerp(0.105f, 0.065f, dirtDark);

        float rockR = 0.315f;
        float rockG = 0.305f;
        float rockB = 0.275f;

        float r = grassR * s.grass + dirtR * s.dirt + rockR * s.rock;
        float g = grassG * s.grass + dirtG * s.dirt + rockG * s.rock;
        float b = grassB * s.grass + dirtB * s.dirt + rockB * s.rock;

        // Broad tonal breakup plus small local variation. Fine texture still supplies the true
        // close-frequency detail, so this never turns into visible procedural TV static.
        float brightness = 0.94f + macro * 0.085f + fine * 0.050f;

        // Cheap terrain AO from local concavity. It darkens drainage folds and the bases of
        // rough shapes without painting shadows in one fixed sun direction.
        float probe = sampleSpacing * 1.5f;
        float center = sourceHeight(x, z);
        float neighborhood = (sourceHeight(x + probe, z) + sourceHeight(x - probe, z)
                + sourceHeight(x, z + probe) + sourceHeight(x, z - probe)) * 0.25f;
        float concavity = MathUtils.clamp((neighborhood - center) * 0.30f, -0.08f, 0.16f);
        if (concavity > 0f) brightness *= 1f - concavity * 0.75f;
        else brightness *= 1f - concavity * 0.22f;

        colorScratch.set(
                MathUtils.clamp(r * brightness, 0.035f, 0.72f),
                MathUtils.clamp(g * brightness, 0.030f, 0.70f),
                MathUtils.clamp(b * brightness, 0.025f, 0.58f),
                1f);
        return colorScratch.toFloatBits();
    }

    private static float noise01(FastNoiseLite noise, float x, float z) {
        return MathUtils.clamp(noise.GetNoise(x, z) * 0.5f + 0.5f, 0f, 1f);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static final float TERRAIN_Y_OFFSET = -0.028f;

    float groundHeight(float x, float z) {
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
        float r = sampleSpacing * 2f;
        return (groundHeight(x + r, z) - groundHeight(x - r, z)) / (2f * r);
    }

    float groundSlopeZ(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        float r = sampleSpacing * 2f;
        return (groundHeight(x, z + r) - groundHeight(x, z - r)) / (2f * r);
    }

    private float meshVertexHeight(float x, float z) {
        float base = Math.abs(x) <= ROAD_EDGE ? 0f : sourceHeight(x, z);
        return base + TERRAIN_Y_OFFSET;
    }

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

    private float decoded(int x, int z) {
        int q = heightSamples[z * mapWidth + x] & 0xffff;
        return encodedMin + (q / 65535f) * (encodedMax - encodedMin);
    }

    void update(float bikeZ) {
        int firstWorldIndex = MathUtils.floor((bikeZ - 120f) / SEGMENT_LENGTH);
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
        if (groundDetailModels[slot] != null) {
            groundDetailModels[slot].dispose();
            groundDetailModels[slot] = null;
            groundDetailInstances[slot] = null;
        }

        Model model = buildSegmentModel(worldIndex);
        terrainModels[slot] = model;
        terrainInstances[slot] = new ModelInstance(model);

        Model details = buildGroundDetailModel(worldIndex);
        groundDetailModels[slot] = details;
        groundDetailInstances[slot] = details == null ? null : new ModelInstance(details);

        terrainWorldIndex[slot] = worldIndex;
        placeTreesForSegment(slot, worldIndex);
    }

    private Model buildSegmentModel(int worldIndex) {
        float zStart = worldIndex * SEGMENT_LENGTH;
        float zStep = SEGMENT_LENGTH / Z_CELLS;
        int xCount = X_SAMPLES.length;
        int zCount = Z_CELLS + 1;
        int vertexCount = xCount * zCount;
        int indexCount = (xCount - 1) * Z_CELLS * 6;
        float[] vertices = new float[vertexCount * 9]; // pos3 + normal3 + packedColor1 + uv2
        short[] indices = new short[indexCount];

        int vo = 0;
        for (int zi = 0; zi < zCount; zi++) {
            float z = zStart + zi * zStep;
            for (float x : X_SAMPLES) {
                float y = meshVertexHeight(x, z);
                float sx = groundSlopeX(x, z);
                float sz = groundSlopeZ(x, z);
                float slope = (float) Math.sqrt(sx * sx + sz * sz);
                float inv = 1f / (float) Math.sqrt(1f + sx * sx + sz * sz);

                vertices[vo++] = x;
                vertices[vo++] = y;
                vertices[vo++] = z;
                vertices[vo++] = -sx * inv;
                vertices[vo++] = inv;
                vertices[vo++] = -sz * inv;
                vertices[vo++] = terrainColorBits(x, z, y - TERRAIN_Y_OFFSET, slope);

                // Slightly warp UV phase with macro fields so the neutral micro texture does not
                // line up as an obvious world grid even though the texture itself tiles cleanly.
                float warpU = macroNoise.GetNoise(x * 0.45f + 91f, z * 0.45f) * 0.52f;
                float warpV = surfaceNoise.GetNoise(x * 0.50f, z * 0.50f - 117f) * 0.52f;
                vertices[vo++] = (x + warpU) / DETAIL_REPEAT_METERS;
                vertices[vo++] = (z + warpV) / DETAIL_REPEAT_METERS;
            }
        }

        int io = 0;
        for (int zi = 0; zi < Z_CELLS; zi++) {
            int row0 = zi * xCount;
            int row1 = (zi + 1) * xCount;
            for (int xi = 0; xi < xCount - 1; xi++) {
                short a = (short) (row0 + xi);
                short b = (short) (row0 + xi + 1);
                short c = (short) (row1 + xi);
                short d = (short) (row1 + xi + 1);
                indices[io++] = a;
                indices[io++] = b;
                indices[io++] = c;
                indices[io++] = b;
                indices[io++] = d;
                indices[io++] = c;
            }
        }

        Mesh mesh = new Mesh(true, vertexCount, indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(),
                VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);

        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part("eroded_terrain", mesh, GL20.GL_TRIANGLES, terrainMaterial);
        return builder.end();
    }

    private Model buildGroundDetailModel(int worldIndex) {
        DetailMeshBuilder out = new DetailMeshBuilder(2600, 5400);
        float zStart = worldIndex * SEGMENT_LENGTH;

        for (int i = 0; i < 116; i++) {
            float x = MathUtils.lerp(-52f, 52f, hash01(worldIndex * 409 + i, 901));
            float z = zStart + 0.35f
                    + hash01(worldIndex * 421 + i, 919) * (SEGMENT_LENGTH - 0.70f);
            if (Math.abs(x) <= ROAD_EDGE + 0.85f) continue;

            float y = groundHeight(x, z);
            float sx = groundSlopeX(x, z);
            float sz = groundSlopeZ(x, z);
            float slope = (float) Math.sqrt(sx * sx + sz * sz);
            if (slope > 0.90f) continue;

            SurfaceSample s = classifySurface(x, z, y, slope, surfaceScratch);
            float spawn = hash01(worldIndex * 433 + i, 937);

            if (s.wear < 0.32f && s.grass > 0.30f
                    && spawn < 0.24f + s.grass * 0.58f) {
                addGrassClump(out, x, y + 0.012f, z, worldIndex, i);
            } else if (spawn > 0.83f && (s.rock > 0.16f || s.dirt > 0.48f)) {
                addRock(out, x, y + 0.008f, z, worldIndex, i, s.rock);
            }
        }

        if (out.vertexCount == 0) return null;
        Mesh mesh = new Mesh(true, out.vertexCount, out.indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked());
        mesh.setVertices(out.vertices, 0, out.vertexCount * DetailMeshBuilder.FLOATS_PER_VERTEX);
        mesh.setIndices(out.indices, 0, out.indexCount);

        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part("procedural_ground_clutter", mesh, GL20.GL_TRIANGLES, groundDetailMaterial);
        return builder.end();
    }

    private void addGrassClump(DetailMeshBuilder out, float x, float y, float z,
                               int worldIndex, int item) {
        int blades = 3 + (int) (hash01(worldIndex * 457 + item, 953) * 3f);
        float dry = noise01(dryNoise, x, z);
        for (int b = 0; b < blades; b++) {
            float rnd = hash01(item * 17 + b, worldIndex * 31 + 971);
            float angle = rnd * MathUtils.PI2 + b * 1.71f;
            float rightX = MathUtils.cos(angle);
            float rightZ = MathUtils.sin(angle);
            float normalX = -rightZ;
            float normalZ = rightX;
            float width = MathUtils.lerp(0.018f, 0.050f,
                    hash01(item * 23 + b, worldIndex * 43 + 977));
            float height = MathUtils.lerp(0.12f, 0.34f,
                    hash01(item * 29 + b, worldIndex * 47 + 983));
            float jitterX = (hash01(item * 37 + b, 991) - 0.5f) * 0.32f;
            float jitterZ = (hash01(item * 41 + b, 997) - 0.5f) * 0.32f;
            float lean = (hash01(item * 53 + b, 1009) - 0.5f) * height * 0.25f;

            float baseR = MathUtils.lerp(0.12f, 0.42f, dry);
            float baseG = MathUtils.lerp(0.31f, 0.36f, dry);
            float baseB = MathUtils.lerp(0.055f, 0.13f, dry);
            float shade = MathUtils.lerp(0.82f, 1.10f,
                    hash01(item * 59 + b, worldIndex * 61 + 1013));
            colorScratch.set(baseR * shade, baseG * shade, baseB * shade, 1f);
            float color = colorScratch.toFloatBits();

            float cx = x + jitterX;
            float cz = z + jitterZ;
            short a = out.vertex(cx - rightX * width, y, cz - rightZ * width,
                    normalX, 0.20f, normalZ, color);
            short c = out.vertex(cx + rightX * width, y, cz + rightZ * width,
                    normalX, 0.20f, normalZ, color);
            short tip = out.vertex(cx + normalX * lean, y + height, cz + normalZ * lean,
                    normalX, 0.20f, normalZ, color);
            out.triangle(a, c, tip);
        }
    }

    private void addRock(DetailMeshBuilder out, float x, float y, float z,
                         int worldIndex, int item, float rockiness) {
        float radius = MathUtils.lerp(0.055f, 0.18f,
                hash01(worldIndex * 71 + item, 1031));
        float height = radius * MathUtils.lerp(0.45f, 1.05f,
                hash01(worldIndex * 73 + item, 1033));
        float gray = MathUtils.lerp(0.25f, 0.39f,
                hash01(worldIndex * 79 + item, 1039));
        float dirtTint = 1f - rockiness;
        colorScratch.set(
                gray + dirtTint * 0.055f,
                gray * 0.95f + dirtTint * 0.018f,
                gray * 0.85f,
                1f);
        float color = colorScratch.toFloatBits();

        short a = out.vertex(x - radius, y, z - radius * 0.72f, 0f, 1f, 0f, color);
        short b = out.vertex(x + radius, y, z - radius * 0.72f, 0f, 1f, 0f, color);
        short c = out.vertex(x + radius * 0.74f, y, z + radius, 0f, 1f, 0f, color);
        short d = out.vertex(x - radius * 0.78f, y, z + radius * 0.86f, 0f, 1f, 0f, color);
        short top = out.vertex(
                x + (hash01(item, 1049) - 0.5f) * radius * 0.45f,
                y + height,
                z + (hash01(item, 1051) - 0.5f) * radius * 0.45f,
                0f, 1f, 0f, color);
        out.triangle(a, b, top);
        out.triangle(b, c, top);
        out.triangle(c, d, top);
        out.triangle(d, a, top);
        out.triangle(a, d, c);
        out.triangle(a, c, b);
    }

    private static float[] buildXSamples() {
        ArrayList<Float> xs = new ArrayList<>();
        for (float x = -375f; x <= -244.5f + 0.01f; x += 9f) xs.add(x);
        for (float x = -240f; x <= -100.5f + 0.01f; x += 4.5f) xs.add(x);
        for (float x = -96f; x <= -6f + 0.01f; x += 1.5f) xs.add(x);
        xs.add(-ROAD_EDGE);
        xs.add(ROAD_EDGE);
        for (float x = 6f; x <= 96f + 0.01f; x += 1.5f) xs.add(x);
        for (float x = 100.5f; x <= 240f + 0.01f; x += 4.5f) xs.add(x);
        for (float x = 244.5f; x <= 375f + 0.01f; x += 9f) xs.add(x);
        float[] result = new float[xs.size()];
        for (int i = 0; i < result.length; i++) result[i] = xs.get(i);
        return result;
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
        float keep = hash01(worldIndex * 43 + t, salt);
        if (keep < 0.22f) {
            hideTree(index);
            return;
        }

        float treeZ = zStart + 0.8f + hash01(worldIndex * 53 + t, salt + 5)
                * (SEGMENT_LENGTH - 1.6f);
        float radial = hash01(worldIndex * 61 + t, salt + 9);
        float ax = MathUtils.lerp(11f, 128f, radial * radial);
        float x = side * ax;
        float slopeX = groundSlopeX(x, treeZ);
        float slopeZ = groundSlopeZ(x, treeZ);
        float slope = (float) Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        if (slope > 0.82f) {
            hideTree(index);
            return;
        }

        float scale = 0.70f + hash01(worldIndex * 67 + t, salt + 13) * 0.72f;
        float ground = groundHeight(x, treeZ);
        trunks[index].transform.setToTranslation(x, ground + 1.22f * scale, treeZ)
                .scale(scale, scale, scale);
        trees[index].transform.setToTranslation(x, ground + 4.15f * scale, treeZ)
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

    private static float hash01(int a, int b) {
        int x = a * 0x1f123bb5 ^ b * 0x5f356495;
        x ^= x >>> 16;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        return (x & 0x7fffffff) / (float) 0x7fffffff;
    }

    void render(ModelBatch batch, Environment environment, float centerZ, boolean highQuality) {
        for (ModelInstance instance : terrainInstances) {
            if (instance != null) batch.render(instance, environment);
        }

        // Dense geometry detail exists only near the player and only in high quality. Each 24 m
        // segment is one mesh/draw call, so this remains dramatically cheaper than hundreds of
        // individual grass sprites or ModelInstances.
        if (highQuality) {
            for (int i = 0; i < groundDetailInstances.length; i++) {
                ModelInstance instance = groundDetailInstances[i];
                if (instance == null || terrainWorldIndex[i] == Integer.MIN_VALUE) continue;
                float segmentCenter = terrainWorldIndex[i] * SEGMENT_LENGTH + SEGMENT_LENGTH * 0.5f;
                if (Math.abs(segmentCenter - centerZ) <= 132f) {
                    batch.render(instance, environment);
                }
            }
        }

        final float lowTreeDistance = 145f;
        final float lowFullDensityDistance = 78f;
        for (int i = 0; i < trees.length; i++) {
            if (!treeActive[i]) continue;
            if (!highQuality) {
                float distance = Math.abs(treeWorldZ[i] - centerZ);
                if (distance > lowTreeDistance) continue;
                if (distance > lowFullDensityDistance && (i & 1) != 0) continue;
            }
            batch.render(trunks[i], environment);
            batch.render(trees[i], environment);
        }
    }

    void dispose() {
        for (int i = 0; i < terrainModels.length; i++) {
            if (terrainModels[i] != null) {
                terrainModels[i].dispose();
                terrainModels[i] = null;
                terrainInstances[i] = null;
            }
            if (groundDetailModels[i] != null) {
                groundDetailModels[i].dispose();
                groundDetailModels[i] = null;
                groundDetailInstances[i] = null;
            }
        }
        terrainTexture.dispose();
    }

    private static final class CellSample {
        float fx;
        float fz;
        float h00;
        float h10;
        float h01;
        float h11;
    }

    private static final class SurfaceSample {
        float grass;
        float dirt;
        float rock;
        float wear;
    }

    private static final class DetailMeshBuilder {
        static final int FLOATS_PER_VERTEX = 7;
        final float[] vertices;
        final short[] indices;
        int vertexCount;
        int indexCount;

        DetailMeshBuilder(int maxVertices, int maxIndices) {
            vertices = new float[maxVertices * FLOATS_PER_VERTEX];
            indices = new short[maxIndices];
        }

        short vertex(float x, float y, float z,
                     float nx, float ny, float nz, float color) {
            if (vertexCount >= Short.MAX_VALUE || (vertexCount + 1) * FLOATS_PER_VERTEX > vertices.length) {
                throw new GdxRuntimeException("Ground detail vertex budget exceeded");
            }
            int o = vertexCount * FLOATS_PER_VERTEX;
            vertices[o] = x;
            vertices[o + 1] = y;
            vertices[o + 2] = z;
            vertices[o + 3] = nx;
            vertices[o + 4] = ny;
            vertices[o + 5] = nz;
            vertices[o + 6] = color;
            return (short) vertexCount++;
        }

        void triangle(short a, short b, short c) {
            if (indexCount + 3 > indices.length) {
                throw new GdxRuntimeException("Ground detail index budget exceeded");
            }
            indices[indexCount++] = a;
            indices[indexCount++] = b;
            indices[indexCount++] = c;
        }
    }
}
