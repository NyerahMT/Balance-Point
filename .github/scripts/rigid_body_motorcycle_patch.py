from pathlib import Path

p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

# Add contact-model constants next to the chassis parameters.
old = '''    private static final float PITCH_INERTIA = 168f;\n    private static final float COM_FORWARD = 0.60f;\n'''
new = '''    private static final float PITCH_INERTIA = 168f;\n    // Unified wheel/terrain contact. Wheels generate forces continuously from local terrain\n    // penetration and point velocity; wheelies, jumps and landings are results, not modes.\n    private static final float CONTACT_STIFFNESS = 52_000f;\n    private static final float CONTACT_DAMPING = 4_500f;\n    private static final float CONTACT_PRELOAD_GAP = 0.018f;\n    private static final float CONTACT_BUMP_START = 0.050f;\n    private static final float CONTACT_BUMP_STIFFNESS = 180_000f;\n    private static final float MAX_CONTACT_FORCE = MASS * GRAVITY * 10f;\n    private static final float RIGID_PITCH_DAMPING = 0.45f;\n    private static final float COM_FORWARD = 0.60f;\n'''
if old not in s:
    raise SystemExit('constants anchor not found')
s = s.replace(old, new, 1)

# COM vertical position is now the translational body state. bikeY remains the derived rear axle
# height for camera/UI compatibility.
old = '''    private float bikeY = WHEEL_RADIUS;\n    private float verticalVelocity;\n'''
new = '''    private float bikeY = WHEEL_RADIUS;\n    private float chassisY = WHEEL_RADIUS + COM_HEIGHT;\n    private float verticalVelocity;\n'''
if old not in s:
    raise SystemExit('chassisY anchor not found')
s = s.replace(old, new, 1)

# Add compact reusable contact state immediately before create().
anchor = '''    private float wheelieTime;\n    private float bestWheelieTime;\n    private double physicsAccumulator;\n\n    @Override\n'''
insert = '''    private float wheelieTime;\n    private float bestWheelieTime;\n    private double physicsAccumulator;\n\n    private static final class WheelContact {\n        float normalForce;\n        float normalForward;\n        float normalUp;\n        float groundY;\n        float gap;\n\n        boolean touching() {\n            return normalForce > 12f;\n        }\n    }\n\n    private final WheelContact rearContactState = new WheelContact();\n    private final WheelContact frontContactState = new WheelContact();\n\n    @Override\n'''
if anchor not in s:
    raise SystemExit('contact state anchor not found')
s = s.replace(anchor, insert, 1)

# Replace the entire riding dynamics function. Crash handling remains separate for now; normal
# riding no longer switches between wheelie/airborne/landing pitch state machines.
start = s.index('    private void simulate(float dt) {')
end = s.index('    private void beginCrash()', start)
new_sim = r'''    private void simulate(float dt) {
        if (crashed) {
            simulateCrash(dt);
            return;
        }

        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);

        // Axle locations relative to the COM, expressed in the forward/up plane.
        float rearForward = -COM_FORWARD * cosPitch + COM_HEIGHT * sinPitch;
        float rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;
        float frontForward = (WHEELBASE - COM_FORWARD) * cosPitch + COM_HEIGHT * sinPitch;
        float frontVertical = (WHEELBASE - COM_FORWARD) * sinPitch - COM_HEIGHT * cosPitch;

        float rearX = bikeX + sinYaw * rearForward;
        float rearZ = bikeZ + cosYaw * rearForward;
        float frontX = bikeX + sinYaw * frontForward;
        float frontZ = bikeZ + cosYaw * frontForward;
        float rearY = chassisY + rearVertical;
        float frontY = chassisY + frontVertical;

        // Point velocity = COM translation + angular velocity x radius. This is what lets one
        // wheel leave/strike the terrain independently without any explicit landing mode.
        float rearForwardVelocity = speed - pitchVelocity * rearVertical;
        float rearVerticalVelocity = verticalVelocity + pitchVelocity * rearForward;
        float frontForwardVelocity = speed - pitchVelocity * frontVertical;
        float frontVerticalVelocity = verticalVelocity + pitchVelocity * frontForward;

        solveWheelContact(rearContactState, rearX, rearY, rearZ,
                rearForwardVelocity, rearVerticalVelocity, sinYaw, cosYaw);
        solveWheelContact(frontContactState, frontX, frontY, frontZ,
                frontForwardVelocity, frontVerticalVelocity, sinYaw, cosYaw);

        boolean rearTouching = rearContactState.touching();
        boolean frontTouching = frontContactState.touching();
        terrainAirborne = !rearTouching && !frontTouching;
        frontGrounded = frontTouching;
        frontNormalLoad = frontContactState.normalForce;

        float absSpeed = Math.abs(speed);
        boolean offRoad = Math.abs(bikeX) > ROAD_HALF_WIDTH;

        // Tire force exists only through a real rear contact patch and is friction-limited by
        // the normal force solved above. The drivetrain can rev in the air, but cannot push the
        // chassis without a ground reaction.
        float drivetrainForce = drivetrain.update(absSpeed, throttle, dt);
        float rearTractionLimit = TIRE_MU * rearContactState.normalForce;
        float driveForce = rearTouching
                ? Math.min(Math.min(MAX_ENGINE_FORCE, drivetrainForce), rearTractionLimit) : 0f;
        float brakeForce = 0f;
        if (rearTouching && rearBrake > 0f && absSpeed > 0.03f) {
            brakeForce = Math.min(MAX_REAR_BRAKE_FORCE * rearBrake, rearTractionLimit * 0.98f);
            brakeForce = Math.min(brakeForce, absSpeed * MASS / Math.max(dt, 0.001f));
        }
        float rearTireForce = driveForce - brakeForce;

        // Surface tangent is perpendicular to the solved terrain normal in the forward/up
        // plane. Engine and rear-brake force therefore naturally gain/lose vertical component
        // on a slope instead of using a separate scripted grade-acceleration rule.
        float rearTangentForward = rearContactState.normalUp;
        float rearTangentUp = -rearContactState.normalForward;
        float rearTireForward = rearTireForce * rearTangentForward;
        float rearTireUp = rearTireForce * rearTangentUp;

        float rearNormalForward = rearContactState.normalForce * rearContactState.normalForward;
        float rearNormalUp = rearContactState.normalForce * rearContactState.normalUp;
        float frontNormalForward = frontContactState.normalForce * frontContactState.normalForward;
        float frontNormalUp = frontContactState.normalForce * frontContactState.normalUp;

        float rolling = (rearContactState.normalForce + frontContactState.normalForce)
                * ROLLING_RESISTANCE;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = (rearTouching || frontTouching) && offRoad ? 95f + absSpeed * 5f : 0f;

        float totalForwardForce = rearNormalForward + frontNormalForward + rearTireForward
                - rolling - aero - surfaceDrag;
        float totalVerticalForce = rearNormalUp + frontNormalUp + rearTireUp - MASS * GRAVITY;

        longitudinalAcceleration = totalForwardForce / MASS;
        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);
        verticalVelocity += totalVerticalForce / MASS * dt;

        // Rear-wheel angular speed is coupled to road speed while the tire is loaded. In the
        // air it becomes an independent rotating mass; accelerating it with throttle rotates
        // the chassis nose-up, and braking it rotates the chassis nose-down.
        float wheelReactionTorque = 0f;
        if (rearTouching) {
            float tangentSpeed = Math.max(0f, speed * rearTangentForward
                    + verticalVelocity * rearTangentUp);
            float coupledWheelSpeed = tangentSpeed / WHEEL_RADIUS;
            rearWheelAngularSpeed += (coupledWheelSpeed - rearWheelAngularSpeed)
                    * Math.min(1f, dt * 34f);
        } else {
            float previousWheelAngularSpeed = rearWheelAngularSpeed;
            float wheelAngularAcceleration = throttle * AIR_WHEEL_DRIVE_ACCEL
                    - rearBrake * AIR_WHEEL_BRAKE_ACCEL
                    - rearWheelAngularSpeed * AIR_WHEEL_DRAG;
            rearWheelAngularSpeed = MathUtils.clamp(rearWheelAngularSpeed
                    + wheelAngularAcceleration * dt, 0f, 225f);
            float actualAngularAcceleration = (rearWheelAngularSpeed - previousWheelAngularSpeed)
                    / Math.max(dt, 0.0001f);
            wheelReactionTorque = actualAngularAcceleration * REAR_WHEEL_INERTIA;
        }

        // Apply contact forces at actual tire contact patches and integrate the resulting moment
        // about the COM. There is no wheelie/jump/landing torque branch here.
        float rearContactForwardArm = rearForward
                - WHEEL_RADIUS * rearContactState.normalForward;
        float rearContactVerticalArm = rearVertical
                - WHEEL_RADIUS * rearContactState.normalUp;
        float frontContactForwardArm = frontForward
                - WHEEL_RADIUS * frontContactState.normalForward;
        float frontContactVerticalArm = frontVertical
                - WHEEL_RADIUS * frontContactState.normalUp;

        float rearForceForward = rearNormalForward + rearTireForward;
        float rearForceUp = rearNormalUp + rearTireUp;
        float pitchTorque = rearContactForwardArm * rearForceUp
                - rearContactVerticalArm * rearForceForward
                + frontContactForwardArm * frontNormalUp
                - frontContactVerticalArm * frontNormalForward
                + wheelReactionTorque;

        float pitchAcceleration = pitchTorque / PITCH_INERTIA
                - pitchVelocity * RIGID_PITCH_DAMPING;
        pitchVelocity += pitchAcceleration * dt;
        pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
        pitch += pitchVelocity * dt;

        // Semi-implicit translation after force/torque integration.
        bikeX += sinYaw * speed * dt;
        bikeZ += cosYaw * speed * dt;
        chassisY += verticalVelocity * dt;

        // Derived rear-axle height retained for camera/UI code. Physics itself lives at COM.
        sinPitch = MathUtils.sin(pitch);
        cosPitch = MathUtils.cos(pitch);
        rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;
        bikeY = chassisY + rearVertical;

        wheelSpin += rearWheelAngularSpeed * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        // Steering authority comes continuously from front normal load rather than a binary
        // grounded state. As the front unloads during a wheelie, steering fades naturally.
        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);
        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);
        float steerAngle = steer * maxSteerDeg * MathUtils.degreesToRadians;
        float frontAuthority = MathUtils.clamp(frontContactState.normalForce
                / (MASS * GRAVITY * 0.32f), 0f, 1f);
        float yawRate = speed > 0.35f
                ? (speed / WHEELBASE) * (float) Math.tan(steerAngle) * frontAuthority
                : 0f;
        yaw += yawRate * dt;
        if (yaw > MathUtils.PI) yaw -= MathUtils.PI2;
        if (yaw < -MathUtils.PI) yaw += MathUtils.PI2;

        // Lean remains a rider-stabilized degree of freedom for now, but its target is based on
        // the same continuous lateral acceleration rather than contact modes.
        float lateralAcceleration = speed * yawRate;
        float rollTarget = -(float) Math.atan2(lateralAcceleration, GRAVITY);
        rollTarget = MathUtils.clamp(rollTarget,
                -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);
        float rollResponse = MathUtils.lerp(1.25f, 3.1f, frontAuthority);
        roll += (rollTarget - roll) * Math.min(1f, dt * rollResponse);

        // A wheelie is now just rear contact with an unloaded front tire. No wheelie-specific
        // force model is entered.
        if (rearTouching && !frontTouching && speed > 3f) {
            wheelieTime += dt;
            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);
        } else if (frontTouching) {
            wheelieTime = 0f;
        }

        if (pitch > LOOP_ANGLE
                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)
                || Float.isNaN(roll) || Float.isNaN(bikeX) || Float.isNaN(bikeZ)
                || Float.isNaN(chassisY)) {
            beginCrash();
        }
    }

    private void solveWheelContact(WheelContact out, float worldX, float wheelY, float worldZ,
                                   float pointForwardVelocity, float pointVerticalVelocity,
                                   float sinYaw, float cosYaw) {
        float ground = terrainVisuals != null ? terrainVisuals.groundHeight(worldX, worldZ) : 0f;
        float slopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(worldX, worldZ) : 0f;
        float slopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(worldX, worldZ) : 0f;
        float forwardSlope = slopeX * sinYaw + slopeZ * cosYaw;
        float invLength = 1f / (float) Math.sqrt(1f + forwardSlope * forwardSlope);

        out.normalForward = -forwardSlope * invLength;
        out.normalUp = invLength;
        out.groundY = ground + WHEEL_RADIUS;
        out.gap = wheelY - out.groundY;

        float virtualCompression = CONTACT_PRELOAD_GAP - out.gap;
        if (virtualCompression <= 0f) {
            out.normalForce = 0f;
            return;
        }

        float normalVelocity = pointForwardVelocity * out.normalForward
                + pointVerticalVelocity * out.normalUp;
        float force = CONTACT_STIFFNESS * virtualCompression
                - CONTACT_DAMPING * normalVelocity;

        float actualPenetration = Math.max(0f, -out.gap);
        if (actualPenetration > CONTACT_BUMP_START) {
            force += (actualPenetration - CONTACT_BUMP_START) * CONTACT_BUMP_STIFFNESS;
        }
        out.normalForce = MathUtils.clamp(force, 0f, MAX_CONTACT_FORCE);
    }

'''
s = s[:start] + new_sim + s[end:]

# Reset the COM state explicitly.
old = '''        bikeX = 0f;\n        bikeY = WHEEL_RADIUS;\n        verticalVelocity = 0f;\n'''
new = '''        bikeX = 0f;\n        chassisY = WHEEL_RADIUS + COM_HEIGHT;\n        bikeY = WHEEL_RADIUS;\n        verticalVelocity = 0f;\n'''
if old not in s:
    raise SystemExit('reset anchor not found')
s = s.replace(old, new, 1)

# Rendering now uses the same world-space chassis pitch solved by physics. Do not calculate and
# add a second terrain pitch visually. Compute the rear-axle transform from the COM state.
render_start = s.index('    private void updateBikeInstances() {')
spin_anchor = s.index('        float spinDeg = -wheelSpin * MathUtils.radiansToDegrees;', render_start)
prefix_end = spin_anchor
new_prefix = r'''    private void updateBikeInstances() {
        float pitchDeg = pitch * MathUtils.radiansToDegrees;
        float rollDeg = roll * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;

        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float sinPitch = MathUtils.sin(pitch);
        float cosPitch = MathUtils.cos(pitch);
        float rearForward = -COM_FORWARD * cosPitch + COM_HEIGHT * sinPitch;
        float rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;
        float rootX = bikeX + sinYaw * rearForward;
        float rootZ = bikeZ + cosYaw * rearForward;
        float rootHeight = chassisY + rearVertical;
        bikeY = rootHeight;

        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(rootX, rootZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(rootX, rootZ) : 0f;
        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;
        float terrainRollDeg = terrainAirborne ? 0f
                : (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;

        if (crashed && crashSettled) {
            float rearTerrainHeight = terrainVisuals != null
                    ? terrainVisuals.groundHeight(rootX, rootZ) : 0f;
            rootHeight = rearTerrainHeight + 0.22f;
        }

        bikeRoot.idt().translate(rootX, rootHeight, rootZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
                .rotate(Vector3.X, -pitchDeg);

'''
s = s[:render_start] + new_prefix + s[prefix_end:]

p.write_text(s)
