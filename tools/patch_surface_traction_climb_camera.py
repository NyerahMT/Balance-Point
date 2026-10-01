from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing anchor: {label}')
    if text.count(old) != 1:
        raise SystemExit(f'non-unique anchor {label}: {text.count(old)}')
    return text.replace(old, new, 1)

root = Path('.')

game_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java'
game = game_path.read_text()

game = replace_once(game,
'''    private float lookYaw;\n    private float lookPitch;\n    private boolean looking;''',
'''    private float lookYaw;\n    private float lookPitch;\n    // Helmet pitch follows the bike's forward path, not chassis attitude or a fixed horizon.\n    // A damped angular state prevents suspension/terrain chatter from shaking the rider view.\n    private float helmetTrajectoryPitch;\n    private float helmetTrajectoryPitchVelocity;\n    private boolean looking;''',
'helmet trajectory fields')

game = replace_once(game,
'''        // Axle locations relative to the CURRENT combined COM. Positive rider lean moves\n        // the mass forward; negative lean moves it rearward. Contact geometry, velocities\n        // and force moments all use the same shifted COM instead of applying a late torque\n        // correction after wheel contact has already been solved.\n        float effectiveComForward = MathUtils.clamp(COM_FORWARD + riderLean * RIDER_SHIFT,\n                0.48f, 0.88f);''',
'''        // Axle locations relative to the CURRENT combined COM. Positive rider lean moves\n        // the mass forward; negative lean moves it rearward. On an uphill, a real rider does\n        // not remain rigidly rotated with the chassis: they naturally keep their torso forward\n        // over the bike. Approximate that posture with a grade-dependent COM shift instead of\n        // letting steep terrain rotate the whole combined mass rearward and create a fake loop.\n        float climbPostureShift = 0f;\n        if (terrainVisuals != null && !terrainAirborne) {\n            float centerSlopeX = terrainVisuals.groundSlopeX(bikeX, bikeZ);\n            float centerSlopeZ = terrainVisuals.groundSlopeZ(bikeX, bikeZ);\n            float forwardGrade = centerSlopeX * sinYaw + centerSlopeZ * cosYaw;\n            float uphillAngle = (float) Math.atan(Math.max(0f, forwardGrade));\n            float climbBlend = MathUtils.clamp(uphillAngle\n                    / (40f * MathUtils.degreesToRadians), 0f, 1f);\n            climbPostureShift = climbBlend * 0.14f;\n        }\n        float effectiveComForward = MathUtils.clamp(\n                COM_FORWARD + riderLean * RIDER_SHIFT + climbPostureShift, 0.48f, 0.96f);''',
'climb posture shift')

game = replace_once(game,
'''        float drivetrainForce = drivetrain.update(absSpeed, throttle, dt);\n        float rearTractionLimit = TIRE_MU * rearContactState.normalForce;\n        float driveForce = rearTouching''',
'''        float drivetrainForce = drivetrain.update(absSpeed, throttle, dt);\n        // Grip comes from the same terrain classification used to paint the baked albedo.\n        // Asphalt is the 1.00 reference; grass/dirt/rock progressively lose longitudinal grip.\n        float rearSurfaceMu = terrainVisuals != null\n                ? terrainVisuals.tractionCoefficient(rearX, rearZ) : 1.0f;\n        float rearTractionLimit = rearSurfaceMu * rearContactState.normalForce;\n        float driveForce = rearTouching''',
'surface traction')

game = replace_once(game,
'''            // Helmet position follows bike height, but eye pitch remains horizon-referenced.\n            // Bank/sidehill roll still comes through, matching how a rider keeps their gaze\n            // level up/down while their body and helmet roll with the motorcycle.\n            float cp = MathUtils.cos(lookPitch);\n            camera.direction.set(MathUtils.sin(viewYaw) * cp,\n                    MathUtils.sin(lookPitch), MathUtils.cos(viewYaw) * cp).nor();''',
'''            // Look along the bike's FORWARD TRAJECTORY rather than locking pitch to either\n            // the horizon or chassis. Grounded trajectory follows the locally averaged grade;\n            // in flight it follows the ballistic velocity vector. A critically damped angular\n            // state keeps suspension motion from turning into helmet-camera shake.\n            float trajectoryPitchTarget;\n            if (terrainAirborne) {\n                trajectoryPitchTarget = (float) Math.atan2(verticalVelocity,\n                        Math.max(1.5f, Math.abs(speed)));\n            } else if (terrainVisuals != null) {\n                float pathSlopeX = terrainVisuals.groundSlopeX(bikeX, bikeZ);\n                float pathSlopeZ = terrainVisuals.groundSlopeZ(bikeX, bikeZ);\n                float pathGrade = pathSlopeX * MathUtils.sin(yaw)\n                        + pathSlopeZ * MathUtils.cos(yaw);\n                trajectoryPitchTarget = (float) Math.atan(pathGrade);\n            } else {\n                trajectoryPitchTarget = 0f;\n            }\n            float cameraPitchFrequency = 6.2f;\n            float cameraPitchDamping = 1.08f;\n            float cameraPitchAccel = (trajectoryPitchTarget - helmetTrajectoryPitch)\n                    * cameraPitchFrequency * cameraPitchFrequency\n                    - 2f * cameraPitchDamping * cameraPitchFrequency\n                    * helmetTrajectoryPitchVelocity;\n            helmetTrajectoryPitchVelocity += cameraPitchAccel * dt;\n            helmetTrajectoryPitch += helmetTrajectoryPitchVelocity * dt;\n            float viewPitch = MathUtils.clamp(helmetTrajectoryPitch + lookPitch,\n                    -60f * MathUtils.degreesToRadians, 60f * MathUtils.degreesToRadians);\n            float cp = MathUtils.cos(viewPitch);\n            camera.direction.set(MathUtils.sin(viewYaw) * cp,\n                    MathUtils.sin(viewPitch), MathUtils.cos(viewYaw) * cp).nor();''',
'helmet trajectory camera')

game = replace_once(game,
'''        lookYaw = lookPitch = 0f;\n        shiftDownHeld = shiftUpHeld = false;''',
'''        lookYaw = lookPitch = 0f;\n        helmetTrajectoryPitch = helmetTrajectoryPitchVelocity = 0f;\n        shiftDownHeld = shiftUpHeld = false;''',
'helmet reset')

game_path.write_text(game)

terrain_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/TerrainVisuals.java'
terrain = terrain_path.read_text()

terrain = replace_once(terrain,
'''    private final Texture terrainTexture;\n    private final Material terrainMaterial;''',
'''    private final Texture terrainTexture;\n    private final Material terrainMaterial;\n    // Same deterministic surface field used by TerrainBaker.writeAlbedo(). This keeps\n    // physical grip aligned with what the player actually sees on the baked terrain.\n    private final FastNoiseLite surfaceNoise = createSurfaceNoise();''',
'surface noise field')

terrain = replace_once(terrain,
'''    private static Material material(float r, float g, float b) {\n        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));\n    }\n''',
'''    private static Material material(float r, float g, float b) {\n        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));\n    }\n\n    private static FastNoiseLite createSurfaceNoise() {\n        FastNoiseLite n = new FastNoiseLite(4502026 + 131);\n        n.SetNoiseType(FastNoiseLite.NoiseType.Perlin);\n        n.SetFractalType(FastNoiseLite.FractalType.FBm);\n        n.SetFrequency(0.035f);\n        n.SetFractalOctaves(4);\n        n.SetFractalGain(0.48f);\n        n.SetFractalLacunarity(2.0f);\n        return n;\n    }\n\n    /**\n     * Longitudinal tire friction coefficient at a world point. Road is the 1.00 baseline.\n     * Off-road values blend continuously using the same grass/dirt/rock weights as the\n     * albedo baker so there are no invisible traction boundaries.\n     */\n    float tractionCoefficient(float x, float z) {\n        if (Math.abs(x) <= ROAD_EDGE) return 1.00f;\n\n        float h = groundHeight(x, z);\n        float sx = groundSlopeX(x, z);\n        float sz = groundSlopeZ(x, z);\n        float slope = (float) Math.sqrt(sx * sx + sz * sz);\n        float patch = MathUtils.clamp(surfaceNoise.GetNoise(x, z) * 0.5f + 0.5f, 0f, 1f);\n\n        float rock = smootherStep(0.62f, 1.25f, slope);\n        rock = Math.max(rock, smootherStep(58f, 92f, h) * 0.70f);\n        float dirt = smootherStep(0.24f, 0.68f, slope) * (1f - rock);\n        dirt = Math.max(dirt, smootherStep(0.73f, 0.91f, patch)\n                * (1f - rock) * 0.65f);\n        float grass = MathUtils.clamp(1f - rock - dirt, 0f, 1f);\n\n        // Dry knobby-tire prototype values: asphalt 1.00, dirt 0.82, rock 0.72, grass 0.64.\n        return MathUtils.clamp(grass * 0.64f + dirt * 0.82f + rock * 0.72f, 0.60f, 0.86f);\n    }\n\n    private static float smootherStep(float edge0, float edge1, float x) {\n        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);\n        return t * t * t * (t * (t * 6f - 15f) + 10f);\n    }\n''',
'surface traction methods')

terrain_path.write_text(terrain)
