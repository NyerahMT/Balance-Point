package com.nyerahworks.balancepoint;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Owns menu, chase, and helmet camera state independently from motorcycle simulation. */
final class GameCamera {
    static final class State {
        GameState gameState = GameState.MAIN_MENU;
        Matrix4 bikeRoot;
        boolean importedBikeLoaded;
        boolean terrainAirborne;
        boolean cockpitCamera;
        float bikeX;
        float bikeY;
        float bikeZ;
        float speed;
        float verticalVelocity;
        float pitch;
        float roll;
        float yaw;
        float terrainRoll;
        float riderLean;
        float lookYaw;
        float lookPitch;
    }

    private final PerspectiveCamera camera = new PerspectiveCamera(67f, 1f, 1f);
    private final Vector3 tempPosition = new Vector3();
    private final Vector3 tempTarget = new Vector3();
    private final State state = new State();

    private float helmetTrajectoryPitch;
    private float helmetTrajectoryPitchVelocity;
    private float menuCameraZ;
    private float menuCameraBobPhase;

    GameCamera() {
        camera.near = 0.08f;
        camera.far = 900f;
        camera.position.set(0f, 2.5f, -5.4f);
        camera.lookAt(0f, 0.8f, 4f);
        camera.update();
    }

    PerspectiveCamera camera() {
        return camera;
    }

    State state() {
        return state;
    }

    void resize(int width, int height) {
        camera.viewportWidth = Math.max(1, width);
        camera.viewportHeight = Math.max(1, height);
        camera.update();
    }

    void enterMainMenu() {
        menuCameraZ = MathUtils.random(-100f, 5000f);
        menuCameraBobPhase = MathUtils.random(0f, MathUtils.PI2);
    }

    void resetRide() {
        helmetTrajectoryPitch = 0f;
        helmetTrajectoryPitchVelocity = 0f;
    }

    void update(float dt, TerrainVisuals terrain) {
        if (state.gameState == GameState.MAIN_MENU) {
            updateMenu(dt);
            return;
        }

        float viewYaw = state.yaw + state.lookYaw;
        float sinView = MathUtils.sin(viewYaw);
        float cosView = MathUtils.cos(viewYaw);

        if (state.cockpitCamera) {
            updateHelmetCamera(dt, terrain, viewYaw);
        } else {
            float orbitPitch = MathUtils.clamp(
                    state.lookPitch,
                    -22f * MathUtils.degreesToRadians,
                    24f * MathUtils.degreesToRadians);
            float chaseDistance = 3.90f;
            float horizontalDistance = chaseDistance * MathUtils.cos(orbitPitch);
            float desiredHeight = state.bikeY
                    + 1.57f
                    + chaseDistance * MathUtils.sin(orbitPitch);
            tempPosition.set(
                    state.bikeX - sinView * horizontalDistance,
                    Math.max(0.82f, desiredHeight),
                    state.bikeZ - cosView * horizontalDistance);

            float followResponse = Math.min(1f, dt * (11.5f + state.speed * 1.35f));
            camera.position.lerp(tempPosition, followResponse);

            float targetLead = 1.15f;
            tempTarget.set(
                    state.bikeX + MathUtils.sin(state.yaw) * targetLead,
                    state.bikeY + 0.57f + MathUtils.sin(state.pitch) * 0.42f,
                    state.bikeZ + MathUtils.cos(state.yaw) * targetLead);
            camera.up.set(Vector3.Y);
            camera.lookAt(tempTarget);

            float speedFov = MathUtils.clamp(state.speed / 38f, 0f, 1f);
            camera.fieldOfView = MathUtils.lerp(68f, 76f, speedFov);
        }
        camera.update();
    }

    private void updateMenu(float dt) {
        menuCameraZ += dt * 5.5f;
        if (menuCameraZ > 5200f) menuCameraZ = -120f;
        menuCameraBobPhase += dt * 0.42f;
        float cameraY = 1.22f + MathUtils.sin(menuCameraBobPhase) * 0.028f;
        camera.position.set(0f, cameraY, menuCameraZ);
        camera.up.set(Vector3.Y);
        camera.lookAt(0f, 0.52f, menuCameraZ + 15f);
        camera.fieldOfView = 64f;
        camera.update();
    }

    private void updateHelmetCamera(float dt, TerrainVisuals terrain, float viewYaw) {
        if (state.bikeRoot == null) {
            throw new IllegalStateException("Helmet camera requires a bike root transform");
        }

        float helmetY = state.importedBikeLoaded ? 1.16f : 1.30f;
        float helmetZ = state.importedBikeLoaded ? 0.60f : (0.52f + state.riderLean * 0.10f);
        tempPosition.set(0f, helmetY, helmetZ).mul(state.bikeRoot);
        camera.position.set(tempPosition);

        float trajectoryPitchTarget;
        if (state.terrainAirborne) {
            trajectoryPitchTarget = (float) Math.atan2(
                    state.verticalVelocity,
                    Math.max(1.5f, Math.abs(state.speed)));
        } else if (terrain != null) {
            float pathSlopeX = terrain.groundSlopeX(state.bikeX, state.bikeZ);
            float pathSlopeZ = terrain.groundSlopeZ(state.bikeX, state.bikeZ);
            float pathGrade = pathSlopeX * MathUtils.sin(state.yaw)
                    + pathSlopeZ * MathUtils.cos(state.yaw);
            trajectoryPitchTarget = (float) Math.atan(pathGrade);
        } else {
            trajectoryPitchTarget = 0f;
        }

        float cameraPitchFrequency = 6.2f;
        float cameraPitchDamping = 1.08f;
        float cameraPitchAcceleration = (trajectoryPitchTarget - helmetTrajectoryPitch)
                * cameraPitchFrequency
                * cameraPitchFrequency
                - 2f
                * cameraPitchDamping
                * cameraPitchFrequency
                * helmetTrajectoryPitchVelocity;
        helmetTrajectoryPitchVelocity += cameraPitchAcceleration * dt;
        helmetTrajectoryPitch += helmetTrajectoryPitchVelocity * dt;

        float viewPitch = MathUtils.clamp(
                helmetTrajectoryPitch + state.lookPitch,
                -60f * MathUtils.degreesToRadians,
                60f * MathUtils.degreesToRadians);
        float cosPitch = MathUtils.cos(viewPitch);
        camera.direction.set(
                MathUtils.sin(viewYaw) * cosPitch,
                MathUtils.sin(viewPitch),
                MathUtils.cos(viewYaw) * cosPitch).nor();

        float cameraBank = state.roll + state.terrainRoll;
        tempTarget.set(camera.direction).crs(Vector3.Y).nor();
        camera.up.set(tempTarget).crs(camera.direction).nor()
                .rotate(camera.direction, cameraBank * MathUtils.radiansToDegrees).nor();
        camera.fieldOfView = 80f;
    }
}
