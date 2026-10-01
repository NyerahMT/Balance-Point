package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
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
 * X while retaining continuous heights/normals. A single baked albedo texture replaces the old
 * per-quad material classification, eliminating checkerboard terrain patches.
 */
final class TerrainVisuals {
    static final float FLAT_CORRIDOR_HALF_WIDTH = 27f;

    private static final String HEIGHT_PATH = "terrain/world.bpheight";
    private static final String ALBEDO_PATH = "terrain/world_albedo.png";
    private static final int HEIGHT_MAGIC = 0x42504854; // BPHT

    private static final float ROAD_EDGE = 4.22f;
    private static final float SEGMENT_LENGTH = 24f;
    private static final int Z_CELLS = 16; // 1.5 m longitudinal mesh spacing
    private static final int SEGMENTS = 24;
    private static final int TREES_PER_SEGMENT = 8;
    private static final float[] X_SAMPLES = buildXSamples();

    private final Model[] terrainModels = new Model[SEGMENTS];
    private final ModelInstance[] terrainInstances = new ModelInstance[SEGMENTS];
    private final int[] terrainWorldIndex = new int[SEGMENTS];
    private final ModelInstance[] trees = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];
    private final ModelInstance[] trunks = new ModelInstance[SEGMENTS * TREES_PER_SEGMENT * 2];

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
    // Same deterministic surface field used by TerrainBaker.writeAlbedo(). This keeps
    // physical grip aligned with what the player actually sees on the baked terrain.
    private final FastNoiseLite surfaceNoise = createSurfaceNoise();

    TerrainVisuals(Array<Model> ownedModels) {
        loadHeightfield();

        terrainTexture = new Texture(Gdx.files.internal(ALBEDO_PATH), true);
        terrainTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        terrainTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        terrainMaterial = new Material(
                TextureAttribute.createDiffuse(terrainTexture),
                // Small daylight tint keeps the baked albedo from reading oversaturated under
                // the stronger sun while retaining its grass/dirt/rock variation.
                ColorAttribute.createDiffuse(new Color(0.95f, 0.985f, 0.92f, 1f)),
                IntAttribute.createCullFace(GL20.GL_NONE));

        for (int i = 0; i < terrainWorldIndex.length; i++) {
            terrainWorldIndex[i] = Integer.MIN_VALUE;
        }

        ModelBuilder b = new ModelBuilder();
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

    private static FastNoiseLite createSurfaceNoise() {
        FastNoiseLite n = new FastNoiseLite(4502026 + 131);
        n.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        n.SetFractalType(FastNoiseLite.FractalType.FBm);
        n.SetFrequency(0.035f);
        n.SetFractalOctaves(4);
        n.SetFractalGain(0.48f);
        n.SetFractalLacunarity(2.0f);
        return n;
    }

    /**
     * Longitudinal tire friction coefficient at a world point. Road is the 1.00 baseline.
     * Off-road values blend continuously using the same grass/dirt/rock weights as the
     * albedo baker so there are no invisible traction boundaries.
     */
    float tractionCoefficient(float x, float z) {
        if (Math.abs(x) <= ROAD_EDGE) return 1.00f;

        float h = groundHeight(x, z);
        float sx = groundSlopeX(x, z);
        float sz = groundSlopeZ(x, z);
        float slope = (float) Math.sqrt(sx * sx + sz * sz);
        float patch = MathUtils.clamp(surfaceNoise.GetNoise(x, z) * 0.5f + 0.5f, 0f, 1f);

        float rock = smootherStep(0.62f, 1.25f, slope);
        rock = Math.max(rock, smootherStep(58f, 92f, h) * 0.70f);
        float dirt = smootherStep(0.24f, 0.68f, slope) * (1f - rock);
        dirt = Math.max(dirt, smootherStep(0.73f, 0.91f, patch)
                * (1f - rock) * 0.65f);
        float grass = MathUtils.clamp(1f - rock - dirt, 0f, 1f);

        // Dry knobby-tire prototype values: asphalt 1.00, dirt 0.82, rock 0.72, grass 0.64.
        return MathUtils.clamp(grass * 0.64f + dirt * 0.82f + rock * 0.72f, 0.60f, 0.86f);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /**
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
        Model model = buildSegmentModel(worldIndex);
        terrainModels[slot] = model;
        terrainInstances[slot] = new ModelInstance(model);
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
        float[] vertices = new float[vertexCount * 8]; // pos3 + normal3 + uv2
        short[] indices = new short[indexCount];

        float mapSpanX = (mapWidth - 1) * sampleSpacing;
        float mapSpanZ = (mapHeight - 1) * sampleSpacing;
        int vo = 0;
        for (int zi = 0; zi < zCount; zi++) {
            float z = zStart + zi * zStep;
            for (float x : X_SAMPLES) {
                float y = groundHeight(x, z) - 0.028f;
                float sx = groundSlopeX(x, z);
                float sz = groundSlopeZ(x, z);
                float inv = 1f / (float) Math.sqrt(1f + sx * sx + sz * sz);

                vertices[vo++] = x;
                vertices[vo++] = y;
                vertices[vo++] = z;
                vertices[vo++] = -sx * inv;
                vertices[vo++] = inv;
                vertices[vo++] = -sz * inv;
                vertices[vo++] = MathUtils.clamp((x - originX) / mapSpanX, 0f, 1f);
                vertices[vo++] = MathUtils.clamp((z - originZ) / mapSpanZ, 0f, 1f);
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
                // Same diagonal convention as groundHeight(): a-b-c and b-d-c.
                indices[io++] = a;
                indices[io++] = b;
                indices[io++] = c;
                indices[io++] = b;
                indices[io++] = d;
                indices[io++] = c;
            }
        }

        Mesh mesh = new Mesh(true, vertexCount, indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);

        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part("eroded_terrain", mesh, GL20.GL_TRIANGLES, terrainMaterial);
        return builder.end();
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
        // Density varies in broad ~100 m groves rather than being statistically identical
        // every chunk. This creates natural open meadows followed by denser tree lines.
        int groveCell = Math.floorDiv(worldIndex, 4);
        float grove = hash01(groveCell, salt + 71);
        float keepThreshold = MathUtils.lerp(0.08f, 0.40f, grove);
        if (keep < keepThreshold) {
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
                        scale * (1.02f - (widthVariation - 0.82f) * 0.20f));
    }

    private void hideTree(int index) {
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
}
