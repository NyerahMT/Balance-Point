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

    old = """    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;
    private static final float REAR_STATIC_COMPRESSION = 0.09240f;
    private static final float AUTO_BRACE_PER_G = 0.85f;
    private static final float AUTO_BRACE_MAX = 0.80f;
    private static final String PHYSICS_V2_RIDEABILITY_PASS_2 = "multibody-rider-v2";
"""
    new = """    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;
    private static final float REAR_STATIC_COMPRESSION = 0.09240f;
    // The outer game loop is 120 Hz, but clutch/contact/unsprung modes are faster.
    // Keep integration at or below 1/480 s to stop those modes aliasing into chassis motion.
    private static final float MAX_INTERNAL_DT = 1f / 480f;
    private static final String PHYSICS_V2_STABILITY_PASS_1 =
            "substep-feedback-cleanup-v1";
"""
    text = replace_once(text, old, new, "stability constants")
    text = text.replace("    private float lastLongitudinalAcceleration;\n", "")
    text = text.replace("        lastLongitudinalAcceleration = 0f;\n", "")

    old = """    void step(Terrain terrain, float dt) {
        if (terrain == null || dt <= 0f) return;
        basis();
"""
    new = """    void step(Terrain terrain, float dt) {
        if (terrain == null || dt <= 0f) return;
        int substeps = Math.max(1, (int)Math.ceil(dt / MAX_INTERNAL_DT));
        float h = dt / substeps;
        for (int i = 0; i < substeps; i++) substep(terrain, h);
    }

    private void substep(Terrain terrain, float dt) {
        basis();
"""
    text = replace_once(text, old, new, "substep wrapper")

    old = """        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;
        float autoBrace = clamp(
                lastLongitudinalAcceleration / G * AUTO_BRACE_PER_G,
                0f,
                AUTO_BRACE_MAX);
        float riderCommand = clamp(input.riderForeAft + autoBrace, -1f, 1f);
        riderLongitudinal.step(
                riderCommand,
                lastLongitudinalAcceleration,
                airborne(),
                dt);
"""
    new = """        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;
        // Use explicit rider input only. The former acceleration-derived brace fed body
        // rotation back into CG location and could create a self-exciting geometry loop.
        riderLongitudinal.step(
                clamp(input.riderForeAft, -1f, 1f),
                0f,
                airborne(),
                dt);
"""
    text = replace_once(text, old, new, "remove acceleration rider feedback")

    old = """        float steerTorque = riderSteer * MAX_STEER_TORQUE
                + frontTireForce.mz - STEER_DAMPING * steerRate;
        steerRate += steerTorque / STEER_INERTIA * dt;
"""
    new = """        float riderSteerTorque = riderSteer * MAX_STEER_TORQUE;
        float steeringDampingTorque = -STEER_DAMPING * steerRate;
        float steerTorque = riderSteerTorque + frontTireForce.mz
                + steeringDampingTorque;
        steerRate += steerTorque / STEER_INERTIA * dt;
"""
    text = replace_once(text, old, new, "steering torque decomposition")

    old = """        addContactToRigidBody(
                rearContact,
                rearTireForce,
                rearSpring,
                totalForce,
                totalMomentWorld);
        addContactToRigidBody(
                frontContact,
                frontTireForce,
                frontSpring,
                totalForce,
                totalMomentWorld);
"""
    new = """        addContactToRigidBody(
                rearContact,
                rearTireForce,
                rearSpring,
                false,
                totalForce,
                totalMomentWorld);
        addContactToRigidBody(
                frontContact,
                frontTireForce,
                frontSpring,
                true,
                totalForce,
                totalMomentWorld);
"""
    text = replace_once(text, old, new, "front/rear contact ownership")

    old = """        float riderPitchTorque = riderLongitudinal.pitchReactionTorque(
                airborne(),
                frontGrounded());
        totalMomentWorld.x += right.x * riderPitchTorque;
        totalMomentWorld.y += right.y * riderPitchTorque;
        totalMomentWorld.z += right.z * riderPitchTorque;

"""
    text = replace_once(text, old, "", "remove legacy rider pitch torque")

    old = """        // Wheel gyroscopic bearing reactions. Spin acceleration remains handled by hub torque.
        float cs = (float)Math.cos(steerAngle);
        float ss = (float)Math.sin(steerAngle);
        float frontH = FRONT_WHEEL_INERTIA * frontWheelOmega;
        float rearH = REAR_WHEEL_INERTIA * rearWheelOmega;
        float hx = rearH + frontH * cs;
        float hz = -frontH * ss;
        torqueBody.x += -wy * hz - steerRate * hz;
        torqueBody.y += -(wz * hx - wx * hz);
        torqueBody.z += wy * hx + steerRate * frontH * cs;

"""
    new = """        // The former hand-expanded gyro cross terms were not independently validated.
        // Omit them until wheel orientation/Jacobian coupling has conservation tests.
        // Wheel spin inertia itself remains physical.

"""
    text = replace_once(text, old, new, "remove unvalidated gyro coupling")

    old = """        integrateQuaternion(dt);
        basis();
        float forwardSpeedEnd = forward.x * vx + forward.y * vy + forward.z * vz;
        lastLongitudinalAcceleration = (forwardSpeedEnd - forwardSpeedStart)
                / Math.max(dt, 0.0001f);

        if (!finite()) reset(terrain);
"""
    new = """        integrateQuaternion(dt);

        if (!finite()) reset(terrain);
"""
    text = replace_once(text, old, new, "remove projected acceleration feedback")

    old = """            PhysicsV2Tire.Force f,
            float suspensionForce,
            Vec forceSum,
            Vec momentSum) {
"""
    new = """            PhysicsV2Tire.Force f,
            float suspensionForce,
            boolean isFront,
            Vec forceSum,
            Vec momentSum) {
"""
    text = replace_once(text, old, new, "contact signature")

    old = """        momentSum.add(m);
        momentSum.x += c.normal.x * f.mz + c.wheelForward.x * f.mx;
        momentSum.y += c.normal.y * f.mz + c.wheelForward.y * f.mx;
        momentSum.z += c.normal.z * f.mz + c.wheelForward.z * f.mx;
"""
    new = """        momentSum.add(m);
        // Front aligning moment belongs to the steering DOF. Adding it here too
        // double-counts the same external tire moment.
        if (!isFront) {
            momentSum.x += c.normal.x * f.mz;
            momentSum.y += c.normal.y * f.mz;
            momentSum.z += c.normal.z * f.mz;
        }
        momentSum.x += c.wheelForward.x * f.mx;
        momentSum.y += c.wheelForward.y * f.mx;
        momentSum.z += c.wheelForward.z * f.mx;
"""
    text = replace_once(text, old, new, "front aligning moment ownership")

    old = """        totalMomentWorld.set(0f, 0f, 0f);
"""
    new = """        totalMomentWorld.set(0f, 0f, 0f);
        // Rider bar torque and steering-joint damping are internal reactions.
        float steeringInternalReaction =
                -(riderSteerTorque + steeringDampingTorque);
        totalMomentWorld.x += up.x * steeringInternalReaction;
        totalMomentWorld.y += up.y * steeringInternalReaction;
        totalMomentWorld.z += up.z * steeringInternalReaction;
"""
    text = replace_once(text, old, new, "steering chassis reaction")

    old = """                && finite(frontWheelOmega) && finite(rearWheelOmega)
                && finite(lastLongitudinalAcceleration);"""
    new = """                && finite(frontWheelOmega) && finite(rearWheelOmega);"""
    text = replace_once(text, old, new, "finite state")

    PLANT.write_text(text, encoding="utf-8")


def test_source() -> str:
    return """package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2StabilityTest {
    private static final class Flat implements PhysicsV2Plant.Terrain {
        @Override public float height(float x, float z) { return 0f; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void timestepHalvingConvergesForStraightLaunch() {
        Snapshot a = run(1f / 120f, false);
        Snapshot b = run(1f / 240f, false);
        assertTrue(
                "speed delta=" + Math.abs(a.speed - b.speed),
                Math.abs(a.speed - b.speed) < 0.35f);
        assertTrue(
                "pitch delta=" + Math.abs(a.pitch - b.pitch),
                Math.abs(a.pitch - b.pitch) < radians(1.5f));
        assertTrue(
                "height delta=" + Math.abs(a.y - b.y),
                Math.abs(a.y - b.y) < 0.02f);
    }

    @Test
    public void timestepHalvingConvergesForMildTurn() {
        Snapshot a = run(1f / 120f, true);
        Snapshot b = run(1f / 240f, true);
        assertTrue(
                "roll delta=" + Math.abs(a.roll - b.roll),
                Math.abs(a.roll - b.roll) < radians(3f));
        assertTrue(
                "yaw delta=" + Math.abs(a.yaw - b.yaw),
                angleDelta(a.yaw, b.yaw) < radians(4f));
    }

    @Test
    public void scriptedRideDoesNotDevelopSpontaneousAngularExplosion() {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        float dt = 1f / 120f;
        float maxPitchRate = 0f;
        float maxRollRate = 0f;
        for (int i = 0; i < 1200; i++) {
            float t = i * dt;
            p.input().throttle =
                    t < 2f ? 0.38f : (t < 5f ? 0.55f : 0.28f);
            p.input().steer =
                    t < 2.5f ? 0f : (t < 4f ? 0.22f :
                            (t < 5.5f ? -0.18f : 0f));
            p.input().riderForeAft = t < 3f ? 0.10f : 0f;
            p.step(terrain, dt);
            maxPitchRate = Math.max(maxPitchRate, Math.abs(p.pitchRate()));
            maxRollRate = Math.max(maxRollRate, Math.abs(p.rollRate()));
            assertTrue(
                    "non-finite orientation at t=" + t,
                    Float.isFinite(p.pitch())
                            && Float.isFinite(p.roll())
                            && Float.isFinite(p.yaw()));
        }
        assertTrue("pitch rate=" + maxPitchRate, maxPitchRate < 5f);
        assertTrue("roll rate=" + maxRollRate, maxRollRate < 7f);
    }

    private Snapshot run(float dt, boolean turn) {
        Flat terrain = new Flat();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        int count = Math.round(5f / dt);
        for (int i = 0; i < count; i++) {
            float t = i * dt;
            p.input().throttle = t < 1f ? 0.25f : 0.48f;
            p.input().steer =
                    turn && t > 2.0f && t < 4.0f ? 0.20f : 0f;
            p.step(terrain, dt);
        }
        Snapshot s = new Snapshot();
        s.speed = p.speed();
        s.pitch = p.pitch();
        s.roll = p.roll();
        s.yaw = p.yaw();
        s.y = p.y();
        return s;
    }

    private static float angleDelta(float a, float b) {
        float d = Math.abs(a - b);
        while (d > Math.PI * 2f) d -= Math.PI * 2f;
        return d > Math.PI ? (float)(Math.PI * 2f - d) : d;
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

    private static final class Snapshot {
        float speed, pitch, roll, yaw, y;
    }
}
"""


def main() -> None:
    patch_plant()
    TEST.write_text(test_source(), encoding="utf-8")
    print("Physics V2 stability pass applied")


if __name__ == "__main__":
    main()

# Trigger marker: stability-migration-3
