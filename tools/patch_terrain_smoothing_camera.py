from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    if text.count(old) != 1:
        raise SystemExit(f"patch anchor not unique ({text.count(old)}): {label}")
    return text.replace(old, new, 1)

root = Path('.')

# --- Terrain baker: smooth the actual baked surface, fix albedo orientation/aspect. ---
baker_path = root / 'tools/TerrainBaker.java'
baker = baker_path.read_text()

baker = replace_once(
    baker,
    '''        baker.carveRoadCorridor();\n        baker.thermalRelax(1);\n\n        File heightFile = new File(outDir, "world.bpheight");''',
    '''        baker.carveRoadCorridor();\n        baker.thermalRelax(1);\n        System.out.println("Local Gaussian terrain smoothing...");\n        baker.gaussianSmooth(2);\n\n        File heightFile = new File(outDir, "world.bpheight");''',
    'post erosion smoothing call')

baker = replace_once(
    baker,
    '        baker.writeAlbedo(albedoFile, 1024, 4096);',
    '        baker.writeAlbedo(albedoFile, 512, 4096);',
    'albedo aspect ratio')

insert_anchor = '''    private void writeHeightfield(File file) throws Exception {\n'''
insert_method = '''    /**\n     * Local low-pass pass over the baked heightfield. A separable 1-4-6-4-1 kernel\n     * removes one/two-cell needles left by ridged noise + erosion without flattening\n     * the broad mountain mass. Two passes give an effective smoothing radius of only\n     * a few metres on the 1.5 m source grid.\n     */\n    private void gaussianSmooth(int passes) {\n        final int[] kernel = {1, 4, 6, 4, 1};\n        final int kernelSum = 16;\n        float[] scratch = new float[heights.length];\n\n        for (int pass = 0; pass < passes; pass++) {\n            for (int z = 0; z < HEIGHT; z++) {\n                for (int x = 0; x < WIDTH; x++) {\n                    float sum = 0f;\n                    for (int k = -2; k <= 2; k++) {\n                        int sx = Math.max(0, Math.min(WIDTH - 1, x + k));\n                        sum += heights[index(sx, z)] * kernel[k + 2];\n                    }\n                    scratch[index(x, z)] = sum / kernelSum;\n                }\n            }\n\n            for (int z = 0; z < HEIGHT; z++) {\n                for (int x = 0; x < WIDTH; x++) {\n                    float sum = 0f;\n                    for (int k = -2; k <= 2; k++) {\n                        int sz = Math.max(0, Math.min(HEIGHT - 1, z + k));\n                        sum += scratch[index(x, sz)] * kernel[k + 2];\n                    }\n                    heights[index(x, z)] = sum / kernelSum;\n                }\n            }\n        }\n    }\n\n'''
baker = replace_once(baker, insert_anchor, insert_method + insert_anchor,
                     'gaussian smoothing method insertion')

baker = replace_once(
    baker,
    '''                // Flip rows here so runtime UV v=0 corresponds to ORIGIN_Z.\n                image.setRGB(px, texH - 1 - py, rgb);''',
    '''                // Raw mesh UV v=0 samples the first PNG row, so keep image rows in\n                // increasing world-Z order. The old extra flip mirrored the albedo along Z.\n                image.setRGB(px, py, rgb);''',
    'albedo row orientation')

baker_path.write_text(baker)

# --- Runtime terrain: use a local central-difference grade rather than one triangle face. ---
terrain_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/TerrainVisuals.java'
terrain = terrain_path.read_text()

terrain = replace_once(
    terrain,
    '''    float groundSlopeX(float x, float z) {\n        if (Math.abs(x) <= ROAD_EDGE) return 0f;\n        CellSample c = cell(x, z);\n        return c.fx + c.fz <= 1f\n                ? (c.h10 - c.h00) / sampleSpacing\n                : (c.h11 - c.h01) / sampleSpacing;\n    }\n\n    float groundSlopeZ(float x, float z) {\n        if (Math.abs(x) <= ROAD_EDGE) return 0f;\n        CellSample c = cell(x, z);\n        return c.fx + c.fz <= 1f\n                ? (c.h01 - c.h00) / sampleSpacing\n                : (c.h11 - c.h10) / sampleSpacing;\n    }''',
    '''    float groundSlopeX(float x, float z) {\n        if (Math.abs(x) <= ROAD_EDGE) return 0f;\n        // Average grade over a 6 m window instead of inheriting the slope of one 1.5 m\n        // triangle. Geometry still uses the same heightfield, but normals/contact response\n        // no longer jump at every cell diagonal.\n        float r = sampleSpacing * 2f;\n        return (groundHeight(x + r, z) - groundHeight(x - r, z)) / (2f * r);\n    }\n\n    float groundSlopeZ(float x, float z) {\n        if (Math.abs(x) <= ROAD_EDGE) return 0f;\n        float r = sampleSpacing * 2f;\n        return (groundHeight(x, z + r) - groundHeight(x, z - r)) / (2f * r);\n    }''',
    'smoothed terrain gradients')

terrain_path.write_text(terrain)

# --- Helmet camera: consume the already-damped bike/support bank, never raw terrain slope. ---
game_path = root / 'core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java'
game = game_path.read_text()

game = replace_once(
    game,
    '''            float cameraTerrainSlopeX = terrainVisuals != null\n                    ? terrainVisuals.groundSlopeX(bikeX, bikeZ) : 0f;\n            float cameraTerrainSlopeZ = terrainVisuals != null\n                    ? terrainVisuals.groundSlopeZ(bikeX, bikeZ) : 0f;\n            float cameraLateralGrade = cameraTerrainSlopeX * MathUtils.cos(yaw)\n                    - cameraTerrainSlopeZ * MathUtils.sin(yaw);\n            float cameraBank = roll + (terrainAirborne ? 0f\n                    : (float) Math.atan(cameraLateralGrade));''',
    '''            // Do not resample raw terrain under the camera. The chassis already carries\n            // critically damped roll plus filtered sidehill support bank; re-reading a single\n            // terrain cell here was the last source of the old sideways helmet-camera twitch.\n            float cameraBank = roll + terrainRoll;''',
    'helmet camera bank')

game_path.write_text(game)

print('patched TerrainBaker.java, TerrainVisuals.java, and BalancePointGame.java')
