#!/usr/bin/env python3
"""Reproducible first-plant hardpack tire/contact parameter pack for Physics V2.

Research/calibration support only. This does not change the legacy runtime.
The model is intentionally compact but coherent: one normal-load law, pure-slip
longitudinal/lateral laws, one combined-slip constraint, contact moments, and
first-order force relaxation. Engineering estimates remain centralized,
uncertainty-bounded, telemetry-visible, and replaceable.
"""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path

REFERENCE_PRESSURE_KPA = 100.0  # Honda nominal ~15 psi; source evidence spans 80-100 kPa.
STATIC_FRONT_LOAD_N = 539.67
STATIC_REAR_LOAD_N = 568.48

TIRES = {
    "front": {
        "identity": "Dunlop Geomax MX33 80/100-21 tube type",
        "unloaded_radius_m": 0.3538,
        "unloaded_radius_interval_m": [0.3526, 0.3550],
        "k_eff_at_960n_npm": 105000.0,
        "mu_x_ref": 0.95,
        "mu_y_ref": 0.90,
        "longitudinal_stiffness_per_fz": 11.0,
        "cornering_stiffness_per_fz_per_rad": 9.0,
        "camber_equivalent_slip_gain": 0.32,
        "radial_damping_ns_per_m": 550.0,
        "relaxation_x_m": 0.14,
        "relaxation_y_m": 0.20,
        "pneumatic_trail_m": 0.030,
        "overturning_coeff": 0.020,
    },
    "rear": {
        "identity": "Dunlop Geomax MX33 120/80-19 tube type",
        "unloaded_radius_m": 0.3373,
        "unloaded_radius_interval_m": [0.329, 0.345],
        "k_eff_at_960n_npm": 115000.0,
        "mu_x_ref": 1.05,
        "mu_y_ref": 0.95,
        "longitudinal_stiffness_per_fz": 10.0,
        "cornering_stiffness_per_fz_per_rad": 8.0,
        "camber_equivalent_slip_gain": 0.28,
        "radial_damping_ns_per_m": 650.0,
        "relaxation_x_m": 0.16,
        "relaxation_y_m": 0.24,
        "pneumatic_trail_m": 0.020,
        "overturning_coeff": 0.020,
    },
}

# Sumitomo motocross patent: d(Kvertical)/dF in first 0.96-1.92 kN region
# is preferably 50-100 N/mm per kN; examples use 75. The second region above
# 1.92 kN is preferably 30-80. We use the center-ish 75 / 55 slopes and a
# gentler low-load continuation. Units reduce to N/m stiffness gain per N load.
VERTICAL_STIFFNESS_SLOPE_LOW = 35.0
VERTICAL_STIFFNESS_SLOPE_MID = 75.0
VERTICAL_STIFFNESS_SLOPE_HIGH = 55.0
VERTICAL_FORCE_UNCERTAINTY = 0.35
RADIAL_DAMPING_UNCERTAINTY = 0.60

# Hardpack effective friction is an engineering envelope, not an MX33 bench
# measurement. Load sensitivity follows the normal pneumatic-tire trend that
# effective mu falls mildly as Fz increases.
MU_REFERENCE_LOAD_N = 1000.0
MU_LOAD_EXPONENT = 0.08
MU_UNCERTAINTY = 0.22
STIFFNESS_UNCERTAINTY = 0.30
CAMBER_GAIN_UNCERTAINTY = 0.50
RELAXATION_UNCERTAINTY = 0.50


def effective_vertical_stiffness(load_n: float, base_at_960_npm: float) -> float:
    load_n = max(0.0, load_n)
    if load_n <= 960.0:
        return base_at_960_npm + VERTICAL_STIFFNESS_SLOPE_LOW * (load_n - 960.0)
    if load_n <= 1920.0:
        return base_at_960_npm + VERTICAL_STIFFNESS_SLOPE_MID * (load_n - 960.0)
    k_1920 = base_at_960_npm + VERTICAL_STIFFNESS_SLOPE_MID * 960.0
    return k_1920 + VERTICAL_STIFFNESS_SLOPE_HIGH * (load_n - 1920.0)


def deflection_from_load(load_n: float, tire: dict) -> float:
    if load_n <= 0.0:
        return 0.0
    return load_n / effective_vertical_stiffness(load_n, tire["k_eff_at_960n_npm"])


def load_sensitive_mu(mu_ref: float, fz: float) -> float:
    if fz <= 0.0:
        return 0.0
    ratio = max(0.20, fz / MU_REFERENCE_LOAD_N)
    return mu_ref * ratio ** (-MU_LOAD_EXPONENT)


def pure_forces(tire: dict, fz: float, kappa: float, alpha: float, gamma: float) -> tuple[float, float, float, float]:
    if fz <= 0.0:
        return 0.0, 0.0, 0.0, 0.0
    mux = load_sensitive_mu(tire["mu_x_ref"], fz)
    muy = load_sensitive_mu(tire["mu_y_ref"], fz)
    cx = tire["longitudinal_stiffness_per_fz"] * fz
    ca = tire["cornering_stiffness_per_fz_per_rad"] * fz
    alpha_eff = alpha + tire["camber_equivalent_slip_gain"] * gamma
    fx = mux * fz * math.tanh(cx * kappa / max(mux * fz, 1e-9))
    fy = muy * fz * math.tanh(ca * alpha_eff / max(muy * fz, 1e-9))
    return fx, fy, mux, muy


def combined_forces(tire: dict, fz: float, kappa: float, alpha: float, gamma: float) -> dict:
    fx, fy, mux, muy = pure_forces(tire, fz, kappa, alpha, gamma)
    if fz <= 0.0:
        return {"fx_n": 0.0, "fy_n": 0.0, "mz_nm": 0.0, "mx_nm": 0.0, "utilization": 0.0}
    nx = fx / max(mux * fz, 1e-9)
    ny = fy / max(muy * fz, 1e-9)
    utilization = math.sqrt(nx * nx + ny * ny)
    scale = 1.0 / max(1.0, utilization)
    fx *= scale
    fy *= scale
    utilization = min(1.0, utilization)

    lateral_util = min(1.0, abs(fy) / max(muy * fz, 1e-9))
    trail = tire["pneumatic_trail_m"] * max(0.0, 1.0 - lateral_util)
    mz = -trail * fy
    mx = -tire["overturning_coeff"] * fz * tire["unloaded_radius_m"] * math.sin(gamma)
    return {"fx_n": fx, "fy_n": fy, "mz_nm": mz, "mx_nm": mx, "utilization": utilization}


def vertical_table(tire: dict) -> list[dict]:
    rows = []
    for load_n in range(0, 3001, 100):
        d = deflection_from_load(float(load_n), tire)
        rows.append({
            "load_n": load_n,
            "deflection_m": d,
            "effective_stiffness_npm": effective_vertical_stiffness(float(load_n), tire["k_eff_at_960n_npm"]),
        })
    return rows


def force_surface_rows(name: str, tire: dict) -> list[dict]:
    rows = []
    for fz in (300.0, 600.0, 1000.0, 1500.0, 2200.0):
        for kappa in (-0.30, -0.20, -0.10, 0.0, 0.10, 0.20, 0.30):
            for alpha_deg in (-12.0, -6.0, 0.0, 6.0, 12.0):
                alpha = math.radians(alpha_deg)
                out = combined_forces(tire, fz, kappa, alpha, 0.0)
                rows.append({"tire": name, "fz_n": fz, "kappa": kappa, "alpha_deg": alpha_deg, **out})
    return rows


def validate_vertical(rows: list[dict]) -> None:
    previous_d = -1.0
    for row in rows:
        d = row["deflection_m"]
        if d + 1e-12 < previous_d:
            raise RuntimeError("vertical deflection table is not monotone")
        previous_d = d


def main() -> None:
    out_dir = Path("gate_d_estimate")
    out_dir.mkdir(exist_ok=True)

    result = {
        "classification": "ENGINEERING_ESTIMATE_WITH_UNCERTAINTY",
        "runtime_status": "AUTHORIZED_FOR_FIRST_PHYSICS_V2_PLANT_ONLY",
        "surface": "effective groomed/hardpack motocross baseline",
        "reference_pressure_kpa": REFERENCE_PRESSURE_KPA,
        "source_constraints": {
            "mx_test_sizes": ["80/100-21", "120/80-19"],
            "patent_test_pressure_kpa": 80,
            "patent_vertical_load_regions_n": [[960, 1920], [1920, 2880]],
            "patent_stiffness_growth_first_region_n_per_mm_per_kn": [50, 100],
            "patent_stiffness_growth_second_region_n_per_mm_per_kn": [30, 80],
            "motorcycle_relaxation_prior_m": "roughly 0.1-0.3 m; first-plant off-road values intentionally near upper half",
        },
        "uncertainty": {
            "vertical_force_fraction": VERTICAL_FORCE_UNCERTAINTY,
            "radial_damping_fraction": RADIAL_DAMPING_UNCERTAINTY,
            "mu_fraction": MU_UNCERTAINTY,
            "slip_stiffness_fraction": STIFFNESS_UNCERTAINTY,
            "camber_gain_fraction": CAMBER_GAIN_UNCERTAINTY,
            "relaxation_length_fraction": RELAXATION_UNCERTAINTY,
        },
        "tires": {},
        "architecture": {
            "vertical": "monotone table Fz(delta) generated from load-dependent effective stiffness; add c_z * delta_dot only in compression/contact",
            "pure_longitudinal": "Fx0 = mu_x(Fz)*Fz*tanh(Ck*kappa/(mu_x(Fz)*Fz))",
            "pure_lateral": "Fy0 = mu_y(Fz)*Fz*tanh(Ca*(alpha + Kgamma*gamma)/(mu_y(Fz)*Fz))",
            "combined_slip": "elliptic normalization of pure Fx/Fy so normalized resultant <= 1",
            "aligning_moment": "Mz = -pneumatic_trail(utilization)*Fy with trail fading to zero at saturation",
            "overturning_moment": "Mx = -Kmx*Fz*R*sin(gamma)",
            "transient": "first-order force states using relaxation length / max(|Vx|, Vmin)",
            "contact_release": "Fz,Fx,Fy,Mx,Mz -> 0 continuously when penetration/load -> 0; relaxation states reset/decay without impulse",
        },
        "rules": [
            "Front and rear tires remain separate parameter sets.",
            "No independent longitudinal and lateral grip clamps after combined-slip resolution.",
            "No force exists with Fz <= 0.",
            "Surface scaling, if added later, acts on physical contact parameters and is telemetry-visible.",
            "No tire coefficient may be tuned solely to make a wheelie, drift, or landing feel correct.",
        ],
    }

    all_surface_rows = []
    for name, tire in TIRES.items():
        vrows = vertical_table(tire)
        validate_vertical(vrows)
        static_load = STATIC_FRONT_LOAD_N if name == "front" else STATIC_REAR_LOAD_N
        static_deflection = deflection_from_load(static_load, tire)
        loaded_radius = tire["unloaded_radius_m"] - static_deflection
        effective_radius = tire["unloaded_radius_m"] - 0.65 * static_deflection
        result["tires"][name] = {
            **tire,
            "loaded_radius_at_bike_only_static_load_m": loaded_radius,
            "effective_rolling_radius_first_plant_m": effective_radius,
            "loaded_radius_uncertainty_m": 0.006,
            "effective_radius_uncertainty_m": 0.006,
            "mu_load_exponent": MU_LOAD_EXPONENT,
            "vertical_force_table": vrows,
        }
        with (out_dir / f"{name}_vertical.csv").open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=vrows[0].keys())
            writer.writeheader()
            writer.writerows(vrows)
        surface = force_surface_rows(name, tire)
        for row in surface:
            if row["utilization"] > 1.0 + 1e-9:
                raise RuntimeError("combined-slip utilization exceeded unity")
        all_surface_rows.extend(surface)

    with (out_dir / "hardpack_force_surface.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=all_surface_rows[0].keys())
        writer.writeheader()
        writer.writerows(all_surface_rows)

    with (out_dir / "gate_d_estimate.json").open("w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
        handle.write("\n")

    print(json.dumps({
        "classification": result["classification"],
        "reference_pressure_kpa": REFERENCE_PRESSURE_KPA,
        "front_loaded_radius_m": result["tires"]["front"]["loaded_radius_at_bike_only_static_load_m"],
        "rear_loaded_radius_m": result["tires"]["rear"]["loaded_radius_at_bike_only_static_load_m"],
        "front_effective_radius_m": result["tires"]["front"]["effective_rolling_radius_first_plant_m"],
        "rear_effective_radius_m": result["tires"]["rear"]["effective_rolling_radius_first_plant_m"],
        "front_mu_x_ref": TIRES["front"]["mu_x_ref"],
        "rear_mu_x_ref": TIRES["rear"]["mu_x_ref"],
        "front_mu_y_ref": TIRES["front"]["mu_y_ref"],
        "rear_mu_y_ref": TIRES["rear"]["mu_y_ref"],
        "surface_rows": len(all_surface_rows),
        "decision": "Gate D first-plant dataset complete; validate uncertainty endpoints in V2 solver",
    }, indent=2))


if __name__ == "__main__":
    main()
