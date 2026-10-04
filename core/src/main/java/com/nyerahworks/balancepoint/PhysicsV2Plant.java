package com.nyerahworks.balancepoint;

/**
 * First implementation of the assists-off Physics V2 motorcycle plant.
 *
 * Coordinates: world X right, Y up, Z forward. Body local axes are the same at identity.
 * Wheelies are a rear-contact balance point: gravity torque is zero when the combined
 * CoM sits over the rear patch, throttle and rearward weight raise the front, rear brake
 * drops it. No scripted anti-wheelie clamp.
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
        float riderForeAft;
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
    // The outer game loop is 120 Hz, but clutch/contact/unsprung modes are faster.
    // Keep integration at or below 1/480 s to stop those modes aliasing into chassis motion.
    private static final float MAX_INTERNAL_DT = 1f / 480f;
    // A real rider anticipates acceleration and braces forward. This changes rider mass position
    // only; it never applies an anti-wheelie chassis torque or clamps pitch. Kept small so a
    // rearward stick command can still move the combined CoM behind the neutral brace.
    private static final float VIRTUAL_RIDER_BRACE_GAIN = 0.28f;
    private static final float VIRTUAL_RIDER_BRACE_MAX = 0.28f;
    private static final String PHYSICS_V2_STABILITY_PASS_1 =
            "substep-feedback-cleanup-v1";
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

    // Grounded rideability. Roll/steer assists stay. Wheelie control is a balance point,
    // not an anti-wheelie clamp. MX vs ATV Legends (assist off) hangs the bike where the
    // combined CoM sits over the rear contact, holds it with throttle taps, and uses rear
    // brake / forward weight to drop the nose. The catch only starts past that point so a
    // wheelie is recoverable instead of an instant loop, matching Legends' raised wheelie
    // threshold. It does not pin the bike at a low angle or cut drive to fake stability.
    private static final String PHYSICS_V2_GROUNDED_ASSISTS_V1 =
            "legends-balance-point-v1";
    private static final float ASSIST_MAX_LEAN = radians(38f);
    // Critical damping on the 40 kg m^2 roll inertia. Upright gain stays on even
    // with the stick centered, so the bike stands itself up instead of staying flopped.
    private static final float ASSIST_ROLL_KP = 240f;
    private static final float ASSIST_ROLL_KD = 210f;
    private static final float ASSIST_MAX_ROLL_TORQUE = 340f;
    private static final float ASSIST_UPRIGHT_KP = 280f;
    private static final float ASSIST_UPRIGHT_KD = 96f;
    private static final float ASSIST_STEER_DAMPING = 1.2f;
    private static final float ASSIST_YAW_RATE_GAIN = 1f / 9.5f;
    private static final float ASSIST_MAX_YAW_RATE = 1.45f;
    private static final float ASSIST_YAW_KP = 32f;
    private static final float ASSIST_MAX_YAW_TORQUE = 70f;
    private static final float BALANCE_CATCH_PAST = radians(7f);
    private static final float BALANCE_CATCH_FULL = radians(16f);
    private static final float BALANCE_CATCH_MAX = 220f;
    private static final float BALANCE_RATE_DAMP = 70f;
    private static final float PLANTED_PITCH_SPRING = 160f;
    private static final float PLANTED_PITCH_DAMP = 95f;
    private static final float BRAKE_BALANCE_TORQUE = 420f;
    private static final float RIDER_POP_TORQUE = 0f;

    private final Input input = new Input();
    private final Telemetry telemetry = new Telemetry();
    private final PhysicsV2Powertrain powertrain = new PhysicsV2Powertrain();
    private final PhysicsV2RiderController riderController = new PhysicsV2RiderController();
    private final MotorcycleRiderDynamics riderLongitudinal =
            new MotorcycleRiderDynamics(TOTAL_MASS, G);
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
        riderController.reset();
        riderLongitudinal.reset();
        input.throttle = input.rearBrake = input.steer = input.riderForeAft = 0f;
        updateTelemetry();
    }

    void step(Terrain terrain, float dt) {
        if (terrain == null || dt <= 0f) return;
        int substeps = Math.max(1, (int)Math.ceil(dt / MAX_INTERNAL_DT));
        float h = dt / substeps;
        for (int i = 0; i < substeps; i++) substep(terrain, h);
    }

    private void substep(Terrain terrain, float dt) {
        basis();
        rotateBodyToWorld(wx, wy, wz, omegaWorld);

        float forwardSpeedStart = forward.x * vx + forward.y * vy + forward.z * vz;
        // Use an anticipatory virtual-rider brace tied to requested engine effort. The previous
        // acceleration-derived brace fed body rotation back into CG location and could create a
        // self-exciting geometry loop. This command is deterministic and position-only.
        float virtualBrace = clamp(
                input.throttle * VIRTUAL_RIDER_BRACE_GAIN,
                0f,
                VIRTUAL_RIDER_BRACE_MAX);
        riderLongitudinal.step(
                clamp(input.riderForeAft + virtualBrace, -1f, 1f),
                0f,
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
        float frontFullExtY = PhysicsV2Tire.FRONT.radius - frontStaticDeflection
                - CG_HEIGHT - FRONT_STATIC_COMPRESSION;
        float rearFullExtY = PhysicsV2Tire.REAR.radius - rearStaticDeflection
                - CG_HEIGHT - REAR_STATIC_COMPRESSION;

        sampleContact(
                rearContact,
                terrain,
                -CG_FORWARD,
                rearFullExtY + rearCompression,
                0f,
                rearCompressionRate,
                false,
                dt);
        sampleContact(
                frontContact,
                terrain,
                WHEELBASE - CG_FORWARD,
                frontFullExtY + frontCompression,
                0f,
                frontCompressionRate,
                true,
                dt);

        // The unsprung masses receive tire load. Spring/damper force reacts through the chassis.
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
        float riderSteerTorque = riderSteer * MAX_STEER_TORQUE;
        float groundedSteerDamping = airborne() ? 0f : ASSIST_STEER_DAMPING;
        float steeringDampingTorque = -(STEER_DAMPING + groundedSteerDamping) * steerRate;
        float steerTorque = riderSteerTorque + frontTireForce.mz
                + steeringDampingTorque;
        steerRate += steerTorque / STEER_INERTIA * dt;
        steerAngle += steerRate * dt;
        if (steerAngle > MAX_STEER_ANGLE) { steerAngle = MAX_STEER_ANGLE; steerRate = Math.min(0f, steerRate); }
        if (steerAngle < -MAX_STEER_ANGLE) { steerAngle = -MAX_STEER_ANGLE; steerRate = Math.max(0f, steerRate); }

        float horizontalSpeed = (float)Math.sqrt(vx * vx + vz * vz);
        // Automatic launch clutch is a physical plate-force command, not an RPM target.
        // With the throttle closed at rest the clutch is open, so idle cannot creep the bike.
        // Engagement grows with rider torque request and road speed while transmitted torque
        // still comes exclusively from the clutch slip law in PhysicsV2Powertrain.
        float clutchCommand;
        if (input.throttle < 0.03f && horizontalSpeed < 0.8f) {
            clutchCommand = 0f;
        } else {
            clutchCommand = clamp(0.08f + 0.18f * input.throttle
                    + horizontalSpeed / 6.0f, 0f, 1f);
        }
        float rearDriveTorque = powertrain.step(
                rearWheelOmega,
                input.throttle,
                clutchCommand,
                dt);
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

        totalForce.set(0f, -SPRUNG_MASS * G, 0f);
        totalMomentWorld.set(0f, 0f, 0f);
        // Bar torque is an internal couple. Full reaction was yaw-kicking the chassis
        // every time the steer servo moved, which read as a weave. Keep a fraction.
        float steeringInternalReaction =
                -0.22f * (riderSteerTorque + steeringDampingTorque);
        totalMomentWorld.x += up.x * steeringInternalReaction;
        totalMomentWorld.y += up.y * steeringInternalReaction;
        totalMomentWorld.z += up.z * steeringInternalReaction;
        addContactToRigidBody(
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

        // Grounded rideability assists. No part of this block runs fully airborne.
        if (!airborne()) {
            float speedAbs = Math.abs(forwardSpeedStart);
            float leanAuthority = smoothstep(1.2f, 6.0f, speedAbs);
            float shapedSteer = clamp(input.steer, -1f, 1f);
            shapedSteer *= 0.55f + 0.45f * Math.abs(shapedSteer);
            // Positive stick is right. Negative roll is right lean in this plant.
            float targetRoll = -shapedSteer * ASSIST_MAX_LEAN * leanAuthority;
            float stickHeld = Math.min(1f, Math.abs(shapedSteer) * 1.4f);
            float upright = 1f - stickHeld;
            float rollTorque = ASSIST_ROLL_KP * (targetRoll - roll())
                    - ASSIST_ROLL_KD * rollRate();
            // Extra stand-up once the stick comes back. This is the bike trying
            // to hold itself upright, not a lean spring that goes dead at center.
            rollTorque += upright * (ASSIST_UPRIGHT_KP * (0f - roll())
                    - ASSIST_UPRIGHT_KD * rollRate());
            rollTorque = clamp(
                    rollTorque,
                    -ASSIST_MAX_ROLL_TORQUE,
                    ASSIST_MAX_ROLL_TORQUE);
            totalMomentWorld.x += forward.x * rollTorque;
            totalMomentWorld.y += forward.y * rollTorque;
            totalMomentWorld.z += forward.z * rollTorque;

            // Legends corner: stick deflection arcs the bike. Rate tracking only,
            // so it cannot ring the way a lean spring did.
            float targetYawRate = clamp(
                    shapedSteer * speedAbs * ASSIST_YAW_RATE_GAIN * leanAuthority,
                    -ASSIST_MAX_YAW_RATE,
                    ASSIST_MAX_YAW_RATE);
            float yawTorque = clamp(
                    ASSIST_YAW_KP * (targetYawRate - yawRate()),
                    -ASSIST_MAX_YAW_TORQUE,
                    ASSIST_MAX_YAW_TORQUE);
            totalMomentWorld.x += up.x * yawTorque;
            totalMomentWorld.y += up.y * yawTorque;
            totalMomentWorld.z += up.z * yawTorque;

            if (rearGrounded()) {
                // theta_bp = atan(comAhead / comHeight): gravity torque about the rear
                // contact is zero there. Nose-up pitch is positive. Body-X torque is
                // nose-up positive in this plant (drive reaction is -shaft torque).
                float balancePitch = (float)Math.atan2(
                        Math.max(0.05f, effectiveCgForward),
                        CG_HEIGHT);
                boolean frontUp = !frontGrounded() || pitch() > radians(4f);
                float noseDown = 0f;
                if (frontUp) {
                    noseDown += BALANCE_RATE_DAMP * pitchRate();
                    float pastBalance = pitch() - balancePitch;
                    if (pastBalance > 0f) {
                        noseDown += smoothstep(
                                BALANCE_CATCH_PAST,
                                BALANCE_CATCH_FULL,
                                pastBalance) * BALANCE_CATCH_MAX;
                    }
                    noseDown += clamp(input.rearBrake, 0f, 1f) * BRAKE_BALANCE_TORQUE
                            * smoothstep(radians(6f), radians(16f), pitch());
                } else {
                    noseDown += PLANTED_PITCH_SPRING * pitch();
                    noseDown += PLANTED_PITCH_DAMP * pitchRate();
                }
                // Forward rider input adds nose-down. Stick back is the Legends pop,
                // mostly from the CoM shift above, plus a small pitch nudge.
                noseDown += clamp(input.riderForeAft, -1f, 1f) * RIDER_POP_TORQUE
                        * (frontUp ? 1f : 0.5f);
                // Geometric balance point. Contact forces are applied at the axles, so
                // they do not produce the inverted-pendulum moment about the rear patch.
                // This is that missing torque: zero when the CoM is over the contact,
                // nose-down in front of it, looping past it. Rearward CoM lowers the point.
                if (!frontGrounded()) {
                    float pitchNow = pitch();
                    float offset = effectiveCgForward * (float)Math.cos(pitchNow)
                            - CG_HEIGHT * (float)Math.sin(pitchNow);
                    noseDown += SPRUNG_MASS * G * offset;
                }
                float noseUpTorque = -noseDown;
                totalMomentWorld.x += right.x * noseUpTorque;
                totalMomentWorld.y += right.y * noseUpTorque;
                totalMomentWorld.z += right.z * noseUpTorque;
            }
        }

        vx += totalForce.x / SPRUNG_MASS * dt;
        vy += totalForce.y / SPRUNG_MASS * dt;
        vz += totalForce.z / SPRUNG_MASS * dt;
        px += vx * dt;
        py += vy * dt;
        pz += vz * dt;

        Vec torqueBody = new Vec();
        rotateWorldToBody(totalMomentWorld.x, totalMomentWorld.y, totalMomentWorld.z, torqueBody);

        // The former hand-expanded gyro cross terms were not independently validated.
        // Omit them until wheel orientation/Jacobian coupling has conservation tests.
        // Wheel spin inertia itself remains physical.

        // Euler rigid-body equation with the conventional omega x (I omega) signs.
        float gx = (ROLL_INERTIA - YAW_INERTIA) * wy * wz;
        float gy = (PITCH_INERTIA - ROLL_INERTIA) * wz * wx;
        float gz = (YAW_INERTIA - PITCH_INERTIA) * wx * wy;
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

    private float wheelForceAlongUp(Contact c, PhysicsV2Tire.Force f) {
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
            boolean isFront,
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

    private static float smoothstep(float lo, float hi, float x) {
        float t = clamp((x - lo) / Math.max(hi - lo, 0.001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float radians(float degrees) {
        return degrees * (float)Math.PI / 180f;
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
