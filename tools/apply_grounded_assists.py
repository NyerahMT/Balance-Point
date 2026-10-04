#!/usr/bin/env python3
"""Add explicit grounded rideability assists to Physics V2.

Airborne dynamics are intentionally untouched: when the rear tire leaves the ground,
the anti-wheelie torque trim disappears, and when both tires are off the ground the
rideability layer contributes zero chassis attitude torque.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
MARKER = "PHYSICS_V2_GROUNDED_ASSISTS_V1"

text = PLANT.read_text()
if MARKER in text:
    print("Grounded assists already applied")
    raise SystemExit(0)

old_constants = """    private static final float AERO_COEFF = 0.34f;
    private static final float ROLLING_COEFF = 0.017f;
"""
new_constants = """    private static final float AERO_COEFF = 0.34f;
    private static final float ROLLING_COEFF = 0.017f;

    // Explicit game-facing rideability layer. These terms exist only while grounded.
    private static final String PHYSICS_V2_GROUNDED_ASSISTS_V1 =
            "ground-roll-steer-wheelie-v1";
    private static final float ASSIST_MAX_LEAN = radians(24f);
    private static final float ASSIST_ROLL_KP = 150f;
    private static final float ASSIST_ROLL_KD = 48f;
    private static final float ASSIST_MAX_ROLL_TORQUE = 240f;
    private static final float ASSIST_STEER_DAMPING = 5.5f;
    private static final float ASSIST_WHEELIE_TARGET = radians(14f);
    private static final float ASSIST_WHEELIE_ONSET = radians(5f);
    private static final float ASSIST_WHEELIE_KP = 520f;
    private static final float ASSIST_WHEELIE_KD = 180f;
    private static final float ASSIST_MAX_WHEELIE_TORQUE = 1200f;
    private static final float ASSIST_POWER_CUT_START = radians(8f);
    private static final float ASSIST_POWER_CUT_FULL = radians(18f);
    private static final float ASSIST_POWER_CUT_MAX = 0.82f;
"""
if old_constants not in text:
    raise SystemExit("constant insertion anchor not found")
text = text.replace(old_constants, new_constants, 1)

old_steer = """        float steeringDampingTorque = -STEER_DAMPING * steerRate;
        float steerTorque = riderSteerTorque + frontTireForce.mz
                + steeringDampingTorque;
"""
new_steer = """        float groundedSteerDamping = airborne() ? 0f : ASSIST_STEER_DAMPING;
        float steeringDampingTorque = -(STEER_DAMPING + groundedSteerDamping) * steerRate;
        float steerTorque = riderSteerTorque + frontTireForce.mz
                + steeringDampingTorque;
"""
if old_steer not in text:
    raise SystemExit("steering anchor not found")
text = text.replace(old_steer, new_steer, 1)

old_drive = """        float rearDriveTorque = powertrain.step(rearWheelOmega, input.throttle, clutchCommand, dt);
        float brakeTorque = clamp(input.rearBrake, 0f, 1f) * MAX_REAR_BRAKE_TORQUE;
"""
new_drive = """        float assistedThrottle = input.throttle;
        if (rearGrounded()) {
            float pitchCut = smoothstep(
                    ASSIST_POWER_CUT_START,
                    ASSIST_POWER_CUT_FULL,
                    pitch());
            float noseUpRate = Math.max(0f, -pitchRate());
            float rateCut = smoothstep(0.45f, 1.8f, noseUpRate);
            float cut = Math.max(pitchCut, rateCut) * ASSIST_POWER_CUT_MAX;
            assistedThrottle *= 1f - cut;
        }
        float rearDriveTorque = powertrain.step(
                rearWheelOmega,
                assistedThrottle,
                clutchCommand,
                dt);
        float brakeTorque = clamp(input.rearBrake, 0f, 1f) * MAX_REAR_BRAKE_TORQUE;
"""
if old_drive not in text:
    raise SystemExit("drive insertion anchor not found")
text = text.replace(old_drive, new_drive, 1)

old_moment_anchor = """        float engineReaction = -0.0065f * powertrain.getEngineAlpha();
        totalMomentWorld.x += right.x * engineReaction;
        totalMomentWorld.y += right.y * engineReaction;
        totalMomentWorld.z += right.z * engineReaction;

        vx += totalForce.x / SPRUNG_MASS * dt;
"""
new_moment_anchor = """        float engineReaction = -0.0065f * powertrain.getEngineAlpha();
        totalMomentWorld.x += right.x * engineReaction;
        totalMomentWorld.y += right.y * engineReaction;
        totalMomentWorld.z += right.z * engineReaction;

        // Grounded rideability assists. No part of this block runs fully airborne.
        if (!airborne()) {
            float speedAbs = Math.abs(forwardSpeedStart);
            float leanAuthority = smoothstep(2.0f, 8.0f, speedAbs);
            float targetRoll = -clamp(input.steer, -1f, 1f)
                    * ASSIST_MAX_LEAN * leanAuthority;
            float rollTorque = ASSIST_ROLL_KP * (targetRoll - roll())
                    - ASSIST_ROLL_KD * rollRate();
            rollTorque = clamp(
                    rollTorque,
                    -ASSIST_MAX_ROLL_TORQUE,
                    ASSIST_MAX_ROLL_TORQUE);
            totalMomentWorld.x += forward.x * rollTorque;
            totalMomentWorld.y += forward.y * rollTorque;
            totalMomentWorld.z += forward.z * rollTorque;

            if (rearGrounded() && pitch() > ASSIST_WHEELIE_ONSET) {
                // Positive body-X torque pitches the nose down in this coordinate system.
                float noseUpRate = Math.max(0f, -pitchRate());
                float wheelieTorque = ASSIST_WHEELIE_KP
                        * (pitch() - ASSIST_WHEELIE_TARGET)
                        + ASSIST_WHEELIE_KD * noseUpRate;
                wheelieTorque = clamp(
                        wheelieTorque,
                        0f,
                        ASSIST_MAX_WHEELIE_TORQUE);
                totalMomentWorld.x += right.x * wheelieTorque;
                totalMomentWorld.y += right.y * wheelieTorque;
                totalMomentWorld.z += right.z * wheelieTorque;
            }
        }

        vx += totalForce.x / SPRUNG_MASS * dt;
"""
if old_moment_anchor not in text:
    raise SystemExit("moment insertion anchor not found")
text = text.replace(old_moment_anchor, new_moment_anchor, 1)

helper_anchor = """    private void basis() {
"""
helper = """    private static float smoothstep(float lo, float hi, float x) {
        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

"""
if helper_anchor not in text:
    raise SystemExit("helper insertion anchor not found")
text = text.replace(helper_anchor, helper + helper_anchor, 1)

PLANT.write_text(text)
print("Grounded assists applied; airborne attitude remains untouched")
