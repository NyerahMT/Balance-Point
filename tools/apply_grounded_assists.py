#!/usr/bin/env python3
"""Add explicit grounded rideability assists to Physics V2.

Airborne dynamics are intentionally untouched: when both wheels are off the ground,
this layer contributes zero chassis attitude torque.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
MARKER = "PHYSICS_V2_GROUNDED_ASSISTS_V1"

text = PLANT.read_text()
if MARKER in text:
    print("Grounded assists already applied")
    raise SystemExit(0)

old_constants = """    private static final float AERO_COEFF = 0.34f;\n    private static final float ROLLING_COEFF = 0.017f;\n"""
new_constants = """    private static final float AERO_COEFF = 0.34f;\n    private static final float ROLLING_COEFF = 0.017f;\n\n    // Explicit game-facing rideability layer. These torques exist only while grounded.\n    // They are intentionally separate from the motorcycle plant so they can be removed later.\n    private static final String PHYSICS_V2_GROUNDED_ASSISTS_V1 =\n            \"ground-roll-steer-wheelie-v1\";\n    private static final float ASSIST_MAX_LEAN = radians(24f);\n    private static final float ASSIST_ROLL_KP = 150f;\n    private static final float ASSIST_ROLL_KD = 48f;\n    private static final float ASSIST_MAX_ROLL_TORQUE = 240f;\n    private static final float ASSIST_STEER_DAMPING = 5.5f;\n    private static final float ASSIST_WHEELIE_TARGET = radians(14f);\n    private static final float ASSIST_WHEELIE_ONSET = radians(10f);\n    private static final float ASSIST_WHEELIE_KP = 260f;\n    private static final float ASSIST_WHEELIE_KD = 72f;\n    private static final float ASSIST_MAX_WHEELIE_TORQUE = 320f;\n"""
if old_constants not in text:
    raise SystemExit("constant insertion anchor not found")
text = text.replace(old_constants, new_constants, 1)

old_steer = """        float steeringDampingTorque = -STEER_DAMPING * steerRate;\n        float steerTorque = riderSteerTorque + frontTireForce.mz\n                + steeringDampingTorque;\n"""
new_steer = """        float groundedSteerDamping = airborne() ? 0f : ASSIST_STEER_DAMPING;\n        float steeringDampingTorque = -(STEER_DAMPING + groundedSteerDamping) * steerRate;\n        float steerTorque = riderSteerTorque + frontTireForce.mz\n                + steeringDampingTorque;\n"""
if old_steer not in text:
    raise SystemExit("steering anchor not found")
text = text.replace(old_steer, new_steer, 1)

old_moment_anchor = """        float engineReaction = -0.0065f * powertrain.getEngineAlpha();\n        totalMomentWorld.x += right.x * engineReaction;\n        totalMomentWorld.y += right.y * engineReaction;\n        totalMomentWorld.z += right.z * engineReaction;\n\n        vx += totalForce.x / SPRUNG_MASS * dt;\n"""
new_moment_anchor = """        float engineReaction = -0.0065f * powertrain.getEngineAlpha();\n        totalMomentWorld.x += right.x * engineReaction;\n        totalMomentWorld.y += right.y * engineReaction;\n        totalMomentWorld.z += right.z * engineReaction;\n\n        // Grounded rideability assists. No part of this block runs fully airborne.\n        if (!airborne()) {\n            float speedAbs = Math.abs(forwardSpeedStart);\n            float leanAuthority = smoothstep(2.0f, 8.0f, speedAbs);\n            float targetRoll = -clamp(input.steer, -1f, 1f)\n                    * ASSIST_MAX_LEAN * leanAuthority;\n            float rollTorque = ASSIST_ROLL_KP * (targetRoll - roll())\n                    - ASSIST_ROLL_KD * rollRate();\n            rollTorque = clamp(rollTorque,\n                    -ASSIST_MAX_ROLL_TORQUE, ASSIST_MAX_ROLL_TORQUE);\n            totalMomentWorld.x += forward.x * rollTorque;\n            totalMomentWorld.y += forward.y * rollTorque;\n            totalMomentWorld.z += forward.z * rollTorque;\n\n            if (rearGrounded() && !frontGrounded() && pitch() > ASSIST_WHEELIE_ONSET) {\n                float wheelieTorque = ASSIST_WHEELIE_KP\n                        * (ASSIST_WHEELIE_TARGET - pitch())\n                        - ASSIST_WHEELIE_KD * Math.max(0f, pitchRate());\n                wheelieTorque = clamp(wheelieTorque,\n                        -ASSIST_MAX_WHEELIE_TORQUE, 0f);\n                totalMomentWorld.x += right.x * wheelieTorque;\n                totalMomentWorld.y += right.y * wheelieTorque;\n                totalMomentWorld.z += right.z * wheelieTorque;\n            }\n        }\n\n        vx += totalForce.x / SPRUNG_MASS * dt;\n"""
if old_moment_anchor not in text:
    raise SystemExit("moment insertion anchor not found")
text = text.replace(old_moment_anchor, new_moment_anchor, 1)

helper_anchor = """    private void basis() {\n"""
helper = """    private static float smoothstep(float lo, float hi, float x) {\n        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);\n        return t * t * (3f - 2f * t);\n    }\n\n    private static float radians(float degrees) {\n        return degrees * (float)Math.PI / 180f;\n    }\n\n"""
if helper_anchor not in text:
    raise SystemExit("helper insertion anchor not found")
text = text.replace(helper_anchor, helper + helper_anchor, 1)

PLANT.write_text(text)
print("Grounded assists applied; airborne attitude remains untouched")
