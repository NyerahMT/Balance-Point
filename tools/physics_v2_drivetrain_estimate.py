#!/usr/bin/env python3
"""Reproducible first-plant drivetrain parameter pack for Physics V2.

This is research/calibration support only. It does not modify the current runtime.
Engineering estimates remain centralized, uncertainty-bounded, and replaceable.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

PRIMARY = 2.357
GEARS = [2.133, 1.706, 1.421, 1.211, 1.043]
FINAL = 49.0 / 13.0
IDLE_RPM = 2000.0

# Dirt Rider Dynojet rear-wheel-equivalent torque curve. Interior points are a
# bounded engineering reconstruction of the published chart; the 6900 rpm
# torque and 9600 rpm power anchors are measured/published.
REAR_EQ_TORQUE = [
    (2500, 30.0),
    (3000, 32.5),
    (4000, 37.0),
    (5000, 40.5),
    (6000, 43.5),
    (6900, 44.61),  # 32.9 lb-ft published peak
    (7500, 44.2),
    (8500, 42.5),
    (9000, 40.2),
    (9600, 37.904),  # 51.1 hp published power peak
    (10200, 33.0),
    (10800, 27.0),
    (11200, 20.0),
]

DRIVETRAIN_EFFICIENCY = 0.95
DRIVETRAIN_EFFICIENCY_INTERVAL = [0.93, 0.97]
ENGINE_INERTIA = 0.0065  # kg m^2, crank-referenced equivalent
ENGINE_INERTIA_INTERVAL = [0.00325, 0.00975]
CLUTCH_CAPACITY = 150.0  # Nm at clutch shaft, fully engaged
CLUTCH_CAPACITY_INTERVAL = [115.0, 190.0]
CLUTCH_SLIP_SCALE = 20.0  # rad/s in tanh slip law

# Closed-throttle crank loss/braking torque. This is intentionally a smooth,
# replaceable engineering estimate rather than a claimed Honda measurement.
ENGINE_BRAKE = [
    (2000, 2.5),
    (3000, 3.2),
    (4000, 4.1),
    (5000, 5.1),
    (6000, 6.2),
    (7000, 7.4),
    (8000, 8.6),
    (9000, 9.8),
    (10000, 11.0),
    (11000, 12.1),
    (11500, 12.7),
]


def kw_from_nm_rpm(torque_nm: float, rpm: float) -> float:
    return torque_nm * rpm * (2.0 * math.pi / 60.0) / 1000.0


def hp_from_kw(kw: float) -> float:
    return kw / 0.745699872


def main() -> None:
    overall = [PRIMARY * g * FINAL for g in GEARS]
    crank_curve = [(rpm, tq / DRIVETRAIN_EFFICIENCY) for rpm, tq in REAR_EQ_TORQUE]
    peak_tq = max(REAR_EQ_TORQUE, key=lambda p: p[1])
    power_rows = [(rpm, hp_from_kw(kw_from_nm_rpm(tq, rpm))) for rpm, tq in REAR_EQ_TORQUE]
    peak_power = max(power_rows, key=lambda p: p[1])
    peak_crank_tq = peak_tq[1] / DRIVETRAIN_EFFICIENCY
    required_clutch_tq = peak_crank_tq * PRIMARY

    result = {
        "classification": "ENGINEERING_ESTIMATE_WITH_UNCERTAINTY",
        "runtime_status": "AUTHORIZED_FOR_FIRST_PHYSICS_V2_PLANT_ONLY",
        "published": {
            "idle_rpm": {"nominal": IDLE_RPM, "tolerance_rpm": 100},
            "primary_ratio": PRIMARY,
            "gear_ratios": GEARS,
            "final_ratio": FINAL,
            "overall_ratios": overall,
            "clutch": "wet multiplate hydraulic, 8 plates, 6 springs",
            "measured_rear_equivalent_peak_torque_nm": peak_tq,
            "measured_rear_power_anchor_hp": [9600, 51.1],
        },
        "first_plant_estimates": {
            "drivetrain_efficiency": {
                "nominal": DRIVETRAIN_EFFICIENCY,
                "interval": DRIVETRAIN_EFFICIENCY_INTERVAL,
            },
            "engine_equivalent_inertia_kg_m2": {
                "nominal": ENGINE_INERTIA,
                "interval": ENGINE_INERTIA_INTERVAL,
            },
            "combustion_torque_curve_nm": crank_curve,
            "combustion_curve_shape_uncertainty_fraction": 0.06,
            "closed_throttle_loss_torque_nm": ENGINE_BRAKE,
            "closed_throttle_loss_uncertainty_fraction": 0.40,
            "clutch_capacity_nm_at_clutch_shaft": {
                "nominal": CLUTCH_CAPACITY,
                "interval": CLUTCH_CAPACITY_INTERVAL,
                "minimum_peak_required_from_nominal_engine_map": required_clutch_tq,
            },
            "clutch_slip_law": {
                "form": "T = engagement * Tcap * tanh(delta_omega / omega_scale)",
                "omega_scale_rad_s": CLUTCH_SLIP_SCALE,
                "omega_scale_interval_rad_s": [10.0, 35.0],
            },
            "limiter_rpm": {"nominal": 11500, "interval": [11000, 12000]},
        },
        "sanity": {
            "reconstructed_peak_power_hp_from_table": peak_power,
            "peak_crank_torque_nm_nominal_efficiency": peak_crank_tq,
            "clutch_capacity_margin_over_peak_required": CLUTCH_CAPACITY / required_clutch_tq,
        },
        "rules": [
            "RPM is an integrated rotational state, never a target-RPM interpolation.",
            "Clutch torque is equal/opposite on engine and gearbox sides.",
            "Engine braking is a crank loss torque, not a direct chassis drag force.",
            "Shift torque interruption acts through clutch/combustion states, not wheel-force scaling.",
            "All engineering estimates remain centralized and replaceable.",
        ],
    }

    out = Path("physics_v2_drivetrain_estimate.json")
    out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
