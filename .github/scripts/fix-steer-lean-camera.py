from pathlib import Path

p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

repls = [
    (
        '    private static final float RIDER_SHIFT = 0.14f;',
        '    // Maximum fore/aft shift of the combined bike+rider COM from rider body movement.\n'
        '    // About 180 mm gives meaningful weight transfer while staying plausible for a rider\n'
        '    // moving between the tank and rear of the seat.\n'
        '    private static final float RIDER_SHIFT = 0.18f;'
    ),
    (
        '        // Axle locations relative to the COM, expressed in the forward/up plane.\n'
        '        float rearForward = -COM_FORWARD * cosPitch + COM_HEIGHT * sinPitch;\n'
        '        float rearVertical = -COM_FORWARD * sinPitch - COM_HEIGHT * cosPitch;\n'
        '        float frontForward = (WHEELBASE - COM_FORWARD) * cosPitch + COM_HEIGHT * sinPitch;\n'
        '        float frontVertical = (WHEELBASE - COM_FORWARD) * sinPitch - COM_HEIGHT * cosPitch;',
        '        // Axle locations relative to the CURRENT combined COM. Positive rider lean moves\n'
        '        // the mass forward; negative lean moves it rearward. Contact geometry, velocities\n'
        '        // and force moments all use the same shifted COM instead of applying a late torque\n'
        '        // correction after wheel contact has already been solved.\n'
        '        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT,\n'
        '                0.48f, 0.88f);\n'
        '        float rearForward = -effectiveComForward * cosPitch + COM_HEIGHT * sinPitch;\n'
        '        float rearVertical = -effectiveComForward * sinPitch - COM_HEIGHT * cosPitch;\n'
        '        float frontForward = (WHEELBASE - effectiveComForward) * cosPitch + COM_HEIGHT * sinPitch;\n'
        '        float frontVertical = (WHEELBASE - effectiveComForward) * sinPitch - COM_HEIGHT * cosPitch;'
    ),
    (
        '        // Apply contact forces at actual tire contact patches and integrate the resulting moment\n'
        '        // about the COM. There is no wheelie/jump/landing torque branch here.\n'
        '        // Rider fore/aft input shifts the combined COM relative to both tire patches. Positive\n'
        '        // lean moves the COM forward, loading the front; negative lean moves it rearward. This\n'
        '        // changes force moments continuously instead of applying a scripted wheelie torque.\n'
        '        float comShiftForward = riderLean * RIDER_SHIFT;\n'
        '        float rearContactForwardArm = rearForward - comShiftForward\n'
        '                - WHEEL_RADIUS * rearContactState.normalForward;\n'
        '        float rearContactVerticalArm = rearVertical\n'
        '                - WHEEL_RADIUS * rearContactState.normalUp;\n'
        '        float frontContactForwardArm = frontForward - comShiftForward\n'
        '                - WHEEL_RADIUS * frontContactState.normalForward;',
        '        // Apply contact forces at the actual tire contact patches and integrate the resulting\n'
        '        // moment about the already-shifted COM. No second COM offset is applied here: axle\n'
        '        // geometry above already contains the rider weight transfer.\n'
        '        float rearContactForwardArm = rearForward\n'
        '                - WHEEL_RADIUS * rearContactState.normalForward;\n'
        '        float rearContactVerticalArm = rearVertical\n'
        '                - WHEEL_RADIUS * rearContactState.normalUp;\n'
        '        float frontContactForwardArm = frontForward\n'
        '                - WHEEL_RADIUS * frontContactState.normalForward;'
    ),
    (
        '        float steerAngle = steer * maxSteerDeg * MathUtils.degreesToRadians;',
        '        // UI/control convention is X<0 left, X>0 right. The world yaw convention used by\n'
        '        // this prototype is opposite, so convert once here rather than reversing the pad axis.\n'
        '        float steerAngle = -steer * maxSteerDeg * MathUtils.degreesToRadians;'
    ),
    (
        '            float visualSteerDeg = -steer * MathUtils.lerp(28f, 5f, visualSteerBlend);',
        '            // Match the imported fork/wheel to the same left-negative/right-positive control\n'
        '            // convention used by the rectangular pad.\n'
        '            float visualSteerDeg = steer * MathUtils.lerp(28f, 5f, visualSteerBlend);'
    ),
    (
        '            float helmetY = importedBikeLoaded ? 1.22f : 1.36f;\n'
        '            float helmetZ = importedBikeLoaded ? 0.66f : (0.58f + riderLean * 0.10f);',
        '            // Slightly lower/rearward helmet viewpoint for a more natural rider eye position.\n'
        '            float helmetY = importedBikeLoaded ? 1.16f : 1.30f;\n'
        '            float helmetZ = importedBikeLoaded ? 0.60f : (0.52f + riderLean * 0.10f);'
    ),
]

for old, new in repls:
    if old not in s:
        raise SystemExit('Expected block not found:\n' + old[:240])
    s = s.replace(old, new, 1)

p.write_text(s)
