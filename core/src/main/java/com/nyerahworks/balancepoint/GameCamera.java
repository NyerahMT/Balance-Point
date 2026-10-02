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
    private final Vector3 bodyAnchor = new Vector3();
    private final Vector3 smoothedTarget = new Vector3();
    private final Vector3 tempRight = new Vector3();
    private final State state = new State();

    private float helmetTrajectoryPitch;
    private float helmetTrajectoryPitchVelocity;
    private float menuCameraZ;
    private float menuCameraBobPhase;
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

        float viewYaw = state.yaw + state.lookYaw;
        if (state.cockpitCamera) {
            updateHelmetCamera(dt, terrain, viewYaw);
        } else {
            updateChaseCamera(dt, terrain, viewYaw);
        }
        camera.update();
    }

    private void updateChaseCamera(float dt, TerrainVisuals terrain, float viewYaw) {
        updateBodyAnchor();

        float orbitPitch = MathUtils.clamp(
                state.lookPitch,
                -35f * MathUtils.degreesToRadians,
                32f * MathUtils.degreesToRadians);
        float speedBlend = MathUtils.clamp(Math.abs(state.speed) / 40f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);

        // Pull back as speed builds and a little more in the air. This makes jumps readable
        // without making low-speed technical riding feel like the camera is in another county.
        float chaseDistance = MathUtils.lerp(4.05f, 5.15f, speedBlend)
                + (state.terrainAirborne ? 0.35f : 0f);
        float horizontalDistance = chaseDistance * MathUtils.cos(orbitPitch);
        float sinView = MathUtils.sin(viewYaw);
        float cosView = MathUtils.cos(viewYaw);

        tempPosition.set(
                bodyAnchor.x - sinView * horizontalDistance,
                bodyAnchor.y + 0.78f + chaseDistance * MathUtils.sin(orbitPitch),
                bodyAnchor.z - cosView * horizontalDistance);

        // Keep the chase camera from diving through hills when orbiting low behind the bike.
        if (terrain != null) {
            float ground = terrain.groundHeight(tempPosition.x, tempPosition.z);
            tempPosition.y = Math.max(tempPosition.y, ground + 0.68f);
        } else {
            tempPosition.y = Math.max(tempPosition.y, 0.68f);
        }

        float positionResponse = MathUtils.lerp(8.5f, 12.5f, speedBlend);
        float positionBlend = expResponse(positionResponse, dt);
        if (!rideCameraInitialized) {
            camera.position.set(tempPosition);
        } else {
            camera.position.lerp(tempPosition, positionBlend);
        }

        float targetLead = MathUtils.lerp(0.75f, 1.85f, speedBlend);
        tempTarget.set(
                bodyAnchor.x + MathUtils.sin(state.yaw) * targetLead,
                bodyAnchor.y + 0.13f + MathUtils.sin(state.pitch) * 0.22f,
                bodyAnchor.z + MathUtils.cos(state.yaw) * targetLead);

        if (!rideCameraInitialized) {
            smoothedTarget.set(tempTarget);
        } else {
            smoothedTarget.lerp(tempTarget, expResponse(13f, dt));
        }

        camera.up.set(Vector3.Y);
        camera.lookAt(smoothedTarget);
        applyCameraBank((state.roll + state.terrainRoll) * 0.18f);

        float targetFov = MathUtils.lerp(67f, 78f, speedBlend)
                + (state.terrainAirborne ? 1.5f : 0f);
        camera.fieldOfView = rideCameraInitialized
                ? MathUtils.lerp(camera.fieldOfView, targetFov, expResponse(5.5f, dt))
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

        // A helmet is attached to a rider, not welded to the steering head. High-frequency
        // chassis movement is therefore attenuated while large bike motion still comes through.
        if (!rideCameraInitialized) {
            camera.position.set(tempPosition);
        } else {
            camera.position.lerp(tempPosition, expResponse(18f, dt));
        }

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

        // The rider's eyes broadly follow trajectory rather than copying every chassis pitch.
        // A slightly slower response in flight lets takeoff/landing attitude read naturally.
        float cameraPitchFrequency = state.terrainAirborne ? 4.7f : 6.4f;
        float cameraPitchDamping = state.terrainAirborne ? 0.96f : 1.10f;
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
                -62f * MathUtils.degreesToRadians,
                62f * MathUtils.degreesToRadians);
        float cosPitch = MathUtils.cos(viewPitch);
        camera.direction.set(
                MathUtils.sin(viewYaw) * cosPitch,
                MathUtils.sin(viewPitch),
                MathUtils.cos(viewYaw) * cosPitch).nor();

        // Human head stabilization prevents full chassis bank from being copied 1:1. Keeping
        // some bank preserves the sense of lean without turning the horizon into a rigid HUD.
        float cameraBank = (state.roll + state.terrainRoll) * 0.58f;
        tempRight.set(camera.direction).crs(Vector3.Y);
        if (tempRight.len2() < 0.0001f) tempRight.set(Vector3.X);
        tempRight.nor();
        camera.up.set(tempRight).crs(camera.direction).nor()
                .rotate(camera.direction, cameraBank * MathUtils.radiansToDegrees).nor();

        float speedBlend = MathUtils.clamp(Math.abs(state.speed) / 40f, 0f, 1f);
        float targetFov = MathUtils.lerp(76f, 85f, speedBlend)
                + (state.terrainAirborne ? 1f : 0f);
        camera.fieldOfView = rideCameraInitialized
                ? MathUtils.lerp(camera.fieldOfView, targetFov, expResponse(6f, dt))
                : targetFov;
        rideCameraInitialized = true;
    }

    /**
     * Follow a chassis point rather than the rear-axle scalar used by the old chase camera.
     * This is intentionally transformed by bikeRoot so suspension movement remains visible
     * inside the frame instead of being visually cancelled by the camera following a wheel.
     */
    private void updateBodyAnchor() {
        if (state.bikeRoot != null) {
            bodyAnchor.set(0f, 0.62f, 0.62f).mul(state.bikeRoot);
        } else {
            bodyAnchor.set(state.bikeX, state.bikeY + 0.62f, state.bikeZ);
        }
    }

    private void applyCameraBank(float bankRadians) {
        tempRight.set(camera.direction).crs(Vector3.Y);
        if (tempRight.len2() < 0.0001f) return;
        tempRight.nor();
        camera.up.set(tempRight).crs(camera.direction).nor()
                .rotate(camera.direction, bankRadians * MathUtils.radiansToDegrees).nor();
    }

    private static float expResponse(float responsePerSecond, float dt) {
        return 1f - (float) Math.exp(-Math.max(0f, responsePerSecond) * Math.max(0f, dt));
    }
}
