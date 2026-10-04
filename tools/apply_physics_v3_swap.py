#!/usr/bin/env python3
"""Swap BalancePointGame from Physics V2 to Physics V3.

The script is intentionally tiny and idempotent. Physics V2 source stays in the
repository as a fallback/reference; only the live game dependency is changed.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java"
MARKER = "PHYSICS_V3_RUNTIME"

text = GAME.read_text()
if MARKER in text:
    print("Physics V3 runtime already active")
    raise SystemExit(0)

required = [
    "PHYSICS_V2_RUNTIME",
    "PhysicsV2Plant",
    "physicsV2Terrain",
    "physicsV2",
]
for token in required:
    if token not in text:
        raise SystemExit(f"missing expected migration token: {token}")

text = text.replace("PHYSICS_V2_RUNTIME", "PHYSICS_V3_RUNTIME")
text = text.replace("PhysicsV2Plant", "PhysicsV3MultibodyPlant")
text = text.replace("physicsV2Terrain", "physicsV3Terrain")
text = text.replace("physicsV2", "physicsV3")
text = text.replace(
    "The Y conversion below is only\n        // an imported-mesh origin adapter: GameScene still expects its historical visual root.",
    "The Y conversion below is only\n        // an imported-mesh origin adapter; it does not feed back into Physics V3.",
)

GAME.write_text(text)
print("BalancePointGame now uses PhysicsV3MultibodyPlant")
