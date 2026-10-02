from __future__ import annotations

import io
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "terrain" / "materials"
TARGET_SIZE = 768

MATERIALS = {
    "sparse_grass_diff.jpg": (
        "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/"
        "sparse_grass/sparse_grass_diff_1k.jpg"
    ),
    "dirt_diff.jpg": (
        "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/"
        "dirt/dirt_diff_1k.jpg"
    ),
    "rocks_ground_05_diff.jpg": (
        "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/"
        "rocks_ground_05/rocks_ground_05_diff_1k.jpg"
    ),
    "dirt_nor_gl.jpg": (
        "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/"
        "dirt/dirt_nor_gl_1k.jpg"
    ),
}


def download(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "BalancePoint-build/1.0 (+https://github.com/NyerahMT/Balance-Point)"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if len(data) < 32_000:
        raise SystemExit(f"Terrain material download unexpectedly small: {url} ({len(data)} bytes)")
    return data


def stage_one(filename: str, url: str) -> None:
    data = download(url)
    with Image.open(io.BytesIO(data)) as image:
        source = image.convert("RGB")
        if min(source.size) < TARGET_SIZE:
            raise SystemExit(
                f"Terrain source {filename} is only {source.size}; expected at least {TARGET_SIZE}px"
            )
        source = source.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
        OUTPUT.mkdir(parents=True, exist_ok=True)
        quality = 95 if "nor_gl" in filename else 91
        source.save(
            OUTPUT / filename,
            "JPEG",
            quality=quality,
            subsampling=0,
            optimize=True,
            progressive=True,
        )
    print(f"Staged scanned terrain material: {filename}")


def stage_terrain_materials() -> None:
    for filename, url in MATERIALS.items():
        stage_one(filename, url)


if __name__ == "__main__":
    stage_terrain_materials()
