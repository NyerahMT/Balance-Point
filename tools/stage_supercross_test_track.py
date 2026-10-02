from __future__ import annotations

import math
import struct
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HEIGHT_PATH = ROOT / "app" / "src" / "main" / "assets" / "terrain" / "world.bpheight"
ALBEDO_PATH = ROOT / "app" / "src" / "main" / "assets" / "terrain" / "world_albedo.png"

MAGIC = 0x42504854
HEADER_FORMAT = ">iiii5f"
HEADER_SIZE = struct.calcsize(HEADER_FORMAT)

TRACK_CENTER_X = 7.5
TRACK_HALF_FLAT = 1.55
TRACK_HALF_FADE = 3.05
TRACK_Z_START = 18.0
TRACK_Z_END = 270.0
TRACK_BASE_HEIGHT = 0.028  # cancels TerrainVisuals' -28 mm render/collision underlay


def clamp01(v: float) -> float:
    return max(0.0, min(1.0, v))


def smooth01(v: float) -> float:
    t = clamp01(v)
    return t * t * (3.0 - 2.0 * t)


def lane_blend(x: float) -> float:
    d = abs(x - TRACK_CENTER_X)
    if d <= TRACK_HALF_FLAT:
        return 1.0
    if d >= TRACK_HALF_FADE:
        return 0.0
    return 1.0 - smooth01((d - TRACK_HALF_FLAT) / (TRACK_HALF_FADE - TRACK_HALF_FLAT))


def z_blend(z: float) -> float:
    if z < TRACK_Z_START or z > TRACK_Z_END:
        return 0.0
    enter = smooth01((z - TRACK_Z_START) / 8.0)
    leave = smooth01((TRACK_Z_END - z) / 8.0)
    return min(enter, leave)


def smooth_ramp(z: float, start: float, end: float, height: float) -> float:
    if z < start or z > end:
        return 0.0
    return height * smooth01((z - start) / max(0.001, end - start))


def landing_mound(z: float, start: float, peak: float, end: float, height: float) -> float:
    if z < start or z > end:
        return 0.0
    if z <= peak:
        return height * smooth01((z - start) / max(0.001, peak - start))
    return height * (1.0 - smooth01((z - peak) / max(0.001, end - peak)))


def cosine_bump(z: float, center: float, half_width: float, height: float) -> float:
    t = abs(z - center) / max(0.001, half_width)
    if t >= 1.0:
        return 0.0
    return height * 0.5 * (1.0 + math.cos(math.pi * t))


def repeating_whoops(z: float, start: float, end: float, spacing: float, height: float) -> float:
    if z < start or z > end:
        return 0.0
    phase = ((z - start) % spacing) / spacing
    return height * max(0.0, math.sin(math.pi * phase)) ** 1.35


def feature_height(x: float, z: float) -> float:
    h = 0.0

    # 1) Low braking/chatter bumps: enough to exercise tire compliance without launching the bike.
    h += repeating_whoops(z, 30.0, 51.0, 3.0, 0.055)

    # 2) Small tabletop: first easy suspension/rebound and landing check.
    if 57.0 <= z <= 63.0:
        h += smooth_ramp(z, 57.0, 63.0, 0.52)
    elif 63.0 < z <= 68.0:
        h += 0.52
    elif 68.0 < z <= 75.0:
        h += 0.52 * (1.0 - smooth01((z - 68.0) / 7.0))

    # 3) Real whoop lane. At the 1.5 m terrain grid these produce alternating crest/trough load.
    h += repeating_whoops(z, 80.0, 110.0, 3.0, 0.235)

    # 4) Double: distinct launch face, gap, and rising landing face for casing tests.
    if 116.0 <= z <= 123.0:
        h += smooth_ramp(z, 116.0, 123.0, 0.98)
    elif 123.0 < z <= 124.5:
        h += 0.98 * (1.0 - smooth01((z - 123.0) / 1.5))
    h += landing_mound(z, 133.0, 138.0, 145.0, 0.86)

    # 5) Rhythm/triple: stronger takeoff, small intermediate knuckle, then a broad landing.
    if 149.0 <= z <= 156.0:
        h += smooth_ramp(z, 149.0, 156.0, 1.20)
    elif 156.0 < z <= 157.5:
        h += 1.20 * (1.0 - smooth01((z - 156.0) / 1.5))
    h += cosine_bump(z, 166.0, 3.0, 0.44)
    h += landing_mound(z, 176.0, 181.0, 189.0, 1.02)

    # 6) Deliberately awkward case jump: short gap and a tall, relatively sharp landing knuckle.
    if 194.0 <= z <= 201.0:
        h += smooth_ramp(z, 194.0, 201.0, 1.32)
    elif 201.0 < z <= 202.5:
        h += 1.32 * (1.0 - smooth01((z - 201.0) / 1.5))
    h += landing_mound(z, 208.0, 212.0, 219.0, 1.18)

    # 7) Uneven landing pad: mild longitudinal mound plus a cross-slope that asks the roll
    # dynamics and terrain-normal contact solver to work together.
    if 222.0 <= z <= 241.0:
        longitudinal = math.sin(math.pi * (z - 222.0) / 19.0)
        lateral = clamp01((x - (TRACK_CENTER_X - TRACK_HALF_FLAT)) / (TRACK_HALF_FLAT * 2.0))
        h += max(0.0, longitudinal) * 0.18
        h += (lateral - 0.5) * 0.20 * max(0.0, longitudinal)

    # 8) Bank/berm slice. It is still a straight test lane, but the right edge progressively
    # rises enough to exercise camber, terrain roll, and one-wheel loading before the strip ends.
    if 246.0 <= z <= 264.0:
        along = math.sin(math.pi * (z - 246.0) / 18.0)
        lateral = clamp01((x - (TRACK_CENTER_X - TRACK_HALF_FADE)) / (TRACK_HALF_FADE * 2.0))
        h += max(0.0, along) * (lateral ** 2.2) * 0.48

    return h


def stage_heightfield() -> tuple[float, float, float, float]:
    data = bytearray(HEIGHT_PATH.read_bytes())
    if len(data) < HEADER_SIZE:
        raise SystemExit("Terrain heightfield is truncated")

    magic, version, width, height, spacing, origin_x, origin_z, encoded_min, encoded_max = \
        struct.unpack_from(HEADER_FORMAT, data, 0)
    if magic != MAGIC or version != 1:
        raise SystemExit("Unsupported Balance Point terrain heightfield")
    expected = HEADER_SIZE + width * height * 2
    if len(data) != expected:
        raise SystemExit(f"Unexpected heightfield size {len(data)} != {expected}")

    range_h = encoded_max - encoded_min
    ix0 = max(0, int(math.floor((TRACK_CENTER_X - TRACK_HALF_FADE - origin_x) / spacing)) - 1)
    ix1 = min(width - 1, int(math.ceil((TRACK_CENTER_X + TRACK_HALF_FADE - origin_x) / spacing)) + 1)
    iz0 = max(0, int(math.floor((TRACK_Z_START - origin_z) / spacing)) - 1)
    iz1 = min(height - 1, int(math.ceil((TRACK_Z_END - origin_z) / spacing)) + 1)

    changed = 0
    max_added = 0.0
    for iz in range(iz0, iz1 + 1):
        z = origin_z + iz * spacing
        zb = z_blend(z)
        if zb <= 0.0:
            continue
        for ix in range(ix0, ix1 + 1):
            x = origin_x + ix * spacing
            blend = lane_blend(x) * zb
            if blend <= 0.0:
                continue

            offset = HEADER_SIZE + 2 * (iz * width + ix)
            q = struct.unpack_from(">H", data, offset)[0]
            original = encoded_min + (q / 65535.0) * range_h
            target = TRACK_BASE_HEIGHT + feature_height(x, z)
            value = original + (target - original) * blend
            q_new = round(clamp01((value - encoded_min) / range_h) * 65535.0)
            struct.pack_into(">H", data, offset, q_new)
            changed += 1
            max_added = max(max_added, value - original)

    HEIGHT_PATH.write_bytes(data)
    print(f"Supercross heightfield staged: {changed} vertices, max raise {max_added:.2f} m")
    return origin_x, origin_z, (width - 1) * spacing, (height - 1) * spacing


def stage_albedo(origin_x: float, origin_z: float, world_w: float, world_h: float) -> None:
    image = Image.open(ALBEDO_PATH).convert("RGB")
    pixels = image.load()
    tex_w, tex_h = image.size

    # Match TerrainBaker.writeAlbedo(): PNG row 0 is increasing-world-Z origin, no vertical flip.
    px0 = max(0, int((TRACK_CENTER_X - TRACK_HALF_FADE - origin_x) / world_w * (tex_w - 1)) - 2)
    px1 = min(tex_w - 1, int((TRACK_CENTER_X + TRACK_HALF_FADE - origin_x) / world_w * (tex_w - 1)) + 2)
    py0 = max(0, int((TRACK_Z_START - origin_z) / world_h * (tex_h - 1)) - 2)
    py1 = min(tex_h - 1, int((TRACK_Z_END - origin_z) / world_h * (tex_h - 1)) + 2)

    for py in range(py0, py1 + 1):
        z = origin_z + (py / max(1, tex_h - 1)) * world_h
        zb = z_blend(z)
        for px in range(px0, px1 + 1):
            x = origin_x + (px / max(1, tex_w - 1)) * world_w
            blend = lane_blend(x) * zb
            if blend <= 0.0:
                continue
            r, g, b = pixels[px, py]
            grit = 0.90 + 0.10 * math.sin(x * 4.7 + z * 1.9)
            dirt = (int(112 * grit), int(78 * grit), int(43 * grit))
            amount = 0.82 * blend
            pixels[px, py] = (
                round(r + (dirt[0] - r) * amount),
                round(g + (dirt[1] - g) * amount),
                round(b + (dirt[2] - b) * amount),
            )

    image.save(ALBEDO_PATH, "PNG", optimize=True)
    print("Supercross dirt albedo strip staged")


def main() -> None:
    geometry = stage_heightfield()
    stage_albedo(*geometry)


if __name__ == "__main__":
    main()
