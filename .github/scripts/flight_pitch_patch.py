from pathlib import Path
p=Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s=p.read_text()

def rep(a,b,label):
    global s
    if a not in s: raise SystemExit('missing '+label)
    s=s.replace(a,b,1)

old='''        if (frontGrounded) {
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
                if (!terrainAirborne) frontGrounded = true;
            }
        }
'''
new='''        if (terrainAirborne) {
            // Once both tires are off the ground there is no rear contact patch to create
            // the wheelie torque used below. Preserve angular momentum through the jump
            // with only light aerodynamic/rider damping.
            pitchVelocity *= Math.max(0f, 1f - 0.32f * dt);
            pitchVelocity = MathUtils.clamp(pitchVelocity, -MAX_PITCH_RATE, MAX_PITCH_RATE);
            pitch += pitchVelocity * dt;
        } else if (frontGrounded) {
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
rep(old,new,'airborne pitch block')

rep('''            if (rearHit || frontHit) {
                terrainAirborne = false;
                bikeY = rearSupport;
                verticalVelocity = supportVelocity;
                if (pitch < 6f * MathUtils.degreesToRadians) {
                    pitch = 0f;
                    pitchVelocity = 0f;
                    frontGrounded = true;
                }
            }
''','''            if (rearHit || frontHit) {
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
''','landing pitch transfer')

rep('''            if (crestLaunch) {
                terrainAirborne = true;
                frontGrounded = false;
                bikeY += verticalVelocity * dt;
                verticalVelocity -= GRAVITY * dt;
            } else {
''','''            if (crestLaunch) {
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
''','takeoff pitch transfer')

p.write_text(s)
