#!/usr/bin/env python3
"""Stabilize Physics V2 numerics and remove unvalidated feedback paths."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
TEST = ROOT / "core/src/test/java/com/nyerahworks/balancepoint/PhysicsV2StabilityTest.java"
MARKER = "PHYSICS_V2_STABILITY_PASS_1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_plant() -> None:
    text = PLANT.read_text(encoding="utf-8")
    if MARKER in text:
        return

    text = replace_once(
        text,
        '''    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;\n    private static final float REAR_STATIC_COMPRESSION = 0.09240f;\n    private static final float AUTO_BRACE_PER_G = 0.85f;\n    private static final float AUTO_BRACE_MAX = 0.80f;\n    private static final String PHYSICS_V2_RIDEABILITY_PASS_2 = "multibody-rider-v2";\n''',
        '''    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;\n    private static final float REAR_STATIC_COMPRESSION = 0.09240f;\n    // The game calls the plant at 120 Hz, but clutch/contact/unsprung modes are faster.\n    // Keep the integrator step at or below 1/480 s so those modes do not alias into chassis motion.\n    private static final float MAX_INTERNAL_DT = 1f / 480f;\n    private static final String PHYSICS_V2_STABILITY_PASS_1 = "substep-feedback-cleanup-v1";\n''',
        "stability constants")

    text = text.replace('    private float lastLongitudinalAcceleration;\n', '')
    text = text.replace('        lastLongitudinalAcceleration = 0f;\n', '')

    old_step = '''    void step(Terrain terrain, float dt) {\n        if (terrain == null || dt <= 0f) return;\n        basis();\n'''
    new_step = '''    void step(Terrain terrain, float dt) {\n        if (terrain == null || dt <= 0f) return;\n        int substeps = Math.max(1, (int)Math.ceil(dt / MAX_INTERNAL_DT));\n        float h = dt / substeps;\n        for (int i = 0; i < substeps; i++) substep(terrain, h);\n    }\n\n    private void substep(Terrain terrain, float dt) {\n        basis();\n'''
    text = replace_once(text, old_step, new_step, "substep wrapper")

    old_rider = '''        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;\n        float autoBrace = clamp(\n                lastLongitudinalAcceleration / G * AUTO_BRACE_PER_G,\n                0f,\n                AUTO_BRACE_MAX);\n        float riderCommand = clamp(input.riderForeAft + autoBrace, -1f, 1f);\n        riderLongitudinal.step(\n                riderCommand,\n                lastLongitudinalAcceleration,\n                airborne(),\n                dt);\n'''
    new_rider = '''        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;\n        // Rider position follows only explicit player input in this plant pass. The previous\n        // acceleration-derived auto-brace fed body rotation back into CG location and could create\n        // a self-exciting loop. Proper inertial rider coupling returns with a validated multibody.\n        riderLongitudinal.step(\n                clamp(input.riderForeAft, -1f, 1f),\n                0f,\n                airborne(),\n                dt);\n'''
    text = replace_once(text, old_rider, new_rider, "remove acceleration rider feedback")

    old_steer = '''        float steerTorque = riderSteer * MAX_STEER_TORQUE\n                + frontTireForce.mz - STEER_DAMPING * steerRate;\n        steerRate += steerTorque / STEER_INERTIA * dt;\n'''
    new_steer = '''        float riderSteerTorque = riderSteer * MAX_STEER_TORQUE;\n        float steeringDampingTorque = -STEER_DAMPING * steerRate;\n        float steerTorque = riderSteerTorque + frontTireForce.mz + steeringDampingTorque;\n        steerRate += steerTorque / STEER_INERTIA * dt;\n'''
    text = replace_once(text, old_steer, new_steer, "steering torque decomposition")

    text = replace_once(
        text,
        '''        addContactToRigidBody(\n                rearContact,\n                rearTireForce,\n                rearSpring,\n                totalForce,\n                totalMomentWorld);\n        addContactToRigidBody(\n                frontContact,\n                frontTireForce,\n                frontSpring,\n                totalForce,\n                totalMomentWorld);\n''',
        '''        addContactToRigidBody(\n                rearContact,\n                rearTireForce,\n                rearSpring,\n                false,\n                totalForce,\n                totalMomentWorld);\n        addContactToRigidBody(\n                frontContact,\n                frontTireForce,\n                frontSpring,\n                true,\n                totalForce,\n                totalMomentWorld);\n''',
        "front/rear contact ownership")

    old_rider_pitch = '''        float riderPitchTorque = riderLongitudinal.pitchReactionTorque(\n                airborne(),\n                frontGrounded());\n        totalMomentWorld.x += right.x * riderPitchTorque;\n        totalMomentWorld.y += right.y * riderPitchTorque;\n        totalMomentWorld.z += right.z * riderPitchTorque;\n\n'''
    text = replace_once(text, old_rider_pitch, '', "remove legacy rider pitch torque")

    old_gyro = '''        // Wheel gyroscopic bearing reactions. Spin acceleration remains handled by hub torque.\n        float cs = (float)Math.cos(steerAngle);\n        float ss = (float)Math.sin(steerAngle);\n        float frontH = FRONT_WHEEL_INERTIA * frontWheelOmega;\n        float rearH = REAR_WHEEL_INERTIA * rearWheelOmega;\n        float hx = rearH + frontH * cs;\n        float hz = -frontH * ss;\n        torqueBody.x += -wy * hz - steerRate * hz;\n        torqueBody.y += -(wz * hx - wx * hz);\n        torqueBody.z += wy * hx + steerRate * frontH * cs;\n\n'''
    new_gyro = '''        // Wheel gyroscopic chassis coupling is intentionally omitted in this stabilization pass.\n        // The previous hand-expanded cross terms were not independently validated and could inject\n        // energy. Wheel spin inertia remains physical; gyro coupling returns with a tested wheel\n        // orientation/Jacobian model rather than another sign-sensitive shortcut.\n\n'''
    text = replace_once(text, old_gyro, new_gyro, "remove unvalidated gyro coupling")

    old_end = '''        integrateQuaternion(dt);\n        basis();\n        float forwardSpeedEnd = forward.x * vx + forward.y * vy + forward.z * vz;\n        lastLongitudinalAcceleration = (forwardSpeedEnd - forwardSpeedStart)\n                / Math.max(dt, 0.0001f);\n\n        if (!finite()) reset(terrain);\n'''
    new_end = '''        integrateQuaternion(dt);\n\n        if (!finite()) reset(terrain);\n'''
    text = replace_once(text, old_end, new_end, "remove projected acceleration feedback")

    old_sig = '''            PhysicsV2Tire.Force f,\n            float suspensionForce,\n            Vec forceSum,\n            Vec momentSum) {\n'''
    new_sig = '''            PhysicsV2Tire.Force f,\n            float suspensionForce,\n            boolean isFront,\n            Vec forceSum,\n            Vec momentSum) {\n'''
    text = replace_once(text, old_sig, new_sig, "contact signature")

    old_moments = '''        momentSum.add(m);\n        momentSum.x += c.normal.x * f.mz + c.wheelForward.x * f.mx;\n        momentSum.y += c.normal.y * f.mz + c.wheelForward.y * f.mx;\n        momentSum.z += c.normal.z * f.mz + c.wheelForward.z * f.mx;\n'''
    new_moments = '''        momentSum.add(m);\n        // Front aligning moment belongs to the steering DOF. Applying it here as well would\n        // double-count the same external tire moment. Rear aligning moment acts on the chassis.\n        if (!isFront) {\n            momentSum.x += c.normal.x * f.mz;\n            momentSum.y += c.normal.y * f.mz;\n            momentSum.z += c.normal.z * f.mz;\n        }\n        momentSum.x += c.wheelForward.x * f.mx;\n        momentSum.y += c.wheelForward.y * f.mx;\n        momentSum.z += c.wheelForward.z * f.mx;\n'''
    text = replace_once(text, old_moments, new_moments, "front aligning moment ownership")

    # Internal rider/damper steering torques react on the chassis. The front-tire aligning moment
    # is external and remains on the steering assembly, so it is excluded from this reaction.
    reaction_anchor = '''        totalMomentWorld.set(0f, 0f, 0f);\n'''
    reaction = '''        totalMomentWorld.set(0f, 0f, 0f);\n        float steeringInternalReaction = -(riderSteerTorque + steeringDampingTorque);\n        totalMomentWorld.x += up.x * steeringInternalReaction;\n        totalMomentWorld.y += up.y * steeringInternalReaction;\n        totalMomentWorld.z += up.z * steeringInternalReaction;\n'''
    text = replace_once(text, reaction_anchor, reaction, "steering chassis reaction")

    text = text.replace(
        '''                && finite(frontWheelOmega) && finite(rearWheelOmega)\n                && finite(lastLongitudinalAcceleration);''',
        '''                && finite(frontWheelOmega) && finite(rearWheelOmega);''')

    PLANT.write_text(text, encoding="utf-8")


def test_source() -> str:
    return '''package com.nyerahworks.balancepoint;\n\nimport static org.junit.Assert.assertTrue;\n\nimport org.junit.Test;\n\npublic final class PhysicsV2StabilityTest {\n    private static final class Flat implements PhysicsV2Plant.Terrain {\n        @Override public float height(float x, float z) { return 0f; }\n        @Override public float slopeX(float x, float z) { return 0f; }\n        @Override public float slopeZ(float x, float z) { return 0f; }\n        @Override public float traction(float x, float z) { return 1f; }\n    }\n\n    @Test\n    public void timestepHalvingConvergesForStraightLaunch() {\n        Snapshot a = run(1f / 120f, false);\n        Snapshot b = run(1f / 240f, false);\n        assertTrue("speed delta=" + Math.abs(a.speed - b.speed),\n                Math.abs(a.speed - b.speed) < 0.35f);\n        assertTrue("pitch delta=" + Math.abs(a.pitch - b.pitch),\n                Math.abs(a.pitch - b.pitch) < radians(1.5f));\n        assertTrue("height delta=" + Math.abs(a.y - b.y),\n                Math.abs(a.y - b.y) < 0.02f);\n    }\n\n    @Test\n    public void timestepHalvingConvergesForMildTurn() {\n        Snapshot a = run(1f / 120f, true);\n        Snapshot b = run(1f / 240f, true);\n        assertTrue("roll delta=" + Math.abs(a.roll - b.roll),\n                Math.abs(a.roll - b.roll) < radians(3f));\n        assertTrue("yaw delta=" + Math.abs(a.yaw - b.yaw),\n                angleDelta(a.yaw, b.yaw) < radians(4f));\n    }\n\n    @Test\n    public void scriptedRideDoesNotDevelopSpontaneousAngularExplosion() {\n        Flat terrain = new Flat();\n        PhysicsV2Plant p = new PhysicsV2Plant();\n        p.reset(terrain);\n        float dt = 1f / 120f;\n        float maxPitchRate = 0f;\n        float maxRollRate = 0f;\n        for (int i = 0; i < 1200; i++) {\n            float t = i * dt;\n            p.input().throttle = t < 2f ? 0.38f : (t < 5f ? 0.55f : 0.28f);\n            p.input().steer = t < 2.5f ? 0f : (t < 4f ? 0.22f : (t < 5.5f ? -0.18f : 0f));\n            p.input().riderForeAft = t < 3f ? 0.10f : 0f;\n            p.step(terrain, dt);\n            maxPitchRate = Math.max(maxPitchRate, Math.abs(p.pitchRate()));\n            maxRollRate = Math.max(maxRollRate, Math.abs(p.rollRate()));\n            assertTrue("non-finite orientation at t=" + t,\n                    Float.isFinite(p.pitch()) && Float.isFinite(p.roll()) && Float.isFinite(p.yaw()));\n        }\n        assertTrue("pitch rate=" + maxPitchRate, maxPitchRate < 5f);\n        assertTrue("roll rate=" + maxRollRate, maxRollRate < 7f);\n    }\n\n    private Snapshot run(float dt, boolean turn) {\n        Flat terrain = new Flat();\n        PhysicsV2Plant p = new PhysicsV2Plant();\n        p.reset(terrain);\n        int count = Math.round(5f / dt);\n        for (int i = 0; i < count; i++) {\n            float t = i * dt;\n            p.input().throttle = t < 1f ? 0.25f : 0.48f;\n            p.input().steer = turn && t > 2.0f && t < 4.0f ? 0.20f : 0f;\n            p.step(terrain, dt);\n        }\n        Snapshot s = new Snapshot();\n        s.speed = p.speed();\n        s.pitch = p.pitch();\n        s.roll = p.roll();\n        s.yaw = p.yaw();\n        s.y = p.y();\n        return s;\n    }\n\n    private static float angleDelta(float a, float b) {\n        float d = Math.abs(a - b);\n        while (d > Math.PI * 2f) d -= Math.PI * 2f;\n        return d > Math.PI ? (float)(Math.PI * 2f - d) : d;\n    }\n\n    private static float radians(float degrees) {\n        return degrees * (float)Math.PI / 180f;\n    }\n\n    private static final class Snapshot {\n        float speed, pitch, roll, yaw, y;\n    }\n}\n'''


def main() -> None:
    patch_plant()
    TEST.write_text(test_source(), encoding="utf-8")
    print("Physics V2 stability pass applied")


if __name__ == "__main__":
    main()
