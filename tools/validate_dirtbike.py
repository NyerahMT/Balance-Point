from pathlib import Path
import io
import struct
import zlib

PATH = Path("app/src/main/assets/models/dirtbike.bpq1z")
MAGIC = b"BPQ1"
EXPECTED = [(1396, 2714), (1190, 2308), (2118, 3676), (3041, 5508)]

compressed = PATH.read_bytes()
inflater = zlib.decompressobj()
raw = inflater.decompress(compressed) + inflater.flush()
if not inflater.eof:
    raise SystemExit("DirtBike zlib stream did not reach EOF")
if inflater.unused_data or inflater.unconsumed_tail:
    raise SystemExit("DirtBike zlib stream has unexpected extra data")

s = io.BytesIO(raw)
def exact(n, label):
    b = s.read(n)
    if len(b) != n:
        raise SystemExit(f"Unexpected EOF reading {label}: {len(b)}/{n}")
    return b

def varuint():
    value = 0
    shift = 0
    while shift < 35:
        b = exact(1, "varint")[0]
        value |= (b & 0x7f) << shift
        if (b & 0x80) == 0:
            return value
        shift += 7
    raise SystemExit("Malformed varint")

if exact(4, "magic") != MAGIC:
    raise SystemExit("Bad BPQ1 magic")
mesh_count = exact(1, "mesh count")[0]
if mesh_count != 4:
    raise SystemExit(f"Expected 4 meshes, got {mesh_count}")

summaries = []
for mesh_index, (expected_vertices, expected_tris) in enumerate(EXPECTED):
    vertex_count = struct.unpack(">H", exact(2, "vertex count"))[0]
    index_count = struct.unpack(">I", exact(4, "index count"))[0]
    bounds = struct.unpack(">6f", exact(24, "bounds"))
    if vertex_count != expected_vertices:
        raise SystemExit(f"Mesh {mesh_index} vertices {vertex_count} != {expected_vertices}")
    if index_count != expected_tris * 3:
        raise SystemExit(f"Mesh {mesh_index} triangles {index_count // 3} != {expected_tris}")
    exact(vertex_count * 6, "positions")
    exact(vertex_count * 2, "normals")
    previous = 0
    for i in range(index_count):
        packed = varuint()
        delta = (packed >> 1) ^ -(packed & 1)
        previous += delta
        if previous < 0 or previous >= vertex_count:
            raise SystemExit(f"Mesh {mesh_index} index {i} out of range: {previous}")
    summaries.append((vertex_count, index_count // 3, tuple(round(v, 4) for v in bounds)))

if s.read():
    raise SystemExit("Unexpected trailing BPQ1 bytes")

print(f"DirtBike payload OK: {len(compressed)} compressed bytes, {len(raw)} unpacked bytes")
for i, (verts, tris, bounds) in enumerate(summaries):
    print(f"Mesh {i}: {verts} vertices, {tris} triangles, bounds {bounds}")
