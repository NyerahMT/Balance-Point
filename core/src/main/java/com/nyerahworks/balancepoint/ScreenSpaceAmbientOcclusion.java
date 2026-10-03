package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.nio.IntBuffer;

/**
 * Lightweight mobile SSAO pass. The already-rendered scene is copied into a color/depth
 * texture pair using GLES 3 framebuffer blits, AO is evaluated at half resolution, then
 * multiplied back into the scene before the HUD is drawn.
 */
final class ScreenSpaceAmbientOcclusion implements Disposable {
    private static final float AO_SCALE = 0.50f;
    private static final float AO_RADIUS_METERS = 1.05f;
    private static final float AO_STRENGTH = 0.72f;

    private static final String FULLSCREEN_VERTEX =
            "attribute vec2 a_position;\n"
                    + "varying vec2 v_uv;\n"
                    + "void main() {\n"
                    + "  v_uv = a_position * 0.5 + 0.5;\n"
                    + "  gl_Position = vec4(a_position, 0.0, 1.0);\n"
                    + "}\n";

    private static final String AO_FRAGMENT =
            "#ifdef GL_ES\n"
                    + "precision highp float;\n"
                    + "#endif\n"
                    + "uniform sampler2D u_depthTex;\n"
                    + "uniform mat4 u_invProjection;\n"
                    + "uniform vec2 u_depthTexel;\n"
                    + "uniform float u_projScale;\n"
                    + "uniform float u_radius;\n"
                    + "varying vec2 v_uv;\n"
                    + "vec3 viewPosition(vec2 uv, float depth) {\n"
                    + "  vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);\n"
                    + "  vec4 view = u_invProjection * clip;\n"
                    + "  return view.xyz / max(abs(view.w), 0.00001);\n"
                    + "}\n"
                    + "vec3 positionOrCenter(vec2 uv, vec3 center) {\n"
                    + "  float d = texture2D(u_depthTex, uv).r;\n"
                    + "  return d >= 0.99998 ? center : viewPosition(uv, d);\n"
                    + "}\n"
                    + "float aoTap(vec3 p, vec3 n, vec2 offset) {\n"
                    + "  vec2 uv = v_uv + offset;\n"
                    + "  if (uv.x <= 0.001 || uv.x >= 0.999 || uv.y <= 0.001 || uv.y >= 0.999)\n"
                    + "    return 0.0;\n"
                    + "  float d = texture2D(u_depthTex, uv).r;\n"
                    + "  if (d >= 0.99998) return 0.0;\n"
                    + "  vec3 q = viewPosition(uv, d);\n"
                    + "  vec3 delta = q - p;\n"
                    + "  float distanceToSample = length(delta);\n"
                    + "  if (distanceToSample < 0.012 || distanceToSample > u_radius) return 0.0;\n"
                    + "  vec3 direction = delta / distanceToSample;\n"
                    + "  float hemisphere = max(dot(n, direction) - 0.045, 0.0);\n"
                    + "  float inFront = smoothstep(0.008, 0.070, q.z - p.z);\n"
                    + "  float range = 1.0 - smoothstep(u_radius * 0.28, u_radius, distanceToSample);\n"
                    + "  return hemisphere * inFront * range;\n"
                    + "}\n"
                    + "void main() {\n"
                    + "  float depth = texture2D(u_depthTex, v_uv).r;\n"
                    + "  if (depth >= 0.99998) { gl_FragColor = vec4(1.0); return; }\n"
                    + "  vec3 p = viewPosition(v_uv, depth);\n"
                    + "  vec3 leftP = positionOrCenter(v_uv - vec2(u_depthTexel.x, 0.0), p);\n"
                    + "  vec3 rightP = positionOrCenter(v_uv + vec2(u_depthTexel.x, 0.0), p);\n"
                    + "  vec3 downP = positionOrCenter(v_uv - vec2(0.0, u_depthTexel.y), p);\n"
                    + "  vec3 upP = positionOrCenter(v_uv + vec2(0.0, u_depthTexel.y), p);\n"
                    + "  vec3 dxA = rightP - p;\n"
                    + "  vec3 dxB = p - leftP;\n"
                    + "  vec3 dyA = upP - p;\n"
                    + "  vec3 dyB = p - downP;\n"
                    + "  vec3 dx = abs(dxA.z) < abs(dxB.z) ? dxA : dxB;\n"
                    + "  vec3 dy = abs(dyA.z) < abs(dyB.z) ? dyA : dyB;\n"
                    + "  vec3 crossN = cross(dx, dy);\n"
                    + "  float normalLength = length(crossN);\n"
                    + "  if (normalLength < 0.00001) { gl_FragColor = vec4(1.0); return; }\n"
                    + "  vec3 n = crossN / normalLength;\n"
                    + "  if (n.z < 0.0) n = -n;\n"
                    + "  float uvRadius = 0.5 * u_radius * u_projScale / max(-p.z, 0.30);\n"
                    + "  uvRadius = clamp(uvRadius, u_depthTexel.y * 2.0, 0.060);\n"
                    + "  float occ = 0.0;\n"
                    + "  occ += aoTap(p, n, vec2( 1.000,  0.000) * uvRadius * 0.34);\n"
                    + "  occ += aoTap(p, n, vec2(-1.000,  0.000) * uvRadius * 0.34);\n"
                    + "  occ += aoTap(p, n, vec2( 0.000,  1.000) * uvRadius * 0.34);\n"
                    + "  occ += aoTap(p, n, vec2( 0.000, -1.000) * uvRadius * 0.34);\n"
                    + "  occ += aoTap(p, n, vec2( 0.707,  0.707) * uvRadius * 0.58);\n"
                    + "  occ += aoTap(p, n, vec2(-0.707,  0.707) * uvRadius * 0.58);\n"
                    + "  occ += aoTap(p, n, vec2( 0.707, -0.707) * uvRadius * 0.58);\n"
                    + "  occ += aoTap(p, n, vec2(-0.707, -0.707) * uvRadius * 0.58);\n"
                    + "  occ += aoTap(p, n, vec2( 0.924,  0.383) * uvRadius * 0.92);\n"
                    + "  occ += aoTap(p, n, vec2(-0.383,  0.924) * uvRadius * 0.92);\n"
                    + "  occ += aoTap(p, n, vec2(-0.924, -0.383) * uvRadius * 0.92);\n"
                    + "  occ += aoTap(p, n, vec2( 0.383, -0.924) * uvRadius * 0.92);\n"
                    + "  float ao = 1.0 - occ * (1.34 / 12.0);\n"
                    + "  ao = clamp(ao, 0.52, 1.0);\n"
                    + "  ao = smoothstep(0.50, 1.0, ao);\n"
                    + "  gl_FragColor = vec4(vec3(ao), 1.0);\n"
                    + "}\n";

    private static final String COMPOSITE_FRAGMENT =
            "#ifdef GL_ES\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "uniform sampler2D u_sceneTex;\n"
                    + "uniform sampler2D u_aoTex;\n"
                    + "uniform float u_strength;\n"
                    + "varying vec2 v_uv;\n"
                    + "void main() {\n"
                    + "  vec3 scene = texture2D(u_sceneTex, v_uv).rgb;\n"
                    + "  float ao = texture2D(u_aoTex, v_uv).r;\n"
                    + "  float shade = mix(1.0, ao, u_strength);\n"
                    + "  gl_FragColor = vec4(scene * shade, 1.0);\n"
                    + "}\n";

    private boolean supported;
    private Mesh fullscreenQuad;
    private ShaderProgram aoProgram;
    private ShaderProgram compositeProgram;
    private final Matrix4 inverseProjection = new Matrix4();
    private final IntBuffer framebufferBinding = BufferUtils.newIntBuffer(1);

    private FrameBuffer captureBuffer;
    private FrameBuffer aoBuffer;
    private int width;
    private int height;
    private boolean failed;

    ScreenSpaceAmbientOcclusion() {
        supported = Gdx.graphics.isGL30Available();
        if (!supported) return;

        try {
            fullscreenQuad = new Mesh(true, 4, 6,
                    new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
            fullscreenQuad.setVertices(new float[] {
                    -1f, -1f,
                     1f, -1f,
                     1f,  1f,
                    -1f,  1f
            });
            fullscreenQuad.setIndices(new short[] {0, 1, 2, 0, 2, 3});

            aoProgram = new ShaderProgram(FULLSCREEN_VERTEX, AO_FRAGMENT);
            if (!aoProgram.isCompiled()) {
                throw new GdxRuntimeException(
                        "SSAO shader failed to compile: " + aoProgram.getLog());
            }
            compositeProgram = new ShaderProgram(FULLSCREEN_VERTEX, COMPOSITE_FRAGMENT);
            if (!compositeProgram.isCompiled()) {
                throw new GdxRuntimeException(
                        "SSAO composite shader failed to compile: " + compositeProgram.getLog());
            }
        } catch (RuntimeException error) {
            supported = false;
            Gdx.app.error("BalancePoint", "SSAO unavailable; using normal forward render", error);
            if (fullscreenQuad != null) { fullscreenQuad.dispose(); fullscreenQuad = null; }
            if (aoProgram != null) { aoProgram.dispose(); aoProgram = null; }
            if (compositeProgram != null) { compositeProgram.dispose(); compositeProgram = null; }
        }
    }

    void apply(PerspectiveCamera camera, boolean highQuality) {
        if (!supported || failed || !highQuality) return;

        int targetWidth = Gdx.graphics.getBackBufferWidth();
        int targetHeight = Gdx.graphics.getBackBufferHeight();
        if (targetWidth < 2 || targetHeight < 2) return;

        try {
            ensureBuffers(targetWidth, targetHeight);
            int defaultFramebuffer = currentFramebuffer();
            capture(defaultFramebuffer);
            renderAo(camera);
            composite(defaultFramebuffer);
        } catch (RuntimeException error) {
            failed = true;
            Gdx.app.error("BalancePoint", "Disabling SSAO after framebuffer/shader failure", error);
            disposeBuffers();
        }
    }

    private int currentFramebuffer() {
        framebufferBinding.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, framebufferBinding);
        return framebufferBinding.get(0);
    }

    private void ensureBuffers(int targetWidth, int targetHeight) {
        if (captureBuffer != null && targetWidth == width && targetHeight == height) return;
        disposeBuffers();
        width = targetWidth;
        height = targetHeight;

        GLFrameBuffer.FrameBufferBuilder captureBuilder =
                new GLFrameBuffer.FrameBufferBuilder(width, height);
        captureBuilder.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
        captureBuilder.addDepthTextureAttachment(GL20.GL_DEPTH_COMPONENT16, GL20.GL_UNSIGNED_SHORT);
        captureBuffer = captureBuilder.build();

        Texture sceneTexture = captureBuffer.getTextureAttachments().get(0);
        Texture depthTexture = captureBuffer.getTextureAttachments().get(1);
        sceneTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        sceneTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        depthTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        depthTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        int aoWidth = Math.max(1, Math.round(width * AO_SCALE));
        int aoHeight = Math.max(1, Math.round(height * AO_SCALE));
        aoBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, aoWidth, aoHeight, false);
        aoBuffer.getColorBufferTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        aoBuffer.getColorBufferTexture().setWrap(
                Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    private void capture(int defaultFramebuffer) {
        Gdx.gl30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, defaultFramebuffer);
        Gdx.gl30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureBuffer.getFramebufferHandle());
        Gdx.gl30.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT,
                GL20.GL_NEAREST);
        Gdx.gl30.glBindFramebuffer(GL20.GL_FRAMEBUFFER, defaultFramebuffer);
    }

    private void renderAo(PerspectiveCamera camera) {
        aoBuffer.begin();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Texture depthTexture = captureBuffer.getTextureAttachments().get(1);
        depthTexture.bind(0);
        aoProgram.bind();
        aoProgram.setUniformi("u_depthTex", 0);
        inverseProjection.set(camera.projection).inv();
        aoProgram.setUniformMatrix("u_invProjection", inverseProjection);
        aoProgram.setUniformf("u_depthTexel", 1f / width, 1f / height);
        aoProgram.setUniformf("u_projScale", camera.projection.val[Matrix4.M11]);
        aoProgram.setUniformf("u_radius", AO_RADIUS_METERS);
        fullscreenQuad.render(aoProgram, GL20.GL_TRIANGLES);
        aoBuffer.end();
    }

    private void composite(int defaultFramebuffer) {
        Gdx.gl30.glBindFramebuffer(GL20.GL_FRAMEBUFFER, defaultFramebuffer);
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        captureBuffer.getTextureAttachments().get(0).bind(0);
        aoBuffer.getColorBufferTexture().bind(1);
        compositeProgram.bind();
        compositeProgram.setUniformi("u_sceneTex", 0);
        compositeProgram.setUniformi("u_aoTex", 1);
        compositeProgram.setUniformf("u_strength", AO_STRENGTH);
        fullscreenQuad.render(compositeProgram, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
    }

    private void disposeBuffers() {
        if (captureBuffer != null) {
            captureBuffer.dispose();
            captureBuffer = null;
        }
        if (aoBuffer != null) {
            aoBuffer.dispose();
            aoBuffer = null;
        }
        width = 0;
        height = 0;
    }

    @Override
    public void dispose() {
        disposeBuffers();
        if (fullscreenQuad != null) fullscreenQuad.dispose();
        if (aoProgram != null) aoProgram.dispose();
        if (compositeProgram != null) compositeProgram.dispose();
    }
}
