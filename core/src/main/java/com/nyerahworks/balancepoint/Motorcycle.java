package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * First-class motorcycle presentation aggregate.
 *
 * GameScene owns one motorcycle; the motorcycle owns the imported mechanical art and rig.
 * Physics is still coordinated by BalancePointGame for now, but presentation code outside
 * this class no longer needs to know that the bike is composed of separate libGDX instances.
 */
final class Motorcycle {
    private final DirtBikeMeshLoader.LoadedBike art;
    private final DirtBikeVisualRig rig;

    Motorcycle(DirtBikeMeshLoader.LoadedBike art,
               float wheelbase,
               float physicsWheelRadius) {
        this.art = art;
        this.rig = new DirtBikeVisualRig(art, wheelbase, physicsWheelRadius);
    }

    void update(Matrix4 bikeRoot, GameScene.BikeState state, TerrainVisuals terrain) {
        rig.update(bikeRoot, state, terrain);
    }

    Matrix4 steeringRoot() {
        return rig.steeringRoot();
    }

    Vector3 steeringHead() {
        return art.steeringHead;
    }

    void render(ModelBatch batch, Environment environment) {
        batch.render(art.body, environment);
        batch.render(art.engine, environment);
        batch.render(art.steering, environment);
        rig.renderExtras(batch, environment);
        batch.render(art.rearWheel, environment);
        batch.render(art.frontWheel, environment);
    }

    void renderShadow(ModelBatch batch) {
        batch.render(art.body);
        batch.render(art.engine);
        batch.render(art.steering);
        rig.renderShadowExtras(batch);
        batch.render(art.rearWheel);
        batch.render(art.frontWheel);
    }
}
