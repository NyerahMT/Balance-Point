from pathlib import Path

root = Path(__file__).resolve().parents[1]
game = root / 'core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java'
drive = root / 'core/src/main/java/com/nyerahworks/balancepoint/MotorcycleDrivetrain.java'

g = game.read_text()
repls = {
    'private static final float WHEELBASE = 1.45f;': 'private static final float WHEELBASE = 1.478f;',
    'private static final float WHEEL_RADIUS = 0.31f;': 'private static final float WHEEL_RADIUS = 0.337f;',
    'private static final float MASS = 212f;': '// Approx. 247 lb wet 450 + 190 lb rider = 198 kg combined system mass.\n    private static final float MASS = 198f;'
}
for old, new in repls.items():
    if old not in g:
        raise SystemExit(f'missing expected BalancePointGame token: {old}')
    g = g.replace(old, new, 1)
game.write_text(g)

d = drive.read_text()
repls = {
    'private static final float IDLE_RPM = 1_800f;': 'private static final float IDLE_RPM = 1_900f;',
    'private static final float REDLINE_RPM = 13_200f;': 'private static final float REDLINE_RPM = 11_500f;',
    'private static final float HARD_LIMIT_RPM = 13_700f;': 'private static final float HARD_LIMIT_RPM = 11_900f;',
    'private static final float FINAL_DRIVE_RATIO = 4.00f;': '// 49/13 is a common modern 450 MX final drive (e.g. CRF450R).\n    private static final float FINAL_DRIVE_RATIO = 49f / 13f;',
    'float freeRevRpm = IDLE_RPM + throttle * 5_300f;': 'float freeRevRpm = IDLE_RPM + throttle * 9_000f;'
}
for old, new in repls.items():
    if old not in d:
        raise SystemExit(f'missing expected drivetrain token: {old}')
    d = d.replace(old, new, 1)

start = d.index('    private static float torqueCurve(float rpm) {')
end = d.index('    private static float overallRatio', start)
new_curve = '''    private static float torqueCurve(float rpm) {\n        // Broad modern 450 single: huge midrange, peak torque around 7k,\n        // then a real top-end roll-off instead of carrying torque to 13k.\n        if (rpm < 3_000f) {\n            return MathUtils.lerp(0.60f, 0.73f,\n                    MathUtils.clamp((rpm - IDLE_RPM) / (3_000f - IDLE_RPM), 0f, 1f));\n        }\n        if (rpm < 5_500f) {\n            return MathUtils.lerp(0.73f, 0.92f, (rpm - 3_000f) / 2_500f);\n        }\n        if (rpm < 7_000f) {\n            return MathUtils.lerp(0.92f, 1.00f, (rpm - 5_500f) / 1_500f);\n        }\n        if (rpm < 8_500f) {\n            return MathUtils.lerp(1.00f, 0.98f, (rpm - 7_000f) / 1_500f);\n        }\n        if (rpm < 9_500f) {\n            return MathUtils.lerp(0.98f, 0.95f, (rpm - 8_500f) / 1_000f);\n        }\n        if (rpm < 10_500f) {\n            return MathUtils.lerp(0.95f, 0.84f, (rpm - 9_500f) / 1_000f);\n        }\n        if (rpm < REDLINE_RPM) {\n            return MathUtils.lerp(0.84f, 0.68f,\n                    (rpm - 10_500f) / (REDLINE_RPM - 10_500f));\n        }\n        return 0.60f;\n    }\n\n'''
d = d[:start] + new_curve + d[end:]
drive.write_text(d)
print('450 baseline applied')
