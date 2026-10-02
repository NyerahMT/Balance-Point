from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "terrain" / "ground_detail.png"
SIZE = 512
TAU = math.tau


def clamp8(v: float) -> int:
    return max(0, min(255, round(v)))


def periodic_noise(x: int, y: int) -> float:
    u = TAU * x / SIZE
    v = TAU * y / SIZE
    value = 0.0
    value += math.sin(u * 3.0 + v * 2.0 + 0.7) * 0.22
    value += math.sin(u * 7.0 - v * 5.0 + 2.1) * 0.16
    value += math.cos(u * 13.0 + v * 11.0 + 1.4) * 0.10
    value += math.sin(u * 29.0 - v * 23.0 + 0.2) * 0.055
    value += math.cos(u * 61.0 + v * 47.0 + 2.8) * 0.028
    return value


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    rng = random.Random(4502026)
    image = Image.new("RGB", (SIZE, SIZE))
    px = image.load()

    # Neutral earth micro-detail. TerrainVisuals supplies the actual grass/dirt/rock color;
    # this texture only contributes grain, pebbles, tiny dark creases and highlight breakup.
    for y in range(SIZE):
        for x in range(SIZE):
            n = periodic_noise(x, y)
            grit = rng.random()
            micro = (grit - 0.5) * 16.0
            base = 222.0 + n * 42.0 + micro
            warm = 2.5 * math.sin(TAU * x / SIZE * 17.0 + 0.4)
            px[x, y] = (
                clamp8(base + warm),
                clamp8(base - 2.0),
                clamp8(base - 6.0 - warm * 0.35),
            )

    # Deterministic tiny aggregate/stone marks. Wrap coordinates so the texture remains seamless.
    for i in range(1800):
        cx = rng.randrange(SIZE)
        cy = rng.randrange(SIZE)
        radius = rng.choice((1, 1, 1, 2, 2, 3))
        dark = rng.random() < 0.72
        strength = rng.uniform(18.0, 46.0) * (-1.0 if dark else 1.0)
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                d = math.sqrt(dx * dx + dy * dy)
                if d > radius + 0.25:
                    continue
                x = (cx + dx) % SIZE
                y = (cy + dy) % SIZE
                falloff = 1.0 - d / (radius + 0.75)
                r, g, b = px[x, y]
                amount = strength * falloff
                px[x, y] = (
                    clamp8(r + amount),
                    clamp8(g + amount * 0.92),
                    clamp8(b + amount * 0.80),
                )

    image.save(OUT, "PNG", optimize=True)
    print(f"Ground detail texture staged: {OUT} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
