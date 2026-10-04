#!/usr/bin/env python3
"""Reproducible engineering mass-property estimate for Physics V2.

This is not a same-bike measurement. It transports dimensionless mass-property
information from published motorcycle models to the frozen 2025 CRF450R
reference and carries deliberately broad uncertainty bands.
"""

from __future__ import annotations

import json

CRF_MASS_KG = 112.94
CRF_WHEELBASE_M = 1.48082
CG_X_M = 0.722221
CG_Z_M = 0.657
CG_X_UNCERTAINTY_M = 0.015
CG_Z_UNCERTAINTY_M = 0.040

# Vasquez et al. / off-road motorcycle model body data reconstructed in the
# longitudinal plane. Body 7 is the rider and is intentionally excluded.
SOURCE_WHEELBASE_M = 1.35
SOURCE_BODIES = (
    # mass kg, x m, z m, local pitch inertia kg m^2
    (18.0, 0.000, -0.300, 0.00),
    (0.0, 0.279, -0.328, 0.28),
    (81.0, 0.768, -0.507, 23.00),
    (12.0, 1.350, -0.300, 0.00),
)


def reconstructed_source_pitch_inertia() -> tuple[float, float, float, float]:
    total_mass = sum(body[0] for body in SOURCE_BODIES)
    x_cg = sum(m * x for m, x, _, _ in SOURCE_BODIES) / total_mass
    z_cg = sum(m * z for m, _, z, _ in SOURCE_BODIES) / total_mass
    inertia = sum(
        local_i + m * ((x - x_cg) ** 2 + (z - z_cg) ** 2)
        for m, x, z, local_i in SOURCE_BODIES
    )
    return total_mass, x_cg, z_cg, inertia


def main() -> None:
    source_mass, source_x, source_z, source_iyy = reconstructed_source_pitch_inertia()
    k_pitch = source_iyy / (source_mass * SOURCE_WHEELBASE_M**2)
    crf_iyy = k_pitch * CRF_MASS_KG * CRF_WHEELBASE_M**2

    # Roll/yaw ratios are transported only as low-confidence shape priors from
    # a published Enduro motorcycle+rider model. They are intentionally given
    # wider bands than pitch inertia.
    roll_to_pitch = 33.4 / 64.9
    yaw_to_pitch = 39.4 / 64.9
    crf_ixx = crf_iyy * roll_to_pitch
    crf_izz = crf_iyy * yaw_to_pitch

    result = {
        "classification": "ENGINEERING_ESTIMATE_WITH_UNCERTAINTY",
        "runtime_replaceable": True,
        "cg": {
            "x_from_rear_contact_m": CG_X_M,
            "x_uncertainty_m": CG_X_UNCERTAINTY_M,
            "z_above_ground_m": CG_Z_M,
            "z_uncertainty_m": CG_Z_UNCERTAINTY_M,
        },
        "source_reconstruction": {
            "mass_kg": source_mass,
            "cg_x_m": source_x,
            "cg_z_m": source_z,
            "pitch_inertia_kg_m2": source_iyy,
            "wheelbase_m": SOURCE_WHEELBASE_M,
            "dimensionless_pitch_coefficient": k_pitch,
        },
        "crf450r_estimate": {
            "mass_kg": CRF_MASS_KG,
            "wheelbase_m": CRF_WHEELBASE_M,
            "Ixx_roll_kg_m2": crf_ixx,
            "Iyy_pitch_kg_m2": crf_iyy,
            "Izz_yaw_kg_m2": crf_izz,
            "Ixz_kg_m2": 0.0,
            "pitch_uncertainty_fraction": 0.25,
            "roll_uncertainty_fraction": 0.35,
            "yaw_uncertainty_fraction": 0.35,
        },
        "policy": {
            "not_factory_data": True,
            "do_not_tune_to_feel": True,
            "replace_when_better_data_arrives": True,
            "validate_across_uncertainty_envelope": True,
        },
    }
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
