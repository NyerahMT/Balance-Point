package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.io.IOException;

/**
 * Stable game-facing dirt-bike loader API.
 *
 * The old importer rebuilt the motorcycle into synthetic body/engine/steering/wheel
 * groups and accumulated placement corrections. That path is intentionally gone.
 * ViewerFaithfulDirtBikeLoader now imports the replacement GLB as one rigid scene.
 */
final class DirtBikeMeshLoader {
    private DirtBikeMeshLoader() {}

    static final class LoadedBike {
        final ModelInstance body;
        final ModelInstance engine;
        final ModelInstance steering;
        final ModelInstance frontWheel;
        final ModelInstance rearWheel;
        final Vector3 steeringHead;
        final Vector3 steeringAxis;
        final Vector3 frontAxleOffset;

        LoadedBike(Model bodyModel,
                   Model engineModel,
                   Model steeringModel,
                   Model frontWheelModel,
                   Model rearWheelModel,
                   Vector3 steeringHead,
                   Vector3 steeringAxis,
                   Vector3 frontAxleOffset) {
            this.body = new ModelInstance(bodyModel);
            this.engine = new ModelInstance(engineModel);
            this.steering = new ModelInstance(steeringModel);
            this.frontWheel = new ModelInstance(frontWheelModel);
            this.rearWheel = new ModelInstance(rearWheelModel);
            this.steeringHead = new Vector3(steeringHead);
            this.steeringAxis = new Vector3(steeringAxis).nor();
            this.frontAxleOffset = new Vector3(frontAxleOffset);
        }
    }

    static LoadedBike load(Array<Model> ownedModels,
                           Material bodyMaterial,
                           Material engineMaterial,
                           Material wheelMaterial) throws IOException {
        // Materials are retained in the signature so BalancePointGame does not need
        // a compatibility refactor. The replacement GLB's own material colors are used.
        return ViewerFaithfulDirtBikeLoader.load(ownedModels);
    }
}
