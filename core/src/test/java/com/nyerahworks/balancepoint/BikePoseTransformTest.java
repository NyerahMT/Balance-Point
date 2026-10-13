package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import org.junit.Test;

public final class BikePoseTransformTest {
    private final Matrix4 root = new Matrix4();
    private final Vector3 offset = new Vector3();

    @Test
    public void positiveYawRotatesVisibleForwardTowardPositiveX() {
        Quaternion q = new Quaternion().setFromAxis(Vector3.Y, 30f);
        BikePoseTransform.setFromPhysics(root, offset,
                2f, 1f, 4f, 0.72f, 0.36f, q);
        Vector3 visibleForward = new Vector3(0f, 0f, 1f).rot(root);
        assertEquals(0.5f, visibleForward.x, 0.0001f);
        assertEquals((float)Math.cos(Math.toRadians(30)), visibleForward.z, 0.0001f);
        assertEquals(2f + offset.x, root.val[Matrix4.M03], 0.0001f);
        assertEquals(4f + offset.z, root.val[Matrix4.M23], 0.0001f);
        assertTrue("rear axle should remain behind the simulated body",
                offset.z < 0f);
    }

    @Test
    public void realQuaternionPreservesCombinedPitchRollYaw() {
        Matrix4 attitude = new Matrix4().idt()
                .rotate(Vector3.Y, -28f)
                .rotate(Vector3.Z, 24f)
                .rotate(Vector3.X, -17f);
        Quaternion q = new Quaternion().setFromMatrix(attitude);
        BikePoseTransform.setFromPhysics(root, offset,
                3f, 0.9f, 8f, 0.72f, 0.36f, q);
        Vector3 simulatedForward = new Vector3(0f, 0f, 1f).mul(q);
        Vector3 renderedForward = new Vector3(0f, 0f, 1f).rot(root);
        assertEquals(simulatedForward.x, renderedForward.x, 0.0001f);
        assertEquals(simulatedForward.y, renderedForward.y, 0.0001f);
        assertEquals(simulatedForward.z, renderedForward.z, 0.0001f);
        assertEquals(0.9f + offset.y, root.val[Matrix4.M13], 0.0001f);
    }

    @Test
    public void neutralPoseDoesNotIntroduceHeadingOrLean() {
        BikePoseTransform.setFromPhysics(root, offset,
                0f, 0.826f, 0f, 0.72f, 0.36f, new Quaternion());
        Vector3 forward = new Vector3(0f, 0f, 1f).rot(root);
        assertEquals(0f, forward.x, 0f);
        assertEquals(0f, forward.y, 0f);
        assertEquals(1f, forward.z, 0f);
        assertEquals(-0.36f, offset.y, 0.00001f);
    }
}
