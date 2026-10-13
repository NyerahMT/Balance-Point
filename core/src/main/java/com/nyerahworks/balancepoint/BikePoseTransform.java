package com.nyerahworks.balancepoint;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** The same body rotation moves both the chassis mesh and rear-axle anchor. */
final class BikePoseTransform {
    private BikePoseTransform() {
    }

    static void setFromPhysics(Matrix4 root, Vector3 rearOffset,
                               float bodyX, float bodyY, float bodyZ,
                               float visualComForward, float visualComHeight,
                               Quaternion orientation) {
        // Physics uses +X right, +Y up, +Z forward. A positive yaw turns
        // the +Z bike-forward axis toward +X; never negate physics yaw.
        rearOffset.set(0f, -visualComHeight, -visualComForward)
                .mul(orientation);
        root.idt()
                .translate(bodyX + rearOffset.x,
                        bodyY + rearOffset.y,
                        bodyZ + rearOffset.z)
                .rotate(orientation);
    }
}
