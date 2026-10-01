from pathlib import Path

path = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
text = path.read_text()

repls = [
    (
        'import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;\nimport com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;',
        'import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;\nimport com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;\nimport com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;'
    ),
    (
        '        Material dashScreenMat = new Material(dashTextureAttribute,\n                ColorAttribute.createDiffuse(Color.WHITE));',
        '        Material dashScreenMat = new Material(dashTextureAttribute,\n                ColorAttribute.createDiffuse(Color.WHITE));\n        // The UV-corrected quad faces the opposite winding from the generated bike geometry.\n        // Keep the LCD double-sided so global back-face culling cannot make it disappear.\n        dashScreenMat.set(IntAttribute.createCullFace(GL20.GL_NONE));'
    ),
    (
        '            steerTarget = leftArrowTouch ? 0.65f : -0.65f;',
        '            steerTarget = leftArrowTouch ? 1.0f : -1.0f;'
    ),
    (
        '        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);\n        float maxSteerDeg = MathUtils.lerp(16f, 4.5f, speedBlend);',
        '        // Dirt-bike steering needs real low-speed lock for switchbacks/U-turns, while\n        // high-speed steering must remain small enough to stay controllable.\n        float speedBlend = MathUtils.clamp(speed / 30f, 0f, 1f);\n        speedBlend = speedBlend * speedBlend * (3f - 2f * speedBlend);\n        float maxSteerDeg = MathUtils.lerp(34f, 5.0f, speedBlend);'
    ),
    (
        '        rollTarget = MathUtils.clamp(rollTarget,\n                -46f * MathUtils.degreesToRadians, 46f * MathUtils.degreesToRadians);',
        '        rollTarget = MathUtils.clamp(rollTarget,\n                -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);'
    ),
    (
        '        if (pitch > LOOP_ANGLE || Math.abs(bikeX) > ROAD_HALF_WIDTH + 10f\n                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)) {',
        '        // Leaving the road is not a crash. The old lateral boundary check made any\n        // committed turn or U-turn eventually trigger an artificial game-over.\n        if (pitch > LOOP_ANGLE\n                || Float.isNaN(pitch) || Float.isNaN(speed) || Float.isNaN(yaw)\n                || Float.isNaN(roll) || Float.isNaN(bikeX) || Float.isNaN(bikeZ)) {'
    ),
    (
        '            float visualSteerDeg = -steer * MathUtils.lerp(13f, 5f,\n                    MathUtils.clamp(speed / 30f, 0f, 1f));',
        '            float visualSteerBlend = MathUtils.clamp(speed / 30f, 0f, 1f);\n            visualSteerBlend = visualSteerBlend * visualSteerBlend * (3f - 2f * visualSteerBlend);\n            float visualSteerDeg = -steer * MathUtils.lerp(28f, 5f, visualSteerBlend);'
    ),
]

for old, new in repls:
    if old not in text:
        raise SystemExit('expected patch target not found:\n' + old)
    text = text.replace(old, new, 1)

path.write_text(text)
