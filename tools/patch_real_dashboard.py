from pathlib import Path
import re

path = Path("core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java")
s = path.read_text()


def replace_once(old, new, label):
    global s
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    s = s.replace(old, new, 1)


replace_once(
    "import com.badlogic.gdx.graphics.PerspectiveCamera;\nimport com.badlogic.gdx.graphics.VertexAttributes;",
    "import com.badlogic.gdx.graphics.PerspectiveCamera;\nimport com.badlogic.gdx.graphics.Pixmap;\nimport com.badlogic.gdx.graphics.Texture;\nimport com.badlogic.gdx.graphics.VertexAttributes;",
    "graphics imports",
)
replace_once(
    "import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;",
    "import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;\nimport com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;",
    "texture attribute import",
)
replace_once(
    "    private BitmapFont font;\n    private Environment environment;",
    "    private BitmapFont font;\n    private Pixmap instrumentPixmap;\n    private Texture instrumentTexture;\n    private Environment environment;",
    "dashboard texture fields",
)
replace_once(
    "    private ModelInstance frontNumberPlate;\n    private ModelInstance instrumentPanel;",
    "    private ModelInstance frontNumberPlate;\n    private ModelInstance instrumentPanel;\n    private ModelInstance instrumentScreen;\n    private ModelInstance instrumentButtonLeft;\n    private ModelInstance instrumentButtonRight;",
    "dashboard model fields",
)

replace_once(
    "        Material dashMat = material(0.025f, 0.030f, 0.032f);",
    """        Material dashMat = material(0.030f, 0.034f, 0.035f);\n        Material dashButtonMat = material(0.070f, 0.075f, 0.074f);\n\n        instrumentPixmap = new Pixmap(192, 96, Pixmap.Format.RGBA8888);\n        instrumentPixmap.setColor(0.008f, 0.014f, 0.013f, 1f);\n        instrumentPixmap.fill();\n        instrumentTexture = new Texture(instrumentPixmap);\n        instrumentTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);\n        TextureAttribute dashTextureAttribute = TextureAttribute.createDiffuse(instrumentTexture);\n        // Framebuffer-style UV orientation is wrong for a bike-mounted plane unless V is flipped.\n        dashTextureAttribute.offsetV = 1f;\n        dashTextureAttribute.scaleV = -1f;\n        Material dashScreenMat = new Material(dashTextureAttribute,\n                ColorAttribute.createDiffuse(Color.WHITE));""",
    "dashboard materials",
)

replace_once(
    "        Model dashM = box(b, 0.30f, 0.035f, 0.16f, dashMat);",
    """        // Compact trail-computer-sized housing instead of the oversized prototype box.\n        Model dashM = box(b, 0.19f, 0.025f, 0.105f, dashMat);\n        Model dashButtonM = box(b, 0.020f, 0.007f, 0.014f, dashButtonMat);\n        Model dashScreenM = b.createRect(\n                -0.078f, 0f, -0.030f,\n                -0.078f, 0f,  0.038f,\n                 0.078f, 0f,  0.038f,\n                 0.078f, 0f, -0.030f,\n                 0f, 1f, 0f, dashScreenMat,\n                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal\n                        | VertexAttributes.Usage.TextureCoordinates);\n        ownedModels.add(dashScreenM);""",
    "dashboard geometry",
)

replace_once(
    "        frontNumberPlate = new ModelInstance(plateM);\n        instrumentPanel = new ModelInstance(dashM);",
    """        frontNumberPlate = new ModelInstance(plateM);\n        instrumentPanel = new ModelInstance(dashM);\n        instrumentScreen = new ModelInstance(dashScreenM);\n        instrumentButtonLeft = new ModelInstance(dashButtonM);\n        instrumentButtonRight = new ModelInstance(dashButtonM);""",
    "dashboard instances",
)

replace_once(
    "        updateWorldInstances();\n        updateBikeInstances();\n        updateCamera(frameDt);",
    "        updateWorldInstances();\n        updateBikeInstances();\n        updateInstrumentTexture();\n        updateCamera(frameDt);",
    "dashboard texture update",
)

replace_once(
    "        setPart(instrumentPanel, 0f, 0.90f, 0.98f);\n        instrumentPanel.transform.rotate(Vector3.X, -24f);",
    """        // The display is actual bike geometry. Everything below inherits the same local\n        // transform, so camera movement changes perspective instead of sliding a 2D overlay.\n        setPart(instrumentPanel, 0f, 0.915f, 0.985f);\n        instrumentPanel.transform.rotate(Vector3.X, -22f);\n        instrumentScreen.transform.set(instrumentPanel.transform)\n                .translate(0f, 0.0132f, 0.006f);\n        instrumentButtonLeft.transform.set(instrumentPanel.transform)\n                .translate(-0.055f, 0.0162f, -0.041f);\n        instrumentButtonRight.transform.set(instrumentPanel.transform)\n                .translate(0.055f, 0.0162f, -0.041f);""",
    "dashboard transforms",
)

replace_once(
    "            modelBatch.render(instrumentPanel, environment);\n            return;",
    """            modelBatch.render(instrumentPanel, environment);\n            modelBatch.render(instrumentScreen, environment);\n            modelBatch.render(instrumentButtonLeft, environment);\n            modelBatch.render(instrumentButtonRight, environment);\n            return;""",
    "imported bike dashboard render",
)
replace_once(
    "        modelBatch.render(instrumentPanel, environment);\n        modelBatch.render(riderTorso, environment);",
    """        modelBatch.render(instrumentPanel, environment);\n        modelBatch.render(instrumentScreen, environment);\n        modelBatch.render(instrumentButtonLeft, environment);\n        modelBatch.render(instrumentButtonRight, environment);\n        modelBatch.render(riderTorso, environment);""",
    "fallback dashboard render",
)

replace_once(
    "        drawTouchControls(w, h, min);\n        drawBikeGauge(w, h);",
    "        drawTouchControls(w, h, min);",
    "remove projected gauge call",
)

# Remove the old projected 2D gauge entirely. It was visually anchored by camera.project(), not
# actually drawn on the bike, which is why it slid around as the camera moved.
s, removed = re.subn(
    r"\n    private void drawBikeGauge\(int w, int h\) \{.*?\n    \}\n\n    @Override\n    public void resize",
    "\n\n    @Override\n    public void resize",
    s,
    count=1,
    flags=re.S,
)
if removed != 1:
    raise RuntimeError(f"remove projected gauge: expected 1 method, found {removed}")

# Insert the real LCD renderer before resize(). The texture is uploaded onto the screen mesh and
# therefore obeys the bike's model transform, depth buffer and camera perspective.
marker = "\n    @Override\n    public void resize(int width, int height) {"
if s.count(marker) != 1:
    raise RuntimeError("resize marker not unique")
helpers = r'''

    private void updateInstrumentTexture() {
        if (instrumentPixmap == null || instrumentTexture == null) return;

        final int width = instrumentPixmap.getWidth();
        final int height = instrumentPixmap.getHeight();
        final float rpmNorm = MathUtils.clamp(drivetrain.getRpm()
                / drivetrain.getRedlineRpm(), 0f, 1f);

        // Deep green-black LCD glass with a subtle inner frame and top sheen.
        instrumentPixmap.setColor(0.007f, 0.014f, 0.012f, 1f);
        instrumentPixmap.fill();
        instrumentPixmap.setColor(0.07f, 0.12f, 0.10f, 1f);
        instrumentPixmap.drawRectangle(2, 2, width - 5, height - 5);
        instrumentPixmap.setColor(0.035f, 0.070f, 0.060f, 1f);
        instrumentPixmap.fillRectangle(4, 4, width - 8, 3);

        // Segmented tach across the top. The last two segments become a warm shift zone.
        final int rpmSegments = 14;
        final int activeSegments = MathUtils.clamp(Math.round(rpmNorm * rpmSegments), 0,
                rpmSegments);
        final int barX = 9;
        final int barY = 11;
        final int barGap = 2;
        final int barWidth = 10;
        for (int i = 0; i < rpmSegments; i++) {
            boolean active = i < activeSegments;
            if (!active) {
                instrumentPixmap.setColor(0.045f, 0.095f, 0.078f, 1f);
            } else if (i >= rpmSegments - 2) {
                instrumentPixmap.setColor(0.98f, 0.28f, 0.08f, 1f);
            } else if (i >= rpmSegments - 4) {
                instrumentPixmap.setColor(0.98f, 0.70f, 0.10f, 1f);
            } else {
                instrumentPixmap.setColor(0.30f, 0.98f, 0.68f, 1f);
            }
            instrumentPixmap.fillRectangle(barX + i * (barWidth + barGap), barY,
                    barWidth, 6);
        }

        // A small divider makes the right-side gear readout feel like a dedicated instrument cell.
        instrumentPixmap.setColor(0.055f, 0.115f, 0.095f, 1f);
        instrumentPixmap.drawLine(142, 25, 142, 86);

        int mph = MathUtils.clamp(Math.round(speed * 2.23694f), 0, 199);
        drawSevenSegmentNumber(mph, 10, 29, 31, 45, 5, 3);
        drawSevenSegmentDigit(MathUtils.clamp(drivetrain.getGear(), 0, 9),
                153, 29, 28, 45, 5);

        drawMiniText("MPH", 11, 80, 2);
        drawMiniText("G", 162, 80, 2);

        // Shift indicator dot. Unlike the tach bar, this only lights very near redline.
        if (rpmNorm > 0.92f) {
            instrumentPixmap.setColor(1f, 0.24f, 0.08f, 1f);
            instrumentPixmap.fillCircle(181, 17, 4);
        } else {
            instrumentPixmap.setColor(0.09f, 0.035f, 0.025f, 1f);
            instrumentPixmap.fillCircle(181, 17, 3);
        }

        instrumentTexture.draw(instrumentPixmap, 0, 0);
    }

    private void drawSevenSegmentNumber(int value, int x, int y, int digitWidth,
                                        int digitHeight, int thickness, int gap) {
        String text = Integer.toString(Math.max(0, value));
        int slots = 3;
        int startX = x + (slots - text.length()) * (digitWidth + gap);
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            drawSevenSegmentDigit(digit, startX + i * (digitWidth + gap), y,
                    digitWidth, digitHeight, thickness);
        }
    }

    private void drawSevenSegmentDigit(int digit, int x, int y, int width, int height,
                                       int thickness) {
        int mask;
        switch (digit) {
            case 0: mask = 0x3F; break;
            case 1: mask = 0x06; break;
            case 2: mask = 0x5B; break;
            case 3: mask = 0x4F; break;
            case 4: mask = 0x66; break;
            case 5: mask = 0x6D; break;
            case 6: mask = 0x7D; break;
            case 7: mask = 0x07; break;
            case 8: mask = 0x7F; break;
            case 9: mask = 0x6F; break;
            default: mask = 0; break;
        }

        instrumentPixmap.setColor(0.032f, 0.078f, 0.064f, 1f);
        drawSevenSegmentMask(0x7F, x, y, width, height, thickness);
        instrumentPixmap.setColor(0.55f, 1.00f, 0.78f, 1f);
        drawSevenSegmentMask(mask, x, y, width, height, thickness);
    }

    private void drawSevenSegmentMask(int mask, int x, int y, int width, int height,
                                      int thickness) {
        int half = height / 2;
        if ((mask & 0x01) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y,
                    width - thickness * 2, thickness); // A
        if ((mask & 0x02) != 0)
            instrumentPixmap.fillRectangle(x + width - thickness, y + thickness,
                    thickness, half - thickness); // B
        if ((mask & 0x04) != 0)
            instrumentPixmap.fillRectangle(x + width - thickness, y + half,
                    thickness, half - thickness); // C
        if ((mask & 0x08) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y + height - thickness,
                    width - thickness * 2, thickness); // D
        if ((mask & 0x10) != 0)
            instrumentPixmap.fillRectangle(x, y + half,
                    thickness, half - thickness); // E
        if ((mask & 0x20) != 0)
            instrumentPixmap.fillRectangle(x, y + thickness,
                    thickness, half - thickness); // F
        if ((mask & 0x40) != 0)
            instrumentPixmap.fillRectangle(x + thickness, y + half - thickness / 2,
                    width - thickness * 2, thickness); // G
    }

    private void drawMiniText(String text, int x, int y, int scale) {
        int cursor = x;
        instrumentPixmap.setColor(0.32f, 0.58f, 0.48f, 1f);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int[] rows = miniGlyph(c);
            if (rows == null) {
                cursor += 4 * scale;
                continue;
            }
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < 5; col++) {
                    if ((rows[row] & (1 << (4 - col))) != 0) {
                        instrumentPixmap.fillRectangle(cursor + col * scale,
                                y + row * scale, scale, scale);
                    }
                }
            }
            cursor += 6 * scale;
        }
    }

    private static int[] miniGlyph(char c) {
        switch (c) {
            case 'M': return new int[] {0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'P': return new int[] {0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'H': return new int[] {0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'G': return new int[] {0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0E};
            default: return null;
        }
    }
'''
s = s.replace(marker, helpers + marker, 1)

replace_once(
    "        if (font != null) font.dispose();\n        for (Model m : ownedModels) m.dispose();",
    """        if (font != null) font.dispose();\n        if (instrumentTexture != null) instrumentTexture.dispose();\n        if (instrumentPixmap != null) instrumentPixmap.dispose();\n        for (Model m : ownedModels) m.dispose();""",
    "dashboard disposal",
)

path.write_text(s)
print("Patched real bike-mounted dashboard")
