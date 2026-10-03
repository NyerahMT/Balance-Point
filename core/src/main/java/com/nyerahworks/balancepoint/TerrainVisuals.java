package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;

/**
 * Streamed terrain backed by the same offline-eroded heightfield used by motorcycle collision.
 *
 * Physics keeps the compact heightfield resident while visual chunks are created incrementally.
 * Visual composition is deterministic and intentionally clustered so the landscape reads as a
 * place rather than an evenly scattered procedural field.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final String HEIGHT_PATH = "terrain/world.bpheight";
    private static final int HEIGHT_MAGIC = 0x42504854;
    private static final float ROAD_EDGE = 4.22f;
    private static final float SEGMENT_LENGTH = 24f;
    private static final int Z_CELLS = 16;
    private static final int SEGMENTS = 24;
    private static final int TREES_PER_SEGMENT = 8;
    private static final float DETAIL_DISTANCE = 108f;
    private static final float HIGH_TERRAIN_DISTANCE = 500f;
    private static final float LOW_TERRAIN_DISTANCE = 340f;
    private static final float HIGH_TREE_DISTANCE = 255f;
    private static final float LOW_TREE_DISTANCE = 145f;
    private static final float LOW_FULL_DENSITY_DISTANCE = 78f;
    private static final float SUPERCROSS_CENTER_X = 7.5f;
    private static final float SUPERCROSS_Z_START = 18f;
    private static final float SUPERCROSS_Z_END = 270f;
    private static final float TERRAIN_Y_OFFSET = -0.028f;
    private static final float[] X_SAMPLES = buildXSamples();

    private final Mesh[] terrainMeshes = new Mesh[SEGMENTS];
    private final Model[] groundDetailModels = new Model[SEGMENTS];
    private final ModelInstance[] groundDetailInstances = new ModelInstance[SEGMENTS];
    private final boolean[] groundDetailReady = new boolean[SEGMENTS];
    private final int[] terrainWorldIndex = new int[SEGMENTS];
    private final float[] segmentMinY = new float[SEGMENTS];
    private final float[] segmentMaxY = new float[SEGMENTS];

    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final byte[] treeType = new byte[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final boolean[] treeActive = new boolean[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeWorldX = new float[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeWorldY = new float[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeWorldZ = new float[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final float[] treeScale = new float[SEGMENTS * TREES_PER_SEGMENT * 2];

    private final ProfessionalTerrainShader terrainShader;
    private final Material groundDetailMaterial;
    private final Color colorScratch = new Color();
    private final SurfaceSample surfaceScratch = new SurfaceSample();
    private final CellSample cellScratch = new CellSample();
    private final Vector3 boundsCenterScratch = new Vector3();
    private final Vector3 boundsDimensionsScratch = new Vector3();
    private final Vector3 treeCenterScratch = new Vector3();

    private final FastNoiseLite materialNoise = createNoise(4502157, 0.035f, 4, 0.48f);
    private final FastNoiseLite macroNoise = createNoise(4502281, 0.0065f, 4, 0.52f);
    private final FastNoiseLite dryNoise = createNoise(4502399, 0.014f, 3, 0.55f);
    private final FastNoiseLite scatterNoise = createNoise(4502603, 0.055f, 3, 0.50f);
    private final FastNoiseLite groveNoise = createNoise(4502711, 0.011f, 3, 0.56f);

    private int mapWidth;
    private int mapHeight;
    private float sampleSpacing;
    private float originX;
    private float originZ;
    private float encodedMin;
    private float encodedMax;
    private short[] heightSamples;

    TerrainVisuals(Array<Model> ownedModels) {
        loadHeightfield();
        terrainShader = new ProfessionalTerrainShader();
        groundDetailMaterial = new Material(
                ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE));

        for (int i = 0; i < terrainWorldIndex.length; i++) {
            terrainWorldIndex[i] = Integer.MIN_VALUE;
        }

        ModelBuilder builder = new ModelBuilder();
        Material pineDark = material(0.075f, 0.175f, 0.082f);
        Material pineWarm = material(0.125f, 0.225f, 0.095f);
        Material broadleaf = material(0.165f, 0.285f, 0.105f);
        Material trunk = material(0.22f, 0.145f, 0.075f);
        Model pineTall = builder.createCone(2.25f, 5.7f, 2.25f, 9, pineDark,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model pineWide = builder.createCone(3.25f, 4.35f, 3.25f, 9, pineWarm,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model crownRound = builder.createSphere(3.35f, 3.65f, 3.35f, 9, 6, broadleaf,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model trunkModel = builder.createCylinder(0.36f, 3.0f, 0.36f, 7, trunk,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ownedModels.add(pineTall);
        ownedModels.add(pineWide);
        ownedModels.add(crownRound);
        ownedModels.add(trunkModel);

        for (int i = 0; i < trees.length; i++) {
            byte type = (byte) (i % 3);
            treeType[i] = type;
            Model crown = type == 0 ? pineTall : (type == 1 ? pineWide : crownRound);
            trees[i] = new ModelInstance(crown);
            trunks[i] = new ModelInstance(trunkModel);
            hideTree(i);
        }
    }

    private void loadHeightfield() {
        byte[] bytes = Gdx.files.internal(HEIGHT_PATH).readBytes();
        if (bytes.length < 36) {
            throw new GdxRuntimeException("Truncated Balance Point terrain heightfield");
        }

        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != HEIGHT_MAGIC) {
            throw new GdxRuntimeException("Invalid Balance Point terrain heightfield");
        }
        int version = input.getInt();
        if (version != 1) {
            throw new GdxRuntimeException("Unsupported terrain version " + version);
        }

        mapWidth = input.getInt();
        mapHeight = input.getInt();
        sampleSpacing = input.getFloat();
        originX = input.getFloat();
        originZ = input.getFloat();
        encodedMin = input.getFloat();
        encodedMax = input.getFloat();
        if (mapWidth < 2 || mapHeight < 2 || sampleSpacing <= 0f) {
            throw new GdxRuntimeException("Invalid terrain dimensions");
        }

        long sampleCountLong = (long) mapWidth * mapHeight;
        if (sampleCountLong > Integer.MAX_VALUE) {
            throw new GdxRuntimeException("Terrain heightfield is too large");
        }
        int sampleCount = (int) sampleCountLong;
        if (input.remaining() < sampleCount * 2L) {
            throw new GdxRuntimeException("Truncated Balance Point terrain sample data");
        }

        heightSamples = new short[sampleCount];
        ShortBuffer samples = input.slice().order(ByteOrder.BIG_ENDIAN).asShortBuffer();
        samples.get(heightSamples);
    }

    private static Material material(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private static FastNoiseLite createNoise(int seed, float frequency, int octaves, float gain) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFrequency(frequency);
        noise.SetFractalOctaves(octaves);
        noise.SetFractalGain(gain);
        noise.SetFractalLacunarity(2.0f);
        return noise;
    }

    float tractionCoefficient(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 1.00f;

        float height = groundHeight(x, z);
        float sx = groundSlopeX(x, z);
        float sz = groundSlopeZ(x, z);
        float slope = (float) Math.sqrt(sx * sx + sz * sz);
        SurfaceSample surface = classifySurface(x, z, height, slope, surfaceScratch);
        return MathUtils.clamp(
                surface.grass * 0.94f + surface.dirt * 0.98f + surface.rock * 0.90f,
                0.88f,
                0.985f);
    }

    private SurfaceSample classifySurface(float x, float z, float height, float slope,
                                          SurfaceSample out) {
        float patch = noise01(materialNoise, x, z);
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
        return MathUtils.clamp(Math.max(shoulder, laneX * Math.min(enter, leave)), 0f, 1f);
    }

    private static float noise01(FastNoiseLite noise, float x, float z) {
        return MathUtils.clamp(noise.GetNoise(x, z) * 0.5f + 0.5f, 0f, 1f);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

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
        return h11 + (1f - fx) * (h01 - h11) + (1f - fz) * (h10 - h11);
    }

    float groundSlopeX(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        float radius = sampleSpacing * 2f;
        return (groundHeight(x + radius, z) - groundHeight(x - radius, z)) / (2f * radius);
    }

    float groundSlopeZ(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 0f;
        float radius = sampleSpacing * 2f;
        return (groundHeight(x, z + radius) - groundHeight(x, z - radius)) / (2f * radius);
    }

    private float meshVertexHeight(float x, float z) {
        float base = Math.abs(x) <= ROAD_EDGE ? 0f : sourceHeight(x, z);
        return base + TERRAIN_Y_OFFSET;
    }

    private float sourceHeight(float x, float z) {
        CellSample cell = sourceCell(x, z);
        if (cell.fx + cell.fz <= 1f) {
            return cell.h00 + cell.fx * (cell.h10 - cell.h00) + cell.fz * (cell.h01 - cell.h00);
        }
        return cell.h11 + (1f - cell.fx) * (cell.h01 - cell.h11)
                + (1f - cell.fz) * (cell.h10 - cell.h11);
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
        cellScratch.fx = gx - ix;
        cellScratch.fz = gz - iz;
        cellScratch.h00 = decoded(ix, iz);
        cellScratch.h10 = decoded(ix + 1, iz);
        cellScratch.h01 = decoded(ix, iz + 1);
        cellScratch.h11 = decoded(ix + 1, iz + 1);
        return cellScratch;
    }

    private float decoded(int x, int z) {
        int q = heightSamples[z * mapWidth + x] & 0xffff;
        return encodedMin + (q / 65535f) * (encodedMax - encodedMin);
    }

    void update(float centerZ) {
        int firstWorldIndex = MathUtils.floor((centerZ - 120f) / SEGMENT_LENGTH);
        int loaded = countLoadedDesired(firstWorldIndex);
        int buildBudget = loaded < 8 ? 2 : 1;

        for (int build = 0; build < buildBudget; build++) {
            int bestWorldIndex = findNearestMissing(firstWorldIndex, centerZ);
            if (bestWorldIndex == Integer.MIN_VALUE) break;
            int slot = slotForWorldIndex(bestWorldIndex);
            rebuildSegment(slot, bestWorldIndex);
        }

        buildNearestGroundDetail(centerZ);
    }

    private int countLoadedDesired(int firstWorldIndex) {
        int loaded = 0;
        for (int n = 0; n < SEGMENTS; n++) {
            int worldIndex = firstWorldIndex + n;
            if (terrainWorldIndex[slotForWorldIndex(worldIndex)] == worldIndex) loaded++;
        }
        return loaded;
    }

    private int findNearestMissing(int firstWorldIndex, float centerZ) {
        int best = Integer.MIN_VALUE;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int n = 0; n < SEGMENTS; n++) {
            int worldIndex = firstWorldIndex + n;
            int slot = slotForWorldIndex(worldIndex);
            if (terrainWorldIndex[slot] == worldIndex) continue;
            float segmentCenter = worldIndex * SEGMENT_LENGTH + SEGMENT_LENGTH * 0.5f;
            float distance = Math.abs(segmentCenter - centerZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = worldIndex;
            }
        }
        return best;
    }

    private static int slotForWorldIndex(int worldIndex) {
        int slot = worldIndex % SEGMENTS;
        return slot < 0 ? slot + SEGMENTS : slot;
    }

    private void rebuildSegment(int slot, int worldIndex) {
        if (terrainMeshes[slot] != null) {
            terrainMeshes[slot].dispose();
            terrainMeshes[slot] = null;
        }
        disposeGroundDetail(slot);

        terrainMeshes[slot] = buildSegmentMesh(slot, worldIndex);
        terrainWorldIndex[slot] = worldIndex;
        groundDetailReady[slot] = false;
        placeTreesForSegment(slot, worldIndex);
    }

    private void disposeGroundDetail(int slot) {
        if (groundDetailModels[slot] != null) {
            groundDetailModels[slot].dispose();
            groundDetailModels[slot] = null;
        }
        groundDetailInstances[slot] = null;
        groundDetailReady[slot] = false;
    }

    private void buildNearestGroundDetail(float centerZ) {
        int bestSlot = -1;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int slot = 0; slot < SEGMENTS; slot++) {
            if (terrainMeshes[slot] == null || groundDetailReady[slot]) continue;
            int worldIndex = terrainWorldIndex[slot];
            if (worldIndex == Integer.MIN_VALUE) continue;
            float segmentCenter = worldIndex * SEGMENT_LENGTH + SEGMENT_LENGTH * 0.5f;
            float distance = Math.abs(segmentCenter - centerZ);
            if (distance <= DETAIL_DISTANCE && distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
            }
        }

        if (bestSlot < 0) return;
        int worldIndex = terrainWorldIndex[bestSlot];
        Model details = buildGroundDetailModel(worldIndex);
        groundDetailModels[bestSlot] = details;
        groundDetailInstances[bestSlot] = details == null ? null : new ModelInstance(details);
        groundDetailReady[bestSlot] = true;
    }

    private Mesh buildSegmentMesh(int slot, int worldIndex) {
        float zStart = worldIndex * SEGMENT_LENGTH;
        float zStep = SEGMENT_LENGTH / Z_CELLS;
        int xCount = X_SAMPLES.length;
        int zCount = Z_CELLS + 1;
        int vertexCount = xCount * zCount;
        int indexCount = (xCount - 1) * Z_CELLS * 6;
        float[] heights = new float[vertexCount];
        float[] vertices = new float[vertexCount * 6];
        short[] indices = new short[indexCount];

        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int zi = 0; zi < zCount; zi++) {
            float z = zStart + zi * zStep;
            int row = zi * xCount;
            for (int xi = 0; xi < xCount; xi++) {
                float y = meshVertexHeight(X_SAMPLES[xi], z);
                heights[row + xi] = y;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        int vo = 0;
        for (int zi = 0; zi < zCount; zi++) {
            float z = zStart + zi * zStep;
            int row = zi * xCount;
            for (int xi = 0; xi < xCount; xi++) {
                float x = X_SAMPLES[xi];
                float y = heights[row + xi];

                float sx;
                if (xi == 0) {
                    sx = (heights[row + 1] - y) / (X_SAMPLES[1] - X_SAMPLES[0]);
                } else if (xi == xCount - 1) {
                    sx = (y - heights[row + xi - 1])
                            / (X_SAMPLES[xi] - X_SAMPLES[xi - 1]);
                } else {
                    sx = (heights[row + xi + 1] - heights[row + xi - 1])
                            / (X_SAMPLES[xi + 1] - X_SAMPLES[xi - 1]);
                }

                float sz;
                if (zi == 0 || zi == zCount - 1) {
                    float before = meshVertexHeight(x, z - zStep);
                    float after = meshVertexHeight(x, z + zStep);
                    sz = (after - before) / (2f * zStep);
                } else {
                    sz = (heights[(zi + 1) * xCount + xi]
                            - heights[(zi - 1) * xCount + xi]) / (2f * zStep);
                }

                float inv = 1f / (float) Math.sqrt(1f + sx * sx + sz * sz);
                vertices[vo++] = x;
                vertices[vo++] = y;
                vertices[vo++] = z;
                vertices[vo++] = -sx * inv;
                vertices[vo++] = inv;
                vertices[vo++] = -sz * inv;
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
                indices[io++] = c;
                indices[io++] = b;
                indices[io++] = b;
                indices[io++] = c;
                indices[io++] = d;
            }
        }

        Mesh mesh = new Mesh(true, vertexCount, indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal());
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        segmentMinY[slot] = minY;
        segmentMaxY[slot] = maxY;
        return mesh;
    }

    private Model buildGroundDetailModel(int worldIndex) {
        DetailMeshBuilder output = new DetailMeshBuilder(7600, 13200);
        float zStart = worldIndex * SEGMENT_LENGTH;

        for (int i = 0; i < 220; i++) {
            float x = MathUtils.lerp(-68f, 68f, hash01(worldIndex * 409 + i, 901));
            float z = zStart + 0.35f
                    + hash01(worldIndex * 421 + i, 919) * (SEGMENT_LENGTH - 0.70f);
            if (Math.abs(x) <= ROAD_EDGE + 0.90f) continue;

            float y = groundHeight(x, z);
            float sx = groundSlopeX(x, z);
            float sz = groundSlopeZ(x, z);
            float slope = (float) Math.sqrt(sx * sx + sz * sz);
            if (slope > 0.90f) continue;

            SurfaceSample surface = classifySurface(x, z, y, slope, surfaceScratch);
            float cluster = noise01(scatterNoise, x, z);
            float grove = noise01(groveNoise, x, z);
            float spawn = hash01(worldIndex * 433 + i, 937);
            float grassChance = (0.17f + surface.grass * 0.52f)
                    * MathUtils.lerp(0.52f, 1.20f, cluster);

            if (surface.wear < 0.25f && surface.grass > 0.40f
                    && grove > 0.46f && spawn > 0.70f && spawn < 0.81f) {
                addShrub(output, x, y + 0.01f, z, worldIndex, i, grove);
            } else if (surface.wear < 0.30f && surface.grass > 0.30f
                    && spawn < grassChance) {
                addGrassClump(output, x, y + 0.012f, z, worldIndex, i);
            } else if (spawn > 0.955f && (surface.rock > 0.10f || surface.dirt > 0.36f)) {
                addBoulder(output, x, y + 0.006f, z, worldIndex, i, surface.rock, 0.32f);
            } else if (spawn > 0.88f && (surface.rock > 0.15f || surface.dirt > 0.48f)) {
                addRock(output, x, y + 0.008f, z, worldIndex, i, surface.rock);
            }
        }

        addLandmarkOutcrops(output, worldIndex, zStart);

        if (output.vertexCount == 0) return null;
        Mesh mesh = new Mesh(true, output.vertexCount, output.indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked());
        mesh.setVertices(
                output.vertices,
                0,
                output.vertexCount * DetailMeshBuilder.FLOATS_PER_VERTEX);
        mesh.setIndices(output.indices, 0, output.indexCount);

        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part("procedural_ground_clutter", mesh, GL20.GL_TRIANGLES, groundDetailMaterial);
        return builder.end();
    }

    private void addLandmarkOutcrops(DetailMeshBuilder output, int worldIndex, float zStart) {
        float rhythm = hash01(worldIndex, 1409);
        if (rhythm < 0.48f) return;

        int count = rhythm > 0.82f ? 3 : 2;
        float side = hash01(worldIndex, 1423) < 0.5f ? -1f : 1f;
        for (int i = 0; i < count; i++) {
            float lateral = MathUtils.lerp(13f, 46f, hash01(worldIndex * 31 + i, 1433));
            float x = side * lateral;
            float z = zStart + 3.5f
                    + hash01(worldIndex * 37 + i, 1447) * (SEGMENT_LENGTH - 7f);
            float y = groundHeight(x, z);
            float sx = groundSlopeX(x, z);
            float sz = groundSlopeZ(x, z);
            if ((float) Math.sqrt(sx * sx + sz * sz) > 0.86f) continue;
            float size = MathUtils.lerp(0.85f, 1.85f,
                    hash01(worldIndex * 41 + i, 1459));
            addBoulder(output, x, y + 0.005f, z, worldIndex, 500 + i, 0.82f, size);
        }
    }

    private void addGrassClump(DetailMeshBuilder output, float x, float y, float z,
                               int worldIndex, int item) {
        int blades = 4 + (int) (hash01(worldIndex * 457 + item, 953) * 4f);
        float dry = noise01(dryNoise, x, z);
        for (int blade = 0; blade < blades; blade++) {
            float angle = hash01(item * 17 + blade, worldIndex * 31 + 971) * MathUtils.PI2;
            float rightX = MathUtils.cos(angle);
            float rightZ = MathUtils.sin(angle);
            float width = MathUtils.lerp(
                    0.016f,
                    0.040f,
                    hash01(item * 23 + blade, worldIndex * 43 + 977));
            float height = MathUtils.lerp(
                    0.13f,
                    0.35f,
                    hash01(item * 29 + blade, worldIndex * 47 + 983));
            float jitterX = (hash01(item * 37 + blade, 991) - 0.5f) * 0.38f;
            float jitterZ = (hash01(item * 41 + blade, 997) - 0.5f) * 0.38f;
            float leanX = (hash01(item * 53 + blade, 1009) - 0.5f) * height * 0.22f;
            float leanZ = (hash01(item * 61 + blade, 1013) - 0.5f) * height * 0.22f;

            float baseR = MathUtils.lerp(0.11f, 0.34f, dry);
            float baseG = MathUtils.lerp(0.28f, 0.34f, dry);
            float baseB = MathUtils.lerp(0.055f, 0.105f, dry);
            float shade = MathUtils.lerp(
                    0.92f,
                    1.10f,
                    hash01(item * 67 + blade, worldIndex * 71 + 1019));
            colorScratch.set(baseR * shade, baseG * shade, baseB * shade, 1f);
            float color = colorScratch.toFloatBits();

            float cx = x + jitterX;
            float cz = z + jitterZ;
            float topX = cx + leanX;
            float topZ = cz + leanZ;
            short a = output.vertex(
                    cx - rightX * width,
                    y,
                    cz - rightZ * width,
                    0f,
                    0.82f,
                    0f,
                    color);
            short b = output.vertex(
                    cx + rightX * width,
                    y,
                    cz + rightZ * width,
                    0f,
                    0.82f,
                    0f,
                    color);
            short c = output.vertex(
                    topX + rightX * width * 0.18f,
                    y + height,
                    topZ + rightZ * width * 0.18f,
                    0f,
                    0.82f,
                    0f,
                    color);
            short d = output.vertex(
                    topX - rightX * width * 0.18f,
                    y + height,
                    topZ - rightZ * width * 0.18f,
                    0f,
                    0.82f,
                    0f,
                    color);
            output.triangle(a, b, c);
            output.triangle(a, c, d);
        }
    }

    private void addShrub(DetailMeshBuilder output, float x, float y, float z,
                          int worldIndex, int item, float grove) {
        float width = MathUtils.lerp(0.32f, 0.82f,
                hash01(worldIndex * 79 + item, 1117));
        float height = MathUtils.lerp(0.28f, 0.78f,
                hash01(worldIndex * 83 + item, 1123));
        float dry = noise01(dryNoise, x, z);
        float shade = MathUtils.lerp(0.80f, 1.08f, grove);
        colorScratch.set(
                MathUtils.lerp(0.08f, 0.22f, dry) * shade,
                MathUtils.lerp(0.23f, 0.31f, dry) * shade,
                MathUtils.lerp(0.055f, 0.09f, dry) * shade,
                1f);
        float color = colorScratch.toFloatBits();

        for (int fan = 0; fan < 3; fan++) {
            float angle = fan * MathUtils.PI / 3f
                    + hash01(item * 17 + fan, worldIndex + 1139) * 0.38f;
            float rx = MathUtils.cos(angle) * width;
            float rz = MathUtils.sin(angle) * width;
            float topX = x + (hash01(item * 19 + fan, 1151) - 0.5f) * width * 0.32f;
            float topZ = z + (hash01(item * 23 + fan, 1163) - 0.5f) * width * 0.32f;
            short a = output.vertex(x - rx, y, z - rz, 0f, 0.78f, 0f, color);
            short b = output.vertex(x + rx, y, z + rz, 0f, 0.78f, 0f, color);
            short c = output.vertex(topX, y + height, topZ, 0f, 0.90f, 0f, color);
            output.triangle(a, b, c);
            output.triangle(b, a, c);
        }
    }

    private void addRock(DetailMeshBuilder output, float x, float y, float z,
                         int worldIndex, int item, float rockiness) {
        float radius = MathUtils.lerp(0.055f, 0.18f, hash01(worldIndex * 71 + item, 1031));
        addBoulder(output, x, y, z, worldIndex, item, rockiness, radius);
    }

    private void addBoulder(DetailMeshBuilder output, float x, float y, float z,
                            int worldIndex, int item, float rockiness, float radius) {
        float height = radius * MathUtils.lerp(0.52f, 1.12f,
                hash01(worldIndex * 73 + item, 1033));
        float stretchX = MathUtils.lerp(0.72f, 1.28f,
                hash01(worldIndex * 77 + item, 1037));
        float stretchZ = MathUtils.lerp(0.72f, 1.24f,
                hash01(worldIndex * 79 + item, 1039));
        float gray = MathUtils.lerp(0.23f, 0.39f,
                hash01(worldIndex * 83 + item, 1049));
        float dirtTint = 1f - rockiness;
        colorScratch.set(
                gray + dirtTint * 0.038f,
                gray * 0.94f + dirtTint * 0.018f,
                gray * 0.83f,
                1f);
        float color = colorScratch.toFloatBits();
        float rx = radius * stretchX;
        float rz = radius * stretchZ;

        short a = output.vertex(x - rx, y, z - rz * 0.70f, 0f, 0.65f, 0f, color);
        short b = output.vertex(x + rx, y, z - rz * 0.70f, 0f, 0.65f, 0f, color);
        short c = output.vertex(x + rx * 0.72f, y, z + rz, 0f, 0.65f, 0f, color);
        short d = output.vertex(x - rx * 0.80f, y, z + rz * 0.86f, 0f, 0.65f, 0f, color);
        short top = output.vertex(
                x + (hash01(item, 1051) - 0.5f) * rx * 0.42f,
                y + height,
                z + (hash01(item, 1061) - 0.5f) * rz * 0.42f,
                0f,
                1f,
                0f,
                color);
        output.triangle(a, b, top);
        output.triangle(b, c, top);
        output.triangle(c, d, top);
        output.triangle(d, a, top);
        output.triangle(a, d, c);
        output.triangle(a, c, b);
    }

    private static float[] buildXSamples() {
        ArrayList<Float> samples = new ArrayList<>();
        for (float x = -375f; x <= -244.5f + 0.01f; x += 9f) samples.add(x);
        for (float x = -240f; x <= -100.5f + 0.01f; x += 4.5f) samples.add(x);
        for (float x = -96f; x <= -6f + 0.01f; x += 1.5f) samples.add(x);
        samples.add(-ROAD_EDGE);
        samples.add(ROAD_EDGE);
        for (float x = 6f; x <= 96f + 0.01f; x += 1.5f) samples.add(x);
        for (float x = 100.5f; x <= 240f + 0.01f; x += 4.5f) samples.add(x);
        for (float x = 244.5f; x <= 375f + 0.01f; x += 9f) samples.add(x);
        float[] result = new float[samples.size()];
        for (int i = 0; i < result.length; i++) result[i] = samples.get(i);
        return result;
    }

    private void placeTreesForSegment(int slot, int worldIndex) {
        float zStart = worldIndex * SEGMENT_LENGTH;
        int base = slot * TREES_PER_SEGMENT * 2;
        for (int tree = 0; tree < TREES_PER_SEGMENT; tree++) {
            placeTree(base + tree * 2, -1f, worldIndex, tree, zStart);
            placeTree(base + tree * 2 + 1, 1f, worldIndex, tree, zStart);
        }
    }

    private void placeTree(int index, float side, int worldIndex, int tree, float zStart) {
        int salt = side < 0f ? 211 : 223;
        float treeZ = zStart + 0.8f + hash01(worldIndex * 53 + tree, salt + 5)
                * (SEGMENT_LENGTH - 1.6f);
        float radial = hash01(worldIndex * 61 + tree, salt + 9);
        float x = side * MathUtils.lerp(9.5f, 132f, radial * radial);
        float grove = noise01(groveNoise, x, treeZ);
        float density = MathUtils.lerp(0.32f, 0.94f, smootherStep(0.28f, 0.76f, grove));
        if (hash01(worldIndex * 43 + tree, salt) > density) {
            hideTree(index);
            return;
        }

        float slopeX = groundSlopeX(x, treeZ);
        float slopeZ = groundSlopeZ(x, treeZ);
        float slope = (float) Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        if (slope > 0.82f) {
            hideTree(index);
            return;
        }

        byte type = treeType[index];
        float typeScale = type == 0 ? 1.10f : (type == 1 ? 0.92f : 0.82f);
        float scale = (0.58f + hash01(worldIndex * 67 + tree, salt + 13) * 1.02f)
                * typeScale;
        float widthNoise = MathUtils.lerp(0.84f, 1.20f,
                hash01(worldIndex * 71 + tree, salt + 17));
        float ground = groundHeight(x, treeZ);

        float trunkCenter = type == 2 ? 1.65f : 1.38f;
        float crownCenter = type == 0 ? 4.55f : (type == 1 ? 4.0f : 4.25f);
        trunks[index].transform.setToTranslation(x, ground + trunkCenter * scale, treeZ)
                .scale(scale * 0.92f, scale, scale * 0.92f);
        trees[index].transform.setToTranslation(x, ground + crownCenter * scale, treeZ)
                .scale(scale * widthNoise, scale, scale * widthNoise);

        treeActive[index] = true;
        treeWorldX[index] = x;
        treeWorldY[index] = ground + crownCenter * scale;
        treeWorldZ[index] = treeZ;
        treeScale[index] = scale * Math.max(1f, widthNoise);
    }

    private void hideTree(int index) {
        treeActive[index] = false;
        treeWorldX[index] = 0f;
        treeWorldY[index] = -1000f;
        treeWorldZ[index] = 0f;
        treeScale[index] = 0f;
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
        Camera camera = batch.getCamera();
        float terrainDistance = highQuality ? HIGH_TERRAIN_DISTANCE : LOW_TERRAIN_DISTANCE;

        batch.flush();
        terrainShader.begin(camera, batch.getRenderContext(), environment);
        for (int slot = 0; slot < terrainMeshes.length; slot++) {
            Mesh mesh = terrainMeshes[slot];
            if (mesh != null && segmentVisible(camera, slot, centerZ, terrainDistance, false)) {
                terrainShader.render(mesh);
            }
        }
        terrainShader.end();

        if (highQuality) {
            for (int slot = 0; slot < groundDetailInstances.length; slot++) {
                ModelInstance instance = groundDetailInstances[slot];
                if (instance == null || terrainWorldIndex[slot] == Integer.MIN_VALUE) continue;
                float segmentCenter = segmentCenterZ(slot);
                if (Math.abs(segmentCenter - centerZ) <= DETAIL_DISTANCE
                        && segmentVisible(camera, slot, centerZ, DETAIL_DISTANCE + 8f, true)) {
                    batch.render(instance, environment);
                }
            }
        }

        float treeDistance = highQuality ? HIGH_TREE_DISTANCE : LOW_TREE_DISTANCE;
        for (int i = 0; i < trees.length; i++) {
            if (!treeActive[i]) continue;
            float distance = Math.abs(treeWorldZ[i] - centerZ);
            if (distance > treeDistance) continue;
            if (!highQuality && distance > LOW_FULL_DENSITY_DISTANCE && (i & 1) != 0) continue;
            treeCenterScratch.set(treeWorldX[i], treeWorldY[i], treeWorldZ[i]);
            float radius = treeType[i] == 2 ? 3.3f : 4.2f;
            if (!camera.frustum.sphereInFrustum(treeCenterScratch, radius * treeScale[i])) continue;
            batch.render(trunks[i], environment);
            batch.render(trees[i], environment);
        }
    }

    private boolean segmentVisible(Camera camera, int slot, float centerZ,
                                   float maxDistance, boolean detailBounds) {
        if (terrainWorldIndex[slot] == Integer.MIN_VALUE) return false;
        float segmentCenter = segmentCenterZ(slot);
        if (Math.abs(segmentCenter - centerZ) > maxDistance) return false;

        float minY = segmentMinY[slot];
        float maxY = segmentMaxY[slot];
        float centerY = (minY + maxY) * 0.5f;
        float height = Math.max(16f, maxY - minY + 12f);
        float width = detailBounds ? 144f : X_SAMPLES[X_SAMPLES.length - 1] - X_SAMPLES[0] + 4f;
        boundsCenterScratch.set(0f, centerY, segmentCenter);
        boundsDimensionsScratch.set(width, height, SEGMENT_LENGTH + 4f);
        return camera.frustum.boundsInFrustum(boundsCenterScratch, boundsDimensionsScratch);
    }

    private float segmentCenterZ(int slot) {
        return terrainWorldIndex[slot] * SEGMENT_LENGTH + SEGMENT_LENGTH * 0.5f;
    }

    void dispose() {
        for (int i = 0; i < terrainMeshes.length; i++) {
            if (terrainMeshes[i] != null) {
                terrainMeshes[i].dispose();
                terrainMeshes[i] = null;
            }
            disposeGroundDetail(i);
        }
        terrainShader.dispose();
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
            if (vertexCount >= Short.MAX_VALUE
                    || (vertexCount + 1) * FLOATS_PER_VERTEX > vertices.length) {
                throw new GdxRuntimeException("Ground detail vertex budget exceeded");
            }
            int offset = vertexCount * FLOATS_PER_VERTEX;
            vertices[offset] = x;
            vertices[offset + 1] = y;
            vertices[offset + 2] = z;
            vertices[offset + 3] = nx;
            vertices[offset + 4] = ny;
            vertices[offset + 5] = nz;
            vertices[offset + 6] = color;
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
