# Physics V3 Live Runtime

Physics V3 became the live Balance Point runtime in commit
`0e64ccf67eb37e4adc45a69465f5a7b79f7ff2a5`.

The previous Physics V2 classes remain in the repository only as rollback/reference code.
`BalancePointGame` now owns a `PhysicsV3MultibodyPlant` and routes gameplay input, state,
telemetry, drivetrain display and wheelie detection through that plant.

This commit intentionally triggers the standard mobile build pipeline after the guarded
migration commit so the rolling Android and iOS assets are built from the V3 runtime.
