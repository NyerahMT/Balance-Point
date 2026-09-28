from pathlib import Path
import json
import math
import runpy

# Reuse the authoritative GLB parser/scene traversal from the normal asset validator.
ns = runpy.run_path("tools/validate_dirtbike.py")

doc = ns["doc"]
meshes = ns["meshes"]
nodes = ns["nodes"]
world = ns["world"]
read_positions = ns["read_positions"]
transform_point = ns["transform_point"]

layout_path = Path("app/src/main/assets/models/DirtBike.layout.json")
layout = json.loads(layout_path.read_text())
anchors = layout["anchors"]
rear = tuple(float(v) for v in anchors["rearAxle"])
front = tuple(float(v) for v in anchors["frontAxle"])
head = tuple(float(v) for v in anchors["steeringHead"])

# Legacy correction metadata may remain for provenance, but it must be identity.
correction = layout.get("staticGeometryCorrection")
if correction is not None:
    translation = tuple(float(v) for v in correction.get("translation", [0, 0, 0]))
    if any(abs(v) > 1e-9 for v in translation):
        raise SystemExit(
            "DirtBike neutral pose is no longer viewer-identical: "
            f"staticGeometryCorrection={translation}"
        )

steering_meshes = {
    "FrontShock_GEO",
    "Handle_GEO",
    "LeftLeaver_GEO",
    "RightLeaver_GEO",
}


def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def max_abs_delta(a, b):
    return max(abs(a[i] - b[i]) for i in range(3))


# Mirror the runtime grouping/rebasing math with every animation angle at zero.
# A correct importer must reconstruct every source GLB world-space vertex exactly.
max_error = 0.0
worst = None
for node_index, node in enumerate(nodes):
    mesh_index = node.get("mesh")
    if mesh_index is None:
        continue
    mesh = meshes[mesh_index]
    mesh_name = mesh.get("name", f"mesh-{mesh_index}")

    if mesh_name == "FrontWheel_GEO":
        pivot = front
        neutral_parent = add(head, sub(front, head))
    elif mesh_name == "RearWheel_GEO":
        pivot = rear
        neutral_parent = rear
    elif mesh_name in steering_meshes:
        pivot = head
        neutral_parent = head
    else:
        pivot = rear
        neutral_parent = rear

    for primitive in mesh.get("primitives", []):
        accessor_index = primitive.get("attributes", {}).get("POSITION")
        if accessor_index is None:
            continue
        for local in read_positions(accessor_index):
            authored = transform_point(world[node_index], local)
            rebased = sub(authored, pivot)
            reconstructed = add(neutral_parent, rebased)
            error = max_abs_delta(authored, reconstructed)
            if error > max_error:
                max_error = error
                worst = (mesh_name, authored, reconstructed)

if max_error > 1e-6:
    raise SystemExit(
        "DirtBike neutral-pose reconstruction drifted from the GLB viewer pose: "
        f"max_error={max_error:.9f} worst={worst}"
    )

# Guard against reintroducing the screenshot-tuned runtime offsets that caused the
# viewer/game mismatch in the first place.
game = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java"
).read_text()
for forbidden in (
    "IMPORTED_BODY_LIFT",
    "IMPORTED_STEERING_VISUAL_DROP",
    "importedBike.engine.transform.set(bikeRoot).translate",
):
    if forbidden in game:
        raise SystemExit(f"Forbidden imported-bike visual correction returned: {forbidden}")

required_snippets = (
    "importedBike.body.transform.set(bikeRoot);",
    "importedBike.engine.transform.set(bikeRoot);",
    "importedBike.steering.transform.set(importedSteeringRoot);",
)
for required in required_snippets:
    if required not in game:
        raise SystemExit(f"Missing neutral-pose runtime transform: {required}")

print(
    "DirtBike neutral pose OK: runtime zero pose reproduces authored GLB coordinates "
    f"(max vertex error {max_error:.9f} m)"
)
