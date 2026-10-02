package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Hard-part terrain contact for crash decisions.
 *
 * Wheels and suspension may hit as hard as they like without this class declaring a crash.
 * Only motorcycle body hard-points are sampled. The rear fender is intentionally omitted so a
 * rider can drag/scrape it during a wheelie; the rear underside/corners of the seat are the first
 * rearward fatal hard parts instead.
 */
final class MotorcycleBodyContact {
    interface GroundHeightSampler {
        float height(float x, float z);
    }

    private static final float CONTACT_MARGIN = 0.012f;

    // Local coordinates match GameScene's fallback/imported-bike root: rear axle is (0,0,0).
    private static final float[][] HARD_POINTS = {
            // Handlebar ends / lower edge.
            {-0.37f, 0.8875f, 1.04f},
            { 0.37f, 0.8875f, 1.04f},
            // Rear underside/corners of the seat. Do not move these onto the rear fender.
            {-0.20f, 0.615f, 0.09f},
            { 0.20f, 0.615f, 0.09f}
    };

    private final Matrix4 transform = new Matrix4();
    private final Vector3 point = new Vector3();

    boolean hitsGround(float rootX,
                       float rootY,
                       float rootZ,
                       float yaw,
                       float pitch,
                       float roll,
                       GroundHeightSampler ground) {
        transform.idt()
                .translate(rootX, rootY, rootZ)
                .rotate(Vector3.Y, yaw * MathUtils.radiansToDegrees)
                .rotate(Vector3.Z, roll * MathUtils.radiansToDegrees)
                .rotate(Vector3.X, -pitch * MathUtils.radiansToDegrees);

        for (float[] hardPoint : HARD_POINTS) {
            point.set(hardPoint[0], hardPoint[1], hardPoint[2]).mul(transform);
            float terrainY = ground.height(point.x, point.z);
            if (point.y <= terrainY + CONTACT_MARGIN) return true;
        }
        return false;
    }
}
