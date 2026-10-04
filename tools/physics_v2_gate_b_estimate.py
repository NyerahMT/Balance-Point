#!/usr/bin/env python3

import csv
import json
import math
from pathlib import Path

REAR_WHEEL_TRAVEL_M = 0.30988
SHOCK_STROKE_M = 0.133
SHOCK_SPRING_NPM = 52000.0
FORK_SPRING_TOTAL_NPM = 10000.0

MR_START = 2.42
MR_70 = 2.34
AVG_MR = REAR_WHEEL_TRAVEL_M / SHOCK_STROKE_M
AREA_FIRST = 0.7 * (MR_START + MR_70) * 0.5
MR_END = ((AVG_MR - AREA_FIRST) / 0.15) - MR_70

CRIT_FRONT_NS_PER_M = 1723.0
CRIT_REAR_NS_PER_M = 1928.0
ZETA_NOMINAL = 0.45
ZETA_LOW = 0.35
ZETA_HIGH = 0.55
COMP_FACTOR = 0.80
REBOUND_FACTOR = 1.20

FRONT_ENDSTOP_START = 0.75
FRONT_ENDSTOP_FULL_N = 2500.0
REAR_BUMPSTOP_START = 0.82
REAR_BUMPSTOP_FULL_N = 4500.0


def motion_ratio(u: float) -> float:
    if u <= 0.7:
        t = u / 0.7
        return MR_START + (MR_70 - MR_START) * t
    t = (u - 0.7) / 0.3
    return MR_70 + (MR_END - MR_70) * t


def cubic_endstop(fraction: float, start: float, full_force: float) -> float:
    if fraction <= start:
        return 0.0
    x = (fraction - start) / (1.0 - start)
    return full_force * x * x * x


def main() -> None:
    out_dir = Path("gate_b_estimate")
    out_dir.mkdir(exist_ok=True)

    front_base = CRIT_FRONT_NS_PER_M * ZETA_NOMINAL
    rear_wheel_base = CRIT_REAR_NS_PER_M * ZETA_NOMINAL
    mr_mid = motion_ratio(0.5)
    rear_shock_base = rear_wheel_base * mr_mid * mr_mid

    rows = []
    wheel_travel = 0.0
    samples = 41
    previous_mr = motion_ratio(0.0)
    for i in range(samples):
        u = i / (samples - 1)
        mr = motion_ratio(u)
        if i:
            ds = SHOCK_STROKE_M / (samples - 1)
            wheel_travel += 0.5 * (previous_mr + mr) * ds
        wheel_rate = SHOCK_SPRING_NPM / (mr * mr)
        rows.append({
            "shock_fraction": u,
            "shock_travel_m": u * SHOCK_STROKE_M,
            "wheel_travel_m": wheel_travel,
            "motion_ratio_wheel_per_shock": mr,
            "rear_wheel_spring_rate_npm": wheel_rate,
            "rear_bumpstop_force_shock_n": cubic_endstop(
                u, REAR_BUMPSTOP_START, REAR_BUMPSTOP_FULL_N
            ),
        })
        previous_mr = mr

    result = {
        "classification": "ENGINEERING_ESTIMATE_WITH_UNCERTAINTY",
        "rear_linkage": {
            "average_motion_ratio": AVG_MR,
            "start_motion_ratio": MR_START,
            "motion_ratio_at_70pct_stroke": MR_70,
            "end_motion_ratio": MR_END,
            "wheel_rate_start_npm": SHOCK_SPRING_NPM / (MR_START * MR_START),
            "wheel_rate_end_npm": SHOCK_SPRING_NPM / (MR_END * MR_END),
            "nominal_motion_ratio_uncertainty": "+/-0.12 local",
        },
        "damping": {
            "literature_critical_front_ns_per_m": CRIT_FRONT_NS_PER_M,
            "literature_critical_rear_wheel_ns_per_m": CRIT_REAR_NS_PER_M,
            "racing_zeta_nominal": ZETA_NOMINAL,
            "racing_zeta_range": [ZETA_LOW, ZETA_HIGH],
            "front_equivalent_nominal_ns_per_m": front_base,
            "front_compression_nominal_ns_per_m": front_base * COMP_FACTOR,
            "front_rebound_nominal_ns_per_m": front_base * REBOUND_FACTOR,
            "rear_wheel_equivalent_nominal_ns_per_m": rear_wheel_base,
            "rear_reference_motion_ratio": mr_mid,
            "rear_shock_equivalent_nominal_ns_per_m": rear_shock_base,
            "rear_shock_compression_nominal_ns_per_m": rear_shock_base * COMP_FACTOR,
            "rear_shock_rebound_nominal_ns_per_m": rear_shock_base * REBOUND_FACTOR,
            "nominal_force_law_uncertainty": "+/-35%",
        },
        "springs_and_endstops": {
            "fork_total_coil_rate_npm": FORK_SPRING_TOTAL_NPM,
            "front_endstop_start_fraction": FRONT_ENDSTOP_START,
            "front_endstop_full_force_n": FRONT_ENDSTOP_FULL_N,
            "shock_spring_rate_npm": SHOCK_SPRING_NPM,
            "rear_bumpstop_start_fraction": REAR_BUMPSTOP_START,
            "rear_bumpstop_full_force_shock_n": REAR_BUMPSTOP_FULL_N,
            "endstop_force_uncertainty": "+/-50%",
        },
        "runtime_policy": (
            "Use as first-plant values only; keep linkage, damping, friction and end-stop "
            "parameters replaceable and telemetry-visible."
        ),
    }

    with (out_dir / "gate_b_estimate.json").open("w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
        handle.write("\n")

    with (out_dir / "rear_linkage_curve.csv").open(
        "w", newline="", encoding="utf-8"
    ) as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
