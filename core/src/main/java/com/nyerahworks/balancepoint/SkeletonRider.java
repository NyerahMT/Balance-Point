package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Presentation-only rider built from Kenney's CC0 Graveyard Kit skeleton OBJ.
 * Rider physics remains in MotorcycleRiderDynamics; this class only presents the pose.
 */
final class SkeletonRider {
    private static final String ASSET = "models/character-skeleton.obj";
    private static final float SCALE_X = 1.55f;
    private static final float SCALE_Y = 1.90f;
    private static final float SCALE_Z = 1.70f;

    // The source torso begins at y≈0.223 and the legs end at y≈0.195. Keeping the upper body
    // hinged around this shared pelvis point prevents the ribcage from sliding away from the
    // hips as the rider leans fore/aft.
    private static final float HIP_Y = 0.205f;
    private static final float HIP_Z = 0.005f;

    private final Model model;
    private final ModelInstance instance;
    private final Quaternion poseRotation = new Quaternion();
    private final Quaternion upperRotation = new Quaternion();
    private final Quaternion localRotation = new Quaternion();
    private final Quaternion combinedRotation = new Quaternion();
    private final Vector3 posePivot = new Vector3();
    private final Vector3 rotatedPivot = new Vector3();
    private final Vector3 upperTranslation = new Vector3();
    private final Vector3 localTranslation = new Vector3();

    private SkeletonRider(Model model) {
        this.model = model;
        this.instance = new ModelInstance(model);

        Color bone = new Color(0.94f, 0.90f, 0.74f, 1f);
        for (Material material : instance.materials) {
            material.set(ColorAttribute.createDiffuse(bone));
            material.set(IntAttribute.createCullFace(GL20.GL_NONE));
        }
    }

    static SkeletonRider load(Array<Model> ownedModels) {
        if (!Gdx.files.internal(ASSET).exists()) {
            Gdx.app.error("BalancePoint", "Skeleton rider asset not packaged; using fallback rider");
            return null;
        }

        try {
            Model model = new ObjLoader().loadModel(Gdx.files.internal(ASSET));
            ownedModels.add(model);
            SkeletonRider rider = new SkeletonRider(model);
            Gdx.app.log("BalancePoint", "Loaded skeleton rider with "
                    + model.nodes.size + " nodes and " + model.meshes.size + " meshes");
            return rider;
        } catch (RuntimeException e) {
            Gdx.app.error("BalancePoint", "Could not load skeleton rider; using fallback rider", e);
            return null;
        }
    }

    void update(Matrix4 bikeRoot, float riderLean) {
        float shift = riderLean;
        float leanMagnitude = Math.abs(shift);
        instance.transform.set(bikeRoot)
                .translate(0f, 0.29f - leanMagnitude * 0.015f, 0.45f + shift * 0.15f)
                .rotate(Vector3.X, -7f - shift * 7f)
                .scale(SCALE_X, SCALE_Y, SCALE_Z);

        // The pelvis follows the rider mass directly. The upper torso hinges from that pelvis
        // in the SAME direction, so shoulders travel slightly farther fore/aft than the hips
        // instead of the hips sliding underneath a nearly stationary chest. The extra reach is
        // mildly progressive near full lean to give the pose a natural bowed/extended shape.
        float spineDegrees = -18f + shift * (6f + 4f * leanMagnitude);
        poseUpperNode("torso", 0f, HIP_Y, HIP_Z, spineDegrees, 0f);
        poseUpperNode("head", 0f, 0.45f, 0f, spineDegrees, 10f + shift * 2f);
        poseUpperNode("arm-left", 0.18f, 0.42f, 0f,
                spineDegrees, -46f - shift);
        poseUpperNode("arm-right", -0.18f, 0.42f, 0f,
                spineDegrees, -46f - shift);

        // Legs stay tied to their peg/hip pivots while the torso remains connected at the pelvis.
        poseNode("leg-left", 0.075f, 0.20f, 0f, 27f + shift * 3f);
        poseNode("leg-right", -0.075f, 0.20f, 0f, 27f + shift * 3f);
        instance.calculateTransforms();
    }

    private void poseUpperNode(
            String id,
            float localPx,
            float localPy,
            float localPz,
            float upperDegrees,
            float localDegrees) {
        Node node = instance.getNode(id, true);
        if (node == null) return;

        upperRotation.set(Vector3.X, upperDegrees);
        posePivot.set(0f, HIP_Y, HIP_Z);
        rotatedPivot.set(posePivot);
        upperRotation.transform(rotatedPivot);
        upperTranslation.set(posePivot).sub(rotatedPivot);

        localRotation.set(Vector3.X, localDegrees);
        posePivot.set(localPx, localPy, localPz);
        rotatedPivot.set(posePivot);
        localRotation.transform(rotatedPivot);
        localTranslation.set(posePivot).sub(rotatedPivot);
        upperRotation.transform(localTranslation);

        combinedRotation.set(upperRotation).mul(localRotation);
        node.translation.set(upperTranslation).add(localTranslation);
        node.rotation.set(combinedRotation);
        node.scale.set(1f, 1f, 1f);
    }

    private void poseNode(String id, float px, float py, float pz, float degrees) {
        Node node = instance.getNode(id, true);
        if (node == null) return;

        poseRotation.set(Vector3.X, degrees);
        posePivot.set(px, py, pz);
        rotatedPivot.set(posePivot);
        poseRotation.transform(rotatedPivot);
        node.translation.set(posePivot).sub(rotatedPivot);
        node.rotation.set(poseRotation);
        node.scale.set(1f, 1f, 1f);
    }

    void render(ModelBatch batch, Environment environment) {
        batch.render(instance, environment);
    }

    void renderShadow(ModelBatch batch) {
        batch.render(instance);
    }
}
