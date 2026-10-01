from pathlib import Path
import math
import runpy
import struct

ns = runpy.run_path("tools/validate_dirtbike.py")
rear = ns["rear"]
front = ns["front"]
wheelbase = ns["wheelbase"]
doc = ns["doc"]
nodes = ns["nodes"]
meshes = ns["meshes"]
accessors = ns["accessors"]
views = ns["views"]
world = ns["world"]
bin_chunk = ns["bin_chunk"]
read_positions = ns["read_positions"]
transform_point = ns["transform_point"]

# GameScene raises BikeRoot by 0.31 m. The loader gets exactly one
# scene-wide translation so the source rear tire touches that existing ground
# convention. Every moving-part pivot is derived only after this rigid move.
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


def read_indices(accessor_index):
    accessor = accessors[accessor_index]
    view = views[accessor["bufferView"]]
    component_type = accessor["componentType"]
    formats = {5121: ("<B", 1), 5123: ("<H", 2), 5125: ("<I", 4)}
    if component_type not in formats:
        raise SystemExit(f"Unsupported index component type: {component_type}")
    fmt, size = formats[component_type]
    base = int(view.get("byteOffset", 0)) + int(accessor.get("byteOffset", 0))
    stride = int(view.get("byteStride", size))
    return [
        struct.unpack_from(fmt, bin_chunk, base + i * stride)[0]
        for i in range(int(accessor["count"]))
    ]


class UnionFind:
    def __init__(self, count):
        self.parent = list(range(count))
        self.rank = [0] * count

    def find(self, value):
        while self.parent[value] != value:
            self.parent[value] = self.parent[self.parent[value]]
            value = self.parent[value]
        return value

    def union(self, a, b):
        a = self.find(a)
        b = self.find(b)
        if a == b:
            return
        if self.rank[a] < self.rank[b]:
            a, b = b, a
        self.parent[b] = a
        if self.rank[a] == self.rank[b]:
            self.rank[a] += 1


def bounds(points):
    lo = tuple(min(p[i] for p in points) for i in range(3))
    hi = tuple(max(p[i] for p in points) for i in range(3))
    center = tuple((lo[i] + hi[i]) * 0.5 for i in range(3))
    return lo, hi, center


# Reproduce the Java topology split independently. Triangles are connected when
# they share a geometric vertex within 10 micrometres; no named fork/body offset
# or hand-authored placement table is involved.
region_start_z = rear_after[2] + wheelbase * 0.67
cube_component_count = 0
steering_components = []
fork_components = []

for node_index, node in enumerate(nodes):
    mesh_index = node.get("mesh")
    if mesh_index is None:
        continue
    mesh = meshes[mesh_index]
    mesh_name = mesh.get("name", "")
    if not mesh_name.startswith("Cube_"):
        continue

    for primitive in mesh.get("primitives", []):
        source_positions = read_positions(primitive["attributes"]["POSITION"])
        positions = [
            tuple(transform_point(world[node_index], p)[i] + translation[i] for i in range(3))
            for p in source_positions
        ]
        indices = read_indices(primitive["indices"])
        triangle_count = len(indices) // 3
        uf = UnionFind(triangle_count)
        first_triangle_at_position = {}

        for triangle in range(triangle_count):
            for corner in range(3):
                point = positions[indices[triangle * 3 + corner]]
                key = tuple(round(v * 100000.0) for v in point)
                previous = first_triangle_at_position.get(key)
                if previous is None:
                    first_triangle_at_position[key] = triangle
                else:
                    uf.union(triangle, previous)

        groups = {}
        for triangle in range(triangle_count):
            groups.setdefault(uf.find(triangle), []).append(triangle)

        for triangles in groups.values():
            cube_component_count += 1
            component_points = [
                positions[indices[triangle * 3 + corner]]
                for triangle in triangles
                for corner in range(3)
            ]
            lo, hi, center = bounds(component_points)
            if center[2] <= region_start_z:
                continue

            steering_components.append((mesh_name, lo, hi, center, component_points))
            height = hi[1] - lo[1]
            depth = hi[2] - lo[2]
            if (
                mesh_name == "Cube_METAL_0"
                and abs(center[0] - front_after[0]) < 0.15
                and height > 0.45
                and depth > 0.20
            ):
                fork_components.append(component_points)

if cube_component_count != 50:
    raise SystemExit(f"Unexpected Cube topology component count: {cube_component_count}")
if len(steering_components) != 22:
    raise SystemExit(f"Unexpected steering component count: {len(steering_components)}")
if len(fork_components) != 2:
    raise SystemExit(f"Expected two fork-axis components, got {len(fork_components)}")

# Fit the steering/rake axis to the two long fork components in the source Y/Z
# plane. The fitted line should pass through the authored front axle to within a
# few millimetres; otherwise the source topology or our classification changed.
fork_points = [point for component in fork_components for point in component]
count = len(fork_points)
mean_y = sum(p[1] for p in fork_points) / count
mean_z = sum(p[2] for p in fork_points) / count
cov_yy = sum(p[1] * p[1] for p in fork_points) / count - mean_y * mean_y
cov_zz = sum(p[2] * p[2] for p in fork_points) / count - mean_z * mean_z
cov_yz = sum(p[1] * p[2] for p in fork_points) / count - mean_y * mean_z
theta = 0.5 * math.atan2(2.0 * cov_yz, cov_yy - cov_zz)
axis = [0.0, math.cos(theta), math.sin(theta)]
if axis[1] < 0.0:
    axis = [-v for v in axis]

if axis[1] < 0.85 or not -0.60 < axis[2] < -0.25:
    raise SystemExit(f"Implausible steering/rake axis: {axis}")

line_point = (front_after[0], mean_y, mean_z)
delta = tuple(front_after[i] - line_point[i] for i in range(3))
along = sum(delta[i] * axis[i] for i in range(3))
pivot = tuple(line_point[i] + axis[i] * along for i in range(3))
front_axle_miss = math.dist(pivot, front_after)
if front_axle_miss > 0.015:
    raise SystemExit(
        f"Source-derived steering axis misses front axle by {front_axle_miss:.6f} m"
    )

loader = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/ViewerFaithfulDirtBikeLoader.java"
).read_text(encoding="utf-8")
facade = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/DirtBikeMeshLoader.java"
).read_text(encoding="utf-8")
scene = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/GameScene.java"
).read_text(encoding="utf-8")
visual_rig = Path(
    "core/src/main/java/com/nyerahworks/balancepoint/DirtBikeVisualRig.java"
).read_text(encoding="utf-8")
model_dir = Path("app/src/main/assets/models")

required = (
    'ASSET_PATH = "models/dirt_bike_off_road_bike_low_poly.glb"',
    "translatePositions(positions, normalization);",
    "STEERING_REGION_FRACTION = 0.67f",
    "splitConnectedComponents(",
    "ForkAxisAccumulator",
    "return ViewerFaithfulDirtBikeLoader.load(ownedModels);",
    "new DirtBikeVisualRig(importedBike, wheelbase, wheelRadius)",
    "importedRig.update(bikeRoot, state, terrainVisuals);",
    ".rotate(bike.steeringAxis, visualSteerDeg)",
    "tempB.set(bike.frontAxleOffset).sub(bike.steeringHead)",
    "findSwingarmParts(",
    "findFrontSliderParts(",
)
combined = loader + "\n" + facade + "\n" + scene + "\n" + visual_rig
for snippet in required:
    if snippet not in combined:
        raise SystemExit(f"Missing source-derived rig invariant: {snippet}")

# These were symptoms of the old screenshot-tuned importer. Their return is a
# build failure: neutral placement must still come from the GLB plus one rigid move.
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
    "Topology steering/suspension rig OK: "
    f"{cube_component_count} Cube components, {len(steering_components)} steerable, "
    f"axis=({axis[0]:.6f}, {axis[1]:.6f}, {axis[2]:.6f}), "
    f"front-axle miss={front_axle_miss:.6f} m; neutral pose remains exact"
)
