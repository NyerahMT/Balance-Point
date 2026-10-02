from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "branding" / "balancepoint-icon.jpg"
EXPECTED_SHA256 = "ac1a0645bb2155675d2a0f127e31d867d71f04bcb96e04f8e1bc331ad3c94934"
IOS_CATALOG = ROOT / "ios" / "data" / "Media.xcassets"
IOS_APPICON = IOS_CATALOG / "AppIcon.appiconset"

IOS_IMAGES = [
    ("icon-20.png", 20, "ipad", "20x20", "1x"),
    ("icon-20@2x.png", 40, "iphone", "20x20", "2x"),
    ("icon-20@3x.png", 60, "iphone", "20x20", "3x"),
    ("icon-29.png", 29, "ipad", "29x29", "1x"),
    ("icon-29@2x.png", 58, "iphone", "29x29", "2x"),
    ("icon-29@3x.png", 87, "iphone", "29x29", "3x"),
    ("icon-40.png", 40, "ipad", "40x40", "1x"),
    ("icon-40@2x.png", 80, "iphone", "40x40", "2x"),
    ("icon-40@3x.png", 120, "iphone", "40x40", "3x"),
    ("icon-60@2x.png", 120, "iphone", "60x60", "2x"),
    ("icon-60@3x.png", 180, "iphone", "60x60", "3x"),
    ("icon-76.png", 76, "ipad", "76x76", "1x"),
    ("icon-76@2x.png", 152, "ipad", "76x76", "2x"),
    ("icon-83.5@2x.png", 167, "ipad", "83.5x83.5", "2x"),
    ("icon-1024.png", 1024, "ios-marketing", "1024x1024", "1x"),
]

IOS_EXTRA_ENTRIES = [
    ("icon-20@2x.png", "ipad", "20x20", "2x"),
    ("icon-29@2x.png", "ipad", "29x29", "2x"),
    ("icon-40@2x.png", "ipad", "40x40", "2x"),
]


def verify_source() -> None:
    digest = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"Unexpected app icon source SHA-256: {digest}")


def save_png(source: Image.Image, output: Path, pixels: int) -> None:
    resized = source.resize((pixels, pixels), Image.Resampling.LANCZOS).convert("RGB")
    output.parent.mkdir(parents=True, exist_ok=True)
    resized.save(output, "PNG", optimize=True)


def stage_ios(source: Image.Image) -> None:
    IOS_APPICON.mkdir(parents=True, exist_ok=True)
    images = []
    for filename, pixels, idiom, size, scale in IOS_IMAGES:
        save_png(source, IOS_APPICON / filename, pixels)
        images.append({
            "filename": filename,
            "idiom": idiom,
            "scale": scale,
            "size": size,
        })

    for filename, idiom, size, scale in IOS_EXTRA_ENTRIES:
        images.append({
            "filename": filename,
            "idiom": idiom,
            "scale": scale,
            "size": size,
        })

    (IOS_APPICON / "Contents.json").write_text(
        json.dumps({"images": images, "info": {"author": "xcode", "version": 1}}, indent=2)
        + "\n",
        encoding="utf-8",
    )
    IOS_CATALOG.mkdir(parents=True, exist_ok=True)
    (IOS_CATALOG / "Contents.json").write_text(
        json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    verify_source()
    with Image.open(SOURCE) as image:
        source = image.convert("RGB")
        stage_ios(source)
    print(f"Staged Balance Point app icons from {SOURCE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
