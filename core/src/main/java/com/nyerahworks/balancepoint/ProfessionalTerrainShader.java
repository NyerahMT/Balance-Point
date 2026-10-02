package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.environment.ShadowMap;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Mobile landscape shader using scanned materials, stochastic de-tiling, triplanar cliffs,
 * detail normals, directional/hemisphere lighting, aerial perspective and an atmospheric sky.
 */
final class ProfessionalTerrainShader implements Disposable {
    private static final String MATERIAL_ROOT = "terrain/materials/";
    private static final float SHADOW_TEXEL = 1f / 1024f;

    private static final String SKY_VERTEX_SHADER =
            "attribute vec3 a_position;\n"
                    + "varying vec2 v_ndc;\n"
                    + "void main() {\n"
                    + "  v_ndc = a_position.xy;\n"
                    + "  gl_Position = vec4(a_position.xy, 0.999, 1.0);\n"
                    + "}\n";

    private static final String SKY_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
                    + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
                    + "precision highp float;\n"
                    + "#else\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "#endif\n"
                    + "uniform mat4 u_invProjView;\n"
                    + "uniform vec3 u_cameraPos;\n"
                    + "varying vec2 v_ndc;\n"
                    + "float sat(float x) { return clamp(x, 0.0, 1.0); }\n"
                    + "void main() {\n"
                    + "  vec4 farPoint = u_invProjView * vec4(v_ndc, 1.0, 1.0);\n"
                    + "  farPoint /= max(abs(farPoint.w), 0.0001);\n"
                    + "  vec3 dir = normalize(farPoint.xyz - u_cameraPos);\n"
                    + "  float up = sat(dir.y);\n"
                    + "  float horizonBand = exp(-abs(dir.y) * 6.5);\n"
                    + "  float gradient = pow(up, 0.52);\n"
                    + "  vec3 horizon = vec3(0.61, 0.73, 0.79);\n"
                    + "  vec3 midSky = vec3(0.34, 0.56, 0.74);\n"
                    + "  vec3 zenith = vec3(0.16, 0.35, 0.60);\n"
                    + "  vec3 color = mix(horizon, midSky, gradient);\n"
                    + "  color = mix(color, zenith, pow(up, 2.25) * 0.70);\n"
                    + "  vec3 sunDir = normalize(vec3(0.45, 1.0, 0.28));\n"
                    + "  float sunDot = max(dot(dir, sunDir), 0.0);\n"
                    + "  float glow = pow(sunDot, 9.0);\n"
                    + "  float innerGlow = pow(sunDot, 44.0);\n"
                    + "  float disc = pow(sunDot, 720.0);\n"
                    + "  color += vec3(1.00, 0.73, 0.46) * glow * 0.095;\n"
                    + "  color += vec3(1.00, 0.84, 0.62) * innerGlow * 0.16;\n"
                    + "  color += vec3(1.00, 0.94, 0.78) * disc * 1.30;\n"
                    + "  color = mix(color, vec3(0.67, 0.74, 0.76), horizonBand * 0.17);\n"
                    + "  float below = sat(-dir.y * 2.8);\n"
                    + "  color = mix(color, vec3(0.54, 0.61, 0.61), below * 0.40);\n"
                    + "  gl_FragColor = vec4(color, 1.0);\n"
                    + "}\n";

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
                    + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
                    + "precision highp float;\n"
                    + "#else\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "#endif\n"
                    + "uniform sampler2D u_grassTex;\n"
                    + "uniform sampler2D u_dirtTex;\n"
                    + "uniform sampler2D u_rockTex;\n"
                    + "uniform sampler2D u_detailNormalTex;\n"
                    + "uniform sampler2D u_shadowTex;\n"
                    + "uniform vec3 u_cameraPos;\n"
                    + "uniform float u_hasShadow;\n"
                    + "uniform float u_shadowTexel;\n"
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
                    + "vec2 hash22(vec2 p) {\n"
                    + "  vec2 q = vec2(dot(p, vec2(127.1, 311.7)),\n"
                    + "      dot(p, vec2(269.5, 183.3)));\n"
                    + "  return fract(sin(q) * 43758.5453);\n"
                    + "}\n"
                    + "float hash21(vec2 p) {\n"
                    + "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                    + "}\n"
                    + "float valueNoise(vec2 p) {\n"
                    + "  vec2 i = floor(p);\n"
                    + "  vec2 f = fract(p);\n"
                    + "  vec2 u = f * f * (3.0 - 2.0 * f);\n"
                    + "  float a = hash21(i);\n"
                    + "  float b = hash21(i + vec2(1.0, 0.0));\n"
                    + "  float c = hash21(i + vec2(0.0, 1.0));\n"
                    + "  float d = hash21(i + vec2(1.0, 1.0));\n"
                    + "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n"
                    + "}\n"
                    + "float fbm(vec2 p) {\n"
                    + "  float value = valueNoise(p) * 0.55;\n"
                    + "  p = rotateUv(p * 2.03 + vec2(17.2, -8.7), 0.8192, 0.5736);\n"
                    + "  value += valueNoise(p) * 0.29;\n"
                    + "  p = rotateUv(p * 2.07 + vec2(-4.3, 21.8), 0.9397, -0.3420);\n"
                    + "  value += valueNoise(p) * 0.16;\n"
                    + "  return value;\n"
                    + "}\n"
                    + "vec3 sampleDetiled(sampler2D tex, vec2 world, float tileMeters,\n"
                    + "    float cellMeters, float seed) {\n"
                    + "  vec2 grid = world / cellMeters;\n"
                    + "  vec2 cell = floor(grid);\n"
                    + "  vec2 f = fract(grid);\n"
                    + "  vec2 w = f * f * (3.0 - 2.0 * f);\n"
                    + "  vec2 uv = world / tileMeters;\n"
                    + "  vec2 seedVec = vec2(seed, seed * 1.713);\n"
                    + "  vec2 o00 = hash22(cell + seedVec) * 29.37;\n"
                    + "  vec2 o10 = hash22(cell + vec2(1.0, 0.0) + seedVec) * 29.37;\n"
                    + "  vec2 o01 = hash22(cell + vec2(0.0, 1.0) + seedVec) * 29.37;\n"
                    + "  vec2 o11 = hash22(cell + vec2(1.0, 1.0) + seedVec) * 29.37;\n"
                    + "  vec3 a = texture2D(tex, uv + o00).rgb;\n"
                    + "  vec3 b = texture2D(tex, uv + o10).rgb;\n"
                    + "  vec3 c = texture2D(tex, uv + o01).rgb;\n"
                    + "  vec3 d = texture2D(tex, uv + o11).rgb;\n"
                    + "  return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);\n"
                    + "}\n"
                    + "vec3 sampleRockTriplanar(vec3 world, vec3 weights, float meters,\n"
                    + "    vec3 warp) {\n"
                    + "  vec3 p = world + warp;\n"
                    + "  vec3 x = texture2D(u_rockTex, p.zy / meters + vec2(3.17, 8.41)).rgb;\n"
                    + "  vec3 y = texture2D(u_rockTex, p.xz / meters + vec2(11.23, 1.79)).rgb;\n"
                    + "  vec3 z = texture2D(u_rockTex, p.xy / meters + vec2(6.53, 13.11)).rgb;\n"
                    + "  return x * weights.x + y * weights.y + z * weights.z;\n"
                    + "}\n"
                    + "float unpackDepth(vec4 rgba) {\n"
                    + "  return dot(rgba, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0,\n"
                    + "      1.0 / 16581375.0));\n"
                    + "}\n"
                    + "float shadowTap(vec2 uv, float depth, float bias) {\n"
                    + "  float stored = unpackDepth(texture2D(u_shadowTex, uv));\n"
                    + "  return depth - bias <= stored ? 1.0 : 0.0;\n"
                    + "}\n"
                    + "float shadowFactor(vec3 n) {\n"
                    + "  if (u_hasShadow < 0.5 || abs(v_shadowPos.w) < 0.0001) return 1.0;\n"
                    + "  vec3 sc = v_shadowPos.xyz / v_shadowPos.w;\n"
                    + "  sc = sc * 0.5 + 0.5;\n"
                    + "  if (sc.x <= 0.0 || sc.x >= 1.0 || sc.y <= 0.0 || sc.y >= 1.0\n"
                    + "      || sc.z <= 0.0 || sc.z >= 1.0) return 1.0;\n"
                    + "  float bias = 0.0022 + (1.0 - max(n.y, 0.0)) * 0.0032;\n"
                    + "  vec2 d = vec2(u_shadowTexel);\n"
                    + "  float sum = shadowTap(sc.xy + vec2(-d.x, -d.y), sc.z, bias);\n"
                    + "  sum += shadowTap(sc.xy + vec2(d.x, -d.y), sc.z, bias);\n"
                    + "  sum += shadowTap(sc.xy + vec2(-d.x, d.y), sc.z, bias);\n"
                    + "  sum += shadowTap(sc.xy + vec2(d.x, d.y), sc.z, bias);\n"
                    + "  return mix(0.42, 1.0, sum * 0.25);\n"
                    + "}\n"
                    + "void main() {\n"
                    + "  vec3 n = normalize(v_normal);\n"
                    + "  vec2 p = v_worldPos.xz;\n"
                    + "  float macroA = fbm(p * 0.013);\n"
                    + "  float macroB = fbm(rotateUv(p, 0.8387, 0.5446) * 0.0061\n"
                    + "      + vec2(19.2, -7.4));\n"
                    + "  float macroC = fbm(rotateUv(p, 0.9659, -0.2588) * 0.021\n"
                    + "      + vec2(-11.8, 23.1));\n"
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
                    + "  float rockW = smoother(0.18, 0.54, slope);\n"
                    + "  rockW = max(rockW, smoother(58.0, 104.0, v_worldPos.y) * 0.72);\n"
                    + "  rockW *= 1.0 - wear * 0.80;\n"
                    + "  float dirtW = smoother(0.07, 0.34, slope) * (1.0 - rockW * 0.56);\n"
                    + "  dirtW = max(dirtW, smoother(0.61, 0.84, macroA) * 0.68);\n"
                    + "  dirtW = max(dirtW, smoother(0.69, 0.88, macroC) * 0.28);\n"
                    + "  dirtW = max(dirtW, wear);\n"
                    + "  dirtW = saturate(dirtW * (1.0 - rockW * 0.34));\n"
                    + "  rockW = saturate(rockW * (1.0 - dirtW * 0.28));\n"
                    + "  float grassW = max(0.0, 1.0 - dirtW - rockW);\n"
                    + "  float sumW = max(0.001, grassW + dirtW + rockW);\n"
                    + "  grassW /= sumW; dirtW /= sumW; rockW /= sumW;\n"
                    + "  vec3 grass = sampleDetiled(u_grassTex, p, 2.35, 9.7, 3.1);\n"
                    + "  vec2 grassCoarseUv = rotateUv(p + vec2(31.7, -19.4),\n"
                    + "      0.7317, 0.6816) / 6.65;\n"
                    + "  vec3 grassCoarse = texture2D(u_grassTex, grassCoarseUv).rgb;\n"
                    + "  grass = mix(grass, grassCoarse, 0.14 + macroB * 0.17);\n"
                    + "  vec2 dirtWorld = rotateUv(p, 0.9272, -0.3746);\n"
                    + "  vec3 dirt = sampleDetiled(u_dirtTex, dirtWorld, 2.15, 10.9, 8.7);\n"
                    + "  vec2 dirtCoarseUv = rotateUv(p + vec2(-27.1, 46.8),\n"
                    + "      0.6157, 0.7880) / 5.75;\n"
                    + "  vec3 dirtCoarse = texture2D(u_dirtTex, dirtCoarseUv).rgb;\n"
                    + "  dirt = mix(dirt, dirtCoarse, 0.13 + macroC * 0.16);\n"
                    + "  vec3 tri = pow(abs(n), vec3(4.5));\n"
                    + "  tri /= max(tri.x + tri.y + tri.z, 0.001);\n"
                    + "  vec3 warp = vec3(macroA - 0.5, macroB - 0.5, macroC - 0.5) * 2.8;\n"
                    + "  vec3 rockFine = sampleRockTriplanar(v_worldPos, tri, 3.35, warp);\n"
                    + "  vec3 rockCoarse = sampleRockTriplanar(v_worldPos, tri, 11.7,\n"
                    + "      warp * -2.3 + vec3(17.0, -9.0, 23.0));\n"
                    + "  vec3 rock = mix(rockFine, rockCoarse, 0.20 + macroB * 0.25);\n"
                    + "  float dry = saturate(macroB * 0.76\n"
                    + "      + smoother(34.0, 94.0, v_worldPos.y) * 0.25);\n"
                    + "  grass *= mix(vec3(0.88, 0.96, 0.84), vec3(1.08, 1.02, 0.78), dry);\n"
                    + "  dirt *= mix(vec3(1.01, 0.97, 0.91), vec3(0.79, 0.71, 0.61),\n"
                    + "      wear * 0.56);\n"
                    + "  rock *= mix(vec3(0.94, 0.93, 0.90), vec3(1.02, 0.97, 0.89), macroA);\n"
                    + "  vec3 albedo = grass * grassW + dirt * dirtW + rock * rockW;\n"
                    + "  vec2 normalUv0 = rotateUv(p + vec2(4.7, -9.3),\n"
                    + "      0.9848, 0.1736) / 1.95;\n"
                    + "  vec2 normalUv1 = rotateUv(p + vec2(-21.2, 14.1),\n"
                    + "      0.6157, -0.7880) / 4.85;\n"
                    + "  vec2 detail0 = texture2D(u_detailNormalTex, normalUv0).xy * 2.0 - 1.0;\n"
                    + "  vec2 detail1 = texture2D(u_detailNormalTex, normalUv1).xy * 2.0 - 1.0;\n"
                    + "  vec2 detail = mix(detail0, detail1, 0.28 + macroC * 0.28);\n"
                    + "  float distanceToCamera = length(v_worldPos - u_cameraPos);\n"
                    + "  float detailFade = 1.0 - smoother(42.0, 172.0, distanceToCamera);\n"
                    + "  float normalStrength = (grassW * 0.070 + dirtW * 0.19 + rockW * 0.11)\n"
                    + "      * detailFade;\n"
                    + "  n = normalize(n + vec3(detail.x, 0.0, detail.y) * normalStrength);\n"
                    + "  vec3 sunDir = normalize(vec3(0.45, 1.0, 0.28));\n"
                    + "  float ndl = max(dot(n, sunDir), 0.0);\n"
                    + "  float shadow = shadowFactor(n);\n"
                    + "  vec3 linearAlbedo = pow(max(albedo, vec3(0.001)), vec3(2.2));\n"
                    + "  float skyFacing = 0.32 + 0.68 * saturate(n.y);\n"
                    + "  vec3 skyAmbient = vec3(0.17, 0.25, 0.35) * skyFacing;\n"
                    + "  float groundFacing = 1.0 - saturate(n.y);\n"
                    + "  vec3 groundBounce = vec3(0.25, 0.16, 0.085)\n"
                    + "      * (0.070 + groundFacing * 0.085);\n"
                    + "  vec3 sun = vec3(1.70, 1.42, 1.08) * ndl * shadow;\n"
                    + "  float cavity = mix(0.87, 1.03, macroC);\n"
                    + "  vec3 lit = linearAlbedo * (skyAmbient + groundBounce + sun) * cavity;\n"
                    + "  vec3 viewDir = normalize(u_cameraPos - v_worldPos);\n"
                    + "  vec3 halfDir = normalize(sunDir + viewDir);\n"
                    + "  float spec = pow(max(dot(n, halfDir), 0.0), 28.0) * shadow;\n"
                    + "  lit += vec3(1.0, 0.92, 0.78) * spec\n"
                    + "      * (dirtW * 0.015 + rockW * 0.028);\n"
                    + "  float backLight = pow(saturate(dot(-viewDir, sunDir)), 4.0);\n"
                    + "  lit += linearAlbedo * vec3(0.17, 0.12, 0.07) * backLight * grassW;\n"
                    + "  lit = lit / (lit + vec3(0.82));\n"
                    + "  lit = pow(max(lit, vec3(0.0)), vec3(1.0 / 2.2));\n"
                    + "  float luminance = dot(lit, vec3(0.2126, 0.7152, 0.0722));\n"
                    + "  lit = mix(vec3(luminance), lit, 1.05);\n"
                    + "  float distanceFog = smoother(175.0, 650.0, distanceToCamera);\n"
                    + "  float lowAltitude = 1.0 - smoother(18.0, 96.0, v_worldPos.y);\n"
                    + "  float lowFog = smoother(120.0, 470.0, distanceToCamera) * lowAltitude * 0.18;\n"
                    + "  float fog = saturate(distanceFog * 0.70 + lowFog);\n"
                    + "  vec3 fogColor = vec3(0.60, 0.70, 0.73);\n"
                    + "  float sunHaze = pow(saturate(dot(normalize(v_worldPos - u_cameraPos), sunDir)),\n"
                    + "      5.0);\n"
                    + "  fogColor += vec3(0.10, 0.055, 0.018) * sunHaze;\n"
                    + "  gl_FragColor = vec4(mix(lit, fogColor, fog), 1.0);\n"
                    + "}\n";

    private final ShaderProgram program;
    private final ShaderProgram skyProgram;
    private final Texture grassTexture;
    private final Texture dirtTexture;
    private final Texture rockTexture;
    private final Texture detailNormalTexture;
    private final Mesh skyMesh;
    private final Matrix4 noShadow = new Matrix4();
    private final Matrix4 inverseProjectionView = new Matrix4();

    ProfessionalTerrainShader() {
        grassTexture = loadScannedTexture(
                "sparse_grass_diff.jpg",
                new Color(0.24f, 0.34f, 0.12f, 1f));
        dirtTexture = loadScannedTexture(
                "dirt_diff.jpg",
                new Color(0.34f, 0.22f, 0.12f, 1f));
        rockTexture = loadScannedTexture(
                "rocks_ground_05_diff.jpg",
                new Color(0.32f, 0.30f, 0.26f, 1f));
        detailNormalTexture = loadScannedTexture(
                "dirt_nor_gl.jpg",
                new Color(0.50f, 0.50f, 1f, 1f));

        program = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!program.isCompiled()) {
            throw new GdxRuntimeException(
                    "Professional terrain shader failed to compile: " + program.getLog());
        }
        skyProgram = new ShaderProgram(SKY_VERTEX_SHADER, SKY_FRAGMENT_SHADER);
        if (!skyProgram.isCompiled()) {
            throw new GdxRuntimeException(
                    "Atmospheric sky shader failed to compile: " + skyProgram.getLog());
        }
        skyMesh = createSkyMesh();
    }

    private static Mesh createSkyMesh() {
        Mesh mesh = new Mesh(true, 4, 6, VertexAttribute.Position());
        mesh.setVertices(new float[] {
                -1f, -1f, 0f,
                 1f, -1f, 0f,
                 1f,  1f, 0f,
                -1f,  1f, 0f
        });
        mesh.setIndices(new short[] {0, 1, 2, 0, 2, 3});
        return mesh;
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
        texture.setAnisotropicFilter(12f);
    }

    void begin(Camera camera, RenderContext context, Environment environment) {
        renderSky(camera, context);

        context.setDepthTest(GL20.GL_LEQUAL, 0f, 1f);
        context.setDepthMask(true);
        context.setCullFace(GL20.GL_NONE);

        program.bind();
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformf("u_cameraPos", camera.position);
        program.setUniformf("u_shadowTexel", SHADOW_TEXEL);
        program.setUniformi("u_grassTex", context.textureBinder.bind(grassTexture));
        program.setUniformi("u_dirtTex", context.textureBinder.bind(dirtTexture));
        program.setUniformi("u_rockTex", context.textureBinder.bind(rockTexture));
        program.setUniformi(
                "u_detailNormalTex",
                context.textureBinder.bind(detailNormalTexture));

        ShadowMap shadowMap = environment == null ? null : environment.shadowMap;
        if (shadowMap != null) {
            program.setUniformf("u_hasShadow", 1f);
            program.setUniformMatrix("u_shadowProjViewTrans", shadowMap.getProjViewTrans());
            program.setUniformi("u_shadowTex", context.textureBinder.bind(shadowMap.getDepthMap()));
        } else {
            program.setUniformf("u_hasShadow", 0f);
            program.setUniformMatrix("u_shadowProjViewTrans", noShadow.idt());
            program.setUniformi("u_shadowTex", context.textureBinder.bind(grassTexture));
        }
    }

    private void renderSky(Camera camera, RenderContext context) {
        context.setDepthTest(GL20.GL_NONE, 0f, 1f);
        context.setDepthMask(false);
        context.setCullFace(GL20.GL_NONE);
        skyProgram.bind();
        inverseProjectionView.set(camera.combined).inv();
        skyProgram.setUniformMatrix("u_invProjView", inverseProjectionView);
        skyProgram.setUniformf("u_cameraPos", camera.position);
        skyMesh.render(skyProgram, GL20.GL_TRIANGLES);
    }

    void render(Mesh mesh) {
        mesh.render(program, GL20.GL_TRIANGLES);
    }

    void end() {
        // ShaderProgram has no paired end call; the next ModelBatch shader bind takes ownership.
    }

    @Override
    public void dispose() {
        program.dispose();
        skyProgram.dispose();
        skyMesh.dispose();
        grassTexture.dispose();
        dirtTexture.dispose();
        rockTexture.dispose();
        detailNormalTexture.dispose();
    }
}
