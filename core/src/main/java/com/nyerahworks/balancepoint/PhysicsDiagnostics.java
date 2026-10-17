package com.nyerahworks.balancepoint;

/**
 * Fixed-step diagnostic recorder. A capture begins at a known plant reset so its
 * input sequence can be deterministically replayed on the same terrain.
 * No per-step allocation; hard bounded to 90 seconds at 120 Hz.
 */
final class PhysicsDiagnostics {
    static final int MAX_FRAMES = 120 * 90;
    static final float FIXED_DT = 1f / 120f;
    private static final String[] COLUMNS = {
            "throttle",
            "rear_brake",
            "steer_input",
            "rider_fore_aft",
            "speed_mps",
            "pos_x_m",
            "pos_y_m",
            "pos_z_m",
            "roll_rad",
            "pitch_rad",
            "yaw_rad",
            "steer_angle_rad",
            "front_load_n",
            "rear_load_n",
            "front_tire_utilization",
            "rear_tire_utilization",
            "front_suspension_m",
            "rear_suspension_m",
            "steering_controller_nm",
            "countersteer_request_rad",
            "low_speed_balance_nm",
            "wheelie_catch_nm",
            "front_grounded",
            "rear_grounded",
            "engine_rpm"
    };
    private final float[][] values = new float[COLUMNS.length][MAX_FRAMES];
    private final float[] capturedParameters = new float[PhysicsTuning.PARAMETERS.length];
    private int frames;
    private int playback;
    private boolean capturing;
    private boolean replaying;
    private float maxPositionError;
    private float maxRollError;

    void startCapture(PhysicsTuning tuning) {
        frames = 0;
        playback = 0;
        capturing = true;
        replaying = false;
        maxPositionError = maxRollError = 0f;
        for (int i = 0; i < capturedParameters.length; i++) {
            capturedParameters[i] = tuning.get(i);
        }
    }

    void stopCapture() {
        capturing = false;
    }

    boolean capturing() {
        return capturing;
    }

    boolean replaying() {
        return replaying;
    }

    int frameCount() {
        return frames;
    }

    int replayFrame() {
        return playback;
    }

    boolean hasCapture() {
        return frames > 0;
    }

    float maxPositionError() {
        return maxPositionError;
    }

    float maxRollError() {
        return maxRollError;
    }

    void capture(PhysicsV3MultibodyPlant.Input input, PhysicsV3MultibodyPlant plant) {
        if (!capturing) return;
        if (frames == MAX_FRAMES) {
            capturing = false;
            return;
        }
        int f = frames++;
        values[0][f] = input.throttle;
        values[1][f] = input.rearBrake;
        values[2][f] = input.steer;
        values[3][f] = input.riderForeAft;
        PhysicsV3MultibodyPlant.Telemetry t = plant.telemetry();
        values[4][f] = plant.speed();
        values[5][f] = plant.x();
        values[6][f] = plant.y();
        values[7][f] = plant.z();
        values[8][f] = plant.roll();
        values[9][f] = plant.pitch();
        values[10][f] = plant.yaw();
        values[11][f] = plant.steerAngle();
        values[12][f] = t.frontFz;
        values[13][f] = t.rearFz;
        values[14][f] = t.frontUtilization;
        values[15][f] = t.rearUtilization;
        values[16][f] = t.frontSuspension;
        values[17][f] = t.rearSuspension;
        values[18][f] = t.steerControllerTorque;
        values[19][f] = t.countersteerRequest;
        values[20][f] = t.lowSpeedBalanceTorque;
        values[21][f] = t.wheelieCatchTorque;
        values[22][f] = t.frontGrounded ? 1f : 0f;
        values[23][f] = t.rearGrounded ? 1f : 0f;
        values[24][f] = t.engineRpm;
        if (frames == MAX_FRAMES) capturing = false;
    }

    boolean startReplay() {
        if (frames == 0 || capturing) return false;
        playback = 0;
        maxPositionError = maxRollError = 0f;
        replaying = true;
        return true;
    }

    void stopReplay() {
        replaying = false;
    }

    boolean applyReplayInput(PhysicsV3MultibodyPlant.Input out) {
        if (!replaying || playback >= frames) {
            replaying = false;
            return false;
        }
        out.throttle = values[0][playback];
        out.rearBrake = values[1][playback];
        out.steer = values[2][playback];
        out.riderForeAft = values[3][playback];
        return true;
    }

    void compareReplayStep(PhysicsV3MultibodyPlant plant) {
        if (!replaying || playback >= frames) return;
        float dx = plant.x() - values[5][playback];
        float dy = plant.y() - values[6][playback];
        float dz = plant.z() - values[7][playback];
        float error = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        maxPositionError = Math.max(maxPositionError, error);
        maxRollError = Math.max(maxRollError,
                Math.abs(plant.roll() - values[8][playback]));
        playback++;
        if (playback >= frames) replaying = false;
    }

    /** Portable CSV, including parameters required to reproduce the test. */
    String toCsv() {
        if (frames == 0) return "";
        StringBuilder out = new StringBuilder(160 + frames * 200);
        out.append("# Balance Point V3 diagnostic capture\n");
        out.append("# fixed_step_s,").append(FIXED_DT).append('\n');
        out.append("# frames,").append(frames).append('\n');
        for (int i = 0; i < capturedParameters.length; i++) {
            out.append("# parameter_")
                    .append(i).append(',')
                    .append(PhysicsTuning.PARAMETERS[i].label).append(',')
                    .append(capturedParameters[i]).append('\n');
        }
        out.append("frame,time_s");
        for (String column : COLUMNS) out.append(',').append(column);
        out.append('\n');
        for (int f = 0; f < frames; f++) {
            out.append(f).append(',').append(f * FIXED_DT);
            for (float[] series : values) out.append(',').append(series[f]);
            out.append('\n');
        }
        return out.toString();
    }
}
