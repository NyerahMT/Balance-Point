from pathlib import Path

path = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
text = path.read_text()

old = '''        TextureAttribute dashTextureAttribute = TextureAttribute.createDiffuse(instrumentTexture);\n        // The screen quad is already oriented correctly in world space. Keep the texture\n        // unflipped so the LCD reads upright from the rider/chase camera.\n        Material dashScreenMat = new Material(dashTextureAttribute,'''
new = '''        TextureAttribute dashTextureAttribute = TextureAttribute.createDiffuse(instrumentTexture);\n        // The world-space quad is upright now; only mirror U so the LCD reads left-to-right.\n        dashTextureAttribute.offsetU = 1f;\n        dashTextureAttribute.scaleU = -1f;\n        Material dashScreenMat = new Material(dashTextureAttribute,'''
if old not in text:
    raise SystemExit('dash texture block not found')
text = text.replace(old, new, 1)

old = '''        float absSpeed = Math.abs(speed);\n        boolean offRoad = Math.abs(bikeX) > ROAD_HALF_WIDTH;\n        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT, 0.48f, 0.82f);'''
new = '''        float absSpeed = Math.abs(speed);\n        boolean offRoad = Math.abs(bikeX) > ROAD_HALF_WIDTH;\n        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;\n        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;\n        float terrainSlopeMagnitude = (float) Math.sqrt(terrainSlopeX * terrainSlopeX\n                + terrainSlopeZ * terrainSlopeZ);\n        float terrainNormalScale = 1f / (float) Math.sqrt(1f\n                + terrainSlopeMagnitude * terrainSlopeMagnitude);\n        float forwardGrade = terrainSlopeX * MathUtils.sin(yaw)\n                + terrainSlopeZ * MathUtils.cos(yaw);\n        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT, 0.48f, 0.82f);'''
if old not in text:
    raise SystemExit('simulate terrain insertion point not found')
text = text.replace(old, new, 1)

old = '''        rearNormalEstimate = Math.max(0f, rearNormalEstimate);\n        float tractionLimit = TIRE_MU * rearNormalEstimate;'''
new = '''        rearNormalEstimate = Math.max(0f, rearNormalEstimate) * terrainNormalScale;\n        float tractionLimit = TIRE_MU * rearNormalEstimate;'''
if old not in text:
    raise SystemExit('traction block not found')
text = text.replace(old, new, 1)

old = '''        float surfaceDrag = offRoad ? 95f + absSpeed * 5f : 0f;\n        longitudinalAcceleration = (driveForce - brakeForce - rolling - aero - surfaceDrag) / MASS;'''
new = '''        float surfaceDrag = offRoad ? 95f + absSpeed * 5f : 0f;\n        float gradeAngle = (float) Math.atan(forwardGrade);\n        float gradeAcceleration = GRAVITY * MathUtils.sin(gradeAngle);\n        longitudinalAcceleration = (driveForce - brakeForce - rolling - aero - surfaceDrag) / MASS\n                - gradeAcceleration;'''
if old not in text:
    raise SystemExit('grade acceleration block not found')
text = text.replace(old, new, 1)

old = '''        float pitchDeg = pitch * MathUtils.radiansToDegrees;\n        float rollDeg = roll * MathUtils.radiansToDegrees;\n        float yawDeg = yaw * MathUtils.radiansToDegrees;\n        float rootHeight = crashed && crashSettled ? 0.22f : WHEEL_RADIUS;\n        bikeRoot.idt().translate(bikeX, rootHeight, bikeZ)\n                .rotate(Vector3.Y, yawDeg)\n                .rotate(Vector3.Z, rollDeg)\n                .rotate(Vector3.X, -pitchDeg);'''
new = '''        float pitchDeg = pitch * MathUtils.radiansToDegrees;\n        float rollDeg = roll * MathUtils.radiansToDegrees;\n        float yawDeg = yaw * MathUtils.radiansToDegrees;\n\n        float terrainHeight = terrainVisuals != null ? terrainVisuals.groundHeight(bikeX, bikeZ) : 0f;\n        float terrainSlopeX = terrainVisuals != null ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;\n        float terrainSlopeZ = terrainVisuals != null ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;\n        float sinYaw = MathUtils.sin(yaw);\n        float cosYaw = MathUtils.cos(yaw);\n        float terrainForwardGrade = terrainSlopeX * sinYaw + terrainSlopeZ * cosYaw;\n        float terrainLateralGrade = terrainSlopeX * cosYaw - terrainSlopeZ * sinYaw;\n        float terrainPitchDeg = (float) Math.atan(terrainForwardGrade) * MathUtils.radiansToDegrees;\n        float terrainRollDeg = (float) Math.atan(terrainLateralGrade) * MathUtils.radiansToDegrees;\n\n        float rootHeight = terrainHeight + (crashed && crashSettled ? 0.22f : WHEEL_RADIUS);\n        bikeRoot.idt().translate(bikeX, rootHeight, bikeZ)\n                .rotate(Vector3.Y, yawDeg)\n                .rotate(Vector3.Z, rollDeg + terrainRollDeg)\n                .rotate(Vector3.X, -(pitchDeg + terrainPitchDeg));'''
if old not in text:
    raise SystemExit('bike root block not found')
text = text.replace(old, new, 1)

path.write_text(text)
