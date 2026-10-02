package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.environment.ShadowMap;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Mobile terrain material stack modelled after a conventional game-engine landscape shader:
 * scanned material layers, world-space projection, macro breakup, detail normals, distance
 * filtering and optional bike-shadow reception. Geometry/collision remains owned by TerrainVisuals.
 */
final class ProfessionalTerrainShader implements Disposable {
    private static final String MATERIAL_ROOT = "terrain/materials/";

    private static final String VERTEX_SHADER =
            "attribute vec3 a_position;\n"
                    + "attribute vec3 a_normal;\n"
                    + "uniform mat4 u_projViewTrans;\n"
                    + "uniform mat4 u_shadowProjViewTrans;\n"
                    + "varying vec3 v_worldPos;\n"
                    + "varying vec3 v_normal;\n"
                    + "varying vec4 v_shadowPos;\n"
                    + "void main() {\n"
                    + "  vec4 world = vec4(a_position, 1.0);\n"
                    + "  v_worldPos = a_position;\n"
                    + "  v_normal = a_normal;\n"
                    + "  v_shadowPos = u_shadowProjViewTrans * world;\n"
                    + "  gl_Position = u_projViewTrans * world;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "uniform sampler2D u_grassTex;\n"
                    + "uniform sampler2D u_dirtTex;\n"
                    + "uniform sampler2D u_rockTex;\n"
                    + "uniform sampler2D u_detailNormalTex;\n"
                    + "uniform sampler2D u_macroTex;\n"
                    + "uniform sampler2D u_shadowTex;\n"
                    + "uniform vec3 u_cameraPos;\n"
                    + "uniform float u_hasShadow;\n"
                    + "varying vec3 v_worldPos;\n"
                    + "varying vec3 v_normal;\n"
                    + "varying vec4 v_shadowPos;\n"
                    + "float saturate(float x) { return clamp(x, 0.0, 1.0); }\n"
                    + "float smoother(float a, float b, float x) {\n"
                    + "  float t = saturate((x - a) / (b - a));\n"
                    + "  return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);\n"
                    + "}\n"
                    + "vec2 rotateUv(vec2 p, float c, float s) {\n"
                    + "  return vec2(c * p.x - s * p.y, s * p.x + c * p.y);\n"
                    + "}\n"
                    + "float unpackDepth(vec4 rgba) {\n"
                    + "  return dot(rgba, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0,\n"
                    + "      1.0 / 16581375.0));\n"
                    + "}\n"
                    + "float shadowFactor(vec3 n) {\n"
                    + "  if (u_hasShadow < 0.5 || abs(v_shadowPos.w) < 0.0001) return 1.0;\n"
                    + "  vec3 sc = v_shadowPos.xyz / v_shadowPos.w;\n"
                    + "  sc = sc * 0.5 + 0.5;\n"
                    + "  if (sc.x <= 0.0 || sc.x >= 1.0 || sc.y <= 0.0 || sc.y >= 1.0\n"
                    + "      || sc.z <= 0.0 || sc.z >= 1.0) return 1.0;\n"
                    + "  float depth = unpackDepth(texture2D(u_shadowTex, sc.xy));\n"
                    + "  float bias = 0.0018 + (1.0 - n.y) * 0.0020;\n"
                    + "  return sc.z - bias <= depth ? 1.0 : 0.55;\n"
                    + "}\n"
                    + "void main() {\n"
                    + "  vec3 n = normalize(v_normal);\n"
                    + "  vec2 p = v_worldPos.xz;\n"
                    + "  vec4 macro = texture2D(u_macroTex, p / 185.0 + vec2(0.37, 0.19));\n"
                    + "  float slope = 1.0 - saturate(n.y);\n"
                    + "  float ax = abs(v_worldPos.x);\n"
                    + "  float shoulder = 0.0;\n"
                    + "  if (ax > 4.22) {\n"
                    + "    shoulder = (1.0 - smoother(4.55, 7.65, ax)) * 0.72;\n"
                    + "  }\n"
                    + "  float laneX = 1.0 - smoother(1.45, 3.45, abs(v_worldPos.x - 7.5));\n"
                    + "  float laneIn = smoother(18.0, 28.0, v_worldPos.z);\n"
                    + "  float laneOut = 1.0 - smoother(258.0, 270.0, v_worldPos.z);\n"
                    + "  float wear = max(shoulder, laneX * min(laneIn, laneOut));\n"
                    + "  float rockW = smoother(0.20, 0.57, slope);\n"
                    + "  rockW = max(rockW, smoother(62.0, 105.0, v_worldPos.y) * 0.70);\n"
                    + "  rockW *= 1.0 - wear * 0.78;\n"
                    + "  float dirtW = smoother(0.075, 0.36, slope) * (1.0 - rockW * 0.58);\n"
                    + "  dirtW = max(dirtW, smoother(0.57, 0.82, macro.r) * 0.70);\n"
                    + "  dirtW = max(dirtW, wear);\n"
                    + "  dirtW = saturate(dirtW * (1.0 - rockW * 0.34));\n"
                    + "  rockW = saturate(rockW * (1.0 - dirtW * 0.28));\n"
                    + "  float grassW = max(0.0, 1.0 - dirtW - rockW);\n"
                    + "  float sumW = max(0.001, grassW + dirtW + rockW);\n"
                    + "  grassW /= sumW; dirtW /= sumW; rockW /= sumW;\n"
                    + "  vec2 grassUv0 = p / 2.05;\n"
                    + "  vec2 grassUv1 = rotateUv(p + vec2(31.7, -19.4), 0.7317, 0.6816) / 5.35;\n"
                    + "  vec2 dirtUv0 = rotateUv(p, 0.9272, -0.3746) / 1.95;\n"
                    + "  vec2 dirtUv1 = rotateUv(p + vec2(-27.1, 46.8), 0.6157, 0.7880) / 4.65;\n"
                    + "  vec3 grass = mix(texture2D(u_grassTex, grassUv0).rgb,\n"
                    + "      texture2D(u_grassTex, grassUv1).rgb, 0.24);\n"
                    + "  vec3 dirt = mix(texture2D(u_dirtTex, dirtUv0).rgb,\n"
                    + "      texture2D(u_dirtTex, dirtUv1).rgb, 0.22);\n"
                    + "  vec3 an = abs(n);\n"
                    + "  vec2 rockPlane = an.y >= max(an.x, an.z) ? v_worldPos.xz\n"
                    + "      : (an.x > an.z ? v_worldPos.zy : v_worldPos.xy);\n"
                    + "  vec3 rock = texture2D(u_rockTex, rockPlane / 3.05).rgb;\n"
                    + "  float dry = saturate(macro.g * 0.82 + smoother(36.0, 92.0, v_worldPos.y) * 0.26);\n"
                    + "  grass *= mix(vec3(0.88, 0.96, 0.83), vec3(1.16, 1.08, 0.78), dry);\n"
                    + "  dirt *= mix(vec3(1.04, 0.98, 0.92), vec3(0.78, 0.70, 0.61), wear * 0.58);\n"
                    + "  rock *= mix(vec3(0.94, 0.92, 0.88), vec3(1.05, 0.98, 0.87), macro.b);\n"
                    + "  vec3 albedo = grass * grassW + dirt * dirtW + rock * rockW;\n"
                    + "  vec3 detailN = texture2D(u_detailNormalTex, rotateUv(p, 0.9848, 0.1736) / 1.75).rgb\n"
                    + "      * 2.0 - 1.0;\n"
                    + "  float normalStrength = (grassW * 0.18 + dirtW * 0.33 + rockW * 0.10)\n"
                    + "      * smoother(220.0, 18.0, length(v_worldPos - u_cameraPos));\n"
                    + "  n = normalize(n + vec3(detailN.x, 0.0, detailN.y) * normalStrength);\n"
                    + "  vec3 sunDir = normalize(vec3(0.45, 1.0, 0.28));\n"
                    + "  float ndl = max(dot(n, sunDir), 0.0);\n"
                    + "  float hemi = 0.54 + 0.18 * saturate(n.y);\n"
                    + "  float shadow = shadowFactor(n);\n"
                    + "  vec3 sunColor = vec3(1.00, 0.92, 0.79);\n"
                    + "  vec3 lit = albedo * (hemi + sunColor * ndl * 0.63 * shadow);\n"
                    + "  lit *= mix(0.88, 1.10, macro.a);\n"
                    + "  float distanceToCamera = length(v_worldPos - u_cameraPos);\n"
                    + "  float fog = smoother(180.0, 690.0, distanceToCamera) * 0.70;\n"
                    + "  vec3 sky = vec3(0.58, 0.72, 0.80);\n"
                    + "  gl_FragColor = vec4(mix(lit, sky, fog), 1.0);\n"
                    + "}\n";

    private final ShaderProgram program;
    private final Texture grassTexture;
    private final Texture dirtTexture;
    private final Texture rockTexture;
    private final Texture detailNormalTexture;
    private final Texture macroTexture;
    private final Matrix4 noShadow = new Matrix4();

    ProfessionalTerrainShader() {
        grassTexture = loadScannedTexture("sparse_grass_diff.jpg", new Color(0.24f, 0.34f, 0.12f, 1f));
        dirtTexture = loadScannedTexture("dirt_diff.jpg", new Color(0.34f, 0.22f, 0.12f, 1f));
        rockTexture = loadScannedTexture("rocks_ground_05_diff.jpg", new Color(0.32f, 0.30f, 0.26f, 1f));
        detailNormalTexture = loadScannedTexture("dirt_nor_gl.jpg", new Color(0.50f, 0.50f, 1f, 1f));
        macroTexture = createMacroTexture();

        program = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!program.isCompiled()) {
            throw new GdxRuntimeException("Professional terrain shader failed to compile: " + program.getLog());
        }
    }

    private Texture loadScannedTexture(String name, Color fallback) {
        com.badlogic.gdx.files.FileHandle handle = Gdx.files.internal(MATERIAL_ROOT + name);
        Texture texture;
        if (handle.exists()) {
            texture = new Texture(handle, true);
        } else {
            Gdx.app.error("BalancePoint", "Missing staged terrain material " + handle.path());
            Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGB888);
            pixmap.setColor(fallback);
            pixmap.fill();
            texture = new Texture(pixmap, true);
            pixmap.dispose();
        }
        configureSurfaceTexture(texture);
        return texture;
    }

    private static void configureSurfaceTexture(Texture texture) {
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        texture.setAnisotropicFilter(8f);
    }

    private Texture createMacroTexture() {
        final int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        FastNoiseLite red = macroNoise(66031, 0.038f);
        FastNoiseLite green = macroNoise(66047, 0.031f);
        FastNoiseLite blue = macroNoise(66067, 0.052f);
        FastNoiseLite alpha = macroNoise(66083, 0.024f);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float wx = x * 1.7f;
                float wz = y * 1.7f;
                pixmap.setColor(
                        red.GetNoise(wx, wz) * 0.5f + 0.5f,
                        green.GetNoise(wx, wz) * 0.5f + 0.5f,
                        blue.GetNoise(wx, wz) * 0.5f + 0.5f,
                        alpha.GetNoise(wx, wz) * 0.5f + 0.5f);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap, true);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return texture;
    }

    private static FastNoiseLite macroNoise(int seed, float frequency) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFrequency(frequency);
        noise.SetFractalOctaves(4);
        noise.SetFractalGain(0.52f);
        noise.SetFractalLacunarity(2.0f);
        return noise;
    }

    void begin(Camera camera, RenderContext context, Environment environment) {
        context.setDepthTest(GL20.GL_LEQUAL, 0f, 1f);
        context.setDepthMask(true);
        context.setCullFace(GL20.GL_NONE);

        program.bind();
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformf("u_cameraPos", camera.position);

        program.setUniformi("u_grassTex", context.textureBinder.bind(grassTexture));
        program.setUniformi("u_dirtTex", context.textureBinder.bind(dirtTexture));
        program.setUniformi("u_rockTex", context.textureBinder.bind(rockTexture));
        program.setUniformi("u_detailNormalTex", context.textureBinder.bind(detailNormalTexture));
        program.setUniformi("u_macroTex", context.textureBinder.bind(macroTexture));

        ShadowMap shadowMap = environment == null ? null : environment.shadowMap;
        if (shadowMap != null) {
            program.setUniformf("u_hasShadow", 1f);
            program.setUniformMatrix("u_shadowProjViewTrans", shadowMap.getProjViewTrans());
            program.setUniformi("u_shadowTex", context.textureBinder.bind(shadowMap.getDepthMap()));
        } else {
            program.setUniformf("u_hasShadow", 0f);
            program.setUniformMatrix("u_shadowProjViewTrans", noShadow.idt());
            program.setUniformi("u_shadowTex", context.textureBinder.bind(macroTexture));
        }
    }

    void render(Mesh mesh) {
        mesh.render(program, GL20.GL_TRIANGLES);
    }

    void end() {
        // ShaderProgram has no paired end call; ModelBatch's next shader bind takes ownership.
    }

    @Override
    public void dispose() {
        program.dispose();
        grassTexture.dispose();
        dirtTexture.dispose();
        rockTexture.dispose();
        detailNormalTexture.dispose();
        macroTexture.dispose();
    }
}
