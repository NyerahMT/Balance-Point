package com.nyerahworks.balancepoint;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Offline terrain baker for Balance Point.
 *
 * Generates a deterministic, eroded heightfield from FastNoiseLite, carves the road corridor
 * after erosion, then emits a compact 16-bit height asset plus a continuous albedo texture.
 * This tool is build-time only; no AWT classes are referenced by the runtime game.
 */
public final class TerrainBaker {
    private static final int WIDTH = 513;
    private static final int HEIGHT = 4097;
    private static final float SPACING = 1.5f;
    private static final float ORIGIN_X = -384f;
    private static final float ORIGIN_Z = -504f;
    private static final float ENCODE_MIN = -18f;
    private static final float ENCODE_MAX = 132f;
    private static final int SEED = 4502026;

    private static final int DROPLETS = 420_000;
    private static final int MAX_DROPLET_STEPS = 34;

    private final float[] heights = new float[WIDTH * HEIGHT];

    private final FastNoiseLite macro = noise(SEED + 11,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.00155f, 5, 0.50f, 2.0f);
    private final FastNoiseLite ridges = noise(SEED + 23,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.Ridged,
            0.0048f, 5, 0.49f, 2.06f);
    private final FastNoiseLite regional = noise(SEED + 37,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.00105f, 4, 0.52f, 2.0f);
    private final FastNoiseLite detail = noise(SEED + 53,
            FastNoiseLite.NoiseType.Perlin, FastNoiseLite.FractalType.FBm,
            0.0125f, 4, 0.47f, 2.0f);
    private final FastNoiseLite warpA = noise(SEED + 71,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0018f, 3, 0.52f, 2.0f);
    private final FastNoiseLite warpB = noise(SEED + 79,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0018f, 3, 0.52f, 2.0f);
    private final FastNoiseLite corridor = noise(SEED + 97,
            FastNoiseLite.NoiseType.OpenSimplex2S, FastNoiseLite.FractalType.FBm,
            0.0031f, 3, 0.52f, 2.0f);
    private final FastNoiseLite surface = noise(SEED + 131,
            FastNoiseLite.NoiseType.Perlin, FastNoiseLite.FractalType.FBm,
            0.035f, 4, 0.48f, 2.0f);

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "app/src/main/assets/terrain");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("Could not create " + outDir);
        }

        TerrainBaker baker = new TerrainBaker();
        System.out.println("Generating base landform...");
        baker.generateBase();
        System.out.println("Thermal relaxation...");
        baker.thermalRelax(3);
        System.out.println("Hydraulic erosion: " + DROPLETS + " droplets...");
        baker.hydraulicErode();
        System.out.println("Carving road valley...");
        baker.carveRoadCorridor();
        baker.thermalRelax(1);
        System.out.println("Local Gaussian terrain smoothing...");
        baker.gaussianSmooth(2);

        File heightFile = new File(outDir, "world.bpheight");
        File albedoFile = new File(outDir, "world_albedo.png");
        baker.writeHeightfield(heightFile);
        baker.writeAlbedo(albedoFile, 512, 4096);
        System.out.println("Wrote " + heightFile + " (" + heightFile.length() + " bytes)");
        System.out.println("Wrote " + albedoFile + " (" + albedoFile.length() + " bytes)");
    }

    private static FastNoiseLite noise(int seed, FastNoiseLite.NoiseType type,
                                       FastNoiseLite.FractalType fractal,
                                       float frequency, int octaves,
                                       float gain, float lacunarity) {
        FastNoiseLite n = new FastNoiseLite(seed);
        n.SetNoiseType(type);
        n.SetFractalType(fractal);
        n.SetFrequency(frequency);
        n.SetFractalOctaves(octaves);
        n.SetFractalGain(gain);
        n.SetFractalLacunarity(lacunarity);
        return n;
    }

    private void generateBase() {
        for (int iz = 0; iz < HEIGHT; iz++) {
            float z = ORIGIN_Z + iz * SPACING;
            for (int ix = 0; ix < WIDTH; ix++) {
                float x = ORIGIN_X + ix * SPACING;

                float wx = x + warpA.GetNoise(x, z) * 92f;
                float wz = z + warpB.GetNoise(x - 913f, z + 611f) * 92f;

                float broad = macro.GetNoise(wx, wz);
                float region = unit(regional.GetNoise(wx * 0.78f + 170f, wz * 0.78f - 330f));
                float ridge = unit(ridges.GetNoise(wx + wz * 0.10f, wz - wx * 0.07f));
                float ridgeShape = pow(clamp01((ridge - 0.18f) / 0.82f), 2.05f);
                float mountainMask = smootherStep(0.34f, 0.72f, region);

                float rolling = broad * 12.5f;
                float mountain = mountainMask * ridgeShape * (42f + 34f * region);
                float valleys = (unit(macro.GetNoise(wx + 2300f, wz - 1700f)) - 0.5f) * 9f;
                float fine = detail.GetNoise(wx, wz) * (1.2f + mountainMask * 2.4f);

                // Slightly bias the world upward so eroded drainage can cut below surrounding land
                // without creating enormous submerged basins.
                heights[index(ix, iz)] = 5.5f + rolling + valleys + mountain + fine;
            }
        }
    }

    /** Small thermal pass removes needle-like ridges before/after hydraulic erosion. */
    private void thermalRelax(int iterations) {
        float[] delta = new float[heights.length];
        final float talus = 1.25f;
        for (int iter = 0; iter < iterations; iter++) {
            java.util.Arrays.fill(delta, 0f);
            for (int z = 1; z < HEIGHT - 1; z++) {
                for (int x = 1; x < WIDTH - 1; x++) {
                    int i = index(x, z);
                    float h = heights[i];
                    int low = i;
                    float lowH = h;
                    int[] n = {i - 1, i + 1, i - WIDTH, i + WIDTH};
                    for (int ni : n) {
                        if (heights[ni] < lowH) {
                            lowH = heights[ni];
                            low = ni;
                        }
                    }
                    float excess = h - lowH - talus;
                    if (low != i && excess > 0f) {
                        float move = excess * 0.12f;
                        delta[i] -= move;
                        delta[low] += move;
                    }
                }
            }
            for (int i = 0; i < heights.length; i++) heights[i] += delta[i];
        }
    }

    /**
     * Particle hydraulic erosion. The droplet follows the local gradient, carries sediment,
     * deposits when it slows/climbs, and erodes when capacity exceeds its current load.
     */
    private void hydraulicErode() {
        Random random = new Random(SEED ^ 0x5f3759df);
        HeightSample s0 = new HeightSample();
        HeightSample s1 = new HeightSample();

        for (int d = 0; d < DROPLETS; d++) {
            float x = 2f + random.nextFloat() * (WIDTH - 5f);
            float z = 2f + random.nextFloat() * (HEIGHT - 5f);
            float dirX = 0f;
            float dirZ = 0f;
            float speed = 1f;
            float water = 1f;
            float sediment = 0f;

            for (int step = 0; step < MAX_DROPLET_STEPS; step++) {
                sampleGrid(x, z, s0);

                dirX = dirX * 0.18f - s0.gradX * 0.82f;
                dirZ = dirZ * 0.18f - s0.gradZ * 0.82f;
                float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (len < 0.0001f) {
                    float a = random.nextFloat() * 6.2831855f;
                    dirX = (float) Math.cos(a);
                    dirZ = (float) Math.sin(a);
                } else {
                    dirX /= len;
                    dirZ /= len;
                }

                float nx = x + dirX;
                float nz = z + dirZ;
                if (nx < 1f || nx >= WIDTH - 2f || nz < 1f || nz >= HEIGHT - 2f) break;

                sampleGrid(nx, nz, s1);
                float dh = s1.height - s0.height;
                float capacity = Math.max(-dh * speed * water * 5.2f, 0.018f);

                if (sediment > capacity || dh > 0f) {
                    float amount = dh > 0f
                            ? Math.min(sediment, dh)
                            : (sediment - capacity) * 0.20f;
                    if (amount > 0f) {
                        depositBilinear(x, z, amount);
                        sediment -= amount;
                    }
                } else {
                    float amount = Math.min((capacity - sediment) * 0.23f, Math.max(0f, -dh));
                    if (amount > 0f) {
                        float removed = erodeBilinear(x, z, amount);
                        sediment += removed;
                    }
                }

                speed = (float) Math.sqrt(Math.max(0.05f, speed * speed - dh * 4.2f));
                water *= 0.984f;
                x = nx;
                z = nz;
                if (water < 0.08f) break;
            }
        }
    }

    private void carveRoadCorridor() {
        for (int iz = 0; iz < HEIGHT; iz++) {
            float z = ORIGIN_Z + iz * SPACING;
            float leftN = unit(corridor.GetNoise(z, -730f));
            float rightN = unit(corridor.GetNoise(z + 1170f, 810f));
            float leftEdge = 15f + leftN * 34f;
            float rightEdge = 15f + rightN * 34f;

            for (int ix = 0; ix < WIDTH; ix++) {
                float x = ORIGIN_X + ix * SPACING;
                float ax = Math.abs(x);
                float edge = x < 0f ? leftEdge : rightEdge;

                // Fully flat road/field zone followed by a long C2-continuous blend into the
                // eroded landscape. Narrow sections read as road cuts; wide sections become
                // believable open fields/residential space.
                float blend = 18f + 5f * unit(corridor.GetNoise(z - 2100f, x < 0f ? 310f : -510f));
                float t = smootherStep(edge, edge + blend, ax);
                float h = heights[index(ix, iz)];
                heights[index(ix, iz)] = h * t;
            }
        }
    }

    /**
     * Local low-pass pass over the baked heightfield. A separable 1-4-6-4-1 kernel
     * removes one/two-cell needles left by ridged noise + erosion without flattening
     * the broad mountain mass. Two passes give an effective smoothing radius of only
     * a few metres on the 1.5 m source grid.
     */
    private void gaussianSmooth(int passes) {
        final int[] kernel = {1, 4, 6, 4, 1};
        final int kernelSum = 16;
        float[] scratch = new float[heights.length];

        for (int pass = 0; pass < passes; pass++) {
            for (int z = 0; z < HEIGHT; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    float sum = 0f;
                    for (int k = -2; k <= 2; k++) {
                        int sx = Math.max(0, Math.min(WIDTH - 1, x + k));
                        sum += heights[index(sx, z)] * kernel[k + 2];
                    }
                    scratch[index(x, z)] = sum / kernelSum;
                }
            }

            for (int z = 0; z < HEIGHT; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    float sum = 0f;
                    for (int k = -2; k <= 2; k++) {
                        int sz = Math.max(0, Math.min(HEIGHT - 1, z + k));
                        sum += scratch[index(x, sz)] * kernel[k + 2];
                    }
                    heights[index(x, z)] = sum / kernelSum;
                }
            }
        }
    }

    private void writeHeightfield(File file) throws Exception {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), 1 << 20))) {
            out.writeInt(0x42504854); // BPHT
            out.writeInt(1);
            out.writeInt(WIDTH);
            out.writeInt(HEIGHT);
            out.writeFloat(SPACING);
            out.writeFloat(ORIGIN_X);
            out.writeFloat(ORIGIN_Z);
            out.writeFloat(ENCODE_MIN);
            out.writeFloat(ENCODE_MAX);
            float range = ENCODE_MAX - ENCODE_MIN;
            for (float h : heights) {
                int q = Math.round(clamp01((h - ENCODE_MIN) / range) * 65535f);
                out.writeShort(q);
            }
        }
    }

    private void writeAlbedo(File file, int texW, int texH) throws Exception {
        BufferedImage image = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_RGB);
        float worldW = (WIDTH - 1) * SPACING;
        float worldH = (HEIGHT - 1) * SPACING;

        for (int py = 0; py < texH; py++) {
            float z = ORIGIN_Z + (py / (float) (texH - 1)) * worldH;
            for (int px = 0; px < texW; px++) {
                float x = ORIGIN_X + (px / (float) (texW - 1)) * worldW;
                float h = sampleWorld(x, z);
                float sx = (sampleWorld(x + 1.5f, z) - sampleWorld(x - 1.5f, z)) / 3f;
                float sz = (sampleWorld(x, z + 1.5f) - sampleWorld(x, z - 1.5f)) / 3f;
                float slope = (float) Math.sqrt(sx * sx + sz * sz);

                float patch = unit(surface.GetNoise(x, z));
                float tiny = unit(detail.GetNoise(x * 2.8f + 700f, z * 2.8f - 500f));
                float rock = smootherStep(0.62f, 1.25f, slope);
                rock = Math.max(rock, smootherStep(58f, 92f, h) * 0.70f);
                float dirt = smootherStep(0.24f, 0.68f, slope) * (1f - rock);
                dirt = Math.max(dirt, smootherStep(0.73f, 0.91f, patch) * (1f - rock) * 0.65f);
                float grass = clamp01(1f - rock - dirt);

                float[] grassC = {0.24f, 0.39f, 0.13f};
                float[] dirtC = {0.38f, 0.28f, 0.16f};
                float[] rockC = {0.34f, 0.35f, 0.33f};
                float brightness = 0.84f + tiny * 0.24f + (patch - 0.5f) * 0.10f;

                float r = (grassC[0] * grass + dirtC[0] * dirt + rockC[0] * rock) * brightness;
                float g = (grassC[1] * grass + dirtC[1] * dirt + rockC[1] * rock) * brightness;
                float b = (grassC[2] * grass + dirtC[2] * dirt + rockC[2] * rock) * brightness;

                int rgb = new Color(clamp01(r), clamp01(g), clamp01(b)).getRGB() & 0x00ffffff;
                // Raw mesh UV v=0 samples the first PNG row, so keep image rows in
                // increasing world-Z order. The old extra flip mirrored the albedo along Z.
                image.setRGB(px, py, rgb);
            }
        }
        ImageIO.write(image, "png", file);
    }

    private float sampleWorld(float x, float z) {
        float gx = (x - ORIGIN_X) / SPACING;
        float gz = (z - ORIGIN_Z) / SPACING;
        gx = clamp(gx, 0f, WIDTH - 1.001f);
        gz = clamp(gz, 0f, HEIGHT - 1.001f);
        int ix = (int) gx;
        int iz = (int) gz;
        float fx = gx - ix;
        float fz = gz - iz;
        float h00 = heights[index(ix, iz)];
        float h10 = heights[index(ix + 1, iz)];
        float h01 = heights[index(ix, iz + 1)];
        float h11 = heights[index(ix + 1, iz + 1)];
        if (fx + fz <= 1f) {
            return h00 + fx * (h10 - h00) + fz * (h01 - h00);
        }
        return h11 + (1f - fx) * (h01 - h11) + (1f - fz) * (h10 - h11);
    }

    private void sampleGrid(float x, float z, HeightSample out) {
        int ix = (int) x;
        int iz = (int) z;
        float fx = x - ix;
        float fz = z - iz;
        float h00 = heights[index(ix, iz)];
        float h10 = heights[index(ix + 1, iz)];
        float h01 = heights[index(ix, iz + 1)];
        float h11 = heights[index(ix + 1, iz + 1)];

        out.height = h00 * (1f - fx) * (1f - fz)
                + h10 * fx * (1f - fz)
                + h01 * (1f - fx) * fz
                + h11 * fx * fz;
        out.gradX = (h10 - h00) * (1f - fz) + (h11 - h01) * fz;
        out.gradZ = (h01 - h00) * (1f - fx) + (h11 - h10) * fx;
    }

    private void depositBilinear(float x, float z, float amount) {
        int ix = (int) x;
        int iz = (int) z;
        float fx = x - ix;
        float fz = z - iz;
        heights[index(ix, iz)] += amount * (1f - fx) * (1f - fz);
        heights[index(ix + 1, iz)] += amount * fx * (1f - fz);
        heights[index(ix, iz + 1)] += amount * (1f - fx) * fz;
        heights[index(ix + 1, iz + 1)] += amount * fx * fz;
    }

    private float erodeBilinear(float x, float z, float amount) {
        int ix = (int) x;
        int iz = (int) z;
        float fx = x - ix;
        float fz = z - iz;
        float[] weights = {
                (1f - fx) * (1f - fz), fx * (1f - fz),
                (1f - fx) * fz, fx * fz
        };
        int[] ids = {
                index(ix, iz), index(ix + 1, iz),
                index(ix, iz + 1), index(ix + 1, iz + 1)
        };
        float removed = 0f;
        for (int i = 0; i < 4; i++) {
            float take = amount * weights[i];
            heights[ids[i]] -= take;
            removed += take;
        }
        return removed;
    }

    private static int index(int x, int z) {
        return z * WIDTH + x;
    }

    private static float unit(float n) {
        return clamp01(n * 0.5f + 0.5f);
    }

    private static float smootherStep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float pow(float x, float p) {
        return (float) Math.pow(x, p);
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    private static float clamp(float x, float lo, float hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }

    private static final class HeightSample {
        float height;
        float gradX;
        float gradZ;
    }
}
