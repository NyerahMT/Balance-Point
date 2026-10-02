package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
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
    private final Vector3 bodyAnchor = new Vector3();
    private final Vector3 smoothedTarget = new Vector3();
    private final Vector3 tempRight = new Vector3();
    private final State state = new State();

    private float helmetTrajectoryPitch;
    private float helmetTrajectoryPitchVelocity;
    private float menuCameraZ;
    private float menuCameraBobPhase;
    private float chaseOrbitYaw;
    private float chaseOrbitPitch;
    private boolean rideCameraInitialized;
    private boolean previousCockpitCamera;

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

    float worldCenterZ(float rideZ) {
        return state.gameState == GameState.MAIN_MENU ? menuCameraZ : rideZ;
    }

    void resize(int width, int height) {
        camera.viewportWidth = Math.max(1, width);
        camera.viewportHeight = Math.max(1, height);
        camera.update();
    }

    void enterMainMenu() {
        menuCameraZ = MathUtils.random(-100f, 5000f);
        menuCameraBobPhase = MathUtils.random(0f, MathUtils.PI2);
        rideCameraInitialized = false;
    }

    void resetRide() {
        helmetTrajectoryPitch = 0f;
        helmetTrajectoryPitchVelocity = 0f;
        chaseOrbitYaw = 0f;
        chaseOrbitPitch = 0f;
        rideCameraInitialized = false;
        previousCockpitCamera = state.cockpitCamera;
    }

    void update(float dt, TerrainVisuals terrain) {
        if (state.gameState == GameState.MAIN_MENU) {
            updateMenu(dt);
            return;
        }

        boolean cameraModeChanged = state.cockpitCamera != previousCockpitCamera;
        if (cameraModeChanged) {
            rideCameraInitialized = false;
            helmetTrajectoryPitchVelocity = 0f;
            previousCockpitCamera = state.cockpitCamera;
        }

        if (state.cockpitCamera) {
            float viewYaw = state.yaw + state.lookYaw;
            updateHelmetCamera(dt, terrain, viewYaw);
        } else {
            updateChaseOrbitInput();
            updateChaseCamera(dt, terrain, state.yaw + chaseOrbitYaw);
        }
        camera.update();
    }

    /**
     * External-camera orbit is deliberately owned here rather than by the temporary look input
     * used by the helmet camera. Once the rider drags the chase camera somewhere, it stays there
     * until they drag it again instead of easing back behind the motorcycle.
     */
    private void updateChaseOrbitInput() {
        int width = Math.max(1, Gdx.graphics.getWidth());
        int height = Math.max(1, Gdx.graphics.getHeight());
        for (int pointer = 0; pointer < 8; pointer++) {
            if (!Gdx.input.isTouched(pointer)) continue;
            float x = Gdx.input.getX(pointer) / (float) width;
            float y = Gdx.input.getY(pointer) / (float) height;
            if (x <= 0.30f || x >= 0.70f || y <= 0.10f || y >= 0.66f) continue;

            chaseOrbitYaw -= Gdx.input.getDeltaX(pointer) * 0.0048f;
            chaseOrbitPitch -= Gdx.input.getDeltaY(pointer) * 0.0043f;
            chaseOrbitYaw = wrapRadians(chaseOrbitYaw);
            chaseOrbitPitch = MathUtils.clamp(
                    chaseOrbitPitch,
                    -32f * MathUtils.degreesToRadians,
                    38f * MathUtils.degreesToRadians);
        }
    }

    private void updateChaseCamera(float dt, TerrainVisuals terrain, float viewYaw) {
        updateBodyAnchor();

        // Keep ride-camera geometry predictable. Speed changes only FOV; it never changes
        // chase distance, height, target lead, banking or airborne framing.
        final float chaseDistance = 4.25f;
        float horizontalDistance = chaseDistance * MathUtils.cos(chaseOrbitPitch);
        float sinView = MathUtils.sin(viewYaw);
        float cosView = MathUtils.cos(viewYaw);

        tempPosition.set(
                bodyAnchor.x - sinView * horizontalDistance,
                bodyAnchor.y + 0.72f + chaseDistance * MathUtils.sin(chaseOrbitPitch),
                bodyAnchor.z - cosView * horizontalDistance);

        if (terrain != null) {
            float ground = terrain.groundHeight(tempPosition.x, tempPosition.z);
            tempPosition.y = Math.max(tempPosition.y, ground + 0.62f);
        } else {
            tempPosition.y = Math.max(tempPosition.y, 0.62f);
        }

        if (!rideCameraInitialized) {
            camera.position.set(tempPosition);
        } else {
            camera.position.lerp(tempPosition, expResponse(10.5f, dt));
        }

        final float targetLead = 1.05f;
        tempTarget.set(
                bodyAnchor.x + MathUtils.sin(state.yaw) * targetLead,
                bodyAnchor.y + 0.10f,
                bodyAnchor.z + MathUtils.cos(state.yaw) * targetLead);

        if (!rideCameraInitialized) {
            smoothedTarget.set(tempTarget);
        } else {
            smoothedTarget.lerp(tempTarget, expResponse(12f, dt));
        }

        camera.up.set(Vector3.Y);
        camera.lookAt(smoothedTarget);

        float speedBlend = MathUtils.clamp(Math.abs(state.speed) / 40f, 0f, 1f);
        float targetFov = MathUtils.lerp(67f, 72f, speedBlend);
        camera.fieldOfView = rideCameraInitialized
                ? MathUtils.lerp(camera.fieldOfView, targetFov, expResponse(6f, dt))
                : targetFov;
        rideCameraInitialized = true;
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
        tempPosition.set(0f, helmetY, helmetZ + state.riderLean * 0.055f)
                .mul(state.bikeRoot);

        // Do not lag the eye point behind the bike. The previous positional lerp caused visible
        // skipping/dragging whenever chassis motion outran the smoothed camera position.
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
                * cameraPitchFrequency * cameraPitchFrequency
                - 2f * cameraPitchDamping * cameraPitchFrequency
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
        tempRight.set(camera.direction).crs(Vector3.Y);
        if (tempRight.len2() < 0.0001f) tempRight.set(Vector3.X);
        tempRight.nor();
        camera.up.set(tempRight).crs(camera.direction).nor()
                .rotate(camera.direction, cameraBank * MathUtils.radiansToDegrees).nor();

        float speedBlend = MathUtils.clamp(Math.abs(state.speed) / 40f, 0f, 1f);
        float targetFov = MathUtils.lerp(79f, 83f, speedBlend);
        camera.fieldOfView = rideCameraInitialized
                ? MathUtils.lerp(camera.fieldOfView, targetFov, expResponse(6f, dt))
                : targetFov;
        rideCameraInitialized = true;
    }

    /** Follow the chassis so wheel/suspension movement remains visible inside the frame. */
    private void updateBodyAnchor() {
        if (state.bikeRoot != null) {
            bodyAnchor.set(0f, 0.62f, 0.62f).mul(state.bikeRoot);
        } else {
            bodyAnchor.set(state.bikeX, state.bikeY + 0.62f, state.bikeZ);
        }
    }

    private static float wrapRadians(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }

    private static float expResponse(float responsePerSecond, float dt) {
        return 1f - (float) Math.exp(-Math.max(0f, responsePerSecond) * Math.max(0f, dt));
    }
}
