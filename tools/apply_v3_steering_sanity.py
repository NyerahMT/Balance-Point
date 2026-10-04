from pathlib import Path

PATH = Path("core/src/main/java/com/nyerahworks/balancepoint/PhysicsV3MultibodyPlant.java")
text = PATH.read_text()
if "COUNTERSTEER_KP" in text and "LOW_SPEED_BALANCE_KP" in text:
    print("countersteer lean law already applied")
    raise SystemExit(0)
raise SystemExit("plant is missing the countersteer lean law; do not reapply the old same-direction steer patch")
