from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# BalancePointGame: damp angular rates, smooth sidehill bank, add wheelie steering.
# -----------------------------------------------------------------------------
p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

s = replace_once(s,
'''    private static final float RIGID_PITCH_DAMPING = 1.35f;''',
'''    // Angular damping is contact-dependent. With both tires loaded the chassis should
    // absorb short terrain impulses instead of carrying the rotation; a rear-only wheelie
    // stays freer, and true flight remains freer still so brake-tap/throttle control works.
    private static final float PITCH_DAMPING_GROUNDED = 2.85f;
    private static final float PITCH_DAMPING_WHEELIE = 1.20f;
    private static final float PITCH_DAMPING_AIR = 0.38f;
    private static final float YAW_RATE_RESPONSE_GROUND = 9.0f;
    private static final float YAW_RATE_RESPONSE_AIR = 3.0f;
    private static final float ROLL_NATURAL_FREQUENCY = 7.0f;
    private static final float ROLL_DAMPING_RATIO = 1.10f;
    private static final float TERRAIN_ROLL_NATURAL_FREQUENCY = 5.5f;
    private static final float TERRAIN_ROLL_DAMPING_RATIO = 1.15f;''',
'angular damping constants')

s = replace_once(s,
'''    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float yaw;
    private float wheelSpin;''',
'''    private float pitch;
    private float pitchVelocity;
    private float roll;
    private float rollVelocity;
    private float yaw;
    private float yawVelocity;
    // Sidehill bank is visual/support attitude, but it is rate-damped just like the chassis
    // instead of snapping to each newly sampled terrain normal.
    private float terrainRoll;
    private float terrainRollVelocity;
    private float wheelSpin;''',
'angular state fields')

s = replace_once(s,
'''        float pitchAcceleration = pitchTorque / PITCH_INERTIA
                - pitchVelocity * RIGID_PITCH_DAMPING;
        pitchVelocity += pitchAcceleration * dt;''',
'''        float pitchDamping = frontTouching ? PITCH_DAMPING_GROUNDED
                : (rearTouching ? PITCH_DAMPING_WHEELIE : PITCH_DAMPING_AIR);
        float pitchAcceleration = pitchTorque / PITCH_INERTIA
                - pitchVelocity * pitchDamping;
        pitchVelocity += pitchAcceleration * dt;''',
'pitch rate damping')

old_steering = '''        // Steering authority comes continuously from front normal load rather than a binary
        // grounded state. As the front unloads during a wheelie, steering fades naturally.
        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);
        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);
        // UI/control convention is X<0 left, X>0 right. The world yaw convention used by
        // this prototype is opposite, so convert once here rather than reversing the pad axis.
        float steerAngle = -steer * maxSteerDeg * MathUtils.degreesToRadians;
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
'''
new_steering = '''        // Front-tire steering still comes from front load, but a wheelie no longer loses all
        // directional authority. Rear-only steering represents rider/handlebar/gyro steering
        // while balancing on the rear tire, blended continuously as front load disappears.
        float speedBlend = MathUtils.clamp(Math.abs(speed) / 30f, 0f, 1f);
        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);
        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);
        // UI/control convention is X<0 left, X>0 right. The world yaw convention used by
        // this prototype is opposite, so convert once here rather than reversing the pad axis.
        float steerAngle = -steer * maxSteerDeg * MathUtils.degreesToRadians;
        float frontAuthority = MathUtils.clamp(frontContactState.normalForce
                / (MASS * GRAVITY * 0.32f), 0f, 1f);
        float frontYawRateTarget = Math.abs(speed) > 0.35f
                ? (speed / WHEELBASE) * (float) Math.tan(steerAngle) * frontAuthority
                : 0f;

        float wheelieBlend = rearTouching && !frontTouching ? 1f - frontAuthority : 0f;
        float wheelieSpeedBlend = MathUtils.clamp(Math.abs(speed) / 35f, 0f, 1f);
        // Full input gives about 54 deg/s at low wheelie speed and ~24 deg/s at high speed.
        // This is deliberately stronger than the old zero-authority wheelie behavior, but is
        // rate limited so a bump cannot instantly yaw the whole bike sideways.
        float wheelieYawRateTarget = -steer
                * MathUtils.lerp(0.95f, 0.42f, wheelieSpeedBlend) * wheelieBlend;
        float yawRateTarget = frontYawRateTarget + wheelieYawRateTarget;
        float yawResponse = terrainAirborne ? YAW_RATE_RESPONSE_AIR : YAW_RATE_RESPONSE_GROUND;
        yawVelocity += (yawRateTarget - yawVelocity) * Math.min(1f, dt * yawResponse);
        yawVelocity = MathUtils.clamp(yawVelocity, -2.6f, 2.6f);
        yaw += yawVelocity * dt;
        if (yaw > MathUtils.PI) yaw -= MathUtils.PI2;
        if (yaw < -MathUtils.PI) yaw += MathUtils.PI2;

        // Roll is a critically/over-damped angular state rather than an immediate lerp. Short
        // lateral impulses from bumps change roll velocity, then the damper kills the motion.
        float lateralAcceleration = speed * yawVelocity;
        float rollTarget = -(float) Math.atan2(lateralAcceleration, GRAVITY);
        rollTarget = MathUtils.clamp(rollTarget,
                -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);
        float rollFrequency = terrainAirborne ? 3.2f : ROLL_NATURAL_FREQUENCY;
        float rollDampingRatio = terrainAirborne ? 0.72f : ROLL_DAMPING_RATIO;
        float rollAcceleration = (rollTarget - roll) * rollFrequency * rollFrequency
                - 2f * rollDampingRatio * rollFrequency * rollVelocity;
        rollVelocity += rollAcceleration * dt;
        rollVelocity = MathUtils.clamp(rollVelocity, -4.0f, 4.0f);
        roll += rollVelocity * dt;

        // The old renderer snapped directly to the local side-slope normal, which made the bike
        // visibly twitch sideways on every irregular triangle. Filter that support bank with a
        // second-order damper at the fixed physics rate.
        float terrainRollTarget = 0f;
        if (!terrainAirborne && terrainVisuals != null) {
            float slopeX = terrainVisuals.groundSlopeX(bikeX, bikeZ);
            float slopeZ = terrainVisuals.groundSlopeZ(bikeX, bikeZ);
            float lateralGrade = slopeX * MathUtils.cos(yaw) - slopeZ * MathUtils.sin(yaw);
            terrainRollTarget = (float) Math.atan(lateralGrade);
        }
        float terrainRollAcceleration = (terrainRollTarget - terrainRoll)
                * TERRAIN_ROLL_NATURAL_FREQUENCY * TERRAIN_ROLL_NATURAL_FREQUENCY
                - 2f * TERRAIN_ROLL_DAMPING_RATIO * TERRAIN_ROLL_NATURAL_FREQUENCY
                * terrainRollVelocity;
        terrainRollVelocity += terrainRollAcceleration * dt;
        terrainRollVelocity = MathUtils.clamp(terrainRollVelocity, -2.5f, 2.5f);
        terrainRoll += terrainRollVelocity * dt;
'''
s = replace_once(s, old_steering, new_steering, 'steering/roll dynamics')

old_render_bank = '''        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(rootX, rootZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(rootX, rootZ) : 0f;
        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;
        float terrainRollDeg = terrainAirborne ? 0f
                : (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;
'''
new_render_bank = '''        // Sidehill support attitude is already filtered at the fixed physics rate.
        float terrainRollDeg = terrainRoll * MathUtils.radiansToDegrees;
'''
s = replace_once(s, old_render_bank, new_render_bank, 'render terrain bank')

s = replace_once(s,
'''        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        yaw = 0f;
        wheelSpin = 0f;''',
'''        pitch = 0f;
        pitchVelocity = 0f;
        roll = 0f;
        rollVelocity = 0f;
        yaw = 0f;
        yawVelocity = 0f;
        terrainRoll = 0f;
        terrainRollVelocity = 0f;
        wheelSpin = 0f;''',
'reset angular states')

s = replace_once(s,
'''        crashTimer = 0f;
        crashSide = roll < -0.05f ? -1f : (roll > 0.05f ? 1f : (steer < 0f ? -1f : 1f));''',
'''        crashTimer = 0f;
        rollVelocity = 0f;
        yawVelocity = 0f;
        terrainRollVelocity = 0f;
        crashSide = roll < -0.05f ? -1f : (roll > 0.05f ? 1f : (steer < 0f ? -1f : 1f));''',
'crash angular state handoff')

s = replace_once(s,
'''            float visualSteerDeg = steer * MathUtils.lerp(28f, 5f, visualSteerBlend);''',
'''            float visualMaxSteerDeg = MathUtils.lerp(28f, 5f, visualSteerBlend);
            // Let the bars remain visibly useful while balancing on the rear wheel.
            if (!frontGrounded && !terrainAirborne) visualMaxSteerDeg = Math.max(visualMaxSteerDeg, 11f);
            float visualSteerDeg = steer * visualMaxSteerDeg;''',
'wheelie visual steering')

p.write_text(s)

# -----------------------------------------------------------------------------
# MotorcycleDrivetrain: restore a real repeating hard limiter rather than a
# torque fade that can asymptotically prevent the engine from reaching the cut.
# -----------------------------------------------------------------------------
p = Path('core/src/main/java/com/nyerahworks/balancepoint/MotorcycleDrivetrain.java')
s = p.read_text()

s = replace_once(s,
'''    private static final float SHIFT_DURATION = 0.115f;
    private static final float MIN_SHIFT_INTERVAL = 0.16f;''',
'''    private static final float SHIFT_DURATION = 0.115f;
    private static final float MIN_SHIFT_INTERVAL = 0.16f;
    private static final float LIMITER_CUT_SECONDS = 0.055f;
    private static final float LIMITER_RESET_RPM = 10_900f;''',
'limiter constants')

s = replace_once(s,
'''    private float rpm = IDLE_RPM;
    private float shiftTimer;
    private float shiftLockout;''',
'''    private float rpm = IDLE_RPM;
    private float shiftTimer;
    private float shiftLockout;
    private float limiterCutTimer;''',
'limiter field')

s = replace_once(s,
'''        shiftTimer = 0f;
        shiftLockout = 0f;''',
'''        shiftTimer = 0f;
        shiftLockout = 0f;
        limiterCutTimer = 0f;''',
'limiter reset')

s = replace_once(s,
'''        shiftTimer = Math.max(0f, shiftTimer - dt);
        shiftLockout = Math.max(0f, shiftLockout - dt);''',
'''        shiftTimer = Math.max(0f, shiftTimer - dt);
        shiftLockout = Math.max(0f, shiftLockout - dt);
        limiterCutTimer = Math.max(0f, limiterCutTimer - dt);''',
'limiter timer update')

s = replace_once(s,
'''        targetRpm = Math.min(targetRpm, HARD_LIMIT_RPM + 250f);

        float rpmResponse = targetRpm < rpm ? 24f : 18f;
        rpm += (targetRpm - rpm) * Math.min(1f, dt * rpmResponse);''',
'''        targetRpm = Math.min(targetRpm, HARD_LIMIT_RPM + 250f);

        // A real hard limiter repeatedly cuts combustion, lets rpm fall, then re-fires.
        // The previous all-soft limiter could reduce wheel torque to zero before the engine
        // ever reached an audible cut. Trigger near the hard limit and pull the target down
        // for ~55 ms so RPM and sound both visibly/audibly bounce.
        if (limiterCutTimer <= 0f && throttle > 0.45f
                && rpm >= HARD_LIMIT_RPM - 90f && targetRpm >= REDLINE_RPM) {
            limiterCutTimer = LIMITER_CUT_SECONDS;
        }
        if (limiterCutTimer > 0f) {
            targetRpm = Math.min(targetRpm, LIMITER_RESET_RPM);
        }

        float rpmResponse = targetRpm < rpm ? 26f : 18f;
        rpm += (targetRpm - rpm) * Math.min(1f, dt * rpmResponse);
        rpm = Math.min(rpm, HARD_LIMIT_RPM + 40f);''',
'limiter rpm cycling')

s = replace_once(s,
'''        // Soft limiter starts just below redline and reaches zero at the hard limit.
        float limiter = 1f - MathUtils.clamp((rpm - (REDLINE_RPM - 250f))
                / (HARD_LIMIT_RPM - (REDLINE_RPM - 250f)), 0f, 1f);''',
'''        // Keep a mild pre-limit torque rolloff, but do not fade all the way to zero before
        // the hard cut can occur. During the cut, ignition torque is fully removed.
        float limiterApproach = MathUtils.clamp((rpm - (REDLINE_RPM - 150f))
                / (HARD_LIMIT_RPM - (REDLINE_RPM - 150f)), 0f, 1f);
        float limiter = 1f - 0.25f * limiterApproach;
        if (limiterCutTimer > 0f) limiter = 0f;''',
'limiter torque cut')

s = replace_once(s,
'''    boolean isShifting() {
        return shiftTimer > 0f;
    }
}''',
'''    boolean isShifting() {
        return shiftTimer > 0f;
    }

    boolean isLimiterCut() {
        return limiterCutTimer > 0f;
    }
}''',
'limiter state getter')

p.write_text(s)

# -----------------------------------------------------------------------------
# EngineAudio: its old combustion-cut threshold was still from the 13k tune.
# -----------------------------------------------------------------------------
p = Path('core/src/main/java/com/nyerahworks/balancepoint/EngineAudio.java')
s = p.read_text()
s = replace_once(s,
'''            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 11_400f, 0f, 1f);''',
'''            float rpmNorm = MathUtils.clamp((smoothRpm - 1_800f) / 10_100f, 0f, 1f);''',
'audio rpm normalization')
s = replace_once(s,
'''                    boolean limiterCut = smoothRpm > 13_050f && smoothThrottle > 0.70f
                            && (combustionCount & 3) == 3;''',
'''                    // Match the current 11.9k hard limiter. At the limiter skip every other
                    // combustion event, giving the 450 a clear braap-braap cut instead of the
                    // old unreachable 13,050 rpm condition.
                    boolean limiterCut = smoothRpm > 11_450f && smoothThrottle > 0.65f
                            && (combustionCount & 1) == 1;''',
'audio limiter threshold')
p.write_text(s)

print('Patched chassis angular damping, wheelie steering, drivetrain limiter, and limiter audio.')
