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
 * The replacement importer preserves the approved neutral GLB pose, but moving
 * assemblies are allowed to use local pivot coordinates as long as zero-angle
 * reconstruction lands exactly on that authored pose.
 */
final class DirtBikeMeshLoader {
    private DirtBikeMeshLoader() {}

    static final class LoadedBike {
        final ModelInstance body;
        final ModelInstance engine;
        final ModelInstance steering;
        final ModelInstance frontWheel;
        final ModelInstance rearWheel;
        final Vector3 rearAxleOffset;
        final Vector3 steeringHead;
        final Vector3 steeringAxis;
        final Vector3 frontAxleOffset;
        final float wheelRadius;

        LoadedBike(Model bodyModel,
                   Model engineModel,
                   Model steeringModel,
                   Model frontWheelModel,
                   Model rearWheelModel,
                   Vector3 rearAxleOffset,
                   Vector3 steeringHead,
                   Vector3 steeringAxis,
                   Vector3 frontAxleOffset,
                   float wheelRadius) {
            this.body = new ModelInstance(bodyModel);
            this.engine = new ModelInstance(engineModel);
            this.steering = new ModelInstance(steeringModel);
            this.frontWheel = new ModelInstance(frontWheelModel);
            this.rearWheel = new ModelInstance(rearWheelModel);
            this.rearAxleOffset = new Vector3(rearAxleOffset);
            this.steeringHead = new Vector3(steeringHead);
            this.steeringAxis = new Vector3(steeringAxis).nor();
            this.frontAxleOffset = new Vector3(frontAxleOffset);
            this.wheelRadius = wheelRadius;
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
