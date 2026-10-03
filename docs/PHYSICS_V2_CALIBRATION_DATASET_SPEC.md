# Physics V2 Calibration Dataset Specification

Status: **pre-runtime data contract.**

This file defines the raw-data artifacts needed to close the remaining Physics V2 coding gates. It intentionally stores observations separately from fitted runtime curves so future model changes do not destroy provenance.

All numeric columns use SI units internally. Any source recorded in another unit must preserve the original value/unit in metadata and store the converted SI value beside it.

---

## 1. Common metadata

Every dataset must include:

- `configuration_id` — exact motorcycle/tire/suspension configuration;
- `source_type` — PUBLISHED / MEASURED / DERIVED / IDENTIFIED;
- `source_reference` — document, URL, test ID, or file identifier;
- `date_utc`;
- `operator` or source author where applicable;
- `ambient_temp_c`;
- `component_temp_c` where relevant;
- `notes`;
- explicit uncertainty fields or repeatability statistics.

No fitted parameter may lose the raw test rows that produced it.

---

## 2. Mass-property dataset

### 2.1 Static axle-load / CG rows

```text
configuration_id,total_mass_kg,front_load_n,rear_load_n,wheelbase_m,tilt_angle_rad,front_height_m,rear_height_m,cg_x_m,cg_z_m,sigma_cg_x_m,sigma_cg_z_m,source_reference
```

`cg_x_m` is measured from the rear contact patch in the declared chassis reference frame.

### 2.2 Whole-bike inertia rows

```text
configuration_id,axis,test_method,period_s,support_geometry,known_mass_kg,known_geometry,inertia_kgm2,sigma_inertia_kgm2,source_reference
```

Axes must resolve at least roll, pitch and yaw about the chosen bike CG/reference state.

### 2.3 Component rotational inertia

```text
component_id,configuration_id,axis,test_method,period_or_alpha,known_torque_nm,inertia_kgm2,sigma_inertia_kgm2,source_reference
```

Use for front wheel, rear wheel, steering assembly and swingarm/linkage where measured.

---

## 3. Geometry / hardpoint dataset

```text
configuration_id,point_id,x_m,y_m,z_m,sigma_x_m,sigma_y_m,sigma_z_m,reference_frame,source_method,source_reference
```

Required point IDs include, at minimum:

- steering-axis upper/lower reference points;
- swingarm pivot;
- rear axle at reference ride height;
- front axle/fork-axis relation;
- upper and lower shock mounts;
- cushion-arm pivots;
- pullrod pivots;
- countershaft center;
- front/rear sprocket centers;
- footpeg reaction points;
- handlebar/steering-torque application point.

---

## 4. Pro-Link kinematics dataset

Raw observations:

```text
configuration_id,sample_id,shock_length_m,shock_stroke_m,rear_axle_x_m,rear_axle_z_m,sigma_shock_m,sigma_axle_m,source_reference
```

Derived curve rows are stored separately:

```text
configuration_id,wheel_travel_m,shock_travel_m,motion_ratio_dxwheel_dxshock,sigma_motion_ratio,fit_method,fit_version
```

The fitted curve must be monotone over physical travel and may not exceed measured endpoints without an explicit extrapolation flag.

---

## 5. Suspension force-law dataset

### 5.1 Front fork

```text
configuration_id,leg_or_pair,position_m,shaft_velocity_mps,force_n,direction,compression_clicks,rebound_clicks,oil_temp_c,friction_branch,endstroke_flag,sigma_force_n,source_reference
```

### 5.2 Rear shock

```text
configuration_id,shock_position_m,shaft_velocity_mps,force_n,direction,ls_comp_setting,hs_comp_setting,rebound_setting,oil_temp_c,endstroke_flag,sigma_force_n,source_reference
```

`direction` is `compression` or `rebound`.

Bench and on-bike identified data remain distinguishable by `source_reference` / metadata.

---

## 6. Engine / dyno dataset

### 6.1 Full-throttle curve

```text
configuration_id,rpm,rear_wheel_power_w,rear_wheel_torque_nm,sigma_power_w,sigma_torque_nm,source_reference
```

The source test configuration must record the dyno rear tire because Dirt Rider uses a Dunlop D404 for roller compatibility rather than the stock MX33.

### 6.2 Engine-inertia perturbation traces

```text
configuration_id,test_id,added_inertia_kgm2,time_s,rpm,throttle_command,clutch_state,gear,coolant_temp_c,oil_temp_c
```

Derived result:

```text
configuration_id,rpm_band_min,rpm_band_max,equivalent_engine_inertia_kgm2,sigma_inertia_kgm2,fit_method,source_test_ids
```

### 6.3 Closed-throttle loss map

```text
configuration_id,rpm,loss_torque_nm,sigma_loss_torque_nm,source_test_ids
```

---

## 7. Clutch dataset

```text
configuration_id,test_id,time_s,engine_rpm,clutch_output_rpm,clutch_command,transmitted_torque_nm,oil_temp_c,gear,sigma_torque_nm,source_reference
```

The runtime clutch law is fitted against relative slip speed and command/state. Full-engagement capacity must remain above the already-derived >105 N m clutch-shaft lower bound at the measured peak-torque condition.

---

## 8. Tire geometry / vertical dataset

### 8.1 Radius observations

```text
tire_id,axle,pressure_pa,vertical_load_n,speed_mps,geometric_radius_m,loaded_radius_m,effective_rolling_radius_m,sigma_radius_m,source_method,source_reference
```

### 8.2 Vertical load-deflection

```text
tire_id,pressure_pa,vertical_deflection_m,vertical_load_n,deflection_rate_mps,temperature_c,sigma_load_n,sigma_deflection_m,source_reference
```

The fitted `Fz(delta)` relation must allow load-dependent stiffness.

---

## 9. Tire hardpack force dataset

Canonical observation row:

```text
surface_id,tire_id,pressure_pa,speed_mps,fz_n,kappa,alpha_rad,camber_rad,fx_n,fy_n,mz_nm,radius_effective_m,temperature_c,source_method,sigma_fz_n,sigma_fx_n,sigma_fy_n,sigma_mz_nm,source_reference
```

Required source-method values include:

- `direct_rig`;
- `inverse_dynamics`;
- `derived`.

The first production fit must cover separated longitudinal/lateral tests and be validated on combined slip. Independent longitudinal and lateral force clamps are not an acceptable fitted representation.

---

## 10. Tire transient dataset

```text
surface_id,tire_id,pressure_pa,speed_mps,fz_n,input_type,input_step_or_sweep,time_s,kappa,alpha_rad,camber_rad,fx_n,fy_n,mz_nm,source_reference
```

Use this dataset to identify relaxation length/time or an equivalent carcass state. The steady-state force table must not be used as an instantaneous transient law without validation.

---

## 11. Validation-run dataset

Every no-assist validation run should emit one deterministic telemetry format:

```text
time_s,chassis_x_m,chassis_y_m,chassis_z_m,qw,qx,qy,qz,vx_mps,vy_mps,vz_mps,wx_radps,wy_radps,wz_radps,steer_rad,fork_travel_m,swingarm_rad,front_omega_radps,rear_omega_radps,engine_omega_radps,front_fz_n,rear_fz_n,front_fx_n,rear_fx_n,front_fy_n,rear_fy_n,front_mz_nm,rear_mz_nm,front_kappa,rear_kappa,front_alpha_rad,rear_alpha_rad,front_camber_rad,rear_camber_rad,assist_force_n,assist_torque_nm
```

For physical validation, all assist columns must remain zero.

---

## 12. Gate-opening rule

A runtime parameter may be generated only from:

- a published/measured value;
- a documented derivation;
- a fit to one of the raw datasets above;
- or a defensible bounded interval shown by sensitivity analysis to be low-impact.

No value may enter the runtime parameter set solely because it produces desirable gameplay.
