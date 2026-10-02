from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

from stage_terrain_materials import stage_terrain_materials

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "branding" / "62C6ECA5-5CF8-4A35-8BCD-3BCFEF430967.png"
EXPECTED_GIT_BLOB_SHA1 = "48fe1ad7654f62d5c1117e8f9f4db0bc01de8dd2"
IOS_DATA = ROOT / "ios" / "data"
IOS_APPICON = IOS_DATA / "Media.xcassets" / "AppIcon.appiconset"

IOS_IMAGES = [
    ("Icon-20.png", 20, "ipad", "20x20", "1x"),
    ("Icon-20@2x.png", 40, "iphone", "20x20", "2x"),
    ("Icon-20@3x.png", 60, "iphone", "20x20", "3x"),
    ("Icon-29.png", 29, "ipad", "29x29", "1x"),
    ("Icon-29@2x.png", 58, "iphone", "29x29", "2x"),
    ("Icon-29@3x.png", 87, "iphone", "29x29", "3x"),
    ("Icon-40.png", 40, "ipad", "40x40", "1x"),
    ("Icon-40@2x.png", 80, "iphone", "40x40", "2x"),
    ("Icon-40@3x.png", 120, "iphone", "40x40", "3x"),
    ("Icon-60@2x.png", 120, "iphone", "60x60", "2x"),
    ("Icon-60@3x.png", 180, "iphone", "60x60", "3x"),
    ("Icon-76.png", 76, "ipad", "76x76", "1x"),
    ("Icon-76@2x.png", 152, "ipad", "76x76", "2x"),
    ("Icon-83.5@2x.png", 167, "ipad", "83.5x83.5", "2x"),
    ("Icon-1024.png", 1024, "ios-marketing", "1024x1024", "1x"),
]

IOS_EXTRA_ENTRIES = [
    ("Icon-20@2x.png", "ipad", "20x20", "2x"),
    ("Icon-29@2x.png", "ipad", "29x29", "2x"),
    ("Icon-40@2x.png", "ipad", "40x40", "2x"),
]

ANDROID_IMAGES = [
    ("mipmap-mdpi", 48),
    ("mipmap-hdpi", 72),
    ("mipmap-xhdpi", 96),
    ("mipmap-xxhdpi", 144),
    ("mipmap-xxxhdpi", 192),
]


def verify_source() -> None:
    data = SOURCE.read_bytes()
    header = f"blob {len(data)}\0".encode("ascii")
    digest = hashlib.sha1(header + data).hexdigest()
    if digest != EXPECTED_GIT_BLOB_SHA1:
        raise SystemExit(f"Unexpected app icon source Git blob SHA-1: {digest}")


def save_png(source: Image.Image, output: Path, pixels: int) -> None:
    resized = source.resize((pixels, pixels), Image.Resampling.LANCZOS).convert("RGB")
    output.parent.mkdir(parents=True, exist_ok=True)
    resized.save(output, "PNG", optimize=True)


def stage_ios(source: Image.Image) -> None:
    IOS_DATA.mkdir(parents=True, exist_ok=True)
    IOS_APPICON.mkdir(parents=True, exist_ok=True)
    images = []
    for filename, pixels, idiom, size, scale in IOS_IMAGES:
        save_png(source, IOS_DATA / filename, pixels)
        save_png(source, IOS_APPICON / filename, pixels)
        images.append({"filename": filename, "idiom": idiom, "scale": scale, "size": size})

    for filename, idiom, size, scale in IOS_EXTRA_ENTRIES:
        images.append({"filename": filename, "idiom": idiom, "scale": scale, "size": size})

    (IOS_APPICON / "Contents.json").write_text(
        json.dumps({"images": images, "info": {"author": "xcode", "version": 1}}, indent=2) + "\n",
        encoding="utf-8",
    )
    catalog = IOS_DATA / "Media.xcassets" / "Contents.json"
    catalog.parent.mkdir(parents=True, exist_ok=True)
    catalog.write_text(
        json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n",
        encoding="utf-8",
    )


def stage_android(source: Image.Image) -> None:
    res = ROOT / "app" / "src" / "main" / "res"
    for directory, pixels in ANDROID_IMAGES:
        save_png(source, res / directory / "ic_launcher.png", pixels)


def main() -> None:
    verify_source()
    with Image.open(SOURCE) as image:
        source = image.convert("RGB")
        stage_ios(source)
        stage_android(source)
    stage_terrain_materials()
    print(f"Staged Balance Point app icons from {SOURCE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
