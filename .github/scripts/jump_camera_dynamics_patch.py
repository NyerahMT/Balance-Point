from pathlib import Path

p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'{label}: target not found')
    s = s.replace(old, new, 1)

replace_once(
'''    private static final float PITCH_DAMPING = 30f;
    private static final float MAX_PITCH_RATE = 5.4f;
''',
'''    private static final float PITCH_DAMPING = 30f;
    private static final float MAX_PITCH_RATE = 5.4f;
    // In flight the chassis is a free rigid body. Rear-wheel acceleration/deceleration
    // exchanges angular momentum with the bike: throttle raises the nose, rear brake lowers it.
    private static final float REAR_WHEEL_INERTIA = 1.05f;
    private static final float AIR_WHEEL_DRIVE_ACCEL = 115f;
    private static final float AIR_WHEEL_BRAKE_ACCEL = 220f;
    private static final float AIR_WHEEL_DRAG = 0.22f;
    private static final float AIR_PITCH_DAMPING = 0.22f;
    private static final float AIR_MAX_PITCH_RATE = 3.8f;
    private static final float CONTACT_PITCH_SPRING = 38f;
    private static final float CONTACT_PITCH_DAMPING = 11f;
''', 'pitch constants')

replace_once(
'''    private float wheelSpin;
    private float longitudinalAcceleration;
''',
'''    private float wheelSpin;
    private float rearWheelAngularSpeed;
    private float lastGroundSupportPitch;
    private float groundSupportPitchRate;
    private float longitudinalAcceleration;
''', 'airborne state fields')

replace_once(
'''        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);

        float staticFrontLoad = MASS * GRAVITY * effectiveComForward / WHEELBASE;
''',
'''        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);

        // Rear wheel has its own angular speed in flight. The reaction torque from changing
        // wheel speed is what gives real bikes their throttle-up / brake-tap pitch control.
        float wheelAngularAcceleration = 0f;
        if (terrainAirborne) {
            float previousWheelAngularSpeed = rearWheelAngularSpeed;
            float wheelAccel = throttle * AIR_WHEEL_DRIVE_ACCEL
                    - rearBrake * AIR_WHEEL_BRAKE_ACCEL
                    - rearWheelAngularSpeed * AIR_WHEEL_DRAG;
            rearWheelAngularSpeed = MathUtils.clamp(rearWheelAngularSpeed + wheelAccel * dt,
                    0f, 225f);
            wheelAngularAcceleration = (rearWheelAngularSpeed - previousWheelAngularSpeed)
                    / Math.max(dt, 0.0001f);
        } else {
            float coupledWheelSpeed = speed / WHEEL_RADIUS;
            rearWheelAngularSpeed += (coupledWheelSpeed - rearWheelAngularSpeed)
                    * Math.min(1f, dt * 30f);
        }

        float staticFrontLoad = MASS * GRAVITY * effectiveComForward / WHEELBASE;
''', 'wheel angular dynamics')

old_pitch = '''        if (frontGrounded) {
            pitch = 0f;
            pitchVelocity = 0f;
            if (frontNormalLoad <= 0f && speed > 2.5f) {
                float liftMargin = MathUtils.clamp(-frontNormalLoad
                        / Math.max(staticFrontLoad * LIFT_MARGIN_FOR_FULL_SEED, 1f), 0f, 1f);
                float liftAuthority = liftMargin * liftMargin * (3f - 2f * liftMargin);
                frontGrounded = false;
                frontNormalLoad = 0f;
                pitchVelocity = (LIFT_SEED_RATE + throttleSnap * THROTTLE_SNAP_IMPULSE)
                        * liftAuthority;
                throttleSnap = 0f;
            }
        } else {
            float sinPitch = MathUtils.sin(pitch);
            float cosPitch = MathUtils.cos(pitch);
            float airborneComBlend = MathUtils.clamp((pitch - AIRBORNE_COM_SHIFT_START)
                    / (AIRBORNE_COM_SHIFT_END - AIRBORNE_COM_SHIFT_START), 0f, 1f);
            airborneComBlend = airborneComBlend * airborneComBlend * (3f - 2f * airborneComBlend);
            float airborneComForward = MathUtils.lerp(COM_FORWARD, AIRBORNE_COM_FORWARD,
                    airborneComBlend) + riderLean * RIDER_SHIFT;
            float comWorldForward = airborneComForward * cosPitch - COM_HEIGHT * sinPitch;
            float comWorldHeight = airborneComForward * sinPitch + COM_HEIGHT * cosPitch;
            float pitchTorque = MASS * longitudinalAcceleration * comWorldHeight
                    - MASS * GRAVITY * comWorldForward
                    - pitchVelocity * PITCH_DAMPING;

            pitchVelocity += (pitchTorque / PITCH_INERTIA) * dt;
            pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
            pitch += pitchVelocity * dt;

            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                frontGrounded = true;
            }
        }
'''
new_pitch = '''        if (terrainAirborne) {
            // Free flight: gravity acts through the COM, so it changes the trajectory rather
            // than forcing a canned chassis angle. Rear-wheel angular momentum is the main
            // rider-controllable pitch torque in the air.
            frontGrounded = false;
            float reactionPitchAcceleration = wheelAngularAcceleration * REAR_WHEEL_INERTIA
                    / PITCH_INERTIA;
            pitchVelocity += reactionPitchAcceleration * dt;
            pitchVelocity *= Math.max(0f, 1f - AIR_PITCH_DAMPING * dt);
            pitchVelocity = MathUtils.clamp(pitchVelocity, -AIR_MAX_PITCH_RATE,
                    AIR_MAX_PITCH_RATE);
            pitch += pitchVelocity * dt;
        } else if (frontGrounded) {
            // Let landing attitude settle through a short suspension-like pitch response
            // instead of snapping the chassis exactly to the terrain chord in one frame.
            if (Math.abs(pitch) > 0.0008f || Math.abs(pitchVelocity) > 0.012f) {
                float contactPitchAcceleration = -pitch * CONTACT_PITCH_SPRING
                        - pitchVelocity * CONTACT_PITCH_DAMPING;
                pitchVelocity += contactPitchAcceleration * dt;
                pitch += pitchVelocity * dt;
                if (Math.abs(pitch) < 0.20f * MathUtils.degreesToRadians
                        && Math.abs(pitchVelocity) < 0.025f) {
                    pitch = 0f;
                    pitchVelocity = 0f;
                }
            } else {
                pitch = 0f;
                pitchVelocity = 0f;
            }

            if (frontNormalLoad <= 0f && speed > 2.5f
                    && Math.abs(pitch) < 4f * MathUtils.degreesToRadians) {
                float liftMargin = MathUtils.clamp(-frontNormalLoad
                        / Math.max(staticFrontLoad * LIFT_MARGIN_FOR_FULL_SEED, 1f), 0f, 1f);
                float liftAuthority = liftMargin * liftMargin * (3f - 2f * liftMargin);
                frontGrounded = false;
                frontNormalLoad = 0f;
                pitchVelocity = (LIFT_SEED_RATE + throttleSnap * THROTTLE_SNAP_IMPULSE)
                        * liftAuthority;
                throttleSnap = 0f;
            }
        } else {
            // Rear tire planted, front tire airborne: this is wheelie dynamics, distinct from
            // a full-bike jump where neither tire is supporting the motorcycle.
            float sinPitch = MathUtils.sin(pitch);
            float cosPitch = MathUtils.cos(pitch);
            float airborneComBlend = MathUtils.clamp((pitch - AIRBORNE_COM_SHIFT_START)
                    / (AIRBORNE_COM_SHIFT_END - AIRBORNE_COM_SHIFT_START), 0f, 1f);
            airborneComBlend = airborneComBlend * airborneComBlend * (3f - 2f * airborneComBlend);
            float airborneComForward = MathUtils.lerp(COM_FORWARD, AIRBORNE_COM_FORWARD,
                    airborneComBlend) + riderLean * RIDER_SHIFT;
            float comWorldForward = airborneComForward * cosPitch - COM_HEIGHT * sinPitch;
            float comWorldHeight = airborneComForward * sinPitch + COM_HEIGHT * cosPitch;
            float pitchTorque = MASS * longitudinalAcceleration * comWorldHeight
                    - MASS * GRAVITY * comWorldForward
                    - pitchVelocity * PITCH_DAMPING;

            pitchVelocity += (pitchTorque / PITCH_INERTIA) * dt;
            pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
            pitch += pitchVelocity * dt;

            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                frontGrounded = true;
            }
        }
'''
replace_once(old_pitch, new_pitch, 'pitch state machine')

replace_once(
'''        float oldRearSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(bikeX, bikeZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        if (!terrainAirborne) {
            verticalVelocity = speed * forwardGrade;
            bikeY = oldRearSupport;
        }
''',
'''        float oldRearSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(bikeX, bikeZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        float oldFrontX = bikeX + MathUtils.sin(yaw) * WHEELBASE;
        float oldFrontZ = bikeZ + MathUtils.cos(yaw) * WHEELBASE;
        float oldFrontSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(oldFrontX, oldFrontZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        float oldSupportPitch = frontGrounded
                ? (float) Math.atan2(oldFrontSupport - oldRearSupport, WHEELBASE)
                : (float) Math.atan(forwardGrade);
        if (!terrainAirborne) {
            float rawSupportPitchRate = (oldSupportPitch - lastGroundSupportPitch)
                    / Math.max(dt, 0.0001f);
            rawSupportPitchRate = MathUtils.clamp(rawSupportPitchRate, -2.4f, 2.4f);
            groundSupportPitchRate += (rawSupportPitchRate - groundSupportPitchRate)
                    * Math.min(1f, dt * 16f);
            lastGroundSupportPitch = oldSupportPitch;
            verticalVelocity = speed * forwardGrade;
            bikeY = oldRearSupport;
        }
''', 'pre-move support attitude')

replace_once(
'''        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;
''',
'''        wheelSpin += rearWheelAngularSpeed * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;
''', 'wheel spin integration')

old_air = '''        if (terrainAirborne) {
            verticalVelocity -= GRAVITY * dt;
            bikeY += verticalVelocity * dt;

            // Either tire may be the first one to meet the ground. Front-wheel height is
            // evaluated independently instead of assuming both wheels sit on one gradient.
            float airborneFrontY = bikeY + MathUtils.sin(pitch) * WHEELBASE;
            boolean rearHit = bikeY <= rearSupport;
            boolean frontHit = airborneFrontY <= frontSupport;
            if (rearHit || frontHit) {
                terrainAirborne = false;
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
                // Flight pitch is world-relative. Convert it back to terrain-relative pitch
                // so wheelie/contact dynamics resume smoothly on the landing slope.
                float landingSupportPitch = (float) Math.atan2(frontSupport - rearSupport,
                        WHEELBASE);
                pitch -= landingSupportPitch;
                if (pitch < 6f * MathUtils.degreesToRadians) {
                    pitch = 0f;
                    pitchVelocity = 0f;
                    frontGrounded = true;
                }
            }
        } else {
            // If the support surface begins moving downward substantially faster than the
            // motorcycle was travelling upward, the terrain can no longer pull the bike
            // down. Preserve that vertical momentum and transition to ballistic flight.
            float separationVelocity = verticalVelocity - supportVelocity;
            boolean crestLaunch = speed > 3.5f && separationVelocity > 0.60f;
            if (crestLaunch) {
                // Transfer the terrain chord angle into world-space flight attitude before
                // terrain support disappears from the renderer.
                float launchSupportPitch = (float) Math.atan2(frontSupport - rearSupport,
                        WHEELBASE);
                pitch += launchSupportPitch;
                terrainAirborne = true;
                frontGrounded = false;
                bikeY += verticalVelocity * dt;
                verticalVelocity -= GRAVITY * dt;
            } else {
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
            }
        }
'''
new_air = '''        if (terrainAirborne) {
            verticalVelocity -= GRAVITY * dt;
            bikeY += verticalVelocity * dt;

            // Collision points follow the actual airborne chassis angle. A pitched bike's
            // front tire is not a full wheelbase ahead in the horizontal plane.
            float frontReach = WHEELBASE * Math.max(0.12f, MathUtils.cos(pitch));
            float airborneFrontX = bikeX + MathUtils.sin(yaw) * frontReach;
            float airborneFrontZ = bikeZ + MathUtils.cos(yaw) * frontReach;
            float airborneFrontSupport = terrainVisuals != null
                    ? terrainVisuals.groundHeight(airborneFrontX, airborneFrontZ) + WHEEL_RADIUS
                    : WHEEL_RADIUS;
            float airborneFrontY = bikeY + MathUtils.sin(pitch) * WHEELBASE;
            boolean rearHit = bikeY <= rearSupport;
            boolean frontHit = airborneFrontY <= airborneFrontSupport;

            // A front-first touch should create a pitch reaction, not teleport the whole bike
            // level. Nudge it out of penetration and let angular momentum carry it onward until
            // the rear tire actually establishes support.
            if (frontHit && !rearHit) {
                float penetration = airborneFrontSupport - airborneFrontY;
                bikeY += Math.max(0f, penetration) + 0.004f;
                float impactSpeed = Math.max(0f, supportVelocity - verticalVelocity);
                pitchVelocity += MathUtils.clamp(impactSpeed * 0.10f, 0f, 1.15f);
                verticalVelocity = Math.max(verticalVelocity, supportVelocity) * 0.30f;
            }

            if (rearHit) {
                float landingSupportPitch = (float) Math.atan2(frontSupport - rearSupport,
                        WHEELBASE);
                float relativeLandingPitch = pitch - landingSupportPitch;
                float frontGap = airborneFrontY - frontSupport;
                terrainAirborne = false;
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
                pitch = relativeLandingPitch;
                pitchVelocity *= 0.72f;
                frontGrounded = frontGap <= 0.10f
                        && relativeLandingPitch < 7f * MathUtils.degreesToRadians;
                lastGroundSupportPitch = landingSupportPitch;
                groundSupportPitchRate = 0f;
            }
        } else {
            // If terrain drops away faster than the motorcycle's existing vertical trajectory,
            // support is lost. Launch from the LAST CONTACT attitude, not the new downhill/cliff
            // chord, and carry the contact pitch rate into free flight.
            float separationVelocity = verticalVelocity - supportVelocity;
            boolean crestLaunch = speed > 3.5f && separationVelocity > 0.60f;
            if (crestLaunch) {
                pitch += oldSupportPitch;
                pitchVelocity += groundSupportPitchRate;
                pitchVelocity = MathUtils.clamp(pitchVelocity, -AIR_MAX_PITCH_RATE,
                        AIR_MAX_PITCH_RATE);
                terrainAirborne = true;
                frontGrounded = false;
                bikeY += verticalVelocity * dt;
                verticalVelocity -= GRAVITY * dt;
            } else {
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
            }
        }
'''
replace_once(old_air, new_air, 'airborne contact block')

replace_once(
'''        wheelSpin = 0f;
        longitudinalAcceleration = 0f;
''',
'''        wheelSpin = 0f;
        rearWheelAngularSpeed = 0f;
        lastGroundSupportPitch = 0f;
        groundSupportPitchRate = 0f;
        longitudinalAcceleration = 0f;
''', 'reset flight state')

replace_once(
'''        float supportPitch = terrainAirborne ? 0f
                : (float) Math.atan2(frontTerrainHeight - rearTerrainHeight, WHEELBASE);
''',
'''        float supportPitch;
        if (terrainAirborne) {
            supportPitch = 0f;
        } else if (frontGrounded) {
            supportPitch = (float) Math.atan2(frontTerrainHeight - rearTerrainHeight, WHEELBASE);
        } else {
            // During a wheelie the rear tire is the only support point, so terrain under the
            // airborne front wheel must not rotate the whole chassis.
            float terrainForwardGrade = terrainSlopeX * sinYaw + terrainSlopeZ * cosYaw;
            supportPitch = (float) Math.atan(terrainForwardGrade);
        }
''', 'renderer support pitch')

old_cam = '''            // Helmet view lives in the motorcycle's local frame. Roll and pitch therefore
            // follow banks, hills and jumps instead of being artificially horizon-locked.
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(MathUtils.sin(lookYaw) * cp,
                    MathUtils.sin(lookPitch), MathUtils.cos(lookYaw) * cp)
                    .rot(bikeRoot).nor();
            camera.up.set(Vector3.Y).rot(bikeRoot).nor();
            camera.fieldOfView = 80f;
'''
new_cam = '''            // Helmet position follows the motorcycle vertically, but the rider's eyes keep
            // pitch referenced to the horizon. Bank/sidehill roll still comes through so the
            // view feels attached to the rider instead of becoming a floating gimbal.
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(MathUtils.sin(viewYaw) * cp,
                    MathUtils.sin(lookPitch), MathUtils.cos(viewYaw) * cp).nor();

            float cameraTerrainSlopeX = terrainVisuals != null
                    ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;
            float cameraTerrainSlopeZ = terrainVisuals != null
                    ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;
            float cameraLateralGrade = cameraTerrainSlopeX * MathUtils.cos(yaw)
                    - cameraTerrainSlopeZ * MathUtils.sin(yaw);
            float cameraBank = roll + (terrainAirborne ? 0f
                    : (float) Math.atan(cameraLateralGrade));
            tempB.set(camera.direction).crs(Vector3.Y).nor();
            camera.up.set(tempB).crs(camera.direction).nor()
                    .rotate(camera.direction, cameraBank * MathUtils.radiansToDegrees).nor();
            camera.fieldOfView = 80f;
'''
replace_once(old_cam, new_cam, 'helmet camera')

p.write_text(s)
