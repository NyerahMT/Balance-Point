from pathlib import Path
import json
import math
import struct

PATH = Path("app/src/main/assets/models/DirtBike.glb")
if not PATH.is_file():
    raise SystemExit("Missing app/src/main/assets/models/DirtBike.glb")

blob = PATH.read_bytes()
if len(blob) < 20:
    raise SystemExit(f"DirtBike.glb is too small: {len(blob)} bytes")

magic, version, declared_length = struct.unpack_from("<4sII", blob, 0)
if magic != b"glTF":
    raise SystemExit(f"Bad GLB magic: {magic!r}")
if version != 2:
    raise SystemExit(f"Expected GLB version 2, got {version}")
if declared_length != len(blob):
    raise SystemExit(
        f"Truncated GLB: header declares {declared_length} bytes, file has {len(blob)}"
    )

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
        raise SystemExit(
            f"GLB chunk overruns file: {chunk_length} bytes at offset {offset}"
        )
    chunk = blob[offset:end]
    offset = end
    if chunk_type == JSON_CHUNK:
        json_chunk = chunk
    elif chunk_type == BIN_CHUNK:
        bin_chunk = chunk

if offset != len(blob):
    raise SystemExit(f"Unexpected trailing GLB bytes: {len(blob) - offset}")
if json_chunk is None or bin_chunk is None:
    raise SystemExit("DirtBike.glb must contain both JSON and BIN chunks")

try:
    doc = json.loads(json_chunk.decode("utf-8").rstrip(" \t\r\n\x00"))
except Exception as exc:
    raise SystemExit(f"Invalid GLB JSON chunk: {exc}") from exc

if doc.get("asset", {}).get("version") != "2.0":
    raise SystemExit(f"Unexpected glTF asset version: {doc.get('asset')}")

meshes = doc.get("meshes", [])
accessors = doc.get("accessors", [])
views = doc.get("bufferViews", [])
nodes = doc.get("nodes", [])
if not meshes or not accessors or not views or not nodes:
    raise SystemExit("DirtBike.glb is missing meshes/accessors/bufferViews/nodes")

mesh_names = {mesh.get("name") for mesh in meshes}
required_meshes = {
    "Body_GEO",
    "Engine_GEO",
    "FrontWheel_GEO",
    "RearWheel_GEO",
}
missing = sorted(required_meshes - mesh_names)
if missing:
    raise SystemExit(f"DirtBike.glb missing required meshes: {missing}")

expected_extras = doc.get("scenes", [{}])[0].get("extras", {})
wheelbase = expected_extras.get("wheelbase_m")
if wheelbase is None or abs(float(wheelbase) - 1.45) > 0.001:
    raise SystemExit(f"Unexpected DirtBike wheelbase metadata: {wheelbase}")

vertex_total = 0
triangle_total = 0
for mesh in meshes:
    name = mesh.get("name", "<unnamed>")
    primitives = mesh.get("primitives", [])
    if len(primitives) != 1:
        raise SystemExit(f"{name}: expected exactly one primitive")
    primitive = primitives[0]
    if primitive.get("mode", 4) != 4:
        raise SystemExit(f"{name}: expected TRIANGLES primitive")

    attrs = primitive.get("attributes", {})
    if "POSITION" not in attrs or "indices" not in primitive:
        raise SystemExit(f"{name}: missing POSITION or indices")

    pos = accessors[attrs["POSITION"]]
    ind = accessors[primitive["indices"]]
    if pos.get("componentType") != 5126 or pos.get("type") != "VEC3":
        raise SystemExit(f"{name}: POSITION must be FLOAT VEC3")
    if ind.get("type") != "SCALAR" or ind.get("componentType") not in (5121, 5123, 5125):
        raise SystemExit(f"{name}: unsupported index accessor")
    if ind.get("count", 0) % 3:
        raise SystemExit(f"{name}: index count is not divisible by 3")

    vertex_total += int(pos.get("count", 0))
    triangle_total += int(ind.get("count", 0)) // 3

    if "COLOR_0" in attrs:
        color = accessors[attrs["COLOR_0"]]
        if color.get("type") not in ("VEC3", "VEC4"):
            raise SystemExit(f"{name}: unsupported COLOR_0 type")


def mat_identity():
    return [
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0,
    ]


def mat_mul(a, b):
    # glTF/OpenGL column-major: result = a * b.
    out = [0.0] * 16
    for col in range(4):
        for row in range(4):
            out[col * 4 + row] = sum(
                a[k * 4 + row] * b[col * 4 + k] for k in range(4)
            )
    return out


def quat_matrix(q):
    x, y, z, w = q
    length = math.sqrt(x*x + y*y + z*z + w*w)
    if length <= 1e-12:
        x = y = z = 0.0
        w = 1.0
    else:
        x /= length
        y /= length
        z /= length
        w /= length
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

    tm = mat_identity()
    tm[12], tm[13], tm[14] = t
    rm = quat_matrix(r)
    sm = mat_identity()
    sm[0], sm[5], sm[10] = s
    return mat_mul(mat_mul(tm, rm), sm)


def transform_point(m, p):
    x, y, z = p
    return (
        m[0]*x + m[4]*y + m[8]*z + m[12],
        m[1]*x + m[5]*y + m[9]*z + m[13],
        m[2]*x + m[6]*y + m[10]*z + m[14],
    )


world = [None] * len(nodes)
parents = [None] * len(nodes)
for parent_i, node in enumerate(nodes):
    for child_i in node.get("children", []):
        if child_i < 0 or child_i >= len(nodes):
            raise SystemExit(f"Invalid child node index {child_i}")
        if parents[child_i] is not None:
            raise SystemExit(f"Node {child_i} has multiple parents")
        parents[child_i] = parent_i


def resolve_world(i, visiting=None):
    if world[i] is not None:
        return world[i]
    if visiting is None:
        visiting = set()
    if i in visiting:
        raise SystemExit("Cycle in glTF node hierarchy")
    visiting.add(i)
    local = node_local(nodes[i])
    parent_i = parents[i]
    world[i] = local if parent_i is None else mat_mul(resolve_world(parent_i, visiting), local)
    visiting.remove(i)
    return world[i]


for i in range(len(nodes)):
    resolve_world(i)


def read_positions(accessor_index):
    accessor = accessors[accessor_index]
    if accessor.get("componentType") != 5126 or accessor.get("type") != "VEC3":
        raise SystemExit(f"Accessor {accessor_index} is not FLOAT VEC3")
    view = views[accessor["bufferView"]]
    base = int(view.get("byteOffset", 0)) + int(accessor.get("byteOffset", 0))
    stride = int(view.get("byteStride", 12))
    count = int(accessor["count"])
    pts = []
    for i in range(count):
        p = base + i * stride
        pts.append(struct.unpack_from("<fff", bin_chunk, p))
    return pts


def fmt3(v):
    return f"({v[0]: .6f}, {v[1]: .6f}, {v[2]: .6f})"


def mesh_world_bounds(mesh_index, matrix):
    xs, ys, zs = [], [], []
    for primitive in meshes[mesh_index].get("primitives", []):
        accessor_index = primitive.get("attributes", {}).get("POSITION")
        if accessor_index is None:
            continue
        for p in read_positions(accessor_index):
            x, y, z = transform_point(matrix, p)
            xs.append(x); ys.append(y); zs.append(z)
    if not xs:
        return None
    lo = (min(xs), min(ys), min(zs))
    hi = (max(xs), max(ys), max(zs))
    center = tuple((a + b) * 0.5 for a, b in zip(lo, hi))
    size = tuple(b - a for a, b in zip(lo, hi))
    return lo, hi, center, size


print(
    f"DirtBike GLB OK: {len(blob)} bytes, {len(meshes)} meshes, "
    f"{vertex_total} vertices, {triangle_total} triangles, wheelbase {wheelbase} m"
)
print("\n=== AUTHORITATIVE SCENE EXTRAS ===")
for key in sorted(expected_extras):
    print(f"{key}: {expected_extras[key]}")

print("\n=== EXACT NODE / MESH LAYOUT ===")
for i, node in enumerate(nodes):
    name = node.get("name", f"node-{i}")
    origin = transform_point(world[i], (0.0, 0.0, 0.0))
    parent_name = "<root>" if parents[i] is None else nodes[parents[i]].get("name", str(parents[i]))
    mesh_index = node.get("mesh")
    if mesh_index is None:
        print(f"NODE {i:02d} {name:24s} parent={parent_name:24s} origin={fmt3(origin)}")
        continue
    mesh_name = meshes[mesh_index].get("name", f"mesh-{mesh_index}")
    bounds = mesh_world_bounds(mesh_index, world[i])
    if bounds is None:
        print(f"NODE {i:02d} {name:24s} mesh={mesh_name:24s} origin={fmt3(origin)} NO_POSITIONS")
        continue
    lo, hi, center, size = bounds
    print(
        f"NODE {i:02d} {name:24s} mesh={mesh_name:24s} parent={parent_name:24s} "
        f"origin={fmt3(origin)} center={fmt3(center)} min={fmt3(lo)} max={fmt3(hi)} size={fmt3(size)}"
    )

rear = expected_extras.get("rear_axle_game")
front = expected_extras.get("front_axle_game")
if rear and front:
    delta = tuple(float(front[i]) - float(rear[i]) for i in range(3))
    print(f"\nAUTHORITATIVE AXLE DELTA: {fmt3(delta)}")
