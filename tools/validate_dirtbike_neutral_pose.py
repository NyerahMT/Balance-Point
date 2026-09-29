from pathlib import Path
import math
import runpy

ns = runpy.run_path("tools/validate_dirtbike.py")
rear = ns["rear"]
front = ns["front"]
wheelbase = ns["wheelbase"]

# BalancePointGame currently translates BikeRoot upward by 0.31 m. The new loader
# is allowed exactly ONE scene-wide translation so the source rear tire touches
# that ground convention. This preserves every pairwise relationship in the GLB.
GAME_ROOT_LIFT = 0.31
translation = (
    -rear["center"][0],
    -GAME_ROOT_LIFT - rear["min"][1],
    -rear["center"][2],
)

rear_after = tuple(rear["center"][i] + translation[i] for i in range(3))
front_after = tuple(front["center"][i] + translation[i] for i in range(3))
rear_bottom_after = rear["min"][1] + translation[1]

if abs(rear_after[0]) > 1e-6 or abs(rear_after[2]) > 1e-6:
    raise SystemExit(f"Rear wheel is not normalized to game X/Z origin: {rear_after}")
if abs(rear_bottom_after + GAME_ROOT_LIFT) > 1e-6:
    raise SystemExit(
        f"Rear tire contact is not normalized to BikeRoot ground convention: {rear_bottom_after}"
    )
if abs(math.dist(rear_after, front_after) - wheelbase) > 1e-9:
    raise SystemExit("Rigid normalization changed the wheelbase")

loader = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/ViewerFaithfulDirtBikeLoader.java"
).read_text(encoding="utf-8")
facade = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/DirtBikeMeshLoader.java"
).read_text(encoding="utf-8")
model_dir = Path("app/src/main/assets/models")

required = (
    'ASSET_PATH = "models/dirt_bike_off_road_bike_low_poly.glb"',
    "translatePositions(positions, normalization);",
    "return ViewerFaithfulDirtBikeLoader.load(ownedModels);",
)
combined = loader + "\n" + facade
for snippet in required:
    if snippet not in combined:
        raise SystemExit(f"Missing viewer-faithful import invariant: {snippet}")

# These were all symptoms of the old screenshot-tuned importer. Their return is a
# build failure: neutral placement must come from the GLB plus one whole-scene move.
for forbidden in (
    "DirtBike.layout.json",
    "staticGeometryCorrection",
    "rebasePositions",
    "IMPORTED_BODY_LIFT",
    "IMPORTED_STEERING_VISUAL_DROP",
    "Body_GEO",
    "Engine_GEO",
    "FrontShock_GEO",
):
    if forbidden in combined:
        raise SystemExit(f"Legacy per-part import logic returned: {forbidden}")

for obsolete in ("DirtBike.layout.json", "DirtBike.payload.00", "DirtBike.glb"):
    if (model_dir / obsolete).exists():
        raise SystemExit(f"Obsolete dirt-bike asset still present: {obsolete}")

print(
    "Viewer-faithful neutral pose OK: source scene preserved with one rigid translation "
    f"{translation}; wheelbase remains {wheelbase:.6f} m"
)
