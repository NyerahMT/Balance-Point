package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Presentation-only rider built from Kenney's CC0 Graveyard Kit skeleton OBJ.
 *
 * The rider physics remains in MotorcycleRiderDynamics. This class only turns that fore/aft
 * state into a deliberately goofy but recognizable body pose on the motorcycle.
 */
final class SkeletonRider {
    private static final String ASSET = "models/character-skeleton.obj";

    // Source model is intentionally small. Non-uniform scale gets it into believable rider
    // proportions without touching the original CC0 geometry.
    private static final float SCALE_X = 1.55f;
    private static final float SCALE_Y = 1.90f;
    private static final float SCALE_Z = 1.70f;

    private final Model model;
    private final ModelInstance instance;

    private SkeletonRider(Model model) {
        this.model = model;
        this.instance = new ModelInstance(model);

        Color bone = new Color(0.86f, 0.83f, 0.70f, 1f);
        for (Material material : model.materials) {
            material.set(ColorAttribute.createDiffuse(bone));
        }
    }

    static SkeletonRider load(Array<Model> ownedModels) {
        if (!Gdx.files.internal(ASSET).exists()) {
            Gdx.app.log("BalancePoint", "Skeleton rider asset not packaged; using fallback rider");
            return null;
        }

        try {
            Model model = new ObjLoader().loadModel(Gdx.files.internal(ASSET));
            ownedModels.add(model);
            return new SkeletonRider(model);
        } catch (RuntimeException e) {
            Gdx.app.error("BalancePoint", "Could not load skeleton rider; using fallback rider", e);
            return null;
        }
    }

    void update(Matrix4 bikeRoot, float riderLean) {
        // Feet originate near source Y=0. Put them close to the pegs, then move/lean the whole
        // body with the dynamic rider state. Full-forward/full-rearward input remains visible.
        float shift = riderLean;
        instance.transform.set(bikeRoot)
                .translate(0f, 0.29f - Math.abs(shift) * 0.015f, 0.45f + shift * 0.15f)
                .rotate(Vector3.X, -7f - shift * 7f)
                .scale(SCALE_X, SCALE_Y, SCALE_Z);

        // Kenney's OBJ exposes these pieces as named groups. Bend the standing source pose into
        // a riding stance around approximate anatomical pivots. If a future source revision
        // omits a group, the rest of the skeleton still renders rather than failing the ride.
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
        node.localTransform.idt()
                .translate(px, py, pz)
                .rotate(Vector3.X, degrees)
                .translate(-px, -py, -pz);
    }

    void render(ModelBatch batch, Environment environment) {
        batch.render(instance, environment);
    }

    void renderShadow(ModelBatch batch) {
        batch.render(instance);
    }
}
