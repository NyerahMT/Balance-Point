package com.nyerahworks.balancepoint;

/**
 * First implementation of the assists-off Physics V2 motorcycle plant.
 *
 * Coordinates: world X right, Y up, Z forward. Body local axes are the same at identity.
 * The rigid system integrates translation, a quaternion attitude, body angular velocity,
 * steering, front/rear suspension coordinates and both wheel angular speeds. No wheelie,
 * jump, landing or airborne attitude mode exists in this class.
 */
final class PhysicsV2Plant {
    interface Terrain {
        float height(float x, float z);
        float slopeX(float x, float z);
        float slopeZ(float x, float z);
        float traction(float x, float z);
    }

    static final class Input {
        float throttle;
        float rearBrake;
        float steer;
    }

    static final class Telemetry {
        float rearFz, frontFz;
        float rearFx, frontFx;
        float rearFy, frontFy;
        float rearKappa, frontKappa;
        float rearAlpha, frontAlpha;
        float rearUtilization, frontUtilization;
        float rearSuspension, frontSuspension;
        float steerAngle;
        float engineRpm;
        float speed;
        boolean rearGrounded, frontGrounded;
    }

    private static final float G = 9.80665f;
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
    private static final float FRONT_SPRING = 10_000f;
    private static final float FRONT_COMP_DAMP = 620f;
    private static final float FRONT_REBOUND_DAMP = 930f;
    private static final float REAR_COMP_DAMP = 705f;
    private static final float REAR_REBOUND_DAMP = 1056f;
    private static final float FRONT_WHEEL_INERTIA = 0.82f;
    private static final float REAR_WHEEL_INERTIA = 1.05f;
    private static final float MAX_REAR_BRAKE_TORQUE = 820f;

    private static final float STEER_INERTIA = 0.24f;
    private static final float STEER_DAMPING = 2.2f;
    private static final float MAX_STEER_TORQUE = 18f;
    private static final float MAX_STEER_ANGLE = 35f * (float)Math.PI / 180f;
    private static final float AERO_COEFF = 0.34f;
    private static final float ROLLING_COEFF = 0.017f;

    private final Input input = new Input();
    private final Telemetry telemetry = new Telemetry();
    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2Tire.State rearTireState = new PhysicsV2Tire.State();
    private final PhysicsV2Tire.State frontTireState = new PhysicsV2Tire.State();
    private final PhysicsV2Tire.Force rearTireForce = new PhysicsV2Tire.Force();
    private final PhysicsV2Tire.Force frontTireForce = new PhysicsV2Tire.Force();

    // World translation of the combined reference CG.
    private float px, py, pz;
    private float vx, vy, vz;
    // Body-to-world unit quaternion.
    private float qx, qy, qz, qw = 1f;
    // Body angular velocity: X pitch, Y yaw, Z roll.
    private float wx, wy, wz;
    private float steerAngle, steerRate;
    private float frontCompression, frontCompressionRate;
    private float rearCompression, rearCompressionRate;
    private float frontWheelOmega, rearWheelOmega;
    private float rearWheelSpin, frontWheelSpin;

    private final Vec right = new Vec();
    private final Vec up = new Vec();
    private final Vec forward = new Vec();
    private final Vec omegaWorld = new Vec();
    private final Vec totalForce = new Vec();
    private final Vec totalMomentWorld = new Vec();
    private final Contact rearContact = new Contact();
    private final Contact frontContact = new Contact();

    PhysicsV2Plant() {
        reset(null);
    }

    Input input() { return input; }
    Telemetry telemetry() { return telemetry; }
    PhysicsV2Powertrain powertrain() { return powertrain; }

    void reset(Terrain terrain) {
        px = pz = 0f;
        py = CG_HEIGHT + (terrain == null ? 0f : terrain.height(0f, 0f));
        vx = vy = vz = 0f;
        qx = qy = qz = 0f;
        qw = 1f;
        wx = wy = wz = 0f;
        steerAngle = steerRate = 0f;
        frontCompression = FRONT_STATIC_COMPRESSION;
        rearCompression = REAR_STATIC_COMPRESSION;
        frontCompressionRate = rearCompressionRate = 0f;
        frontWheelOmega = rearWheelOmega = 0f;
        rearWheelSpin = frontWheelSpin = 0f;
        rearTireState.reset();
        frontTireState.reset();
        powertrain.reset();
        input.throttle = input.rearBrake = input.steer = 0f;
        updateTelemetry();
    }

    void step(Terrain terrain, float dt) {
        if (terrain == null || dt <= 0f) return;
        basis();
        rotateBodyToWorld(wx, wy, wz, omegaWorld);

        float staticFront = MASS * G * CG_FORWARD / WHEELBASE;
        float staticRear = MASS * G - staticFront;
        float frontStaticDeflection = staticFront
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.FRONT, staticFront);
        float rearStaticDeflection = staticRear
                / PhysicsV2Tire.effectiveVerticalStiffness(PhysicsV2Tire.REAR, staticRear);
        float frontFullExtY = PhysicsV2Tire.FRONT.radius - frontStaticDeflection
                - CG_HEIGHT - FRONT_STATIC_COMPRESSION;
        float rearFullExtY = PhysicsV2Tire.REAR.radius - rearStaticDeflection
                - CG_HEIGHT - REAR_STATIC_COMPRESSION;

        sampleContact(rearContact, terrain, -CG_FORWARD, rearFullExtY + rearCompression,
                0f, rearCompressionRate, false, dt);
        sampleContact(frontContact, terrain, WHEELBASE - CG_FORWARD,
                frontFullExtY + frontCompression, 0f, frontCompressionRate, true, dt);

        // Reduced unsprung generalized-coordinate dynamics. These coordinates alter actual wheel
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
        steerAngle += steerRate * dt;
        if (steerAngle > MAX_STEER_ANGLE) { steerAngle = MAX_STEER_ANGLE; steerRate = Math.min(0f, steerRate); }
        if (steerAngle < -MAX_STEER_ANGLE) { steerAngle = -MAX_STEER_ANGLE; steerRate = Math.max(0f, steerRate); }

        float horizontalSpeed = (float)Math.sqrt(vx * vx + vz * vz);
        float clutchCommand = clamp(0.16f + horizontalSpeed / 6.0f, 0.16f, 1f);
        float rearDriveTorque = powertrain.step(rearWheelOmega, input.throttle, clutchCommand, dt);
        float brakeTorque = clamp(input.rearBrake, 0f, 1f) * MAX_REAR_BRAKE_TORQUE;
        float rearBrakeSign = Math.abs(rearWheelOmega) > 0.2f ? Math.signum(rearWheelOmega) : 1f;

        float rearAngularAccel = (rearDriveTorque - rearTireForce.fx * PhysicsV2Tire.REAR.effectiveRadius
                - rearBrakeSign * brakeTorque) / REAR_WHEEL_INERTIA;
        float frontAngularAccel = (-frontTireForce.fx * PhysicsV2Tire.FRONT.effectiveRadius)
                / FRONT_WHEEL_INERTIA;
        rearWheelOmega += rearAngularAccel * dt;
        frontWheelOmega += frontAngularAccel * dt;
        if (input.rearBrake > 0f && rearWheelOmega * rearBrakeSign < 0f) rearWheelOmega = 0f;
        rearWheelSpin += rearWheelOmega * dt;
        frontWheelSpin += frontWheelOmega * dt;

        totalForce.set(0f, -MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContactToRigidBody(rearContact, rearTireForce, totalForce, totalMomentWorld);
        addContactToRigidBody(frontContact, frontTireForce, totalForce, totalMomentWorld);

        // Drag/rolling forces are external and oppose world horizontal velocity.
        if (horizontalSpeed > 0.05f) {
            float inv = 1f / horizontalSpeed;
            float rolling = ROLLING_COEFF * (rearContact.normalForce + frontContact.normalForce);
            float aero = AERO_COEFF * horizontalSpeed * horizontalSpeed;
            totalForce.x -= vx * inv * (rolling + aero);
            totalForce.z -= vz * inv * (rolling + aero);
        }

        // Equal-and-opposite shaft reactions. These are the only airborne wheel attitude authority.
        Vec rearAxis = rearContact.wheelRight;
        totalMomentWorld.x += rearAxis.x * (-rearDriveTorque + rearBrakeSign * brakeTorque);
        totalMomentWorld.y += rearAxis.y * (-rearDriveTorque + rearBrakeSign * brakeTorque);
        totalMomentWorld.z += rearAxis.z * (-rearDriveTorque + rearBrakeSign * brakeTorque);
        float engineReaction = -0.0065f * powertrain.getEngineAlpha();
        totalMomentWorld.x += right.x * engineReaction;
        totalMomentWorld.y += right.y * engineReaction;
        totalMomentWorld.z += right.z * engineReaction;

        vx += totalForce.x / MASS * dt;
        vy += totalForce.y / MASS * dt;
        vz += totalForce.z / MASS * dt;
        px += vx * dt;
        py += vy * dt;
        pz += vz * dt;

        Vec torqueBody = new Vec();
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
        updateTelemetry();
    }

    boolean shiftUp() { return powertrain.shiftUp(); }
    boolean shiftDown() { return powertrain.shiftDown(rearWheelOmega); }

    float x() { return px; }
    float y() { return py; }
    float z() { return pz; }
    float speed() { return forward.x * vx + forward.y * vy + forward.z * vz; }
    float verticalVelocity() { return vy; }
    float pitch() {
        basis();
        return (float)Math.atan2(forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z));
    }
    float yaw() {
        basis();
        return (float)Math.atan2(forward.x, forward.z);
    }
    float roll() {
        basis();
        return (float)Math.atan2(right.y, up.y);
    }
    float pitchRate() { return wx; }
    float rollRate() { return wz; }
    float yawRate() { return wy; }
    float steerAngle() { return steerAngle; }
    float steerNormalized() { return steerAngle / MAX_STEER_ANGLE; }
    float rearCompression() { return rearCompression; }
    float frontCompression() { return frontCompression; }
    float rearWheelSpin() { return rearWheelSpin; }
    float frontWheelSpin() { return frontWheelSpin; }
    boolean rearGrounded() { return rearContact.normalForce > 10f; }
    boolean frontGrounded() { return frontContact.normalForce > 10f; }
    boolean airborne() { return !rearGrounded() && !frontGrounded(); }

    private void sampleContact(Contact c, Terrain terrain, float localZ, float localY,
                               float localX, float suspensionRate, boolean isFront, float dt) {
        Vec rel = c.rel;
        rotateBodyToWorld(localX, localY, localZ, rel);
        c.center.set(px + rel.x, py + rel.y, pz + rel.z);

        float sx = terrain.slopeX(c.center.x, c.center.z);
        float sz = terrain.slopeZ(c.center.x, c.center.z);
        c.normal.set(-sx, 1f, -sz).nor();
        c.groundHeight = terrain.height(c.center.x, c.center.z);
        c.traction = terrain.traction(c.center.x, c.center.z);

        Vec spinAxisBase = right;
        if (isFront) {
            float cs = (float)Math.cos(steerAngle);
            float ss = (float)Math.sin(steerAngle);
            c.wheelForward.set(
                    forward.x * cs + right.x * ss,
                    forward.y * cs + right.y * ss,
                    forward.z * cs + right.z * ss).nor();
            c.wheelRight.set(
                    right.x * cs - forward.x * ss,
                    right.y * cs - forward.y * ss,
                    right.z * cs - forward.z * ss).nor();
        } else {
            c.wheelForward.set(forward);
            c.wheelRight.set(spinAxisBase);
        }

        Vec angularAtRel = cross(omegaWorld, rel, c.tmpA);
        c.centerVelocity.set(vx + angularAtRel.x, vy + angularAtRel.y, vz + angularAtRel.z);
        c.centerVelocity.x += up.x * suspensionRate;
        c.centerVelocity.y += up.y * suspensionRate;
        c.centerVelocity.z += up.z * suspensionRate;

        PhysicsV2Tire.Spec spec = isFront ? PhysicsV2Tire.FRONT : PhysicsV2Tire.REAR;
        float penetration = c.groundHeight + spec.radius - c.center.y;
        float penetrationRate = -c.centerVelocity.dot(c.normal);
        c.normalForce = PhysicsV2Tire.verticalLoad(spec, penetration, penetrationRate);
        float longSpeed = c.centerVelocity.dot(c.wheelForward);
        float lateralSpeed = c.centerVelocity.dot(c.wheelRight);
        float wheelOmega = isFront ? frontWheelOmega : rearWheelOmega;
        float denom = Math.max(Math.abs(longSpeed), 2.0f);
        c.kappa = (wheelOmega * spec.effectiveRadius - longSpeed) / denom;
        c.alpha = (float)Math.atan2(-lateralSpeed, Math.max(Math.abs(longSpeed), 1f));
        c.gamma = (float)Math.asin(clamp(c.wheelRight.dot(c.normal), -1f, 1f));

        PhysicsV2Tire.State state = isFront ? frontTireState : rearTireState;
        PhysicsV2Tire.Force force = isFront ? frontTireForce : rearTireForce;
        PhysicsV2Tire.step(spec, state, force, c.normalForce, c.kappa, c.alpha, c.gamma,
                longSpeed, dt, c.traction);

        c.contactPoint.set(
                c.center.x - c.normal.x * spec.effectiveRadius,
                c.center.y - c.normal.y * spec.effectiveRadius,
                c.center.z - c.normal.z * spec.effectiveRadius);
        c.contactArm.set(c.contactPoint.x - px, c.contactPoint.y - py, c.contactPoint.z - pz);
    }

    private void addContactToRigidBody(Contact c, PhysicsV2Tire.Force f,
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

    private float frontSuspensionForce(float q, float qd) {
        float damping = qd >= 0f ? FRONT_COMP_DAMP : FRONT_REBOUND_DAMP;
        float end = 0f;
        float start = 0.75f * FRONT_TRAVEL;
        if (q > start) {
            float u = clamp((q - start) / (FRONT_TRAVEL - start), 0f, 1f);
            end = 2500f * u * u * u;
        }
        return Math.max(0f, FRONT_SPRING * q + damping * qd + end);
    }

    private float rearSuspensionForce(float q, float qd) {
        float u = clamp(q / REAR_TRAVEL, 0f, 1f);
        float mr;
        if (u <= 0.7f) mr = 2.420f + (2.340f - 2.420f) * (u / 0.7f);
        else mr = 2.340f + (2.086f - 2.340f) * ((u - 0.7f) / 0.3f);
        float wheelRate = 52_000f / (mr * mr);
        float damping = qd >= 0f ? REAR_COMP_DAMP : REAR_REBOUND_DAMP;
        float end = 0f;
        float start = 0.82f * REAR_TRAVEL;
        if (q > start) {
            float x = clamp((q - start) / (REAR_TRAVEL - start), 0f, 1f);
            end = (4500f / mr) * x * x * x;
        }
        return Math.max(0f, wheelRate * q + damping * qd + end);
    }

    private void enforceSuspensionLimits(boolean front) {
        if (front) {
            if (frontCompression < 0f) { frontCompression = 0f; frontCompressionRate = Math.max(0f, frontCompressionRate); }
            if (frontCompression > FRONT_TRAVEL) { frontCompression = FRONT_TRAVEL; frontCompressionRate = Math.min(0f, frontCompressionRate); }
        } else {
            if (rearCompression < 0f) { rearCompression = 0f; rearCompressionRate = Math.max(0f, rearCompressionRate); }
            if (rearCompression > REAR_TRAVEL) { rearCompression = REAR_TRAVEL; rearCompressionRate = Math.min(0f, rearCompressionRate); }
        }
    }

    private void basis() {
        rotateBodyToWorld(1f, 0f, 0f, right);
        rotateBodyToWorld(0f, 1f, 0f, up);
        rotateBodyToWorld(0f, 0f, 1f, forward);
    }

    private void integrateQuaternion(float dt) {
        float dx = 0.5f * (qw * wx + qy * wz - qz * wy);
        float dy = 0.5f * (qw * wy + qz * wx - qx * wz);
        float dz = 0.5f * (qw * wz + qx * wy - qy * wx);
        float dw = -0.5f * (qx * wx + qy * wy + qz * wz);
        qx += dx * dt;
        qy += dy * dt;
        qz += dz * dt;
        qw += dw * dt;
        float n = (float)Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (n > 1e-8f) { qx /= n; qy /= n; qz /= n; qw /= n; }
        else { qx = qy = qz = 0f; qw = 1f; }
    }

    private void rotateBodyToWorld(float x, float y, float z, Vec out) {
        float tx = 2f * (qy * z - qz * y);
        float ty = 2f * (qz * x - qx * z);
        float tz = 2f * (qx * y - qy * x);
        out.set(
                x + qw * tx + (qy * tz - qz * ty),
                y + qw * ty + (qz * tx - qx * tz),
                z + qw * tz + (qx * ty - qy * tx));
    }

    private void rotateWorldToBody(float x, float y, float z, Vec out) {
        float ox = qx, oy = qy, oz = qz;
        qx = -qx; qy = -qy; qz = -qz;
        rotateBodyToWorld(x, y, z, out);
        qx = ox; qy = oy; qz = oz;
    }

    private void updateTelemetry() {
        telemetry.rearFz = rearTireForce.fz;
        telemetry.frontFz = frontTireForce.fz;
        telemetry.rearFx = rearTireForce.fx;
        telemetry.frontFx = frontTireForce.fx;
        telemetry.rearFy = rearTireForce.fy;
        telemetry.frontFy = frontTireForce.fy;
        telemetry.rearKappa = rearContact.kappa;
        telemetry.frontKappa = frontContact.kappa;
        telemetry.rearAlpha = rearContact.alpha;
        telemetry.frontAlpha = frontContact.alpha;
        telemetry.rearUtilization = rearTireForce.utilization;
        telemetry.frontUtilization = frontTireForce.utilization;
        telemetry.rearSuspension = rearCompression;
        telemetry.frontSuspension = frontCompression;
        telemetry.steerAngle = steerAngle;
        telemetry.engineRpm = powertrain.getRpm();
        telemetry.speed = speed();
        telemetry.rearGrounded = rearGrounded();
        telemetry.frontGrounded = frontGrounded();
    }

    private boolean finite() {
        return finite(px) && finite(py) && finite(pz) && finite(vx) && finite(vy) && finite(vz)
                && finite(qx) && finite(qy) && finite(qz) && finite(qw)
                && finite(wx) && finite(wy) && finite(wz)
                && finite(frontCompression) && finite(rearCompression)
                && finite(frontWheelOmega) && finite(rearWheelOmega);
    }

    private static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Vec cross(Vec a, Vec b, Vec out) {
        return out.set(a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    private static final class Vec {
        float x, y, z;
        Vec set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }
        Vec set(Vec v) { return set(v.x, v.y, v.z); }
        Vec add(Vec v) { x += v.x; y += v.y; z += v.z; return this; }
        float dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
        Vec nor() {
            float n = (float)Math.sqrt(x * x + y * y + z * z);
            if (n > 1e-8f) { x /= n; y /= n; z /= n; }
            return this;
        }
    }

    private static final class Contact {
        final Vec rel = new Vec();
        final Vec center = new Vec();
        final Vec normal = new Vec();
        final Vec wheelForward = new Vec();
        final Vec wheelRight = new Vec();
        final Vec centerVelocity = new Vec();
        final Vec contactPoint = new Vec();
        final Vec contactArm = new Vec();
        final Vec force = new Vec();
        final Vec tmpA = new Vec();
        final Vec tmpB = new Vec();
        float groundHeight, traction, normalForce, kappa, alpha, gamma;
    }
}
