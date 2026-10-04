#!/usr/bin/env python3
"""Deterministic Physics V2 Gate-A center-of-gravity sensitivity sweep.

This is a research harness, not runtime physics. It uses one internally
consistent same-generation Honda 25YM configuration for longitudinal geometry:
1.483 m wheelbase and 48.7/51.3 percent front/rear static load split.

Only CG height is varied. The purpose is to determine whether the unresolved
late-2025 US CG-height uncertainty can honestly be classified as low
sensitivity before the Physics V2 coding gate opens.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

G = 9.80665
WHEELBASE_M = 1.483
FRONT_STATIC_FRACTION = 0.487
X_CG_FROM_REAR_M = WHEELBASE_M * FRONT_STATIC_FRACTION
HEIGHTS_M = (0.600, 0.630, 0.657, 0.690, 0.720)
TEST_ACCEL_G = (0.4, 0.6, 0.8, 1.0)
LOW_SENSITIVITY_LIMIT_PERCENT = 5.0


def front_normal_fraction(height_m: float, accel_g: float) -> float:
    """Rigid-body front normal load / weight during forward acceleration."""
    return FRONT_STATIC_FRACTION - accel_g * height_m / WHEELBASE_M


def rear_normal_fraction_braking(height_m: float, decel_g: float) -> float:
    """Rigid-body rear normal load / weight during straight-line braking."""
    rear_static = 1.0 - FRONT_STATIC_FRACTION
    return rear_static - decel_g * height_m / WHEELBASE_M


def wheelie_threshold_g(height_m: float) -> float:
    """Acceleration at which quasi-static front normal load reaches zero."""
    return X_CG_FROM_REAR_M / height_m


def rear_lift_threshold_g(height_m: float) -> float:
    """Braking deceleration at which quasi-static rear normal load is zero."""
    return (WHEELBASE_M - X_CG_FROM_REAR_M) / height_m


def percent_span(values: list[float], reference: float) -> float:
    return 100.0 * (max(values) - min(values)) / reference


def build_results() -> dict:
    rows = []
    for height_m in HEIGHTS_M:
        accel_loads = {
            f"front_fraction_at_{accel_g:.1f}g": front_normal_fraction(height_m, accel_g)
            for accel_g in TEST_ACCEL_G
        }
        brake_loads = {
            f"rear_fraction_at_{decel_g:.1f}g_braking": rear_normal_fraction_braking(height_m, decel_g)
            for decel_g in TEST_ACCEL_G
        }
        rows.append(
            {
                "cg_height_m": height_m,
                "wheelie_threshold_g": wheelie_threshold_g(height_m),
                "rear_lift_threshold_g": rear_lift_threshold_g(height_m),
                "longitudinal_force_pitch_arm_m": height_m,
                **accel_loads,
                **brake_loads,
            }
        )

    center = next(row for row in rows if row["cg_height_m"] == 0.657)
    wheelie_values = [row["wheelie_threshold_g"] for row in rows]
    rear_lift_values = [row["rear_lift_threshold_g"] for row in rows]
    arm_values = [row["longitudinal_force_pitch_arm_m"] for row in rows]

    wheelie_span = percent_span(wheelie_values, center["wheelie_threshold_g"])
    rear_lift_span = percent_span(rear_lift_values, center["rear_lift_threshold_g"])
    arm_span = percent_span(arm_values, center["longitudinal_force_pitch_arm_m"])
    controlling_span = max(wheelie_span, rear_lift_span, arm_span)

    return {
        "reference": {
            "configuration": "Honda Europe 25YM same-generation prior",
            "wheelbase_m": WHEELBASE_M,
            "front_static_fraction": FRONT_STATIC_FRACTION,
            "rear_static_fraction": 1.0 - FRONT_STATIC_FRACTION,
            "x_cg_from_rear_m": X_CG_FROM_REAR_M,
            "height_sweep_m": list(HEIGHTS_M),
            "runtime_status": "RESEARCH_ONLY_NOT_A_RUNTIME_CONSTANT",
        },
        "rows": rows,
        "sensitivity": {
            "wheelie_threshold_full_span_percent_of_0p657": wheelie_span,
            "rear_lift_threshold_full_span_percent_of_0p657": rear_lift_span,
            "longitudinal_pitch_arm_full_span_percent_of_0p657": arm_span,
            "controlling_full_span_percent": controlling_span,
            "low_sensitivity_limit_percent": LOW_SENSITIVITY_LIMIT_PERCENT,
            "cg_height_is_low_sensitivity": controlling_span <= LOW_SENSITIVITY_LIMIT_PERCENT,
        },
        "decision": {
            "classification": "HIGH_SENSITIVITY_UNRESOLVED"
            if controlling_span > LOW_SENSITIVITY_LIMIT_PERCENT
            else "BOUNDED_LOW_SENSITIVITY",
            "gate_a_effect": "CG height remains an identification/measurement blocker"
            if controlling_span > LOW_SENSITIVITY_LIMIT_PERCENT
            else "CG height may be bounded for first validation",
        },
    }


def write_csv(path: Path, results: dict) -> None:
    rows = results["rows"]
    fieldnames = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def validate(results: dict) -> None:
    rows = results["rows"]
    wheelie = [row["wheelie_threshold_g"] for row in rows]
    rear_lift = [row["rear_lift_threshold_g"] for row in rows]
    assert all(a > b for a, b in zip(wheelie, wheelie[1:])), wheelie
    assert all(a > b for a, b in zip(rear_lift, rear_lift[1:])), rear_lift
    assert abs(X_CG_FROM_REAR_M - 0.722221) < 1e-9
    assert results["decision"]["classification"] == "HIGH_SENSITIVITY_UNRESOLVED"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out-dir", default="gate_a_sensitivity")
    args = parser.parse_args()
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    results = build_results()
    validate(results)
    (out_dir / "gate_a_cg_sensitivity.json").write_text(
        json.dumps(results, indent=2) + "\n",
        encoding="utf-8",
    )
    write_csv(out_dir / "gate_a_cg_sensitivity.csv", results)
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
