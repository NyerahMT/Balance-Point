#!/usr/bin/env python3
"""Reproducible Honda CR ELECTRIC / CRF450R Figure 9 CG extraction.

The source is Honda R&D Technical Review 2026 Vol. 38 pp. 23-30. Figure 9 is
an annotated CR ELECTRIC side-view image carrying both the CR ELECTRIC and
CRF450R CG markers. The visible machine is therefore scaled with the 1491 mm
CR ELECTRIC wheelbase published later in the same paper, not the parenthesized
1481 mm 2021 CRF450R wheelbase.

The output is a graphical prior only. It must never be relabeled as a measured
2025 CRF450R mass property.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import subprocess
from pathlib import Path

import cv2
import fitz  # PyMuPDF
import numpy as np
import requests

ARTICLE_URL = "https://www.jstage.jst.go.jp/article/hondatechnicalreview/38/0/38_2026_38_3/_article/-char/en"
PDF_URLS = [
    "https://www.jstage.jst.go.jp/article/hondatechnicalreview/38/0/38_2026_38_3/_pdf/-char/en",
    "https://www.jstage.jst.go.jp/article/hondatechnicalreview/38/0/38_2026_38_3/_supplement/_download/38_2026_38_3_1.pdf",
    "https://jstage.jst.go.jp/article/hondatechnicalreview/38/0/38_2026_38_3/_supplement/_download/38_2026_38_3_1.pdf",
]
PAGE_INDEX = 3  # printed page 26, Figure 9
CR_ELECTRIC_WHEELBASE_MM = 1491.0
CRF450R_2021_WHEELBASE_MM = 1481.0
RENDER_SCALE = 6.0  # 432 dpi

# Nominal unloaded tire radii from the published motocross sizes. These are not
# used to scale the figure; they are an independent sanity check on the image
# scale recovered from wheelbase.
FRONT_NOMINAL_RADIUS_MM = (21.0 * 25.4) / 2.0 + 80.0       # 80/100-21
REAR_NOMINAL_RADIUS_MM = (19.0 * 25.4) / 2.0 + 120.0 * .8 # 120/80-19


def rect_dict(r: fitz.Rect) -> dict[str, float]:
    return {"x0": r.x0, "y0": r.y0, "x1": r.x1, "y1": r.y1, "w": r.width, "h": r.height}


def connected_components(mask: np.ndarray, min_area: int = 8) -> list[dict[str, float]]:
    n, _labels, stats, centroids = cv2.connectedComponentsWithStats(mask, 8)
    out: list[dict[str, float]] = []
    for i in range(1, n):
        x, y, w, h, area = stats[i].tolist()
        if area < min_area:
            continue
        cx, cy = centroids[i].tolist()
        out.append(
            {
                "x": int(x), "y": int(y), "w": int(w), "h": int(h),
                "area": int(area), "cx": float(cx), "cy": float(cy),
                "fill": float(area) / float(max(1, w * h)),
                "aspect": float(w) / float(max(1, h)),
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
        gray, cv2.HOUGH_GRADIENT, dp=1.2,
        minDist=max(30, int(w * 0.28)), param1=120, param2=38,
        minRadius=max(8, int(h * 0.13)), maxRadius=max(16, int(h * 0.36)),
    )
    if circles is None:
        return []
    ans = []
    for x, y, r in np.round(circles[0]).astype(int):
        if y >= h * 0.35:
            ans.append({"cx": int(x), "cy": int(y), "r": int(r)})
    return sorted(ans, key=lambda c: c["cx"])


def download_pdf(out: Path) -> tuple[bytes, str]:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141 Safari/537.36",
        "Accept": "application/pdf,text/html;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": ARTICLE_URL,
        "Connection": "close",
    }
    failures: list[str] = []
    session = requests.Session()
    for url in PDF_URLS:
        try:
            r = session.get(url, timeout=45, headers=headers, allow_redirects=True)
            if r.ok and r.content.startswith(b"%PDF"):
                return r.content, url
            failures.append(f"requests {url}: status={r.status_code} bytes={len(r.content)} type={r.headers.get('content-type')}")
        except Exception as exc:
            failures.append(f"requests {url}: {exc!r}")

    curl_path = out / "curl_source.pdf"
    for url in PDF_URLS:
        cmd = [
            "curl", "--http1.1", "-fL", "--retry", "3", "--retry-delay", "1", "--retry-all-errors",
            "-A", headers["User-Agent"], "-e", ARTICLE_URL,
            "-H", "Accept: application/pdf,text/html;q=0.9,*/*;q=0.8",
            "-o", str(curl_path), url,
        ]
        try:
            cp = subprocess.run(cmd, check=False, text=True, capture_output=True, timeout=90)
            if curl_path.exists():
                data = curl_path.read_bytes()
                if cp.returncode == 0 and data.startswith(b"%PDF"):
                    return data, url
            failures.append(f"curl {url}: rc={cp.returncode} stderr={cp.stderr[-300:]}")
        except Exception as exc:
            failures.append(f"curl {url}: {exc!r}")
    raise RuntimeError("Could not retrieve Honda PDF:\n" + "\n".join(failures))


def choose_real_wheels(circles: list[dict[str, float]], image_h: int) -> tuple[dict[str, float], dict[str, float]]:
    plausible = [c for c in circles if 80 <= c["r"] <= 150 and c["cy"] >= image_h * 0.55]
    if len(plausible) < 2:
        raise RuntimeError(f"Could not isolate two real wheel circles from {circles}")
    plausible.sort(key=lambda c: c["cx"])
    return plausible[0], plausible[-1]


def choose_marker(components: list[dict[str, float]], rear: dict[str, float], front: dict[str, float], image_h: int) -> dict[str, float]:
    span = front["cx"] - rear["cx"]
    lo_x = rear["cx"] + 0.25 * span
    hi_x = rear["cx"] + 0.65 * span
    candidates = [
        c for c in components
        if lo_x <= c["cx"] <= hi_x
        and image_h * 0.35 <= c["cy"] <= min(rear["cy"], front["cy"])
        and 15 <= c["w"] <= 40 and 15 <= c["h"] <= 40
        and c["area"] >= 200 and c["fill"] >= 0.5
    ]
    if not candidates:
        raise RuntimeError(f"Could not isolate marker from components={components[:20]}")
    return max(candidates, key=lambda c: c["area"])


def tire_contact_y(gray: np.ndarray, wheel: dict[str, float]) -> int:
    h, w = gray.shape
    cx, cy, r = int(wheel["cx"]), int(wheel["cy"]), int(wheel["r"])
    half_width = max(70, int(r * 1.45))
    x0, x1 = max(0, cx - half_width), min(w, cx + half_width)
    y0, y1 = cy, min(h, cy + max(190, int(r * 1.7)))
    region = gray[y0:y1, x0:x1]
    dark_counts = (region < 80).sum(axis=1)
    threshold = max(12, int(r * 0.22))
    rows = np.where(dark_counts >= threshold)[0]
    if not len(rows):
        raise RuntimeError(f"No tire-contact row found for wheel={wheel}")
    return int(y0 + rows[-1])


def graphical_coordinates(marker: dict[str, float], rear: dict[str, float], front: dict[str, float], ground_y: float) -> dict[str, float]:
    rear_v = np.array([float(rear["cx"]), float(rear["cy"])])
    front_v = np.array([float(front["cx"]), float(front["cy"])])
    p = np.array([float(marker["cx"]), float(marker["cy"])])
    axis = front_v - rear_v
    wheelbase_px = float(np.linalg.norm(axis))
    mm_per_px = CR_ELECTRIC_WHEELBASE_MM / wheelbase_px
    frac = float(np.dot(p - rear_v, axis) / np.dot(axis, axis))
    return {
        "wheelbase_px": wheelbase_px,
        "mm_per_px": mm_per_px,
        "x_fraction_of_visible_wheelbase": frac,
        "x_from_rear_contact_mm": frac * CR_ELECTRIC_WHEELBASE_MM,
        "z_above_ground_mm": (float(ground_y) - float(marker["cy"])) * mm_per_px,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default="cg_extract")
    args = ap.parse_args()
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)

    pdf_bytes, resolved_url = download_pdf(out)
    (out / "source.pdf").write_bytes(pdf_bytes)
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    page = doc[PAGE_INDEX]
    page_rect = page.rect

    image_placements = []
    for item in page.get_images(full=True):
        xref = int(item[0])
        width, height = int(item[2]), int(item[3])
        for rect in page.get_image_rects(xref):
            image_placements.append({"xref": xref, "pixel_width": width, "pixel_height": height, "rect": rect_dict(rect)})

    candidates = [
        p for p in image_placements
        if p["rect"]["x0"] > page_rect.width * 0.45
        and p["rect"]["y0"] > page_rect.height * 0.50
        and p["rect"]["w"] > page_rect.width * 0.12
        and p["rect"]["h"] > page_rect.height * 0.07
    ]
    if not candidates:
        raise RuntimeError(f"Could not locate Figure 9 image; placements={image_placements}")
    candidate = max(candidates, key=lambda p: p["rect"]["w"] * p["rect"]["h"])
    ir = candidate["rect"]
    image_rect = fitz.Rect(ir["x0"], ir["y0"], ir["x1"], ir["y1"])
    crop_rect = fitz.Rect(
        max(page_rect.x0, image_rect.x0 - image_rect.width * 0.05),
        max(page_rect.y0, image_rect.y0 - image_rect.height * 0.08),
        min(page_rect.x1, image_rect.x1 + image_rect.width * 0.05),
        min(page_rect.y1, image_rect.y1 + image_rect.height * 0.08),
    )

    pix = page.get_pixmap(matrix=fitz.Matrix(RENDER_SCALE, RENDER_SCALE), clip=crop_rect, alpha=False)
    render_path = out / "figure9_render.png"
    pix.save(render_path)
    bgr = cv2.imread(str(render_path), cv2.IMREAD_COLOR)
    if bgr is None:
        raise RuntimeError("OpenCV could not read rendered Figure 9 crop")
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    red_mask, green_mask = hsv_masks(bgr)
    cv2.imwrite(str(out / "figure9_red_mask.png"), red_mask)
    cv2.imwrite(str(out / "figure9_green_mask.png"), green_mask)
    red_components = connected_components(red_mask)
    green_components = connected_components(green_mask)
    wheels = hough_wheels(bgr)
    rear, front = choose_real_wheels(wheels, bgr.shape[0])
    crf_marker = choose_marker(red_components, rear, front, bgr.shape[0])
    electric_marker = choose_marker(green_components, rear, front, bgr.shape[0])

    rear_ground_y = tire_contact_y(gray, rear)
    front_ground_y = tire_contact_y(gray, front)
    ground_y = 0.5 * (rear_ground_y + front_ground_y)

    crf_coord = graphical_coordinates(crf_marker, rear, front, ground_y)
    electric_coord = graphical_coordinates(electric_marker, rear, front, ground_y)
    scale = crf_coord["mm_per_px"]
    observed_rear_radius = (rear_ground_y - rear["cy"]) * scale
    observed_front_radius = (front_ground_y - front["cy"]) * scale

    overlay = bgr.copy()
    cv2.line(overlay, (rear["cx"], int(round(ground_y))), (front["cx"], int(round(ground_y))), (0, 0, 0), 2)
    for wheel in (rear, front):
        cv2.circle(overlay, (wheel["cx"], wheel["cy"]), 6, (255, 0, 255), -1)
    cv2.circle(overlay, (int(round(crf_marker["cx"])), int(round(crf_marker["cy"]))), 18, (255, 0, 255), 3)
    cv2.circle(overlay, (int(round(electric_marker["cx"])), int(round(electric_marker["cy"]))), 22, (255, 255, 0), 3)
    cv2.imwrite(str(out / "figure9_measurement_overlay.png"), overlay)

    raw = doc.extract_image(candidate["xref"])
    raw_path = out / f"figure9_xref_{candidate['xref']}.{raw['ext']}"
    raw_path.write_bytes(raw["image"])

    results = {
        "source": {
            "requested_urls": PDF_URLS,
            "resolved_url": resolved_url,
            "sha256": hashlib.sha256(pdf_bytes).hexdigest(),
            "page_index_zero_based": PAGE_INDEX,
            "page_number_printed": 26,
            "page_rect_points": rect_dict(page_rect),
        },
        "calibration": {
            "visible_vehicle": "CR ELECTRIC",
            "visible_vehicle_wheelbase_mm": CR_ELECTRIC_WHEELBASE_MM,
            "comparison_crf450r_2021_wheelbase_mm": CRF450R_2021_WHEELBASE_MM,
            "note": "Figure 9 is a CR ELECTRIC side view; 1491 mm is the image scale. The 1481 mm value identifies the comparison 2021 CRF450R but is not the visible-image wheelbase.",
        },
        "figure9_candidate": candidate,
        "figure9_crop_rect_points": rect_dict(crop_rect),
        "render": {"scale": RENDER_SCALE, "width_px": int(bgr.shape[1]), "height_px": int(bgr.shape[0])},
        "measurement": {
            "rear_wheel_center_px": [rear["cx"], rear["cy"]],
            "front_wheel_center_px": [front["cx"], front["cy"]],
            "rear_contact_y_px": rear_ground_y,
            "front_contact_y_px": front_ground_y,
            "ground_y_px": ground_y,
            "crf450r_marker_center_px": [crf_marker["cx"], crf_marker["cy"]],
            "cr_electric_marker_center_px": [electric_marker["cx"], electric_marker["cy"]],
            "wheelbase_px": crf_coord["wheelbase_px"],
            "mm_per_px": scale,
        },
        "scale_cross_check": {
            "observed_rear_tire_radius_mm": observed_rear_radius,
            "nominal_120_80_19_radius_mm": REAR_NOMINAL_RADIUS_MM,
            "rear_radius_error_mm": observed_rear_radius - REAR_NOMINAL_RADIUS_MM,
            "observed_front_tire_radius_mm": observed_front_radius,
            "nominal_80_100_21_radius_mm": FRONT_NOMINAL_RADIUS_MM,
            "front_radius_error_mm": observed_front_radius - FRONT_NOMINAL_RADIUS_MM,
        },
        "graphical_prior_2021_crf450r": {
            "x_from_rear_contact_m": crf_coord["x_from_rear_contact_mm"] / 1000.0,
            "z_above_ground_m": crf_coord["z_above_ground_mm"] / 1000.0,
            "extraction_uncertainty_m_conservative": 0.015,
            "classification": "GRAPHICAL_PRIOR_2021_HONDA",
            "runtime_2025_status": "NOT_A_RUNTIME_CONSTANT",
        },
        "cr_electric_marker_for_cross_check": {
            "x_from_rear_contact_m": electric_coord["x_from_rear_contact_mm"] / 1000.0,
            "z_above_ground_m": electric_coord["z_above_ground_mm"] / 1000.0,
        },
        "diagnostics": {
            "hough_wheels_all": wheels,
            "red_components_top": red_components[:20],
            "green_components_top": green_components[:20],
        },
    }

    (out / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
