from pathlib import Path

p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing patch target: {label}')
    s = s.replace(old, new, 1)

rep(
'''    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();
''',
'''    private final Vector3 tempA = new Vector3();
    private final Vector3 tempB = new Vector3();
    private final Vector3 tempC = new Vector3();
''', 'tempC')

rep(
'''    private float speed;
    private float bikeZ;
    private float bikeX;
    private float pitch;
''',
'''    private float speed;
    private float bikeZ;
    private float bikeX;
    // Rear axle world height. Unlike the old root-height snap, this carries vertical
    // momentum across crests when both wheels leave the terrain.
    private float bikeY = WHEEL_RADIUS;
    private float verticalVelocity;
    private boolean terrainAirborne;
    private float pitch;
''', 'vertical state fields')

rep(
'''        float terrainSlopeMagnitude = (float) Math.sqrt(terrainSlopeX * terrainSlopeX
                + terrainSlopeZ * terrainSlopeZ);
        float terrainNormalScale = 1f / (float) Math.sqrt(1f
                + terrainSlopeMagnitude * terrainSlopeMagnitude);
        float forwardGrade = terrainSlopeX * MathUtils.sin(yaw)
                + terrainSlopeZ * MathUtils.cos(yaw);
''',
'''        float terrainSlopeMagnitude = (float) Math.sqrt(terrainSlopeX * terrainSlopeX
                + terrainSlopeZ * terrainSlopeZ);
        float terrainNormalScale = terrainAirborne ? 0f : 1f / (float) Math.sqrt(1f
                + terrainSlopeMagnitude * terrainSlopeMagnitude);
        float forwardGrade = terrainSlopeX * MathUtils.sin(yaw)
                + terrainSlopeZ * MathUtils.cos(yaw);

        // A full-bike jump is different from a wheelie. With no tire contact the front
        // cannot be considered planted even if the relative wheelie pitch is near zero.
        if (terrainAirborne && frontGrounded) frontGrounded = false;
''', 'terrain normal/contact')

rep(
'''        float rolling = absSpeed > 0.02f ? MASS * GRAVITY * ROLLING_RESISTANCE : 0f;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = offRoad ? 95f + absSpeed * 5f : 0f;
        float gradeAngle = (float) Math.atan(forwardGrade);
        float gradeAcceleration = GRAVITY * MathUtils.sin(gradeAngle);
''',
'''        float rolling = !terrainAirborne && absSpeed > 0.02f
                ? MASS * GRAVITY * ROLLING_RESISTANCE : 0f;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = !terrainAirborne && offRoad ? 95f + absSpeed * 5f : 0f;
        float gradeAngle = (float) Math.atan(forwardGrade);
        float gradeAcceleration = terrainAirborne ? 0f : GRAVITY * MathUtils.sin(gradeAngle);
''', 'airborne longitudinal forces')

rep(
'''            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                frontGrounded = true;
            }
''',
'''            if (pitch <= 0f) {
                pitch = 0f;
                pitchVelocity = 0f;
                if (!terrainAirborne) frontGrounded = true;
            }
''', 'front grounding while airborne')

rep(
'''        bikeX += MathUtils.sin(yaw) * speed * dt;
        bikeZ += MathUtils.cos(yaw) * speed * dt;
        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;
''',
'''        // Remember the support point before horizontal motion. While attached to the
        // terrain, its vertical speed is the launch velocity the bike should retain when
        // the surface drops away at a crest.
        float oldRearSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(bikeX, bikeZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        if (!terrainAirborne) {
            verticalVelocity = speed * forwardGrade;
            bikeY = oldRearSupport;
        }

        bikeX += MathUtils.sin(yaw) * speed * dt;
        bikeZ += MathUtils.cos(yaw) * speed * dt;
        wheelSpin += speed / WHEEL_RADIUS * dt;
        if (wheelSpin > MathUtils.PI2) wheelSpin -= MathUtils.PI2;

        float rearSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(bikeX, bikeZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        float frontSampleX = bikeX + MathUtils.sin(yaw) * WHEELBASE;
        float frontSampleZ = bikeZ + MathUtils.cos(yaw) * WHEELBASE;
        float frontSupport = terrainVisuals != null
                ? terrainVisuals.groundHeight(frontSampleX, frontSampleZ) + WHEEL_RADIUS
                : WHEEL_RADIUS;
        float supportVelocity = (rearSupport - oldRearSupport) / Math.max(dt, 0.0001f);

        if (terrainAirborne) {
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
                terrainAirborne = true;
                frontGrounded = false;
                bikeY += verticalVelocity * dt;
                verticalVelocity -= GRAVITY * dt;
            } else {
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
            }
        }
''', 'horizontal movement and vertical contact')

rep(
'''        speed = 0f;
        bikeX = 0f;
        pitch = 0f;
''',
'''        speed = 0f;
        bikeX = 0f;
        bikeY = WHEEL_RADIUS;
        verticalVelocity = 0f;
        terrainAirborne = false;
        pitch = 0f;
''', 'reset vertical state')

old_visual = '''        float terrainHeight = terrainVisuals != null ? terrainVisuals.groundHeight(bikeX, bikeZ) : 0f;
        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;
        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float terrainForwardGrade = terrainSlopeX * sinYaw + terrainSlopeZ * cosYaw;
        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;
        float terrainPitchDeg = (float) Math.atan(terrainForwardGrade) * MathUtils.radiansToDegrees;
        float terrainRollDeg = (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;

        float rootHeight = terrainHeight + (crashed && crashSettled ? 0.22f : WHEEL_RADIUS);
        bikeRoot.idt().translate(bikeX, rootHeight, bikeZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
                .rotate(Vector3.X, -(pitchDeg + terrainPitchDeg));
'''
new_visual = '''        float rearTerrainHeight = terrainVisuals != null
                ? terrainVisuals.groundHeight(bikeX, bikeZ) : 0f;
        float sinYaw = MathUtils.sin(yaw);
        float cosYaw = MathUtils.cos(yaw);
        float frontSampleX = bikeX + sinYaw * WHEELBASE;
        float frontSampleZ = bikeZ + cosYaw * WHEELBASE;
        float frontTerrainHeight = terrainVisuals != null
                ? terrainVisuals.groundHeight(frontSampleX, frontSampleZ) : 0f;
        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;
        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;
        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;

        // Chassis terrain pitch comes from the actual rear/front tire support heights,
        // not a single derivative under the center of the bike. This lets one wheel sit on
        // a different grade while the frame bridges the terrain between them.
        float supportPitch = terrainAirborne ? 0f
                : (float) Math.atan2(frontTerrainHeight - rearTerrainHeight, WHEELBASE);
        float terrainPitchDeg = supportPitch * MathUtils.radiansToDegrees;
        float terrainRollDeg = terrainAirborne ? 0f
                : (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;

        float rootHeight = crashed && crashSettled ? rearTerrainHeight + 0.22f : bikeY;
        bikeRoot.idt().translate(bikeX, rootHeight, bikeZ)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.Z, rollDeg + terrainRollDeg)
                .rotate(Vector3.X, -(pitchDeg + terrainPitchDeg));
'''
rep(old_visual, new_visual, 'two-wheel visual contact')

old_camera = '''        if (cockpitCamera) {
            float helmetY = importedBikeLoaded ? 1.22f : 1.36f;
            float helmetZ = importedBikeLoaded ? 0.66f : (0.58f + riderLean * 0.10f);
            tempA.set(0f, helmetY, helmetZ).mul(bikeRoot);
            camera.position.set(tempA);
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(sinView * cp, MathUtils.sin(lookPitch), cosView * cp).nor();
            camera.up.set(Vector3.Y);
            camera.fieldOfView = 80f;
        } else {
'''
new_camera = '''        if (cockpitCamera) {
            float helmetY = importedBikeLoaded ? 1.22f : 1.36f;
            float helmetZ = importedBikeLoaded ? 0.66f : (0.58f + riderLean * 0.10f);
            tempA.set(0f, helmetY, helmetZ).mul(bikeRoot);
            camera.position.set(tempA);

            // Helmet view lives in the motorcycle's local frame. Roll and pitch therefore
            // follow banks, hills and jumps instead of being artificially horizon-locked.
            float cp = MathUtils.cos(lookPitch);
            camera.direction.set(MathUtils.sin(lookYaw) * cp,
                    MathUtils.sin(lookPitch), MathUtils.cos(lookYaw) * cp)
                    .rot(bikeRoot).nor();
            camera.up.set(Vector3.Y).rot(bikeRoot).nor();
            camera.fieldOfView = 80f;
        } else {
'''
rep(old_camera, new_camera, 'helmet camera local frame')

rep(
'''            float desiredHeight = 1.88f + chaseDistance * MathUtils.sin(orbitPitch);
            tempA.set(bikeX - sinView * horizontalDistance,
                    Math.max(0.82f, desiredHeight),
                    bikeZ - cosView * horizontalDistance);
''',
'''            float desiredHeight = bikeY + 1.57f + chaseDistance * MathUtils.sin(orbitPitch);
            tempA.set(bikeX - sinView * horizontalDistance,
                    Math.max(0.82f, desiredHeight),
                    bikeZ - cosView * horizontalDistance);
''', 'chase camera height')

rep(
'''            tempB.set(bikeX + MathUtils.sin(yaw) * targetLead,
                    0.88f + MathUtils.sin(pitch) * 0.42f,
                    bikeZ + MathUtils.cos(yaw) * targetLead);
''',
'''            tempB.set(bikeX + MathUtils.sin(yaw) * targetLead,
                    bikeY + 0.57f + MathUtils.sin(pitch) * 0.42f,
                    bikeZ + MathUtils.cos(yaw) * targetLead);
''', 'chase camera target height')

p.write_text(s)
