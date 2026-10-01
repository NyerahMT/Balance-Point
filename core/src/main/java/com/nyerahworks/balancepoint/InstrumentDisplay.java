package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

/** Renders the bike's world-space LCD texture from simulation telemetry. */
final class InstrumentDisplay {
    private static final int WIDTH = 192;
    private static final int HEIGHT = 96;

    private final Pixmap pixmap;
    private final Texture texture;

    InstrumentDisplay() {
        pixmap = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.008f, 0.014f, 0.013f, 1f);
        pixmap.fill();
        texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    Texture texture() {
        return texture;
    }

    void update(float speed, MotorcycleDrivetrain drivetrain) {
        float rpmNorm = MathUtils.clamp(
                drivetrain.getRpm() / drivetrain.getRedlineRpm(), 0f, 1f);

        pixmap.setColor(0.007f, 0.014f, 0.012f, 1f);
        pixmap.fill();
        pixmap.setColor(0.07f, 0.12f, 0.10f, 1f);
        pixmap.drawRectangle(2, 2, WIDTH - 5, HEIGHT - 5);
        pixmap.setColor(0.035f, 0.070f, 0.060f, 1f);
        pixmap.fillRectangle(4, 4, WIDTH - 8, 3);

        final int rpmSegments = 14;
        final int activeSegments = MathUtils.clamp(
                Math.round(rpmNorm * rpmSegments), 0, rpmSegments);
        final int barX = 9;
        final int barY = 11;
        final int barGap = 2;
        final int barWidth = 10;
        for (int i = 0; i < rpmSegments; i++) {
            boolean active = i < activeSegments;
            if (!active) {
                pixmap.setColor(0.045f, 0.095f, 0.078f, 1f);
            } else if (i >= rpmSegments - 2) {
                pixmap.setColor(0.98f, 0.28f, 0.08f, 1f);
            } else if (i >= rpmSegments - 4) {
                pixmap.setColor(0.98f, 0.70f, 0.10f, 1f);
            } else {
                pixmap.setColor(0.30f, 0.98f, 0.68f, 1f);
            }
            pixmap.fillRectangle(barX + i * (barWidth + barGap), barY, barWidth, 6);
        }

        pixmap.setColor(0.055f, 0.115f, 0.095f, 1f);
        pixmap.drawLine(142, 25, 142, 86);

        int mph = MathUtils.clamp(Math.round(speed * 2.23694f), 0, 199);
        drawSevenSegmentNumber(mph, 10, 29, 31, 45, 5, 3);
        drawSevenSegmentDigit(
                MathUtils.clamp(drivetrain.getGear(), 0, 9), 153, 29, 28, 45, 5);
        drawMiniText("MPH", 11, 80, 2);
        drawMiniText("G", 162, 80, 2);

        if (rpmNorm > 0.92f) {
            pixmap.setColor(1f, 0.24f, 0.08f, 1f);
            pixmap.fillCircle(181, 17, 4);
        } else {
            pixmap.setColor(0.09f, 0.035f, 0.025f, 1f);
            pixmap.fillCircle(181, 17, 3);
        }

        texture.draw(pixmap, 0, 0);
    }

    private void drawSevenSegmentNumber(
            int value,
            int x,
            int y,
            int digitWidth,
            int digitHeight,
            int thickness,
            int gap) {
        String text = Integer.toString(Math.max(0, value));
        int slots = 3;
        int startX = x + (slots - text.length()) * (digitWidth + gap);
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            drawSevenSegmentDigit(
                    digit,
                    startX + i * (digitWidth + gap),
                    y,
                    digitWidth,
                    digitHeight,
                    thickness);
        }
    }

    private void drawSevenSegmentDigit(
            int digit, int x, int y, int width, int height, int thickness) {
        int mask;
        switch (digit) {
            case 0:
                mask = 0x3F;
                break;
            case 1:
                mask = 0x06;
                break;
            case 2:
                mask = 0x5B;
                break;
            case 3:
                mask = 0x4F;
                break;
            case 4:
                mask = 0x66;
                break;
            case 5:
                mask = 0x6D;
                break;
            case 6:
                mask = 0x7D;
                break;
            case 7:
                mask = 0x07;
                break;
            case 8:
                mask = 0x7F;
                break;
            case 9:
                mask = 0x6F;
                break;
            default:
                mask = 0;
                break;
        }

        pixmap.setColor(0.032f, 0.078f, 0.064f, 1f);
        drawSevenSegmentMask(0x7F, x, y, width, height, thickness);
        pixmap.setColor(0.55f, 1.00f, 0.78f, 1f);
        drawSevenSegmentMask(mask, x, y, width, height, thickness);
    }

    private void drawSevenSegmentMask(
            int mask, int x, int y, int width, int height, int thickness) {
        int half = height / 2;
        if ((mask & 0x01) != 0) {
            pixmap.fillRectangle(x + thickness, y, width - thickness * 2, thickness);
        }
        if ((mask & 0x02) != 0) {
            pixmap.fillRectangle(
                    x + width - thickness, y + thickness, thickness, half - thickness);
        }
        if ((mask & 0x04) != 0) {
            pixmap.fillRectangle(x + width - thickness, y + half, thickness, half - thickness);
        }
        if ((mask & 0x08) != 0) {
            pixmap.fillRectangle(
                    x + thickness,
                    y + height - thickness,
                    width - thickness * 2,
                    thickness);
        }
        if ((mask & 0x10) != 0) {
            pixmap.fillRectangle(x, y + half, thickness, half - thickness);
        }
        if ((mask & 0x20) != 0) {
            pixmap.fillRectangle(x, y + thickness, thickness, half - thickness);
        }
        if ((mask & 0x40) != 0) {
            pixmap.fillRectangle(
                    x + thickness,
                    y + half - thickness / 2,
                    width - thickness * 2,
                    thickness);
        }
    }

    private void drawMiniText(String text, int x, int y, int scale) {
        int cursor = x;
        pixmap.setColor(0.32f, 0.58f, 0.48f, 1f);
        for (int i = 0; i < text.length(); i++) {
            int[] rows = miniGlyph(text.charAt(i));
            if (rows == null) {
                cursor += 4 * scale;
                continue;
            }
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < 5; col++) {
                    if ((rows[row] & (1 << (4 - col))) != 0) {
                        pixmap.fillRectangle(
                                cursor + col * scale, y + row * scale, scale, scale);
                    }
                }
            }
            cursor += 6 * scale;
        }
    }

    private static int[] miniGlyph(char c) {
        switch (c) {
            case 'M':
                return new int[] {0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'P':
                return new int[] {0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'H':
                return new int[] {0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'G':
                return new int[] {0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0E};
            default:
                return null;
        }
    }

    void dispose() {
        texture.dispose();
        pixmap.dispose();
    }
}
