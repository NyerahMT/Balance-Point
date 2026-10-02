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

    private final Model model;
    private final ModelInstance instance;
    private final Quaternion poseRotation = new Quaternion();
    private final Vector3 posePivot = new Vector3();
    private final Vector3 rotatedPivot = new Vector3();

    private SkeletonRider(Model model) {
        this.model = model;
        this.instance = new ModelInstance(model);

        // Apply presentation material to the instance materials themselves. ModelInstance may
        // own copies of the source materials, so mutating only model.materials is not reliable.
        // Disable culling as well: this tiny low-poly OBJ is cheap to draw two-sided and doing so
        // avoids winding/culling differences between mobile GL backends making bones disappear.
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
        instance.transform.set(bikeRoot)
                .translate(0f, 0.29f - Math.abs(shift) * 0.015f, 0.45f + shift * 0.15f)
                .rotate(Vector3.X, -7f - shift * 7f)
                .scale(SCALE_X, SCALE_Y, SCALE_Z);

        // Kenney's source groups use absolute vertex coordinates. Rotate each group around an
        // anatomical pivot by expressing T(p) * R * T(-p) as node translation + rotation.
        // Using node TRS rather than writing localTransform directly survives calculateTransforms.
        poseNode("torso", 0f, 0.27f, 0f, -20f - shift * 7f);
        poseNode("head", 0f, 0.45f, 0f, -8f - shift * 4f);
        poseNode("arm-left", 0.18f, 0.42f, 0f, -64f - shift * 5f);
        poseNode("arm-right", -0.18f, 0.42f, 0f, -64f - shift * 5f);
        poseNode("leg-left", 0.075f, 0.20f, 0f, 27f + shift * 3f);
        poseNode("leg-right", -0.075f, 0.20f, 0f, 27f + shift * 3f);
        instance.calculateTransforms();
    }

    private void poseNode(String id, float px, float py, float pz, float degrees) {
        Node node = instance.getNode(id, true);
        if (node == null) return;

        poseRotation.set(Vector3.X, degrees);
        posePivot.set(px, py, pz);
        rotatedPivot.set(posePivot).rot(poseRotation);
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
