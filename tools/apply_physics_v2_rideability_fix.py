#!/usr/bin/env python3
"""Apply corrected Physics V2 rideability changes to the live runtime."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
TIRE = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Tire.java"
GAME = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java"
CONTROLLER = ROOT / (
    "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2RiderController.java"
)
TEST = ROOT / (
    "core/src/test/java/com/nyerahworks/balancepoint/PhysicsV2RideabilityTest.java"
)
MARKER = "PHYSICS_V2_RIDEABILITY_PASS_2"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def write_controller() -> None:
    CONTROLLER.write_text(
        '''package com.nyerahworks.balancepoint;

/** Converts phone turn intent into physical handlebar torque. */
final class PhysicsV2RiderController {
    private static final float G = 9.80665f;
    private static final float WHEELBASE = 1.48082f;
    private static final float MAX_LEAN = radians(28f);
    private static final float MAX_HANDLEBAR_TORQUE = 10.0f;
    private static final float LOW_SPEED_TORQUE = 3.0f;
    private static final float ROLL_KP = 16.0f;
    private static final float ROLL_KD = 4.5f;
    private static final float STEER_KP = 6.0f;
    private static final float MAX_TARGET_ROLL_RATE = 1.8f;

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
        float blend = clamp(dt * 9f, 0f, 1f);
        filteredCommand += (command - filteredCommand) * blend;

        float speed = Math.abs(forwardSpeed);
        float highSpeedBlend = smoothstep(2.2f, 5.5f, speed);
        float leanAuthority = smoothstep(1.5f, 8.0f, speed);
        float shaped = filteredCommand * (0.70f + 0.30f * Math.abs(filteredCommand));

        // Positive player command means right turn. Right lean is negative roll here.
        float targetRoll = -shaped * MAX_LEAN * leanAuthority;
        float rollError = targetRoll - roll;
        float targetRollRate = clamp(
                rollError * 4.2f,
                -MAX_TARGET_ROLL_RATE,
                MAX_TARGET_ROLL_RATE);

        float targetSteer = 0f;
        if (speed > 1.0f) {
            targetSteer = -WHEELBASE * G * (float)Math.tan(targetRoll)
                    / Math.max(speed * speed, 1.0f);
            targetSteer = clamp(targetSteer, -radians(12f), radians(12f));
        }

        float highSpeedTorque = ROLL_KP * rollError
                + ROLL_KD * (targetRollRate - rollRate)
                + STEER_KP * (targetSteer - steerAngle);
        float lowSpeedTorque = filteredCommand * LOW_SPEED_TORQUE
                - 2.2f * steerAngle - 0.35f * steerRate;
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
''',
        encoding="utf-8",
    )


def write_tests() -> None:
    TEST.write_text(
        '''package com.nyerahworks.balancepoint;

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
        p.input().throttle = 0.42f;
        for (int i = 0; i < 600; i++) p.step(terrain, DT);

        p.input().throttle = 0.18f;
        p.input().steer = 0.42f;
        float maxRoll = 0f;
        for (int i = 0; i < 300; i++) {
            p.step(terrain, DT);
            maxRoll = Math.max(maxRoll, Math.abs(p.roll()));
        }
        assertTrue("max roll=" + maxRoll, maxRoll < radians(42f));

        p.input().steer = 0f;
        for (int i = 0; i < 300; i++) p.step(terrain, DT);
        assertTrue("release roll=" + p.roll(), Math.abs(p.roll()) < radians(20f));
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

    @Test
    public void forwardRiderPositionReducesHardLaunchPitch() {
        FlatTerrain terrain = new FlatTerrain();
        PhysicsV2Plant neutral = new PhysicsV2Plant();
        PhysicsV2Plant forward = new PhysicsV2Plant();
        neutral.reset(terrain);
        forward.reset(terrain);
        neutral.input().throttle = 0.82f;
        forward.input().throttle = 0.82f;
        forward.input().riderForeAft = 0.65f;
        float neutralMax = 0f;
        float forwardMax = 0f;
        for (int i = 0; i < 360; i++) {
            neutral.step(terrain, DT);
            forward.step(terrain, DT);
            neutralMax = Math.max(neutralMax, neutral.pitch());
            forwardMax = Math.max(forwardMax, forward.pitch());
        }
        assertTrue("neutral=" + neutralMax + " forward=" + forwardMax,
                forwardMax < neutralMax);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }
}
''',
        encoding="utf-8",
    )


def patch_tire() -> None:
    text = TIRE.read_text(encoding="utf-8")
    old = "        float alphaEffective = alpha + spec.camberGain * gamma;\n"
    new = '''        // Camber thrust acts toward the lean direction. Positive gamma in this coordinate
        // system is left lean, so its equivalent slip-angle contribution is negative.
        float alphaEffective = alpha - spec.camberGain * gamma;
'''
    if old in text:
        text = replace_once(text, old, new, "camber thrust sign")
        TIRE.write_text(text, encoding="utf-8")
    elif "alpha - spec.camberGain" not in text:
        raise SystemExit("camber thrust seam not found")


def patch_game() -> None:
    text = GAME.read_text(encoding="utf-8")
    old = '''        v2Input.steer = steer;
        physicsV2.step(physicsV2Terrain, dt);
'''
    new = '''        v2Input.steer = steer;
        v2Input.riderForeAft = riderLean;
        physicsV2.step(physicsV2Terrain, dt);
'''
    if old in text:
        text = replace_once(text, old, new, "rider fore/aft runtime input")
        GAME.write_text(text, encoding="utf-8")
    elif "v2Input.riderForeAft = riderLean;" not in text:
        raise SystemExit("runtime rider input seam not found")


def patch_plant() -> None:
    text = PLANT.read_text(encoding="utf-8")
    if MARKER in text:
        return

    text = replace_once(
        text,
        '''    static final class Input {
        float throttle;
        float rearBrake;
        float steer;
    }
''',
        '''    static final class Input {
        float throttle;
        float rearBrake;
        float steer;
        float riderForeAft;
    }
''',
        "input rider axis",
    )

    text = replace_once(
        text,
        '''    private static final float G = 9.80665f;
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
''',
        '''    private static final float G = 9.80665f;
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
    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;
    private static final float REAR_STATIC_COMPRESSION = 0.09240f;
    private static final float AUTO_BRACE_PER_G = 0.85f;
    private static final float AUTO_BRACE_MAX = 0.80f;
    private static final String PHYSICS_V2_RIDEABILITY_PASS_2 = "multibody-rider-v2";
''',
        "mass and suspension constants",
    )

    text = replace_once(
        text,
        '''    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2Tire.State rearTireState = new PhysicsV2Tire.State();
''',
        '''    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2RiderController riderController = new PhysicsV2RiderController();
    private final MotorcycleRiderDynamics riderLongitudinal =
            new MotorcycleRiderDynamics(TOTAL_MASS, G);
    private final PhysicsV2Tire.State rearTireState = new PhysicsV2Tire.State();
''',
        "rider fields",
    )

    text = replace_once(
        text,
        '''    private float rearWheelSpin, frontWheelSpin;
''',
        '''    private float rearWheelSpin, frontWheelSpin;
    private float lastLongitudinalAcceleration;
''',
        "longitudinal acceleration state",
    )

    text = replace_once(
        text,
        '''        powertrain.reset();
        input.throttle = input.rearBrake = input.steer = 0f;
''',
        '''        powertrain.reset();
        riderController.reset();
        riderLongitudinal.reset();
        lastLongitudinalAcceleration = 0f;
        input.throttle = input.rearBrake = input.steer = input.riderForeAft = 0f;
''',
        "rider reset",
    )

    text = replace_once(
        text,
        '''        float staticFront = MASS * G * CG_FORWARD / WHEELBASE;
        float staticRear = MASS * G - staticFront;
        float frontStaticDeflection = staticFront
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.FRONT, staticFront);
        float rearStaticDeflection = staticRear
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.REAR, staticRear);
''',
        '''        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;
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
        float effectiveCgForward = CG_FORWARD + riderLongitudinal.combinedComShift();

        float staticFrontSprung = SPRUNG_MASS * G * CG_FORWARD / WHEELBASE;
        float staticRearSprung = SPRUNG_MASS * G - staticFrontSprung;
        float staticFront = staticFrontSprung + FRONT_UNSPRUNG_EFFECTIVE_MASS * G;
        float staticRear = staticRearSprung + REAR_UNSPRUNG_EFFECTIVE_MASS * G;
        float frontStaticDeflection = staticFront
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.FRONT, staticFront);
        float rearStaticDeflection = staticRear
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.REAR, staticRear);
''',
        "rider and static loads",
    )

    text = replace_once(
        text,
        '''        sampleContact(rearContact, terrain, -CG_FORWARD, rearFullExtY + rearCompression,
                0f, rearCompressionRate, false, dt);
        sampleContact(frontContact, terrain, WHEELBASE - CG_FORWARD,
                frontFullExtY + frontCompression, 0f, frontCompressionRate, true, dt);
''',
        '''        sampleContact(
                rearContact,
                terrain,
                -effectiveCgForward,
                rearFullExtY + rearCompression,
                0f,
                rearCompressionRate,
                false,
                dt);
        sampleContact(
                frontContact,
                terrain,
                WHEELBASE - effectiveCgForward,
                frontFullExtY + frontCompression,
                0f,
                frontCompressionRate,
                true,
                dt);
''',
        "dynamic rider CG contact geometry",
    )

    text = replace_once(
        text,
        '''        // Reduced unsprung generalized-coordinate dynamics. These coordinates alter actual wheel
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
''',
        '''        // The unsprung masses receive tire load. Spring/damper force reacts through the chassis.
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

        float riderSteer = riderController.normalizedTorque(
                input.steer,
                forwardSpeedStart,
                roll(),
                rollRate(),
                steerAngle,
                steerRate,
                MAX_STEER_TORQUE,
                dt);
        float steerTorque = riderSteer * MAX_STEER_TORQUE
                + frontTireForce.mz - STEER_DAMPING * steerRate;
        steerRate += steerTorque / STEER_INERTIA * dt;
''',
        "unsprung and steering dynamics",
    )

    text = replace_once(
        text,
        '''        totalForce.set(0f, -MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContactToRigidBody(rearContact, rearTireForce, totalForce, totalMomentWorld);
        addContactToRigidBody(frontContact, frontTireForce, totalForce, totalMomentWorld);
''',
        '''        totalForce.set(0f, -SPRUNG_MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContactToRigidBody(
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
''',
        "sprung chassis force path",
    )

    text = replace_once(
        text,
        '''        float engineReaction = -0.0065f * powertrain.getEngineAlpha();
        totalMomentWorld.x += right.x * engineReaction;
        totalMomentWorld.y += right.y * engineReaction;
        totalMomentWorld.z += right.z * engineReaction;

        vx += totalForce.x / MASS * dt;
        vy += totalForce.y / MASS * dt;
        vz += totalForce.z / MASS * dt;
''',
        '''        float engineReaction = -0.0065f * powertrain.getEngineAlpha();
        totalMomentWorld.x += right.x * engineReaction;
        totalMomentWorld.y += right.y * engineReaction;
        totalMomentWorld.z += right.z * engineReaction;

        float riderPitchTorque = riderLongitudinal.pitchReactionTorque(
                airborne(),
                frontGrounded());
        totalMomentWorld.x += right.x * riderPitchTorque;
        totalMomentWorld.y += right.y * riderPitchTorque;
        totalMomentWorld.z += right.z * riderPitchTorque;

        vx += totalForce.x / SPRUNG_MASS * dt;
        vy += totalForce.y / SPRUNG_MASS * dt;
        vz += totalForce.z / SPRUNG_MASS * dt;
''',
        "rider pitch reaction and sprung acceleration",
    )

    text = replace_once(
        text,
        '''        Vec torqueBody = new Vec();
        rotateWorldToBody(totalMomentWorld.x, totalMomentWorld.y, totalMomentWorld.z, torqueBody);
        // Euler rigid-body equation with diagonal body inertia.
        float gx = (YAW_INERTIA - ROLL_INERTIA) * wy * wz;
        float gy = (ROLL_INERTIA - PITCH_INERTIA) * wz * wx;
        float gz = (PITCH_INERTIA - YAW_INERTIA) * wx * wy;
        wx += (torqueBody.x - gx) / PITCH_INERTIA * dt;
        wy += (torqueBody.y - gy) / YAW_INERTIA * dt;
        wz += (torqueBody.z - gz) / ROLL_INERTIA * dt;
        integrateQuaternion(dt);

        if (!finite()) reset(terrain);
''',
        '''        Vec torqueBody = new Vec();
        rotateWorldToBody(totalMomentWorld.x, totalMomentWorld.y, totalMomentWorld.z, torqueBody);

        // Wheel gyroscopic bearing reactions. Spin acceleration remains handled by hub torque.
        float cs = (float)Math.cos(steerAngle);
        float ss = (float)Math.sin(steerAngle);
        float frontH = FRONT_WHEEL_INERTIA * frontWheelOmega;
        float rearH = REAR_WHEEL_INERTIA * rearWheelOmega;
        float hx = rearH + frontH * cs;
        float hz = -frontH * ss;
        torqueBody.x += -wy * hz - steerRate * hz;
        torqueBody.y += -(wz * hx - wx * hz);
        torqueBody.z += wy * hx + steerRate * frontH * cs;

        // Euler rigid-body equation with the conventional omega x (I omega) signs.
        float gx = (ROLL_INERTIA - YAW_INERTIA) * wy * wz;
        float gy = (PITCH_INERTIA - ROLL_INERTIA) * wz * wx;
        float gz = (YAW_INERTIA - PITCH_INERTIA) * wx * wy;
        wx += (torqueBody.x - gx) / PITCH_INERTIA * dt;
        wy += (torqueBody.y - gy) / YAW_INERTIA * dt;
        wz += (torqueBody.z - gz) / ROLL_INERTIA * dt;
        integrateQuaternion(dt);
        basis();
        float forwardSpeedEnd = forward.x * vx + forward.y * vy + forward.z * vz;
        lastLongitudinalAcceleration = (forwardSpeedEnd - forwardSpeedStart)
                / Math.max(dt, 0.0001f);

        if (!finite()) reset(terrain);
''',
        "gyroscopic and Euler dynamics",
    )

    text = replace_once(
        text,
        '''    private void addContactToRigidBody(Contact c, PhysicsV2Tire.Force f,
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
''',
        '''    private float wheelForceAlongUp(Contact c, PhysicsV2Tire.Force f) {
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
''',
        "axle and suspension force transmission",
    )

    text = replace_once(
        text,
        '''                && finite(frontWheelOmega) && finite(rearWheelOmega);
''',
        '''                && finite(frontWheelOmega) && finite(rearWheelOmega)
                && finite(lastLongitudinalAcceleration);
''',
        "finite rider acceleration state",
    )

    PLANT.write_text(text, encoding="utf-8")


def main() -> None:
    write_controller()
    write_tests()
    patch_tire()
    patch_game()
    patch_plant()
    print("Physics V2 corrected rideability pass applied")


if __name__ == "__main__":
    main()
