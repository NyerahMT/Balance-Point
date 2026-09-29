from pathlib import Path
import json
import math
import struct

PATH = Path("app/src/main/assets/models/dirt_bike_off_road_bike_low_poly.glb")
ATTRIBUTION = Path("app/src/main/assets/models/DirtBike.ATTRIBUTION.txt")

if not PATH.is_file():
    raise SystemExit(f"Missing replacement dirt bike: {PATH}")
if not ATTRIBUTION.is_file():
    raise SystemExit("Missing CC BY 4.0 dirt-bike attribution file")

blob = PATH.read_bytes()
if len(blob) < 20:
    raise SystemExit(f"Replacement dirt bike GLB is too small: {len(blob)} bytes")

magic, version, declared_length = struct.unpack_from("<4sII", blob, 0)
if magic != b"glTF":
    raise SystemExit(f"Bad GLB magic: {magic!r}")
if version != 2:
    raise SystemExit(f"Expected GLB version 2, got {version}")
if declared_length != len(blob):
    raise SystemExit(f"Truncated GLB: header={declared_length} file={len(blob)}")

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942
offset = 12
json_chunk = None
bin_chunk = None
while offset + 8 <= len(blob):
    chunk_length, chunk_type = struct.unpack_from("<II", blob, offset)
    offset += 8
    end = offset + chunk_length
    if end > len(blob):
        raise SystemExit("GLB chunk overruns file")
    chunk = blob[offset:end]
    offset = end
    if chunk_type == JSON_CHUNK:
        json_chunk = chunk
    elif chunk_type == BIN_CHUNK:
        bin_chunk = chunk

if json_chunk is None or bin_chunk is None:
    raise SystemExit("Replacement dirt bike must contain JSON and BIN chunks")

doc = json.loads(json_chunk.decode("utf-8").rstrip(" \t\r\n\x00"))
asset = doc.get("asset", {})
if asset.get("version") != "2.0":
    raise SystemExit(f"Unexpected glTF version: {asset.get('version')}")

extras = asset.get("extras", {})
license_text = str(extras.get("license", ""))
source_text = str(extras.get("source", ""))
author_text = str(extras.get("author", ""))
if "CC-BY-4.0" not in license_text:
    raise SystemExit(f"Unexpected dirt-bike license metadata: {license_text!r}")
if "sketchfab.com" not in source_text or not author_text:
    raise SystemExit("Replacement dirt bike is missing source/author metadata")

attribution = ATTRIBUTION.read_text(encoding="utf-8")
for required in ("CC BY 4.0", "nabeelashrafphotography", "sketchfab.com"):
    if required not in attribution:
        raise SystemExit(f"Attribution file is missing {required!r}")

meshes = doc.get("meshes", [])
accessors = doc.get("accessors", [])
views = doc.get("bufferViews", [])
nodes = doc.get("nodes", [])
if len(meshes) != 17 or len(nodes) != 27:
    raise SystemExit(
        f"Unexpected replacement dirt-bike structure: {len(meshes)} meshes, {len(nodes)} nodes"
    )


def mat_identity():
    return [
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0,
    ]


def mat_mul(a, b):
    out = [0.0] * 16
    for col in range(4):
        for row in range(4):
            out[col * 4 + row] = sum(
                a[k * 4 + row] * b[col * 4 + k] for k in range(4)
            )
    return out


def quat_matrix(q):
    x, y, z, w = (float(v) for v in q)
    length = math.sqrt(x*x + y*y + z*z + w*w)
    if length <= 1e-12:
        x = y = z = 0.0
        w = 1.0
    else:
        x /= length; y /= length; z /= length; w /= length
    xx, yy, zz = x*x, y*y, z*z
    xy, xz, yz = x*y, x*z, y*z
    wx, wy, wz = w*x, w*y, w*z
    return [
        1 - 2*(yy + zz), 2*(xy + wz), 2*(xz - wy), 0.0,
        2*(xy - wz), 1 - 2*(xx + zz), 2*(yz + wx), 0.0,
        2*(xz + wy), 2*(yz - wx), 1 - 2*(xx + yy), 0.0,
        0.0, 0.0, 0.0, 1.0,
    ]


def node_local(node):
    if "matrix" in node:
        values = [float(v) for v in node["matrix"]]
        if len(values) != 16:
            raise SystemExit("Node matrix must have 16 values")
        return values

    t = [float(v) for v in node.get("translation", [0, 0, 0])]
    r = [float(v) for v in node.get("rotation", [0, 0, 0, 1])]
    s = [float(v) for v in node.get("scale", [1, 1, 1])]
    tm = mat_identity(); tm[12], tm[13], tm[14] = t
    rm = quat_matrix(r)
    sm = mat_identity(); sm[0], sm[5], sm[10] = s
    return mat_mul(mat_mul(tm, rm), sm)


def transform_point(m, p):
    x, y, z = p
    return (
        m[0]*x + m[4]*y + m[8]*z + m[12],
        m[1]*x + m[5]*y + m[9]*z + m[13],
        m[2]*x + m[6]*y + m[10]*z + m[14],
    )


parents = [None] * len(nodes)
for parent_index, node in enumerate(nodes):
    for child in node.get("children", []):
        if parents[child] is not None:
            raise SystemExit(f"Node {child} has multiple parents")
        parents[child] = parent_index

world = [None] * len(nodes)


def resolve_world(index, visiting=None):
    if world[index] is not None:
        return world[index]
    if visiting is None:
        visiting = set()
    if index in visiting:
        raise SystemExit("Cycle in glTF node hierarchy")
    visiting.add(index)
    local = node_local(nodes[index])
    parent = parents[index]
    world[index] = local if parent is None else mat_mul(resolve_world(parent, visiting), local)
    visiting.remove(index)
    return world[index]


for i in range(len(nodes)):
    resolve_world(i)


def read_positions(accessor_index):
    accessor = accessors[accessor_index]
    if accessor.get("componentType") != 5126 or accessor.get("type") != "VEC3":
        raise SystemExit(f"Accessor {accessor_index} is not FLOAT VEC3")
    view = views[accessor["bufferView"]]
    base = int(view.get("byteOffset", 0)) + int(accessor.get("byteOffset", 0))
    stride = int(view.get("byteStride", 12))
    return [
        struct.unpack_from("<fff", bin_chunk, base + i * stride)
        for i in range(int(accessor["count"]))
    ]


def mesh_bounds_by_name(wanted):
    points = []
    for node_index, node in enumerate(nodes):
        mesh_index = node.get("mesh")
        if mesh_index is None:
            continue
        mesh = meshes[mesh_index]
        if mesh.get("name") != wanted:
            continue
        for primitive in mesh.get("primitives", []):
            pos_index = primitive.get("attributes", {}).get("POSITION")
            if pos_index is None:
                continue
            points.extend(
                transform_point(world[node_index], p)
                for p in read_positions(pos_index)
            )
    if not points:
        raise SystemExit(f"Missing expected mesh {wanted}")
    lo = tuple(min(p[i] for p in points) for i in range(3))
    hi = tuple(max(p[i] for p in points) for i in range(3))
    center = tuple((lo[i] + hi[i]) * 0.5 for i in range(3))
    return {"min": lo, "max": hi, "center": center}


required_meshes = {
    "Cylinder.005_TYRE_0",
    "Cylinder.003_TYRE_0",
    "Cube_Material.001_0",
    "Cube_METAL_0",
    "Cube_wire.001_0",
}
mesh_names = {mesh.get("name") for mesh in meshes}
missing = sorted(required_meshes - mesh_names)
if missing:
    raise SystemExit(f"Replacement dirt bike is missing meshes: {missing}")

wheel_a = mesh_bounds_by_name("Cylinder.005_TYRE_0")
wheel_b = mesh_bounds_by_name("Cylinder.003_TYRE_0")
rear = wheel_a if wheel_a["center"][2] <= wheel_b["center"][2] else wheel_b
front = wheel_b if rear is wheel_a else wheel_a

wheelbase = math.dist(rear["center"], front["center"])
rear_radius = (
    (rear["max"][1] - rear["min"][1])
    + (rear["max"][2] - rear["min"][2])
) * 0.25
front_radius = (
    (front["max"][1] - front["min"][1])
    + (front["max"][2] - front["min"][2])
) * 0.25
ground_delta = abs(rear["min"][1] - front["min"][1])

if not 1.35 <= wheelbase <= 1.45:
    raise SystemExit(f"Unexpected wheelbase: {wheelbase:.6f} m")
if not 0.32 <= rear_radius <= 0.35 or not 0.32 <= front_radius <= 0.35:
    raise SystemExit(
        f"Unexpected tire radii: rear={rear_radius:.6f}, front={front_radius:.6f}"
    )
if abs(rear_radius - front_radius) > 0.005:
    raise SystemExit("Front/rear tire radii do not match")
if ground_delta > 0.015:
    raise SystemExit(f"Wheel contact planes differ by {ground_delta:.6f} m")

triangle_total = 0
vertex_total = 0
for mesh in meshes:
    for primitive in mesh.get("primitives", []):
        attrs = primitive.get("attributes", {})
        if "POSITION" not in attrs or "indices" not in primitive:
            raise SystemExit(f"{mesh.get('name')}: missing POSITION/indices")
        pa = accessors[attrs["POSITION"]]
        ia = accessors[primitive["indices"]]
        vertex_total += int(pa.get("count", 0))
        triangle_total += int(ia.get("count", 0)) // 3

if triangle_total > 25000:
    raise SystemExit(f"Replacement dirt bike unexpectedly heavy: {triangle_total} triangles")

print(
    "Replacement DirtBike GLB OK: "
    f"{len(blob)} bytes, {len(nodes)} nodes, {len(meshes)} meshes, "
    f"{vertex_total} vertices, {triangle_total} triangles, "
    f"wheelbase={wheelbase:.6f} m, tire radius≈{rear_radius:.6f} m, "
    f"contact delta={ground_delta:.6f} m"
)
