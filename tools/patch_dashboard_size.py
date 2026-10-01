from pathlib import Path

p = Path("core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java")
s = p.read_text()

replacements = {
    "Model dashM = box(b, 0.19f, 0.025f, 0.105f, dashMat);":
        "Model dashM = box(b, 0.145f, 0.022f, 0.082f, dashMat);",
    "Model dashButtonM = box(b, 0.020f, 0.007f, 0.014f, dashButtonMat);":
        "Model dashButtonM = box(b, 0.016f, 0.006f, 0.011f, dashButtonMat);",
    "                -0.078f, 0f, -0.030f,\n                -0.078f, 0f,  0.038f,\n                 0.078f, 0f,  0.038f,\n                 0.078f, 0f, -0.030f,":
        "                -0.060f, 0f, -0.024f,\n                -0.060f, 0f,  0.029f,\n                 0.060f, 0f,  0.029f,\n                 0.060f, 0f, -0.024f,",
    ".translate(0f, 0.0132f, 0.006f);":
        ".translate(0f, 0.0117f, 0.004f);",
    ".translate(-0.055f, 0.0162f, -0.041f);":
        ".translate(-0.041f, 0.0143f, -0.032f);",
    ".translate(0.055f, 0.0162f, -0.041f);":
        ".translate(0.041f, 0.0143f, -0.032f);",
}

for old, new in replacements.items():
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match for {old!r}, found {count}")
    s = s.replace(old, new, 1)

p.write_text(s)
print("Shrank physical dashboard to compact dirtbike-computer proportions")
