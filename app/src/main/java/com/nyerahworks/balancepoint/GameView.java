package com.nyerahworks.balancepoint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Lightweight single-view game loop for maximum compatibility on the GMEE Connect Pro.
 *
 * v0.1 originally used SurfaceView + a dedicated render thread. Some low-end/custom Android
 * builds are touchy about Surface lifecycle changes during launch, especially when forcing
 * landscape/fullscreen. This version intentionally renders on the UI thread at ~30 FPS instead.
 * The scene is tiny, so this is both cheaper and substantially harder to crash.
 */
final class GameView extends View {
    private static final float PHYSICS_DT = 1f / 120f;
    private static final long FRAME_DELAY_MS = 33L;

    private final BikePhysics bike = new BikePhysics();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    private boolean running;
    private boolean throttlePressed;
    private boolean brakePressed;

    private long startNs;
    private long previousNs;
    private double accumulator;

    private int viewWidth;
    private int viewHeight;
    private float pixelsPerMeter = 1f;
    private float groundScreenY;
    private float cameraX;

    GameView(Context context) {
        super(context);
        setFocusable(true);
        setKeepScreenOn(true);
        setBackgroundColor(Color.BLACK);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void resumeGame() {
        running = true;
        long now = System.nanoTime();
        previousNs = now;
        if (startNs == 0L) startNs = now;
        invalidate();
    }

    void pauseGame() {
        running = false;
        throttlePressed = false;
        brakePressed = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = Math.max(1, w);
        viewHeight = Math.max(1, h);
        pixelsPerMeter = Math.max(1f, viewWidth / 10.8f);
        groundScreenY = viewHeight * 0.73f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewWidth <= 1 || viewHeight <= 1) {
            if (running) postInvalidateDelayed(FRAME_DELAY_MS);
            return;
        }

        long now = System.nanoTime();
        if (previousNs == 0L) previousNs = now;
        float elapsed = Math.min(0.050f, Math.max(0f, (now - previousNs) / 1_000_000_000f));
        previousNs = now;

        if (running) {
            accumulator += elapsed;
            bike.setInput(throttlePressed ? 1f : 0f, brakePressed ? 1f : 0f);

            int steps = 0;
            while (accumulator >= PHYSICS_DT && steps < 8) {
                bike.step(PHYSICS_DT);
                accumulator -= PHYSICS_DT;
                steps++;
            }
            if (steps == 8) accumulator = 0.0;

            updateCamera(elapsed);
        }

        drawWorld(canvas);

        if (running) postInvalidateDelayed(FRAME_DELAY_MS);
    }

    private void updateCamera(float dt) {
        float target = bike.getX() + Math.max(0f, bike.getVx()) * 0.12f;
        float response = 1f - (float) Math.exp(-5.5f * dt);
        cameraX += (target - cameraX) * response;
    }

    private void drawWorld(Canvas c) {
        c.drawColor(Color.rgb(193, 218, 226));
        drawSun(c);
        drawDistantHills(c);
        drawIndustrialBackground(c);
        drawRoad(c);
        drawBikeShadow(c);
        drawBike(c);
        drawHud(c);
        drawControls(c);

        if (bike.isCrashed()) drawCrashOverlay(c);
        long aliveMs = startNs == 0L ? 0L : (System.nanoTime() - startNs) / 1_000_000L;
        if (aliveMs < 4200L) drawIntro(c, aliveMs);
    }

    private void drawSun(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(236, 229, 197));
        c.drawCircle(viewWidth * 0.80f, viewHeight * 0.18f, viewHeight * 0.075f, paint);
    }

    private void drawDistantHills(Canvas c) {
        float offset = wrap(cameraX * pixelsPerMeter * 0.08f, viewWidth * 1.4f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(151, 180, 178));
        path.reset();
        path.moveTo(-viewWidth * 0.5f - offset, groundScreenY);
        for (int i = -2; i <= 5; i++) {
            float bx = i * viewWidth * 0.42f - offset;
            path.lineTo(bx, groundScreenY);
            path.lineTo(bx + viewWidth * 0.18f, viewHeight * 0.38f);
            path.lineTo(bx + viewWidth * 0.42f, groundScreenY);
        }
        path.lineTo(viewWidth * 2f, groundScreenY);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawIndustrialBackground(Canvas c) {
        float parallax = cameraX * pixelsPerMeter * 0.22f;
        float blockW = viewWidth * 0.15f;
        float spacing = viewWidth * 0.25f;
        float cycle = spacing * 8f;
        float base = -wrap(parallax, cycle) - spacing;

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            float x = base + i * spacing;
            float h = viewHeight * (0.10f + (i % 3) * 0.035f);
            paint.setColor(i % 2 == 0 ? Color.rgb(121, 139, 140) : Color.rgb(110, 128, 131));
            c.drawRect(x, groundScreenY - h, x + blockW, groundScreenY, paint);

            if (i % 4 == 1) {
                paint.setColor(Color.rgb(95, 112, 114));
                c.drawRect(x + blockW * 0.70f, groundScreenY - h - viewHeight * 0.10f,
                        x + blockW * 0.79f, groundScreenY, paint);
            }
        }
    }

    private void drawRoad(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(70, 72, 73));
        c.drawRect(0, groundScreenY, viewWidth, viewHeight, paint);

        paint.setColor(Color.rgb(94, 95, 95));
        c.drawRect(0, groundScreenY, viewWidth,
                groundScreenY + Math.max(2f, viewHeight * 0.012f), paint);

        float stripeY = groundScreenY + viewHeight * 0.15f;
        float stripeW = viewWidth * 0.085f;
        float gap = viewWidth * 0.075f;
        float cycle = stripeW + gap;
        float roadOffset = wrap(cameraX * pixelsPerMeter, cycle);
        paint.setColor(Color.rgb(221, 214, 180));
        for (float x = -roadOffset - stripeW; x < viewWidth + stripeW; x += cycle) {
            c.drawRect(x, stripeY, x + stripeW, stripeY + viewHeight * 0.012f, paint);
        }
    }

    private void drawBikeShadow(Canvas c) {
        float rearX = sx(bike.getRearWheelX());
        float frontX = sx(bike.getFrontWheelX());
        float left = Math.min(rearX, frontX) - pixelsPerMeter * 0.25f;
        float right = Math.max(rearX, frontX) + pixelsPerMeter * 0.25f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(56, 0, 0, 0));
        oval.set(left, groundScreenY - viewHeight * 0.012f,
                right, groundScreenY + viewHeight * 0.018f);
        c.drawOval(oval, paint);
    }

    private void drawBike(Canvas c) {
        float rearX = sx(bike.getRearWheelX());
        float rearY = sy(bike.getRearWheelY());
        float frontX = sx(bike.getFrontWheelX());
        float frontY = sy(bike.getFrontWheelY());
        float wheelR = BikePhysics.WHEEL_RADIUS * pixelsPerMeter;

        drawWheel(c, rearX, rearY, wheelR, bike.getRearWheelAngle());
        drawWheel(c, frontX, frontY, wheelR, bike.getFrontWheelAngle());

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(5f, viewHeight * 0.010f));
        paint.setColor(Color.rgb(35, 38, 39));
        lineLocal(c, BikePhysics.REAR_LOCAL_X, BikePhysics.WHEEL_LOCAL_Y, -0.12f, -0.12f);
        lineLocal(c, -0.12f, -0.12f, 0.43f, 0.22f);
        lineLocal(c, 0.43f, 0.22f, BikePhysics.FRONT_LOCAL_X, BikePhysics.WHEEL_LOCAL_Y);
        lineLocal(c, -0.12f, -0.12f, 0.22f, 0.35f);
        lineLocal(c, 0.22f, 0.35f, 0.43f, 0.22f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(214, 102, 58));
        path.reset();
        moveLocal(path, -0.43f, 0.10f);
        lineLocal(path, -0.22f, 0.32f);
        lineLocal(path, 0.20f, 0.37f);
        lineLocal(path, 0.40f, 0.22f);
        lineLocal(path, 0.18f, 0.09f);
        lineLocal(path, -0.23f, 0.06f);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(Color.rgb(44, 46, 46));
        paint.setStrokeWidth(Math.max(5f, viewHeight * 0.010f));
        paint.setStyle(Paint.Style.STROKE);
        lineLocal(c, -0.48f, 0.20f, -0.12f, 0.22f);
        lineLocal(c, 0.37f, 0.34f, 0.58f, 0.40f);

        drawRider(c);
    }

    private void drawWheel(Canvas c, float cx, float cy, float radius, float wheelAngle) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(6f, viewHeight * 0.012f));
        paint.setColor(Color.rgb(28, 30, 31));
        c.drawCircle(cx, cy, radius, paint);

        paint.setStrokeWidth(Math.max(2f, viewHeight * 0.004f));
        paint.setColor(Color.rgb(172, 177, 176));
        c.drawCircle(cx, cy, radius * 0.67f, paint);
        for (int i = 0; i < 6; i++) {
            float a = wheelAngle + i * ((float) Math.PI / 3f);
            c.drawLine(cx, cy,
                    cx + (float) Math.cos(a) * radius * 0.62f,
                    cy + (float) Math.sin(a) * radius * 0.62f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(40, 42, 43));
        c.drawCircle(cx, cy, radius * 0.10f, paint);
    }

    private void drawRider(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(7f, viewHeight * 0.012f));
        paint.setColor(Color.rgb(33, 36, 38));

        lineLocal(c, -0.22f, 0.27f, -0.34f, 0.67f);
        lineLocal(c, -0.31f, 0.58f, 0.49f, 0.40f);
        lineLocal(c, -0.21f, 0.28f, 0.02f, -0.05f);
        lineLocal(c, 0.02f, -0.05f, 0.18f, -0.13f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(47, 51, 53));
        c.drawCircle(sx(bike.worldX(-0.35f, 0.79f)),
                sy(bike.worldY(-0.35f, 0.79f)), pixelsPerMeter * 0.12f, paint);

        paint.setColor(Color.rgb(162, 192, 197));
        path.reset();
        moveLocal(path, -0.27f, 0.84f);
        lineLocal(path, -0.14f, 0.80f);
        lineLocal(path, -0.27f, 0.75f);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawHud(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(142, 15, 17, 18));
        float panelW = viewWidth * 0.34f;
        float panelH = viewHeight * 0.17f;
        oval.set(viewWidth * 0.025f, viewHeight * 0.035f,
                viewWidth * 0.025f + panelW, viewHeight * 0.035f + panelH);
        c.drawRoundRect(oval, viewHeight * 0.025f, viewHeight * 0.025f, paint);

        float mph = Math.max(0f, bike.getVx() * 2.23694f);
        float deg = (float) Math.toDegrees(bike.getAngle());

        paint.setColor(Color.WHITE);
        paint.setTextSize(viewHeight * 0.045f);
        paint.setFakeBoldText(true);
        c.drawText(String.format(Locale.US, "%02.0f MPH", mph),
                viewWidth * 0.045f, viewHeight * 0.095f, paint);

        paint.setTextSize(viewHeight * 0.028f);
        paint.setFakeBoldText(false);
        c.drawText(String.format(Locale.US, "ANGLE  %+.0f°", deg),
                viewWidth * 0.045f, viewHeight * 0.138f, paint);
        c.drawText(String.format(Locale.US, "WHEELIE  %.1fs   BEST  %.1fs",
                        bike.getWheelieTime(), bike.getBestWheelieTime()),
                viewWidth * 0.045f, viewHeight * 0.178f, paint);
    }

    private void drawControls(Canvas c) {
        float r = Math.min(viewWidth, viewHeight) * 0.105f;
        float cy = viewHeight - r * 1.18f;
        float brakeX = r * 1.32f;
        float throttleX = viewWidth - r * 1.32f;

        drawControl(c, brakeX, cy, r, brakePressed, "BRAKE");
        drawControl(c, throttleX, cy, r, throttlePressed, "THROTTLE");
    }

    private void drawControl(Canvas c, float cx, float cy, float r, boolean pressed, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.argb(175, 238, 238, 238) : Color.argb(76, 255, 255, 255));
        c.drawCircle(cx, cy, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, viewHeight * 0.004f));
        paint.setColor(Color.argb(160, 255, 255, 255));
        c.drawCircle(cx, cy, r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.rgb(25, 27, 28) : Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(viewHeight * 0.025f);
        paint.setFakeBoldText(true);
        c.drawText(label, cx, cy + viewHeight * 0.010f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
    }

    private void drawCrashOverlay(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(110, 0, 0, 0));
        c.drawRect(0, 0, viewWidth, viewHeight, paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(viewHeight * 0.10f);
        c.drawText("LOOPED IT", viewWidth * 0.5f, viewHeight * 0.47f, paint);
        paint.setTextSize(viewHeight * 0.030f);
        c.drawText("resetting...", viewWidth * 0.5f, viewHeight * 0.53f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
    }

    private void drawIntro(Canvas c, long aliveMs) {
        float fade = 1f;
        if (aliveMs > 3200L) fade = Math.max(0f, 1f - (aliveMs - 3200L) / 1000f);
        int alpha = (int) (190 * fade);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 10, 12, 13));
        oval.set(viewWidth * 0.31f, viewHeight * 0.055f,
                viewWidth * 0.69f, viewHeight * 0.19f);
        c.drawRoundRect(oval, viewHeight * 0.025f, viewHeight * 0.025f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb((int) (255 * fade), 255, 255, 255));
        paint.setFakeBoldText(true);
        paint.setTextSize(viewHeight * 0.043f);
        c.drawText("FIND THE BALANCE POINT", viewWidth * 0.5f, viewHeight * 0.112f, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(viewHeight * 0.026f);
        c.drawText("Throttle it up. Catch it with the rear brake.",
                viewWidth * 0.5f, viewHeight * 0.158f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            throttlePressed = false;
            brakePressed = false;
            invalidate();
            return true;
        }

        boolean newThrottle = false;
        boolean newBrake = false;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            float x = event.getX(i);
            float y = event.getY(i);
            if (y < viewHeight * 0.45f) continue;
            if (x >= viewWidth * 0.56f) newThrottle = true;
            if (x <= viewWidth * 0.44f) newBrake = true;
        }
        throttlePressed = newThrottle;
        brakePressed = newBrake;
        return true;
    }

    private void lineLocal(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(sx(bike.worldX(x1, y1)), sy(bike.worldY(x1, y1)),
                sx(bike.worldX(x2, y2)), sy(bike.worldY(x2, y2)), paint);
    }

    private void moveLocal(Path p, float x, float y) {
        p.moveTo(sx(bike.worldX(x, y)), sy(bike.worldY(x, y)));
    }

    private void lineLocal(Path p, float x, float y) {
        p.lineTo(sx(bike.worldX(x, y)), sy(bike.worldY(x, y)));
    }

    private float sx(float worldX) {
        return viewWidth * 0.36f + (worldX - cameraX) * pixelsPerMeter;
    }

    private float sy(float worldY) {
        return groundScreenY - worldY * pixelsPerMeter;
    }

    private static float wrap(float value, float period) {
        if (period <= 0f) return 0f;
        float r = value % period;
        return r < 0f ? r + period : r;
    }
}
