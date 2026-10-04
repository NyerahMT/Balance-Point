#!/usr/bin/env python3
"""Reproducible Honda CR ELECTRIC / CRF450R Figure 9 CG extraction helper.

This script downloads Honda R&D Technical Review 2026 Vol. 38 pp. 23-30,
renders the page containing Figure 9 at high resolution, locates the placed
motorcycle image, and reports image/color/circle diagnostics used to extract a
manufacturer-graphical CRF450R CG prior.

It deliberately emits raw diagnostics before promoting any number into the
Physics V2 parameter pack.  The source image is not a metrology drawing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

import cv2
import fitz  # PyMuPDF
import numpy as np
import requests

PDF_URL = (
    "https://www.jstage.jst.go.jp/article/hondatechnicalreview/38/0/"
    "38_2026_38_3/_supplement/_download/38_2026_38_3_1.pdf"
)
PAGE_INDEX = 3  # PDF page 26, Figure 9
CR_ELECTRIC_WHEELBASE_MM = 1491.0
CRF450R_2021_WHEELBASE_MM = 1481.0
RENDER_SCALE = 6.0  # 432 dpi


def rect_dict(r: fitz.Rect) -> dict[str, float]:
    return {"x0": r.x0, "y0": r.y0, "x1": r.x1, "y1": r.y1, "w": r.width, "h": r.height}


def connected_components(mask: np.ndarray, min_area: int = 8) -> list[dict[str, float]]:
    n, labels, stats, centroids = cv2.connectedComponentsWithStats(mask, 8)
    out: list[dict[str, float]] = []
    for i in range(1, n):
        x, y, w, h, area = stats[i].tolist()
        if area < min_area:
            continue
        cx, cy = centroids[i].tolist()
        fill = float(area) / float(max(1, w * h))
        aspect = float(w) / float(max(1, h))
        out.append(
            {
                "x": int(x),
                "y": int(y),
                "w": int(w),
                "h": int(h),
                "area": int(area),
                "cx": float(cx),
                "cy": float(cy),
                "fill": fill,
                "aspect": aspect,
            }
        )
    return sorted(out, key=lambda c: c["area"], reverse=True)


def hsv_masks(bgr: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    red1 = cv2.inRange(hsv, np.array([0, 85, 65]), np.array([12, 255, 255]))
    red2 = cv2.inRange(hsv, np.array([168, 85, 65]), np.array([179, 255, 255]))
    red = cv2.bitwise_or(red1, red2)
    green = cv2.inRange(hsv, np.array([33, 55, 45]), np.array([95, 255, 255]))
    return red, green


def hough_wheels(bgr: np.ndarray) -> list[dict[str, float]]:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (9, 9), 1.8)
    h, w = gray.shape
    circles = cv2.HoughCircles(
        gray,
        cv2.HOUGH_GRADIENT,
        dp=1.2,
        minDist=max(30, int(w * 0.28)),
        param1=120,
        param2=38,
        minRadius=max(8, int(h * 0.13)),
        maxRadius=max(16, int(h * 0.36)),
    )
    if circles is None:
        return []
    ans = []
    for x, y, r in np.round(circles[0]).astype(int):
        # Wheels must live in the lower ~70% of the motorcycle image.
        if y < h * 0.35:
            continue
        ans.append({"cx": int(x), "cy": int(y), "r": int(r)})
    return sorted(ans, key=lambda c: c["cx"])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default="cg_extract")
    args = ap.parse_args()
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)

    response = requests.get(PDF_URL, timeout=60, headers={"User-Agent": "Mozilla/5.0 Balance-Point Physics V2 research"})
    response.raise_for_status()
    pdf_bytes = response.content
    (out / "source.pdf").write_bytes(pdf_bytes)

    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    page = doc[PAGE_INDEX]
    page_rect = page.rect

    image_placements = []
    for item in page.get_images(full=True):
        xref = int(item[0])
        width = int(item[2])
        height = int(item[3])
        for rect in page.get_image_rects(xref):
            image_placements.append(
                {
                    "xref": xref,
                    "pixel_width": width,
                    "pixel_height": height,
                    "rect": rect_dict(rect),
                }
            )

    # Figure 9 is the lower-right placed motorcycle image on page 26.
    candidates = [
        p
        for p in image_placements
        if p["rect"]["x0"] > page_rect.width * 0.45
        and p["rect"]["y0"] > page_rect.height * 0.50
        and p["rect"]["w"] > page_rect.width * 0.12
        and p["rect"]["h"] > page_rect.height * 0.07
    ]
    if not candidates:
        raise RuntimeError(f"Could not locate lower-right Figure 9 image; placements={image_placements}")
    candidate = max(candidates, key=lambda p: p["rect"]["w"] * p["rect"]["h"])
    ir = candidate["rect"]
    image_rect = fitz.Rect(ir["x0"], ir["y0"], ir["x1"], ir["y1"])

    # Render a modest expansion around the placed image so any vector annotations
    # immediately adjacent to the raster are retained.
    expand_x = image_rect.width * 0.05
    expand_y = image_rect.height * 0.08
    crop_rect = fitz.Rect(
        max(page_rect.x0, image_rect.x0 - expand_x),
        max(page_rect.y0, image_rect.y0 - expand_y),
        min(page_rect.x1, image_rect.x1 + expand_x),
        min(page_rect.y1, image_rect.y1 + expand_y),
    )
    pix = page.get_pixmap(matrix=fitz.Matrix(RENDER_SCALE, RENDER_SCALE), clip=crop_rect, alpha=False)
    render_path = out / "figure9_render.png"
    pix.save(render_path)
    bgr = cv2.imread(str(render_path), cv2.IMREAD_COLOR)
    if bgr is None:
        raise RuntimeError("OpenCV could not read rendered Figure 9 crop")

    red_mask, green_mask = hsv_masks(bgr)
    cv2.imwrite(str(out / "figure9_red_mask.png"), red_mask)
    cv2.imwrite(str(out / "figure9_green_mask.png"), green_mask)
    red_components = connected_components(red_mask)
    green_components = connected_components(green_mask)
    wheels = hough_wheels(bgr)

    # A second wheel pass on the placed raster alone helps reject surrounding labels.
    raw = doc.extract_image(candidate["xref"])
    raw_path = out / f"figure9_xref_{candidate['xref']}.{raw['ext']}"
    raw_path.write_bytes(raw["image"])
    raw_bgr = cv2.imdecode(np.frombuffer(raw["image"], dtype=np.uint8), cv2.IMREAD_COLOR)
    raw_wheels = hough_wheels(raw_bgr) if raw_bgr is not None else []

    results = {
        "source": {
            "url": PDF_URL,
            "sha256": hashlib.sha256(pdf_bytes).hexdigest(),
            "page_index_zero_based": PAGE_INDEX,
            "page_number_printed": 26,
            "page_rect_points": rect_dict(page_rect),
        },
        "calibration": {
            "visible_vehicle": "CR ELECTRIC",
            "visible_vehicle_wheelbase_mm": CR_ELECTRIC_WHEELBASE_MM,
            "comparison_crf450r_2021_wheelbase_mm": CRF450R_2021_WHEELBASE_MM,
            "note": "Figure 9 is a CR ELECTRIC side view; use 1491 mm for wheel-center scale, not the parenthesized 1481 mm CRF450R wheelbase.",
        },
        "image_placements": image_placements,
        "figure9_candidate": candidate,
        "figure9_crop_rect_points": rect_dict(crop_rect),
        "render": {"scale": RENDER_SCALE, "width_px": int(bgr.shape[1]), "height_px": int(bgr.shape[0])},
        "red_components": red_components[:80],
        "green_components": green_components[:80],
        "hough_wheels_render": wheels,
        "hough_wheels_raw": raw_wheels,
    }

    # If exactly two plausible raw-image wheel circles are found, emit a scale
    # candidate, but do not auto-promote a CG coordinate from color alone.
    if len(raw_wheels) >= 2:
        left = raw_wheels[0]
        right = raw_wheels[-1]
        dx = right["cx"] - left["cx"]
        dy = right["cy"] - left["cy"]
        wheelbase_px = math.hypot(dx, dy)
        results["raw_wheel_scale_candidate"] = {
            "rear_center_px": [left["cx"], left["cy"]],
            "front_center_px": [right["cx"], right["cy"]],
            "wheelbase_px": wheelbase_px,
            "mm_per_px_using_cr_electric_1491": CR_ELECTRIC_WHEELBASE_MM / wheelbase_px,
        }

    (out / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
