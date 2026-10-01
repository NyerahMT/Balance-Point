from pathlib import Path

root = Path('.')
game = root / 'core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java'
drive = root / 'core/src/main/java/com/nyerahworks/balancepoint/MotorcycleDrivetrain.java'
s = game.read_text()
d = drive.read_text()

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'{label}: target not found')
    return text.replace(old, new, 1)

# The old rigid-body conversion accidentally interpreted the legacy COM_HEIGHT as an
# axle-to-COM dimension. 0.70 m + wheel radius put the COM >1 m above the ground.
s = rep(s,
'''    private static final float CONTACT_PRELOAD_GAP = 0.018f;
    private static final float CONTACT_BUMP_START = 0.050f;
''',
'''    private static final float CONTACT_BUMP_START = 0.050f;
''', 'remove common preload')

s = rep(s,
'''    private static final float COM_FORWARD = 0.60f;
    private static final float AIRBORNE_COM_FORWARD = 1.00f;
''',
'''    // Neutral combined bike+rider COM. 0.36 m above the axles plus the 0.337 m tire
    // radius puts the system COM about 0.70 m above level ground. 0.67 m forward of the
    // rear axle yields a believable ~55/45 rear/front static load split.
    private static final float COM_FORWARD = 0.67f;
    private static final float AIRBORNE_COM_FORWARD = 1.00f;
''', 'COM forward')

s = rep(s,
'''    private static final float COM_HEIGHT = 0.70f;
''',
'''    private static final float COM_HEIGHT = 0.36f;
    private static final float REAR_CONTACT_PRELOAD = MASS * GRAVITY
            * (WHEELBASE - COM_FORWARD) / WHEELBASE / CONTACT_STIFFNESS;
    private static final float FRONT_CONTACT_PRELOAD = MASS * GRAVITY
            * COM_FORWARD / WHEELBASE / CONTACT_STIFFNESS;
''', 'COM height and preloads')

s = rep(s,
'''        solveWheelContact(rearContactState, rearX, rearY, rearZ,
                rearForwardVelocity, rearVerticalVelocity, sinYaw, cosYaw);
        solveWheelContact(frontContactState, frontX, frontY, frontZ,
                frontForwardVelocity, frontVerticalVelocity, sinYaw, cosYaw);
''',
'''        solveWheelContact(rearContactState, rearX, rearY, rearZ,
                rearForwardVelocity, rearVerticalVelocity, sinYaw, cosYaw,
                REAR_CONTACT_PRELOAD);
        solveWheelContact(frontContactState, frontX, frontY, frontZ,
                frontForwardVelocity, frontVerticalVelocity, sinYaw, cosYaw,
                FRONT_CONTACT_PRELOAD);
''', 'contact calls')

# Signed brake / rolling resistance and rollback. This removes the artificial uphill floor at 0 m/s.
s = rep(s,
'''        float rearTireForce = driveForce - brakeForce;
''',
'''        float brakeDirection = absSpeed > 0.03f ? -Math.signum(speed) : 0f;
        float rearTireForce = driveForce + brakeDirection * brakeForce;
''', 'signed brake force')

s = rep(s,
'''        float rolling = (rearContactState.normalForce + frontContactState.normalForce)
                * ROLLING_RESISTANCE;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDrag = (rearTouching || frontTouching) && offRoad ? 95f + absSpeed * 5f : 0f;

        float totalForwardForce = rearNormalForward + frontNormalForward + rearTireForward
                - rolling - aero - surfaceDrag;
''',
'''        float rollingMagnitude = (rearContactState.normalForce + frontContactState.normalForce)
                * ROLLING_RESISTANCE;
        float rollingForce = absSpeed > 0.05f ? Math.signum(speed) * rollingMagnitude : 0f;
        float aero = AERO_DRAG * speed * absSpeed;
        float surfaceDragForce = (rearTouching || frontTouching) && offRoad && absSpeed > 0.05f
                ? Math.signum(speed) * (95f + absSpeed * 5f) : 0f;

        float totalForwardForce = rearNormalForward + frontNormalForward + rearTireForward
                - rollingForce - aero - surfaceDragForce;
''', 'signed drag')

s = rep(s,
'''        speed += longitudinalAcceleration * dt;
        speed = MathUtils.clamp(speed, 0f, 48f);
''',
'''        speed += longitudinalAcceleration * dt;
        // A bike that cannot hold a grade should roll backward instead of being glued to a
        // zero-speed clamp. Reverse speed is capped only to keep an accidental downhill roll
        // recoverable in the prototype controls.
        speed = MathUtils.clamp(speed, -10f, 48f);
''', 'rollback clamp')

s = rep(s,
'''    private void solveWheelContact(WheelContact out, float worldX, float wheelY, float worldZ,
                                   float pointForwardVelocity, float pointVerticalVelocity,
                                   float sinYaw, float cosYaw) {
''',
'''    private void solveWheelContact(WheelContact out, float worldX, float wheelY, float worldZ,
                                   float pointForwardVelocity, float pointVerticalVelocity,
                                   float sinYaw, float cosYaw, float preloadGap) {
''', 'contact signature')

s = rep(s,
'''        float virtualCompression = CONTACT_PRELOAD_GAP - out.gap;
''',
'''        float virtualCompression = preloadGap - out.gap;
''', 'individual preload')

# The automatic clutch was asking for nearly 10.9k rpm at a standstill and transmitting only 56%.
# Put launch slip in the broad torque peak and give it enough static clutch to start on a real grade.
d = rep(d,
'''        float freeRevRpm = IDLE_RPM + throttle * 9_000f;
        float clutchLock = MathUtils.clamp(absSpeed / 5.0f, 0f, 1f);
''',
'''        // Automatic launch clutch: let the 450 work in its broad midrange instead of
        // free-revving near the limiter while barely coupling the rear wheel.
        float freeRevRpm = IDLE_RPM + throttle * 5_100f;
        float clutchLock = MathUtils.clamp(absSpeed / 6.5f, 0f, 1f);
''', 'launch rpm')

d = rep(d,
'''        float clutchEngagement = MathUtils.clamp(0.56f + absSpeed / 5.0f, 0.56f, 1f);
''',
'''        float clutchEngagement = MathUtils.clamp(0.70f + absSpeed / 6.0f, 0.70f, 1f);
''', 'launch clutch engagement')

game.write_text(s)
drive.write_text(d)
