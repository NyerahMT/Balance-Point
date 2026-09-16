from pathlib import Path
import base64
import zlib

ROOT = Path("app/src/main/assets/models")
chunks = sorted(
    ROOT.glob("dirtbike_qmesh_*.txt"),
    key=lambda p: int(p.stem.rsplit("_", 1)[1]),
)
indices = [int(p.stem.rsplit("_", 1)[1]) for p in chunks]
if indices != list(range(len(chunks))):
    raise SystemExit(f"Non-contiguous DirtBike chunks: {indices}")
if not chunks:
    raise SystemExit("No DirtBike mesh chunks found")

parts = [p.read_text().strip() for p in chunks]
print("DirtBike chunk lengths:", [len(p) for p in parts])
print("DirtBike total Base64 chars:", sum(map(len, parts)))

encoded = "".join(parts)
encoded += "=" * ((-len(encoded)) % 4)
compressed = base64.b64decode(encoded, validate=True)
print("DirtBike compressed bytes:", len(compressed), "header:", compressed[:8].hex())

# Inflate in small pieces so a corrupt source chunk can be traced to an approximate
# compressed/Base64 offset instead of surfacing only as a generic zlib exception.
inflater = zlib.decompressobj()
raw_parts = []
step = 128
try:
    for offset in range(0, len(compressed), step):
        raw_parts.append(inflater.decompress(compressed[offset:offset + step]))
    raw_parts.append(inflater.flush())
except zlib.error as exc:
    base64_offset = (offset * 4) // 3
    cumulative = 0
    source_chunk = None
    source_offset = None
    for index, part in enumerate(parts):
        next_cumulative = cumulative + len(part)
        if base64_offset < next_cumulative:
            source_chunk = index
            source_offset = base64_offset - cumulative
            break
        cumulative = next_cumulative
    print(
        f"DirtBike zlib failure near compressed byte {offset}, "
        f"Base64 char {base64_offset}, source chunk {source_chunk} "
        f"around char {source_offset}: {exc}"
    )
    raise

raw = b"".join(raw_parts)
if raw[:4] != b"BPQ1":
    raise SystemExit(f"Bad DirtBike magic: {raw[:4]!r}")
if len(raw) < 5 or raw[4] != 4:
    got = raw[4] if len(raw) > 4 else "missing"
    raise SystemExit(f"Expected 4 DirtBike meshes, got {got}")

print(
    f"DirtBike payload OK: {len(chunks)} chunks, {len(compressed)} compressed bytes, "
    f"{len(raw)} unpacked bytes, 4 meshes"
)
