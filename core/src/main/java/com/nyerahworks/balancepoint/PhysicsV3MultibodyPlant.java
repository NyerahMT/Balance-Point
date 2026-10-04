package com.nyerahworks.balancepoint;

/**
 * Physics V3 motorcycle plant.
 *
 * Architecture follows the open TUMFTM/MBSim motorcycle model: separate chassis,
 * steering, suspension, wheel-spin and rider generalized coordinates with a
 * lateral rider controller that works through steering. Tire force structure is
 * the MIT-licensed moto-sim Pacejka formulation in PhysicsV3PacejkaTire.
 *
 * The important MX extension is unilateral terrain contact. A tire can push but
 * never pull on the ground. There is no airborne mode or pitch constraint: once
 * both contacts unload, the same rigid/multibody state simply free-flies.
 */
final class PhysicsV3MultibodyPlant {
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
        float riderForeAft;
    }

    static final class Telemetry {
        float rearFz;
        float frontFz;
        float rearFx;
        float frontFx;
        float rearFy;
        float frontFy;
        float rearKappa;
        float frontKappa;
        float rearAlpha;
        float frontAlpha;
        float rearUtilization;
        float frontUtilization;
        float rearSuspension;
        float frontSuspension;
        float steerAngle;
        float engineRpm;
        float speed;
        boolean rearGrounded;
        boolean frontGrounded;
    }

    private static final float G = 9.80665f;
    private static final float TOTAL_MASS = 198f;
    private static final float BIKE_MASS = 112.94f;
    private static final float RIDER_MASS = TOTAL_MASS - BIKE_MASS;
    private static final float WHEELBASE = 1.48082f;
    private static final float BIKE_CG_FORWARD = 0.7222f;
    private static final float CG_HEIGHT = 0.826f;

    // Combined bike+rider inertias. Body X is the wheel axle / pitch axis.
    private static final float I_PITCH = 63f;
    private static final float I_YAW = 45f;
    private static final float I_ROLL = 40f;

    private static final float FRONT_TRAVEL = 0.3099f;
    private static final float REAR_TRAVEL = 0.30988f;
    private static final float FRONT_STATIC_COMPRESSION = 0.08035f;
    private static final float REAR_STATIC_COMPRESSION = 0.09240f;
    private static final float FRONT_SUSPENSION_MASS = 12f;
    private static final float REAR_SUSPENSION_MASS = 18f;
    private static final float FRONT_WHEEL_RATE = 10_000f;
    private static final float REAR_WHEEL_RATE = 10_600f;
    private static final float FRONT_COMP_DAMP = 1_450f;
    private static final float FRONT_REBOUND_DAMP = 2_150f;
    private static final float REAR_COMP_DAMP = 1_650f;
    private static final float REAR_REBOUND_DAMP = 2_450f;

    private static final float FRONT_TIRE_K = 170_000f;
    private static final float REAR_TIRE_K = 190_000f;
    private static final float FRONT_TIRE_C = 1_750f;
    private static final float REAR_TIRE_C = 2_050f;
    private static final float MAX_TIRE_LOAD = TOTAL_MASS * G * 6.5f;

    private static final float FRONT_WHEEL_INERTIA = 0.82f;
    private static final float REAR_WHEEL_INERTIA = 1.05f;
    private static final float ENGINE_INERTIA = 0.0065f;
    private static final float MAX_REAR_BRAKE_TORQUE = 820f;

    private static final float STEER_INERTIA = 0.24f;
    // Player input is turn intent. Above walking speed the bars countersteer to
    // create lean, then hold the bicycle-model steer for the lean that exists.
    private static final float STEER_SERVO_KP = 46f;
    private static final float STEER_SERVO_KD = 15f;
    private static final float STEER_ALIGNING_GAIN = 0.25f;
    private static final float MAX_STEER_TORQUE = 26f;
    private static final float MAX_STEER_ANGLE = radians(32f);
    private static final float MAX_TARGET_LEAN = radians(36f);
    private static final float COUNTERSTEER_KP = 0.85f;
    private static final float COUNTERSTEER_KD = 0.95f;
    private static final float MAX_COUNTERSTEER = radians(6f);

    // Upright assist only while the tires cannot yet balance the bike. An external
    // roll moment at speed has to be cancelled by lateral force and saturates the
    // front tire before the bike can yaw.
    private static final float LOW_SPEED_BALANCE_KP = 160f;
    private static final float LOW_SPEED_BALANCE_KD = 85f;
    private static final float MAX_LOW_SPEED_BALANCE = 140f;
    private static final float YAW_DAMPING = 4f;

    // Grounded anti-loop catch. It is deliberately above the geometric balance
    // point and contributes nothing when the rear contact is gone.
    private static final float WHEELIE_CATCH_OFFSET = radians(5f);
    private static final float WHEELIE_CATCH_KP = 480f;
    private static final float WHEELIE_CATCH_KD = 120f;
    private static final float MAX_WHEELIE_CATCH = 520f;

    private static final float RIDER_TRAVEL = 0.16f;
    private static final float RIDER_KP = 46f;
    private static final float RIDER_KD = 13f;
    private static final float RIDER_HEIGHT_ABOVE_CG = 0.24f;

    private static final float ROLLING_COEFF = 0.017f;
    private static final float AERO_COEFF = 0.34f;
    private static final float MAX_INTERNAL_DT = 1f / 960f;

    private final Input input = new Input();
    private final Telemetry telemetry = new Telemetry();
    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV3PacejkaTire.State rearTireState =
            new PhysicsV3PacejkaTire.State();
    private final PhysicsV3PacejkaTire.State frontTireState =
            new PhysicsV3PacejkaTire.State();
    private final PhysicsV3PacejkaTire.Force rearTireForce =
            new PhysicsV3PacejkaTire.Force();
    private final PhysicsV3PacejkaTire.Force frontTireForce =
            new PhysicsV3PacejkaTire.Force();

    // World position and velocity of the combined reference CG.
    private float px;
    private float py;
    private float pz;
    private float vx;
    private float vy;
    private float vz;

    // Body-to-world quaternion and body angular velocity.
    private float qx;
    private float qy;
    private float qz;
    private float qw = 1f;
    private float wx;
    private float wy;
    private float wz;

    // Generalized coordinates.
    private float steerAngle;
    private float steerRate;
    private float frontSuspension;
    private float frontSuspensionRate;
    private float rearSuspension;
    private float rearSuspensionRate;
    private float frontWheelOmega;
    private float rearWheelOmega;
    private float frontWheelSpin;
    private float rearWheelSpin;
    private float riderX;
    private float riderXRate;

    private final Vec right = new Vec();
    private final Vec up = new Vec();
    private final Vec forward = new Vec();
    private final Vec omegaWorld = new Vec();
    private final Vec totalForce = new Vec();
    private final Vec totalMomentWorld = new Vec();
    private final Vec tmpA = new Vec();
    private final Vec tmpB = new Vec();
    private final Contact rearContact = new Contact();
    private final Contact frontContact = new Contact();

    PhysicsV3MultibodyPlant() {
        reset(null);
    }

    Input input() {
        return input;
    }

    Telemetry telemetry() {
        return telemetry;
    }

    PhysicsV2Powertrain powertrain() {
        return powertrain;
    }

    void reset(Terrain terrain) {
        float ground = terrain == null ? 0f : terrain.height(0f, 0f);
        px = 0f;
        py = ground + CG_HEIGHT;
        pz = 0f;
        vx = vy = vz = 0f;
        qx = qy = qz = 0f;
        qw = 1f;
        wx = wy = wz = 0f;
        steerAngle = steerRate = 0f;
        frontSuspension = frontSuspensionRate = 0f;
        rearSuspension = rearSuspensionRate = 0f;
        frontWheelOmega = rearWheelOmega = 0f;
        frontWheelSpin = rearWheelSpin = 0f;
        riderX = riderXRate = 0f;
        rearTireState.reset();
        frontTireState.reset();
        powertrain.reset();
        input.throttle = 0f;
        input.rearBrake = 0f;
        input.steer = 0f;
        input.riderForeAft = 0f;
        basis();
        updateTelemetry();
    }

    void step(Terrain terrain, float dt) {
        if (terrain == null || dt <= 0f) {
            return;
        }
        int substeps = Math.max(1, (int)Math.ceil(dt / MAX_INTERNAL_DT));
        float h = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            substep(terrain, h);
        }
    }

    private void substep(Terrain terrain, float dt) {
        basis();
        rotateBodyToWorld(wx, wy, wz, omegaWorld);

        float speedForward = dotVelocity(forward);
        float speedHorizontal = (float)Math.sqrt(vx * vx + vz * vz);

        float riderTarget = clamp(input.riderForeAft, -1f, 1f) * RIDER_TRAVEL;
        float riderAccel = RIDER_KP * (riderTarget - riderX) - RIDER_KD * riderXRate;
        riderXRate += riderAccel * dt;
        riderX += riderXRate * dt;
        riderX = clamp(riderX, -RIDER_TRAVEL, RIDER_TRAVEL);

        float riderCgShift = RIDER_MASS / TOTAL_MASS * riderX;
        float effectiveCgForward = BIKE_CG_FORWARD + riderCgShift;

        sampleContact(
                rearContact,
                terrain,
                false,
                -effectiveCgForward,
                rearSuspension,
                rearSuspensionRate,
                dt);
        sampleContact(
                frontContact,
                terrain,
                true,
                WHEELBASE - effectiveCgForward,
                frontSuspension,
                frontSuspensionRate,
                dt);

        integrateSuspension(
                false,
                rearContact.normalForce,
                dt);
        integrateSuspension(
                true,
                frontContact.normalForce,
                dt);

        updateSteering(speedForward, dt);

        float clutchCommand;
        if (input.throttle < 0.025f && speedHorizontal < 0.7f) {
            clutchCommand = 0f;
        } else {
            clutchCommand = clamp(
                    0.06f + 0.20f * input.throttle + speedHorizontal / 6.5f,
                    0f,
                    1f);
        }

        float rearDriveTorque = powertrain.step(
                rearWheelOmega,
                input.throttle,
                clutchCommand,
                dt);
        float rearBrakeTorque = clamp(input.rearBrake, 0f, 1f)
                * MAX_REAR_BRAKE_TORQUE;
        float brakeSign = Math.abs(rearWheelOmega) > 0.25f
                ? Math.signum(rearWheelOmega) : 1f;

        float rearAlpha = (
                rearDriveTorque
                        - rearTireForce.fx * PhysicsV3PacejkaTire.REAR.effectiveRadius
                        - brakeSign * rearBrakeTorque)
                / REAR_WHEEL_INERTIA;
        float frontAlpha = (
                -frontTireForce.fx * PhysicsV3PacejkaTire.FRONT.effectiveRadius)
                / FRONT_WHEEL_INERTIA;

        rearWheelOmega += rearAlpha * dt;
        frontWheelOmega += frontAlpha * dt;
        if (input.rearBrake > 0f && rearWheelOmega * brakeSign < 0f) {
            rearWheelOmega = 0f;
        }
        rearWheelSpin += rearWheelOmega * dt;
        frontWheelSpin += frontWheelOmega * dt;

        totalForce.set(0f, -TOTAL_MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        addContact(rearContact, rearTireForce, totalForce, totalMomentWorld);
        addContact(frontContact, frontTireForce, totalForce, totalMomentWorld);

        if (speedHorizontal > 0.05f) {
            float inv = 1f / speedHorizontal;
            float normalLoad = rearContact.normalForce + frontContact.normalForce;
            float drag = AERO_COEFF * speedHorizontal * speedHorizontal;
            float rolling = ROLLING_COEFF * normalLoad;
            totalForce.x -= vx * inv * (drag + rolling);
            totalForce.z -= vz * inv * (drag + rolling);
        }

        // Wheel-spin angular momentum belongs to the full multibody system.
        // Subtract its derivative from the chassis attitude equation. This gives
        // real throttle/brake attitude authority in the air without an air mode.
        float spinReaction = -REAR_WHEEL_INERTIA * rearAlpha
                - FRONT_WHEEL_INERTIA * frontAlpha;
        addAxisMoment(totalMomentWorld, right, spinReaction);
        addAxisMoment(
                totalMomentWorld,
                right,
                -ENGINE_INERTIA * powertrain.getEngineAlpha());

        addWheelGyroReaction(rearContact.wheelRight, rearWheelOmega, 0f);
        addWheelGyroReaction(frontContact.wheelRight, frontWheelOmega, steerRate);

        // Rider fore/aft acceleration exchanges angular momentum with the frame.
        // Positive rider acceleration is forward; its reaction above the CG is
        // a nose-up body torque (negative body-X in this coordinate convention).
        float riderPitchReaction = -RIDER_MASS * RIDER_HEIGHT_ABOVE_CG * riderAccel;
        addAxisMoment(totalMomentWorld, right, riderPitchReaction);

        boolean anyGround = rearGrounded() || frontGrounded();
        if (anyGround) {
            float balanceFade = 1f - smoothstep(1.4f, 4.2f, Math.abs(speedForward));
            float upright = -LOW_SPEED_BALANCE_KP * roll()
                    - LOW_SPEED_BALANCE_KD * rollRate();
            float rollMoment = clamp(
                    upright,
                    -MAX_LOW_SPEED_BALANCE,
                    MAX_LOW_SPEED_BALANCE) * balanceFade;
            // Rate-only damper stays on at speed so a pickup does not flop through upright.
            rollMoment += clamp(-42f * rollRate(), -90f, 90f);
            addAxisMoment(totalMomentWorld, forward, rollMoment);
            addAxisMoment(totalMomentWorld, up, -YAW_DAMPING * yawRate());
        }

        if (rearGrounded()) {
            float balancePitch = (float)Math.atan2(
                    Math.max(0.05f, effectiveCgForward),
                    CG_HEIGHT);
            float catchStart = balancePitch + WHEELIE_CATCH_OFFSET;
            if (pitch() > catchStart || pitchRate() > 1.6f) {
                float catchTorque = WHEELIE_CATCH_KP
                        * Math.max(0f, pitch() - catchStart)
                        + WHEELIE_CATCH_KD * Math.max(0f, pitchRate() - 0.8f);
                catchTorque = clamp(catchTorque, 0f, MAX_WHEELIE_CATCH);
                // Positive body-X is nose-down.
                addAxisMoment(totalMomentWorld, right, catchTorque);
            }
        }

        vx += totalForce.x / TOTAL_MASS * dt;
        vy += totalForce.y / TOTAL_MASS * dt;
        vz += totalForce.z / TOTAL_MASS * dt;
        px += vx * dt;
        py += vy * dt;
        pz += vz * dt;

        rotateWorldToBody(
                totalMomentWorld.x,
                totalMomentWorld.y,
                totalMomentWorld.z,
                tmpA);

        float gx = (I_ROLL - I_YAW) * wy * wz;
        float gy = (I_PITCH - I_ROLL) * wz * wx;
        float gz = (I_YAW - I_PITCH) * wx * wy;
        wx += (tmpA.x - gx) / I_PITCH * dt;
        wy += (tmpA.y - gy) / I_YAW * dt;
        wz += (tmpA.z - gz) / I_ROLL * dt;
        integrateQuaternion(dt);

        if (!finite()) {
            reset(terrain);
        }
        updateTelemetry();
    }

    private void updateSteering(float speedForward, float dt) {
        float speed = Math.abs(speedForward);
        boolean anyGround = rearGrounded() || frontGrounded();
        float controllerTorque;

        if (anyGround) {
            float command = clamp(input.steer, -1f, 1f);
            float authority = smoothstep(1.2f, 5.5f, speed);
            float targetRoll = -command * MAX_TARGET_LEAN * authority;

            // Right lean is negative roll. A positive roll error means the bike is not
            // leaned into the requested turn yet, so the bars steer the other way.
            // That contact-patch force is what rolls the chassis. Into-the-turn steer
            // is only the steady term, taken from the lean that already exists.
            float rollError = roll() - targetRoll;
            float countersteer = clamp(
                    -COUNTERSTEER_KP * rollError - COUNTERSTEER_KD * rollRate(),
                    -MAX_COUNTERSTEER,
                    MAX_COUNTERSTEER);
            float speedSq = Math.max(speed * speed, 16f);
            float steadySteer = -(float) Math.atan(
                    WHEELBASE * G * (float) Math.tan(roll()) / speedSq);

            float lowSpeed = 1f - smoothstep(0.8f, 3.0f, speed);
            float desiredSteer = clamp(
                    command * MAX_STEER_ANGLE * lowSpeed
                            + (steadySteer + countersteer) * (1f - lowSpeed),
                    -MAX_STEER_ANGLE,
                    MAX_STEER_ANGLE);

            controllerTorque = STEER_SERVO_KP * (desiredSteer - steerAngle)
                    - STEER_SERVO_KD * steerRate;
        } else {
            // Airborne behavior intentionally unchanged: no steering recenter servo.
            controllerTorque = -2.2f * steerRate;
        }

        float tireAligning = frontGrounded()
                ? STEER_ALIGNING_GAIN * frontTireForce.mz : 0f;
        float steerTorque = clamp(
                controllerTorque + tireAligning,
                -MAX_STEER_TORQUE,
                MAX_STEER_TORQUE);
        steerRate += steerTorque / STEER_INERTIA * dt;
        steerAngle += steerRate * dt;

        if (steerAngle > MAX_STEER_ANGLE) {
            steerAngle = MAX_STEER_ANGLE;
            steerRate = Math.min(0f, steerRate);
        } else if (steerAngle < -MAX_STEER_ANGLE) {
            steerAngle = -MAX_STEER_ANGLE;
            steerRate = Math.max(0f, steerRate);
        }

        // Do not inject an artificial equal/opposite yaw kick into the chassis. The front
        // contact force already produces the yaw moment about the CG. The old -0.18 reaction
        // made the bike initially yaw opposite the player's command and contributed to weave.
    }

    private void integrateSuspension(boolean front, float normalForce, float dt) {
        float staticLoad = front ? staticFrontLoad() : staticRearLoad();
        float q = front ? frontSuspension : rearSuspension;
        float qd = front ? frontSuspensionRate : rearSuspensionRate;
        float rate = front ? FRONT_WHEEL_RATE : REAR_WHEEL_RATE;
        float mass = front ? FRONT_SUSPENSION_MASS : REAR_SUSPENSION_MASS;
        float comp = front ? FRONT_COMP_DAMP : REAR_COMP_DAMP;
        float rebound = front ? FRONT_REBOUND_DAMP : REAR_REBOUND_DAMP;
        float damping = qd >= 0f ? comp : rebound;

        float qdd = (normalForce - staticLoad - rate * q - damping * qd) / mass;
        qd += qdd * dt;
        q += qd * dt;

        float staticCompression = front
                ? FRONT_STATIC_COMPRESSION : REAR_STATIC_COMPRESSION;
        float travel = front ? FRONT_TRAVEL : REAR_TRAVEL;
        float minQ = -staticCompression;
        float maxQ = travel - staticCompression;
        if (q < minQ) {
            q = minQ;
            qd = Math.max(0f, qd);
        } else if (q > maxQ) {
            q = maxQ;
            qd = Math.min(0f, qd);
        }

        if (front) {
            frontSuspension = q;
            frontSuspensionRate = qd;
        } else {
            rearSuspension = q;
            rearSuspensionRate = qd;
        }
    }

    private void sampleContact(Contact c, Terrain terrain, boolean front,
                               float localForward, float suspension,
                               float suspensionRate, float dt) {
        PhysicsV3PacejkaTire.Spec spec = front
                ? PhysicsV3PacejkaTire.FRONT : PhysicsV3PacejkaTire.REAR;
        float staticLoad = front ? staticFrontLoad() : staticRearLoad();
        float tireK = front ? FRONT_TIRE_K : REAR_TIRE_K;
        float tireC = front ? FRONT_TIRE_C : REAR_TIRE_C;
        float staticTireDeflection = staticLoad / tireK;
        float hubLocalY = spec.radius - staticTireDeflection - CG_HEIGHT
                + suspension;

        rotateBodyToWorld(0f, hubLocalY, localForward, c.rel);
        c.center.set(px + c.rel.x, py + c.rel.y, pz + c.rel.z);

        float sx = terrain.slopeX(c.center.x, c.center.z);
        float sz = terrain.slopeZ(c.center.x, c.center.z);
        c.normal.set(-sx, 1f, -sz).nor();
        c.groundHeight = terrain.height(c.center.x, c.center.z);
        c.traction = terrain.traction(c.center.x, c.center.z);

        if (front) {
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
            c.wheelRight.set(right);
        }

        cross(omegaWorld, c.rel, c.tmpA);
        c.centerVelocity.set(
                vx + c.tmpA.x + up.x * suspensionRate,
                vy + c.tmpA.y + up.y * suspensionRate,
                vz + c.tmpA.z + up.z * suspensionRate);

        c.groundPoint.set(c.center.x, c.groundHeight, c.center.z);
        c.tmpB.set(
                c.center.x - c.groundPoint.x,
                c.center.y - c.groundPoint.y,
                c.center.z - c.groundPoint.z);
        float signedDistance = c.tmpB.dot(c.normal);
        float penetration = spec.radius - signedDistance;
        float penetrationRate = -c.centerVelocity.dot(c.normal);

        float normalForce = 0f;
        if (penetration > 0f) {
            normalForce = tireK * penetration + tireC * penetrationRate;
            normalForce = clamp(normalForce, 0f, MAX_TIRE_LOAD);
        }
        c.normalForce = normalForce;

        float longSpeed = c.centerVelocity.dot(c.wheelForward);
        float lateralSpeed = c.centerVelocity.dot(c.wheelRight);
        float wheelOmega = front ? frontWheelOmega : rearWheelOmega;
        float denom = Math.max(Math.abs(longSpeed), 2f);
        c.kappa = (wheelOmega * spec.effectiveRadius - longSpeed) / denom;
        c.alpha = (float)Math.atan2(
                -lateralSpeed,
                Math.max(Math.abs(longSpeed), 1f));
        c.gamma = -(float)Math.asin(clamp(c.wheelRight.dot(c.normal), -1f, 1f));

        PhysicsV3PacejkaTire.State state = front ? frontTireState : rearTireState;
        PhysicsV3PacejkaTire.Force force = front ? frontTireForce : rearTireForce;
        PhysicsV3PacejkaTire.step(
                spec,
                state,
                force,
                normalForce,
                c.alpha,
                c.kappa,
                c.gamma,
                longSpeed,
                dt,
                c.traction);

        c.contactPoint.set(
                c.center.x - c.normal.x * spec.radius,
                c.center.y - c.normal.y * spec.radius,
                c.center.z - c.normal.z * spec.radius);
        c.contactArm.set(
                c.contactPoint.x - px,
                c.contactPoint.y - py,
                c.contactPoint.z - pz);
    }

    private void addContact(Contact c, PhysicsV3PacejkaTire.Force tire,
                            Vec forceSum, Vec momentSum) {
        if (c.normalForce <= 0f) {
            return;
        }
        c.force.set(
                c.normal.x * c.normalForce
                        + c.wheelForward.x * tire.fx
                        + c.wheelRight.x * tire.fy,
                c.normal.y * c.normalForce
                        + c.wheelForward.y * tire.fx
                        + c.wheelRight.y * tire.fy,
                c.normal.z * c.normalForce
                        + c.wheelForward.z * tire.fx
                        + c.wheelRight.z * tire.fy);
        forceSum.add(c.force);
        cross(c.contactArm, c.force, c.tmpA);
        momentSum.add(c.tmpA);
        addAxisMoment(momentSum, c.normal, tire.mz);
    }

    private void addWheelGyroReaction(Vec wheelAxis, float wheelOmega, float steerRateLocal) {
        float inertia = wheelAxis == rearContact.wheelRight
                ? REAR_WHEEL_INERTIA : FRONT_WHEEL_INERTIA;
        tmpA.set(
                wheelAxis.x * inertia * wheelOmega,
                wheelAxis.y * inertia * wheelOmega,
                wheelAxis.z * inertia * wheelOmega);
        cross(omegaWorld, tmpA, tmpB);
        totalMomentWorld.x -= tmpB.x;
        totalMomentWorld.y -= tmpB.y;
        totalMomentWorld.z -= tmpB.z;

        if (steerRateLocal != 0f) {
            tmpA.set(
                    up.x * steerRateLocal,
                    up.y * steerRateLocal,
                    up.z * steerRateLocal);
            tmpB.set(
                    wheelAxis.x * inertia * wheelOmega,
                    wheelAxis.y * inertia * wheelOmega,
                    wheelAxis.z * inertia * wheelOmega);
            cross(tmpA, tmpB, tmpA);
            totalMomentWorld.x -= tmpA.x;
            totalMomentWorld.y -= tmpA.y;
            totalMomentWorld.z -= tmpA.z;
        }
    }

    private void addAxisMoment(Vec sum, Vec axis, float magnitude) {
        sum.x += axis.x * magnitude;
        sum.y += axis.y * magnitude;
        sum.z += axis.z * magnitude;
    }

    boolean shiftUp() {
        return powertrain.shiftUp();
    }

    boolean shiftDown() {
        return powertrain.shiftDown(rearWheelOmega);
    }

    float x() {
        return px;
    }

    float y() {
        return py;
    }

    float z() {
        return pz;
    }

    float speed() {
        basis();
        return dotVelocity(forward);
    }

    float verticalVelocity() {
        return vy;
    }

    float pitch() {
        basis();
        return (float)Math.atan2(
                forward.y,
                Math.sqrt(forward.x * forward.x + forward.z * forward.z));
    }

    float yaw() {
        basis();
        return (float)Math.atan2(forward.x, forward.z);
    }

    float roll() {
        basis();
        return (float)Math.atan2(right.y, up.y);
    }

    float pitchRate() {
        return -wx;
    }

    float yawRate() {
        return wy;
    }

    float rollRate() {
        return wz;
    }

    float steerAngle() {
        return steerAngle;
    }

    float steerNormalized() {
        return steerAngle / MAX_STEER_ANGLE;
    }

    float rearCompression() {
        return REAR_STATIC_COMPRESSION + rearSuspension;
    }

    float frontCompression() {
        return FRONT_STATIC_COMPRESSION + frontSuspension;
    }

    float rearWheelSpin() {
        return rearWheelSpin;
    }

    float frontWheelSpin() {
        return frontWheelSpin;
    }

    boolean rearGrounded() {
        return rearContact.normalForce > 10f;
    }

    boolean frontGrounded() {
        return frontContact.normalForce > 10f;
    }

    boolean airborne() {
        return !rearGrounded() && !frontGrounded();
    }

    private float dotVelocity(Vec axis) {
        return vx * axis.x + vy * axis.y + vz * axis.z;
    }

    private float staticFrontLoad() {
        return TOTAL_MASS * G * BIKE_CG_FORWARD / WHEELBASE;
    }

    private float staticRearLoad() {
        return TOTAL_MASS * G - staticFrontLoad();
    }

    private void updateTelemetry() {
        basis();
        telemetry.rearFz = rearContact.normalForce;
        telemetry.frontFz = frontContact.normalForce;
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
        telemetry.rearSuspension = rearCompression();
        telemetry.frontSuspension = frontCompression();
        telemetry.steerAngle = steerAngle;
        telemetry.engineRpm = powertrain.getRpm();
        telemetry.speed = dotVelocity(forward);
        telemetry.rearGrounded = rearGrounded();
        telemetry.frontGrounded = frontGrounded();
    }

    private void basis() {
        rotateBodyToWorld(1f, 0f, 0f, right);
        rotateBodyToWorld(0f, 1f, 0f, up);
        rotateBodyToWorld(0f, 0f, 1f, forward);
    }

    private void integrateQuaternion(float dt) {
        float halfDt = 0.5f * dt;
        float dx = halfDt * (qw * wx + qy * wz - qz * wy);
        float dy = halfDt * (qw * wy + qz * wx - qx * wz);
        float dz = halfDt * (qw * wz + qx * wy - qy * wx);
        float dw = halfDt * (-qx * wx - qy * wy - qz * wz);
        qx += dx;
        qy += dy;
        qz += dz;
        qw += dw;
        float inv = 1f / (float)Math.sqrt(
                qx * qx + qy * qy + qz * qz + qw * qw);
        qx *= inv;
        qy *= inv;
        qz *= inv;
        qw *= inv;
    }

    private void rotateBodyToWorld(float x, float y, float z, Vec out) {
        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float wxq = qw * qx;
        float wyq = qw * qy;
        float wzq = qw * qz;
        out.set(
                (1f - 2f * (yy + zz)) * x
                        + 2f * (xy - wzq) * y
                        + 2f * (xz + wyq) * z,
                2f * (xy + wzq) * x
                        + (1f - 2f * (xx + zz)) * y
                        + 2f * (yz - wxq) * z,
                2f * (xz - wyq) * x
                        + 2f * (yz + wxq) * y
                        + (1f - 2f * (xx + yy)) * z);
    }

    private void rotateWorldToBody(float x, float y, float z, Vec out) {
        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float wxq = qw * qx;
        float wyq = qw * qy;
        float wzq = qw * qz;
        out.set(
                (1f - 2f * (yy + zz)) * x
                        + 2f * (xy + wzq) * y
                        + 2f * (xz - wyq) * z,
                2f * (xy - wzq) * x
                        + (1f - 2f * (xx + zz)) * y
                        + 2f * (yz + wxq) * z,
                2f * (xz + wyq) * x
                        + 2f * (yz - wxq) * y
                        + (1f - 2f * (xx + yy)) * z);
    }

    private boolean finite() {
        return finite(px) && finite(py) && finite(pz)
                && finite(vx) && finite(vy) && finite(vz)
                && finite(qx) && finite(qy) && finite(qz) && finite(qw)
                && finite(wx) && finite(wy) && finite(wz)
                && finite(frontWheelOmega) && finite(rearWheelOmega)
                && finite(frontSuspension) && finite(rearSuspension);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
    }

    private static float smoothstep(float lo, float hi, float x) {
        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static Vec cross(Vec a, Vec b, Vec out) {
        return out.set(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    private static final class Vec {
        float x;
        float y;
        float z;

        Vec set(float nx, float ny, float nz) {
            x = nx;
            y = ny;
            z = nz;
            return this;
        }

        Vec set(Vec other) {
            return set(other.x, other.y, other.z);
        }

        Vec add(Vec other) {
            x += other.x;
            y += other.y;
            z += other.z;
            return this;
        }

        float dot(Vec other) {
            return x * other.x + y * other.y + z * other.z;
        }

        Vec nor() {
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            if (length > 1e-8f) {
                x /= length;
                y /= length;
                z /= length;
            }
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
        final Vec groundPoint = new Vec();
        final Vec contactPoint = new Vec();
        final Vec contactArm = new Vec();
        final Vec force = new Vec();
        final Vec tmpA = new Vec();
        final Vec tmpB = new Vec();
        float groundHeight;
        float traction;
        float normalForce;
        float kappa;
        float alpha;
        float gamma;
    }
}
