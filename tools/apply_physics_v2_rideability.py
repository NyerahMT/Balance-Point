#!/usr/bin/env python3
"""Apply the first Physics V2 rideability correction pass.

This pass fixes the wheel/chassis force path, couples suspension spring/damper force into the
sprung chassis, and adds an explicit virtual-rider steering controller. It intentionally does
not add wheelie, anti-fall, jump, or airborne attitude clamps.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
CONTROLLER = ROOT / (
    "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2RiderController.java"
)
TEST = ROOT / (
    "core/src/test/java/com/nyerahworks/balancepoint/PhysicsV2RideabilityTest.java"
)
MARKER = "PHYSICS_V2_RIDEABILITY_PASS_1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def controller_source() -> str:
    return '''package com.nyerahworks.balancepoint;

/**
 * Explicit virtual-rider steering controller for the phone input layer.
 *
 * The plant remains force/torque based. The controller converts a player's desired turn into
 * physically applied handlebar torque. At walking speed it steers into the turn. At road speed
 * it countersteers to establish a lean, then converges toward the geometric steer angle needed
 * to hold that lean. No roll clamp or direct attitude correction is used.
 */
final class PhysicsV2RiderController {
    private static final float G = 9.80665f;
    private static final float WHEELBASE = 1.48082f;
    private static final float MAX_LEAN = radians(32f);
    private static final float MAX_HANDLEBAR_TORQUE = 7.0f;
    private static final float LOW_SPEED_TORQUE = 3.2f;
    private static final float ROLL_KP = 12.0f;
    private static final float ROLL_KD = 3.5f;
    private static final float STEER_KP = 5.0f;
    private static final float MAX_TARGET_ROLL_RATE = 1.6f;

    private float filteredCommand;

    void reset() {
        filteredCommand = 0f;
    }

    float normalizedTorque(
            float command,
            float forwardSpeed,
            float roll,
            float rollRate,
            float steerAngle,
            float steerRate,
            float plantMaxTorque,
            float dt) {
        command = clamp(command, -1f, 1f);
        float blend = Math.min(1f, Math.max(0f, dt * 8f));
        filteredCommand += (command - filteredCommand) * blend;

        float speed = Math.abs(forwardSpeed);
        float highSpeedBlend = smoothstep(2.5f, 6.0f, speed);
        float leanAuthority = smoothstep(1.5f, 9.0f, speed);
        float shaped = filteredCommand * (0.72f + 0.28f * Math.abs(filteredCommand));

        // Positive control input means turn right. In this coordinate system right lean is
        // negative roll, so the high-speed phase first countersteers left.
        float targetRoll = -shaped * MAX_LEAN * leanAuthority;
        float rollError = targetRoll - roll;
        float targetRollRate = clamp(
                rollError * 3.8f,
                -MAX_TARGET_ROLL_RATE,
                MAX_TARGET_ROLL_RATE);

        float targetSteer = 0f;
        if (speed > 1.0f) {
            float required = -WHEELBASE * G * (float)Math.tan(targetRoll)
                    / Math.max(speed * speed, 1.0f);
            targetSteer = clamp(required, -radians(12f), radians(12f));
        }

        float highSpeedTorque = ROLL_KP * rollError
                + ROLL_KD * (targetRollRate - rollRate)
                + STEER_KP * (targetSteer - steerAngle);

        // At very low speed there is not enough lateral tire force for a useful countersteer
        // loop. The rider simply steers into the requested turn and damps the bar motion.
        float lowSpeedTorque = filteredCommand * LOW_SPEED_TORQUE
                - 2.0f * steerAngle
                - 0.30f * steerRate;

        float torque = lowSpeedTorque
                + (highSpeedTorque - lowSpeedTorque) * highSpeedBlend;
        torque = clamp(torque, -MAX_HANDLEBAR_TORQUE, MAX_HANDLEBAR_TORQUE);
        return torque / Math.max(plantMaxTorque, 0.001f);
    }

    private static float smoothstep(float lo, float hi, float x) {
        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
'''


def test_source() -> str:
    return '''package com.nyerahworks.balancepoint;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhysicsV2RideabilityTest {
    private static final float DT = 1f / 120f;

    private static final class FlatTerrain implements PhysicsV2Plant.Terrain {
        float height;

        @Override public float height(float x, float z) { return height; }
        @Override public float slopeX(float x, float z) { return 0f; }
        @Override public float slopeZ(float x, float z) { return 0f; }
        @Override public float traction(float x, float z) { return 1f; }
    }

    @Test
    public void moderateThrottleDoesNotCreateSpuriousWheelie() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 240; i++) p.step(terrain, DT);

        p.input().throttle = 0.62f;
        int frontOffSamples = 0;
        float maxPitch = 0f;
        for (int i = 0; i < 600; i++) {
            p.step(terrain, DT);
            if (!p.frontGrounded() && p.rearGrounded()) frontOffSamples++;
            maxPitch = Math.max(maxPitch, Math.abs(p.pitch()));
        }

        assertTrue("front-off samples=" + frontOffSamples, frontOffSamples < 36);
        assertTrue("max pitch=" + maxPitch, maxPitch < radians(12f));
    }

    @Test
    public void turnCommandIsRollRegulatedInsteadOfFallingOver() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        p.input().throttle = 0.45f;
        for (int i = 0; i < 720; i++) p.step(terrain, DT);

        p.input().throttle = 0.20f;
        p.input().steer = 0.42f;
        float maxRoll = 0f;
        for (int i = 0; i < 300; i++) {
            p.step(terrain, DT);
            maxRoll = Math.max(maxRoll, Math.abs(p.roll()));
        }
        assertTrue("max roll=" + maxRoll, maxRoll < radians(48f));

        p.input().steer = 0f;
        for (int i = 0; i < 300; i++) p.step(terrain, DT);
        assertTrue("release roll=" + p.roll(), Math.abs(p.roll()) < radians(24f));
    }

    @Test
    public void suspensionHeaveSettlesAfterRoadStep() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant p = new PhysicsV2Plant();
        p.reset(terrain);
        for (int i = 0; i < 480; i++) p.step(terrain, DT);

        terrain.height = 0.06f;
        for (int i = 0; i < 720; i++) p.step(terrain, DT);

        assertTrue("front compression=" + p.frontCompression(),
                Math.abs(p.frontCompression() - 0.08035f) < 0.025f);
        assertTrue("rear compression=" + p.rearCompression(),
                Math.abs(p.rearCompression() - 0.09240f) < 0.025f);
        assertTrue("vertical velocity=" + p.verticalVelocity(),
                Math.abs(p.verticalVelocity()) < 0.20f);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }
}
'''


def patch_plant() -> None:
    text = PLANT.read_text(encoding="utf-8")
    if MARKER in text:
        return

    old_constants = '''    private static final float G = 9.80665f;
    private static final float MASS = 198.0f;
    private static final float WHEELBASE = 1.48082f;
    private static final float CG_FORWARD = 0.7222f;
    // Bike-only Honda prior plus fixed first-pass rider mass. Gate E will replace rider geometry.
    private static final float CG_HEIGHT = 0.826f;
    private static final float PITCH_INERTIA = 63f; // about body-local X (right)
    private static final float YAW_INERTIA = 45f;   // about body-local Y (up)
    private static final float ROLL_INERTIA = 40f;  // about body-local Z (forward)

    private static final float FRONT_TRAVEL = 0.3099f;
    private static final float REAR_TRAVEL = 0.30988f;
    private static final float FRONT_STATIC_COMPRESSION = 0.095f;
    private static final float REAR_STATIC_COMPRESSION = 0.111f;
    private static final float FRONT_UNSPRUNG_EFFECTIVE_MASS = 12f;
    private static final float REAR_UNSPRUNG_EFFECTIVE_MASS = 18f;
'''
    new_constants = '''    private static final float G = 9.80665f;
    private static final float TOTAL_MASS = 198.0f;
    private static final float WHEELBASE = 1.48082f;
    private static final float CG_FORWARD = 0.7222f;
    // Bike-only Honda prior plus fixed first-pass rider mass. Gate E will replace rider geometry.
    private static final float CG_HEIGHT = 0.826f;
    private static final float PITCH_INERTIA = 63f; // about body-local X (right)
    private static final float YAW_INERTIA = 45f;   // about body-local Y (up)
    private static final float ROLL_INERTIA = 40f;  // about body-local Z (forward)

    private static final float FRONT_TRAVEL = 0.3099f;
    private static final float REAR_TRAVEL = 0.30988f;
    private static final float FRONT_UNSPRUNG_EFFECTIVE_MASS = 12f;
    private static final float REAR_UNSPRUNG_EFFECTIVE_MASS = 18f;
    private static final float SPRUNG_MASS = TOTAL_MASS
            - FRONT_UNSPRUNG_EFFECTIVE_MASS - REAR_UNSPRUNG_EFFECTIVE_MASS;
    // Static sag is based on sprung axle load. Tire load separately includes unsprung weight.
    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;
    private static final float REAR_STATIC_COMPRESSION = 0.09240f;
    private static final String PHYSICS_V2_RIDEABILITY_PASS_1 = "force-path-v1";
'''
    text = replace_once(text, old_constants, new_constants, "mass/sag constants")

    old_fields = '''    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2Tire.State rearTireState = new PhysicsV2Tire.State();
'''
    new_fields = '''    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2RiderController riderController = new PhysicsV2RiderController();
    private final PhysicsV2Tire.State rearTireState = new PhysicsV2Tire.State();
'''
    text = replace_once(text, old_fields, new_fields, "rider controller field")

    old_reset = '''        powertrain.reset();
        input.throttle = input.rearBrake = input.steer = 0f;
'''
    new_reset = '''        powertrain.reset();
        riderController.reset();
        input.throttle = input.rearBrake = input.steer = 0f;
'''
    text = replace_once(text, old_reset, new_reset, "rider controller reset")

    old_static = '''        float staticFront = MASS * G * CG_FORWARD / WHEELBASE;
        float staticRear = MASS * G - staticFront;
        float frontStaticDeflection = staticFront
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.FRONT, staticFront);
        float rearStaticDeflection = staticRear
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.REAR, staticRear);
'''
    new_static = '''        float staticFrontSprung = SPRUNG_MASS * G * CG_FORWARD / WHEELBASE;
        float staticRearSprung = SPRUNG_MASS * G - staticFrontSprung;
        float staticFront = staticFrontSprung + FRONT_UNSPRUNG_EFFECTIVE_MASS * G;
        float staticRear = staticRearSprung + REAR_UNSPRUNG_EFFECTIVE_MASS * G;
        float frontStaticDeflection = staticFront
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.FRONT, staticFront);
        float rearStaticDeflection = staticRear
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.REAR, staticRear);
'''
    text = replace_once(text, old_static, new_static, "static tire loads")

    old_dynamics = '''        // Reduced unsprung generalized-coordinate dynamics. These coordinates alter actual wheel
        // center/contact geometry; their forces are not visual filters.
        float frontSpring = frontSuspensionForce(frontCompression, frontCompressionRate);
        float rearSpring = rearSuspensionForce(rearCompression, rearCompressionRate);
        float frontQdd = (frontContact.normalForce - frontSpring) / FRONT_UNSPRUNG_EFFECTIVE_MASS;
        float rearQdd = (rearContact.normalForce - rearSpring) / REAR_UNSPRUNG_EFFECTIVE_MASS;
        frontCompressionRate += frontQdd * dt;
        rearCompressionRate += rearQdd * dt;
        frontCompression += frontCompressionRate * dt;
        rearCompression += rearCompressionRate * dt;
        enforceSuspensionLimits(true);
        enforceSuspensionLimits(false);

        // Physical steering degree of freedom. Player steering is torque authority, not angle.
        float steerTorque = clamp(input.steer, -1f, 1f) * MAX_STEER_TORQUE
                + frontTireForce.mz - STEER_DAMPING * steerRate;
        steerRate += steerTorque / STEER_INERTIA * dt;
'''
    new_dynamics = '''        // PHYSICS_V2_RIDEABILITY_PASS_1
        // Unsprung vertical motion is driven by the tire, opposed by spring/damper force and
        // unsprung weight. The equal-and-opposite suspension force is transmitted to the chassis
        // below; tire normal load is no longer injected directly into the sprung rigid body.
        float frontSpring = frontSuspensionForce(frontCompression, frontCompressionRate);
        float rearSpring = rearSuspensionForce(rearCompression, rearCompressionRate);
        float frontWheelUp = wheelForceAlongUp(frontContact, frontTireForce);
        float rearWheelUp = wheelForceAlongUp(rearContact, rearTireForce);
        float frontQdd = (frontWheelUp - frontSpring
                - FRONT_UNSPRUNG_EFFECTIVE_MASS * G * up.y)
                / FRONT_UNSPRUNG_EFFECTIVE_MASS;
        float rearQdd = (rearWheelUp - rearSpring
                - REAR_UNSPRUNG_EFFECTIVE_MASS * G * up.y)
                / REAR_UNSPRUNG_EFFECTIVE_MASS;
        frontCompressionRate += frontQdd * dt;
        rearCompressionRate += rearQdd * dt;
        frontCompression += frontCompressionRate * dt;
        rearCompression += rearCompressionRate * dt;
        enforceSuspensionLimits(true);
        enforceSuspensionLimits(false);

        // The phone command is a desired turn. A separate virtual rider produces physical
        // handlebar torque: direct steering at walking speed, countersteer/lean regulation once
        // lateral tire authority exists. The plant still receives torque only.
        float riderSteer = riderController.normalizedTorque(
                input.steer,
                forward.x * vx + forward.y * vy + forward.z * vz,
                roll(),
                rollRate(),
                steerAngle,
                steerRate,
                MAX_STEER_TORQUE,
                dt);
        float steerTorque = riderSteer * MAX_STEER_TORQUE
                + frontTireForce.mz - STEER_DAMPING * steerRate;
        steerRate += steerTorque / STEER_INERTIA * dt;
'''
    text = replace_once(text, old_dynamics, new_dynamics, "unsprung and steering dynamics")

    old_force_path = '''        totalForce.set(0f, -MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContactToRigidBody(rearContact, rearTireForce, totalForce, totalMomentWorld);
        addContactToRigidBody(frontContact, frontTireForce, totalForce, totalMomentWorld);
'''
    new_force_path = '''        // The rigid quaternion body is the sprung bike+rider assembly. Tire tangential force
        // enters through each axle, suspension normal force enters through the spring/damper,
        // and hub torque is handled separately below. This avoids double-counting drive pitch.
        totalForce.set(0f, -SPRUNG_MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContactToRigidBody(
                rearContact, rearTireForce, rearSpring, totalForce, totalMomentWorld);
        addContactToRigidBody(
                frontContact, frontTireForce, frontSpring, totalForce, totalMomentWorld);
'''
    text = replace_once(text, old_force_path, new_force_path, "chassis force path")

    text = text.replace("vx += totalForce.x / MASS * dt;", "vx += totalForce.x / SPRUNG_MASS * dt;", 1)
    text = text.replace("vy += totalForce.y / MASS * dt;", "vy += totalForce.y / SPRUNG_MASS * dt;", 1)
    text = text.replace("vz += totalForce.z / MASS * dt;", "vz += totalForce.z / SPRUNG_MASS * dt;", 1)

    old_add = '''    private void addContactToRigidBody(Contact c, PhysicsV2Tire.Force f,
                                       Vec forceSum, Vec momentSum) {
        if (f.fz <= 0f) return;
        c.force.set(
                c.normal.x * f.fz + c.wheelForward.x * f.fx + c.wheelRight.x * f.fy,
                c.normal.y * f.fz + c.wheelForward.y * f.fx + c.wheelRight.y * f.fy,
                c.normal.z * f.fz + c.wheelForward.z * f.fx + c.wheelRight.z * f.fy);
        forceSum.add(c.force);
        Vec m = cross(c.contactArm, c.force, c.tmpB);
        momentSum.add(m);
        momentSum.x += c.normal.x * f.mz + c.wheelForward.x * f.mx;
        momentSum.y += c.normal.y * f.mz + c.wheelForward.y * f.mx;
        momentSum.z += c.normal.z * f.mz + c.wheelForward.z * f.mx;
    }
'''
    new_add = '''    private float wheelForceAlongUp(Contact c, PhysicsV2Tire.Force f) {
        float fx = c.normal.x * f.fz
                + c.wheelForward.x * f.fx + c.wheelRight.x * f.fy;
        float fy = c.normal.y * f.fz
                + c.wheelForward.y * f.fx + c.wheelRight.y * f.fy;
        float fz = c.normal.z * f.fz
                + c.wheelForward.z * f.fx + c.wheelRight.z * f.fy;
        return fx * up.x + fy * up.y + fz * up.z;
    }

    private void addContactToRigidBody(
            Contact c,
            PhysicsV2Tire.Force f,
            float suspensionForce,
            Vec forceSum,
            Vec momentSum) {
        if (f.fz <= 0f && suspensionForce <= 0f) return;

        // Normal tire force is carried by the unsprung mass and reaches the chassis only through
        // the spring/damper. Longitudinal/lateral bearing loads enter at the axle center.
        c.force.set(
                c.wheelForward.x * f.fx + c.wheelRight.x * f.fy
                        + up.x * suspensionForce,
                c.wheelForward.y * f.fx + c.wheelRight.y * f.fy
                        + up.y * suspensionForce,
                c.wheelForward.z * f.fx + c.wheelRight.z * f.fy
                        + up.z * suspensionForce);
        forceSum.add(c.force);

        c.tmpA.set(c.center.x - px, c.center.y - py, c.center.z - pz);
        Vec m = cross(c.tmpA, c.force, c.tmpB);
        momentSum.add(m);
        momentSum.x += c.normal.x * f.mz + c.wheelForward.x * f.mx;
        momentSum.y += c.normal.y * f.mz + c.wheelForward.y * f.mx;
        momentSum.z += c.normal.z * f.mz + c.wheelForward.z * f.mx;
    }
'''
    text = replace_once(text, old_add, new_add, "axle force transmission")

    PLANT.write_text(text, encoding="utf-8")


def main() -> None:
    CONTROLLER.write_text(controller_source(), encoding="utf-8")
    TEST.write_text(test_source(), encoding="utf-8")
    patch_plant()
    print("Physics V2 rideability correction pass applied")


if __name__ == "__main__":
    main()
