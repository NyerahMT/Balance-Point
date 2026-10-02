package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Adds articulated presentation on top of the viewer-faithful imported dirt bike.
 *
 * The source GLB has no useful suspension hierarchy, but the importer already splits
 * disconnected pieces into individual mesh parts. This rig duplicates only the pieces
 * that belong to the swingarm and lower fork guards, hides those pieces in the rigid
 * instances, then animates the duplicates around real motorcycle-style pivots.
 *
 * Suspension motion is presentation-only here: GameScene.BikeState receives signed travel
 * directly from the physics-owned MotorcycleSuspension model. The rig never samples terrain
 * or invents its own suspension response.
 */
final class DirtBikeVisualRig {
    private static final float REAR_MAX_EXTENSION = 0.085f;
    private static final float REAR_MAX_COMPRESSION = 0.225f;
    // Front rider sag is a little over 70 mm, so 60 mm visually clipped the last part of droop.
    // Keep enough range to show the entire physics-owned extension stroke without saturation.
    private static final float FRONT_MAX_EXTENSION = 0.090f;
    private static final float FRONT_MAX_COMPRESSION = 0.260f;

    private final DirtBikeMeshLoader.LoadedBike bike;
    private final float wheelbase;
    private final float physicsWheelRadius;

    private final Matrix4 steeringRoot = new Matrix4();
    private final Matrix4 rearSuspensionRoot = new Matrix4();
    private final Matrix4 frontSuspensionRoot = new Matrix4();
    private final Vector3 swingarmPivot = new Vector3();
    private final Vector3 frontCompressionAxis = new Vector3();
    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();

    private final ModelInstance swingarm;
    private final ModelInstance frontSliders;
    private float visualSteerDegrees;

    DirtBikeVisualRig(DirtBikeMeshLoader.LoadedBike bike,
                      float wheelbase,
                      float physicsWheelRadius) {
        this.bike = bike;
        this.wheelbase = wheelbase;
        this.physicsWheelRadius = physicsWheelRadius;

        bike.body.calculateTransforms();
        bike.steering.calculateTransforms();

        Array<PartBounds> bodyParts = collectPartBounds(bike.body);
        ObjectSet<String> swingarmIds = findSwingarmParts(bodyParts);
        if (swingarmIds.size > 0) {
            swingarm = new ModelInstance(bike.body.model);
            enableOnly(swingarm, swingarmIds);
            disableSelected(bike.body, swingarmIds);
            estimateSwingarmPivot(bodyParts, swingarmIds, swingarmPivot);
        } else {
            swingarm = null;
            swingarmPivot.set(bike.rearAxleOffset).add(0f, 0.22f, wheelbase * 0.39f);
        }

        Array<PartBounds> steeringParts = collectPartBounds(bike.steering);
        ObjectSet<String> sliderIds = findFrontSliderParts(steeringParts);
        if (sliderIds.size > 0) {
            frontSliders = new ModelInstance(bike.steering.model);
            enableOnly(frontSliders, sliderIds);
            disableSelected(bike.steering, sliderIds);
        } else {
            frontSliders = null;
        }

        // SteeringAxis is the fitted fork/rake axis. steeringHead is merely one point on that
        // line chosen near the front axle so steering rotation remains exact; subtracting the
        // axle from that point produces an almost-zero vector and previously made fork travel
        // effectively directionless. Compression must translate along the actual fork axis.
        frontCompressionAxis.set(bike.steeringAxis).nor();

        Gdx.app.log("BalancePoint", "Visual suspension rig: swingarmParts="
                + swingarmIds.size + " lowerForkParts=" + sliderIds.size
                + " lowerForkIds=" + sliderIds
                + " swingarmPivot=" + swingarmPivot
                + " forkCompressionAxis=" + frontCompressionAxis);
    }

    Matrix4 steeringRoot() {
        return steeringRoot;
    }

    void update(Matrix4 bikeRoot, GameScene.BikeState state, TerrainVisuals terrain) {
        bike.body.transform.set(bikeRoot);
        bike.engine.transform.set(bikeRoot);

        float importedSpinDeg = -state.wheelSpin
                * (physicsWheelRadius / bike.wheelRadius)
                * MathUtils.radiansToDegrees;

        float rearOffset = MathUtils.clamp(
                state.rearSuspensionTravel,
                -REAR_MAX_EXTENSION,
                REAR_MAX_COMPRESSION);
        float effectiveRearOffset = swingarm != null ? rearOffset : 0f;
        float rearArmForward = Math.max(0.34f, swingarmPivot.z - bike.rearAxleOffset.z);
        float rearAngleDeg = MathUtils.clamp(
                effectiveRearOffset / rearArmForward * MathUtils.radiansToDegrees,
                -13f,
                22f);

        if (swingarm != null) {
            rearSuspensionRoot.set(bikeRoot)
                    .translate(swingarmPivot)
                    .rotate(Vector3.X, rearAngleDeg)
                    .translate(-swingarmPivot.x, -swingarmPivot.y, -swingarmPivot.z);
            swingarm.transform.set(rearSuspensionRoot);

            tempA.set(bike.rearAxleOffset).sub(swingarmPivot);
            bike.rearWheel.transform.set(bikeRoot)
                    .translate(swingarmPivot)
                    .rotate(Vector3.X, rearAngleDeg)
                    .translate(tempA)
                    .rotate(Vector3.X, importedSpinDeg);
        } else {
            bike.rearWheel.transform.set(bikeRoot)
                    .translate(bike.rearAxleOffset)
                    .rotate(Vector3.X, importedSpinDeg);
        }

        float targetVisualSteer = targetVisualSteerDegrees(state);
        float visualResponse = state.frontGrounded ? 11.5f : 8.0f;
        visualSteerDegrees += (targetVisualSteer - visualSteerDegrees)
                * Math.min(1f, Gdx.graphics.getDeltaTime() * visualResponse);
        float visualSteerDeg = visualSteerDegrees;
        steeringRoot.set(bikeRoot)
                .translate(bike.steeringHead)
                .rotate(bike.steeringAxis, visualSteerDeg);
        bike.steering.transform.set(steeringRoot);

        float frontOffset = MathUtils.clamp(
                state.frontSuspensionTravel,
                -FRONT_MAX_EXTENSION,
                FRONT_MAX_COMPRESSION);
        // Wheel travel is physics-owned and must never depend on whether the visual lower-fork
        // classifier found its optional mesh group. If slider extraction ever fails, the axle
        // still moves correctly and the failure is isolated to presentation.
        float effectiveFrontOffset = frontOffset;
        tempA.set(frontCompressionAxis).scl(effectiveFrontOffset);
        if (frontSliders != null) {
            frontSuspensionRoot.set(steeringRoot).translate(tempA);
            frontSliders.transform.set(frontSuspensionRoot);
        }

        tempB.set(bike.frontAxleOffset).sub(bike.steeringHead);
        bike.frontWheel.transform.set(steeringRoot)
                .translate(tempA)
                .translate(tempB)
                .rotate(Vector3.X, importedSpinDeg);
    }

    void renderExtras(ModelBatch batch, Environment environment) {
        if (swingarm != null) batch.render(swingarm, environment);
        if (frontSliders != null) batch.render(frontSliders, environment);
    }

    void renderShadowExtras(ModelBatch batch) {
        if (swingarm != null) batch.render(swingarm);
        if (frontSliders != null) batch.render(frontSliders);
    }

    private static float targetVisualSteerDegrees(GameScene.BikeState state) {
        float absSpeed = Math.abs(state.speed);

        if (!state.frontGrounded) {
            float airborneMax = state.terrainAirborne ? 9f : 13f;
            return state.steer * airborneMax;
        }

        float roadBlend = MathUtils.clamp((absSpeed - 2.5f) / 10.5f, 0f, 1f);
        roadBlend = roadBlend * roadBlend * (3f - 2f * roadBlend);

        float lowSpeedSteer = state.steer * 28f;
        float rollDeg = state.roll * MathUtils.radiansToDegrees;
        float settledSteer = MathUtils.clamp(rollDeg * 0.070f, -4.0f, 4.0f);

        // At turn-in the bars briefly show a small countersteer cue; once the bike is leaned,
        // steering visually settles with the chassis instead of oscillating between two poses.
        float uprightBlend = 1f - MathUtils.clamp(Math.abs(rollDeg) / 16f, 0f, 1f);
        float counterSteerCue = -state.steer * 1.5f * uprightBlend;
        float highSpeedSteer = settledSteer + counterSteerCue;

        return MathUtils.lerp(lowSpeedSteer, highSpeedSteer, roadBlend);
    }

    private ObjectSet<String> findSwingarmParts(Array<PartBounds> parts) {
        ObjectSet<String> result = new ObjectSet<>();
        float rearZ = bike.rearAxleOffset.z;
        float rearY = bike.rearAxleOffset.y;

        for (PartBounds part : parts) {
            float spanX = part.max.x - part.min.x;
            float spanY = part.max.y - part.min.y;
            float spanZ = part.max.z - part.min.z;
            boolean reachesRear = part.min.z < rearZ + wheelbase * 0.14f;
            boolean reachesPivot = part.max.z > rearZ + wheelbase * 0.25f;
            boolean rearHalf = part.center.z < rearZ + wheelbase * 0.43f;
            boolean low = part.center.y < rearY + 0.40f;
            boolean longEnough = spanZ > wheelbase * 0.24f;
            boolean compactSection = spanY < 0.34f && spanX < 0.55f;

            if (reachesRear && reachesPivot && rearHalf && low
                    && longEnough && compactSection) {
                result.add(part.id);
            }
        }
        return result;
    }

    private ObjectSet<String> findFrontSliderParts(Array<PartBounds> parts) {
        ObjectSet<String> result = new ObjectSet<>();

        // Steering geometry has already been rebased around a point on the fork axis near the
        // front axle. Classify lower-fork pieces by distance ALONG that axis and perpendicular
        // distance FROM it. The old frontAxle-steeringHead Y window was nearly zero by design,
        // so it could never robustly describe where the lower fork actually lives.
        for (PartBounds part : parts) {
            if (isLowerForkGuard(part, bike.steeringAxis, true)) result.add(part.id);
        }

        if (result.size >= 2) return result;
        result.clear();
        for (PartBounds part : parts) {
            if (isLowerForkGuard(part, bike.steeringAxis, false)) result.add(part.id);
        }
        return result;
    }

    private static boolean isLowerForkGuard(PartBounds part,
                                            Vector3 forkAxis,
                                            boolean requireOrange) {
        float spanX = part.max.x - part.min.x;
        float spanY = part.max.y - part.min.y;
        float spanZ = part.max.z - part.min.z;
        float longDimension = (float)Math.sqrt(spanY * spanY + spanZ * spanZ);

        float alongAxis = part.center.dot(forkAxis);
        float radialX = part.center.x - forkAxis.x * alongAxis;
        float radialY = part.center.y - forkAxis.y * alongAxis;
        float radialZ = part.center.z - forkAxis.z * alongAxis;
        float radialDistance = (float)Math.sqrt(
                radialX * radialX + radialY * radialY + radialZ * radialZ);

        boolean orange = isOrange(part.color);
        boolean lowerForkZone = alongAxis > 0.045f && alongAxis < 0.52f;
        boolean nearForkAxis = radialDistance < 0.27f;
        boolean slender = spanX < 0.20f
                && longDimension > 0.20f
                && longDimension < 0.72f;

        return (!requireOrange || orange)
                && lowerForkZone
                && nearForkAxis
                && slender;
    }

    private static boolean isOrange(Color color) {
        return color != null
                && color.r > 0.45f
                && color.r > color.g * 1.22f
                && color.g > color.b * 1.12f;
    }

    private void estimateSwingarmPivot(Array<PartBounds> parts,
                                       ObjectSet<String> selected,
                                       Vector3 out) {
        float sumY = 0f;
        float sumZ = 0f;
        int count = 0;
        for (PartBounds part : parts) {
            if (!selected.contains(part.id)) continue;
            sumY += part.max.y;
            sumZ += part.max.z;
            count++;
        }

        if (count == 0) {
            out.set(bike.rearAxleOffset).add(0f, 0.22f, wheelbase * 0.39f);
            return;
        }

        float pivotY = sumY / count;
        float pivotZ = sumZ / count;
        pivotY = MathUtils.clamp(
                pivotY,
                bike.rearAxleOffset.y + 0.08f,
                bike.rearAxleOffset.y + 0.52f);
        pivotZ = MathUtils.clamp(
                pivotZ,
                bike.rearAxleOffset.z + wheelbase * 0.28f,
                bike.rearAxleOffset.z + wheelbase * 0.62f);
        out.set(bike.rearAxleOffset.x, pivotY, pivotZ);
    }

    private static Array<PartBounds> collectPartBounds(ModelInstance instance) {
        Array<PartBounds> result = new Array<>();
        for (Node node : instance.nodes) collectPartBounds(node, result);
        return result;
    }

    private static void collectPartBounds(Node node, Array<PartBounds> out) {
        for (NodePart part : node.parts) {
            PartBounds bounds = boundsFor(node, part);
            if (bounds != null) out.add(bounds);
        }
        for (Node child : node.getChildren()) collectPartBounds(child, out);
    }

    private static PartBounds boundsFor(Node node, NodePart part) {
        Mesh mesh = part.meshPart.mesh;
        VertexAttribute position = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
        if (position == null || mesh.getNumVertices() == 0) return null;

        int stride = mesh.getVertexSize() / 4;
        int offset = position.offset / 4;
        float[] vertices = new float[mesh.getNumVertices() * stride];
        mesh.getVertices(vertices);

        PartBounds bounds = new PartBounds(part.meshPart.id);
        Vector3 point = new Vector3();
        for (int vertex = 0; vertex < mesh.getNumVertices(); vertex++) {
            int base = vertex * stride + offset;
            point.set(vertices[base], vertices[base + 1], vertices[base + 2])
                    .mul(node.globalTransform);
            bounds.include(point);
        }
        bounds.finish();

        ColorAttribute diffuse = (ColorAttribute)part.material.get(ColorAttribute.Diffuse);
        if (diffuse != null) bounds.color.set(diffuse.color);
        return bounds;
    }

    private static void enableOnly(ModelInstance instance, ObjectSet<String> selected) {
        for (Node node : instance.nodes) setPartMask(node, selected, true);
    }

    private static void disableSelected(ModelInstance instance, ObjectSet<String> selected) {
        for (Node node : instance.nodes) setPartMask(node, selected, false);
    }

    private static void setPartMask(Node node,
                                    ObjectSet<String> selected,
                                    boolean selectedOnly) {
        for (NodePart part : node.parts) {
            boolean chosen = selected.contains(part.meshPart.id);
            part.enabled = selectedOnly ? chosen : !chosen;
        }
        for (Node child : node.getChildren()) setPartMask(child, selected, selectedOnly);
    }

    private static final class PartBounds {
        final String id;
        final Vector3 min = new Vector3(
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY);
        final Vector3 max = new Vector3(
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY);
        final Vector3 center = new Vector3();
        final Color color = new Color(Color.WHITE);

        PartBounds(String id) {
            this.id = id;
        }

        void include(Vector3 point) {
            min.x = Math.min(min.x, point.x);
            min.y = Math.min(min.y, point.y);
            min.z = Math.min(min.z, point.z);
            max.x = Math.max(max.x, point.x);
            max.y = Math.max(max.y, point.y);
            max.z = Math.max(max.z, point.z);
        }

        void finish() {
            center.set(min).add(max).scl(0.5f);
        }
    }
}
