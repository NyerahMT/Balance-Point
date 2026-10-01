from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

TEXT_SUFFIXES = {
    ".java",
    ".py",
    ".gradle",
    ".properties",
    ".xml",
    ".yml",
    ".yaml",
    ".md",
    ".txt",
}

BANNED_PATHS = {
    "app/src/main/java/com/nyerahworks/balancepoint/BikePhysics.java",
    "app/src/main/java/com/nyerahworks/balancepoint/GameView.java",
    "tools/patch_baked_terrain_physics.py",
    ".github/workflows/add-gradle-wrapper.yml",
}

BANNED_MARKERS = ("TODO", "FIXME", "HACK")
WILDCARD_IMPORT = re.compile(r"(?m)^\s*import\s+[\w.]+\.\*\s*;")
LINE_LENGTH_SUFFIXES = {".java", ".py", ".gradle", ".yml", ".yaml"}
ARCHITECTURE_LINE_LIMITS = {
    "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java": 1200,
    "core/src/main/java/com/nyerahworks/balancepoint/GameScene.java": 650,
}

errors = []

for relative in sorted(BANNED_PATHS):
    if (ROOT / relative).exists():
        errors.append(f"obsolete file must not return: {relative}")

for path in ROOT.glob("tools/refactor_*_once.py"):
    errors.append(f"one-shot refactor script must not remain on main: {path.relative_to(ROOT)}")
for path in ROOT.glob(".github/workflows/*refactor*.yml"):
    errors.append(f"temporary refactor workflow must not remain on main: {path.relative_to(ROOT)}")

readme = ROOT / "README.md"
if not readme.is_file() or readme.stat().st_size < 1200:
    errors.append("README.md must contain real build/architecture documentation")

architecture = ROOT / "docs/ARCHITECTURE.md"
if not architecture.is_file() or architecture.stat().st_size < 1000:
    errors.append("docs/ARCHITECTURE.md is missing or incomplete")

wrapper_files = (
    ROOT / "gradlew",
    ROOT / "gradlew.bat",
    ROOT / "gradle/wrapper/gradle-wrapper.jar",
    ROOT / "gradle/wrapper/gradle-wrapper.properties",
)
for wrapper_file in wrapper_files:
    if not wrapper_file.is_file():
        errors.append(f"missing Gradle wrapper file: {wrapper_file.relative_to(ROOT)}")

wrapper_properties = ROOT / "gradle/wrapper/gradle-wrapper.properties"
if wrapper_properties.is_file():
    wrapper_text = wrapper_properties.read_text(encoding="utf-8")
    if "gradle-8.11.1-bin.zip" not in wrapper_text:
        errors.append("Gradle wrapper must remain pinned to 8.11.1")

for relative, limit in ARCHITECTURE_LINE_LIMITS.items():
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"required architecture file missing: {relative}")
        continue
    line_count = len(path.read_text(encoding="utf-8").splitlines())
    if line_count > limit:
        errors.append(
            f"architecture ceiling exceeded: {relative} has {line_count} lines (limit {limit})"
        )

first_party_java = []
for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    if any(part in {".git", ".gradle", "build", "THIRD_PARTY_LICENSES"} for part in path.parts):
        continue

    relative = path.relative_to(ROOT).as_posix()
    if path.suffix not in TEXT_SUFFIXES and path.name not in {".gitignore", ".editorconfig"}:
        continue

    is_vendored = relative.endswith("/FastNoiseLite.java")
    if is_vendored:
        continue

    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        errors.append(f"text file is not UTF-8: {relative}")
        continue

    if text and not text.endswith("\n"):
        errors.append(f"missing final newline: {relative}")

    for line_number, line in enumerate(text.splitlines(), start=1):
        if "\t" in line:
            errors.append(f"tab indentation: {relative}:{line_number}")
        if line.rstrip(" ") != line:
            errors.append(f"trailing whitespace: {relative}:{line_number}")
        if path.suffix in LINE_LENGTH_SUFFIXES and len(line) > 180:
            errors.append(f"line exceeds 180 characters: {relative}:{line_number}")

    if path.suffix == ".java":
        first_party_java.append((relative, text))
        if WILDCARD_IMPORT.search(text):
            errors.append(f"wildcard import: {relative}")
        is_runtime = relative.startswith("core/src/main/java/") or relative.startswith(
            "app/src/main/java/"
        )
        if is_runtime and ("System.out." in text or "System.err." in text):
            errors.append(f"runtime Java must use structured logging: {relative}")
        for marker in BANNED_MARKERS:
            if marker in text:
                errors.append(f"{marker} marker on main: {relative}")

java_tests = [text for relative, text in first_party_java if "src/test/java" in relative]
if not java_tests:
    errors.append("no Java regression tests found under src/test/java")
elif sum(text.count("@Test") for text in java_tests) < 5:
    errors.append("core regression suite must retain at least five deterministic tests")

if errors:
    print("Repository quality gate failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Repository quality gate OK: "
    f"{len(first_party_java)} first-party Java source/test files checked"
)
