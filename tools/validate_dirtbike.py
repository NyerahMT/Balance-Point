from pathlib import Path
import json
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
if not meshes or not accessors or not views:
    raise SystemExit("DirtBike.glb is missing meshes/accessors/bufferViews")

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

print(
    f"DirtBike GLB OK: {len(blob)} bytes, {len(meshes)} meshes, "
    f"{vertex_total} vertices, {triangle_total} triangles, wheelbase {wheelbase} m"
)
